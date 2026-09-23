package snastro.progetto.adattatori.porte

import org.junit.jupiter.api.io.TempDir
import snastro.kernel.ProgettoId
import snastro.progetto.applicazione.porte.VoceRegistro
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

/**
 * AC-120 (atomic rewrite), AC-121 (a corrupt file never crashes the caller) and the round-trip
 * guarantee for `percorso` (spaces, parentheses, Unicode, Windows `\` separators — R3, AC-263/264).
 */
class RegistroProgettiFileRobustezzaTest {
    @TempDir
    lateinit var cartella: Path

    @Test
    fun `AC-120 un guasto a meta scrittura del file temporaneo lascia intatto il contenuto precedente`() {
        val file = cartella.resolve("progetti-recenti")
        val registro = RegistroProgettiFile(file)
        registro.registra(unaVoce())
        val contenutoPrimaDelGuasto = Files.readString(file)

        // F1: sostituisce il passo di scrittura del temporaneo con uno che ne scrive SOLO una parte
        // e poi fallisce — un crash a meta scrittura, non prima di scrivere nulla (il permesso negato
        // sulla cartella non e portabile e non prova il caso "meta file").
        val registroGuasto = RegistroProgettiFile(file) { temporaneo, righe ->
            Files.write(temporaneo, righe.take(1))
            throw IOException("crash simulato a meta scrittura del file temporaneo")
        }
        assertFailsWith<IOException> { registroGuasto.registra(unaVoce(percorso = "/altro/Assemblea.snastro")) }

        assertEquals(contenutoPrimaDelGuasto, Files.readString(file))
        assertEquals(listOf(unaVoce()), registro.elenco())
        val presentiInCartella = Files.list(cartella).use { it.toList() }
        assertEquals(listOf(file), presentiInCartella) // nessun *.tmp abbandonato
    }

    @Test
    fun `una scrittura diretta senza file temporaneo lascerebbe contenuto parziale in caso di guasto`() {
        // Contrasto con AC-120: SENZA la disciplina scrivi-su-temporaneo-poi-rinomina, un guasto a
        // meta scrittura mischia il contenuto vecchio e quello nuovo nello stesso file finale.
        val file = cartella.resolve("scrittura-diretta")
        val originale = "contenuto-originale-integro"
        Files.writeString(file, originale)

        try {
            FileChannel.open(file, StandardOpenOption.WRITE).use { canale ->
                canale.write(ByteBuffer.wrap("NUOVO".toByteArray(Charsets.UTF_8)))
                throw IOException("crash simulato a meta scrittura diretta")
            }
        } catch (ignored: IOException) {
            // atteso: la scrittura diretta e stata interrotta a meta
        }

        val dopoIlGuasto = Files.readString(file)
        assertNotEquals(originale, dopoIlGuasto) // non e piu il contenuto di partenza...
        assertNotEquals("NUOVO", dopoIlGuasto) // ...ne il contenuto nuovo per intero: e un mischione
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
    fun `F7 un tmp abbandonato da una scrittura precedente e spazzato via alla scrittura successiva`() {
        val file = cartella.resolve("progetti-recenti")
        val abbandonato = cartella.resolve("progetti-recenti1234567890.tmp")
        Files.writeString(abbandonato, "resti di un crash precedente")
        val registro = RegistroProgettiFile(file)

        registro.registra(unaVoce())

        assertEquals(false, Files.exists(abbandonato))
        assertEquals(listOf(unaVoce()), registro.elenco())
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
