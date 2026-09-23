package snastro.avvio

import org.junit.jupiter.api.io.TempDir
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.kernel.atteso
import snastro.persistenza.apriDatabaseProgetto
import snastro.progetto.adattatori.persistenza.ProgettoRepositorySql
import snastro.progetto.adattatori.persistenza.RegistrazioneRepositorySql
import snastro.progetto.dominio.NomeProgetto
import snastro.progetto.dominio.Progetto
import snastro.progetto.dominio.Registrazione
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * AC-237: `--smoke <fixture-dir>` opens the fixture project (>=1 Registrazione already imported) and
 * saves one screenshot of S1 and one of S2 — headless, no sherpa natives, no ML models: R0 registers
 * none (AC-350). The fixture here is built DIRECTLY via the SQL repositories + domain factories
 * (never `AggiungiRegistrazioneServizio`'s real FFmpeg probe/copy pipeline — this proves the smoke
 * MECHANISM, not audio import) so this test needs no native library and stays in the default gate.
 */
class SmokeTest {
    @TempDir
    lateinit var cartella: Path

    @Test
    fun `AC-237 smoke apre il progetto fixture e salva uno screenshot di S1 e di S2, senza nativi`() {
        val cartellaFixture = cartella.resolve("Fixture.snastro")
        costruisciProgettoFixture(cartellaFixture)

        eseguiSmoke(cartellaFixture.toString())

        val s1 = Path.of("build/smoke/s1.png")
        val s2 = Path.of("build/smoke/s2.png")
        assertTrue(Files.exists(s1) && Files.size(s1) > 0, "screenshot di S1 mancante o vuoto: $s1")
        assertTrue(Files.exists(s2) && Files.size(s2) > 0, "screenshot di S2 mancante o vuoto: $s2")
    }

    /** A valid `.snastro` folder with a Progetto and one Registrazione — SQL only, no FFmpeg. */
    private fun costruisciProgettoFixture(cartellaProgetto: Path) {
        Files.createDirectories(cartellaProgetto.resolve("audio"))
        Files.createDirectories(cartellaProgetto.resolve("documenti"))
        Files.createDirectories(cartellaProgetto.resolve("cache/audio"))
        // La sorgente non serve alla decodifica FFmpeg qui (S2 controlla solo l'esistenza del file
        // per `disponibile`, non riproduce nulla durante lo smoke) — un file segnaposto basta.
        Files.write(cartellaProgetto.resolve("audio/rec-1.wav"), byteArrayOf(0))

        val db = apriDatabaseProgetto(cartellaProgetto.toFile())
        val progetti = ProgettoRepositorySql(db)
        val registrazioni = RegistrazioneRepositorySql(db)

        val nome = NomeProgetto.di("Progetto Fixture").atteso()
        val progetto = Progetto.crea(ProgettoId("fixture-progetto"), nome)
        progetti.salva(progetto.aggregato)

        val registrazione = Registrazione.aggiungi(
            id = RegistrazioneId("fixture-registrazione"),
            progettoId = progetto.aggregato.id,
            titolo = "Riunione di prova",
            riferimentoAudio = RiferimentoAudio("audio/rec-1.wav"),
            durataMs = 60_000,
            dataRegistrazione = LocalDate.parse("2026-01-01"),
            aggiuntaAlle = Instant.parse("2026-01-01T10:00:00Z"),
        )
        registrazioni.salva(registrazione.aggregato)
    }
}
