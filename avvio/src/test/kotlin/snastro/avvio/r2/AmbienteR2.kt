package snastro.avvio.r2

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import snastro.avvio.CollaboratoriProgettoAperto
import snastro.avvio.ContestoEstensione
import snastro.avvio.EstensioneSessione
import snastro.avvio.GrafoR0
import snastro.avvio.SessioneProgettoImpl
import snastro.avvio.SessioneProgettoSeams
import snastro.avvio.orologioApp
import snastro.avvio.r1.AdattatoriMl
import snastro.avvio.r1.EstensioneR1
import snastro.avvio.r1.attendiFinche
import snastro.kernel.CampioniAudio
import snastro.kernel.GeneratoreIdFinto
import snastro.kernel.GeneratoreIdUuid
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.atteso
import snastro.parlanti.adattatori.persistenza.AttribuzioneRepositorySql
import snastro.parlanti.adattatori.persistenza.ParlanteRepositorySql
import snastro.parlanti.applicazione.porte.DecodificatoreAudio
import snastro.parlanti.applicazione.porte.EstrattoreImpronta
import snastro.parlanti.applicazione.porte.EstrattoreImprontaFinta
import snastro.parlanti.dominio.Impronta
import snastro.persistenza.DatabaseProgetto
import snastro.persistenza.apriDatabaseProgetto
import snastro.progetto.applicazione.comandi.AggiungiRegistrazione
import snastro.progetto.applicazione.letture.ElencoProgetti
import snastro.progetto.applicazione.porte.InfoAudio
import snastro.progetto.applicazione.porte.RegistroProgettiFinta
import snastro.progetto.applicazione.porte.SondaAudioFinta
import snastro.trascrizione.applicazione.comandi.AvviaElaborazione
import snastro.trascrizione.applicazione.letture.StatoElaborazioneVista
import snastro.trascrizione.applicazione.porte.DecodificatoreAudioFinta
import snastro.trascrizione.applicazione.porte.Diarizzatore
import snastro.trascrizione.applicazione.porte.DiarizzatoreFinta
import snastro.trascrizione.applicazione.porte.RiconoscitoreParlatoFinta
import snastro.trascrizione.applicazione.porte.Turno
import snastro.trascrizione.applicazione.porte.VadFinta
import snastro.ui.ApriEsternoFinta
import snastro.ui.ProgettoAperto
import snastro.ui.modelli.ServizioModelliFinta
import snastro.ui.modelli.StatoModelli
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import snastro.parlanti.applicazione.porte.DecodificatoreAudioFinta as DecodificatoreParlantiFinta

/**
 * The REAL R2 composition ([SessioneProgettoImpl] + [EstensioneR2] over [EstensioneR1], SQLite project
 * folder on disk, real queue/pipeline threads, real Documento writer, real Parlanti SQL repositories and
 * subscribers) with only the non-headless edges faked: the FFmpeg probe and decoders, the ML Finte and
 * the print extractor ([estrattore]). The same composition the app runs, minus natives — the `databaseInMemoria`
 * the ACs name is replaced by a throwaway project folder (the session opens its own database: stronger).
 *
 * [contesto] is the extension context of the open project (its database and dispatcher), captured as
 * `SessioneProgettoImpl` hands it over — what a test needs to observe rows and events.
 */
@Suppress("LongParameterList") // one parameter per faked edge of the composition
internal class AmbienteR2(
    radice: Path,
    diarizzatore: Diarizzatore = DiarizzatoreFinta(DUE_VOCI),
    estrattore: EstrattoreImpronta = EstrattoreImprontaFinta(),
    proposte: Boolean = true,
    chiudiDatabase: (DatabaseProgetto) -> Unit = DatabaseProgetto::chiudi,
    rilasciaMl: () -> Unit = {},
    decodificatoreParlanti: DecodificatoreAudio = DecodificatoreParlantiFinta(),
    apriDatabase: (File) -> DatabaseProgetto = ::apriDatabaseProgetto,
    private val durataMs: Long = DURATA_MS,
) : AutoCloseable {
    private val sorgenti = mutableMapOf<RiferimentoAudio, Long>()
    private val sorgente: Path = radice.resolve("riunione.wav").also { Files.write(it, ByteArray(DIMENSIONE_SORGENTE)) }
    private val esecutoreUi = Executors.newSingleThreadExecutor { r -> Thread(r, THREAD_UI) }
    private val esecutoreIo = Executors.newFixedThreadPool(THREAD_IO) { r -> Thread(r, "io-di-prova") }
    private val contesti = CopyOnWriteArrayList<ContestoEstensione>()
    private val estesi = CopyOnWriteArrayList<CollaboratoriR2>()

    /**
     * Stands in for `Dispatchers.Swing`; the presenters' `io` is a small pool of its own (the S3 Proposta
     * waits on it, never on the UI thread) — both drained by [close] before the database closes, so no
     * presenter load is ever still running against a closed project (see `AmbienteR1`).
     */
    val dispatcherUi = esecutoreUi.asCoroutineDispatcher()
    private val dispatcherIo = esecutoreIo.asCoroutineDispatcher()
    val scope = CoroutineScope(SupervisorJob() + dispatcherUi)

    private val estensione = EstensioneR2(
        r1 = EstensioneR1(
            io = Dispatchers.IO,
            clock = orologioApp(),
            generatoreId = GeneratoreIdFinto(),
            adattatoriMl = { AdattatoriMl(diarizzatore, RiconoscitoreParlatoFinta(), VadFinta()) },
            modelliPronti = { true },
            decodificatore = { DecodificatoreAudioFinta(sorgenti) },
            lettoreNomi = ::lettoreNomiDaParlanti,
        ),
        io = Dispatchers.IO,
        clock = orologioApp(),
        generatoreId = GeneratoreIdUuid(),
        adattatori = { AdattatoriParlanti(estrattore, { decodificatoreParlanti }, proposte, rilasciaMl) },
    )

    val sessione = SessioneProgettoImpl(
        registro = RegistroProgettiFinta(),
        generatoreId = GeneratoreIdFinto(),
        clock = orologioApp(),
        scopeGenitore = scope,
        seams = SessioneProgettoSeams(
            sondaAudio = {
                SondaAudioFinta(mapOf(sorgente.toString() to InfoAudio(durataMs, LocalDate.parse("2026-01-01"))))
            },
            chiudiDatabase = chiudiDatabase,
            apriDatabase = apriDatabase,
        ),
        estensione = EstensioneSessione { contesto ->
            contesti += contesto
            (estensione.apri(contesto) as CollaboratoriR2).also(estesi::add)
        },
    )

    val progetto: ProgettoAperto = sessione.crea(radice.resolve("progetti").toString(), "Prova").atteso()

    val collaboratori: CollaboratoriProgettoAperto get() = checkNotNull(sessione.collaboratoriCorrenti())
    val r2: CollaboratoriR2 get() = collaboratori.estensione as CollaboratoriR2
    val contesto: ContestoEstensione get() = contesti.last()

    /**
     * The R2 collaborators of the [indice]-th project opening of this session (0 = the one `crea` made),
     * waited for: a background worker of that opening may ask before `apri` has even returned.
     */
    fun progettoEsteso(indice: Int): CollaboratoriR2 {
        attendiFinche(messaggio = "apertura n. $indice") { estesi.size > indice }
        return estesi[indice]
    }

    val grafoR0: GrafoR0
        get() = GrafoR0(
            scope = scope,
            io = dispatcherIo,
            clock = orologioApp(),
            sessione = sessione,
            elencoProgetti = ElencoProgetti(RegistroProgettiFinta()),
            cartellaProgettiPredefinita = progetto.percorso,
        )

    val grafo: GrafoR2 get() = GrafoR2(grafoR0, ServizioModelliFinta(StatoModelli.Pronti), ApriEsternoFinta())

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

    /** 'Trascrivi' through R1's own command, then waits until its Trascritto is there. */
    fun trascrivi(id: RegistrazioneId) {
        r2.r1.avviaElaborazione(AvviaElaborazione(id)).atteso()
        attendiFinche(messaggio = "elaborazione completata") {
            r2.r1.statiElaborazione(listOf(id)).single().stato == StatoElaborazioneVista.COMPLETATA
        }
    }

    /** Parlanti rows of the open project, read straight from its database. */
    fun conteggi(id: RegistrazioneId): Conteggi {
        val db = contesto.database
        return Conteggi(
            parlanti = ParlanteRepositorySql(db).delProgetto(progetto.progettoId).size,
            attribuzioni = AttribuzioneRepositorySql(db).diRegistrazione(id).size,
            impronte = ParlanteRepositorySql(db).impronteDelProgetto(progetto.progettoId).size,
        )
    }

    override fun close() {
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

/**
 * A print extractor that takes the SAME fair [lock] the native Mutex is (ADR 0017 §1.3), interruptibly
 * (§1.4), around each extraction — standing in for `MotoreSherpa.conSessione`. Counts its calls, remembers
 * their threads; [fallisci] makes it throw (an infra fault).
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
