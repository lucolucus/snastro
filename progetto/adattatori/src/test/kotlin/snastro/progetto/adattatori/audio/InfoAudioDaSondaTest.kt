package snastro.progetto.adattatori.audio

import snastro.audio.InfoFile
import snastro.progetto.applicazione.porte.InfoAudio
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The mapping [SondaAudioFfmpeg] applies to `:audio`'s [InfoFile], in the gate (the real-probe contract is
 * `@Tag("modelli")`): a dropped field, the start time above all, would silently leave it empty on import.
 */
class InfoAudioDaSondaTest {
    @Test
    fun `la sonda porta in InfoAudio durata, data e ora di inizio`() {
        val info = InfoFile(1_500, LocalDate.of(2026, 9, 21), LocalTime.of(22, 22, 13))

        assertEquals(InfoAudio(1_500, LocalDate.of(2026, 9, 21), LocalTime.of(22, 22, 13)), info.inInfoAudio())
    }

    @Test
    fun `senza ora nella sonda InfoAudio non ha ora di inizio`() {
        val info = InfoFile(1_500, LocalDate.of(2026, 9, 21), oraDiInizio = null)

        assertEquals(InfoAudio(1_500, LocalDate.of(2026, 9, 21), oraDiInizio = null), info.inInfoAudio())
    }
}
