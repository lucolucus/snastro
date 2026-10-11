package snastro.ui.registrazione

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import snastro.kernel.SegmentoId
import snastro.ui.formattaDurata
import snastro.ui.stile.BottoneSn
import snastro.ui.stile.Icona
import snastro.ui.stile.IconaSn
import snastro.ui.stile.LocalSnastroColori
import snastro.ui.stile.LocalSnastroTipografia
import snastro.ui.stile.PallinoVoce
import snastro.ui.stile.SnastroMisure
import snastro.ui.stile.VarianteBottone
import snastro.ui.testi.DESCRIZIONE_SELEZIONA_FRASE
import snastro.ui.testi.ETICHETTA_ANNULLA
import snastro.ui.testi.MESSAGGIO_COMANDO_IN_ATTESA
import snastro.ui.testi.TOOLTIP_FRASE_CONFERMATA

private val LARGHEZZA_GUTTER = 56.dp
private val DIMENSIONE_INDICATORE = 14.dp
private val DIMENSIONE_CASELLA = 16.dp
private val PADDING_RIGA_V = 10.dp

/** AC-582: 56dp timecode gutter (a 16dp checked box replaces it once selected) · who line (hidden on a
 * consecutive Segmento of the same Voce) · text in the reading face. Click plays; hover/playing/selected
 * are the presenter's own [SegmentoRiga] flags, never re-decided here. */
// LongMethod/CyclomaticComplexMethod: one transcript row with every AC-582 visual branch (gutter,
// who line, pin, pending naming) inline — RC-2 thin view, splitting further would scatter one row.
// LongParameterList: one parameter per row-level decision the presenter/panel already made.
@Suppress("LongMethod", "CyclomaticComplexMethod", "LongParameterList")
@Composable
internal fun SegmentoItem(
    segmento: SegmentoRiga,
    azioni: AzioniRegistrazione,
    selezione: Boolean?,
    mostraChi: Boolean,
    haNome: Boolean,
) {
    val colori = LocalSnastroColori.current
    val tipografia = LocalSnastroTipografia.current
    val interazione = remember { MutableInteractionSource() }
    val hover by interazione.collectIsHoveredAsState()
    val selezionato = selezione == true
    val sfondo = when {
        selezionato || segmento.inRiproduzione -> colori.accentSoft
        hover -> colori.sunken
        else -> Color.Transparent
    }
    val n = segmento.segmentoId.numero
    Surface(
        onClick = { azioni.riproduciSegmento(segmento.segmentoId) },
        shape = RoundedCornerShape(SnastroMisure.radiusControl),
        color = sfondo,
        contentColor = colori.ink,
        border = if (selezionato) BorderStroke(1.5.dp, colori.accentInk) else null,
        interactionSource = interazione,
        modifier = Modifier.fillMaxWidth().testTag("registrazione-segmento-$n"),
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.padding(horizontal = SnastroMisure.space3, vertical = PADDING_RIGA_V),
        ) {
            Box(modifier = Modifier.width(LARGHEZZA_GUTTER), contentAlignment = Alignment.TopEnd) {
                if (selezione != null && selezionato) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // L761c: a confirmed, consecutive row (who line hidden) used to lose its pin the
                        // moment it was selected — the checkbox replaced the whole gutter content that
                        // drew it. Same rule as the unselected branch below: draw it next to whatever
                        // sits in the gutter, here the checkbox.
                        if (!mostraChi && segmento.confermato) {
                            PuntinaConfermata(n)
                            Spacer(modifier = Modifier.width(SnastroMisure.space1))
                        }
                        CasellaSelezionata(segmento.segmentoId, azioni)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // AC-528/AC-582: a confirmed Segmento keeps its pin even when the who line is
                        // hidden (a consecutive row of the same Voce) — drawn here, next to the timecode,
                        // instead of silently disappearing (rework cycle 1, HIGH-4).
                        if (!mostraChi && segmento.confermato) {
                            PuntinaConfermata(n)
                            Spacer(modifier = Modifier.width(SnastroMisure.space1))
                        }
                        Text(
                            text = formattaDurata(segmento.inizioMs),
                            style = tipografia.timecode,
                            color = if (segmento.inRiproduzione) colori.accentInk else colori.inkMuted,
                            modifier = if (selezione != null) {
                                // AC-209/AC-582 accessibility (rework cycle 1, MED-7): the same toggle
                                // [azioni.selezionaSegmento] already offers, exposed as a real checkbox
                                // (`role`, `value`, a label) — not just a bare clickable text.
                                Modifier
                                    .toggleable(
                                        value = false,
                                        role = Role.Checkbox,
                                        onValueChange = { azioni.selezionaSegmento(segmento.segmentoId) },
                                    )
                                    .semantics { contentDescription = DESCRIZIONE_SELEZIONA_FRASE }
                                    .testTag("registrazione-seleziona-$n")
                            } else {
                                Modifier
                            },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(SnastroMisure.space3))
            Column(modifier = Modifier.weight(1f)) {
                if (mostraChi) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PallinoVoce(voceId = segmento.voceId, conNome = haNome)
                        Spacer(modifier = Modifier.width(SnastroMisure.space1))
                        Text(
                            text = segmento.etichettaVoce,
                            style = if (haNome) {
                                tipografia.label.copy(fontWeight = FontWeight.SemiBold)
                            } else {
                                tipografia.label
                            },
                            color = if (haNome) colori.ink else colori.inkMuted,
                            modifier = Modifier.testTag("registrazione-voce-$n"),
                        )
                        if (segmento.confermato) PuntinaConfermata(n)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                }
                Text(
                    text = segmento.testo,
                    style = tipografia.transcript,
                    color = colori.ink,
                    modifier = Modifier.testTag("registrazione-testo-$n"),
                )
                segmento.attesaFrase?.let { AttesaFrase(segmento, it, azioni) }
            }
        }
    }
}

/** AC-582: the 16dp checked box (accent fill, onAccent Check) that replaces the timecode once selected;
 * clicking it toggles the selection off (same [AzioniRegistrazione.selezionaSegmento], which already
 * toggles membership). */
@Composable
private fun CasellaSelezionata(id: SegmentoId, azioni: AzioniRegistrazione) {
    val colori = LocalSnastroColori.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(DIMENSIONE_CASELLA)
            .clip(RoundedCornerShape(SnastroMisure.radiusControl))
            .background(colori.accent)
            .toggleable(
                value = true,
                role = Role.Checkbox,
                onValueChange = { azioni.selezionaSegmento(id) },
            )
            .semantics { contentDescription = DESCRIZIONE_SELEZIONA_FRASE }
            .testTag("registrazione-seleziona-${id.numero}"),
    ) {
        IconaSn(Icona.Check, descrizione = null, tinta = colori.onAccent, dimensione = SnastroMisure.iconS)
    }
}

/** AC-528: the pin of a confirmed Segmento, with its tooltip (also its accessible description). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PuntinaConfermata(n: Int) {
    val colori = LocalSnastroColori.current
    TooltipArea(
        tooltip = {
            Surface(color = colori.ink, shape = RoundedCornerShape(SnastroMisure.radiusControl)) {
                Text(
                    TOOLTIP_FRASE_CONFERMATA,
                    color = colori.surface,
                    modifier = Modifier.padding(SnastroMisure.space2),
                )
            }
        },
    ) {
        Box(
            modifier = Modifier
                .padding(start = SnastroMisure.space1)
                .semantics { contentDescription = TOOLTIP_FRASE_CONFERMATA }
                .testTag("registrazione-confermato-$n"),
        ) {
            IconaSn(Icona.Pin, descrizione = null, tinta = colori.accentInk, dimensione = SnastroMisure.iconS)
        }
    }
}

/** AC-529: a pending naming of this row — an indicator at once; past the threshold the wait line + 'Annulla'. */
@Composable
private fun AttesaFrase(segmento: SegmentoRiga, attesa: AttesaComando, azioni: AzioniRegistrazione) {
    val colori = LocalSnastroColori.current
    val n = segmento.segmentoId.numero
    when (attesa) {
        AttesaComando.IN_CORSO -> CircularProgressIndicator(
            modifier = Modifier.padding(top = 2.dp).size(DIMENSIONE_INDICATORE)
                .testTag("registrazione-frase-in-corso-$n"),
        )
        AttesaComando.IN_ATTESA -> Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.testTag("registrazione-frase-in-attesa-$n"),
        ) {
            Text(MESSAGGIO_COMANDO_IN_ATTESA, style = LocalSnastroTipografia.current.caption, color = colori.inkMuted)
            BottoneSn(
                ETICHETTA_ANNULLA,
                onClick = { azioni.annullaFrase(segmento.segmentoId) },
                variante = VarianteBottone.Link,
                modifier = Modifier.testTag("registrazione-frase-annulla-$n"),
            )
        }
    }
}
