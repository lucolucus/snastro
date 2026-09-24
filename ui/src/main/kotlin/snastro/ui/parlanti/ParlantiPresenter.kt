package snastro.ui.parlanti

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import snastro.kernel.Esito
import snastro.kernel.EstrattoRef
import snastro.kernel.ParlanteId
import snastro.parlanti.applicazione.comandi.EliminaParlante
import snastro.parlanti.applicazione.comandi.PromuoviParlante
import snastro.parlanti.applicazione.comandi.RinominaParlante
import snastro.parlanti.applicazione.eventi.TipoParlanteVista
import snastro.parlanti.applicazione.letture.ParlanteDelProgetto
import snastro.parlanti.applicazione.letture.StatoParlanteVista
import snastro.ui.AggiornamentiVista
import snastro.ui.lettore.LettoreAudio
import snastro.ui.testi.MESSAGGIO_ERRORE_CARICAMENTO_PARLANTI
import snastro.ui.testi.MESSAGGIO_ERRORE_GENERICO
import snastro.ui.testi.messaggioPer

/**
 * State holder of S4 · Parlanti del Progetto (RC-2, thin UI): reads `parlanti-del-progetto`
 * (AC-175/AC-176/AC-220..222) and triggers `RinominaParlante`/`PromuoviParlante`/`EliminaParlante`
 * (AC-223..226). Refreshes on [AggiornamentiVista] (R15) or after a successful
 * `rinomina`/`promuovi`/`confermaEliminazione` (never after a failure — H1: "nulla cambia" beyond the
 * inline message). A refresh MERGES the freshly-read rows into the current [ParlantiUiStato] instead
 * of rebuilding it from scratch (same fix as `RegistrazioniPresenter`'s M1), so a refresh landing
 * mid-flight of an unrelated in-progress operation never wipes a row's `operazioneInCorso`/
 * `erroreRiga`/`confermaEliminazione`; a generation counter drops a [carica] result that resolves
 * after a newer one already has (out-of-order completion). The INITIAL load failing (no rows known
 * yet) is a distinct [ParlantiUiStato.Errore] with a retry action, never the misleading AC-220 empty
 * message.
 *
 * Depends on `applicazione` through PLAIN FUNCTION TYPES ([parlanti]/[rinominaParlante]/
 * [promuoviParlante]/[eliminaParlante]) rather than the concrete read-model/service classes, exactly
 * like `RegistrazioniPresenter`: `:avvio` binds the real shape (`ParlantiDelProgetto::parlanti`
 * already applied to the open Progetto's id, `<Comando>Servizio::esegui`) — CR-1(b).
 */
@Suppress("LongParameterList", "TooManyFunctions") // one parameter per collaborator; one method per user action
class ParlantiPresenter(
    private val scope: CoroutineScope,
    io: CoroutineDispatcher,
    private val parlanti: () -> List<ParlanteDelProgetto>,
    private val rinominaParlante: (RinominaParlante) -> Esito<Unit>,
    private val promuoviParlante: (PromuoviParlante) -> Esito<Unit>,
    private val eliminaParlante: (EliminaParlante) -> Esito<Unit>,
    private val lettore: LettoreAudio,
    private val aggiornamenti: AggiornamentiVista,
) {
    private val io: CoroutineDispatcher = io

    private val _stato = MutableStateFlow<ParlantiUiStato>(ParlantiUiStato.Caricamento)
    val stato: StateFlow<ParlantiUiStato> = _stato.asStateFlow()

    // AC-226: the excerpt behind each row's '▶', kept OUT of ParlantiUiStato (the view only needs
    // enabled/disabled) and refreshed on every [carica] alongside the rows themselves.
    private var estrattiPerParlante: Map<ParlanteId, EstrattoRef> = emptyMap()

    // M1: bumped at the START of every `carica()`; a result is applied only if this is still the
    // latest call when it resolves — drops a stale (superseded) result instead of overwriting a
    // fresher one on out-of-order completion. Mutated only from `scope`'s own (confined) dispatcher.
    private var generazioneCaricamento = 0

    init {
        scope.launch { carica() }
        scope.launch { aggiornamenti.cambiamenti.collect { carica() } } // R15
    }

    private suspend fun carica() {
        val generazione = ++generazioneCaricamento
        try {
            val viste = withContext(io) { parlanti() }
            if (generazione == generazioneCaricamento) aggiornaConNuoveViste(viste)
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
        ) {
            if (generazione == generazioneCaricamento) segnalaErroreDiCaricamento()
        }
    }

    /** M1: merges freshly-read [viste] into the CURRENT [ParlantiUiStato.Dati] — each row's
     * `operazioneInCorso`/`erroreRiga`/`confermaEliminazione` survives; only their OWNING action
     * ever clears them (see the success branch of [suRiga]). */
    private fun aggiornaConNuoveViste(viste: List<ParlanteDelProgetto>) {
        estrattiPerParlante = viste.mapNotNull { v -> v.estratto?.let { v.parlanteId to it } }.toMap()
        val precedente = _stato.value as? ParlantiUiStato.Dati
        val attive = viste.filter { it.statoParlante == StatoParlanteVista.ATTIVO }
        val (ricorrenti, occasionali) = attive.partition { it.tipoParlante == TipoParlanteVista.RICORRENTE }
        _stato.value = ParlantiUiStato.Dati(
            ricorrenti = ricorrenti.map { riga(it, vecchiaRiga(precedente, it.parlanteId)) },
            occasionali = occasionali.map { riga(it, vecchiaRiga(precedente, it.parlanteId)) },
            eliminati = viste.filter { it.statoParlante == StatoParlanteVista.ELIMINATO }
                .map { RigaParlanteEliminato(it.parlanteId, it.nome) },
            errore = precedente?.errore,
        )
    }

    private fun vecchiaRiga(precedente: ParlantiUiStato.Dati?, id: ParlanteId): RigaParlante? =
        precedente?.ricorrenti?.find { it.parlanteId == id } ?: precedente?.occasionali?.find { it.parlanteId == id }

    private fun riga(v: ParlanteDelProgetto, precedente: RigaParlante?) = RigaParlante(
        parlanteId = v.parlanteId,
        nome = v.nome,
        tipoParlante = v.tipoParlante,
        numImpronte = v.numImpronte,
        numRegistrazioni = v.numRegistrazioni,
        ultimaApparizione = v.ultimaApparizione,
        riproduzioneAbilitata = v.estratto != null,
        operazioneInCorso = precedente?.operazioneInCorso ?: false,
        erroreRiga = precedente?.erroreRiga,
        confermaEliminazione = precedente?.confermaEliminazione ?: false,
    )

    /** The INITIAL load (no [ParlantiUiStato.Dati] known yet) fails into a distinct
     * [ParlantiUiStato.Errore] with a retry action — never the misleading AC-220 empty-list message.
     * A later refresh failure (already showing [ParlantiUiStato.Dati]) keeps the known rows (H1),
     * only [ParlantiUiStato.Dati.errore] changes. */
    private fun segnalaErroreDiCaricamento() {
        when (val attuale = _stato.value) {
            is ParlantiUiStato.Dati -> _stato.value = attuale.copy(errore = MESSAGGIO_ERRORE_GENERICO)
            ParlantiUiStato.Caricamento, is ParlantiUiStato.Errore ->
                _stato.value = ParlantiUiStato.Errore(MESSAGGIO_ERRORE_CARICAMENTO_PARLANTI)
        }
    }

    /** Retries the initial load after [ParlantiUiStato.Errore]. */
    fun riprova() {
        scope.launch { carica() }
    }

    /** AC-223: renames the Parlante from its row's inline Nome field. */
    fun rinomina(id: ParlanteId, nuovoNome: String) =
        suRiga(id) { withContext(io) { rinominaParlante(RinominaParlante(id, nuovoNome)) } }

    /** AC-224: only shown for an `occasionale` row (the view's own decision, exactly like
     * `RegistrazioniPresenter`'s `apribile`); no rename bundled here (out of this block's AC scope). */
    fun promuovi(id: ParlanteId) =
        suRiga(id) { withContext(io) { promuoviParlante(PromuoviParlante(id, nome = null)) } }

    /** AC-225: opens the row's inline confirmation — no command sent yet. */
    fun chiediConfermaEliminazione(id: ParlanteId) = aggiornaRiga(id) { it.copy(confermaEliminazione = true) }

    /** AC-225: "annullare non cambia nulla" — closes the confirmation, nothing else changes. */
    fun annullaEliminazione(id: ParlanteId) = aggiornaRiga(id) { it.copy(confermaEliminazione = false) }

    /** AC-225: the confirmed tombstone. */
    fun confermaEliminazione(id: ParlanteId) =
        suRiga(id) { withContext(io) { eliminaParlante(EliminaParlante(id)) } }

    private fun suRiga(id: ParlanteId, operazione: suspend () -> Esito<Unit>) {
        val riga = trovaRiga(id) ?: return
        if (riga.operazioneInCorso) return // M3
        aggiornaRiga(id) { it.copy(operazioneInCorso = true, erroreRiga = null) }
        scope.launch {
            try {
                when (val esito = operazione()) {
                    is Esito.Ok -> {
                        carica() // M1: merges the refreshed list, preserving this row's flags until reset below
                        aggiornaRiga(id) {
                            it.copy(operazioneInCorso = false, erroreRiga = null, confermaEliminazione = false)
                        }
                    }
                    is Esito.Errore -> {
                        val messaggio = messaggioPer(esito.errore)
                        aggiornaRiga(id) { it.copy(operazioneInCorso = false, erroreRiga = messaggio) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
            ) {
                aggiornaRiga(id) { it.copy(operazioneInCorso = false, erroreRiga = MESSAGGIO_ERRORE_GENERICO) }
            }
        }
    }

    /** AC-226: plays one of [id]'s `ImprontaVocale` excerpts; a no-op when [RigaParlante.riproduzioneAbilitata]
     * is false (the view already disables '▶' for that case, this is the defensive backstop). H2:
     * a throwing [LettoreAudio] is mapped to an inline row error, never left to kill this coroutine. */
    fun riproduci(id: ParlanteId) {
        val estratto = estrattiPerParlante[id] ?: return
        scope.launch {
            try {
                withContext(io) { lettore.riproduciEstratto(estratto) }
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
            ) {
                aggiornaRiga(id) { it.copy(erroreRiga = MESSAGGIO_ERRORE_GENERICO) }
            }
        }
    }

    /** H1: dismisses the current `erroreRiga` of [id], if any. */
    fun chiudiErroreRiga(id: ParlanteId) = aggiornaRiga(id) { it.copy(erroreRiga = null) }

    /** H1: dismisses the current list-level `errore` (refresh failure), if any. */
    fun chiudiErrore() = aggiornaDati { it.copy(errore = null) }

    private fun trovaRiga(id: ParlanteId): RigaParlante? {
        val dati = _stato.value as? ParlantiUiStato.Dati ?: return null
        return dati.ricorrenti.find { it.parlanteId == id } ?: dati.occasionali.find { it.parlanteId == id }
    }

    private fun aggiornaDati(f: (ParlantiUiStato.Dati) -> ParlantiUiStato.Dati) {
        val attuale = _stato.value
        if (attuale is ParlantiUiStato.Dati) _stato.value = f(attuale)
    }

    private fun aggiornaRiga(id: ParlanteId, f: (RigaParlante) -> RigaParlante) = aggiornaDati { dati ->
        dati.copy(
            ricorrenti = dati.ricorrenti.map { if (it.parlanteId == id) f(it) else it },
            occasionali = dati.occasionali.map { if (it.parlanteId == id) f(it) else it },
        )
    }

    val azioni: AzioniParlanti = AzioniParlanti(
        rinomina = ::rinomina,
        promuovi = ::promuovi,
        riproduci = ::riproduci,
        chiediConfermaEliminazione = ::chiediConfermaEliminazione,
        annullaEliminazione = ::annullaEliminazione,
        confermaEliminazione = ::confermaEliminazione,
        chiudiErroreRiga = ::chiudiErroreRiga,
        chiudiErrore = ::chiudiErrore,
        riprova = ::riprova,
    )
}
