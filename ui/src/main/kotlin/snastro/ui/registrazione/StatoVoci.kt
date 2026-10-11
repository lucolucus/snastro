package snastro.ui.registrazione

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.parlanti.applicazione.letture.VoceIdentificata
import snastro.trascrizione.applicazione.letture.TrascrittoView
import snastro.ui.eccezioneRegistrata
import snastro.ui.nonFataleRegistrato
import snastro.ui.testi.MESSAGGIO_ERRORE_GENERICO
import snastro.ui.testi.MESSAGGIO_ERRORE_VOCI
import snastro.ui.testi.MESSAGGIO_RITRASCRIZIONE_PERSA
import java.time.Instant

/**
 * The Voci-panel half of [RegistrazionePresenter]: the Voci panel and the Nome labels, over the mandatory
 * [SorgentiParlanti] (ADR 0030 §1, U1) — writes into the presenter's own [stato], published only once the
 * transcript is loaded (AC-402). It reads the panel and builds it; its holders take each flow: [carte] the card
 * commands; [proposte] the Proposte and the cross-Parte pair; [revisione] the selection toolbar and the Revisione;
 * [nomina] the naming of a phrase; [somiglianza] 'Riassegna per somiglianza'. They share this class's one runner,
 * [lanciaComando].
 *
 * Every field below is mutated only on [scope]'s (UI) dispatcher; every blocking read/command runs on
 * [io] (AC-417): the Proposte through `runInterruptible` (leaving S3 interrupts a waiting extraction,
 * ADR 0017 §3), the card commands through [ComandiVoce] (project-scoped, AC-415/AC-418).
 */
@Suppress("LongParameterList") // one parameter per collaborator of the owning RegistrazionePresenter
internal class StatoVoci(
    private val sorgenti: SorgentiParlanti,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val registrazioneId: RegistrazioneId,
    private val trascritto: () -> TrascrittoView?,
    private val stato: MutableStateFlow<RegistrazioneUiStato>,
    private val segmentiDi: (TrascrittoView) -> List<SegmentoRiga>,
) {
    /**
     * The trascritto the panel is built on; set by the presenter's own load, refreshed after a Revisione.
     * AC-415: the pending-commands collector may run BEFORE the first load sets it (the load suspends on
     * [io]) and then saw no Incontro — so a new Incontro re-reflects the in-memory project state at once.
     */
    var vista: TrascrittoView? = null
        set(nuova) {
            val cambiato = nuova?.incontroId != field?.incontroId
            field = nuova
            if (cambiato) carte.riflettiInCorso(sorgenti.comandi.stato.value)
        }

    /**
     * ADR 0018 Amendment (b) §2 (AC-454/455/461): read live from the presenter's own published [stato]
     * (`RegistrazioneUiStato.Dati.soloLettura`, set by [RegistrazionePresenter.carica] from its
     * optional `stati` source, AC-452) — no separate copy kept here. While `true`, [carte]'s and
     * [revisione]'s commands send no command (the fakes see zero calls) and [proposte] starts no
     * Proposta job; [pannelloDi]/[RevisioneVoci.barraDi] disable every card/toolbar action. '▶ estratto' and Segmento
     * playback are untouched (owned by the base presenter, not here).
     */
    private val soloLettura: Boolean
        get() = (stato.value as? RegistrazioneUiStato.Dati)?.soloLettura == true

    /**
     * ADR 0019 §6 (AC-531/AC-545): every editing action of the panel and of the toolbar is disabled while
     * read-only AND while a similarity run is computing, previewed or applying — the same controls, the
     * same presenter backstops ([carte] and [revisione]'s commands).
     */
    private val modificheBloccate: Boolean
        get() = soloLettura || somiglianza.aperta

    /** AC-530: a Parlanti card command or a naming is pending for this Registrazione. */
    private val comandiPendenti: Boolean
        get() = carte.pendenti || nomina.frasiPendenti

    private var dati: DatiParlanti? = null
    private var erroreLettura = false

    // L665a: bumped on every `ricaricaParlanti()` — a call compares its OWN value against this one
    // before writing, so a superseded read (a newer `ricaricaParlanti()` already started, e.g. two card
    // commands settling around the same time, or a Cambiamento arriving mid-read) can never overwrite a
    // fresher one just because it happens to finish LAST (same pattern as `LettorePresenter`'s HIGH-1
    // `richiestaCorrente`).
    private var generazioneParlanti = 0L
    private val soglieProgrammate = mutableSetOf<Pair<Any, Instant>>()

    /** 'Riassegna per somiglianza'; [RegistrazionePresenter.azioni] binds its actions straight to it. */
    val somiglianza: SomiglianzaVoci = SomiglianzaVoci(
        sorgenti.somiglianza,
        scope,
        io,
        registrazioneId,
        sorgenti.clock,
        pubblica = ::pubblica,
        dopoApplicazione = { if (vista != null) ricaricaDopoRevisione(azzeraSelezione = true) },
        errore = ::impostaErrore,
    )

    /** The card commands; [RegistrazionePresenter.azioni] binds its actions straight to it. */
    val carte: CarteVoci = CarteVoci(
        sorgenti,
        scope,
        io,
        vista = { vista },
        cartaDi = { voceId ->
            (stato.value as? RegistrazioneUiStato.Dati)?.pannello?.carte?.find { it.voceId == voceId }
        },
        lanciaComando = ::lanciaComando,
        ricaricaParlanti = ::ricaricaParlanti,
        pubblica = ::pubblica,
        programmaSoglia = ::programmaSoglia,
    )

    /** The Proposte and the cross-Parte pair, each computed by its own background job. */
    private val proposte: ProposteVoci = ProposteVoci(
        sorgenti,
        scope,
        io,
        vista = { vista },
        dati = { dati },
        erroreLettura = { erroreLettura },
        soloLettura = { soloLettura },
        pubblica = ::pubblica,
        impostaErrore = ::impostaErrore,
    )

    /**
     * The one runner [lanciaComando] with its default `suGuasto`, as the holders take it: a lambda, because a
     * function reference cannot leave out the defaulted parameter.
     */
    private val lanciaComandoGenerico: (String, () -> Unit, suspend () -> Unit) -> Unit =
        { operazione, libera, comando -> lanciaComando(operazione, libera, comando = comando) }

    /** "Dai un nome a questa frase"; [RegistrazionePresenter.azioni] binds its actions straight to it. */
    val nomina: NominaFraseVoci = NominaFraseVoci(
        sorgenti,
        scope,
        io,
        registrazioneId,
        vista = { vista },
        dati = { dati },
        fraseSelezionata = { vista?.let(revisione::barraDi)?.frase },
        lanciaComando = lanciaComandoGenerico,
        ricaricaDopoRevisione = ::ricaricaDopoRevisione,
        pubblica = ::pubblica,
        impostaErrore = ::impostaErrore,
        programmaSoglia = ::programmaSoglia,
    )

    /** The selection toolbar and the Revisione; [RegistrazionePresenter.azioni] binds its actions straight to it. */
    val revisione: RevisioneVoci = RevisioneVoci(
        sorgenti,
        io,
        registrazioneId,
        trascritto,
        vista = { vista },
        dati = { dati },
        modificheBloccate = { modificheBloccate },
        attesaFraseDi = nomina::attesaFraseDi,
        lanciaComando = lanciaComandoGenerico,
        ricaricaDopoRevisione = ::ricaricaDopoRevisione,
        pubblica = ::pubblica,
        impostaErrore = ::impostaErrore,
    )

    fun avvia() {
        carte.avvia()
        nomina.avvia()
        somiglianza.avvia()
        scope.launch {
            // AC-319: ImpronteRiallineate (& co.) reach S3 as a Cambiamento — the Proposte are re-read,
            // the selection is untouched.
            sorgenti.aggiornamenti.cambiamenti.collect { c ->
                if ((c.registrazioneId == null || c.registrazioneId == registrazioneId) && vista != null) {
                    ricaricaParlanti()
                }
            }
        }
    }

    /** AC-405: the attributed Nome replaces "Voce n" in the labels. */
    fun nomeDi(voceId: VoceId): String? = dati?.nomeDi(voceId)

    // --- load --------------------------------------------------------------------------------------

    /** AC-405: reads identificazione-voci, parlanti-attivi and proposta-unione, then the Proposte. */
    suspend fun ricaricaParlanti() {
        val generazione = ++generazioneParlanti
        // AC-I77: the Incontro's Voci are read only when there is another Parte to merge with.
        val incontroDaLeggere = vista?.incontroId.takeIf { vista?.parti.orEmpty().size > 1 }
        // AC-405: a failed read (null) shows a message in the cards, the transcript (AC-402) stays usable.
        val nuovi = nonFataleRegistrato("leggi i parlanti di $registrazioneId") {
            withContext(io) {
                DatiParlanti(
                    identificate = sorgenti.identificazione().associateBy { it.voceId },
                    attivi = sorgenti.parlantiAttivi(),
                    unioni = sorgenti.unioni(),
                    vociIncontro = incontroDaLeggere?.let { sorgenti.vociIncontro(it)?.voci }.orEmpty(),
                )
            }
        }
        // L665a: a call superseded by a NEWER `ricaricaParlanti()` (already started while this one was
        // still reading) writes nothing at all — not `dati`, not `erroreLettura`, no publish, no Proposta
        // job — whatever the outcome of its own read.
        if (generazione != generazioneParlanti) return
        // (rework cycle 1, MED): a FAILED read keeps the last good `dati` — Nomi, `parlantiAttivi` and
        // the toolbar all read off it (`nomeDi`, `pannelloDi`) — instead of wiping it to `null` and
        // reverting every label to "Voce n" and every roster-driven control to empty/disabled. The
        // card-level `Errore` (`contenutoDi`) still shows, since `erroreLettura` is unconditional below.
        if (nuovi != null) dati = nuovi
        erroreLettura = nuovi == null
        pubblica()
        proposte.ricalcola()
    }

    /** AC-412: re-publishes when [inizio] + the threshold is reached, so the card turns to 'In attesa…'. */
    private fun programmaSoglia(ref: Any, inizio: Instant) {
        if (!soglieProgrammate.add(ref to inizio)) return
        val restante = RegistrazionePresenter.SOGLIA_ATTESA_VISIBILE_MS - trascorsiMs(inizio, sorgenti.clock.instant())
        if (restante > 0) {
            scope.launch {
                delay(restante)
                pubblica()
            }
        }
    }

    /**
     * The one runner of the panel's committing commands (cards, Revisione, naming), ADR 0041 §3: publishes the guard
     * its caller has just set, then runs [comando] through [eccezioneRegistrata] — an `Exception` is logged and
     * becomes [suGuasto], an `Error` after the commit still propagates (D-0065). [libera] resets the guard in
     * `finally`: never a card, a row or the toolbar stuck in progress.
     */
    private fun lanciaComando(
        operazione: String,
        libera: () -> Unit,
        suGuasto: () -> Unit = { impostaErrore(MESSAGGIO_ERRORE_GENERICO) },
        comando: suspend () -> Unit,
    ) {
        pubblica()
        scope.launch {
            try {
                eccezioneRegistrata(operazione) { comando() } ?: suGuasto()
            } finally {
                libera()
                pubblica()
            }
        }
    }

    private suspend fun ricaricaDopoRevisione(azzeraSelezione: Boolean) {
        // AC-I84 (L189): cancel BEFORE the reload read — an in-flight pre-join pair must never be published meanwhile.
        proposte.fermaTraParti()
        pubblica()
        val nuova = withContext(io) { trascritto() }
        if (nuova == null) {
            // L253: the pair was cleared above — recompute it anyway, the banner never waits for the next trigger.
            ricaricaParlanti()
            return
        }
        vista = nuova
        stato.update { d -> if (d is RegistrazioneUiStato.Dati) d.copy(segmenti = segmentiDi(nuova)) else d }
        revisione.riallinea(nuova, azzera = azzeraSelezione)
        proposte.azzera()
        ricaricaParlanti()
    }

    private fun impostaErrore(messaggio: String) =
        stato.update { d -> if (d is RegistrazioneUiStato.Dati) d.copy(errore = messaggio) else d }

    // --- state building ----------------------------------------------------------------------------

    /** Rebuilds the labels, the panel and the toolbar from the raw inputs above. */
    fun pubblica() {
        val v = vista ?: return
        stato.update { d ->
            if (d !is RegistrazioneUiStato.Dati) {
                d
            } else {
                d.copy(
                    segmenti = d.segmenti.map { riga ->
                        riga.copy(
                            etichettaVoce = etichettaDiVoce(v, riga.voceId, nomeDi(riga.voceId)),
                            confermato = v.segmenti.find { it.segmentoId == riga.segmentoId }?.confermato == true,
                            attesaFrase = nomina.attesaFraseDi(riga.segmentoId),
                        )
                    },
                    pannello = pannelloDi(v, d.audioDisponibile),
                    selezione = revisione.selezione,
                    barraSelezione = revisione.barraDi(v),
                    // AC-454: the panel's own third banner line — only while the base presenter's
                    // soloLettura (AC-452) is set; the base presenter never writes this field itself.
                    bannerRitrascrizionePannello = if (soloLettura) MESSAGGIO_RITRASCRIZIONE_PERSA else null,
                )
            }
        }
        somiglianza.controllaSolaLettura(soloLettura)
    }

    private fun pannelloDi(v: TrascrittoView, audioDisponibile: Boolean): PannelloVoci {
        val tutte = opzioniDi(v, ::nomeDi)
        // AC-I77: the Incontro's Voci that do not speak in this Parte, with the Parti they speak in.
        val inAltreParti = dati?.vociIncontro.orEmpty().filter { r -> v.voci.none { it.voceId == r.voceId } }
            .map { r -> nomeDi(r.voceId).let { nome -> OpzioneVoce(r.voceId, nome ?: r.etichetta, r.parti, nome) } }
        return PannelloVoci(
            carte = v.voci.map { voce ->
                CartaVoce(
                    voceId = voce.voceId,
                    titolo = voce.etichetta,
                    contenuto = contenutoDi(dati, erroreLettura, voce.voceId, proposte::propostaDi),
                    inCorso = carte.attesaDi(VoceRef(v.incontroId, voce.voceId)),
                    errore = carte.errori[voce.voceId],
                    altreVoci = tutte.filter { it.voceId != voce.voceId },
                    altreParti = voce.altreParti,
                    vociAltreParti = inAltreParti,
                    // AC-454: 'Conferma'/'altri ▾'/'nuovo…'/'salta'/'cambia' disabled while read-only —
                    // and while a similarity run is open (AC-531/AC-545).
                    soloLettura = modificheBloccate,
                )
            },
            parlantiAttivi = dati?.attivi.orEmpty(),
            unioni = dati?.unioni.orEmpty(),
            traParti = proposte.coppiaMostrata,
            estrattiDisponibili = audioDisponibile,
            altreParti = v.parti.filter { it.registrazioneId != v.registrazioneId }
                .associate { it.registrazioneId to it.numero },
            // AC-454: the merge banner's action disabled too — '▶ estratto' stays governed only by
            // estrattiDisponibili (audio availability), untouched by soloLettura.
            unioneAbilitata = !revisione.revisioneInCorso && !modificheBloccate,
            somiglianza = somiglianza.pannello(
                riferimentiDi(v, dati?.identificate.orEmpty(), dati?.attivi.orEmpty()),
                bloccato = soloLettura || comandiPendenti || revisione.revisioneInCorso || dati == null,
                parti = v.parti.associate { it.registrazioneId to it.numero },
            ) { etichettaDiVoce(v, it, nomeDi(it)) },
        )
    }
}

/** A card's content: the read's error, still loading, the attributed Parlante, or the Voce to identify. */
private fun contenutoDi(
    dati: DatiParlanti?,
    erroreLettura: Boolean,
    voceId: VoceId,
    propostaDi: (VoceId) -> StatoProposta,
): ContenutoCarta = when {
    erroreLettura -> ContenutoCarta.Errore(MESSAGGIO_ERRORE_VOCI)
    dati == null -> ContenutoCarta.Caricamento
    else -> dati.identificate[voceId]?.attribuita()
        ?: ContenutoCarta.DaIdentificare(propostaDi(voceId), galleriaVuota = dati.attivi.isEmpty())
}

private fun VoceIdentificata.attribuita(): ContenutoCarta.Attribuita? {
    val id = parlanteId
    val nome = nome
    val tipo = tipoParlante
    return if (id != null && nome != null && tipo != null) ContenutoCarta.Attribuita(id, nome, tipo) else null
}
