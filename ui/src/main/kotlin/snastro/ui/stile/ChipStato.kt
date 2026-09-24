package snastro.ui.stile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import snastro.ui.formattaDurata

private val ALTEZZA_CHIP: Dp = 24.dp
private val PADDING_ORIZZONTALE_CHIP: Dp = 10.dp
private val SCARTO_CHIP: Dp = 6.dp
private val DIAMETRO_PALLINO: Dp = 8.dp

private data class ContenutoChip(val sfondo: Color, val bordo: BorderStroke?, val testo: Color)

/**
 * AC-565: a pill-shaped status chip — one word, one icon (except `DaTrascrivere`), one shape per
 * state. The `running` pulse degrades to a constant dot: Compose Desktop has no way to query the OS
 * "reduce motion" setting without extra native bridging (no ADR wires this yet), and the AC itself
 * allows a constant dot when the platform can't report the setting.
 */
@Composable
public fun ChipStato(tipo: TipoChipStato, modifier: Modifier = Modifier) {
    val colori = LocalSnastroColori.current
    val contenuto = when (tipo) {
        is TipoChipStato.DaTrascrivere ->
            ContenutoChip(Color.Transparent, BorderStroke(1.dp, colori.lineStrong), colori.inkMuted)
        is TipoChipStato.InCoda -> ContenutoChip(colori.sunken, null, colori.ink)
        is TipoChipStato.InCorso -> ContenutoChip(colori.accentSoft, null, colori.ink)
        is TipoChipStato.Trascritta -> ContenutoChip(Color.Transparent, null, colori.inkMuted)
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
            modifier = Modifier.padding(horizontal = PADDING_ORIZZONTALE_CHIP),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SCARTO_CHIP),
        ) {
            ContenutoChipStato(tipo, contenuto.testo)
        }
    }
}

@Composable
private fun ContenutoChipStato(tipo: TipoChipStato, testo: Color) {
    when (tipo) {
        is TipoChipStato.DaTrascrivere -> Text("Da trascrivere", style = LocalSnastroTipografia.current.label)
        is TipoChipStato.InCoda -> {
            IconaSn(Icona.Clock, descrizione = null, tinta = testo, dimensione = SnastroMisure.iconS)
            Text("In coda · ${tipo.posizione}", style = LocalSnastroTipografia.current.label)
        }
        is TipoChipStato.InCorso -> {
            val tipografia = LocalSnastroTipografia.current
            PallinoInCorso()
            Text(text = tipo.fase, style = tipografia.label)
            Text(
                text = formattaDurata(tipo.trascorsoMs),
                style = tipografia.timecode,
                color = LocalSnastroColori.current.inkMuted,
            )
        }
        is TipoChipStato.Trascritta -> {
            IconaSn(
                Icona.Check,
                descrizione = null,
                tinta = LocalSnastroColori.current.accentInk,
                dimensione = SnastroMisure.iconS,
            )
            Text("Trascritta", style = LocalSnastroTipografia.current.label)
        }
        is TipoChipStato.NonRiuscita -> {
            IconaSn(Icona.Alert, descrizione = null, tinta = testo, dimensione = SnastroMisure.iconS)
            Text("Non riuscita", style = LocalSnastroTipografia.current.label)
        }
        is TipoChipStato.Avviso -> {
            IconaSn(tipo.icona, descrizione = null, tinta = testo, dimensione = SnastroMisure.iconS)
            Text(tipo.testo, style = LocalSnastroTipografia.current.label)
        }
    }
}

@Composable
private fun PallinoInCorso() {
    Box(Modifier.size(DIAMETRO_PALLINO).background(LocalSnastroColori.current.accent, CircleShape))
}
