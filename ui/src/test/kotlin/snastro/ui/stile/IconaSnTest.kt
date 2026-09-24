package snastro.ui.stile

import androidx.compose.ui.unit.Density
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/** L704: [IconaSn] must parse a given (icona, density) SVG only once for the whole JVM, not once
 * per composable instance/recomposition. */
class IconaSnTest {
    @Test
    fun `L704 la stessa icona e densita restituiscono lo stesso Painter dalla cache`() {
        val densita = Density(1f)
        val primo = painterIcona(Icona.Play, densita)
        val secondo = painterIcona(Icona.Play, densita)
        assertSame(primo, secondo, "la stessa (icona, densita) deve riusare il Painter gia' caricato")
    }

    @Test
    fun `L704 densita diverse non condividono la voce di cache`() {
        val bassa = painterIcona(Icona.Pause, Density(1f))
        val alta = painterIcona(Icona.Pause, Density(2f))
        assertNotSame(bassa, alta, "densita diverse devono restare voci di cache distinte")
    }
}
