// TooManyFunctions: one screen split into many small, single-purpose composables (RC-2 thin view) —
// the natural shape of a row with a play control, an inline date field and a per-state status column.
@file:Suppress("TooManyFunctions")

package snastro.ui.registrazioni

import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import snastro.kernel.RegistrazioneId
import snastro.ui.SnastroTema
import snastro.ui.formattaData
import snastro.ui.formattaDurata
import snastro.ui.testi.ETICHETTA_CHIUDI_ERRORE
import snastro.ui.testi.ETICHETTA_COMPLETATA
import snastro.ui.testi.ETICHETTA_IMPORTA_FILE
import snastro.ui.testi.ETICHETTA_RIPROVA
import snastro.ui.testi.ETICHETTA_TRASCRIVI
import snastro.ui.testi.MESSAGGIO_REGISTRAZIONI_VUOTO
import snastro.ui.testi.MESSAGGIO_SORGENTE_NON_DISPONIBILE
import snastro.ui.testi.etichettaInAttesa
import snastro.ui.testi.etichettaInCorso
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.swing.JFileChooser

private val PADDING_SCHERMO = 24.dp
private val PADDING_SEZIONE = 16.dp
private val PADDING_RIGA = 8.dp
private val LARGHEZZA_CAMPO_DATA = 120.dp
private val DIMENSIONE_INDICATORE_PICCOLO = 18.dp
private const val RADICE_ESADECIMALE = 16
private const val LUNGHEZZA_ESCAPE_PERCENTO = 2 // "%XX": 2 hex digits after '%'
private val FORMATO_DATA_MODIFICABILE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/**
 * Thin view of S2 · Registrazioni (RC-2): only renders [stato] and forwards [azioni]'s events. The
 * file picker and the drag-and-drop target are OS/platform integration, not a decision — both simply
 * hand the chosen path to [AzioniRegistrazioni.importa] (frugality rung 3: platform-native over a
 * hand-rolled dialog).
 */
@Composable
fun SchermataRegistrazioni(stato: RegistrazioniUiStato, azioni: AzioniRegistrazioni) {
    SnastroTema {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (stato) {
                RegistrazioniUiStato.Caricamento -> IndicatoreCaricamentoRegistrazioni()
                is RegistrazioniUiStato.Dati -> ContenutoRegistrazioni(stato, azioni)
            }
        }
    }
}

@Composable
private fun IndicatoreCaricamentoRegistrazioni() {
    Column(modifier = Modifier.fillMaxSize().padding(PADDING_SCHERMO)) {
        CircularProgressIndicator(modifier = Modifier.testTag("registrazioni-indicatore-caricamento"))
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ContenutoRegistrazioni(stato: RegistrazioniUiStato.Dati, azioni: AzioniRegistrazioni) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(PADDING_SCHERMO)
            .dragAndDropTarget(
                shouldStartDragAndDrop = { !stato.importoInCorso },
                target = remember(azioni) { registrazioneDropTarget(azioni.importa) },
            )
            .testTag("registrazioni-drop-target"),
    ) {
        BarraImportazione(inCorso = stato.importoInCorso, azioni = azioni)
        stato.errore?.let { MessaggioInlineErrore(it, azioni.chiudiErrore, "registrazioni-errore") }
        Spacer(modifier = Modifier.height(PADDING_SEZIONE))
        if (stato.righe.isEmpty()) {
            Text(text = MESSAGGIO_REGISTRAZIONI_VUOTO, modifier = Modifier.testTag("registrazioni-vuoto"))
        } else {
            ElencoRegistrazioni(stato.righe, azioni)
        }
    }
}

@Composable
private fun BarraImportazione(inCorso: Boolean, azioni: AzioniRegistrazioni) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Button(
            onClick = { sceltaFileAudio()?.let { azioni.importa(it) } },
            enabled = !inCorso,
            modifier = Modifier.testTag("registrazioni-importa"),
        ) { Text(ETICHETTA_IMPORTA_FILE) }
        if (inCorso) {
            CircularProgressIndicator(
                modifier = Modifier.padding(start = PADDING_RIGA).size(DIMENSIONE_INDICATORE_PICCOLO)
                    .testTag("registrazioni-import-in-corso"),
            )
        }
    }
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
private fun ElencoRegistrazioni(righe: List<RigaRegistrazione>, azioni: AzioniRegistrazioni) {
    LazyColumn(modifier = Modifier.fillMaxSize().testTag("registrazioni-lista")) {
        items(righe, key = { it.registrazioneId.valore }) { riga -> RigaRegistrazioneItem(riga, azioni) }
    }
}

@Composable
private fun RigaRegistrazioneItem(riga: RigaRegistrazione, azioni: AzioniRegistrazioni) {
    val apribile = riga.elaborazione == StatoElaborazioneRiga.Completata
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = apribile) { azioni.apriRiga(riga.registrazioneId) }
            .padding(vertical = PADDING_RIGA)
            .testTag("registrazioni-riga-${riga.registrazioneId.valore}"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            ControlloRiproduzione(riga, azioni)
            Spacer(modifier = Modifier.width(PADDING_RIGA))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = riga.titolo, style = MaterialTheme.typography.bodyLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CampoData(riga, azioni)
                    Spacer(modifier = Modifier.width(PADDING_RIGA))
                    Text(text = formattaDurata(riga.durataMs), style = MaterialTheme.typography.bodySmall)
                }
            }
            riga.elaborazione?.let { ColonnaElaborazione(it, riga.registrazioneId, riga.operazioneInCorso, azioni) }
        }
        riga.erroreRiga?.let {
            MessaggioInlineErrore(
                it,
                { azioni.chiudiErroreRiga(riga.registrazioneId) },
                "registrazioni-errore-riga-${riga.registrazioneId.valore}",
            )
        }
    }
}

@Composable
private fun ControlloRiproduzione(riga: RigaRegistrazione, azioni: AzioniRegistrazioni) {
    val tagBase = "registrazioni-riproduzione-${riga.registrazioneId.valore}"
    when (riga.riproduzione) {
        StatoRiproduzioneRiga.Disponibile ->
            Text(
                text = "▶",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { azioni.riproduci(riga.registrazioneId) }.testTag(tagBase),
            )
        StatoRiproduzioneRiga.InRiproduzione ->
            Text(
                text = "⏸",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = azioni.pausa).testTag(tagBase),
            )
        StatoRiproduzioneRiga.NonDisponibile ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "▶",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(tagBase),
                )
                Text(
                    text = MESSAGGIO_SORGENTE_NON_DISPONIBILE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = PADDING_RIGA).testTag("$tagBase-non-disponibile"),
                )
            }
    }
}

/** AC-206: local text buffer, resynced from [RigaRegistrazione.dataRegistrazione] on every real change. */
@Composable
private fun CampoData(riga: RigaRegistrazione, azioni: AzioniRegistrazioni) {
    var testo by remember(riga.dataRegistrazione) { mutableStateOf(formattaData(riga.dataRegistrazione)) }
    OutlinedTextField(
        value = testo,
        onValueChange = { nuovo ->
            testo = nuovo
            nuovo.aData()?.let { azioni.modificaData(riga.registrazioneId, it) }
        },
        singleLine = true,
        enabled = !riga.operazioneInCorso,
        textStyle = MaterialTheme.typography.bodySmall,
        modifier = Modifier.width(LARGHEZZA_CAMPO_DATA).testTag("registrazioni-data-${riga.registrazioneId.valore}"),
    )
}

private fun String.aData(): LocalDate? =
    try {
        LocalDate.parse(this, FORMATO_DATA_MODIFICABILE)
    } catch (
        // AC-206: malformed/partial input while typing is recoverable — the field just doesn't submit yet.
        @Suppress("SwallowedException") e: DateTimeParseException,
    ) {
        null
    }

@Composable
private fun ColonnaElaborazione(
    stato: StatoElaborazioneRiga,
    id: RegistrazioneId,
    operazioneInCorso: Boolean,
    azioni: AzioniRegistrazioni,
) {
    Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier.padding(start = PADDING_RIGA).testTag("registrazioni-stato-${id.valore}"),
    ) {
        when (stato) {
            StatoElaborazioneRiga.NonAvviata ->
                TextButton(onClick = { azioni.avviaElaborazione(id) }, enabled = !operazioneInCorso) {
                    Text(ETICHETTA_TRASCRIVI)
                }
            is StatoElaborazioneRiga.InAttesa -> Text(etichettaInAttesa(stato.posizione))
            is StatoElaborazioneRiga.InCorso -> Text(etichettaInCorso(stato.faseEtichetta, stato.trascorsoMs))
            is StatoElaborazioneRiga.Fallita -> {
                Text(
                    text = stato.motivo,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { azioni.avviaElaborazione(id) }, enabled = !operazioneInCorso) {
                    Text(ETICHETTA_RIPROVA)
                }
            }
            StatoElaborazioneRiga.Completata -> Text(ETICHETTA_COMPLETATA)
        }
        if (operazioneInCorso) {
            CircularProgressIndicator(
                modifier = Modifier.size(DIMENSIONE_INDICATORE_PICCOLO)
                    .testTag("registrazioni-operazione-in-corso-${id.valore}"),
            )
        }
    }
}

/** Native file picker (frugality rung 3), audio files only by extension is left to the OS dialog's own filter. */
private fun sceltaFileAudio(): String? {
    val selettore = JFileChooser().apply { fileSelectionMode = JFileChooser.FILES_ONLY }
    return if (selettore.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        selettore.selectedFile.absolutePath
    } else {
        null
    }
}

/** AC-199..201: an OS drag-and-drop of one or more files hands the first one's path to [onFile]. */
@OptIn(ExperimentalComposeUiApi::class)
private fun registrazioneDropTarget(onFile: (String) -> Unit) = object : DragAndDropTarget {
    override fun onDrop(event: DragAndDropEvent): Boolean {
        val uri = (event.dragData() as? DragData.FilesList)?.readFiles()?.firstOrNull() ?: return false
        onFile(File(percorsoDaUriFile(uri)).path)
        return true
    }
}

/**
 * `DragData.FilesList.readFiles()` returns each path as a `file:` URI string (JetBrains Compose
 * Desktop encodes it via `File.toURI().toString()` on the AWT side) — decoded here by hand, not via
 * `java.net.URI` (CR-3 confines `java.net.*` to `:modelli`; this is pure string parsing, no network).
 */
private fun percorsoDaUriFile(uri: String): String {
    val senzaSchema = uri.removePrefix("file:")
    val percorso = if (senzaSchema.startsWith("//")) senzaSchema.substring(1) else senzaSchema
    return percorso.decodificaPercento()
}

/** Reverses URI percent-encoding (RFC 3986) — a `file:` URI is pure ASCII, so one char is one byte. */
private fun String.decodificaPercento(): String {
    val bytes = ByteArrayOutputStream()
    var i = 0
    while (i < length) {
        val c = this[i]
        if (c == '%' && i + LUNGHEZZA_ESCAPE_PERCENTO < length) {
            bytes.write(substring(i + 1, i + 1 + LUNGHEZZA_ESCAPE_PERCENTO).toInt(radix = RADICE_ESADECIMALE))
            i += 1 + LUNGHEZZA_ESCAPE_PERCENTO
        } else {
            bytes.write(c.code)
            i += 1
        }
    }
    return bytes.toString(Charsets.UTF_8)
}
