package snastro.avvio

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import snastro.kernel.Esito
import snastro.persistenza.apriDatabaseProgetto
import snastro.progetto.applicazione.comandi.AggiungiRegistrazione
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * AC-350 (dynamic half): after a REAL `AggiungiRegistrazione`, run through R0's own composition
 * ([costruisciGrafoR0]) end to end, no row lands in `elaborazione` — R0 wires no Trascrizione
 * command/subscriber that would auto-start processing (the R2 behaviour, not built here; the static
 * half — nothing under `avvio/src/main` even imports `snastro.trascrizione` — is proven in
 * [GrafoR0Test]). Needs real FFmpeg to probe the source (ADR 0005), like every other real-audio test
 * in this codebase: `@Tag("modelli")`, excluded from the default gate.
 */
@Tag("modelli")
class AggiungiRegistrazioneR0Test {
    @TempDir
    lateinit var radice: Path

    @Test
    fun `AC-350 dopo AggiungiRegistrazione nessuna riga esiste in elaborazione`() {
        val sorgente = radice.resolve("sorgente.wav")
        scriviWavSintetico(sorgente, durataMs = 200)

        val grafo = costruisciGrafoR0(radice.resolve("registro"))
        val esitoCrea = grafo.sessione.crea(radice.resolve("progetti").toString(), "Prova")
        check(esitoCrea is Esito.Ok) { "crea fallita: $esitoCrea" }
        val progetto = esitoCrea.valore
        val collaboratori = grafo.sessione.collaboratoriCorrenti()!!

        val esitoAggiungi = collaboratori.aggiungiRegistrazione(AggiungiRegistrazione(sorgente.toString()))
        check(esitoAggiungi is Esito.Ok) { "AggiungiRegistrazione fallita: $esitoAggiungi" }
        val registrazioneId = collaboratori.registrazioni().single().registrazioneId

        grafo.sessione.chiudi() // rilascia il .lock prima di riaprire il db per l'ispezione
        val db = apriDatabaseProgetto(Path.of(progetto.percorso).toFile())
        val righeElaborazione =
            db.database.elaborazioneQueries.trovaDiRegistrazione(registrazioneId.valore).executeAsList()
        db.chiudi()

        assertTrue(righeElaborazione.isEmpty(), "R0 non deve mai scrivere in elaborazione")
    }
}
