package snastro.audio

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SondaFfmpegTest {
    private val sonda = SondaFfmpeg()

    // --- Guard clauses: no native FFmpeg call, safe for the default gate -----------------------

    @Test
    fun `AC-122 un file inesistente lancia AudioIlleggibile`(@TempDir dir: Path) {
        val inesistente = dir.resolve("assente.m4a")
        val errore = assertFailsWith<AudioIlleggibile> { sonda.sonda(inesistente) }
        assertEquals(inesistente, errore.file)
    }

    @Test
    fun `AC-122 una cartella lancia AudioIlleggibile`(@TempDir dir: Path) {
        assertFailsWith<AudioIlleggibile> { sonda.sonda(dir) }
    }

    @Test
    fun `AC-122 un file vuoto lancia AudioIlleggibile`(@TempDir dir: Path) {
        val vuoto = dir.resolve("vuoto.m4a")
        Files.createFile(vuoto)
        assertFailsWith<AudioIlleggibile> { sonda.sonda(vuoto) }
    }

    // --- Real FFmpeg probe: opt-in (native libs, excluded from `check`) -------------------------

    /**
     * The real, native FFmpeg probe path, against a synthetic WAV written by [scriviWavSintetico]
     * (never a committed sample — the profile forbids that; `sample/` is never in the repo). The
     * container format (WAV here, `.m4a`/AAC in production) is incidental to what this AC checks:
     * FFmpeg opens the file, finds the audio stream, reports its duration and reads the file's date.
     */
    @Test
    @Tag("modelli")
    fun `AC-122 un file leggibile da una durata positiva e la data del file`(@TempDir dir: Path) {
        val file = dir.resolve("sintetico.wav")
        scriviWavSintetico(file, durataMs = 1_500)

        val info = sonda.sonda(file)

        assertTrue(info.durataMs > 0, "durata ${info.durataMs} ms")
        // AC-364: a WAV has no `creation_time` tag, so the file's birth time decides.
        val nascita = Files.readAttributes(file, BasicFileAttributes::class.java).creationTime().toInstant()
        assertEquals(nascita.atZone(ZoneId.systemDefault()).toLocalDate(), info.dataRegistrazione)
        assertTrue(info.dataRegistrazione <= LocalDate.now(), "la data del file non e nel futuro")
    }

    /**
     * AC-364 end to end on real FFmpeg: an m4a whose `mvhd` carries `creation_time` (written at test
     * time by [scriviM4aSintetico] — never a committed sample) gets THAT date, not the file's own
     * (today's) birth/modified date; converted in the probe's time zone.
     */
    @Test
    @Tag("modelli")
    fun `AC-364 un m4a con creation_time prende la data dal metadato`(@TempDir dir: Path) {
        val file = dir.resolve("sintetico.m4a")
        scriviM4aSintetico(file, creationTime = "2026-03-21T22:30:00Z")
        val tokyo = Clock.system(ZoneId.of("Asia/Tokyo"))

        val info = SondaFfmpeg(tokyo).sonda(file)

        assertTrue(info.durataMs > 0, "durata ${info.durataMs} ms")
        assertEquals(LocalDate.of(2026, 3, 22), info.dataRegistrazione)
    }

    @Test
    @Tag("modelli")
    fun `AC-364 un m4a con creation_time epoch zero ripiega sulla data di nascita del file`(@TempDir dir: Path) {
        val file = dir.resolve("orologio-non-impostato.m4a")
        scriviM4aSintetico(file, creationTime = "1970-01-01T00:00:00Z")

        val info = sonda.sonda(file)

        val nascita = Files.readAttributes(file, BasicFileAttributes::class.java).creationTime().toInstant()
        assertEquals(nascita.atZone(ZoneId.systemDefault()).toLocalDate(), info.dataRegistrazione)
    }

    @Test
    @Tag("modelli")
    fun `AC-122 un file non audio leggibile come contenitore da FormatoNonSupportato`(@TempDir dir: Path) {
        // A well-formed container FFmpeg opens fine, but with no audio stream: an image, written
        // with the JDK's own PNG encoder (no native lib, no committed fixture).
        val file = dir.resolve("senza-audio.png")
        val immagine = java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB)
        javax.imageio.ImageIO.write(immagine, "png", file.toFile())

        assertFailsWith<FormatoNonSupportato> { sonda.sonda(file) }
    }
}
