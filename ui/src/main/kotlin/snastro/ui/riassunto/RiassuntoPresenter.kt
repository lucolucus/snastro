package snastro.ui.riassunto

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.sintesi.applicazione.letture.DisponibilitaVista
import snastro.sintesi.applicazione.letture.ImpostazioniSintesiVista
import snastro.sintesi.applicazione.letture.RiassuntoVista
import snastro.sintesi.applicazione.letture.RichiestaApertaVista
import snastro.sintesi.applicazione.letture.StatoModelloVista
import snastro.ui.AggiornamentiVista
import snastro.ui.coda.PosizioniCoda
import snastro.ui.modelli.ServizioModelli
import snastro.ui.testi.ERRORE_ARGOMENTO_TROPPO_LUNGO
import snastro.ui.testi.LIMITE_CARATTERI_ARGOMENTO
import snastro.ui.testi.contatoreArgomento
import snastro.ui.testi.erroreLunghezzaMassima
import snastro.ui.testi.etichettaScaricaModello
import snastro.ui.testi.messaggioDownloadFallito
import snastro.ui.testi.messaggioFallimento
import snastro.ui.testi.messaggioModelloInDownload
import snastro.ui.testi.messaggioModelloNonInstallato
import snastro.ui.testi.messaggioNonDisponibile
import snastro.ui.testi.messaggioPer
import snastro.ui.testi.testoInCoda
import snastro.ui.testi.testoInCorso
import java.time.Clock
import java.time.Duration
import java.time.Instant

private const val PASSO_TICK_MS = 1_000L
private const val DURATA_SALVATO_MS = 2_000L

/**
 * State holder of S3's Riassunto tab (RC-2, thin UI; AC-S125..S139) — joins `riassunto-vista`
 * ([vista]) with `impostazioni-sintesi` ([impostazioni]) and the shared queue's own position
 * ([posizioni], ADR 0023 §4). Every collaborator is a PLAIN FUNCTION TYPE or the smallest port
 * needed (dev-architecture `#presenter`): [riassumiCmd]/[modificaLunghezzaMassimaCmd] are already
 * bound to this Registrazione/the open Progetto by the caller, so this presenter never needs a
 * `RegistrazioneId`/`ProgettoId`/`RiassuntoId` of its own beyond [registrazioneId] (kept only to key
 * [posizioni]'s own map and filter [aggiornamenti]).
 *
 * Re-reads (What to do): on every [AggiornamentiVista.cambiamenti] of this Registrazione (a
 * `Riassumi`/`ModificaLunghezzaMassimaRiassunto`/policy commit, or a Ritrascrivi replacement — AC-S135
 * falls out of this alone, no special-cased transition) and on every [ServizioModelli.statoFacoltativi]
 * tick (the model download's own progress, a UI-only signal with no Sintesi event of its own).
 *
 * Local edit state ([argomentoToccato]/[lunghezzaMassimaLocale]) survives a background `ricarica()`
 * that a Cambiamento/statoFacoltativi tick can trigger while the user is mid-edit — the same "never
 * clobber an in-flight edit" concern [snastro.ui.registrazione.RegistrazionePresenter] already solves
 * for its own selection/panel state.
 */
@Suppress("LongParameterList", "TooManyFunctions") // one parameter per collaborator; one method per user action
class RiassuntoPresenter(
    private val scope: CoroutineScope,
    io: CoroutineDispatcher,
    private val registrazioneId: RegistrazioneId,
    private val vista: () -> RiassuntoVista?,
    private val impostazioni: () -> ImpostazioniSintesiVista,
    private val posizioni: () -> PosizioniCoda,
    private val riassumiCmd: (String?) -> Esito<Unit>,
    private val modificaLunghezzaMassimaCmd: (Int) -> Esito<Unit>,
    private val servizioModelli: ServizioModelli,
    aggiornamenti: AggiornamentiVista,
    private val clock: Clock,
    /** The optional catalogue entry 'Scarica il modello' downloads — injected by the composition (avvio-sintesi). */
    private val idModelloLinguistico: String = ID_MODELLO_LINGUISTICO,
) {
    private val io: CoroutineDispatcher = io

    private val _stato = MutableStateFlow<RiassuntoUiStato>(RiassuntoUiStato.Caricamento)
    val stato: StateFlow<RiassuntoUiStato> = _stato.asStateFlow()

    // AC-S128: a second click while a `Riassumi` is still in flight is ignored.
    private var invioInCorso = false

    // AC-S128/S134/S139: the field's own local edit — `null` until the user types; reset after a
    // successful Riassumi so the NEXT reload's `argomentoPrecompilato` prefills it again.
    private var argomentoToccato = false
    private var argomentoAttuale = ""

    // AC-S138: `null` = show the freshly read value plainly; non-null while the editor/its "Salvato"
    // confirmation is open — a background `ricarica()` must never overwrite either.
    private var lunghezzaMassimaLocale: LunghezzaMassimaUiStato? = null
    private var ultimaLunghezzaMassimaLetta = 0
    private var minimoLunghezza = 0
    private var massimoLunghezza = 0

    // AC-S131: the running Riassunto's own start instant, `null` outside `in_corso` — the ticker below
    // reads it every second rather than re-querying `vista()` just to advance a clock.
    private var avviatoIl: Instant? = null

    private var messaggioErrore: String? = null

    init {
        scope.launch { ricarica() }
        scope.launch {
            aggiornamenti.cambiamenti.collect { c ->
                if (c.registrazioneId == null || c.registrazioneId == registrazioneId) ricarica()
            }
        }
        // v1 has exactly one optional catalogue entry (the Sintesi LLM, ADR 0025) — no need to filter
        // by id, any tick means THIS model's own state may have moved (frugality rung 6).
        scope.launch { servizioModelli.statoFacoltativi.collect { ricarica() } }
        scope.launch {
            while (true) {
                delay(PASSO_TICK_MS)
                avviatoIl?.let { istante ->
                    aggiornaDati { it.copy(richiesta = RichiestaUi.InCorso(testoInCorso(trascorsoMs(istante)))) }
                }
            }
        }
    }

    private suspend fun ricarica() {
        val v = withContext(io) { vista() } ?: return
        val imp = withContext(io) { impostazioni() }
        val pos = withContext(io) { posizioni() }
        ultimaLunghezzaMassimaLetta = imp.lunghezzaMassimaParole
        minimoLunghezza = imp.minimo
        massimoLunghezza = imp.massimo
        if (!argomentoToccato) argomentoAttuale = v.argomentoPrecompilato.orEmpty()
        _stato.value = costruisci(v, pos)
    }

    private fun costruisci(v: RiassuntoVista, pos: PosizioniCoda): RiassuntoUiStato.Dati = RiassuntoUiStato.Dati(
        modello = modelloUi(v.modello),
        richiesta = richiestaUi(v.richiestaAperta, pos),
        fallimentoTesto = v.ultimoFallimento?.let { messaggioFallimento(it.motivo.codice) },
        nonDisponibileTesto = (v.disponibilita as? DisponibilitaVista.NonDisponibile)?.let {
            messaggioNonDisponibile(it.motivo)
        },
        contenuto = v.mostrato?.let(::contenutoUi),
        argomento = argomentoUiDi(argomentoAttuale),
        lunghezzaMassima = lunghezzaMassimaLocale ?: LunghezzaMassimaUiStato.Testo(ultimaLunghezzaMassimaLetta),
        messaggioErrore = messaggioErrore,
    )

    private fun modelloUi(m: StatoModelloVista): ModelloUi = when (m) {
        is StatoModelloVista.NonInstallato -> ModelloUi.NonInstallato(
            messaggioModelloNonInstallato(m.dimensioneByte),
            etichettaScaricaModello(m.dimensioneByte),
        )
        is StatoModelloVista.InDownload -> ModelloUi.InDownload(
            messaggioModelloInDownload(m.scaricatiByte, m.totaliByte),
            if (m.totaliByte > 0) (m.scaricatiByte.toFloat() / m.totaliByte).coerceIn(0f, 1f) else 0f,
        )
        is StatoModelloVista.DownloadFallito -> ModelloUi.DownloadFallito(messaggioDownloadFallito(m.motivo))
        StatoModelloVista.Installato -> ModelloUi.Installato
    }

    private fun richiestaUi(r: RichiestaApertaVista?, pos: PosizioniCoda): RichiestaUi? {
        avviatoIl = (r as? RichiestaApertaVista.InCorso)?.avviatoIl
        return when (r) {
            null -> null
            is RichiestaApertaVista.InAttesa -> RichiestaUi.InAttesa(testoInCoda(pos.riassunti[registrazioneId]))
            is RichiestaApertaVista.InCorso -> RichiestaUi.InCorso(testoInCorso(trascorsoMs(r.avviatoIl)))
        }
    }

    private fun trascorsoMs(avviatoIl: Instant): Long =
        Duration.between(avviatoIl, clock.instant()).toMillis().coerceAtLeast(0)

    private fun argomentoUiDi(valore: String) = ArgomentoUiStato(
        valore = valore,
        contatore = contatoreArgomento(valore.length),
        errore = if (valore.length > LIMITE_CARATTERI_ARGOMENTO) ERRORE_ARGOMENTO_TROPPO_LUNGO else null,
    )

    private fun dati(): RiassuntoUiStato.Dati? = _stato.value as? RiassuntoUiStato.Dati

    private inline fun aggiornaDati(f: (RiassuntoUiStato.Dati) -> RiassuntoUiStato.Dati) {
        dati()?.let { _stato.value = f(it) }
    }

    /** AC-S137: local edit only — validated (and shown as an error) at submit time, like [Argomento]'s own VO. */
    private fun cambiaArgomento(testo: String) {
        argomentoToccato = true
        argomentoAttuale = testo
        aggiornaDati { it.copy(argomento = argomentoUiDi(testo)) }
    }

    /** AC-S128/S134/S139: one click sends [riassumiCmd] once; re-reads the view either way. */
    private fun riassumi() {
        if (invioInCorso || argomentoAttuale.length > LIMITE_CARATTERI_ARGOMENTO) return
        invioInCorso = true
        val argomento = argomentoAttuale
        scope.launch {
            when (val esito = withContext(io) { riassumiCmd(argomento) }) {
                is Esito.Ok -> {
                    argomentoToccato = false
                    messaggioErrore = null
                }
                is Esito.Errore -> messaggioErrore = messaggioPer(esito.errore)
            }
            invioInCorso = false
            ricarica()
        }
    }

    /** AC-S125/S127: "Scarica il modello"/"Riprova" both call this — blocks, run off [io] (mirrors
     * [snastro.ui.modelli.ModelliPresenter.scarica]); the reactive `statoFacoltativi` collector above
     * is what actually reflects the outcome, this call only starts it. */
    private fun scaricaModello() {
        scope.launch {
            try {
                withContext(io) { servizioModelli.scaricaFacoltativo(idModelloLinguistico) }
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
            ) {
                // Same guard as `ModelliPresenter.scarica` (H2-style): an unexpected throw from the port
                // must not take this presenter's collectors down with it.
                ricarica()
            }
        }
    }

    /** AC-S138: "Cambia" — opens the number field seeded with the last value read. */
    private fun modificaLunghezzaMassima() {
        imposta(LunghezzaMassimaUiStato.Modifica(ultimaLunghezzaMassimaLetta.toString(), null))
    }

    private fun cambiaLunghezzaMassima(testo: String) {
        val corrente = lunghezzaMassimaLocale as? LunghezzaMassimaUiStato.Modifica ?: return
        imposta(corrente.copy(valore = testo, errore = null))
    }

    /** AC-S138: out of [minimoLunghezza]..[massimoLunghezza] → inline error, NO command sent. */
    private fun salvaLunghezzaMassima() {
        val corrente = lunghezzaMassimaLocale as? LunghezzaMassimaUiStato.Modifica ?: return
        val n = corrente.valore.toIntOrNull()
        if (n == null || n < minimoLunghezza || n > massimoLunghezza) {
            mostraErroreLunghezzaMassima(corrente)
            return
        }
        scope.launch {
            when (withContext(io) { modificaLunghezzaMassimaCmd(n) }) {
                is Esito.Ok -> {
                    imposta(LunghezzaMassimaUiStato.Salvato(n))
                    delay(DURATA_SALVATO_MS)
                    lunghezzaMassimaLocale = null
                    ricarica()
                }
                is Esito.Errore -> mostraErroreLunghezzaMassima(corrente)
            }
        }
    }

    private fun mostraErroreLunghezzaMassima(corrente: LunghezzaMassimaUiStato.Modifica) {
        imposta(corrente.copy(errore = erroreLunghezzaMassima(minimoLunghezza, massimoLunghezza)))
    }

    private fun imposta(nuovo: LunghezzaMassimaUiStato) {
        lunghezzaMassimaLocale = nuovo
        aggiornaDati { it.copy(lunghezzaMassima = nuovo) }
    }

    /** AC-S138: restores the last value read from `impostazioni-sintesi`, not merely the one before "Cambia". */
    private fun annullaLunghezzaMassima() {
        lunghezzaMassimaLocale = null
        aggiornaDati { it.copy(lunghezzaMassima = LunghezzaMassimaUiStato.Testo(ultimaLunghezzaMassimaLetta)) }
    }

    val azioni: AzioniRiassunto = AzioniRiassunto(
        cambiaArgomento = ::cambiaArgomento,
        riassumi = ::riassumi,
        scaricaModello = ::scaricaModello,
        modificaLunghezzaMassima = ::modificaLunghezzaMassima,
        cambiaLunghezzaMassima = ::cambiaLunghezzaMassima,
        salvaLunghezzaMassima = ::salvaLunghezzaMassima,
        annullaLunghezzaMassima = ::annullaLunghezzaMassima,
    )

    private companion object {
        /** ADR 0026 §8: the ONE optional catalogue entry v1 has (the Sintesi LLM); [idModelloLinguistico]'s default. */
        const val ID_MODELLO_LINGUISTICO: String = "llm-qwen3.5-9b-q4_k_m"
    }
}
