package snastro.ui.progetti

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import snastro.ui.stile.BottoneSn
import snastro.ui.stile.CampoSn
import snastro.ui.stile.Icona
import snastro.ui.stile.IconaSn
import snastro.ui.stile.LocalSnastroColori
import snastro.ui.stile.LocalSnastroTipografia
import snastro.ui.stile.SnastroMisure
import snastro.ui.stile.VarianteBottone
import snastro.ui.testi.ETICHETTA_ANNULLA
import snastro.ui.testi.ETICHETTA_CAMBIA_CARTELLA
import snastro.ui.testi.ETICHETTA_CHIUDI_ERRORE
import snastro.ui.testi.ETICHETTA_CREA
import snastro.ui.testi.ETICHETTA_NOME_PROGETTO
import snastro.ui.testi.ETICHETTA_NUOVO_PROGETTO

private val LARGHEZZA_PANNELLO_NUOVO = 460.dp
private val OMBRA_PANNELLO = 12.dp
private const val OPACITA_VELO = 0.32f

/**
 * The new-project panel over a veil that closes it (`anteprime/Dialog.html`, inside the window — never an OS-level
 * modal): name, the parent folder with "Cambia cartella…", "Annulla"/"Crea"; a failed `crea` shows its message
 * here. The form values are local UI state, fresh each time the panel opens.
 */
@Suppress("LongParameterList", "LongMethod") // state + actions + injected default and port + close hook
@Composable
internal fun PannelloNuovoProgetto(
    inCorso: Boolean,
    erroreCrea: String?,
    azioni: AzioniProgetti,
    cartellaGenitorePredefinita: String,
    sceltaCartella: SceltaCartella,
    onChiudi: () -> Unit,
) {
    val colori = LocalSnastroColori.current
    val tipografia = LocalSnastroTipografia.current
    var cartella by remember { mutableStateOf(cartellaGenitorePredefinita) }
    var nome by remember { mutableStateOf("") }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // The veil is the panel's SIBLING, not its parent: a clickable parent would merge the panel's fields into
        // its own semantics node.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = OPACITA_VELO))
                .clickable(
                    enabled = !inCorso,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onChiudi,
                )
                .testTag("progetti-velo"),
        )
        Surface(
            shape = RoundedCornerShape(SnastroMisure.radiusDialog),
            color = colori.raised,
            border = BorderStroke(1.dp, colori.line),
            shadowElevation = OMBRA_PANNELLO,
            modifier = Modifier
                .width(LARGHEZZA_PANNELLO_NUOVO)
                // Swallows taps, so a click inside the panel never reaches the veil behind it.
                .pointerInput(Unit) { detectTapGestures {} }
                .testTag("progetti-pannello-nuovo"),
        ) {
            Column(modifier = Modifier.padding(SnastroMisure.space5)) {
                Text(text = ETICHETTA_NUOVO_PROGETTO, style = tipografia.title, color = colori.ink)
                Spacer(modifier = Modifier.height(SnastroMisure.space4))
                CampoSn(
                    valore = nome,
                    onValoreCambiato = { nome = it },
                    etichetta = ETICHETTA_NOME_PROGETTO,
                    abilitato = !inCorso,
                    modifier = Modifier.fillMaxWidth().testTag("progetti-campo-nome"),
                )
                Spacer(modifier = Modifier.height(SnastroMisure.space3))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconaSn(Icona.Reveal, descrizione = null, tinta = colori.inkMuted, dimensione = SnastroMisure.iconS)
                    Spacer(modifier = Modifier.width(SnastroMisure.space1))
                    Text(
                        text = cartella,
                        style = tipografia.caption,
                        color = colori.inkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                        modifier = Modifier.weight(1f).testTag("progetti-cartella"),
                    )
                    BottoneSn(
                        etichetta = ETICHETTA_CAMBIA_CARTELLA,
                        onClick = { sceltaCartella.scegli(ETICHETTA_CAMBIA_CARTELLA)?.let { cartella = it } },
                        variante = VarianteBottone.Link,
                        abilitato = !inCorso,
                    )
                }
                erroreCrea?.let { MessaggioInlineErrore(it, azioni.chiudiErroreCrea, "progetti-errore-crea") }
                Spacer(modifier = Modifier.height(SnastroMisure.space5))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.weight(1f))
                    BottoneSn(
                        etichetta = ETICHETTA_ANNULLA,
                        onClick = onChiudi,
                        variante = VarianteBottone.Fantasma,
                        abilitato = !inCorso,
                        modifier = Modifier.testTag("progetti-annulla-nuovo"),
                    )
                    Spacer(modifier = Modifier.width(SnastroMisure.space2))
                    BottoneSn(
                        etichetta = ETICHETTA_CREA,
                        onClick = { azioni.crea(cartella, nome) },
                        variante = VarianteBottone.Primario,
                        abilitato = !inCorso,
                        modifier = Modifier.testTag("progetti-crea"),
                    )
                }
            }
        }
    }
}

@Composable
internal fun MessaggioInlineErrore(messaggio: String, onChiudi: () -> Unit, tag: String) {
    val colori = LocalSnastroColori.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = SnastroMisure.space2).testTag(tag),
    ) {
        Text(
            text = messaggio,
            color = colori.danger,
            style = LocalSnastroTipografia.current.caption,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            text = ETICHETTA_CHIUDI_ERRORE,
            color = colori.accentInk,
            style = LocalSnastroTipografia.current.label,
            modifier = Modifier
                .padding(start = SnastroMisure.space2)
                .clickable(onClick = onChiudi)
                .testTag("$tag-chiudi"),
        )
    }
}
