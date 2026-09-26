package snastro.ui.stile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import snastro.ui.formattaDurata

private val ALTEZZA_CHIP: Dp = 24.dp
private val PADDING_ORIZZONTALE_CHIP: Dp = 10.dp
private val PADDING_INIZIALE_TRASCRITTA: Dp = 6.dp
private val SCARTO_CHIP: Dp = 6.dp
private val DIAMETRO_PALLINO: Dp = 8.dp
private val ICONA_CHIP: Dp = 14.dp

private data class ContenutoChip(
    val sfondo: Color,
    val bordo: BorderStroke?,
    val testo: Color,
    val padding: PaddingValues = PaddingValues(horizontal = PADDING_ORIZZONTALE_CHIP),
)

/**
 * AC-565: a pill-shaped status chip, `control`-independent 24dp high — one word (`caption` size
 * with a `Medium` weight: 12sp/16sp/500, never the `label` style), one icon (except
 * `DaTrascrivere`, 14dp — smaller than the shared `iconS`), one shape per state. The `running`
 * pulse (1.6s alpha 1→0.35→1) is skipped in favour of a constant dot when
 * [LocalRiduciMovimento] is on (OS "reduce motion", or the platform can't report it).
 */
@Composable
public fun ChipStato(tipo: TipoChipStato, modifier: Modifier = Modifier) {
    val colori = LocalSnastroColori.current
    val contenuto = when (tipo) {
        is TipoChipStato.DaTrascrivere ->
            ContenutoChip(Color.Transparent, BorderStroke(1.dp, colori.lineStrong), colori.inkMuted)
        is TipoChipStato.InCoda -> ContenutoChip(colori.sunken, null, colori.ink)
        is TipoChipStato.InCorso -> ContenutoChip(colori.accentSoft, null, colori.ink)
        is TipoChipStato.Trascritta ->
            ContenutoChip(
                Color.Transparent,
                null,
                colori.inkMuted,
                PaddingValues(start = PADDING_INIZIALE_TRASCRITTA, end = PADDING_ORIZZONTALE_CHIP),
            )
        is TipoChipStato.NonRiuscita -> ContenutoChip(colori.dangerSoft, null, colori.danger)
        is TipoChipStato.Avviso -> ContenutoChip(colori.warningSoft, null, colori.warning)
    }
    Surface(
        modifier = modifier.height(ALTEZZA_CHIP),
        shape = SnastroMisure.radiusPill,
        color = contenuto.sfondo,
        contentColor = contenuto.testo,
        border = contenuto.bordo,
    ) {
        Row(
            modifier = Modifier.padding(contenuto.padding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SCARTO_CHIP),
        ) {
            ContenutoChipStato(tipo, contenuto.testo)
        }
    }
}

@Composable
private fun ContenutoChipStato(tipo: TipoChipStato, testo: Color) {
    val stileEtichetta = LocalSnastroTipografia.current.caption.copy(fontWeight = FontWeight.Medium)
    when (tipo) {
        is TipoChipStato.DaTrascrivere -> Text("Da trascrivere", style = stileEtichetta)
        is TipoChipStato.InCoda -> {
            IconaSn(Icona.Clock, descrizione = null, tinta = testo, dimensione = ICONA_CHIP)
            Text("In coda · ${tipo.posizione}", style = stileEtichetta)
        }
        is TipoChipStato.InCorso -> {
            PallinoInCorso()
            Text(text = tipo.fase, style = stileEtichetta)
            Text(
                text = formattaDurata(tipo.trascorsoMs),
                style = LocalSnastroTipografia.current.timecode,
                color = LocalSnastroColori.current.inkMuted,
            )
        }
        is TipoChipStato.Trascritta -> {
            IconaSn(
                Icona.Check,
                descrizione = null,
                tinta = LocalSnastroColori.current.accentInk,
                dimensione = ICONA_CHIP,
            )
            Text("Trascritta", style = stileEtichetta)
        }
        is TipoChipStato.NonRiuscita -> {
            IconaSn(Icona.Alert, descrizione = null, tinta = testo, dimensione = ICONA_CHIP)
            Text(tipo.testo, style = stileEtichetta)
        }
        is TipoChipStato.Avviso -> {
            IconaSn(tipo.icona, descrizione = null, tinta = testo, dimensione = ICONA_CHIP)
            Text(tipo.testo, style = stileEtichetta)
        }
    }
}

/**
 * AC-565: the shared [PallinoPulsante] (1.6s round-trip pulse, a constant dot when
 * [LocalRiduciMovimento] is on) at this chip's own colour/size.
 */
@Composable
private fun PallinoInCorso() {
    PallinoPulsante(
        colore = LocalSnastroColori.current.accent,
        diametro = DIAMETRO_PALLINO,
        // testTag (L714b): the only way to sample this exact dot's pixel across two clock ticks and
        // prove the pulse actually animates when `LocalRiduciMovimento` is off — the presenter-level
        // tests can't see an `alpha` value, only a render check can (dev-architecture #test).
        modifier = Modifier.testTag(TAG_PALLINO_IN_CORSO),
    )
}

/** L714b: exposed so the render-check test can locate the animated dot without guessing pixel math. */
internal const val TAG_PALLINO_IN_CORSO: String = "chip-pallino-in-corso"
