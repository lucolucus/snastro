package snastro.ui.registrazione

import kotlinx.coroutines.CancellationException
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
import snastro.kernel.ParlanteId
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.parlanti.applicazione.eventi.TipoParlanteVista
import snastro.parlanti.applicazione.letture.ParlanteAttivo
import snastro.parlanti.applicazione.letture.PropostaDiUnione
import snastro.parlanti.applicazione.letture.VoceIdentificata
import snastro.parlanti.applicazione.porte.Fascia
import snastro.trascrizione.applicazione.comandi.ConfermaSegmento
import snastro.trascrizione.applicazione.comandi.DividiVoce
import snastro.trascrizione.applicazione.comandi.RiassegnaSegmento
import snastro.trascrizione.applicazione.comandi.UnisciVoci
import snastro.trascrizione.applicazione.letture.TrascrittoView
import snastro.ui.testi.MESSAGGIO_ERRORE_GENERICO
import snastro.ui.testi.MESSAGGIO_ERRORE_PROPOSTA
import snastro.ui.testi.MESSAGGIO_ERRORE_VOCI
import snastro.ui.testi.MESSAGGIO_RITRASCRIZIONE_PERSA
import snastro.ui.testi.SPIEGAZIONE_DIVIDI_INTERA_VOCE
import snastro.ui.testi.messaggioPer
import java.time.Duration
import java.time.Instant

/**
 * The R2 half of [RegistrazionePresenter] (schermata-registrazione-identificazione): the Voci panel, the
 * Nome labels, the selection toolbar and the Revisione commands. It exists only when [SorgentiParlanti]
 * are supplied (AC-402) and writes into the presenter's own [stato] — one screen, one state.
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
    /** The trascritto the panel is built on; set by the presenter's own load, refreshed after a Revisione. */
    var vista: TrascrittoView? = null

    /**
     * ADR 0018 Amendment (b) §2 (AC-454/455/461): read live from the presenter's own published [stato]
     * (`RegistrazioneUiStato.Dati.soloLettura`, set by [RegistrazionePresenter.carica] from its
     * optional `stati` source, AC-452) — no separate copy kept here. While `true`, [invia]/
     * [eseguiRevisione] send no command (the fakes see zero calls) and [ricaricaParlanti] starts no
     * Proposta job; [pannelloDi]/[barraDi] disable every card/toolbar action. '▶ estratto' and Segmento
     * playback are untouched (owned by the base presenter, not here).
     */
    private val soloLettura: Boolean
        get() = (stato.value as? RegistrazioneUiStato.Dati)?.soloLettura == true

    /**
     * ADR 0019 §6 (AC-531/AC-545): every editing action of the panel and of the toolbar is disabled while
     * read-only AND while a similarity run is computing, previewed or applying — the same controls, the
     * same presenter backstops ([invia], [eseguiRevisione], [nominaFrase]).
     */
    private val modificheBloccate: Boolean
        get() = soloLettura || somiglianza?.aperta == true

    /** AC-530: a Parlanti card command or a naming is pending for this Registrazione. */
    private val comandiPendenti: Boolean
        get() = listOf(invii, inCorsoAltrove, inviiFrasi, frasiAltrove).any { it.isNotEmpty() }

    private class DatiParlanti(
        val identificate: Map<VoceId, VoceIdentificata>,
        val attivi: List<ParlanteAttivo>,
        val unioni: List<PropostaDiUnione>,
    )

    private class RisultatoRevisione(val esito: Esito<Unit>, val modificato: Boolean)

    private var dati: DatiParlanti? = null
    private var erroreLettura = false
    private val proposte = mutableMapOf<VoceId, StatoProposta>()
    private var proposteOltreSoglia = false
    private var lavoroProposte: Job? = null
    private val invii = mutableMapOf<VoceRef, Instant>()
    private var inCorsoAltrove: Map<VoceRef, Instant> = emptyMap()
    private val soglieProgrammate = mutableSetOf<Pair<Any, Instant>>()
    private val inviiFrasi = mutableMapOf<SegmentoId, Instant>()
    private var frasiAltrove: Map<SegmentoId, Instant> = emptyMap()
    private val somiglianza: SomiglianzaVoci? = sorgenti.somiglianza?.let { porta ->
        SomiglianzaVoci(
            porta,
            scope,
            io,
            registrazioneId,
            sorgenti.clock,
            pubblica = ::pubblica,
            dopoApplicazione = { if (vista != null) ricaricaDopoRevisione(azzeraSelezione = true) },
            errore = ::impostaErrore,
        )
    }
    private val erroriCarta = mutableMapOf<VoceId, String>()
    private var selezione: Set<SegmentoId> = emptySet()
    private var revisioneInCorso = false

    fun avvia() {
        scope.launch { sorgenti.comandi.stato.collect { mappa -> rifletti(mappa) } }
        scope.launch { sorgenti.comandi.statoFrasi.collect { mappa -> riflettiFrasi(mappa) } }
        somiglianza?.avvia()
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
    fun nomeDi(voceId: VoceId): String? = dati?.identificate?.get(voceId)?.nome

    // --- load --------------------------------------------------------------------------------------

    /** AC-405: reads identificazione-voci, parlanti-attivi and proposta-unione, then the Proposte. */
    suspend fun ricaricaParlanti() {
        try {
            dati = withContext(io) {
                DatiParlanti(
                    identificate = sorgenti.identificazione().associateBy { it.voceId },
                    attivi = sorgenti.parlantiAttivi(),
                    unioni = sorgenti.unioni(),
                )
            }
            erroreLettura = false
        } catch (e: CancellationException) {
            throw e
        } catch (
            // AC-405: a failed read shows a message in the cards, the transcript (AC-402) stays usable.
            @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
        ) {
            erroreLettura = true
        }
        pubblica()
        // AC-454: no Proposta job while read-only — it would only wait on the native Mutex held by the
        // running pipeline (ADR 0017); AC-461: the job starts again once soloLettura ends (the same
        // ricaricaParlanti() call the base presenter's Cambiamento-triggered carica() already makes).
        if (!erroreLettura && !soloLettura) avviaProposte()
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

    private suspend fun calcola(voceId: VoceId): StatoProposta = try {
        val vista = runInterruptible(io) { sorgenti.proposta(VoceRef(registrazioneId, voceId)) }
        val candidati = vista?.candidati.orEmpty()
        // AC-213: all 'nessuna' → the list is still shown, 'nuovo…' is the preferred action.
        StatoProposta.Pronta(candidati, candidati.isNotEmpty() && candidati.all { it.fascia == Fascia.NESSUNA })
    } catch (e: CancellationException) {
        throw e
    } catch (
        @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
    ) {
        StatoProposta.Errore(MESSAGGIO_ERRORE_PROPOSTA)
    }

    // --- pending card commands (ADR 0017 §3) -------------------------------------------------------

    /**
     * AC-415: the per-project pending state. A command that ended while THIS presenter did not send it
     * (sent by an earlier S3 visit) → re-read the panel, so its outcome (the Nome) shows here too.
     */
    private suspend fun rifletti(mappa: Map<VoceRef, StatoComando>) {
        val mie = mappa.filterKeys { it.registrazioneId == registrazioneId }.mapValues { it.value.avviatoAlle }
        val conclusiAltrove = inCorsoAltrove.keys - mie.keys - invii.keys
        inCorsoAltrove = mie
        mie.forEach { (ref, inizio) -> programmaSoglia(ref, inizio) }
        pubblica()
        if (conclusiAltrove.isNotEmpty() && vista != null) ricaricaParlanti()
    }

    /** AC-529/AC-415: the pending namings of this Registrazione, as [rifletti] does for the cards. */
    private suspend fun riflettiFrasi(mappa: Map<FraseRef, StatoComando>) {
        val mie = mappa.entries.filter { it.key.registrazioneId == registrazioneId }
            .associate { it.key.segmentoId to it.value.avviatoAlle }
        val conclusiAltrove = frasiAltrove.keys - mie.keys - inviiFrasi.keys
        frasiAltrove = mie
        mie.forEach { (segmento, inizio) -> programmaSoglia(FraseRef(registrazioneId, segmento), inizio) }
        pubblica()
        if (conclusiAltrove.isNotEmpty() && vista != null) ricaricaDopoRevisione(azzeraSelezione = false)
    }

    /** AC-412: re-publishes when [inizio] + the threshold is reached, so the card turns to 'In attesa…'. */
    private fun programmaSoglia(ref: Any, inizio: Instant) {
        if (!soglieProgrammate.add(ref to inizio)) return
        val restante = RegistrazionePresenter.SOGLIA_ATTESA_VISIBILE_MS - trascorsiMs(inizio)
        if (restante > 0) {
            scope.launch {
                delay(restante)
                pubblica()
            }
        }
    }

    private fun trascorsiMs(inizio: Instant): Long = Duration.between(inizio, sorgenti.clock.instant()).toMillis()

    private fun attesaDi(ref: VoceRef): AttesaComando? = attesaDa(invii[ref] ?: inCorsoAltrove[ref])

    private fun attesaFraseDi(segmento: SegmentoId): AttesaComando? =
        attesaDa(inviiFrasi[segmento] ?: frasiAltrove[segmento])

    private fun attesaDa(inizio: Instant?): AttesaComando? {
        if (inizio == null) return null
        return if (trascorsiMs(inizio) >= RegistrazionePresenter.SOGLIA_ATTESA_VISIBILE_MS) {
            AttesaComando.IN_ATTESA
        } else {
            AttesaComando.IN_CORSO
        }
    }

    /**
     * AC-411: the card goes pending AT ONCE (published before the command even starts — a second click
     * finds its actions disabled); AC-414: the outcome is shown like any command's; AC-413: a cancelled
     * one (`null`) restores the card with no message. `finally`: never a card stuck in 'in corso'.
     */
    private fun invia(voceId: VoceId, comando: (VoceRef) -> ComandoVoce) {
        // AC-454: 'Conferma'/'altri ▾'/'nuovo…'/'salta'/'cambia' send no command while read-only —
        // `azioniAbilitate` already folds in `soloLettura` (CartaVoce, built by pannelloDi below).
        val carta = cartaDi(voceId)?.takeIf { it.azioniAbilitate } ?: return
        val ref = VoceRef(registrazioneId, voceId)
        val inizio = sorgenti.clock.instant()
        invii[ref] = inizio
        erroriCarta.remove(voceId)
        programmaSoglia(ref, inizio)
        pubblica()
        scope.launch {
            try {
                when (val esito = withContext(io) { sorgenti.comandi.esegui(comando(ref)) }) {
                    null -> Unit
                    is Esito.Ok -> ricaricaParlanti()
                    is Esito.Errore -> erroriCarta[voceId] = messaggioPer(esito.errore)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
            ) {
                erroriCarta[voceId] = MESSAGGIO_ERRORE_GENERICO
            } finally {
                invii.remove(ref)
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
    fun annulla(voceId: VoceId) = sorgenti.comandi.annulla(VoceRef(registrazioneId, voceId))

    fun chiudiErrore(voceId: VoceId) {
        erroriCarta.remove(voceId)
        pubblica()
    }

    // --- selection + Revisione (AC-209..211, AC-216, AC-404) ----------------------------------------

    /** AC-209: the selection stays inside ONE Voce — a Segmento of another Voce starts a new selection. */
    fun selezionaSegmento(id: SegmentoId) {
        val v = vista ?: return
        val voce = v.segmenti.find { it.segmentoId == id }?.voceId ?: return
        selezione = when {
            voceDellaSelezione(v) != voce -> setOf(id)
            id in selezione -> selezione - id
            else -> selezione + id
        }
        pubblica()
    }

    fun deseleziona() {
        selezione = emptySet()
        pubblica()
    }

    /** AC-210: only a proper subset of the Voce can be split off (INV-10). */
    fun dividiVoce() {
        val barra = vista?.let(::barraDi)?.takeIf { it.dividiAbilitato && it.abilitata } ?: return
        val segmenti = selezione
        eseguiRevisione {
            val esito = sorgenti.dividi(DividiVoce(registrazioneId, barra.voceId, segmenti))
            RisultatoRevisione(esito, esito is Esito.Ok)
        }
    }

    /** AC-211: RiassegnaSegmento per Segmento, to [destinazione] or (`null`) ONE new Voce for the whole selection. */
    fun riassegnaA(destinazione: VoceId?) {
        val v = vista ?: return
        val barra = barraDi(v)?.takeIf { it.abilitata && it.voceId != destinazione } ?: return
        val ordinati = v.segmenti
            .filter { it.segmentoId in selezione && it.voceId == barra.voceId }
            .map { it.segmentoId }
        eseguiRevisione { riassegnaTutti(ordinati, destinazione) }
    }

    private fun riassegnaTutti(segmenti: List<SegmentoId>, destinazione: VoceId?): RisultatoRevisione {
        var verso = destinazione
        var errore: Esito.Errore? = null
        var spostati = 0
        for (segmento in segmenti) {
            val esito = sorgenti.riassegna(RiassegnaSegmento(registrazioneId, segmento, verso))
            if (esito is Esito.Errore) {
                errore = esito
                break
            }
            spostati++
            // 'nuova voce': the first Segmento opens it, the next ones follow it there (one Voce, not one each).
            if (verso == null) verso = trascritto()?.segmenti?.find { it.segmentoId == segmento }?.voceId
        }
        return RisultatoRevisione(errore ?: Esito.Ok(Unit), modificato = spostati > 0)
    }

    /** 'Unisci con ▾' on a card ([sopravvive] = that card) and the merge banner's 'Unisci' (AC-216). */
    fun unisci(sopravvive: VoceId, rimossa: VoceId) {
        if (sopravvive == rimossa) return
        eseguiRevisione {
            val esito = sorgenti.unisci(UnisciVoci(registrazioneId, sopravvive, rimossa))
            RisultatoRevisione(esito, esito is Esito.Ok)
        }
    }

    /**
     * AC-404: a Revisione error is an inline message in the transcript — the transcript and the selection
     * stay as they were (only a partly applied multi-Segmento Riassegna re-reads what did change). A
     * success re-reads the transcript and the panel (the merge banner disappears on its own, AC-216).
     * One at a time (no double submit); never blocked by a pending card command (AC-414).
     */
    private fun eseguiRevisione(blocco: () -> RisultatoRevisione) {
        // AC-454: dividiVoce/riassegnaA/unisci (the merge banner's action included) send no command
        // while read-only — the view already disables their controls, this is the presenter's own
        // backstop (unisci() in particular has no `abilitata` guard of its own to rely on).
        if (modificheBloccate || revisioneInCorso) return
        revisioneInCorso = true
        pubblica()
        scope.launch {
            try {
                val risultato = withContext(io) { blocco() }
                if (risultato.modificato) ricaricaDopoRevisione(azzeraSelezione = risultato.esito is Esito.Ok)
                (risultato.esito as? Esito.Errore)?.let { impostaErrore(messaggioPer(it.errore)) }
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
            ) {
                impostaErrore(MESSAGGIO_ERRORE_GENERICO)
            } finally {
                revisioneInCorso = false
                pubblica()
            }
        }
    }

    private suspend fun ricaricaDopoRevisione(azzeraSelezione: Boolean) {
        val nuova = withContext(io) { trascritto() } ?: return
        vista = nuova
        stato.update { d -> if (d is RegistrazioneUiStato.Dati) d.copy(segmenti = segmentiDi(nuova)) else d }
        val esistenti = nuova.segmenti.filter { it.segmentoId in selezione }
        selezione = if (azzeraSelezione || esistenti.map { it.voceId }.distinct().size > 1) {
            emptySet()
        } else {
            esistenti.map { it.segmentoId }.toSet()
        }
        proposte.clear()
        ricaricaParlanti()
    }

    // --- "Dai un nome a questa frase" + "Riassegna per somiglianza" (ADR 0019 §5/§6) ----------------

    /**
     * AC-526/AC-527/AC-529: the case decision ([passiNominaFrase], pure) then ONE `nominaFrase` through the
     * per-project [ComandiVoce] — the presenter never calls a command itself. The row goes pending at once;
     * whatever the outcome the transcript and the panel are re-read (a first step may have committed: e.g.
     * the new Voce of case (d) stays unnamed when naming it fails, NomeGiaInUso shown inline).
     */
    fun nominaFrase(obiettivo: ObiettivoNome) {
        val v = vista
        val segmento = v?.let(::barraDi)?.frase?.takeIf { it.abilitata }?.segmentoId
        val passi = segmento?.let { passiNominaFrase(it, checkNotNull(v), dati?.identificate.orEmpty(), obiettivo) }
        if (segmento == null || passi == null) return
        val inizio = sorgenti.clock.instant()
        inviiFrasi[segmento] = inizio
        programmaSoglia(FraseRef(registrazioneId, segmento), inizio)
        pubblica()
        scope.launch {
            try {
                val esito = withContext(io) { sorgenti.comandi.nominaFrase(registrazioneId, segmento, passi) }
                ricaricaDopoRevisione(azzeraSelezione = esito is Esito.Ok)
                (esito as? Esito.Errore)?.let { impostaErrore(messaggioPer(it.errore)) }
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
            ) {
                impostaErrore(MESSAGGIO_ERRORE_GENERICO)
            } finally {
                inviiFrasi.remove(segmento)
                pubblica()
            }
        }
    }

    /** AC-529: 'Annulla' on a naming past the threshold — the steps not yet run are skipped. */
    fun annullaFrase(segmento: SegmentoId) = sorgenti.comandi.annullaFrase(FraseRef(registrazioneId, segmento))

    /** AC-528: 'Togli conferma' → `ConfermaSegmento(false)` on the ONE selected, confirmed Segmento. */
    fun togliConferma() {
        val conferma = sorgenti.confermaSegmento ?: return
        val menu = vista?.let(::barraDi)?.frase?.takeIf { it.abilitata && it.confermato } ?: return
        eseguiRevisione {
            val esito = conferma(ConfermaSegmento(registrazioneId, menu.segmentoId, confermato = false))
            RisultatoRevisione(esito, esito is Esito.Ok)
        }
    }

    fun calcolaSomiglianza() = somiglianza?.calcola()

    fun applicaSomiglianza() = somiglianza?.applica()

    fun annullaSomiglianza() = somiglianza?.annulla()

    private fun impostaErrore(messaggio: String) =
        stato.update { d -> if (d is RegistrazioneUiStato.Dati) d.copy(errore = messaggio) else d }

    // --- state building ----------------------------------------------------------------------------

    private fun cartaDi(voceId: VoceId): CartaVoce? =
        (stato.value as? RegistrazioneUiStato.Dati)?.pannello?.carte?.find { it.voceId == voceId }

    private fun voceDellaSelezione(v: TrascrittoView): VoceId? =
        v.segmenti.firstOrNull { it.segmentoId in selezione }?.voceId

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
                            attesaFrase = attesaFraseDi(riga.segmentoId),
                        )
                    },
                    pannello = pannelloDi(v, d.audioDisponibile),
                    selezione = selezione,
                    barraSelezione = barraDi(v),
                    // AC-454: the panel's own third banner line — only while the base presenter's
                    // soloLettura (AC-452) is set; the base presenter never writes this field itself.
                    bannerRitrascrizionePannello = if (soloLettura) MESSAGGIO_RITRASCRIZIONE_PERSA else null,
                )
            }
        }
        somiglianza?.controllaSolaLettura(soloLettura)
    }

    private fun etichetta(v: TrascrittoView, voceId: VoceId): String = etichettaDiVoce(v, voceId, nomeDi(voceId))

    private fun opzioni(v: TrascrittoView): List<OpzioneVoce> =
        v.voci.map { OpzioneVoce(it.voceId, etichetta(v, it.voceId)) }

    private fun pannelloDi(v: TrascrittoView, audioDisponibile: Boolean): PannelloVoci {
        val tutte = opzioni(v)
        return PannelloVoci(
            carte = v.voci.map { voce ->
                CartaVoce(
                    voceId = voce.voceId,
                    titolo = voce.etichetta,
                    contenuto = contenutoDi(voce.voceId),
                    inCorso = attesaDi(VoceRef(registrazioneId, voce.voceId)),
                    errore = erroriCarta[voce.voceId],
                    altreVoci = tutte.filter { it.voceId != voce.voceId },
                    // AC-454: 'Conferma'/'altri ▾'/'nuovo…'/'salta'/'cambia' disabled while read-only —
                    // and while a similarity run is open (AC-531/AC-545).
                    soloLettura = modificheBloccate,
                )
            },
            parlantiAttivi = dati?.attivi.orEmpty(),
            unioni = dati?.unioni.orEmpty(),
            estrattiDisponibili = audioDisponibile,
            // AC-454: the merge banner's action disabled too — '▶ estratto' stays governed only by
            // estrattiDisponibili (audio availability), untouched by soloLettura.
            unioneAbilitata = !revisioneInCorso && !modificheBloccate,
            somiglianza = somiglianza?.pannello(
                riferimentiDi(v, dati?.identificate.orEmpty(), dati?.attivi.orEmpty()),
                bloccato = soloLettura || comandiPendenti || revisioneInCorso || dati == null,
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

    private fun barraDi(v: TrascrittoView): BarraSelezione? {
        val voceId = voceDellaSelezione(v) ?: return null
        val interaVoce = v.segmenti.filter { it.voceId == voceId }.all { it.segmentoId in selezione }
        return BarraSelezione(
            voceId = voceId,
            etichetta = etichetta(v, voceId),
            numeroSegmenti = selezione.size,
            dividiAbilitato = !interaVoce,
            spiegazioneDividi = if (interaVoce) SPIEGAZIONE_DIVIDI_INTERA_VOCE else null,
            destinazioni = opzioni(v).filter { it.voceId != voceId },
            // AC-454: 'Riassegna a ▾'/'Dividi voce' disabled while read-only (and during a similarity run).
            abilitata = !revisioneInCorso && !modificheBloccate,
            frase = if (selezione.size == 1) menuFrase(v, selezione.single()) else null,
        )
    }

    /** AC-526/AC-528/AC-529: the naming menu of the ONE selected Segmento. */
    private fun menuFrase(v: TrascrittoView, segmento: SegmentoId) = MenuFrase(
        segmentoId = segmento,
        parlanti = dati?.attivi.orEmpty(),
        confermato = v.segmenti.find { it.segmentoId == segmento }?.confermato == true,
        abilitata = !revisioneInCorso && !modificheBloccate && dati != null && attesaFraseDi(segmento) == null,
        togliConfermaDisponibile = sorgenti.confermaSegmento != null,
    )
}

/** AC-405: the Nome if attributed, else trascritto-view's own "Voce n". */
internal fun etichettaDiVoce(vista: TrascrittoView, voceId: VoceId, nome: String?): String =
    nome ?: vista.voci.find { it.voceId == voceId }?.etichetta ?: "Voce ${voceId.numero}"
