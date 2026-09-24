package snastro.avvio

import org.junit.jupiter.api.io.TempDir
import snastro.kernel.DispatcherEventiInMemoria
import snastro.kernel.ElaborazioneId
import snastro.kernel.GeneratoreIdFinto
import snastro.kernel.IntervalloMs
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.atteso
import snastro.parlanti.adattatori.persistenza.AttribuzioneRepositorySql
import snastro.parlanti.adattatori.persistenza.ParlanteRepositorySql
import snastro.parlanti.adattatori.porte.LettoreRegistrazioneDaProgetto
import snastro.parlanti.adattatori.porte.LettoreVociDaTrascrizione
import snastro.parlanti.applicazione.comandi.ConfermaAttribuzione
import snastro.parlanti.applicazione.comandi.ConfermaAttribuzioneServizio
import snastro.parlanti.applicazione.comandi.ObiettivoAttribuzione
import snastro.parlanti.applicazione.porte.DecodificatoreAudioFinta
import snastro.parlanti.applicazione.porte.EstrattoreImprontaFinta
import snastro.persistenza.SnastroDatabase
import snastro.persistenza.UnitaDiLavoroSql
import snastro.persistenza.apriDatabaseProgetto
import snastro.progetto.adattatori.persistenza.ProgettoRepositorySql
import snastro.progetto.adattatori.persistenza.RegistrazioneRepositorySql
import snastro.progetto.applicazione.letture.CatalogoRegistrazioni
import snastro.progetto.dominio.NomeProgetto
import snastro.progetto.dominio.Progetto
import snastro.progetto.dominio.Registrazione
import snastro.trascrizione.adattatori.persistenza.ElaborazioneRepositorySql
import snastro.trascrizione.adattatori.persistenza.TrascrittoRepositorySql
import snastro.trascrizione.applicazione.letture.VociDelTrascritto
import snastro.trascrizione.dominio.SegmentoIniziale
import snastro.trascrizione.dominio.StatoElaborazione
import snastro.trascrizione.dominio.Trascritto
import snastro.trascrizione.dominio.unaElaborazione
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * AC-237 + AC-351 + AC-357: `--smoke <fixture-dir>` opens the fixture project (>=1 Registrazione already
 * imported, one of them with a completed Trascritto whose Voce 1 is named 'Anna') and saves S1, S2 (the
 * smoke waits for the identification badge '2 voci · 1 da identificare'), S3 (the Voci panel, Voce 1's
 * Nome shown), S4 (the shell's Parlanti section) and S5 — headless, on the ML Finte: no sherpa natives,
 * no models. fix-batch-16 LOW-1: S5 renders the REAL catalogue over an empty cache ('Mancanti',
 * 'Scarica'). The fixture here is built DIRECTLY via the SQL repositories + domain factories (never
 * `AggiungiRegistrazioneServizio`'s real FFmpeg probe/copy pipeline — this proves the smoke MECHANISM,
 * not audio import), and its Parlante through the REAL `ConfermaAttribuzione` over the Finte decoder and
 * extractor, so this test needs no native library and stays in the default gate.
 */
class SmokeTest {
    @TempDir
    lateinit var cartella: Path

    @Test
    fun `AC-237 AC-351 AC-357 smoke apre il progetto fixture e salva S1, S2, S3, S4 e S5, senza nativi`() {
        val cartellaFixture = cartella.resolve("Fixture.snastro")
        costruisciProgettoFixture(cartellaFixture)
        SCHERMATE.forEach { Files.deleteIfExists(Path.of("build/smoke/$it.png")) }

        eseguiSmoke(cartellaFixture.toString())

        SCHERMATE.forEach { nome ->
            val png = Path.of("build/smoke/$nome.png")
            assertTrue(Files.exists(png) && Files.size(png) > 0, "screenshot di $nome mancante o vuoto: $png")
        }
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
        val progetti = ProgettoRepositorySql(db.database)
        val registrazioni = RegistrazioneRepositorySql(db.database)

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

        // AC-351: a completed Elaborazione + its Trascritto (two Voci, no Parlanti -> 'Voce 1'/'Voce 2').
        val registrazioneId = registrazione.aggregato.id
        ElaborazioneRepositorySql(db.database).salva(
            unaElaborazione(StatoElaborazione.COMPLETATA, ElaborazioneId("fixture-elaborazione"), registrazioneId),
        ).atteso()
        val segmenti = listOf(
            SegmentoIniziale(0, IntervalloMs(0, 4_000), "Buongiorno a tutti, iniziamo con il punto sul progetto."),
            SegmentoIniziale(1, IntervalloMs(4_500, 9_000), "Grazie. Da parte mia ci sono due aggiornamenti."),
            SegmentoIniziale(0, IntervalloMs(9_500, 12_000), "Perfetto, partiamo dal primo."),
        )
        val trascritto = Trascritto.crea(registrazioneId, durataMs = 60_000, segmenti = segmenti).atteso()
        TrascrittoRepositorySql(db.database).salva(trascritto.aggregato)

        // AC-357: Voce 1 is 'Anna' (S2 badge '2 voci · 1 da identificare', S3 Nome, one S4 row).
        confermaAttribuzione(db.database, registrazioni).esegui(
            ConfermaAttribuzione(VoceRef(registrazioneId, VoceId(1)), ObiettivoAttribuzione.NuovoParlante("Anna")),
        ).atteso()
        db.chiudi()
    }

    private fun confermaAttribuzione(
        database: SnastroDatabase,
        registrazioni: RegistrazioneRepositorySql,
    ): ConfermaAttribuzioneServizio {
        val eventi = DispatcherEventiInMemoria(UnitaDiLavoroSql(database))
        return ConfermaAttribuzioneServizio(
            eventi.unitaDiLavoro,
            GeneratoreIdFinto(),
            LettoreRegistrazioneDaProgetto(CatalogoRegistrazioni(registrazioni)),
            LettoreVociDaTrascrizione(VociDelTrascritto(TrascrittoRepositorySql(database))),
            ParlanteRepositorySql(database),
            AttribuzioneRepositorySql(database),
            DecodificatoreAudioFinta(),
            EstrattoreImprontaFinta(),
            eventi,
        )
    }

    private companion object {
        val SCHERMATE = listOf("s1", "s2", "s3", "s4", "s5")
    }
}
