package snastro.ui.stile

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
    }

    @Test
    fun `AC-552 schemaMaterial mappa ColoriChiari`() = verificaSchema(ColoriChiari, scuro = false)

    @Test
    fun `AC-552 schemaMaterial mappa ColoriScuri`() = verificaSchema(ColoriScuri, scuro = true)
}
