package snastro.ui.stile

import kotlin.test.Test
import kotlin.test.assertNotNull

/** AC-558: every [Icona] entry resolves to an existing `resources/icone/` SVG. */
class IconaTest {
    @Test
    fun `AC-558 ogni icona risolve a una risorsa esistente`() {
        for (icona in Icona.entries) {
            val risorsa = Thread.currentThread().contextClassLoader.getResource("icone/${icona.file}")
            assertNotNull(risorsa, "risorsa mancante per ${icona.name}: icone/${icona.file}")
        }
    }
}
