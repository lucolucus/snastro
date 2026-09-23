package snastro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import snastro.ui.testi.ETICHETTA_CHIUDI_PROGETTO
import snastro.ui.testi.etichetta

private val LARGHEZZA_NAV = 220.dp
private val PADDING_MESSAGGIO = 24.dp
private val PADDING_NAV = 16.dp
private val PADDING_VOCE_VERTICALE = 8.dp

/**
 * Thin view of the app shell (RC-2): only renders [stato] and forwards [azioni]'s events. `contenuto`
 * hosts the screen of the selected section (or S1 when no Progetto is open) — later `ui` blocks plug
 * their real screens into these slots; here they default to nothing so the render-check of this
 * block proves only the frame (sizing/overflow/contrast/states), not screens that don't exist yet.
 */
@Composable
fun SchermataShell(
    stato: ShellUiStato,
    azioni: AzioniShell,
    contenutoSenzaProgetto: @Composable () -> Unit = {},
    contenuto: @Composable (ShellUiStato.ConProgetto) -> Unit = {},
) {
    SnastroTema {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (stato) {
                ShellUiStato.SenzaProgetto -> contenutoSenzaProgetto()
                ShellUiStato.Caricamento -> IndicatoreCaricamento()
                is ShellUiStato.ErroreApertura -> MessaggioErroreApertura(stato.messaggio)
                is ShellUiStato.ConProgetto ->
                    Row(modifier = Modifier.fillMaxSize()) {
                        NavigazioneShell(
                            stato = stato,
                            azioni = azioni,
                            modifier = Modifier.width(LARGHEZZA_NAV).fillMaxHeight(),
                        )
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) { contenuto(stato) }
                    }
            }
        }
    }
}

@Composable
private fun IndicatoreCaricamento() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.testTag("shell-indicatore-caricamento"))
    }
}

@Composable
private fun MessaggioErroreApertura(messaggio: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = messaggio,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(PADDING_MESSAGGIO).testTag("shell-errore-apertura"),
        )
    }
}

@Composable
private fun NavigazioneShell(stato: ShellUiStato.ConProgetto, azioni: AzioniShell, modifier: Modifier = Modifier) {
    Column(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant).padding(PADDING_NAV)) {
        Text(text = stato.progetto.nome, modifier = Modifier.fillMaxWidth().padding(bottom = PADDING_NAV))
        DestinazioneShell.entries.filter { it in stato.destinazioniDisponibili }.forEach { destinazione ->
            VoceNavigazione(
                etichetta = etichetta(destinazione),
                selezionata = destinazione == stato.destinazioneSelezionata,
                onClick = { azioni.seleziona(destinazione) },
            )
        }
        Text(
            text = ETICHETTA_CHIUDI_PROGETTO,
            modifier = Modifier.fillMaxWidth().padding(top = PADDING_NAV).clickable { azioni.chiudi() },
        )
    }
}

@Composable
private fun VoceNavigazione(etichetta: String, selezionata: Boolean, onClick: () -> Unit) {
    Text(
        text = etichetta,
        color = if (selezionata) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().padding(vertical = PADDING_VOCE_VERTICALE).clickable(onClick = onClick),
    )
}
