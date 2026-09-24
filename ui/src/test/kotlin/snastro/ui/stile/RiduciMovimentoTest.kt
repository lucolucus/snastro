package snastro.ui.stile

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** AC-565: how the macOS "reduce motion" read is interpreted (pure half, no process started). */
class RiduciMovimentoTest {
    private val mac = "Mac OS X"

    @Test
    fun `AC-565 macOS con valore 1 riduce il movimento`() {
        assertTrue(interpretaRiduciMovimento(mac, 0, "1\n"))
    }

    @Test
    fun `AC-565 macOS con valore 0 consente il movimento`() {
        assertFalse(interpretaRiduciMovimento(mac, 0, "0\n"))
    }

    @Test
    fun `AC-565 macOS con chiave assente consente il movimento`() {
        assertFalse(interpretaRiduciMovimento(mac, 1, ""))
    }

    @Test
    fun `AC-565 sistema non macOS ripiega sul punto fermo`() {
        assertTrue(interpretaRiduciMovimento("Linux", 0, "0"))
        assertTrue(interpretaRiduciMovimento("Windows 11", null, ""))
    }

    @Test
    fun `AC-565 timeout o errore di lettura ripiega sul punto fermo`() {
        assertTrue(interpretaRiduciMovimento(mac, null, ""))
    }

    @Test
    fun `AC-565 valore illeggibile ripiega sul punto fermo`() {
        assertTrue(interpretaRiduciMovimento(mac, 0, "boh"))
    }
}
