package snastro.ui.progetti

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Consumer-driven contract of [SceltaCartella] (`tec-shell-ui`, in-process): the fake below proves it
 * green on its own (D1). Unlike every other port in this codebase, `:avvio`'s real implementation
 * (`java.awt.FileDialog`, owned by the app window) does NOT subclass this: showing a native modal
 * dialog and reading back what a human picked cannot be driven headlessly in a test — the same reason
 * [snastro.ui.ApriEsterno] (OS file-manager/default-app integration) carries no contract at all
 * (dev-architecture `#porta-contratto`). This one keeps a thin contract anyway, pinning the two
 * shapes any [SceltaCartella] configuration must honour: a chosen path comes back unchanged, a
 * cancelled pick is `null`.
 */
abstract class SceltaCartellaContratto {
    protected abstract fun con(risultato: String?): SceltaCartella

    @Test
    fun `una cartella scelta viene restituita cosi come e`() {
        assertEquals("/tmp/una-cartella", con("/tmp/una-cartella").scegli("Titolo"))
    }

    @Test
    fun `un annullamento restituisce null`() {
        assertNull(con(null).scegli("Titolo"))
    }
}
