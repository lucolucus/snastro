package snastro.audio

import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** AC-I200, AC-I201: the box reader on synthetic header fixtures (bytes built here, never real audio). */
class UdtaDateTest {
    @TempDir
    lateinit var dir: Path

    private fun box(tipo: String, corpo: ByteArray): ByteArray = ByteArrayOutputStream().also {
        DataOutputStream(it).apply {
            writeInt(8 + corpo.size)
            writeBytes(tipo)
            write(corpo)
        }
    }.toByteArray()

    private fun box(tipo: String, vararg figli: ByteArray): ByteArray = box(tipo, unisci(figli))

    private fun unisci(pezzi: Array<out ByteArray>): ByteArray = pezzi.fold(ByteArray(0)) { a, b -> a + b }

    private fun date(testo: String) = box("date", testo.toByteArray())
    private fun ftyp() = box("ftyp", "M4A \u0000\u0000\u0000\u0000".toByteArray())
    private fun mvhd() = box("mvhd", ByteArray(100)) // creation_time zero: never used for the time
    private fun file(vararg byte: ByteArray): Path =
        Files.write(Files.createTempFile(dir, "f", ".m4a"), unisci(byte))

    private val valore = "2026-09-21T20:44:22Z"

    @Test
    fun `AC-I200 trova moov udta date prima di mdat`() {
        val f = file(ftyp(), box("moov", mvhd(), box("udta", date(valore))), box("mdat", ByteArray(64)))
        assertEquals(valore, leggiUdtaDate(f))
    }

    @Test
    fun `AC-I200 trova moov dopo un mdat grande senza leggerlo`() {
        // A real 50 MB sparse mdat with a 64-bit largesize, then moov: reaching it proves mdat is skipped by seek.
        val grande = dir.resolve("grande.m4a")
        val moov = box("moov", mvhd(), box("udta", date(valore)))
        java.io.RandomAccessFile(grande.toFile(), "rw").use { raf ->
            raf.write(ftyp())
            val mdat = 16L + 50_000_000L
            raf.writeInt(1)
            raf.writeBytes("mdat")
            raf.writeLong(mdat)
            raf.setLength(raf.length() + 50_000_000L) // sparse hole
            raf.seek(raf.length())
            raf.write(moov)
        }
        assertEquals(valore, leggiUdtaDate(grande))
    }

    @Test
    fun `AC-I200 con size 0 l'ultimo box arriva fino alla fine del file`() {
        val moov = box("moov", box("udta", date(valore)))
        val ultimo = moov.copyOf().also { repeat(4) { i -> it[i] = 0 } }
        assertEquals(valore, leggiUdtaDate(file(ftyp(), ultimo)))
    }

    @Test
    fun `AC-I200 un box troncato e assente senza eccezioni`() {
        val completo = file(ftyp(), box("moov", box("udta", date(valore))))
        val tutto = Files.readAllBytes(completo)
        for (taglio in listOf(tutto.size - 1, tutto.size - 10, ftyp().size + 4, ftyp().size + 9, 5, 0)) {
            assertNull(leggiUdtaDate(file(tutto.copyOf(taglio))), "tagliato a $taglio")
        }
    }

    @Test
    fun `AC-I200 una size che sfora il file o e negativa e assente`() {
        val sfora = box("moov", box("udta", date(valore))).also { it[3] = 0x7f } // size far beyond the file
        assertNull(leggiUdtaDate(file(ftyp(), sfora)))
        val negativa = ByteArrayOutputStream().also {
            DataOutputStream(it).apply {
                writeInt(1)
                writeBytes("moov")
                writeLong(-5)
            }
        }.toByteArray()
        assertNull(leggiUdtaDate(file(ftyp(), negativa)))
        val minoreDellIntestazione = box("moov", ByteArray(8)).also { it[3] = 4 }
        assertNull(leggiUdtaDate(file(ftyp(), minoreDellIntestazione)))
    }

    @Test
    fun `AC-I200 senza udta, senza date, senza moov o con un date enorme e assente`() {
        assertNull(leggiUdtaDate(file(ftyp(), box("moov", mvhd()))))
        assertNull(leggiUdtaDate(file(ftyp(), box("moov", mvhd(), box("udta", box("xxxx", ByteArray(4)))))))
        assertNull(leggiUdtaDate(file(ftyp(), box("mdat", ByteArray(32)))))
        assertNull(leggiUdtaDate(file(ftyp(), box("moov", box("udta", box("date", ByteArray(100_000)))))))
    }

    @Test
    fun `AC-I200 un file inesistente, una cartella o un file vuoto sono assenti`() {
        assertNull(leggiUdtaDate(dir.resolve("assente.m4a")))
        assertNull(leggiUdtaDate(dir))
        assertNull(leggiUdtaDate(file()))
    }

    @Test
    fun `AC-I201 con solo creation_time in mvhd e nessun udta date il risultato e assente`() {
        val f = file(ftyp(), box("moov", mvhd()), box("mdat", ByteArray(8)))
        assertNull(leggiUdtaDate(f))
        val adesso = Instant.parse("2026-10-01T00:00:00Z")
        val relogio = Clock.fixed(adesso, ZoneOffset.UTC)
        val info = dataRegistrazione(null, "2026-09-22T22:05:47Z", null, Instant.EPOCH, relogio)
        assertNull(info.ora)
    }
}
