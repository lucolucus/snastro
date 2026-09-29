package snastro.ui.stile

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import snastro.ui.SnastroTema
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val NOME_QUARANTA_CARATTERI = "Un nome di quaranta caratteri esatti qui"
private const val NOME_LUNGO = "Un nome molto lungo di piu' di quaranta caratteri per forzare l andata a capo"
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

    // Rework 1 (verifier HIGH): without a width limit on the name, Row's unweighted measurement
    // gives it the FULL remaining main-axis space before the timecode is even measured — so once
    // `nome` needs to wrap, the timecode is left with near-zero space and Text wraps it one/two
    // characters per line (a tall, near-zero-width column) instead of staying one line — the PNGs
    // show it as a stray ":"/"0" outside the pill. A working chip keeps the timecode on ONE line
    // ([SnastroTipografia.timecode]'s `lineHeight` is 16sp) and fully to the left of the pill's own
    // right edge; the bug blows the timecode's height well past that.
    //
    // Rework 2 (pre-release finding #118, LOW): the container here is a FIXED 300dp width
    // ([LARGHEZZA_CONTENITORE_STRETTO]) regardless of the outer test window — so, unlike a real
    // render-check fixture (AC-571), checking both 1280 and 1024 windows proved nothing a single
    // one didn't already; kept at 1280 only.
    @Test
    fun `AC-S44 il timecode resta su una riga dentro il chip quando il nome va a capo - 40 caratteri`() =
        verificaTimecodeDentroIlChip(NOME_QUARANTA_CARATTERI)

    @Test
    fun `AC-S44 il timecode resta su una riga dentro il chip quando il nome va a capo - nome lungo`() =
        verificaTimecodeDentroIlChip(NOME_LUNGO)

    // Pre-release finding #118 (rework, LOW): the two tests above only proved the TIMECODE stays on
    // one line — neither asserted the NAME itself actually wrapped onto more than one line (as
    // opposed to, say, silently truncating). A single chip alone in the narrow container isolates
    // this from the FIVE-chip wrap-to-a-new-ROW the first test in this file already covers.
    @Test
    fun `AC-S44 rework il nome lungo va a capo su piu righe dentro il chip`() = runDesktopComposeUiTest(
        width = 1280,
        height = 800,
    ) {
        setContent {
            SnastroTema {
                GruppoFonti(
                    fonti = listOf(FonteChipDati(1, NOME_LUNGO, 3_725_000)),
                    modifier = Modifier.width(LARGHEZZA_CONTENITORE_STRETTO),
                )
            }
        }
        val nome = onNodeWithText(NOME_LUNGO).getUnclippedBoundsInRoot()
        val altezzaNome = nome.bottom - nome.top
        assertTrue(
            altezzaNome > ALTEZZA_MASSIMA_TIMECODE_SU_UNA_RIGA,
            "il nome (alto $altezzaNome) non e' andato a capo su piu' righe come atteso in un contenitore stretto",
        )
    }

    private fun verificaTimecodeDentroIlChip(nome: String) = runDesktopComposeUiTest(width = 1280, height = 800) {
        setContent {
            SnastroTema {
                GruppoFonti(
                    fonti = listOf(FonteChipDati(1, nome, 3_725_000)),
                    modifier = Modifier.width(LARGHEZZA_CONTENITORE_STRETTO),
                )
            }
        }
        val chip = onNodeWithTag("fonte-chip-0").getUnclippedBoundsInRoot()
        val timecode = onNodeWithTag(TAG_FONTE_CHIP_TIMECODE).getUnclippedBoundsInRoot()
        assertTrue(
            timecode.right <= chip.right,
            "il timecode (destra=${timecode.right}) esce dal chip (destra=${chip.right})",
        )
        val altezzaTimecode = timecode.bottom - timecode.top
        assertTrue(
            altezzaTimecode <= ALTEZZA_MASSIMA_TIMECODE_SU_UNA_RIGA,
            "il timecode alto $altezzaTimecode non sta su una riga sola: " +
                "e' stato schiacciato in una colonna stretta invece di restare leggibile",
        )
    }
}

// [SnastroTipografia.timecode] has a 16sp lineHeight; a single rendered line is a few dp taller
// once font metrics are applied. The bug wraps the text into many lines instead (48dp/112dp seen
// in the two fixtures above) — comfortably past this bound.
private val ALTEZZA_MASSIMA_TIMECODE_SU_UNA_RIGA = 22.dp
