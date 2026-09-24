// TooManyFunctions: one screen split into many small, single-purpose composables (RC-2 thin view) —
// the natural shape of a row with a play control, inline titolo/date fields and a per-state status column.
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import snastro.kernel.RegistrazioneId
import snastro.ui.SnastroTema
import snastro.ui.formattaData
import snastro.ui.formattaDurata
import snastro.ui.testi.ETICHETTA_CHIUDI_ERRORE
import snastro.ui.testi.ETICHETTA_COMPLETATA
import snastro.ui.testi.ETICHETTA_IMPORTA_FILE
import snastro.ui.testi.ETICHETTA_NUMERO_PERSONE
import snastro.ui.testi.ETICHETTA_RIPROVA
import snastro.ui.testi.ETICHETTA_TRASCRIVI
import snastro.ui.testi.MESSAGGIO_AUDIO_NON_DISPONIBILE
import snastro.ui.testi.MESSAGGIO_DATA_NON_VALIDA
import snastro.ui.testi.MESSAGGIO_REGISTRAZIONI_VUOTO
import snastro.ui.testi.SUGGERIMENTO_NUMERO_PERSONE
import snastro.ui.testi.etichettaIdentificazione
import snastro.ui.testi.etichettaInAttesa
import snastro.ui.testi.etichettaInCorso
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import javax.swing.JFileChooser

private val PADDING_SCHERMO = 24.dp
private val PADDING_SEZIONE = 16.dp
private val PADDING_RIGA = 8.dp
private val LARGHEZZA_CAMPO_DATA = 120.dp
private val LARGHEZZA_CAMPO_NUMERO_PERSONE = 170.dp
private val DIMENSIONE_INDICATORE_PICCOLO = 18.dp

/** M4: `uuuu` (proleptic year, not `yyyy`) + [ResolverStyle.STRICT] rejects an out-of-range day
 * (e.g. 31/02) instead of a SMART resolver silently rolling it into the next month (28/02). */
private val FORMATO_DATA_MODIFICABILE: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)

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
                is RegistrazioniUiStato.Errore -> ErroreCaricamentoRegistrazioni(stato.messaggio, azioni.riprova)
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

/** M5: the INITIAL load failed — a distinct state, never the AC-199 empty-catalog message (which
 * would falsely claim there are no registrazioni) — with a retry action. */
@Composable
private fun ErroreCaricamentoRegistrazioni(messaggio: String, onRiprova: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(PADDING_SCHERMO).testTag("registrazioni-errore-caricamento"),
    ) {
        Text(text = messaggio, color = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.height(PADDING_SEZIONE))
        TextButton(onClick = onRiprova, modifier = Modifier.testTag("registrazioni-riprova")) {
            Text(ETICHETTA_RIPROVA)
        }
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
            onClick = { sceltaFileAudio()?.let { azioni.importa(listOf(it)) } },
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
                CampoTitolo(riga, azioni)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CampoData(riga, azioni)
                    Spacer(modifier = Modifier.width(PADDING_RIGA))
                    Text(text = formattaDurata(riga.durataMs), style = MaterialTheme.typography.bodySmall)
                }
                riga.identificazione?.let { BadgeIdentificazione(it, riga.registrazioneId) }
            }
            riga.elaborazione?.let { ColonnaElaborazione(it, riga, azioni) }
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
                    text = MESSAGGIO_AUDIO_NON_DISPONIBILE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = PADDING_RIGA).testTag("$tagBase-non-disponibile"),
                )
            }
    }
}

/**
 * AC-363: the titolo, editable inline exactly like [CampoData] (AC-206): a local text buffer resynced
 * from [RigaRegistrazione.titolo] on every real change, submitted only on Enter or on losing focus and
 * only when it differs from the titolo shown. Disabled while a row operation is in flight (M3, the
 * presenter's own guard backs it); a refused rename (blank, titolo already used) comes back as the
 * row's inline `erroreRiga` and the row keeps its old titolo — fix-batch-12 #3: `remember(riga.titolo)`
 * alone never resyncs [testo] in that case (a refusal leaves [RigaRegistrazione.titolo] UNCHANGED, so
 * the `remember` key never changes either). [inviato] is OUR OWN "a submit of this exact buffer is in
 * flight" flag — read directly in the composable body (not a `LaunchedEffect` keyed on
 * [RigaRegistrazione.operazioneInCorso]: a fast refusal can flip it true→false inside the SAME
 * recomposition batch this composable observes, so the `true` value is never actually seen as a
 * distinct frame to key an effect off) — so it resyncs on the very first recomposition that shows the
 * operation settled, whether or not an intermediate `true` frame ever rendered. Esc reverts the same
 * way, without submitting.
 */
@Composable
private fun CampoTitolo(riga: RigaRegistrazione, azioni: AzioniRegistrazioni) {
    var testo by remember(riga.titolo) { mutableStateOf(riga.titolo) }
    var eraFocalizzato by remember(riga.titolo) { mutableStateOf(false) }
    var inviato by remember(riga.titolo) { mutableStateOf(false) }
    if (inviato && !riga.operazioneInCorso) {
        testo = riga.titolo
        inviato = false
    }
    fun sottometti() {
        if (testo != riga.titolo) {
            inviato = true
            azioni.rinomina(riga.registrazioneId, testo)
        }
    }
    OutlinedTextField(
        value = testo,
        onValueChange = { testo = it },
        singleLine = true,
        enabled = !riga.operazioneInCorso,
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { sottometti() }),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { stato ->
                if (eraFocalizzato && !stato.isFocused) sottometti()
                eraFocalizzato = stato.isFocused
            }
            .onPreviewKeyEvent { evento ->
                if (evento.type == KeyEventType.KeyDown && evento.key == Key.Escape) {
                    testo = riga.titolo
                    true
                } else {
                    false
                }
            }
            .testTag("registrazioni-titolo-${riga.registrazioneId.valore}"),
    )
}

/**
 * AC-206: local text buffer, resynced from [RigaRegistrazione.dataRegistrazione] on every real
 * change. M4: submits only on Enter or on losing focus — never on every keystroke, so a partial date
 * while typing never round-trips through the parser — and a value that fails to parse (STRICT: no
 * 31/02 silently rolled to 28/02) is shown as an inline error instead of being dropped.
 */
@Composable
private fun CampoData(riga: RigaRegistrazione, azioni: AzioniRegistrazioni) {
    var testo by remember(riga.dataRegistrazione) { mutableStateOf(formattaData(riga.dataRegistrazione)) }
    var nonValido by remember(riga.dataRegistrazione) { mutableStateOf(false) }
    var eraFocalizzato by remember(riga.dataRegistrazione) { mutableStateOf(false) }
    fun sottometti() {
        val data = testo.aData()
        nonValido = data == null
        if (data != null && data != riga.dataRegistrazione) azioni.modificaData(riga.registrazioneId, data)
    }
    Column {
        OutlinedTextField(
            value = testo,
            onValueChange = { testo = it },
            singleLine = true,
            enabled = !riga.operazioneInCorso,
            isError = nonValido,
            textStyle = MaterialTheme.typography.bodySmall,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { sottometti() }),
            modifier = Modifier
                .width(LARGHEZZA_CAMPO_DATA)
                .onFocusChanged { stato ->
                    if (eraFocalizzato && !stato.isFocused) sottometti()
                    eraFocalizzato = stato.isFocused
                }
                .testTag("registrazioni-data-${riga.registrazioneId.valore}"),
        )
        if (nonValido) {
            Text(
                text = MESSAGGIO_DATA_NON_VALIDA,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("registrazioni-data-errore-${riga.registrazioneId.valore}"),
            )
        }
    }
}

/** M4: `FORMATO_DATA_MODIFICABILE` (`uuuu` + STRICT) rejects an out-of-range date instead of a SMART
 * resolver silently rolling it (e.g. 31/02 → 28/02) — `internal` so [PercorsoTrascinatoTest]-style unit
 * tests can cover the parser directly (see `RegistrazioniDataTest`). */
internal fun String.aData(): LocalDate? =
    try {
        LocalDate.parse(this, FORMATO_DATA_MODIFICABILE)
    } catch (
        // Invalid/malformed input is surfaced as an inline error by the caller — never thrown further,
        // never silently dropped.
        @Suppress("SwallowedException") e: DateTimeParseException,
    ) {
        null
    }

/**
 * AC-204/AC-345 (R2, fetta Parlanti): the identification badge — "3 voci · 1 da identificare", or
 * "3 voci" alone once every Voce is identified (AC-345, never "· 0 da identificare"). Absent
 * entirely when [RigaRegistrazione.identificazione] is `null` (R0/R1, or the source's row not yet
 * known/failed — [RegistrazioniPresenter] decides, this only renders what it is given).
 */
@Composable
private fun BadgeIdentificazione(identificazione: IdentificazioneRiga, id: RegistrazioneId) {
    Text(
        text = etichettaIdentificazione(identificazione.numVoci, identificazione.numVociDaIdentificare),
        style = MaterialTheme.typography.bodySmall,
        color = if (identificazione.numVociDaIdentificare > 0) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.testTag("registrazioni-identificazione-${id.valore}"),
    )
}

@Composable
private fun ColonnaElaborazione(stato: StatoElaborazioneRiga, riga: RigaRegistrazione, azioni: AzioniRegistrazioni) {
    val id = riga.registrazioneId
    val operazioneInCorso = riga.operazioneInCorso
    Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier.padding(start = PADDING_RIGA).testTag("registrazioni-stato-${id.valore}"),
    ) {
        when (stato) {
            StatoElaborazioneRiga.NonAvviata -> AvvioConNumeroPersone(riga, ETICHETTA_TRASCRIVI, azioni)
            is StatoElaborazioneRiga.InAttesa -> Text(etichettaInAttesa(stato.posizione))
            is StatoElaborazioneRiga.InCorso -> Text(etichettaInCorso(stato.faseEtichetta, stato.trascorsoMs))
            is StatoElaborazioneRiga.Fallita -> {
                Text(
                    text = stato.motivo,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                AvvioConNumeroPersone(riga, ETICHETTA_RIPROVA, azioni)
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

/**
 * ADR 0014: the plain 'Numero di persone' field (empty = automatic) next to the 'Trascrivi'/'Riprova' button
 * [etichetta]. Its text is presenter state ([RigaRegistrazione.numeroPersone]); validation and the inline
 * message (AC-375) are the presenter's, shown as the row's `erroreRiga`.
 */
@Composable
private fun AvvioConNumeroPersone(riga: RigaRegistrazione, etichetta: String, azioni: AzioniRegistrazioni) {
    val id = riga.registrazioneId
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = riga.numeroPersone,
            onValueChange = { azioni.modificaNumeroPersone(id, it) },
            singleLine = true,
            enabled = !riga.operazioneInCorso,
            label = { Text(ETICHETTA_NUMERO_PERSONE) },
            placeholder = { Text(SUGGERIMENTO_NUMERO_PERSONE) },
            textStyle = MaterialTheme.typography.bodySmall,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { azioni.avviaElaborazione(id) }),
            modifier = Modifier
                .width(LARGHEZZA_CAMPO_NUMERO_PERSONE)
                .testTag("registrazioni-numero-persone-${id.valore}"),
        )
        TextButton(onClick = { azioni.avviaElaborazione(id) }, enabled = !riga.operazioneInCorso) {
            Text(etichetta)
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

/**
 * AC-199..201/LOW: an OS drag-and-drop of one or more files hands every SUCCESSFULLY decoded path to
 * [onFiles] (`snastro.ui.registrazioni` `percorsoDaUriFile`, H1: correct on non-ASCII paths — a
 * malformed `%` escape drops just that one file, never throws inside this AWT callback). Nothing
 * usable in the drop → `false` (rejects the drop, nothing imported).
 */
@OptIn(ExperimentalComposeUiApi::class)
private fun registrazioneDropTarget(onFiles: (List<String>) -> Unit) = object : DragAndDropTarget {
    override fun onDrop(event: DragAndDropEvent): Boolean {
        val uri = (event.dragData() as? DragData.FilesList)?.readFiles().orEmpty()
        val percorsi = uri.mapNotNull(::percorsoDaUriFile)
        if (percorsi.isEmpty()) return false
        onFiles(percorsi)
        return true
    }
}
