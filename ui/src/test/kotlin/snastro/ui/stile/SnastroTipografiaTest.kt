package snastro.ui.stile

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals

/** AC-555: the 11 type styles, exact size/line-height/weight/letter-spacing/family/feature-settings. */
class SnastroTipografiaTest {
    @Suppress("LongParameterList") // one parameter per documented dimension of a type style (AC-555)
    private fun verifica(
        stile: TextStyle,
        famiglia: androidx.compose.ui.text.font.FontFamily,
        peso: FontWeight,
        dimensione: androidx.compose.ui.unit.TextUnit,
        interlinea: androidx.compose.ui.unit.TextUnit,
        tracciato: TextUnit = TextUnit.Unspecified,
        cifreTabulari: Boolean = false,
    ) {
        assertEquals(famiglia, stile.fontFamily)
        assertEquals(peso, stile.fontWeight)
        assertEquals(dimensione, stile.fontSize)
        assertEquals(interlinea, stile.lineHeight)
        assertEquals(tracciato, stile.letterSpacing)
        assertEquals(if (cifreTabulari) "tnum" else null, stile.fontFeatureSettings)
    }

    @Test
    fun `AC-555 display 26 32 semibold interfaccia con tracciato negativo`() {
        verifica(SnastroTipografiaDefault.display, CarattereInterfaccia, FontWeight.SemiBold, 26.sp, 32.sp, (-0.01).em)
    }

    @Test
    fun `AC-555 title 18 24 semibold interfaccia`() {
        verifica(SnastroTipografiaDefault.title, CarattereInterfaccia, FontWeight.SemiBold, 18.sp, 24.sp)
    }

    @Test
    fun `AC-555 heading 14 20 semibold interfaccia`() {
        verifica(SnastroTipografiaDefault.heading, CarattereInterfaccia, FontWeight.SemiBold, 14.sp, 20.sp)
    }

    @Test
    fun `AC-555 body 14 20 normale interfaccia`() {
        verifica(SnastroTipografiaDefault.body, CarattereInterfaccia, FontWeight.Normal, 14.sp, 20.sp)
    }

    @Test
    fun `AC-555 label 13 18 medium interfaccia`() {
        verifica(SnastroTipografiaDefault.label, CarattereInterfaccia, FontWeight.Medium, 13.sp, 18.sp)
    }

    @Test
    fun `AC-555 caption 12 16 normale interfaccia`() {
        verifica(SnastroTipografiaDefault.caption, CarattereInterfaccia, FontWeight.Normal, 12.sp, 16.sp)
    }

    @Test
    fun `AC-555 overline 11 14 semibold con tracciato 0 06em`() {
        verifica(SnastroTipografiaDefault.overline, CarattereInterfaccia, FontWeight.SemiBold, 11.sp, 14.sp, 0.06.em)
    }

    @Test
    fun `AC-555 figure 22 26 semibold cifre tabulari`() {
        verifica(
            SnastroTipografiaDefault.figure,
            CarattereInterfaccia,
            FontWeight.SemiBold,
            22.sp,
            26.sp,
            cifreTabulari = true,
        )
    }

    @Test
    fun `AC-555 transcript 16 26 normale lettura`() {
        verifica(SnastroTipografiaDefault.transcript, CarattereLettura, FontWeight.Normal, 16.sp, 26.sp)
    }

    @Test
    fun `AC-555 abstract 15 24 normale lettura`() {
        verifica(SnastroTipografiaDefault.abstract, CarattereLettura, FontWeight.Normal, 15.sp, 24.sp)
    }

    @Test
    fun `AC-555 timecode 12 16 medium mono cifre tabulari`() {
        verifica(
            SnastroTipografiaDefault.timecode,
            CarattereDati,
            FontWeight.Medium,
            12.sp,
            16.sp,
            cifreTabulari = true,
        )
    }

    @Test
    fun `AC-555 la Typography Material mappa gli stili documentati`() {
        val typography = tipografiaMaterial(SnastroTipografiaDefault)
        assertEquals(SnastroTipografiaDefault.body, typography.bodyMedium)
        assertEquals(SnastroTipografiaDefault.label, typography.labelLarge)
        assertEquals(SnastroTipografiaDefault.title, typography.titleMedium)
        assertEquals(SnastroTipografiaDefault.display, typography.headlineSmall)
        assertEquals(SnastroTipografiaDefault.caption, typography.bodySmall)
    }
}
