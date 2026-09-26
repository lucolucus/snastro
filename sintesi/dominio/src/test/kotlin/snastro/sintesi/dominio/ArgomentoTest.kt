package snastro.sintesi.dominio

import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArgomentoTest {
    @Test
    fun `AC-S1 di toglie gli spazi e un Argomento vuoto o assente e nessun Argomento`() {
        assertEquals("budget 2027", Argomento.di("  budget 2027  ").atteso()?.valore)
        listOf("", "   ", null).forEach { assertNull(Argomento.di(it).atteso(), "'$it'") }
    }

    @Test
    fun `AC-S1 200 caratteri vanno bene e 201 sono ArgomentoTroppoLungo`() {
        assertEquals(200, Argomento.di("a".repeat(200)).atteso()?.valore?.length)
        assertEquals(
            ErroreSintesi.ArgomentoTroppoLungo(201, 200),
            Argomento.di("a".repeat(201)).erroreAtteso<ErroreSintesi.ArgomentoTroppoLungo>(),
        )
        assertEquals(200, Argomento.MASSIMO_CARATTERI)
    }
}
