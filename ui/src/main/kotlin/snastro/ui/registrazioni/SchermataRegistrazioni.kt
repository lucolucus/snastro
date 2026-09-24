// TooManyFunctions: one screen split into many small, single-purpose composables (RC-2 thin view) —
// the natural shape of a row with a play control, inline titolo/date fields and a per-state status column.
@file:Suppress("TooManyFunctions")

package snastro.ui.registrazioni

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import snastro.kernel.RegistrazioneId
import snastro.ui.SnastroTema
import snastro.ui.formattaData
import snastro.ui.formattaDurata
import snastro.ui.formattaDurataEstesa
import snastro.ui.stile.AzioneBanner
import snastro.ui.stile.BannerSn
import snastro.ui.stile.BottonePlay
import snastro.ui.stile.BottoneSn
import snastro.ui.stile.CampoNumeroPersone
import snastro.ui.stile.ChipStato
import snastro.ui.stile.Icona
import snastro.ui.stile.IconaSn
import snastro.ui.stile.LocalSnastroColori
import snastro.ui.stile.LocalSnastroTipografia
import snastro.ui.stile.SnastroMisure
import snastro.ui.stile.TipoBanner
import snastro.ui.stile.TipoChipStato
import snastro.ui.stile.VarianteBottone
import snastro.ui.testi.ETICHETTA_ANNULLA
import snastro.ui.testi.ETICHETTA_CHIUDI_ERRORE
import snastro.ui.testi.ETICHETTA_IMPORTAZIONE_NON_RIUSCITA
import snastro.ui.testi.ETICHETTA_IMPORTA_FILE
import snastro.ui.testi.ETICHETTA_RIPROVA
import snastro.ui.testi.ETICHETTA_RITRASCRIVI
import snastro.ui.testi.ETICHETTA_TRASCRIVI
import snastro.ui.testi.MESSAGGIO_AUDIO_NON_DISPONIBILE
import snastro.ui.testi.MESSAGGIO_CONFERMA_RITRASCRIVI
import snastro.ui.testi.MESSAGGIO_DATA_NON_VALIDA
import snastro.ui.testi.MESSAGGIO_FORMATI_AUDIO_SUPPORTATI
import snastro.ui.testi.MESSAGGIO_REGISTRAZIONI_VUOTO
import snastro.ui.testi.etichettaIdentificazione
import snastro.ui.testi.etichettaInAttesa
import snastro.ui.testi.etichettaInCorso
import snastro.ui.testi.etichettaRegistrazioni
import snastro.ui.testi.etichettaRitrascrizioneInAttesa
import snastro.ui.testi.etichettaRitrascrizioneInCorso
import snastro.ui.testi.messaggioRitrascrizioneNonRiuscita
import snastro.ui.testi.titoloConfermaRitrascrivi
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import javax.swing.JFileChooser

private val LARGHEZZA_CAMPO_DATA = 90.dp
private val DIMENSIONE_INDICATORE_PICCOLO = 18.dp
private val LARGHEZZA_CONFERMA_RITRASCRIVI = 320.dp
private val DIAMETRO_DROPZONE_ICONA = 28.dp

/** M4: `uuuu` (proleptic year, not `yyyy`) + [ResolverStyle.STRICT] rejects an out-of-range day
 * (e.g. 31/02) instead of a SMART resolver silently rolling it into the next month (28/02). */
private val FORMATO_DATA_MODIFICABILE: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)

/**
 * Thin view of S2 · Registrazioni (RC-2): only renders [stato] and forwards [azioni]'s events. The
 * file picker and the drag-and-drop target are OS/platform integration, not a decision — both simply
 * hand the chosen path to [AzioniRegistrazioni.importa] (frugality rung 3: platform-native over a
 * hand-rolled dialog).
 *
 * NOTE (restyle scope): AC-574's "project name in `display`" is not rendered here — the project's
 * name is [snastro.ui.ProgettoAperto], owned by the shell (`SchermataShell`'s sidebar), never passed
 * into this screen's [RegistrazioniUiStato]; adding it would mean a new field/wiring, which this
 * views-only block does not do. The "n registrazioni · durata" caption and the rest of AC-574..576
 * are implemented from data already in [RegistrazioniUiStato].
 */
@Composable
fun SchermataRegistrazioni(
    stato: RegistrazioniUiStato,
    azioni: AzioniRegistrazioni,
    scuro: Boolean = isSystemInDarkTheme(),
    riduciMovimento: Boolean? = null,
) {
    SnastroTema(scuro = scuro, riduciMovimento = riduciMovimento) {
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
    Column(
        modifier = Modifier.fillMaxSize().padding(vertical = SnastroMisure.space5, horizontal = SnastroMisure.space6),
    ) {
        CircularProgressIndicator(modifier = Modifier.testTag("registrazioni-indicatore-caricamento"))
    }
}

/** M5: the INITIAL load failed — a distinct state, never the AC-199 empty-catalog message (which
 * would falsely claim there are no registrazioni) — with a retry action. */
@Composable
private fun ErroreCaricamentoRegistrazioni(messaggio: String, onRiprova: () -> Unit) {
    val colori = LocalSnastroColori.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = SnastroMisure.space5, horizontal = SnastroMisure.space6)
            .testTag("registrazioni-errore-caricamento"),
    ) {
        Text(text = messaggio, color = colori.danger, style = LocalSnastroTipografia.current.body)
        Spacer(modifier = Modifier.height(SnastroMisure.space4))
        BottoneSn(
            etichetta = ETICHETTA_RIPROVA,
            onClick = onRiprova,
            variante = VarianteBottone.Secondario,
            modifier = Modifier.testTag("registrazioni-riprova"),
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ContenutoRegistrazioni(stato: RegistrazioniUiStato.Dati, azioni: AzioniRegistrazioni) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = SnastroMisure.space5, horizontal = SnastroMisure.space6)
            .dragAndDropTarget(
                shouldStartDragAndDrop = { !stato.importoInCorso },
                target = remember(azioni) { registrazioneDropTarget(azioni.importa) },
            )
            .testTag("registrazioni-drop-target"),
    ) {
        IntestazioneRegistrazioni(stato, azioni)
        stato.errore?.let {
            Spacer(modifier = Modifier.height(SnastroMisure.space3))
            BannerSn(
                tipo = TipoBanner.Errore,
                titolo = ETICHETTA_IMPORTAZIONE_NON_RIUSCITA,
                testo = it,
                azione = AzioneBanner(ETICHETTA_CHIUDI_ERRORE, azioni.chiudiErrore),
                modifier = Modifier.testTag("registrazioni-errore"),
            )
        }
        Spacer(modifier = Modifier.height(SnastroMisure.space4))
        if (stato.righe.isEmpty()) {
            DropZoneVuota()
        } else {
            ElencoRegistrazioni(stato.righe, azioni)
        }
    }
}

/** AC-574: "n registrazioni · <durata estesa totale>" (sum of `durataMs` — view arithmetic on the
 * rows already in state) + 'Importa audio…' `Primario`. */
@Composable
private fun IntestazioneRegistrazioni(stato: RegistrazioniUiStato.Dati, azioni: AzioniRegistrazioni) {
    val colori = LocalSnastroColori.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        val durataTotale = stato.righe.sumOf { it.durataMs }
        Text(
            text = "${etichettaRegistrazioni(stato.righe.size)} · ${formattaDurataEstesa(durataTotale)}",
            style = LocalSnastroTipografia.current.caption,
            color = colori.inkMuted,
            modifier = Modifier.weight(1f),
        )
        BottoneSn(
            etichetta = ETICHETTA_IMPORTA_FILE,
            onClick = { sceltaFileAudio()?.let { azioni.importa(listOf(it)) } },
            variante = VarianteBottone.Primario,
            icona = Icona.Import,
            abilitato = !stato.importoInCorso,
            modifier = Modifier.testTag("registrazioni-importa"),
        )
        if (stato.importoInCorso) {
            CircularProgressIndicator(
                modifier = Modifier.padding(start = SnastroMisure.space2).width(DIMENSIONE_INDICATORE_PICCOLO)
                    .testTag("registrazioni-import-in-corso"),
            )
        }
    }
}

/** AC-576: large `DropZone` — Import 28dp, the empty message, the supported formats + the existing
 * import action (kept as [ETICHETTA_IMPORTA_FILE] — same command, same text everywhere else). */
@Composable
private fun DropZoneVuota() {
    val colori = LocalSnastroColori.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colori.sunken, RoundedCornerShape(SnastroMisure.radiusCard))
            .padding(SnastroMisure.space6)
            .testTag("registrazioni-vuoto"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IconaSn(Icona.Import, descrizione = null, tinta = colori.inkMuted, dimensione = DIAMETRO_DROPZONE_ICONA)
        Spacer(modifier = Modifier.height(SnastroMisure.space3))
        Text(text = MESSAGGIO_REGISTRAZIONI_VUOTO, style = LocalSnastroTipografia.current.body, color = colori.ink)
        Spacer(modifier = Modifier.height(SnastroMisure.space1))
        Text(
            text = MESSAGGIO_FORMATI_AUDIO_SUPPORTATI,
            style = LocalSnastroTipografia.current.caption,
            color = colori.inkMuted,
        )
    }
}

@Composable
private fun ElencoRegistrazioni(righe: List<RigaRegistrazione>, azioni: AzioniRegistrazioni) {
    val colori = LocalSnastroColori.current
    Surface(
        modifier = Modifier.fillMaxSize().testTag("registrazioni-lista"),
        color = colori.raised,
        shape = RoundedCornerShape(SnastroMisure.radiusCard),
        border = BorderStroke(1.dp, colori.line),
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(righe, key = { _, riga -> riga.registrazioneId.valore }) { indice, riga ->
                if (indice > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(colori.line))
                RigaRegistrazioneItem(riga, azioni)
            }
        }
    }
}

@Composable
private fun RigaRegistrazioneItem(riga: RigaRegistrazione, azioni: AzioniRegistrazioni) {
    val colori = LocalSnastroColori.current
    // AC-450/AC-451 (ADR 0018): a row opens S3 iff a Trascritto exists — not iff COMPLETATA — so a
    // row mid re-run opens on the still-current old transcript too.
    val apribile = riga.trascrittoDisponibile
    val sfondo = if (riga.riproduzione == StatoRiproduzioneRiga.InRiproduzione) colori.accentSoft else colori.raised
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(sfondo)
            .clickable(enabled = apribile) { azioni.apriRiga(riga.registrazioneId) }
            .padding(horizontal = SnastroMisure.space4, vertical = SnastroMisure.space3)
            .testTag("registrazioni-riga-${riga.registrazioneId.valore}"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            ControlloRiproduzione(riga, azioni)
            Spacer(modifier = Modifier.width(SnastroMisure.space3))
            Column(modifier = Modifier.weight(1f)) {
                CampoTitolo(riga, azioni)
                RigaMeta(riga, azioni)
            }
            riga.elaborazione?.let {
                Spacer(modifier = Modifier.width(SnastroMisure.space3))
                ColonnaElaborazione(it, riga, azioni)
            }
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

/** AC-575: date (editable) · duration (`timecode`) · the identification badge, when present. */
@Composable
private fun RigaMeta(riga: RigaRegistrazione, azioni: AzioniRegistrazioni) {
    val colori = LocalSnastroColori.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        CampoData(riga, azioni = azioni)
        Spacer(modifier = Modifier.width(SnastroMisure.space2))
        Text(
            text = formattaDurata(riga.durataMs),
            style = LocalSnastroTipografia.current.timecode,
            color = colori.inkMuted,
        )
        riga.identificazione?.let {
            Spacer(modifier = Modifier.width(SnastroMisure.space2))
            BadgeIdentificazione(it, riga.registrazioneId)
        }
    }
}

@Composable
private fun ControlloRiproduzione(riga: RigaRegistrazione, azioni: AzioniRegistrazioni) {
    val tagBase = "registrazioni-riproduzione-${riga.registrazioneId.valore}"
    when (riga.riproduzione) {
        StatoRiproduzioneRiga.Disponibile ->
            BottonePlay(
                inRiproduzione = false,
                onClick = { azioni.riproduci(riga.registrazioneId) },
                grande = false,
                modifier = Modifier.testTag(tagBase),
            )
        StatoRiproduzioneRiga.InRiproduzione ->
            BottonePlay(
                inRiproduzione = true,
                onClick = azioni.pausa,
                grande = false,
                modifier = Modifier.testTag(tagBase),
            )
        StatoRiproduzioneRiga.NonDisponibile ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                BottonePlay(
                    inRiproduzione = false,
                    onClick = {},
                    grande = false,
                    abilitato = false,
                    modifier = Modifier.testTag(tagBase),
                )
                Text(
                    text = MESSAGGIO_AUDIO_NON_DISPONIBILE,
                    style = LocalSnastroTipografia.current.caption,
                    color = LocalSnastroColori.current.danger,
                    modifier = Modifier.padding(start = SnastroMisure.space2).testTag("$tagBase-non-disponibile"),
                )
            }
    }
}

/**
 * AC-363/AC-575: the titolo, editable inline: a local text buffer resynced from
 * [RigaRegistrazione.titolo] on every real change, submitted only on Enter or on losing focus and
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
 * way, without submitting. AC-575: renders as plain `heading` text at rest (borderless field, same
 * pattern as [CampoData]) — "title heading ellipsised" in the visual target.
 */
@Composable
private fun CampoTitolo(riga: RigaRegistrazione, azioni: AzioniRegistrazioni) {
    val colori = LocalSnastroColori.current
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
    BasicTextField(
        value = testo,
        onValueChange = { testo = it },
        singleLine = true,
        enabled = !riga.operazioneInCorso,
        textStyle = LocalSnastroTipografia.current.heading.copy(
            color = if (riga.operazioneInCorso) colori.inkFaint else colori.ink,
        ),
        cursorBrush = SolidColor(colori.accentInk),
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
 * AC-206/AC-575: local text buffer, resynced from [RigaRegistrazione.dataRegistrazione] on every
 * real change. M4: submits only on Enter or on losing focus — never on every keystroke, so a partial
 * date while typing never round-trips through the parser — and a value that fails to parse (STRICT:
 * no 31/02 silently rolled to 28/02) is shown as an inline error instead of being dropped. AC-575:
 * "renders as caption text with an Edit icon on hover (no boxed field at rest)".
 */
@Composable
private fun CampoData(riga: RigaRegistrazione, azioni: AzioniRegistrazioni) {
    val colori = LocalSnastroColori.current
    var testo by remember(riga.dataRegistrazione) { mutableStateOf(formattaData(riga.dataRegistrazione)) }
    var nonValido by remember(riga.dataRegistrazione) { mutableStateOf(false) }
    var eraFocalizzato by remember(riga.dataRegistrazione) { mutableStateOf(false) }
    val interazione = remember { MutableInteractionSource() }
    val hover by interazione.collectIsHoveredAsState()
    fun sottometti() {
        val data = testo.aData()
        nonValido = data == null
        if (data != null && data != riga.dataRegistrazione) azioni.modificaData(riga.registrazioneId, data)
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.hoverable(interazione)) {
            BasicTextField(
                value = testo,
                onValueChange = { testo = it },
                singleLine = true,
                enabled = !riga.operazioneInCorso,
                textStyle = LocalSnastroTipografia.current.caption.copy(
                    color = if (nonValido) colori.danger else colori.inkMuted,
                ),
                cursorBrush = SolidColor(colori.accentInk),
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
            if (hover) {
                IconaSn(Icona.Edit, descrizione = null, tinta = colori.inkMuted, dimensione = SnastroMisure.iconS)
            }
        }
        if (nonValido) {
            Text(
                text = MESSAGGIO_DATA_NON_VALIDA,
                color = colori.danger,
                style = LocalSnastroTipografia.current.caption,
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
 * known/failed — [RegistrazioniPresenter] decides, this only renders what it is given). AC-575: no
 * literal coloured voice dots — [IdentificazioneRiga] carries only counts, no `VoceId`s to colour.
 */
@Composable
private fun BadgeIdentificazione(identificazione: IdentificazioneRiga, id: RegistrazioneId) {
    val colori = LocalSnastroColori.current
    Text(
        text = etichettaIdentificazione(identificazione.numVoci, identificazione.numVociDaIdentificare),
        style = LocalSnastroTipografia.current.caption,
        color = if (identificazione.numVociDaIdentificare > 0) colori.accentInk else colori.inkMuted,
        modifier = Modifier.testTag("registrazioni-identificazione-${id.valore}"),
    )
}

@Composable
private fun ColonnaElaborazione(stato: StatoElaborazioneRiga, riga: RigaRegistrazione, azioni: AzioniRegistrazioni) {
    val id = riga.registrazioneId
    val operazioneInCorso = riga.operazioneInCorso
    Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier.testTag("registrazioni-stato-${id.valore}"),
    ) {
        when (stato) {
            StatoElaborazioneRiga.NonAvviata ->
                AvvioConNumeroPersone(
                    riga,
                    ETICHETTA_TRASCRIVI,
                    null,
                    VarianteBottone.Primario,
                    azioni.modificaNumeroPersone,
                    azioni.avviaElaborazione,
                )
            is StatoElaborazioneRiga.InAttesa -> StatoInAttesa(stato, riga, azioni)
            is StatoElaborazioneRiga.InCorso -> StatoInCorso(stato)
            is StatoElaborazioneRiga.Fallita -> {
                Text(
                    text = stato.motivo,
                    color = LocalSnastroColori.current.danger,
                    style = LocalSnastroTipografia.current.caption,
                )
                AvvioConNumeroPersone(
                    riga,
                    ETICHETTA_RIPROVA,
                    Icona.Retry,
                    VarianteBottone.Secondario,
                    azioni.modificaNumeroPersone,
                    azioni.avviaElaborazione,
                )
            }
            StatoElaborazioneRiga.Completata -> ColonnaCompletata(riga, azioni)
        }
        if (operazioneInCorso) {
            CircularProgressIndicator(
                modifier = Modifier.padding(top = SnastroMisure.space1).width(DIMENSIONE_INDICATORE_PICCOLO)
                    .testTag("registrazioni-operazione-in-corso-${id.valore}"),
            )
        }
    }
}

/** AC-575: "In coda" chip + the optional 'Annulla' link (AC-475) + the distinguishing caption
 * (plain vs re-run — [TipoChipStato.InCoda] alone cannot tell them apart). */
@Composable
private fun StatoInAttesa(
    stato: StatoElaborazioneRiga.InAttesa,
    riga: RigaRegistrazione,
    azioni: AzioniRegistrazioni,
) {
    val id = riga.registrazioneId
    ChipStato(TipoChipStato.InCoda(stato.posizione))
    // AC-475: 'Annulla' on ANY IN_ATTESA row (plain or re-run) when the source is supplied.
    if (riga.annullabile) {
        BottoneSn(
            etichetta = ETICHETTA_ANNULLA,
            onClick = { azioni.annullaElaborazione(id) },
            variante = VarianteBottone.Link,
            piccolo = true,
            abilitato = !riga.operazioneInCorso,
            modifier = Modifier.padding(top = SnastroMisure.space1)
                .testTag("registrazioni-annulla-${id.valore}"),
        )
    }
    val etichettaAttesa = if (stato.ritrascrizione) {
        etichettaRitrascrizioneInAttesa(stato.posizione)
    } else {
        etichettaInAttesa(stato.posizione)
    }
    Text(etichettaAttesa, style = LocalSnastroTipografia.current.caption, color = LocalSnastroColori.current.inkMuted)
}

/** AC-575: "In corso" chip + the distinguishing caption (plain vs re-run — [TipoChipStato.InCorso]
 * alone cannot tell them apart). */
@Composable
private fun StatoInCorso(stato: StatoElaborazioneRiga.InCorso) {
    ChipStato(TipoChipStato.InCorso(stato.faseEtichetta, stato.trascorsoMs))
    Text(
        if (stato.ritrascrizione) {
            etichettaRitrascrizioneInCorso(stato.faseEtichetta, stato.trascorsoMs)
        } else {
            etichettaInCorso(stato.faseEtichetta, stato.trascorsoMs)
        },
        style = LocalSnastroTipografia.current.caption,
        color = LocalSnastroColori.current.inkMuted,
    )
}

/**
 * ADR 0018: 'Trascritta' chip plus, when applicable, AC-451's failed-re-run notice and AC-448/449's
 * 'Ritrascrivi' field/button — replaced by the inline confirmation (AC-449, styled like
 * `anteprime/Dialog.html`) once `riga.confermaRitrascrivi` is set.
 */
@Composable
private fun ColonnaCompletata(riga: RigaRegistrazione, azioni: AzioniRegistrazioni) {
    val id = riga.registrazioneId
    if (riga.confermaRitrascrivi) {
        ConfermaRitrascrivi(riga, azioni)
    } else {
        ChipStato(TipoChipStato.Trascritta)
        riga.ritrascrizioneFallita?.let {
            Text(
                text = messaggioRitrascrizioneNonRiuscita(it),
                color = LocalSnastroColori.current.danger,
                style = LocalSnastroTipografia.current.caption,
                modifier = Modifier.testTag("registrazioni-ritrascrizione-fallita-${id.valore}"),
            )
        }
        if (riga.ritrascriviDisponibile) {
            AvvioConNumeroPersone(
                riga,
                ETICHETTA_RITRASCRIVI,
                null,
                VarianteBottone.Secondario,
                azioni.modificaNumeroPersone,
                azioni.ritrascrivi,
            )
        }
    }
}

/**
 * AC-449: the row's OWN 'Ritrascrivi' field/button are replaced by this panel — styled like
 * `anteprime/Dialog.html` (title = the question, body = what is lost, `Primario` "Ritrascrivi" —
 * the design system's own example is not a destructive/`Pericolo` action) — never an OS-level modal
 * dialog (same mechanism as S4's `ConfermaEliminazioneParlante`). Wrapped to
 * [LARGHEZZA_CONFERMA_RITRASCRIVI] so the long text wraps inside the row's trailing column instead of
 * overflowing it.
 */
@Composable
private fun ConfermaRitrascrivi(riga: RigaRegistrazione, azioni: AzioniRegistrazioni) {
    val id = riga.registrazioneId
    val colori = LocalSnastroColori.current
    Column(
        modifier = Modifier
            .width(LARGHEZZA_CONFERMA_RITRASCRIVI)
            .testTag("registrazioni-conferma-ritrascrivi-${id.valore}"),
    ) {
        Text(
            text = titoloConfermaRitrascrivi(riga.titolo),
            style = LocalSnastroTipografia.current.title,
            color = colori.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(SnastroMisure.space1))
        Text(
            text = MESSAGGIO_CONFERMA_RITRASCRIVI,
            style = LocalSnastroTipografia.current.caption,
            color = colori.inkMuted,
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = SnastroMisure.space2)) {
            BottoneSn(
                etichetta = ETICHETTA_RITRASCRIVI,
                onClick = { azioni.confermaRitrascrivi(id) },
                variante = VarianteBottone.Primario,
                abilitato = !riga.operazioneInCorso,
                modifier = Modifier.testTag("registrazioni-conferma-ritrascrivi-conferma-${id.valore}"),
            )
            Spacer(modifier = Modifier.width(SnastroMisure.space2))
            BottoneSn(
                etichetta = ETICHETTA_ANNULLA,
                onClick = { azioni.annullaRitrascrivi(id) },
                variante = VarianteBottone.Secondario,
                abilitato = !riga.operazioneInCorso,
                modifier = Modifier.testTag("registrazioni-annulla-ritrascrivi-${id.valore}"),
            )
        }
    }
}

/**
 * ADR 0014: the plain 'Numero di persone' field (empty = automatic) next to a start/retry button
 * [etichetta] — shared by 'Trascrivi'/'Riprova'/'Ritrascrivi' ([onAvvia] carries which command). Its
 * text is presenter state ([RigaRegistrazione.numeroPersone]); validation and the inline message
 * (AC-375/449) are the presenter's, shown as the row's `erroreRiga`.
 */
@Suppress("LongParameterList") // one parameter per documented knob shared by Trascrivi/Riprova/Ritrascrivi
@Composable
private fun AvvioConNumeroPersone(
    riga: RigaRegistrazione,
    etichetta: String,
    icona: Icona?,
    variante: VarianteBottone,
    onModifica: (RegistrazioneId, String) -> Unit,
    onAvvia: (RegistrazioneId) -> Unit,
) {
    val id = riga.registrazioneId
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SnastroMisure.space2),
    ) {
        CampoNumeroPersone(
            valore = riga.numeroPersone,
            onValoreCambiato = { onModifica(id, it) },
            errore = riga.erroreRiga != null,
            abilitato = !riga.operazioneInCorso,
            modifier = Modifier.testTag("registrazioni-numero-persone-${id.valore}"),
        )
        BottoneSn(
            etichetta = etichetta,
            onClick = { onAvvia(id) },
            variante = variante,
            piccolo = true,
            icona = icona,
            abilitato = !riga.operazioneInCorso,
        )
    }
}

@Composable
private fun MessaggioInlineErrore(messaggio: String, onChiudi: () -> Unit, tag: String) {
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
