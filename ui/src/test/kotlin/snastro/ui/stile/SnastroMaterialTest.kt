package snastro.ui.stile

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals

/** AC-552: both schemes built, mapping asserted per README §'Implementazione in Compose'. */
class SnastroMaterialTest {
    private fun verificaSchema(colori: SnastroColori, scuro: Boolean) {
        val schema = schemaMaterial(colori, scuro)
        assertEquals(colori.accent, schema.primary)
        assertEquals(colori.onAccent, schema.onPrimary)
        assertEquals(colori.ground, schema.background)
        assertEquals(colori.surface, schema.surface)
        assertEquals(colori.raised, schema.surfaceContainer)
        assertEquals(colori.sunken, schema.surfaceVariant)
        assertEquals(colori.lineStrong, schema.outline)
        assertEquals(colori.line, schema.outlineVariant)
        assertEquals(colori.danger, schema.error)
        assertEquals(colori.ink, schema.onSurface)
        assertEquals(colori.inkMuted, schema.onSurfaceVariant)
        // Review MED-5: every other slot too, so nothing falls back to Material's default purple.
        assertEquals(colori.accentSoft, schema.primaryContainer)
        assertEquals(colori.ink, schema.onPrimaryContainer)
        assertEquals(colori.accentInk, schema.secondary)
        assertEquals(colori.raised, schema.onSecondary)
        assertEquals(colori.accentSoft, schema.secondaryContainer)
        assertEquals(colori.ink, schema.onSecondaryContainer)
        assertEquals(colori.accentInk, schema.tertiary)
        assertEquals(colori.ink, schema.onBackground)
        assertEquals(colori.surface, schema.surfaceContainerLowest)
        assertEquals(colori.surface, schema.surfaceContainerLow)
        assertEquals(colori.raised, schema.surfaceContainerHigh)
        assertEquals(colori.raised, schema.surfaceContainerHighest)
        assertEquals(Color.Transparent, schema.surfaceTint)
        assertEquals(colori.dangerSoft, schema.errorContainer)
        assertEquals(colori.danger, schema.onErrorContainer)
        assertEquals(colori.ink, schema.inverseSurface)
        assertEquals(colori.surface, schema.inverseOnSurface)
        assertEquals(Color.Black, schema.scrim.copy(alpha = 1f))
    }

    @Test
    fun `AC-552 schemaMaterial mappa ColoriChiari`() = verificaSchema(ColoriChiari, scuro = false)

    @Test
    fun `AC-552 schemaMaterial mappa ColoriScuri`() = verificaSchema(ColoriScuri, scuro = true)

    @Test
    fun `AC-555 tipografiaMaterial mappa anche le taglie restanti nella famiglia ui`() {
        val t = tipografiaMaterial(SnastroTipografiaDefault)
        assertEquals(SnastroTipografiaDefault.body, t.bodyLarge)
        assertEquals(SnastroTipografiaDefault.label, t.labelMedium)
        assertEquals(SnastroTipografiaDefault.caption.copy(fontWeight = FontWeight.Medium), t.labelSmall)
        assertEquals(SnastroTipografiaDefault.heading, t.titleSmall)
        assertEquals(SnastroTipografiaDefault.title, t.titleLarge)
    }
}
