package snastro.progetto.adattatori.porte

import org.junit.jupiter.api.io.TempDir
import snastro.kernel.ProgettoId
import snastro.progetto.applicazione.porte.VoceRegistro
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.Executors
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
    fun `F2 una riga malformata tra righe valide non cancella le altre e il prossimo registra le conserva`() {
        val file = cartella.resolve("progetti-recenti")
        val buona1 = "id-1\tConsiglio comunale\t/progetti/Consiglio comunale.snastro\t1\t2026-09-23T10:15:30Z"
        val campiSbagliati = "solo\tdue-campi"
        val numeroNonValido = "id-2\tAssemblea\t/progetti/Assemblea.snastro\tNON-UN-NUMERO\t2026-09-23T10:15:30Z"
        val dataNonValida = "id-3\tComitato\t/progetti/Comitato.snastro\t1\tnon-una-data"
        val buona2 = "id-4\tConsulta\t/progetti/Consulta.snastro\t2\t2026-09-23T09:00:00Z"
        val righe = listOf(buona1, campiSbagliati, numeroNonValido, dataNonValida, buona2)
        Files.writeString(file, righe.joinToString("\n"))
        val registro = RegistroProgettiFile(file)

        assertEquals(
            setOf("/progetti/Consiglio comunale.snastro", "/progetti/Consulta.snastro"),
            registro.elenco().map { it.percorso }.toSet(),
        )

        registro.registra(unaVoce(progettoId = ProgettoId("id-5"), percorso = "/progetti/Nuovo.snastro"))

        val dopo = RegistroProgettiFile(file)
        assertEquals(
            setOf("/progetti/Consiglio comunale.snastro", "/progetti/Consulta.snastro", "/progetti/Nuovo.snastro"),
            dopo.elenco().map { it.percorso }.toSet(),
        )
    }

    @Test
    fun `F2 un file con BOM UTF-8 iniziale e leggibile`() {
        val file = cartella.resolve("progetti-recenti")
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val riga = "id-1\tConsiglio comunale\t/progetti/Consiglio comunale.snastro\t1\t2026-09-23T10:15:30Z\n"
        Files.write(file, bom + riga.toByteArray(Charsets.UTF_8))
        val registro = RegistroProgettiFile(file)

        assertEquals(listOf(unaVoce()), registro.elenco())
    }

    @Test
    fun `F2 un file non decodificabile come UTF-8 produce un elenco vuoto`() {
        val file = cartella.resolve("progetti-recenti")
        Files.write(file, byteArrayOf(0x80.toByte(), 0x81.toByte(), 0x82.toByte()))
        val registro = RegistroProgettiFile(file)

        assertEquals(emptyList(), registro.elenco())
    }

    @Test
    fun `F8 una scape sconosciuta nel file mantiene entrambi i caratteri`() {
        val file = cartella.resolve("progetti-recenti")
        // riga scritta a mano (mai prodotta da `blocca`): `\x` non e una scape riconosciuta.
        Files.writeString(file, "id-1\tNome\t/percorso/con\\xb.snastro\t1\t2026-09-23T10:15:30Z\n")
        val registro = RegistroProgettiFile(file)

        assertEquals(listOf("/percorso/con\\xb.snastro"), registro.elenco().map { it.percorso })
    }

    @Test
    fun `F4 due thread che registrano in concorrenza non perdono aggiornamenti`() {
        val file = cartella.resolve("progetti-recenti")
        val registro = RegistroProgettiFile(file)
        val perThread = 30
        val esecutore = Executors.newFixedThreadPool(2)
        try {
            val compiti = (0 until perThread).map { i ->
                esecutore.submit {
                    registro.registra(unaVoce(progettoId = ProgettoId("t1-$i"), percorso = "/t1/progetto-$i.snastro"))
                }
            } + (0 until perThread).map { i ->
                esecutore.submit {
                    registro.registra(unaVoce(progettoId = ProgettoId("t2-$i"), percorso = "/t2/progetto-$i.snastro"))
                }
            }
            compiti.forEach { it.get() }
        } finally {
            esecutore.shutdown()
        }

        assertEquals(2 * perThread, registro.elenco().size)
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
