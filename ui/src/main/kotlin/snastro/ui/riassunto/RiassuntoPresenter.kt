package snastro.ui.riassunto

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import snastro.kernel.Esito
import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.sintesi.applicazione.letture.DisponibilitaVista
import snastro.sintesi.applicazione.letture.ImpostazioniSintesiVista
import snastro.sintesi.applicazione.letture.RiassuntoVista
import snastro.sintesi.applicazione.letture.RichiestaApertaVista
import snastro.sintesi.applicazione.letture.StatoModelloVista
import snastro.supporto.catturaNonFatale
import snastro.ui.AggiornamentiVista
import snastro.ui.coda.PosizioniCoda
import snastro.ui.modelli.ServizioModelli
import snastro.ui.testi.contatoreArgomento
import snastro.ui.testi.erroreArgomentoTroppoLungo
import snastro.ui.testi.erroreLunghezzaMassima
import snastro.ui.testi.etichettaScaricaModello
import snastro.ui.testi.intestazioneIncontro
import snastro.ui.testi.messaggioDownloadFallito
import snastro.ui.testi.messaggioFallimento
import snastro.ui.testi.messaggioModelloInDownload
import snastro.ui.testi.messaggioModelloNonInstallato
import snastro.ui.testi.messaggioNonDisponibile
import snastro.ui.testi.messaggioPer
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
 * [posizioni]'s own map and filter [aggiornamenti]). [idModelloLinguistico]/
 * [dimensioneModelloLinguisticoByte]/[limiteCaratteriArgomento] are the composition's OWN single
 * source (the app's model catalogue, `Argomento.MASSIMO_CARATTERI`) threaded in by `:avvio` — never a
 * `:ui`-local literal duplicating them (pre-release findings #149/#148/#177: the old duplicates drift
 * from the catalogue/domain bound they mirror).
 *
 * Re-reads (What to do): on every [AggiornamentiVista.cambiamenti] of this Registrazione (a
 * `Riassumi`/`ModificaLunghezzaMassimaRiassunto`/policy commit, or a Ritrascrivi replacement — AC-S135
 * falls out of this alone, no special-cased transition) and on every [ServizioModelli.statoFacoltativi]
 * tick (the model download's own progress, a UI-only signal with no Sintesi event of its own).
 *
 * Pre-release finding #151 (rework, MED): those two triggers plus the initial load used to each
 * `launch` their OWN `ricarica()` independently — three concurrent readers, none cancelling another,
 * so whichever happened to finish LAST won even when it had read the STALEST data (a race the
 * dev-architecture's own `collectLatest`/single-flight idiom exists for). The `init` block below
 * merges every trigger into ONE flow collected with `collectLatest`: a new trigger always cancels
 * whatever `ricarica()` an older one still has in flight before starting its own.
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
    /** The optional catalogue entry 'Scarica il modello' downloads — the app's ONE source (`:avvio`). */
    private val idModelloLinguistico: String,
    /** Its `dimensioneByte` — what the `SpazioInsufficiente` download-failure message shows (finding #149). */
    private val dimensioneModelloLinguisticoByte: Long,
    /** Mirrors `:sintesi:dominio Argomento.MASSIMO_CARATTERI`, injected rather than duplicated as a
     * `:ui`-local literal (finding #148 — CR-1(b) still keeps the VO itself out of `:ui`). */
    private val limiteCaratteriArgomento: Int,
    /** AC-I81: switches the page to another Parte of the Incontro, staying on the Riassunto tab (`schermata-parte`). */
    private val vaiAllaParte: (RegistrazioneId) -> Unit,
    /** AC-I81: plays [RegistrazioneId] from a millisecond (the shared `LettoreAudio`, bound by `:avvio`). */
    private val riproduciDa: (RegistrazioneId, Long) -> Unit,
) {
    private val io: CoroutineDispatcher = io

    private val _stato = MutableStateFlow<RiassuntoUiStato>(RiassuntoUiStato.Caricamento)
    val stato: StateFlow<RiassuntoUiStato> = _stato.asStateFlow()

    // AC-S128: a second click while a `Riassumi` is still in flight is ignored.
    private var invioInCorso = false

    // Finding #157 (rework, MED): the same "in-flight" guard as [invioInCorso], for the model
    // download button — a double click used to start two 6 GB downloads.
    private var scaricaModelloInCorso = false

    // Finding #153 (rework, LOW): a Salva in flight likewise guards against a double click.
    private var salvaLunghezzaMassimaInCorso = false

    // AC-S128/S134/S139: the field's own local edit — `null` until the user types; reset after a
    // successful Riassumi so the NEXT reload's `argomentoPrecompilato` prefills it again.
    private var argomentoToccato = false
    private var moduloAperto = false
    private var argomentoAttuale = ""

    // AC-S138: `null` = show the freshly read value plainly; non-null while the editor/its "Salvato"
    // confirmation is open — a background `ricarica()` must never overwrite either.
    private var lunghezzaMassimaLocale: LunghezzaMassimaUiStato? = null
    private var ultimaLunghezzaMassimaLetta = 0
    private var minimoLunghezza = 0
    private var massimoLunghezza = 0

    // AC-S131: the running Riassunto's own start instant, `null` outside `in_corso`. A `StateFlow`
    // (not a plain `var`, finding #159) so the ticker below can `collectLatest` it: the `while(true)`
    // tick loop then only actually RUNS while a Riassunto is `in_corso`, instead of waking up every
    // second for this presenter's whole lifetime regardless of state.
    private val _avviatoIl = MutableStateFlow<Instant?>(null)

    private var messaggioErrore: String? = null

    init {
        // Finding #151: ONE collector, single-flight over every reload trigger — see the class KDoc.
        scope.launch {
            merge(
                flowOf(Unit),
                aggiornamenti.cambiamenti
                    .filter { it.registrazioneId == null || it.registrazioneId == registrazioneId }
                    .map {},
                // v1 has exactly one optional catalogue entry (the Sintesi LLM, ADR 0025) — no need to
                // filter by id, any tick means THIS model's own state may have moved (frugality rung 6).
                servizioModelli.statoFacoltativi.map {},
            ).collectLatest { ricarica() }
        }
        scope.launch {
            _avviatoIl.collectLatest { istante ->
                if (istante != null) {
                    while (true) {
                        delay(PASSO_TICK_MS)
                        val trascorso = trascorsoMs(istante)
                        // Finding #159 rework 2 (regression, MED): a tick that resumes just before
                        // `collectLatest` processes a NEW `_avviatoIl` (e.g. a reload that completes
                        // right at the tick boundary) must not stamp InCorso onto a Dati that has
                        // already moved on to `pronto`/`fallito` — both `richiesta` and `avviatoIl`
                        // read FRESH here (after the clock read above), never the `istante` this
                        // loop iteration merely captured.
                        aggiornaDati {
                            if (it.richiesta is RichiestaUi.InCorso && _avviatoIl.value == istante) {
                                it.copy(richiesta = RichiestaUi.InCorso(trascorso))
                            } else {
                                it
                            }
                        }
                    }
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
        // Finding #154 (rework, MED): a fresh reload always starts clean — an inline error only a
        // RACE produced (e.g. `RiassuntoGiaAperto`) must not outlive the state it was reported on. A
        // `riassumi()` failure re-applies its OWN message right after calling this (below), so it
        // still shows for THAT render; the NEXT independent reload clears it again here.
        messaggioErrore = null
        _stato.value = costruisci(v, pos)
    }

    private fun costruisci(v: RiassuntoVista, pos: PosizioniCoda): RiassuntoUiStato.Dati = RiassuntoUiStato.Dati(
        modello = modelloUi(v.modello),
        richiesta = richiestaUi(v.richiestaAperta, v.incontroId, pos),
        fallimentoTesto = v.ultimoFallimento?.let { messaggioFallimento(it.motivo.codice) },
        nonDisponibileTesto = (v.disponibilita as? DisponibilitaVista.NonDisponibile)?.let {
            messaggioNonDisponibile(it.motivo, v.numParti)
        },
        contenuto = v.mostrato?.let { contenutoUi(it, v.numParti) },
        argomento = argomentoUiDi(argomentoAttuale),
        lunghezzaMassima = lunghezzaMassimaLocale ?: LunghezzaMassimaUiStato.Testo(ultimaLunghezzaMassimaLetta),
        messaggioErrore = messaggioErrore,
        moduloAperto = moduloAperto,
        intestazioneTesto = if (v.numParti > 1) intestazioneIncontro(v.numParti) else null,
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
        is StatoModelloVista.DownloadFallito -> ModelloUi.DownloadFallito(
            messaggioDownloadFallito(m.motivo, dimensioneModelloLinguisticoByte),
        )
        StatoModelloVista.Installato -> ModelloUi.Installato
    }

    private fun richiestaUi(r: RichiestaApertaVista?, incontroId: IncontroId, pos: PosizioniCoda): RichiestaUi? {
        _avviatoIl.value = (r as? RichiestaApertaVista.InCorso)?.avviatoIl
        return when (r) {
            null -> null
            is RichiestaApertaVista.InAttesa -> RichiestaUi.InAttesa(pos.riassunti[incontroId])
            is RichiestaApertaVista.InCorso -> RichiestaUi.InCorso(trascorsoMs(r.avviatoIl))
        }
    }

    private fun trascorsoMs(avviatoIl: Instant): Long =
        Duration.between(avviatoIl, clock.instant()).toMillis().coerceAtLeast(0)

    private fun argomentoUiDi(valore: String): ArgomentoUiStato {
        // Finding #153 (rework, LOW): the counter/errore now mirror the DOMAIN's own trimming
        // (`Argomento.di` trims before checking `MASSIMO_CARATTERI`) — trailing/leading whitespace no
        // longer inflates the count or blocks submission the server would in fact accept.
        val ripulito = valore.trim()
        return ArgomentoUiStato(
            valore = valore,
            contatore = contatoreArgomento(ripulito.length, limiteCaratteriArgomento),
            errore = if (ripulito.length > limiteCaratteriArgomento) {
                erroreArgomentoTroppoLungo(limiteCaratteriArgomento)
            } else {
                null
            },
        )
    }

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
        if (invioInCorso || argomentoAttuale.trim().length > limiteCaratteriArgomento) return
        invioInCorso = true
        val argomento = argomentoAttuale
        scope.launch {
            var erroreDaMostrare: String? = null
            try {
                when (val esito = withContext(io) { riassumiCmd(argomento) }) {
                    is Esito.Ok -> {
                        argomentoToccato = false
                        moduloAperto = false
                    }
                    is Esito.Errore -> erroreDaMostrare = messaggioPer(esito.errore)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (
                // Finding #159 (rework, LOW): a thrown `riassumiCmd` used to leave [invioInCorso] stuck
                // `true` forever (no `finally`) — same H2-style guard as [scaricaModello] below.
                @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
            ) {
                // Nothing to show beyond the next reload's own state — see `scaricaModello`'s KDoc.
            } finally {
                invioInCorso = false
            }
            ricarica()
            // Applied AFTER `ricarica()` (which just cleared it, finding #154): shows for THIS render,
            // cleared again by the next INDEPENDENT reload.
            if (erroreDaMostrare != null) {
                messaggioErrore = erroreDaMostrare
                aggiornaDati { it.copy(messaggioErrore = erroreDaMostrare) }
            }
        }
    }

    /** AC-S125/S127: "Scarica il modello"/"Riprova" both call this — blocks, run off [io] (mirrors
     * [snastro.ui.modelli.ModelliPresenter.scarica]); the reactive `statoFacoltativi` collector above
     * is what actually reflects the outcome, this call only starts it. */
    private fun scaricaModello() {
        // Finding #157 (rework, MED): no in-flight guard — a double click started two 6 GB downloads.
        if (scaricaModelloInCorso) return
        scaricaModelloInCorso = true
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
            } finally {
                scaricaModelloInCorso = false
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
        val corrente = lunghezzaMassimaLocale as? LunghezzaMassimaUiStato.Modifica
        // Finding #153 (rework, LOW): a Salva already in flight is ignored, same guard as [riassumi].
        if (salvaLunghezzaMassimaInCorso || corrente == null) return
        val n = corrente.valore.toIntOrNull()
        if (n == null || n < minimoLunghezza || n > massimoLunghezza) {
            mostraErroreLunghezzaMassima(corrente, erroreLunghezzaMassima(minimoLunghezza, massimoLunghezza))
            return
        }
        salvaLunghezzaMassimaInCorso = true
        scope.launch {
            try {
                when (val esito = withContext(io) { modificaLunghezzaMassimaCmd(n) }) {
                    is Esito.Ok -> {
                        val salvato = LunghezzaMassimaUiStato.Salvato(n)
                        imposta(salvato)
                        delay(DURATA_SALVATO_MS)
                        // Finding #153 (rework, LOW): only close the "Salvato" confirmation if the user
                        // has not since reopened the editor (`lunghezzaMassimaLocale` would then be a
                        // DIFFERENT instance) — otherwise this delayed reset closed a freshly reopened one.
                        if (lunghezzaMassimaLocale === salvato) {
                            lunghezzaMassimaLocale = null
                            ricarica()
                        }
                    }
                    // Finding #153/#159 (rework): a COMMAND failure used to always show the generic
                    // range text, hiding the actual `ErroreSintesi` (e.g. a race) it carried.
                    is Esito.Errore -> mostraErroreLunghezzaMassima(corrente, messaggioPer(esito.errore))
                }
            } finally {
                salvaLunghezzaMassimaInCorso = false
            }
        }
    }

    private fun mostraErroreLunghezzaMassima(corrente: LunghezzaMassimaUiStato.Modifica, messaggio: String) {
        imposta(corrente.copy(errore = messaggio))
    }

    private fun imposta(nuovo: LunghezzaMassimaUiStato) {
        lunghezzaMassimaLocale = nuovo
        aggiornaDati { it.copy(lunghezzaMassima = nuovo) }
    }

    /** The top bar's "Riassumi di nuovo": opens the form above the shown Riassunto. */
    private fun apriModulo() {
        moduloAperto = true
        aggiornaDati { it.copy(moduloAperto = true) }
    }

    /** "Annulla" on the opened form: closes it; the typed Argomento stays for the next opening. */
    private fun chiudiModulo() {
        moduloAperto = false
        aggiornaDati { it.copy(moduloAperto = false) }
    }

    /** AC-S138: restores the last value read from `impostazioni-sintesi`, not merely the one before "Cambia". */
    private fun annullaLunghezzaMassima() {
        lunghezzaMassimaLocale = null
        aggiornaDati { it.copy(lunghezzaMassima = LunghezzaMassimaUiStato.Testo(ultimaLunghezzaMassimaLetta)) }
    }

    /**
     * AC-I81: a Fonte chip of [parte]: the audio plays from [daMs] (off the UI thread), then another Parte's page
     * is opened (the tab stays). Playing first: the switch may dispose this presenter's own scope. L194: a failing
     * playback (no audio, a port fault) never escapes the coroutine and never stops the switch to the Parte.
     */
    private fun apriFonte(parte: RegistrazioneId, daMs: Long) {
        scope.launch {
            catturaNonFatale { withContext(io) { riproduciDa(parte, daMs) } }
            if (parte != registrazioneId) vaiAllaParte(parte)
        }
    }

    val azioni: AzioniRiassunto = AzioniRiassunto(
        cambiaArgomento = ::cambiaArgomento,
        riassumi = ::riassumi,
        scaricaModello = ::scaricaModello,
        modificaLunghezzaMassima = ::modificaLunghezzaMassima,
        cambiaLunghezzaMassima = ::cambiaLunghezzaMassima,
        salvaLunghezzaMassima = ::salvaLunghezzaMassima,
        annullaLunghezzaMassima = ::annullaLunghezzaMassima,
        apriModulo = ::apriModulo,
        chiudiModulo = ::chiudiModulo,
        apriFonte = ::apriFonte,
    )
}
