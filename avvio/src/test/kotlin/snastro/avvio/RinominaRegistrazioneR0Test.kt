package snastro.avvio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.jupiter.api.io.TempDir
import snastro.kernel.GeneratoreIdFinto
import snastro.kernel.RegistrazioneId
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.persistenza.SnastroDatabase
import snastro.progetto.adattatori.persistenza.RegistrazioneRepositorySql
import snastro.progetto.applicazione.comandi.RinominaRegistrazione
import snastro.progetto.applicazione.porte.RegistroProgettiFinta
import snastro.progetto.dominio.ErroreProgetto
import snastro.ui.Cambiamento
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * AC-366 through R0's own composition ([SessioneProgettoImpl]): `RinominaRegistrazione` is wired
 * with `DispatcherEventiInMemoria.unitaDiLavoro` (AC-346 — with the raw `UnitaDiLavoroSql` the
 * `pubblica` inside it would throw instead of returning `Esito.Ok`), it persists on the real project
 * database, and a committed rename produces a [Cambiamento] for that Registrazione on the open
 * Progetto's `AggiornamentiVista`. The Registrazione is seeded straight into the database (through
 * the `costruisciRegistrazioni` seam) so no FFmpeg is needed; AC-362: the stored audio file is untouched.
 */
class RinominaRegistrazioneR0Test {
    @TempDir
    lateinit var cartella: Path

    private val orologio: Clock = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC)
    private val id = RegistrazioneId("reg-1")
    private var db: SnastroDatabase? = null

    private val sessione = SessioneProgettoImpl(
        registro = RegistroProgettiFinta(),
        generatoreId = GeneratoreIdFinto(),
        clock = orologio,
        scopeGenitore = CoroutineScope(SupervisorJob()),
        seams = SessioneProgettoSeams(
            costruisciRegistrazioni = { database ->
                db = database
                RegistrazioneRepositorySql(database)
            },
        ),
    )

    private fun apriConUnaRegistrazione(): CollaboratoriProgettoAperto {
        val progetto = sessione.crea(cartella.toString(), "Prova").atteso()
        Files.write(Path.of(progetto.percorso).resolve("audio/reg-1.m4a"), AUDIO)
        checkNotNull(db).registrazioneQueries.inserisci(
            id = id.valore,
            progettoId = progetto.progettoId.valore,
            titolo = "Seduta di marzo",
            riferimentoAudio = "audio/reg-1.m4a",
            durataMs = 60_000L,
            dataRegistrazione = "2026-03-12",
            aggiuntaAlle = 0L,
        )
        return checkNotNull(sessione.collaboratoriCorrenti())
    }

    @Test
    fun `AC-366 una rinomina dalla composizione R0 persiste e produce un Cambiamento per quella Registrazione`() {
        val collaboratori = apriConUnaRegistrazione()

        collaboratori.rinominaRegistrazione(RinominaRegistrazione(id, "Consiglio di marzo")).atteso()

        assertEquals("Consiglio di marzo", collaboratori.registrazioni().single().titolo)
        assertEquals(Cambiamento(id), cambiamentiDi(collaboratori).lastOrNull())
        val cartellaProgetto = Path.of(checkNotNull(sessione.corrente.value).percorso)
        assertEquals(AUDIO.toList(), Files.readAllBytes(cartellaProgetto.resolve("audio/reg-1.m4a")).toList())
        val fileAudio = Files.list(cartellaProgetto.resolve("audio"))
            .use { elenco -> elenco.map { it.fileName.toString() }.toList() }
        assertEquals(listOf("reg-1.m4a"), fileAudio)
        sessione.chiudi()
    }

    @Test
    fun `AC-366 una rinomina rifiutata non produce alcun Cambiamento`() {
        val collaboratori = apriConUnaRegistrazione()

        collaboratori.rinominaRegistrazione(RinominaRegistrazione(id, "  ")).erroreAtteso<ErroreProgetto.TitoloVuoto>()

        assertEquals("Seduta di marzo", collaboratori.registrazioni().single().titolo)
        assertNull(cambiamentiDi(collaboratori).lastOrNull())
        sessione.chiudi()
    }

    private fun cambiamentiDi(collaboratori: CollaboratoriProgettoAperto): List<Cambiamento> =
        assertIs<AggiornamentiVistaEventi>(collaboratori.aggiornamentiVista).cambiamenti.replayCache

    private companion object {
        val AUDIO = byteArrayOf(1, 2, 3, 4)
    }
}
