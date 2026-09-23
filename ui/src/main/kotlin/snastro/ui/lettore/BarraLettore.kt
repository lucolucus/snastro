package snastro.ui.lettore

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import snastro.ui.formattaDurata

private val SPAZIO_CONTROLLO = 8.dp
private val DIMENSIONE_INDICATORE = 20.dp

/**
 * Thin view of the shared audio bar / "▶" control (RC-2): only renders [stato] and forwards
 * [onRiproduci]/[onPausa] — which source to play is decided by the embedding screen (it knows the
 * `RegistrazioneId`/`EstrattoRef`), not by this shared component.
 */
@Composable
fun BarraLettore(stato: LettoreUiStato, onRiproduci: () -> Unit, onPausa: () -> Unit, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.testTag("lettore-barra")) {
        when (stato) {
            LettoreUiStato.Inattivo -> PulsanteRiproduci(abilitato = true, onClick = onRiproduci)
            is LettoreUiStato.NonDisponibile -> {
                PulsanteRiproduci(abilitato = false, onClick = onRiproduci)
                Text(
                    text = stato.messaggio,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = SPAZIO_CONTROLLO).testTag("lettore-non-disponibile"),
                )
            }
            LettoreUiStato.Caricamento ->
                CircularProgressIndicator(
                    modifier = Modifier.width(DIMENSIONE_INDICATORE).testTag("lettore-caricamento"),
                )
            is LettoreUiStato.Pronto -> {
                if (stato.inRiproduzione) {
                    PulsantePausa(onClick = onPausa)
                } else {
                    PulsanteRiproduci(abilitato = true, onClick = onRiproduci)
                }
                Text(
                    text = formattaDurata(stato.posizioneMs),
                    modifier = Modifier.padding(start = SPAZIO_CONTROLLO).testTag("lettore-posizione"),
                )
            }
        }
    }
}

@Composable
private fun PulsanteRiproduci(abilitato: Boolean, onClick: () -> Unit) {
    val base = Modifier.testTag("lettore-riproduci")
    Text(
        text = "▶",
        color = if (abilitato) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = if (abilitato) base.clickable(onClick = onClick) else base,
    )
}

@Composable
private fun PulsantePausa(onClick: () -> Unit) {
    Text(
        text = "⏸",
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.testTag("lettore-pausa").clickable(onClick = onClick),
    )
}
