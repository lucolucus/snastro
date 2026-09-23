package snastro.ui.registrazioni

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * M4: [String.aData] parses the inline date field with `uuuu` + [java.time.format.ResolverStyle.STRICT]
 * — an out-of-range day (31/02) must be REJECTED, never silently rolled into the next month by a SMART
 * resolver.
 */
class RegistrazioniDataTest {
    @Test
    fun `una data valida e analizzata correttamente`() {
        assertEquals(LocalDate.of(2026, 3, 12), "12/03/2026".aData())
    }

    @Test
    fun `31 02 non e una data valida e non viene arrotondata al 28 02`() {
        assertNull("31/02/2026".aData())
    }

    @Test
    fun `29 02 di un anno non bisestile non e valido`() {
        assertNull("29/02/2026".aData()) // 2026 non è bisestile
    }

    @Test
    fun `29 02 di un anno bisestile e valido`() {
        assertEquals(LocalDate.of(2024, 2, 29), "29/02/2024".aData())
    }

    @Test
    fun `un testo parziale mentre si digita non e una data valida`() {
        assertNull("12/03/20".aData())
        assertNull("12".aData())
        assertNull("".aData())
    }

    @Test
    fun `un formato con separatori diversi non e valido`() {
        assertNull("2026-03-12".aData())
    }
}
