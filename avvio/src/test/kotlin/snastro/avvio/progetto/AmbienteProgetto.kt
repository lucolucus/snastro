package snastro.avvio.progetto

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import snastro.avvio.Grafo
import snastro.avvio.coda.CodaCondivisa
import snastro.avvio.coda.FonteCoda
import snastro.avvio.documento.CollaboratoriDocumento
import snastro.avvio.orologioApp
import snastro.avvio.parlanti.AdattatoriParlanti
import snastro.avvio.parlanti.CollaboratoriParlanti
import snastro.avvio.sintesi.CollaboratoriSintesi
import snastro.avvio.trascrizione.AdattatoriMl
import snastro.avvio.trascrizione.CollaboratoriTrascrizione
import snastro.kernel.CampioniAudio
import snastro.kernel.Esito
import snastro.kernel.GeneratoreIdUuid
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.atteso
import snastro.parlanti.applicazione.porte.DecodificatoreAudio
import snastro.parlanti.applicazione.porte.EstrattoreImpronta
import snastro.parlanti.applicazione.porte.EstrattoreImprontaFinta
import snastro.parlanti.dominio.Impronta
import snastro.persistenza.DatabaseProgetto
import snastro.persistenza.SnastroDatabase
import snastro.persistenza.apriDatabaseProgetto
import snastro.progetto.applicazione.comandi.AggiungiRegistrazione
import snastro.progetto.applicazione.letture.ElencoProgetti
import snastro.progetto.applicazione.porte.InfoAudio
import snastro.progetto.applicazione.porte.RegistrazioneRepository
import snastro.progetto.applicazione.porte.RegistroProgettiFinta
import snastro.progetto.applicazione.porte.SondaAudioFinta
import snastro.sintesi.adattatori.persistenza.RiassuntoRepositorySql
import snastro.sintesi.applicazione.porte.AzioneRisposta
import snastro.sintesi.applicazione.porte.DisponibilitaModelloLinguisticoFinta
import snastro.sintesi.applicazione.porte.ElementoRisposta
import snastro.sintesi.applicazione.porte.ErroreApplicazioneSintesi
import snastro.sintesi.applicazione.porte.ModelloLinguistico
import snastro.sintesi.applicazione.porte.PuntoChiaveRisposta
import snastro.sintesi.applicazione.porte.RichiestaRiassunto
import snastro.sintesi.applicazione.porte.RispostaModello
import snastro.sintesi.applicazione.porte.StatoModelloLinguistico
import snastro.sintesi.dominio.Riassunto
import snastro.supporto.test.attendiFinche
import snastro.supporto.test.pausaInTempoReale
import snastro.trascrizione.applicazione.comandi.AvviaElaborazione
import snastro.trascrizione.applicazione.letture.StatoElaborazioneVista
import snastro.trascrizione.applicazione.letture.StatoRegistrazioneVista
import snastro.trascrizione.applicazione.porte.DecodificatoreAudioFinta
import snastro.trascrizione.applicazione.porte.Diarizzatore
import snastro.trascrizione.applicazione.porte.RiconoscitoreParlato
import snastro.trascrizione.applicazione.porte.RiconoscitoreParlatoFinta
import snastro.trascrizione.applicazione.porte.Turno
import snastro.trascrizione.applicazione.porte.VadFinta
import snastro.trascrizione.dominio.NumeroPersone
import snastro.ui.ApriEsternoFinta
import snastro.ui.Cambiamento
import snastro.ui.ProgettoAperto
import snastro.ui.modelli.ServizioModelli
import snastro.ui.modelli.ServizioModelliFinta
import snastro.ui.modelli.StatoModelli
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import snastro.parlanti.applicazione.porte.DecodificatoreAudioFinta as DecodificatoreParlantiFinta
import snastro.trascrizione.applicazione.porte.DecodificatoreAudio as DecodificatoreTrascrizione

/**
 * The ONE test Ambiente of `:avvio` (ADR 0030 §3, AC-C79): the REAL single composition — [SessioneProgettoImpl] over a
 * SQLite project FILE opened with the production driver, then the production [apriProgetto] (never re-wired by hand):
 * real shared queue and its worker thread, real SQL repositories and subscribers of every context, real Documento
 * writer — with only the non-headless edges of [ComponentiApp] faked: the FFmpeg probe/decoders, the sherpa ML Finte
 * ([diarizzatore], [riconoscitore]), the print [estrattore], the LLM ([modello]) and its availability ([disponibilita],
 * `Installato` by default).
 *
 * [collaboratori], [porte] and [composto] are those of the project open right now (the one `crea` made, or the latest
 * `apri`); [composti] keeps every opening of this session, oldest first.
 */
@Suppress("LongParameterList") // one parameter per faked edge of the composition a test varies
internal class AmbienteProgetto(
    radice: Path,
    val diarizzatore: Diarizzatore = DiarizzatoreScriptato(),
    riconoscitore: RiconoscitoreParlato = RiconoscitoreParlatoFinta(),
    rilasciaDopoElaborazione: () -> Unit = {},
    adattatoriMl: (() -> AdattatoriMl)? = null,
    estrattore: EstrattoreImpronta = EstrattoreImprontaFinta(),
    proposte: Boolean = true,
    rilasciaMl: () -> Unit = {},
    decodificatoreParlanti: DecodificatoreAudio = DecodificatoreParlantiFinta(),
    val modello: ModelloLinguisticoDiProva = ModelloLinguisticoDiProva(),
    val disponibilita: DisponibilitaModelloLinguisticoFinta =
        DisponibilitaModelloLinguisticoFinta(StatoModelloLinguistico.Installato),
    modelliPronti: () -> Boolean = { true },
    chiudiDatabase: (DatabaseProgetto) -> Unit = DatabaseProgetto::chiudi,
    apriDatabase: (File) -> DatabaseProgetto = ::apriDatabaseProgetto,
    costruisciRegistrazioni: (SnastroDatabase) -> RegistrazioneRepository = PorteProgetto.registrazioniSql,
    /** Test seam of the session (AC-C54/AC-C58): queue sources added after the modules' own. */
    fontiCoda: List<FonteCoda> = emptyList(),
    private val durataMs: Long = DURATA_MS,
    // Shared by the whole composition so richiestoAlle/avviatoAlle stay comparable (AC-S146): a test that must
    // guarantee ordering injects an OrologioFinto and calls avanza() instead of Thread.sleep.
    val clock: Clock = orologioApp(),
    scadenzaArresto: Duration = ComponentiApp.SCADENZA_ARRESTO,
) : AutoCloseable {
    private val sorgenti = mutableMapOf<RiferimentoAudio, Long>()
    private val sorgente: Path = radice.resolve("riunione.wav").also { Files.write(it, ByteArray(DIMENSIONE_SORGENTE)) }
    private val esecutoreUi = Executors.newSingleThreadExecutor { r -> Thread(r, THREAD_UI) }
    private val esecutoreIo = Executors.newFixedThreadPool(THREAD_IO) { r -> Thread(r, "io-di-prova") }

    /** Every Registrazione the pipeline started decoding, in order — which runs the queue actually executed. */
    val decodificate: MutableList<RegistrazioneId> = CopyOnWriteArrayList()

    /**
     * Stands in for `Dispatchers.Swing`; the presenters' `io` is a small pool of its own (the S3 Proposta waits on it,
     * never on the UI thread) — both drained by [close] before the database closes, so no presenter load is ever still
     * running against a closed project.
     */
    val dispatcherUi = esecutoreUi.asCoroutineDispatcher()
    val dispatcherIo = esecutoreIo.asCoroutineDispatcher()
    val scope = CoroutineScope(SupervisorJob() + dispatcherUi)

    val app = ComponentiApp(
        io = Dispatchers.IO,
        adattatoriMl = adattatoriMl ?: {
            AdattatoriMl(diarizzatore, riconoscitore, VadFinta(), rilasciaDopoElaborazione)
        },
        decodificatore = { RegistraDecodifiche(DecodificatoreAudioFinta(sorgenti), decodificate) },
        adattatoriParlanti = { AdattatoriParlanti(estrattore, { decodificatoreParlanti }, proposte, rilasciaMl) },
        modelliPronti = modelliPronti,
        modello = modello,
        disponibilita = disponibilita,
        scadenzaArresto = scadenzaArresto,
    )

    val sessione = SessioneProgettoImpl(
        registro = RegistroProgettiFinta(),
        generatoreId = GeneratoreIdUuid(),
        clock = clock,
        scopeGenitore = scope,
        app = app,
        seams = SessioneProgettoSeams(
            apriDatabase = apriDatabase,
            costruisciRegistrazioni = costruisciRegistrazioni,
            chiudiDatabase = chiudiDatabase,
            sondaAudio = {
                SondaAudioFinta(mapOf(sorgente.toString() to InfoAudio(durataMs, LocalDate.parse("2026-01-01"))))
            },
            fontiCodaAggiuntive = fontiCoda,
        ),
    )

    private val apertura = SondaCostruzioni.durante {
        sessione.crea(radice.resolve("progetti").toString(), "Prova").atteso()
    }

    val progetto: ProgettoAperto = apertura.first

    /** AC-C60/AC-C61 (ADR 0030 §1): every constructor the project creation above ran, with its arguments. */
    val costruzioniApertura: SondaCostruzioni.Costruzioni = apertura.second

    /** Every opening of this session (0 = the one `crea` made), oldest first. */
    val composti: MutableList<ProgettoComposto> =
        CopyOnWriteArrayList(listOf(checkNotNull(sessione.progettoCorrente())))

    /** Closes the project and opens it again (the `apri` path): what that open constructed. */
    fun riapri(): SondaCostruzioni.Costruzioni {
        sessione.chiudi()
        return SondaCostruzioni.durante { apri(progetto.percorso) }.second
    }

    /** `sessione.apri` of [percorso], remembering the composed project in [composti]. */
    fun apri(percorso: String): ProgettoAperto = sessione.apri(percorso).atteso().also {
        composti += checkNotNull(sessione.progettoCorrente())
    }

    /** [diarizzatore] as the default [DiarizzatoreScriptato] (its `barriera`/`turni`/`fallisci` knobs). */
    val diarizzatoreScriptato: DiarizzatoreScriptato get() = diarizzatore as DiarizzatoreScriptato

    val composto: ProgettoComposto get() = checkNotNull(sessione.progettoCorrente())
    val collaboratori: CollaboratoriProgetto get() = checkNotNull(sessione.collaboratoriCorrenti())
    val porte: PorteProgetto get() = composto.porte
    val coda: CodaCondivisa get() = composto.coda
    val riassunti: RiassuntoRepositorySql get() = porte.riassunti
    val trascrizione: CollaboratoriTrascrizione get() = collaboratori.trascrizione
    val parlanti: CollaboratoriParlanti get() = collaboratori.parlanti
    val sintesi: CollaboratoriSintesi get() = collaboratori.sintesi
    val documento: CollaboratoriDocumento get() = collaboratori.documento

    /** The app graph over this Ambiente's session, scope and dispatchers (the one `ContenutoApp` would receive). */
    fun grafo(servizioModelli: ServizioModelli = ServizioModelliFinta(StatoModelli.Pronti)): Grafo = Grafo(
        scope = scope,
        io = dispatcherIo,
        clock = clock,
        sessione = sessione,
        elencoProgetti = ElencoProgetti(RegistroProgettiFinta()),
        cartellaProgettiPredefinita = progetto.percorso,
        servizioModelli = servizioModelli,
        apriEsterno = ApriEsternoFinta(),
        preferenze = PreferenzeAppFinta(),
    )

    /** Imports the test source through the REAL AggiungiRegistrazione; returns the NEW Registrazione. */
    fun importa(): RegistrazioneId {
        val prima = collaboratori.registrazioni().map { it.registrazioneId }.toSet()
        collaboratori.aggiungiRegistrazione(AggiungiRegistrazione(sorgente.toString())).atteso()
        val id = collaboratori.registrazioni().map { it.registrazioneId }.single { it !in prima }
        rendiLeggibile(id)
        return id
    }

    /** Makes [id]'s copied audio decodable by the fake decoder — also a Registrazione imported by another session. */
    fun rendiLeggibile(id: RegistrazioneId) {
        sorgenti[RiferimentoAudio("audio/${id.valore}.wav")] = durataMs // minting rule of RiferimentoAudio
    }

    /** 'Trascrivi' through the composition's own command — WITHOUT waiting ([trascrivi] waits). */
    fun avviaElaborazione(id: RegistrazioneId) {
        collaboratori.avviaElaborazione(AvviaElaborazione(id)).atteso()
    }

    /** 'Trascrivi', then waits until its Trascritto is there. */
    fun trascrivi(id: RegistrazioneId) {
        avviaElaborazione(id)
        attendiFinche(timeout = 10.seconds, messaggio = "elaborazione completata") {
            stato(id) == StatoElaborazioneVista.COMPLETATA
        }
    }

    /** Imports the test source and starts its Elaborazione — WITHOUT waiting for it. */
    fun importaEAvvia(): RegistrazioneId = importa().also(::avviaElaborazione)

    /** Imports the test source and transcribes it; returns the NEW one. */
    fun registrazioneTrascritta(): RegistrazioneId = importa().also(::trascrivi)

    /** S2's row state of [id] (the project's ONE `StatiElaborazione`). */
    fun vistaDi(id: RegistrazioneId): StatoRegistrazioneVista = trascrizione.statiElaborazione(listOf(id)).single()

    fun stato(id: RegistrazioneId): StatoElaborazioneVista = vistaDi(id).stato

    fun riassumi(id: RegistrazioneId, argomento: String? = null) {
        sintesi.riassumi(id, argomento).atteso()
    }

    /** ADR 0029 §5: the SQL repository reads the root and its children from ONE snapshot — no wrap needed here. */
    fun diRegistrazione(id: RegistrazioneId): List<Riassunto> = porte.riassunti.diRegistrazione(id)

    fun attendiPronto(id: RegistrazioneId) =
        attendiFinche(timeout = 10.seconds, messaggio = "Riassunto pronto") {
            diRegistrazione(id).singleOrNull()?.pronto == true
        }

    /** Parlanti rows of the open project, read through its own repositories. */
    fun conteggi(id: RegistrazioneId): Conteggi = Conteggi(
        parlanti = porte.parlanti.delProgetto(progetto.progettoId).size,
        attribuzioni = porte.attribuzioni.diRegistrazione(id).size,
        impronte = porte.parlanti.impronteDelProgetto(progetto.progettoId).size,
    )

    fun cartellaDocumenti(): Path = Path.of(progetto.percorso).resolve("documenti")

    /** Collects the project's Cambiamenti from now on: the flows' replayed past ones are drained and dropped. */
    fun raccogliCambiamenti(): MutableList<Cambiamento> {
        val cambiamenti = CopyOnWriteArrayList<Cambiamento>()
        scope.launch { collaboratori.aggiornamentiVista.cambiamenti.collect(cambiamenti::add) }
        pausaInTempoReale(
            ATTESA_REPLAY,
            motivo = "svuota il replay dei Cambiamenti passati prima di raccogliere i nuovi",
        )
        cambiamenti.clear()
        return cambiamenti
    }

    override fun close() {
        modello.sblocca()
        scope.cancel()
        listOf(esecutoreUi, esecutoreIo).forEach { e ->
            e.shutdown()
            e.awaitTermination(ATTESA_CHIUSURA_S, TimeUnit.SECONDS)
        }
        sessione.chiudi()
    }

    data class Conteggi(val parlanti: Int, val attribuzioni: Int, val impronte: Int)

    companion object {
        const val DURATA_MS = 3_000L
        const val THREAD_UI = "ui-di-prova"
        private const val THREAD_IO = 4
        private const val DIMENSIONE_SORGENTE = 64
        private const val ATTESA_CHIUSURA_S = 5L
        private val ATTESA_REPLAY = 300.milliseconds

        /** Voce 1 = [0, 1000), Voce 2 = [2000, 3000). */
        val DUE_VOCI = listOf(Turno(IntervalloMs(0, 1_000), 0), Turno(IntervalloMs(2_000, 3_000), 1))

        /** Voce 1 = Segmenti 1 and 3, Voce 2 = Segmento 2. */
        val VOCE_1_IN_DUE_SEGMENTI = listOf(
            Turno(IntervalloMs(0, 1_000), 0),
            Turno(IntervalloMs(1_000, 2_000), 1),
            Turno(IntervalloMs(2_000, 3_000), 0),
        )

        /** Voce 1, 2, 3 — one Segmento each. */
        val TRE_VOCI = listOf(
            Turno(IntervalloMs(0, 1_000), 0),
            Turno(IntervalloMs(1_000, 2_000), 1),
            Turno(IntervalloMs(2_000, 3_000), 2),
        )
    }
}

internal fun voce(id: RegistrazioneId, n: Int) = VoceRef(id, VoceId(n))

/** [DecodificatoreTrascrizione] recording in [decodificate] the id of every [decodifica]: the start of a run. */
private class RegistraDecodifiche(
    private val delegato: DecodificatoreTrascrizione,
    private val decodificate: MutableList<RegistrazioneId>,
) : DecodificatoreTrascrizione by delegato {
    override fun decodifica(id: RegistrazioneId, sorgente: RiferimentoAudio) {
        decodificate += id
        delegato.decodifica(id, sorgente)
    }
}

/**
 * A print extractor that takes the SAME fair [lock] the native Mutex is (ADR 0017 §1.3), interruptibly (§1.4), around
 * each extraction — standing in for `MotoreSherpa.conSessione`. Counts its calls, remembers their threads; [fallisci]
 * makes it throw (an infra fault).
 */
internal class EstrattoreConMutex(
    val lock: ReentrantLock = ReentrantLock(true),
    override val modello: String = EstrattoreImprontaFinta.MODELLO,
    private val delegato: EstrattoreImpronta = EstrattoreImprontaFinta(modello = modello),
) : EstrattoreImpronta {
    val chiamate = AtomicInteger()
    val thread: MutableList<Thread> = CopyOnWriteArrayList()

    @Volatile var fallisci: Boolean = false

    @Volatile var primaDellaChiamata: () -> Unit = {}

    override fun estrai(c: CampioniAudio): Impronta {
        primaDellaChiamata()
        thread += Thread.currentThread()
        lock.lockInterruptibly()
        try {
            chiamate.incrementAndGet()
            check(!fallisci) { "estrazione fallita (finta)" }
            return delegato.estrai(c)
        } finally {
            lock.unlock()
        }
    }
}

/**
 * The LLM bound for the tests (ADR 0021 §4's `ModelloLinguisticoFinto` role over the real composition): answers
 * [risposta]; while [blocca] holds, it waits on a latch IGNORING thread interrupts and polling only `annullato()`
 * (AC-S149/S161/S162) — it returns `Errore(Annullato)` as soon as `annullato()` reads true.
 */
internal class ModelloLinguisticoDiProva(
    @Volatile var risposta: Esito<RispostaModello> = Esito.Ok(RISPOSTA),
) : ModelloLinguistico {
    @Volatile private var latch: CountDownLatch? = null
    val chiamate = AtomicInteger()
    val richieste: MutableList<RichiestaRiassunto> = CopyOnWriteArrayList()
    val thread: MutableList<Thread> = CopyOnWriteArrayList()
    val esiti: MutableList<Esito<RispostaModello>> = CopyOnWriteArrayList()

    /** Runs on the queue's worker at the start of every call (what the test observes at claim time). */
    @Volatile var primaDellaRisposta: (RichiestaRiassunto) -> Unit = {}

    fun blocca() {
        latch = CountDownLatch(1)
    }

    fun sblocca() {
        latch?.countDown()
    }

    override fun riassumi(richiesta: RichiestaRiassunto, annullato: () -> Boolean): Esito<RispostaModello> {
        primaDellaRisposta(richiesta)
        richieste += richiesta
        thread += Thread.currentThread()
        chiamate.incrementAndGet()
        val attesa = latch
        var interrotto = false
        var esito: Esito<RispostaModello>? = null
        while (attesa != null && esito == null) {
            if (annullato()) esito = Esito.Errore(ErroreApplicazioneSintesi.Annullato)
            try {
                if (attesa.await(PASSO_MS, TimeUnit.MILLISECONDS)) break
            } catch (e: InterruptedException) {
                interrotto = true // ignored on purpose: an LLM run may not honour an interrupt (ADR 0023 §5)
            }
        }
        if (interrotto) Thread.currentThread().interrupt()
        val finale = esito ?: if (annullato()) Esito.Errore(ErroreApplicazioneSintesi.Annullato) else risposta
        esiti += finale
        return finale
    }

    companion object {
        private const val PASSO_MS = 10L

        /** Valid over [AmbienteProgetto.DUE_VOCI]'s Trascritto (Segmenti 1..2, Voci 1..2). */
        val RISPOSTA = RispostaModello(
            sommario = "{V1} e {V2} fanno il punto.",
            decisioni = listOf(ElementoRisposta("Si parte dal primo punto.", listOf(1))),
            questioniAperte = listOf(ElementoRisposta("Quando rivedersi.", listOf(2))),
            azioni = listOf(AzioneRisposta("{V2} manda il riepilogo.", listOf(2), responsabile = 2)),
            puntiChiave = listOf(PuntoChiaveRisposta("Il ritmo.", listOf(1, 2), parlante = 1)),
        )
    }
}

/** A scripted [Diarizzatore]: [turni] (default two Voci); [barriera] holds a run; [fallisci] makes it throw. */
internal class DiarizzatoreScriptato(@Volatile var turni: List<Turno> = AmbienteProgetto.DUE_VOCI) : Diarizzatore {
    @Volatile var barriera: CountDownLatch? = null

    @Volatile var fallisci: Boolean = false

    override fun diarizza(c: CampioniAudio, numeroPersone: NumeroPersone?): List<Turno> {
        barriera?.await()
        check(!fallisci) { "diarizzazione fallita (finta)" }
        return turni
    }
}
