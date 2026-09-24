package snastro.audio

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DecodificaFfmpegTest {
    private val decodifica = DecodificaFfmpeg()

    // --- leggiCampioni: reads its own WAV shape directly, no FFmpeg needed ----------------------
    // (default gate: a synthetic WAV written by scriviWavSintetico, no native decode involved).

    @Test
    fun `AC-124 leggiCampioni restituisce fine meno inizio per 16 campioni`(@TempDir dir: Path) {
        val wav = dir.resolve("campione.wav")
        scriviWavSintetico(wav, durataMs = 500)

        listOf(0L to 1L, 0L to 250L, 100L to 400L, 499L to 500L).forEach { (inizio, fine) ->
            val campioni = decodifica.leggiCampioni(wav, inizio, fine)
            assertEquals((fine - inizio).toInt() * 16, campioni.size, "[$inizio, $fine)")
        }
    }

    @Test
    fun `AC-124 un intervallo che finisce oltre la durata e completato con silenzio`(@TempDir dir: Path) {
        val wav = dir.resolve("campione.wav")
        scriviWavSintetico(wav, durataMs = 300)

        val campioni = decodifica.leggiCampioni(wav, 290, 310)

        assertEquals(20 * 16, campioni.size)
        assertTrue(campioni.drop(10 * 16).all { it == 0f }, "oltre la fine solo silenzio")
    }

    @Test
    fun `AC-124 un intervallo che inizia alla fine o oltre restituisce solo silenzio`(@TempDir dir: Path) {
        val wav = dir.resolve("campione.wav")
        scriviWavSintetico(wav, durataMs = 300)

        val campioni = decodifica.leggiCampioni(wav, 300, 320)

        assertEquals(20 * 16, campioni.size)
        assertTrue(campioni.all { it == 0f })
    }

    @Test
    fun `AC-124 leggiCampioni e la stessa fetta di un intervallo piu ampio`(@TempDir dir: Path) {
        val wav = dir.resolve("campione.wav")
        scriviWavSintetico(wav, durataMs = 500)

        val tutti = decodifica.leggiCampioni(wav, 0, 500)
        val fetta = decodifica.leggiCampioni(wav, 125, 300)

        assertEquals(tutti.toList().subList(125 * 16, 300 * 16), fetta.toList())
    }

    // --- decodificaInWav: the real FFmpeg pipeline — opt-in, excluded from `check` ---------------

    /**
     * Source is a synthetic WAV [scriviWavSintetico] wrote (never a committed sample — `sample/` is
     * never in the repo); FFmpeg still does real work: opening it, resampling/re-muxing it through
     * the same decode path a real `.m4a` takes, so this exercises production `decodificaInWav`, not
     * a shortcut.
     */
    @Test
    @Tag("modelli")
    fun `AC-123 decodificaInWav produce un WAV la cui durata coincide con la sonda entro 50 ms`(@TempDir dir: Path) {
        val sorgente = dir.resolve("sorgente.wav")
        scriviWavSintetico(sorgente, durataMs = 2_000)
        val destinazione = dir.resolve("cache/derivato.wav")

        val durataSondata = SondaFfmpeg().sonda(sorgente).durataMs
        decodifica.decodificaInWav(sorgente, destinazione)
        val durataDecodificata = SondaFfmpeg().sonda(destinazione).durataMs

        assertTrue(
            abs(durataDecodificata - durataSondata) <= 50,
            "sonda=$durataSondata decodificata=$durataDecodificata",
        )
    }

    @Test
    @Tag("modelli")
    fun `AC-124 leggiCampioni su un WAV prodotto dalla pipeline reale restituisce fine meno inizio per 16 campioni`(
        @TempDir dir: Path,
    ) {
        val sorgente = dir.resolve("sorgente.wav")
        scriviWavSintetico(sorgente, durataMs = 1_000)
        val destinazione = dir.resolve("cache/derivato.wav")
        decodifica.decodificaInWav(sorgente, destinazione)

        val campioni = decodifica.leggiCampioni(destinazione, 100, 400)

        assertEquals(300 * 16, campioni.size)
    }

    @Test
    @Tag("modelli")
    fun `AC-123 una sorgente illeggibile lancia AudioIlleggibile`(@TempDir dir: Path) {
        val inesistente = dir.resolve("assente.wav")
        kotlin.test.assertFailsWith<AudioIlleggibile> {
            decodifica.decodificaInWav(inesistente, dir.resolve("derivato.wav"))
        }
    }

    @Test
    @Tag("modelli")
    fun `L502d decodificaInWav non lascia un file tmp e sostituisce atomicamente un derivato preesistente`(
        @TempDir dir: Path,
    ) {
        val sorgente = dir.resolve("sorgente.wav")
        scriviWavSintetico(sorgente, durataMs = 500)
        val destinazione = dir.resolve("cache/derivato.wav")

        decodifica.decodificaInWav(sorgente, destinazione) // prima decodifica
        val primaDurata = SondaFfmpeg().sonda(destinazione).durataMs

        // Ridecodifica sullo STESSO derivato (es. una rigenerazione della cache gia' presente): deve
        // sostituirlo, mai lasciare un `.tmp` accanto ne' un derivato a meta' scritto.
        decodifica.decodificaInWav(sorgente, destinazione)

        assertTrue(Files.notExists(dir.resolve("cache/derivato.wav.tmp")), "il file temporaneo non deve restare")
        assertEquals(primaDurata, SondaFfmpeg().sonda(destinazione).durataMs)
    }
}
