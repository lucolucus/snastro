package snastro.ui.modelli

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import snastro.ui.SnastroTema
import snastro.ui.formattaByte
import snastro.ui.testi.ETICHETTA_LICENZE
import snastro.ui.testi.ETICHETTA_RIPROVA
import snastro.ui.testi.ETICHETTA_SCARICA
import snastro.ui.testi.etichettaDownloadInCorso
import snastro.ui.testi.etichettaModelliMancanti

private val PADDING_SCHERMO = 24.dp
private val PADDING_SEZIONE = 16.dp
private val PADDING_RIGA = 8.dp

/**
 * Thin view of S5 · Modelli (RC-2): only renders [stato] and forwards [azioni]'s events — AC-232's
 * "non blocca l'app" is structural (this is a plain screen, never a non-dismissible dialog): once
 * [ModelliUiStato.Pronti] it simply shows the licences, nothing here prevents the rest of the app
 * from being used.
 */
@Composable
fun SchermataModelli(stato: ModelliUiStato, azioni: AzioniModelli) {
    SnastroTema {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (stato) {
                is ModelliUiStato.Mancanti -> ContenutoMancanti(stato, azioni)
                is ModelliUiStato.InDownload -> ContenutoInDownload(stato)
                is ModelliUiStato.Errore -> ContenutoErrore(stato, azioni)
                is ModelliUiStato.Pronti -> ContenutoPronti(stato)
            }
        }
    }
}

@Composable
private fun ContenutoMancanti(stato: ModelliUiStato.Mancanti, azioni: AzioniModelli) {
    Column(modifier = Modifier.fillMaxSize().padding(PADDING_SCHERMO).testTag("modelli-mancanti")) {
        Text(text = etichettaModelliMancanti(stato.numero))
        Text(text = formattaByte(stato.totaleByte), style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(PADDING_SEZIONE))
        Button(onClick = azioni.scarica, modifier = Modifier.testTag("modelli-scarica")) {
            Text(ETICHETTA_SCARICA)
        }
    }
}

@Composable
private fun ContenutoInDownload(stato: ModelliUiStato.InDownload) {
    Column(modifier = Modifier.fillMaxSize().padding(PADDING_SCHERMO).testTag("modelli-download")) {
        Text(text = etichettaDownloadInCorso(stato.modelloId))
        Text(
            text = "${formattaByte(stato.scaricatiByte)} / ${formattaByte(stato.totaliByte)}",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(modifier = Modifier.height(PADDING_RIGA))
        val avanzamento = if (stato.totaliByte > 0) {
            (stato.scaricatiByte.toFloat() / stato.totaliByte).coerceIn(0f, 1f)
        } else {
            0f
        }
        LinearProgressIndicator(
            progress = { avanzamento },
            modifier = Modifier.fillMaxWidth().testTag("modelli-progresso"),
        )
    }
}

@Composable
private fun ContenutoErrore(stato: ModelliUiStato.Errore, azioni: AzioniModelli) {
    Column(modifier = Modifier.fillMaxSize().padding(PADDING_SCHERMO).testTag("modelli-errore")) {
        Text(text = stato.messaggio, color = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.height(PADDING_SEZIONE))
        Button(onClick = azioni.scarica, modifier = Modifier.testTag("modelli-riprova")) {
            Text(ETICHETTA_RIPROVA)
        }
    }
}

@Composable
private fun ContenutoPronti(stato: ModelliUiStato.Pronti) {
    Column(modifier = Modifier.fillMaxSize().padding(PADDING_SCHERMO).testTag("modelli-pronti")) {
        Text(text = ETICHETTA_LICENZE, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(PADDING_SEZIONE))
        LazyColumn(modifier = Modifier.fillMaxSize().testTag("modelli-licenze")) {
            items(stato.licenze, key = { it.nome }) { licenza -> RigaLicenza(licenza) }
        }
    }
}

@Composable
private fun RigaLicenza(licenza: LicenzaVista) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = PADDING_RIGA).testTag("modelli-licenza-${licenza.nome}"),
    ) {
        Text(text = "${licenza.nome} · ${licenza.ruolo}", style = MaterialTheme.typography.bodyLarge)
        Text(text = licenza.licenza, style = MaterialTheme.typography.bodySmall)
        Text(text = licenza.attribuzione, style = MaterialTheme.typography.bodySmall)
    }
}
