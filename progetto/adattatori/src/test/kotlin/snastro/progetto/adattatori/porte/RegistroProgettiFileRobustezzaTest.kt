package snastro.progetto.adattatori.porte

import org.junit.jupiter.api.io.TempDir
import snastro.kernel.ProgettoId
import snastro.progetto.applicazione.porte.VoceRegistro
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * AC-120 (atomic rewrite), AC-121 (a corrupt file never crashes the caller) and the round-trip
 * guarantee for `percorso` (spaces, parentheses, Unicode, Windows `\` separators — R3, AC-263/264).
 */
class RegistroProgettiFileRobustezzaTest {
    @TempDir
    lateinit var cartella: Path

    @Test
    fun `AC-120 un guasto durante la riscrittura lascia intatto il contenuto precedente`() {
        val file = cartella.resolve("progetti-recenti")
        val registro = RegistroProgettiFile(file)
        registro.registra(unaVoce())
        val contenutoPrimaDelGuasto = Files.readString(file)

        // Simula un crash a meta scrittura: nega il permesso di scrittura sulla cartella, cosi la
        // creazione del file temporaneo (primo passo di `scrivi`) fallisce prima che il file finale
        // venga toccato — mai una scrittura parziale, mai il rename atomico.
        cartella.toFile().setWritable(false)
        try {
            assertFailsWith<IOException> { registro.registra(unaVoce(percorso = "/altro/Assemblea.snastro")) }
        } finally {
            cartella.toFile().setWritable(true)
        }

        assertEquals(contenutoPrimaDelGuasto, Files.readString(file))
        assertEquals(listOf(unaVoce()), registro.elenco())
    }

    @Test
    fun `AC-121 un file illeggibile produce un elenco vuoto e il prossimo registra lo riscrive valido`() {
        val file = cartella.resolve("progetti-recenti")
        Files.writeString(file, "questo non e un registro valido ne un formato riconoscibile")
        val registro = RegistroProgettiFile(file)

        assertEquals(emptyList(), registro.elenco())

        registro.registra(unaVoce())
        assertEquals(listOf(unaVoce()), registro.elenco())
    }

    @Test
    fun `AC-121 un file mancante produce un elenco vuoto senza crash`() {
        val registro = RegistroProgettiFile(cartella.resolve("non-esiste-ancora"))
        assertEquals(emptyList(), registro.elenco())
    }

    @Test
    fun `il percorso attraversa verbatim scrittura e rilettura su file - spazi parentesi unicode separatori Windows`() {
        val file = cartella.resolve("progetti-recenti")
        val percorsi = listOf(
            "/Users/utente/Documents/snastro/Riunione (2).snastro",
            "C:\\Utenti\\Città\\Documenti\\snastro\\Città metropolitana (3).snastro",
            "/percorsi/Attività è più «unicode» ünïcödé/Progetto.snastro",
        )
        val voci = percorsi.mapIndexed { i, p ->
            unaVoce(progettoId = ProgettoId("id-${i + 1}"), nome = "Progetto $i", percorso = p)
        }

        val scrittore = RegistroProgettiFile(file)
        voci.forEach(scrittore::registra)

        // Rilettura da un'istanza nuova: prova che il round-trip passa per il file, non per la memoria.
        val lettore = RegistroProgettiFile(file)
        assertEquals(percorsi.toSet(), lettore.elenco().map { it.percorso }.toSet())
    }

    private fun unaVoce(
        progettoId: ProgettoId = ProgettoId("id-1"),
        nome: String = "Consiglio comunale",
        percorso: String = "/progetti/Consiglio comunale.snastro",
        ultimaAttivita: Instant = Instant.parse("2026-09-23T10:15:30Z"),
    ): VoceRegistro = VoceRegistro(progettoId, nome, percorso, numRegistrazioni = 1, ultimaAttivita = ultimaAttivita)
}
