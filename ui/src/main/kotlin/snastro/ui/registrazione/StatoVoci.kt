package snastro.ui.registrazione

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import snastro.kernel.Esito
import snastro.kernel.IncontroId
import snastro.kernel.ParlanteId
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.parlanti.applicazione.eventi.TipoParlanteVista
import snastro.parlanti.applicazione.letture.CoppiaTraParti
import snastro.parlanti.applicazione.letture.ParlanteAttivo
import snastro.parlanti.applicazione.letture.PropostaDiUnione
import snastro.parlanti.applicazione.letture.VoceIdentificata
import snastro.parlanti.applicazione.porte.Fascia
import snastro.trascrizione.applicazione.letture.TrascrittoView
import snastro.trascrizione.applicazione.letture.VoceIncontroRiga
import snastro.ui.eccezioneRegistrata
import snastro.ui.nonFataleRegistrato
import snastro.ui.testi.MESSAGGIO_ERRORE_GENERICO
import snastro.ui.testi.MESSAGGIO_ERRORE_PROPOSTA
import snastro.ui.testi.MESSAGGIO_ERRORE_VOCI
import snastro.ui.testi.MESSAGGIO_RITRASCRIZIONE_PERSA
import snastro.ui.testi.messaggioPer
import java.time.Duration
import java.time.Instant

/**
 * The Voci-panel half of [RegistrazionePresenter]: the Voci panel, the Nome labels and the card commands, over
 * the mandatory [SorgentiParlanti] (ADR 0030 §1, U1) — writes into the presenter's own [stato], published only
 * once the transcript is loaded (AC-402). Its holders take the rest of the panel: [revisione] the selection
 * toolbar, the Revisione and the naming of a phrase; [somiglianza] 'Riassegna per somiglianza'. They share this
 * class's one runner, [lanciaComando].
 *
 * Every field below is mutated only on [scope]'s (UI) dispatcher; every blocking read/command runs on
 * [io] (AC-417): the Proposte through `runInterruptible` (leaving S3 interrupts a waiting extraction,
 * ADR 0017 §3), the card commands through [ComandiVoce] (project-scoped, AC-415/AC-418).
 */
@Suppress("TooManyFunctions", "LongParameterList") // one method per user action; one parameter per collaborator
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
            if (cambiato) riflettiInCorso(sorgenti.comandi.stato.value)
        }

    /**
     * ADR 0033 §4.1: the Voci are the Incontro's, so their VoceRef carries the Incontro of the trascritto shown.
     * Read off [vista] alone, never through [trascritto]: that is a repository read, only for [io].
     */
    private val incontroId: IncontroId?
        get() = vista?.incontroId

    private fun voceRef(voceId: VoceId): VoceRef =
        VoceRef(checkNotNull(incontroId) { "Voce $voceId senza trascritto caricato" }, voceId)

    /**
     * ADR 0018 Amendment (b) §2 (AC-454/455/461): read live from the presenter's own published [stato]
     * (`RegistrazioneUiStato.Dati.soloLettura`, set by [RegistrazionePresenter.carica] from its
     * optional `stati` source, AC-452) — no separate copy kept here. While `true`, [invia]/
     * [revisione]'s commands send no command (the fakes see zero calls) and [ricaricaParlanti] starts no
     * Proposta job; [pannelloDi]/[RevisioneVoci.barraDi] disable every card/toolbar action. '▶ estratto' and Segmento
     * playback are untouched (owned by the base presenter, not here).
     */
    private val soloLettura: Boolean
        get() = (stato.value as? RegistrazioneUiStato.Dati)?.soloLettura == true

    /**
     * ADR 0019 §6 (AC-531/AC-545): every editing action of the panel and of the toolbar is disabled while
     * read-only AND while a similarity run is computing, previewed or applying — the same controls, the
     * same presenter backstops ([invia] and [revisione]'s commands).
     */
    private val modificheBloccate: Boolean
        get() = soloLettura || somiglianza.aperta

    /** AC-530: a Parlanti card command or a naming is pending for this Registrazione. */
    private val comandiPendenti: Boolean
        get() = invii.isNotEmpty() || inCorsoAltrove.isNotEmpty() || revisione.frasiPendenti

    private var dati: DatiParlanti? = null
    private var erroreLettura = false

    // L665a: bumped on every `ricaricaParlanti()` — a call compares its OWN value against this one
    // before writing, so a superseded read (a newer `ricaricaParlanti()` already started, e.g. two card
    // commands settling around the same time, or a Cambiamento arriving mid-read) can never overwrite a
    // fresher one just because it happens to finish LAST (same pattern as `LettorePresenter`'s HIGH-1
    // `richiestaCorrente`).
    private var generazioneParlanti = 0L
    private val proposte = mutableMapOf<VoceId, StatoProposta>()
    private var proposteOltreSoglia = false
    private var lavoroProposte: Job? = null

    // AC-I83/AC-I84: the cross-Parte pair of the Incontro, computed by its OWN job so the panel never waits on it.
    private var coppiaTraParti: CoppiaTraParti? = null
    private var lavoroTraParti: Job? = null
    private val invii = mutableMapOf<VoceRef, Instant>()
    private var inCorsoAltrove: Map<VoceRef, Instant> = emptyMap()
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

    /** The selection toolbar, the Revisione and the naming of a phrase; [RegistrazionePresenter.azioni] binds to it. */
    val revisione: RevisioneVoci = RevisioneVoci(
        sorgenti,
        scope,
        io,
        registrazioneId,
        trascritto,
        vista = { vista },
        dati = { dati },
        modificheBloccate = { modificheBloccate },
        lanciaComando = { operazione, libera, comando -> lanciaComando(operazione, libera, comando = comando) },
        ricaricaDopoRevisione = ::ricaricaDopoRevisione,
        pubblica = ::pubblica,
        impostaErrore = ::impostaErrore,
        programmaSoglia = ::programmaSoglia,
    )
    private val erroriCarta = mutableMapOf<VoceId, String>()

    fun avvia() {
        scope.launch { sorgenti.comandi.stato.collect { mappa -> rifletti(mappa) } }
        revisione.avvia()
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
        val incontroDaLeggere = incontroId.takeIf { vista?.parti.orEmpty().size > 1 }
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
        // AC-454: no Proposta job while read-only — it would only wait on the native Mutex held by the
        // running pipeline (ADR 0017); AC-461: the job starts again once soloLettura ends (the same
        // ricaricaParlanti() call the base presenter's Cambiamento-triggered carica() already makes).
        if (!erroreLettura && !soloLettura) avviaProposte()
        avviaTraParti()
    }

    /** AC-I84: neither read-only, nor in error, nor a Proposta di unione (which takes the single banner slot). */
    private val bannerTraPartiAmmesso: Boolean
        get() = !soloLettura && !erroreLettura && dati?.unioni.orEmpty().isEmpty()

    /**
     * AC-I84: the cross-Parte proposal is requested only when the banner could show — not read-only, not in
     * error, no Proposta di unione (which takes the single banner slot). A superseded job is cancelled; a
     * failed computation shows no banner (the manual 'Unisci con' stays) and the generic message.
     */
    private fun avviaTraParti() {
        lavoroTraParti?.cancel()
        val incontro = incontroId
        if (incontro == null || !bannerTraPartiAmmesso) {
            coppiaTraParti = null
            return
        }
        lavoroTraParti = scope.launch {
            val coppie = nonFataleRegistrato("calcola la coppia tra parti di $incontro") {
                runInterruptible(io) { sorgenti.traParti(incontro) }
            }
            coppiaTraParti = coppie?.firstOrNull()
            pubblica()
            // A superseded job never gets here (its cancellation propagates): `null` is a real fault.
            if (coppie == null) impostaErrore(MESSAGGIO_ERRORE_GENERICO)
        }
    }

    /**
     * ADR 0017 §3 / AC-421: ONE background job computes the Proposte of the not-attributed Voci one at a
     * time, in panel order; restarting it cancels the previous one. A Proposta already shown stays on
     * screen until its fresh value arrives (AC-319). Past [RegistrazionePresenter.SOGLIA_ATTESA_VISIBILE_MS]
     * a card still without one shows 'Proposta in attesa dell'elaborazione…' (AC-416).
     */
    private fun avviaProposte() {
        val v = vista ?: return
        val d = dati ?: return
        lavoroProposte?.cancel()
        proposteOltreSoglia = false
        val daCalcolare = v.voci.map { it.voceId }.filter { d.identificate[it]?.parlanteId == null }
        lavoroProposte = scope.launch {
            val soglia = launch {
                delay(RegistrazionePresenter.SOGLIA_ATTESA_VISIBILE_MS)
                proposteOltreSoglia = true
                pubblica()
            }
            try {
                for (voceId in daCalcolare) {
                    // AC-212: an empty Galleria has no Candidato to find — no print is extracted at all.
                    proposte[voceId] = if (d.attivi.isEmpty()) {
                        StatoProposta.Pronta(emptyList(), nuovoEvidenziato = false)
                    } else {
                        calcola(voceId)
                    }
                    pubblica()
                }
            } finally {
                soglia.cancel()
            }
        }
    }

    private suspend fun calcola(voceId: VoceId): StatoProposta =
        nonFataleRegistrato("calcola la proposta di $voceId") {
            val candidati = runInterruptible(io) { sorgenti.proposta(voceRef(voceId)) }?.candidati.orEmpty()
            // AC-213: all 'nessuna' → the list is still shown, 'nuovo…' is the preferred action.
            StatoProposta.Pronta(candidati, candidati.isNotEmpty() && candidati.all { it.fascia == Fascia.NESSUNA })
        } ?: StatoProposta.Errore(MESSAGGIO_ERRORE_PROPOSTA)

    // --- pending card commands (ADR 0017 §3) -------------------------------------------------------

    /**
     * AC-415: the per-project pending state. A command that ended while THIS presenter did not send it
     * (sent by an earlier S3 visit) → re-read the panel, so its outcome (the Nome) shows here too.
     */
    private suspend fun rifletti(mappa: Map<VoceRef, StatoComando>) {
        val conclusiAltrove = riflettiInCorso(mappa)
        pubblica()
        if (conclusiAltrove.isNotEmpty() && vista != null) ricaricaParlanti()
    }

    /** Keeps this Incontro's pending commands of [mappa]; returns those that ended while not sent from here. */
    private fun riflettiInCorso(mappa: Map<VoceRef, StatoComando>): Set<VoceRef> {
        val mie = mappa.filterKeys { it.incontroId == incontroId }.mapValues { it.value.avviatoAlle }
        val conclusiAltrove = inCorsoAltrove.keys - mie.keys - invii.keys
        inCorsoAltrove = mie
        mie.forEach { (ref, inizio) -> programmaSoglia(ref, inizio) }
        return conclusiAltrove
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

    private fun attesaDi(ref: VoceRef): AttesaComando? =
        attesaDa(invii[ref] ?: inCorsoAltrove[ref], sorgenti.clock.instant())

    /**
     * AC-411: the card goes pending AT ONCE (published before the command even starts — a second click
     * finds its actions disabled); AC-414: the outcome is shown like any command's; AC-413: a cancelled
     * one (`null`) restores the card with no message; a thrown one is the generic message on the card.
     */
    private fun invia(voceId: VoceId, comando: (VoceRef) -> ComandoVoce) {
        // AC-454: 'Conferma'/'altri ▾'/'nuovo…'/'salta'/'cambia' send no command while read-only —
        // `azioniAbilitate` already folds in `soloLettura` (CartaVoce, built by pannelloDi below).
        if (cartaDi(voceId)?.azioniAbilitate != true) return
        val ref = voceRef(voceId)
        val inizio = sorgenti.clock.instant()
        invii[ref] = inizio
        erroriCarta.remove(voceId)
        programmaSoglia(ref, inizio)
        lanciaComando(
            "comando sulla voce $voceId",
            libera = { invii.remove(ref) },
            suGuasto = { erroriCarta[voceId] = MESSAGGIO_ERRORE_GENERICO },
        ) {
            when (val esito = withContext(io) { sorgenti.comandi.esegui(comando(ref)) }) {
                null -> Unit
                is Esito.Ok -> ricaricaParlanti()
                is Esito.Errore -> erroriCarta[voceId] = messaggioPer(esito.errore)
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

    /** 'Conferma': the top Candidato of the Proposta. */
    fun conferma(voceId: VoceId) {
        val carta = cartaDi(voceId)?.takeIf { it.confermaAbilitata } ?: return
        val primo = ((carta.contenuto as? ContenutoCarta.DaIdentificare)?.proposta as? StatoProposta.Pronta)
            ?.candidati?.firstOrNull() ?: return
        invia(voceId) { ComandoVoce.Conferma(it, primo.parlanteId) }
    }

    /** 'altri ▾ → Conferma' on a card to identify, 'cambia' on an attributed one. */
    fun confermaParlante(voceId: VoceId, parlanteId: ParlanteId) =
        invia(voceId) { ComandoVoce.Conferma(it, parlanteId) }

    /** 'nuovo…' (also from 'cambia'): a new Parlante; the Nome rules are the domain's (AC-215). */
    fun nuovoParlante(voceId: VoceId, nome: String, tipo: TipoParlanteVista) =
        invia(voceId) { ComandoVoce.Nuovo(it, nome, tipo) }

    /** 'salta' — AC-219: never on an attributed Voce ('cambia' there). */
    fun salta(voceId: VoceId) {
        if (cartaDi(voceId)?.contenuto !is ContenutoCarta.DaIdentificare) return
        invia(voceId) { ComandoVoce.Salta(it) }
    }

    /** AC-413: 'Annulla' — cancels the pending command; not a domain command, never an error. */
    fun annulla(voceId: VoceId) = sorgenti.comandi.annulla(voceRef(voceId))

    fun chiudiErrore(voceId: VoceId) {
        erroriCarta.remove(voceId)
        pubblica()
    }

    private suspend fun ricaricaDopoRevisione(azzeraSelezione: Boolean) {
        // AC-I84 (L189): cancel BEFORE the reload read — an in-flight pre-join pair must never be published meanwhile.
        lavoroTraParti?.cancel()
        coppiaTraParti = null
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
        proposte.clear()
        ricaricaParlanti()
    }

    private fun impostaErrore(messaggio: String) =
        stato.update { d -> if (d is RegistrazioneUiStato.Dati) d.copy(errore = messaggio) else d }

    // --- state building ----------------------------------------------------------------------------

    private fun cartaDi(voceId: VoceId): CartaVoce? =
        (stato.value as? RegistrazioneUiStato.Dati)?.pannello?.carte?.find { it.voceId == voceId }

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
                            etichettaVoce = etichetta(v, riga.voceId),
                            confermato = v.segmenti.find { it.segmentoId == riga.segmentoId }?.confermato == true,
                            attesaFrase = revisione.attesaFraseDi(riga.segmentoId),
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

    private fun etichetta(v: TrascrittoView, voceId: VoceId): String = etichettaDiVoce(v, voceId, nomeDi(voceId))

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
                    contenuto = contenutoDi(voce.voceId),
                    inCorso = attesaDi(VoceRef(v.incontroId, voce.voceId)),
                    errore = erroriCarta[voce.voceId],
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
            traParti = coppiaTraParti.takeIf { bannerTraPartiAmmesso },
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
            ) { etichetta(v, it) },
        )
    }

    private fun contenutoDi(voceId: VoceId): ContenutoCarta {
        val d = dati
        return when {
            erroreLettura -> ContenutoCarta.Errore(MESSAGGIO_ERRORE_VOCI)
            d == null -> ContenutoCarta.Caricamento
            else -> contenutoLetto(d, voceId)
        }
    }

    private fun contenutoLetto(d: DatiParlanti, voceId: VoceId): ContenutoCarta {
        val identificata = d.identificate[voceId]
        val id = identificata?.parlanteId
        val nome = identificata?.nome
        val tipo = identificata?.tipoParlante
        return if (id != null && nome != null && tipo != null) {
            ContenutoCarta.Attribuita(id, nome, tipo)
        } else {
            val proposta = proposte[voceId]
                ?: if (proposteOltreSoglia) StatoProposta.InAttesa else StatoProposta.Caricamento
            ContenutoCarta.DaIdentificare(proposta, galleriaVuota = d.attivi.isEmpty())
        }
    }
}

/** What the Voci panel reads from Parlanti (AC-405), kept until a newer read succeeds. */
internal class DatiParlanti(
    val identificate: Map<VoceId, VoceIdentificata>,
    val attivi: List<ParlanteAttivo>,
    val unioni: List<PropostaDiUnione>,
    /** AC-I77: the Incontro's Voci (empty over a 1-Parte Incontro: not read). */
    val vociIncontro: List<VoceIncontroRiga>,
) {
    /** AC-405: the attributed Nome of [voceId], if any. */
    fun nomeDi(voceId: VoceId): String? = identificate[voceId]?.nome
}

/** AC-405: the Nome if attributed, else trascritto-view's own "Voce n". */
internal fun etichettaDiVoce(vista: TrascrittoView, voceId: VoceId, nome: String?): String =
    nome ?: vista.voci.find { it.voceId == voceId }?.etichetta ?: "Voce ${voceId.numero}"

/** Every Voce of [vista] as a menu option, under its label. */
internal fun opzioniDi(vista: TrascrittoView, nomeDi: (VoceId) -> String?): List<OpzioneVoce> =
    vista.voci.map { OpzioneVoce(it.voceId, etichettaDiVoce(vista, it.voceId, nomeDi(it.voceId))) }

/** AC-412/AC-529: a command pending since [inizio] is in progress, then 'In attesa…' past the threshold. */
internal fun attesaDa(inizio: Instant?, adesso: Instant): AttesaComando? {
    if (inizio == null) return null
    return if (trascorsiMs(inizio, adesso) >= RegistrazionePresenter.SOGLIA_ATTESA_VISIBILE_MS) {
        AttesaComando.IN_ATTESA
    } else {
        AttesaComando.IN_CORSO
    }
}

private fun trascorsiMs(inizio: Instant, adesso: Instant): Long = Duration.between(inizio, adesso).toMillis()
