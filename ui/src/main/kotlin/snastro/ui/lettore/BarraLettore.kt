package snastro.ui.lettore

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import snastro.ui.formattaDurata
import snastro.ui.palette
import snastro.ui.stile.BottonePlay
import snastro.ui.stile.Icona
import snastro.ui.stile.IconaSn
import snastro.ui.stile.LocalSnastroColori
import snastro.ui.stile.LocalSnastroTipografia
import snastro.ui.stile.SnastroColori
import snastro.ui.stile.SnastroMisure

private const val ALPHA_NON_DISPONIBILE = 0.45f
private val ALTEZZA_SCRUBBER: Dp = 28.dp
private val ALTEZZA_CORSIA: Dp = 8.dp
private val RAGGIO_CORSIA: Dp = 2.dp
private val LARGHEZZA_MINIMA_CORSIA: Dp = 1.dp
private val Y_TRACK: Dp = 16.dp
private val ALTEZZA_TRACK: Dp = 4.dp
private val RAGGIO_TESTA: Dp = 7.dp
private val SPESSORE_ANELLO_TESTA: Dp = 2.dp
private val DIMENSIONE_INDICATORE_CARICAMENTO = 20.dp

/**
 * AC-579: the shared player bar — raised card, `BottonePlay` grande for transport (Replay/Forward and
 * the rate chip stay out of part A: [AzioniLettore] has no seek, the port no rate — B5), current time
 * in `timecode` ink, and a scrubber (sunken track + accent played part + a ringed knob) with the voice
 * lanes above it built from [corsie] (the embedding screen's own [snastro.ui.registrazione.SegmentoRiga]
 * projection — this component never reads a Segmento itself, RC-2). AC-217: while [stato] is
 * [LettoreUiStato.NonDisponibile] every control sits at 45% opacity, disabled, with a `warning` caption
 * under the bar.
 */
// LongMethod/LongParameterList: one shared player bar covering every AC-579 state (transport, timecode,
// scrubber, the voice lanes, the audio-missing caption) — RC-2 thin view, not a knob to trim.
@Suppress("LongMethod", "LongParameterList")
@Composable
fun BarraLettore(
    stato: LettoreUiStato,
    onRiproduci: () -> Unit,
    onPausa: () -> Unit,
    modifier: Modifier = Modifier,
    durataMs: Long? = null,
    corsie: List<CorsiaVoce> = emptyList(),
) {
    val colori = LocalSnastroColori.current
    val nonDisponibile = stato as? LettoreUiStato.NonDisponibile
    // L471d: a transient Errore keeps play enabled — only NonDisponibile/Caricamento disable it.
    val errore = stato as? LettoreUiStato.Errore
    val inRiproduzione = (stato as? LettoreUiStato.Pronto)?.inRiproduzione == true
    val posizioneMs = (stato as? LettoreUiStato.Pronto)?.posizioneMs ?: 0L
    val abilitato = stato !is LettoreUiStato.NonDisponibile && stato !is LettoreUiStato.Caricamento
    Column(modifier = modifier.fillMaxWidth().testTag("lettore-barra")) {
        Surface(
            shape = RoundedCornerShape(SnastroMisure.radiusCard),
            color = colori.raised,
            contentColor = colori.ink,
            border = BorderStroke(1.dp, colori.line),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (stato is LettoreUiStato.Caricamento) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(SnastroMisure.space4)
                        .size(DIMENSIONE_INDICATORE_CARICAMENTO)
                        .testTag("lettore-caricamento"),
                )
            } else {
                Row(
                    modifier = Modifier
                        .padding(horizontal = SnastroMisure.space4, vertical = SnastroMisure.space3)
                        .alpha(if (nonDisponibile != null) ALPHA_NON_DISPONIBILE else 1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SnastroMisure.space3),
                ) {
                    BottonePlay(
                        inRiproduzione = inRiproduzione,
                        onClick = if (inRiproduzione) onPausa else onRiproduci,
                        grande = true,
                        abilitato = abilitato,
                        modifier = Modifier.testTag(if (inRiproduzione) "lettore-pausa" else "lettore-riproduci"),
                    )
                    Text(
                        text = formattaDurata(posizioneMs),
                        style = LocalSnastroTipografia.current.timecode,
                        color = colori.ink,
                        modifier = Modifier.testTag("lettore-posizione"),
                    )
                    Scrubber(
                        corsie = corsie,
                        durataMs = durataMs,
                        posizioneMs = posizioneMs,
                        colori = colori,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        nonDisponibile?.let { nd ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SnastroMisure.space1),
                modifier = Modifier.padding(top = SnastroMisure.space1).testTag("lettore-non-disponibile"),
            ) {
                IconaSn(Icona.Alert, descrizione = null, tinta = colori.warning, dimensione = SnastroMisure.iconS)
                Text(text = nd.messaggio, style = LocalSnastroTipografia.current.caption, color = colori.warning)
            }
        }
        // L471d: same caption row as NonDisponibile, but the play control ABOVE stays enabled — this is
        // a transient fault, not "the source is unavailable".
        errore?.let { err ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SnastroMisure.space1),
                modifier = Modifier.padding(top = SnastroMisure.space1).testTag("lettore-errore"),
            ) {
                IconaSn(Icona.Alert, descrizione = null, tinta = colori.warning, dimensione = SnastroMisure.iconS)
                Text(text = err.messaggio, style = LocalSnastroTipografia.current.caption, color = colori.warning)
            }
        }
    }
}

/**
 * AC-579: [corsie] painted proportionally to [durataMs] (min 1dp wide — a Canvas, not one `Box` per
 * lane, so an overlapping/dense transcript never forces a relayout); no lane at all when [durataMs] is
 * unknown (S2 R0 context, no transcript). The played fraction and the knob are likewise omitted then —
 * a static, unclickable scrubber (no seek in part A, [AzioniLettore] carries none yet).
 */
@Composable
private fun Scrubber(
    corsie: List<CorsiaVoce>,
    durataMs: Long?,
    posizioneMs: Long,
    colori: SnastroColori,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.height(ALTEZZA_SCRUBBER).testTag("lettore-scrubber")) {
        val larghezza = size.width
        val minimo = LARGHEZZA_MINIMA_CORSIA.toPx()
        val raggioCorsia = CornerRadius(RAGGIO_CORSIA.toPx())
        if (durataMs != null && durataMs > 0) {
            corsie.forEach { corsia ->
                val inizio = (corsia.inizioMs.toFloat() / durataMs).coerceIn(0f, 1f)
                val fine = (corsia.fineMs.toFloat() / durataMs).coerceIn(0f, 1f)
                val x = inizio * larghezza
                val larghezzaCorsia = maxOf((fine - inizio) * larghezza, minimo)
                drawRoundRect(
                    color = palette(corsia.voceId, colori),
                    topLeft = Offset(x, 0f),
                    size = Size(larghezzaCorsia, ALTEZZA_CORSIA.toPx()),
                    cornerRadius = raggioCorsia,
                )
            }
        }
        val yTraccia = Y_TRACK.toPx()
        val altezzaTraccia = ALTEZZA_TRACK.toPx()
        val raggioPillola = CornerRadius(altezzaTraccia / 2)
        drawRoundRect(
            color = colori.sunken,
            topLeft = Offset(0f, yTraccia),
            size = Size(larghezza, altezzaTraccia),
            cornerRadius = raggioPillola,
        )
        if (durataMs != null && durataMs > 0) {
            val frazione = (posizioneMs.toFloat() / durataMs).coerceIn(0f, 1f)
            val xTesta = frazione * larghezza
            drawRoundRect(
                color = colori.accent,
                topLeft = Offset(0f, yTraccia),
                size = Size(xTesta, altezzaTraccia),
                cornerRadius = raggioPillola,
            )
            val centroTesta = Offset(xTesta, yTraccia + altezzaTraccia / 2)
            val raggioTesta = RAGGIO_TESTA.toPx()
            drawCircle(color = colori.raised, radius = raggioTesta, center = centroTesta)
            drawCircle(
                color = colori.accentInk,
                radius = raggioTesta - SPESSORE_ANELLO_TESTA.toPx() / 2,
                center = centroTesta,
                style = Stroke(width = SPESSORE_ANELLO_TESTA.toPx()),
            )
        }
    }
}
