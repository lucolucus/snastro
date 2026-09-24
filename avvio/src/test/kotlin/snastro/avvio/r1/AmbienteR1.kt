package snastro.avvio.r1

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import snastro.avvio.CollaboratoriProgettoAperto
import snastro.avvio.SessioneProgettoImpl
import snastro.avvio.SessioneProgettoSeams
import snastro.avvio.orologioApp
import snastro.kernel.GeneratoreIdFinto
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.kernel.atteso
import snastro.progetto.applicazione.comandi.AggiungiRegistrazione
import snastro.progetto.applicazione.porte.InfoAudio
import snastro.progetto.applicazione.porte.RegistroProgettiFinta
import snastro.progetto.applicazione.porte.SondaAudioFinta
import snastro.trascrizione.applicazione.porte.DecodificatoreAudio
import snastro.trascrizione.applicazione.porte.DecodificatoreAudioFinta
import snastro.trascrizione.applicazione.porte.Diarizzatore
import snastro.trascrizione.applicazione.porte.DiarizzatoreFinta
import snastro.trascrizione.applicazione.porte.RiconoscitoreParlato
import snastro.trascrizione.applicazione.porte.RiconoscitoreParlatoFinta
import snastro.trascrizione.applicazione.porte.VadFinta
import snastro.ui.ProgettoAperto
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The REAL R1 composition ([SessioneProgettoImpl] + [EstensioneR1], SQLite project folder on disk,
 * real queue/pipeline threads, real Documento writer) with only the non-headless edges faked: the
 * FFmpeg probe ([SondaAudioFinta]) and decoder ([DecodificatoreAudioFinta]) and the ML Finte — so the
 * end-to-end ACs run in the gate, without natives or models.
 */
internal class AmbienteR1(
    radice: Path,
    diarizzatore: Diarizzatore = DiarizzatoreFinta(),
    riconoscitore: RiconoscitoreParlato = RiconoscitoreParlatoFinta(),
    rilasciaDopoElaborazione: () -> Unit = {},
    adattatoriMl: () -> AdattatoriMl = {
        AdattatoriMl(diarizzatore, riconoscitore, VadFinta(), rilasciaDopoElaborazione)
    },
) : AutoCloseable {
    private val sorgenti = mutableMapOf<RiferimentoAudio, Long>()
    private val sorgente: Path = radice.resolve("riunione.wav").also { Files.write(it, ByteArray(DIMENSIONE_SORGENTE)) }
    private val esecutoreUi = Executors.newSingleThreadExecutor()

    /** Every Registrazione the pipeline started decoding, in order — which runs the queue actually executed. */
    val decodificate: MutableList<RegistrazioneId> = CopyOnWriteArrayList()

    /**
     * Stands in for `Dispatchers.Swing` AND for the presenters' `io` in these tests: one confined
     * thread, drained by [close] before the database closes — no presenter load is ever still
     * running against a closed project (it would reopen SQLite files under a deleted `@TempDir`).
     */
    val dispatcherUi = esecutoreUi.asCoroutineDispatcher()
    val scope = CoroutineScope(SupervisorJob() + dispatcherUi)

    val sessione = SessioneProgettoImpl(
        registro = RegistroProgettiFinta(),
        generatoreId = GeneratoreIdFinto(),
        clock = orologioApp(),
        scopeGenitore = scope,
        seams = SessioneProgettoSeams(
            sondaAudio = {
                SondaAudioFinta(mapOf(sorgente.toString() to InfoAudio(DURATA_MS, LocalDate.parse("2026-01-01"))))
            },
        ),
        estensione = EstensioneR1(
            io = Dispatchers.IO,
            clock = orologioApp(),
            generatoreId = GeneratoreIdFinto(),
            adattatoriMl = adattatoriMl,
            modelliPronti = { true },
            decodificatore = { RegistraDecodifiche(DecodificatoreAudioFinta(sorgenti), decodificate) },
        ),
    )

    val progetto: ProgettoAperto = sessione.crea(radice.resolve("progetti").toString(), "Prova").atteso()

    val collaboratori: CollaboratoriProgettoAperto get() = checkNotNull(sessione.collaboratoriCorrenti())
    val r1: CollaboratoriR1 get() = collaboratori.estensione as CollaboratoriR1

    /** Imports the test source through the REAL AggiungiRegistrazione of the composition; returns the NEW one. */
    fun importa(): RegistrazioneId {
        val prima = collaboratori.registrazioni().map { it.registrazioneId }.toSet()
        collaboratori.aggiungiRegistrazione(AggiungiRegistrazione(sorgente.toString())).atteso()
        val id = collaboratori.registrazioni().map { it.registrazioneId }.single { it !in prima }
        sorgenti[RiferimentoAudio("audio/${id.valore}.wav")] = DURATA_MS // minting rule of RiferimentoAudio
        return id
    }

    fun cartellaDocumenti(): Path = Path.of(progetto.percorso).resolve("documenti")

    override fun close() {
        scope.cancel()
        esecutoreUi.shutdown()
        esecutoreUi.awaitTermination(ATTESA_CHIUSURA_S, TimeUnit.SECONDS)
        sessione.chiudi()
    }

    companion object {
        const val DURATA_MS = 3_000L
        private const val DIMENSIONE_SORGENTE = 64
        private const val ATTESA_CHIUSURA_S = 5L
    }
}

/** [DecodificatoreAudio] recording in [decodificate] the id of every [decodifica] — the start of a pipeline run. */
private class RegistraDecodifiche(
    private val delegato: DecodificatoreAudio,
    private val decodificate: MutableList<RegistrazioneId>,
) : DecodificatoreAudio by delegato {
    override fun decodifica(id: RegistrazioneId, sorgente: RiferimentoAudio) {
        decodificate += id
        delegato.decodifica(id, sorgente)
    }
}

/** Polls [condizione] (never a fixed sleep as the assertion itself) until true or [timeoutMs] elapses. */
internal fun attendiFinche(timeoutMs: Long = 10_000, messaggio: String = "condizione", condizione: () -> Boolean) {
    val scadenza = System.currentTimeMillis() + timeoutMs
    while (!condizione()) {
        check(System.currentTimeMillis() < scadenza) { "timeout in attesa di: $messaggio" }
        Thread.sleep(PASSO_ATTESA_MS)
    }
}

private const val PASSO_ATTESA_MS = 20L
