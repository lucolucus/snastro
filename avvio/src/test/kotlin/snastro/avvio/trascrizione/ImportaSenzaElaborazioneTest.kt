package snastro.avvio.trascrizione

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import snastro.avvio.costruisciGrafo
import snastro.avvio.scriviWavSintetico
import snastro.kernel.Esito
import snastro.persistenza.apriDatabaseProgetto
import snastro.progetto.applicazione.comandi.AggiungiRegistrazione
import snastro.trascrizione.adattatori.persistenza.ElaborazioneRepositorySql
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * AC-371 with REAL FFmpeg (ADR 0030 §3: AggiungiRegistrazioneR0Test's AC-350 merged into AC-371): after a real
 * `AggiungiRegistrazione` through the app's own graph ([costruisciGrafo], the single composition) end to end, no row
 * lands in `elaborazione` — importing never starts an Elaborazione (ADR 0014; the composition registers no synchronous
 * subscriber on `RegistrazioneAggiunta`, AC-355). The gate-level half, on the Finte, is
 * `ComposizioneTrascrizioneTest`'s AC-371. Needs real FFmpeg to probe the source (ADR 0005): `@Tag("modelli")`,
 * excluded from the default gate.
 */
@Tag("modelli")
class ImportaSenzaElaborazioneTest {
    @TempDir
    lateinit var radice: Path

    @Test
    fun `AC-371 con FFmpeg reale dopo AggiungiRegistrazione nessuna riga esiste in elaborazione`() {
        val sorgente = radice.resolve("sorgente.wav")
        scriviWavSintetico(sorgente, durataMs = 200)

        val modelli = Files.createDirectories(radice.resolve("modelli"))
        val grafo = costruisciGrafo(radice.resolve("registro"), SceltaMl.FINTE, modelli)
        val esitoCrea = grafo.sessione.crea(radice.resolve("progetti").toString(), "Prova")
        check(esitoCrea is Esito.Ok) { "crea fallita: $esitoCrea" }
        val progetto = esitoCrea.valore
        val collaboratori = checkNotNull(grafo.sessione.collaboratoriCorrenti())

        val esitoAggiungi = collaboratori.aggiungiRegistrazione(AggiungiRegistrazione(sorgente.toString()))
        check(esitoAggiungi is Esito.Ok) { "AggiungiRegistrazione fallita: $esitoAggiungi" }
        val registrazioneId = collaboratori.registrazioni().single().registrazioneId

        grafo.sessione.chiudi() // rilascia il .lock prima di riaprire il db per l'ispezione
        val db = apriDatabaseProgetto(Path.of(progetto.percorso).toFile())
        // agg-elaborazione §14: read through the repository port's SQL adapter, never the raw queries.
        val righeElaborazione = ElaborazioneRepositorySql(db.database).diRegistrazione(registrazioneId)
        db.chiudi()

        assertTrue(righeElaborazione.isEmpty(), "l'import non deve mai scrivere in elaborazione")
    }
}
