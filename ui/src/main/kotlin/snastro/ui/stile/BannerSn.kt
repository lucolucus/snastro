package snastro.ui.stile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow

private data class ContenutoBanner(val sfondo: Color, val icona: Icona, val iconaColore: Color)

/**
 * AC-566: a full-width, single-message banner — `radiusCard`, one icon, a bold title, one line of
 * body text, at most one [azione]. At most one banner per screen (review criterion).
 */
@Composable
public fun BannerSn(
    tipo: TipoBanner,
    titolo: String,
    testo: String,
    modifier: Modifier = Modifier,
    azione: AzioneBanner? = null,
) {
    val colori = LocalSnastroColori.current
    val tipografia = LocalSnastroTipografia.current
    val contenuto = when (tipo) {
        TipoBanner.Info -> ContenutoBanner(colori.accentSoft, Icona.People, colori.accentInk)
        TipoBanner.Avviso -> ContenutoBanner(colori.warningSoft, Icona.Retry, colori.warning)
        TipoBanner.Errore -> ContenutoBanner(colori.dangerSoft, Icona.Alert, colori.danger)
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SnastroMisure.radiusCard),
        color = contenuto.sfondo,
        contentColor = colori.ink,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = SnastroMisure.space4, vertical = SnastroMisure.space3),
            horizontalArrangement = Arrangement.spacedBy(SnastroMisure.space3),
        ) {
            IconaSn(
                contenuto.icona,
                descrizione = null,
                tinta = contenuto.iconaColore,
                dimensione = SnastroMisure.iconM,
            )
            Column(Modifier.weight(1f)) {
                Text(text = titolo, style = tipografia.heading)
                Text(text = testo, style = tipografia.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (azione != null) {
                BottoneSn(etichetta = azione.etichetta, onClick = azione.onClick, variante = VarianteBottone.Link)
            }
        }
    }
}
