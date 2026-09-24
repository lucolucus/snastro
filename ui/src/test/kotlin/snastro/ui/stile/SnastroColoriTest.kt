package snastro.ui.stile

import androidx.compose.ui.graphics.Color
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * AC-551: every [SnastroColori] field equals the token of `tokens.json`, copied to
 * `src/test/resources/design-system/tokens.json` — so the Kotlin can never silently drift from the
 * design system. Small dedicated regex extraction (frugality rung 3/6): the colour tokens'
 * `"name"/"value":{"light","dark"}` shape is fixed, this file's only job is this one test, and a
 * JSON library is not installed in this module — overkill for reading twenty hex pairs.
 */
class SnastroColoriTest {
    private val token: Map<String, Pair<Color, Color>> by lazy { leggiTokenColore() }

    private fun leggiTokenColore(): Map<String, Pair<Color, Color>> {
        val testo = File("src/test/resources/design-system/tokens.json").readText()
        val pattern = Regex(
            "\"name\"\\s*:\\s*\"([a-z0-9-]+)\"\\s*,\\s*\"value\"\\s*:\\s*\\{\\s*" +
                "\"light\"\\s*:\\s*\"(#[0-9a-fA-F]{6})\"\\s*,\\s*\"dark\"\\s*:\\s*\"(#[0-9a-fA-F]{6})\"",
        )
        return pattern.findAll(testo).associate { m ->
            val (nome, chiaro, scuro) = m.destructured
            nome to (esadecimale(chiaro) to esadecimale(scuro))
        }
    }

    private fun esadecimale(hex: String): Color = Color(("FF" + hex.removePrefix("#")).toLong(16))

    private fun verifica(nomeToken: String, chiaro: Color, scuro: Color) {
        val (attesoChiaro, attesoScuro) = checkNotNull(token[nomeToken]) { "token mancante in tokens.json: $nomeToken" }
        assertEquals(attesoChiaro, chiaro, "$nomeToken (chiaro)")
        assertEquals(attesoScuro, scuro, "$nomeToken (scuro)")
    }

    @Test
    fun `AC-551 ColoriChiari e ColoriScuri corrispondono esattamente a tokens json`() {
        verifica("ground", ColoriChiari.ground, ColoriScuri.ground)
        verifica("surface", ColoriChiari.surface, ColoriScuri.surface)
        verifica("raised", ColoriChiari.raised, ColoriScuri.raised)
        verifica("sunken", ColoriChiari.sunken, ColoriScuri.sunken)
        verifica("line", ColoriChiari.line, ColoriScuri.line)
        verifica("line-strong", ColoriChiari.lineStrong, ColoriScuri.lineStrong)
        verifica("ink", ColoriChiari.ink, ColoriScuri.ink)
        verifica("ink-muted", ColoriChiari.inkMuted, ColoriScuri.inkMuted)
        verifica("ink-faint", ColoriChiari.inkFaint, ColoriScuri.inkFaint)
        verifica("accent", ColoriChiari.accent, ColoriScuri.accent)
        verifica("on-accent", ColoriChiari.onAccent, ColoriScuri.onAccent)
        verifica("accent-hover", ColoriChiari.accentHover, ColoriScuri.accentHover)
        verifica("accent-soft", ColoriChiari.accentSoft, ColoriScuri.accentSoft)
        verifica("accent-ink", ColoriChiari.accentInk, ColoriScuri.accentInk)
        verifica("focus", ColoriChiari.focus, ColoriScuri.focus)
        verifica("danger", ColoriChiari.danger, ColoriScuri.danger)
        verifica("danger-soft", ColoriChiari.dangerSoft, ColoriScuri.dangerSoft)
        verifica("warning", ColoriChiari.warning, ColoriScuri.warning)
        verifica("warning-soft", ColoriChiari.warningSoft, ColoriScuri.warningSoft)
        for (n in 1..8) {
            verifica("voice-$n", ColoriChiari.voci[n - 1], ColoriScuri.voci[n - 1])
        }
    }
}
