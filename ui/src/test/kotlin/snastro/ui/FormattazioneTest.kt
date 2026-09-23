package snastro.ui

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class FormattazioneTest {
    @Test
    fun `AC-179 75003 ms diventa 01 minuti 15 secondi`() {
        assertEquals("01:15", formattaDurata(75_003))
    }

    @Test
    fun `AC-179 4503000 ms diventa 75 minuti 03 secondi`() {
        assertEquals("75:03", formattaDurata(4_503_000))
    }

    @Test
    fun `AC-179 2026-09-12 diventa 12 09 2026`() {
        assertEquals("12/09/2026", formattaData(LocalDate.of(2026, 9, 12)))
    }
}
