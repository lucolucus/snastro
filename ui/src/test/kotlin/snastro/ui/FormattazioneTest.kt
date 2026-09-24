package snastro.ui

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class FormattazioneTest {
    @Test
    fun `AC-557 75003 ms sotto l ora diventa 1 15 senza zero iniziale sui minuti`() {
        assertEquals("1:15", formattaDurata(75_003))
    }

    @Test
    fun `AC-557 192000 ms diventa 3 12`() {
        assertEquals("3:12", formattaDurata(192_000))
    }

    @Test
    fun `AC-557 3599000 ms resta sotto l ora e diventa 59 59`() {
        assertEquals("59:59", formattaDurata(3_599_000))
    }

    @Test
    fun `AC-557 3600000 ms e esattamente un ora e diventa 1 00 00`() {
        assertEquals("1:00:00", formattaDurata(3_600_000))
    }

    @Test
    fun `AC-557 4503000 ms sopra l ora diventa 1 15 03`() {
        assertEquals("1:15:03", formattaDurata(4_503_000))
    }

    @Test
    fun `AC-557 forma estesa sotto l ora diventa 52 min`() {
        assertEquals("52 min", formattaDurataEstesa(3_120_000))
    }

    @Test
    fun `AC-557 forma estesa sopra l ora diventa 1 h 04 min`() {
        assertEquals("1 h 04 min", formattaDurataEstesa(3_840_000))
    }

    @Test
    fun `AC-557 2026-09-12 diventa 12 09 2026 (le date non cambiano)`() {
        assertEquals("12/09/2026", formattaData(LocalDate.of(2026, 9, 12)))
    }

    @Test
    fun `AC-227 487170055 byte diventano 464 6 MB`() {
        assertEquals("464.6 MB", formattaByte(487_170_055))
    }

    @Test
    fun `AC-227 0 byte diventano 0 0 MB`() {
        assertEquals("0.0 MB", formattaByte(0))
    }
}
