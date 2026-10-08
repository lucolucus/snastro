// TooManyFunctions: one row split into small, single-purpose composables (RC-2 thin view) — its play control,
// the inline titolo/data/ora editors they share and the parsers of what is typed into them.
@file:Suppress("TooManyFunctions")

package snastro.ui.registrazioni

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import snastro.kernel.IncontroId
import snastro.ui.formattaData
import snastro.ui.formattaDurata
import snastro.ui.stile.BottonePlay
import snastro.ui.stile.Icona
import snastro.ui.stile.IconaSn
import snastro.ui.stile.LocalSnastroColori
import snastro.ui.stile.LocalSnastroTipografia
import snastro.ui.stile.MessaggioInlineErrore
import snastro.ui.stile.SnastroMisure
import snastro.ui.testi.ETICHETTA_ORA_SCONOSCIUTA
import snastro.ui.testi.MESSAGGIO_AUDIO_NON_DISPONIBILE
import snastro.ui.testi.MESSAGGIO_DATA_NON_VALIDA
import snastro.ui.testi.MESSAGGIO_ORA_NON_VALIDA
import snastro.ui.testi.SUGGERIMENTO_ORA_SCONOSCIUTA
import snastro.ui.testi.etichettaParte
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle

/** M4: `uuuu` (proleptic year, not `yyyy`) + [ResolverStyle.STRICT] rejects an out-of-range day
 * (e.g. 31/02) instead of a SMART resolver silently rolling it into the next month (28/02). */
private val FORMATO_DATA_MODIFICABILE: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)

@Composable
internal fun RigaRegistrazioneItem(
    riga: RigaRegistrazione,
    azioni: AzioniRegistrazioni,
    aggiungiParti: (() -> Unit)?,
    incontroId: IncontroId,
) {
    // AC-626: the confirmation REPLACES the row's own (clickable) content entirely — same mechanism as
    // ConfermaRitrascrivi above, never an OS-level modal dialog.
    if (riga.confermaElimina) {
        ConfermaElimina(riga, azioni)
        return
    }
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
            // Pre-release finding #83 (rework): the More menu is ALWAYS present (`eliminazione` is
            // never absent, `RegistrazioniPresenter.eliminazioneDi` only ever returns Disponibile/
            // NonDisponibile) — the `StatoEliminazione.Assente` release-flag leftover that used to
            // gate this is retired.
            Spacer(modifier = Modifier.width(SnastroMisure.space2))
            MenuAzioniRegistrazione(riga, azioni, aggiungiParti, incontroId)
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

/**
 * AC-575: date (editable) · duration (`timecode`) · the FALLITA motivo (danger) · the identification
 * badge — `·`-separated like `RecordingRow.html`, normal `space2` gaps throughout (rework cycle 1:
 * the date field no longer forces a fixed width before its separator).
 */
@Composable
private fun RigaMeta(riga: RigaRegistrazione, azioni: AzioniRegistrazioni) {
    val colori = LocalSnastroColori.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SnastroMisure.space2),
    ) {
        riga.parte?.let {
            Text(text = etichettaParte(it.numero), style = LocalSnastroTipografia.current.label, color = colori.ink)
            Separatore()
        }
        CampoData(riga, azioni = azioni)
        if (riga.parte != null) {
            Separatore()
            CampoOra(riga, azioni)
        }
        Separatore()
        Text(
            text = formattaDurata(riga.durataMs),
            style = LocalSnastroTipografia.current.timecode,
            color = colori.inkMuted,
        )
        val motivo = (riga.elaborazione as? StatoElaborazioneRiga.Fallita)?.motivo
        if (motivo != null) {
            Separatore()
            Text(text = motivo, style = LocalSnastroTipografia.current.caption, color = colori.danger)
        }
        riga.identificazione?.let {
            Separatore()
            BadgeIdentificazione(it, riga.registrazioneId.valore)
        }
    }
}

@Composable
internal fun Separatore() {
    Text(text = "·", style = LocalSnastroTipografia.current.caption, color = LocalSnastroColori.current.inkMuted)
}

@Composable
internal fun ControlloRiproduzione(riga: RigaRegistrazione, azioni: AzioniRegistrazioni) {
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
 * operation settled, whether or not an intermediate `true` frame ever rendered. L530c: the resync itself
 * runs in a [SideEffect] (after composition commits), not as a raw statement in the composable body —
 * a plain `if` there WRITES snapshot state DURING composition. `SideEffect` (unlike `LaunchedEffect`) has
 * no key/gating of its own and runs after EVERY recomposition unconditionally, so it still observes
 * exactly the same [riga.operazioneInCorso] value this composition just read — a `LaunchedEffect` keyed
 * on it would reintroduce the coalescing race above (a key that never "changes" across two separate
 * composition passes because both values were collapsed into one). Esc reverts the same way, without
 * submitting. AC-575: renders as plain `heading` text at rest (borderless field, same pattern as
 * [CampoData]) — "title heading ellipsised" in the visual target.
 */
@Composable
private fun CampoTitolo(riga: RigaRegistrazione, azioni: AzioniRegistrazioni) {
    val colori = LocalSnastroColori.current
    var testo by remember(riga.titolo) { mutableStateOf(riga.titolo) }
    var eraFocalizzato by remember(riga.titolo) { mutableStateOf(false) }
    var inviato by remember(riga.titolo) { mutableStateOf(false) }
    SideEffect {
        if (inviato && !riga.operazioneInCorso) {
            testo = riga.titolo
            inviato = false
        }
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
 * no 31/02 silently rolled to 28/02) is shown as an inline error instead of being dropped. Esc reverts
 * without submitting. L755d: on a successful parse [testo] is always re-set from `formattaData(data)`
 * (not just when [data] differs from the row's own date) — the field never keeps showing raw user input
 * once it is known to parse, even when that input's date turns out unchanged. L755c: leaving edit mode
 * ([termina]) hands focus back to the read-only [Text] ([focusData]) — swapping the focused
 * `BasicTextField` out of composition would otherwise drop focus on the floor (a keyboard user loses
 * their place). AC-575 "caption text with an Edit icon on hover (no boxed field at rest)" — rework
 * cycle 2 (LOW #5): at rest it IS plain `Text` (a `BasicTextField` keeps a minimum width that left a gap
 * before the `·`); a click swaps in the focused field, leaving it (Enter/blur/Esc) swaps back.
 */
@Composable
private fun CampoData(riga: RigaRegistrazione, azioni: AzioniRegistrazioni) {
    val id = riga.registrazioneId.valore
    CampoInline(
        valore = riga.dataRegistrazione,
        formatta = ::formattaData,
        interpreta = { t -> t.aData()?.let { Interpretato(it) } },
        messaggioNonValido = MESSAGGIO_DATA_NON_VALIDA,
        abilitato = !riga.operazioneInCorso,
        onModifica = { azioni.modificaData(riga.registrazioneId, it) },
        tag = "registrazioni-data-$id",
        tagErrore = "registrazioni-data-errore-$id",
    )
}

/**
 * AC-I68: a Parte's `OraDiInizio`, edited exactly like the date ([CampoInline]); an empty time reads '—:—' with its
 * tooltip, and clearing the text clears the time.
 */
@Composable
private fun CampoOra(riga: RigaRegistrazione, azioni: AzioniRegistrazioni) {
    val id = riga.registrazioneId.valore
    CampoInline(
        valore = riga.oraDiInizio,
        formatta = { it?.let(::formattaOra) ?: ETICHETTA_ORA_SCONOSCIUTA },
        interpreta = { it.aOra() },
        messaggioNonValido = MESSAGGIO_ORA_NON_VALIDA,
        abilitato = !riga.operazioneInCorso,
        onModifica = { azioni.modificaOraDiInizio(riga.registrazioneId, it) },
        tag = "registrazioni-ora-$id",
        tagErrore = "registrazioni-ora-errore-$id",
        testoModifica = { it?.let(::formattaOra).orEmpty() },
        suggerimento = if (riga.oraDiInizio == null) SUGGERIMENTO_ORA_SCONOSCIUTA else null,
    )
}

/** [contenuto], wrapped in a hover tooltip saying [suggerimento] when there is one. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConSuggerimento(suggerimento: String?, contenuto: @Composable () -> Unit) {
    if (suggerimento == null) {
        contenuto()
        return
    }
    val colori = LocalSnastroColori.current
    TooltipArea(
        tooltip = {
            Surface(color = colori.raised, shape = RoundedCornerShape(SnastroMisure.radiusControl)) {
                Text(
                    suggerimento,
                    style = LocalSnastroTipografia.current.caption,
                    color = colori.ink,
                    modifier = Modifier.padding(SnastroMisure.space2),
                )
            }
        },
        content = contenuto,
    )
}

/** What [CampoInline] parsed: [valore] may legitimately be `null` (a cleared time), unlike a failed parse. */
internal class Interpretato<T>(val valore: T)

/** AC-I68: `HH:mm` (a start time to the minute); blank clears the time; anything else is not a time. */
internal fun String.aOra(): Interpretato<LocalTime?>? {
    val t = trim()
    val m = REGEX_ORA.matchEntire(t)
    return when {
        t.isEmpty() -> Interpretato(null)
        m == null -> null
        else -> Interpretato(LocalTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt()))
    }
}

private val REGEX_ORA = Regex("""([01]?\d|2[0-3]):([0-5]\d)""")

internal fun formattaOra(ora: LocalTime): String = ora.format(DateTimeFormatter.ofPattern("HH:mm"))

@Suppress("LongParameterList") // one parameter per documented knob of the inline editor
@Composable
private fun <T> CampoInline(
    valore: T,
    formatta: (T) -> String,
    interpreta: (String) -> Interpretato<T>?,
    messaggioNonValido: String,
    abilitato: Boolean,
    onModifica: (T) -> Unit,
    tag: String,
    tagErrore: String,
    testoModifica: (T) -> String = formatta,
    suggerimento: String? = null,
) {
    val colori = LocalSnastroColori.current
    var testo by remember(valore) { mutableStateOf(formatta(valore)) }
    var nonValido by remember(valore) { mutableStateOf(false) }
    var inModifica by remember(valore) { mutableStateOf(false) }
    val interazione = remember { MutableInteractionSource() }
    val hover by interazione.collectIsHoveredAsState()
    val stile = LocalSnastroTipografia.current.caption.copy(color = if (nonValido) colori.danger else colori.inkMuted)
    val focusTesto = remember { FocusRequester() }
    var richiediFocusTesto by remember { mutableStateOf(false) }
    fun termina(sottometti: Boolean) {
        if (!inModifica) return // Enter then the blur of the removed field: one submit only
        inModifica = false
        richiediFocusTesto = true // L755c
        if (!sottometti) {
            testo = formatta(valore)
            nonValido = false
            return
        }
        val nuovo = interpreta(testo)
        nonValido = nuovo == null
        if (nuovo != null) {
            testo = formatta(nuovo.valore) // L755d
            if (formatta(nuovo.valore) != formatta(valore)) onModifica(nuovo.valore)
        }
    }
    // L755c: fires only once the read-only Text (below) is actually back in composition — requesting
    // focus while the BasicTextField still owns it (or before either is laid out) would throw.
    LaunchedEffect(richiediFocusTesto, inModifica) {
        if (richiediFocusTesto && !inModifica) {
            focusTesto.requestFocus()
            richiediFocusTesto = false
        }
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.hoverable(interazione)) {
            if (inModifica) {
                CampoDataInModifica(testo, { testo = it }, stile, abilitato, ::termina, tag)
            } else {
                val lettura = @Composable {
                    Text(
                        text = testo,
                        style = stile,
                        modifier = Modifier
                            .focusRequester(focusTesto)
                            .focusable()
                            .clickable(enabled = abilitato) {
                                // L205: an invalid typed text is what the user came back to fix — keep it.
                                if (!nonValido) testo = testoModifica(valore)
                                inModifica = true
                            }
                            .testTag(tag),
                    )
                }
                ConSuggerimento(suggerimento, lettura)
                if (hover) {
                    IconaSn(Icona.Edit, descrizione = null, tinta = colori.inkMuted, dimensione = SnastroMisure.iconS)
                }
            }
        }
        if (nonValido) {
            Text(
                text = messaggioNonValido,
                color = colori.danger,
                style = LocalSnastroTipografia.current.caption,
                modifier = Modifier.testTag(tagErrore),
            )
        }
    }
}

/** [CampoInline] while editing: focused on entry; Enter/blur → `termina(true)`, Esc → `termina(false)`. */
@Suppress("LongParameterList") // one parameter per documented knob of the inline editor
@Composable
private fun CampoDataInModifica(
    testo: String,
    onTesto: (String) -> Unit,
    stile: TextStyle,
    abilitato: Boolean,
    termina: (sottometti: Boolean) -> Unit,
    tag: String,
) {
    val focus = remember { FocusRequester() }
    var eraFocalizzato by remember { mutableStateOf(false) }
    LaunchedEffect(focus) { focus.requestFocus() }
    BasicTextField(
        value = testo,
        onValueChange = onTesto,
        singleLine = true,
        enabled = abilitato,
        textStyle = stile,
        cursorBrush = SolidColor(LocalSnastroColori.current.accentInk),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { termina(true) }),
        modifier = Modifier
            .focusRequester(focus)
            .onFocusChanged { stato ->
                if (eraFocalizzato && !stato.isFocused) termina(true)
                eraFocalizzato = stato.isFocused
            }
            .onPreviewKeyEvent { evento ->
                if (evento.type == KeyEventType.KeyDown && evento.key == Key.Escape) {
                    termina(false)
                    true
                } else {
                    false
                }
            }
            .testTag(tag),
    )
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
