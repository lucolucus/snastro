package snastro.ui.stile

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import snastro.ui.SnastroTema
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val NOME_QUARANTA_CARATTERI = "Un nome di quaranta caratteri esatti qui"
private val LARGHEZZA_CONTENITORE_STRETTO = 300.dp

private val CINQUE_FONTI = (1..5).map { FonteChipDati(it, if (it == 1) NOME_QUARANTA_CARATTERI else null, it * 1000L) }

/** AC-S44: [GruppoFonti] — the `space2` gap on one row, wrapping to a new one instead of clipping. */
@OptIn(ExperimentalTestApi::class)
class GruppoFontiTest {
    @Test
    fun `AC-S44 due chip sulla stessa riga sono separate da uno scarto space2`() = runDesktopComposeUiTest {
        setContent {
            SnastroTema {
                GruppoFonti(
                    fonti = listOf(FonteChipDati(1, "Ada", 0), FonteChipDati(2, "Bea", 1000)),
                    modifier = Modifier.width(400.dp),
                )
            }
        }
        val prima = onNodeWithTag("fonte-chip-0").getUnclippedBoundsInRoot()
        val seconda = onNodeWithTag("fonte-chip-1").getUnclippedBoundsInRoot()
        assertEquals(prima.top, seconda.top, "sulla stessa riga i chip condividono la stessa quota")
        val scarto = seconda.left - prima.right
        assertEquals(SnastroMisure.space2.value, scarto.value, absoluteTolerance = 0.5f)
    }

    @Test
    fun `AC-S44 5 chip con un nome di 40 caratteri vanno a capo invece di tagliare`() = runDesktopComposeUiTest(
        width = 1024,
        height = 640,
    ) {
        setContent {
            SnastroTema {
                GruppoFonti(fonti = CINQUE_FONTI, modifier = Modifier.width(LARGHEZZA_CONTENITORE_STRETTO))
            }
        }
        val quote = (0..4).map { onNodeWithTag("fonte-chip-$it").getUnclippedBoundsInRoot().top }
        assertTrue(quote.distinct().size > 1, "spazio insufficiente su una riga: deve andare a capo, non tagliare")
        // "not clipping": every chip's own right edge stays inside the container's own width — a
        // plain Row (no wrap) would instead let later chips overflow past it.
        (0..4).forEach { indice ->
            val destra = onNodeWithTag("fonte-chip-$indice").getUnclippedBoundsInRoot().right
            assertTrue(
                destra <= LARGHEZZA_CONTENITORE_STRETTO,
                "il chip $indice (destra=$destra) esce dal contenitore ($LARGHEZZA_CONTENITORE_STRETTO)",
            )
        }
    }
}
