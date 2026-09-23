package snastro.kernel

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ErroreDominioTest {
    @Test
    fun `AC-7 ErroreDominio e un interfaccia che non estende Throwable`() {
        assertTrue(ErroreDominio::class.java.isInterface)
        assertFalse(Throwable::class.java.isAssignableFrom(ErroreDominio::class.java))
    }

    @Test
    fun `AC-7 una gerarchia di contesto dichiarata in un altro modulo non estende Throwable`() {
        // ErroriDiProva.kt lives in the testFixtures compilation: it compiling proves ErroreDominio
        // is open to other modules (not sealed); the project-wide half is Konsist CR-8.
        val errore: ErroreDominio = ErroreDiProva.Fallito("x")
        assertFalse(errore is Throwable)
        assertFalse(Throwable::class.java.isAssignableFrom(ErroreDiProva::class.java))
    }
}
