package snastro.ui.progetti

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import snastro.progetto.applicazione.letture.ProgettoVista
import snastro.ui.SnastroTema
import snastro.ui.formattaData
import snastro.ui.testi.ETICHETTA_APRI_PROGETTO
import snastro.ui.testi.ETICHETTA_CAMBIA_CARTELLA
import snastro.ui.testi.ETICHETTA_CHIUDI_ERRORE
import snastro.ui.testi.ETICHETTA_CREA
import snastro.ui.testi.ETICHETTA_NOME_PROGETTO
import snastro.ui.testi.ETICHETTA_NUOVO_PROGETTO
import snastro.ui.testi.MESSAGGIO_PROGETTI_VUOTO
import snastro.ui.testi.etichettaRegistrazioni
import java.time.ZoneId
import javax.swing.JFileChooser

private val PADDING_SCHERMO = 24.dp
private val PADDING_SEZIONE = 16.dp
private val PADDING_RIGA = 8.dp
private val LARGHEZZA_CAMPO_NOME = 240.dp
private val DIMENSIONE_INDICATORE_PICCOLO = 18.dp

/**
 * Thin view of S1 · Progetti (RC-2): only renders [stato] and forwards [azioni]'s events — the
 * folder pickers below are OS integration, not a decision ([sceltaCartella] always hands its result
 * straight to an [azioni] lambda, never branches on it beyond null-cancelled).
 */
@Composable
fun SchermataProgetti(stato: ProgettiUiStato, azioni: AzioniProgetti) {
    SnastroTema {
        when (stato) {
            ProgettiUiStato.Caricamento -> IndicatoreCaricamentoProgetti()
            is ProgettiUiStato.Dati -> ContenutoProgetti(stato, azioni)
        }
    }
}

@Composable
private fun IndicatoreCaricamentoProgetti() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.testTag("progetti-indicatore-caricamento"))
    }
}

@Composable
private fun ContenutoProgetti(stato: ProgettiUiStato.Dati, azioni: AzioniProgetti) {
    Column(modifier = Modifier.fillMaxSize().padding(PADDING_SCHERMO)) {
        FormNuovoProgetto(inCorso = stato.inCorso, erroreCrea = stato.erroreCrea, azioni = azioni)
        Spacer(modifier = Modifier.height(PADDING_SEZIONE))
        AzioneApriProgetto(inCorso = stato.inCorso, erroreApri = stato.erroreApri, azioni = azioni)
        Spacer(modifier = Modifier.height(PADDING_SEZIONE))
        if (stato.progetti.isEmpty()) {
            Text(text = MESSAGGIO_PROGETTI_VUOTO, modifier = Modifier.testTag("progetti-vuoto"))
        } else {
            ElencoProgettiLista(stato.progetti, abilitato = !stato.inCorso, apri = azioni.apri)
        }
    }
}

@Composable
private fun FormNuovoProgetto(inCorso: Boolean, erroreCrea: String?, azioni: AzioniProgetti) {
    var cartella by remember { mutableStateOf(System.getProperty("user.home").orEmpty()) }
    var nome by remember { mutableStateOf("") }

    Text(text = ETICHETTA_NUOVO_PROGETTO, style = MaterialTheme.typography.titleMedium)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = PADDING_RIGA)) {
        OutlinedTextField(
            value = nome,
            onValueChange = { nome = it },
            label = { Text(ETICHETTA_NOME_PROGETTO) },
            singleLine = true,
            modifier = Modifier.width(LARGHEZZA_CAMPO_NOME).testTag("progetti-campo-nome"),
        )
        TextButton(
            onClick = { sceltaCartella(cartella)?.let { cartella = it } },
            enabled = !inCorso,
            modifier = Modifier.padding(start = PADDING_RIGA),
        ) { Text(ETICHETTA_CAMBIA_CARTELLA) }
    }
    Text(text = cartella, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("progetti-cartella"))
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = PADDING_RIGA)) {
        Button(
            onClick = { azioni.crea(cartella, nome) },
            enabled = !inCorso,
            modifier = Modifier.testTag("progetti-crea"),
        ) { Text(ETICHETTA_CREA) }
        if (inCorso) {
            CircularProgressIndicator(
                modifier = Modifier.padding(start = PADDING_RIGA).size(DIMENSIONE_INDICATORE_PICCOLO)
                    .testTag("progetti-operazione-in-corso"),
            )
        }
    }
    erroreCrea?.let { MessaggioInlineErrore(it, azioni.chiudiErroreCrea, "progetti-errore-crea") }
}

@Composable
private fun AzioneApriProgetto(inCorso: Boolean, erroreApri: String?, azioni: AzioniProgetti) {
    Button(
        onClick = { sceltaCartella(System.getProperty("user.home").orEmpty())?.let { azioni.apri(it) } },
        enabled = !inCorso,
        modifier = Modifier.testTag("progetti-apri"),
    ) { Text(ETICHETTA_APRI_PROGETTO) }
    erroreApri?.let { MessaggioInlineErrore(it, azioni.chiudiErroreApri, "progetti-errore-apri") }
}

@Composable
private fun MessaggioInlineErrore(messaggio: String, onChiudi: () -> Unit, tag: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = PADDING_RIGA).testTag(tag)) {
        Text(text = messaggio, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f, fill = false))
        Text(
            text = ETICHETTA_CHIUDI_ERRORE,
            modifier = Modifier.padding(start = PADDING_RIGA).clickable(onClick = onChiudi).testTag("$tag-chiudi"),
        )
    }
}

@Composable
private fun ElencoProgettiLista(progetti: List<ProgettoVista>, abilitato: Boolean, apri: (String) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize().testTag("progetti-lista"), verticalArrangement = Arrangement.Top) {
        items(progetti, key = { it.progettoId.valore }) { progetto ->
            RigaProgetto(progetto, abilitato, apri)
        }
    }
}

@Composable
private fun RigaProgetto(progetto: ProgettoVista, abilitato: Boolean, apri: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .clickable(enabled = abilitato) { apri(progetto.percorso) }
            .padding(vertical = PADDING_RIGA)
            .testTag("progetti-riga-${progetto.progettoId.valore}"),
    ) {
        Text(text = progetto.nome, style = MaterialTheme.typography.bodyLarge)
        val dataUltimaAttivita = formattaData(progetto.ultimaAttivita.atZone(ZoneId.systemDefault()).toLocalDate())
        Text(
            text = "${etichettaRegistrazioni(progetto.numRegistrazioni)} · $dataUltimaAttivita",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Native directory picker (frugality rung 3: platform-native over a hand-rolled dialog); `null` = cancelled. */
private fun sceltaCartella(cartellaIniziale: String): String? {
    val selettore = JFileChooser(cartellaIniziale.ifBlank { null }).apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
    }
    return if (selettore.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        selettore.selectedFile.absolutePath
    } else {
        null
    }
}
