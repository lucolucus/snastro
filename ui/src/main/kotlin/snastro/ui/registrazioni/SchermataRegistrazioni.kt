// TooManyFunctions: one screen split into many small, single-purpose composables (RC-2 thin view) —
// the natural shape of a row with a play control, inline titolo/date fields and a per-state status column.
@file:Suppress("TooManyFunctions")

package snastro.ui.registrazioni

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.ui.SnastroTema
import snastro.ui.formattaData
import snastro.ui.formattaDurata
import snastro.ui.formattaDurataEstesa
import snastro.ui.stile.AzioneBanner
import snastro.ui.stile.BannerSn
import snastro.ui.stile.BottoneIconaSn
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
import snastro.ui.temaScuro
import snastro.ui.testi.ETICHETTA_AGGIUNGI_PARTI
import snastro.ui.testi.ETICHETTA_ALTRE_AZIONI
import snastro.ui.testi.ETICHETTA_ANNULLA
import snastro.ui.testi.ETICHETTA_CHIUDI_ERRORE
import snastro.ui.testi.ETICHETTA_COMPRIMI_PARTI
import snastro.ui.testi.ETICHETTA_CONFERMA_ELIMINAZIONE
import snastro.ui.testi.ETICHETTA_DA_IDENTIFICARE
import snastro.ui.testi.ETICHETTA_ELIMINA
import snastro.ui.testi.ETICHETTA_ESPANDI_PARTI
import snastro.ui.testi.ETICHETTA_IMPORTAZIONE_NON_RIUSCITA
import snastro.ui.testi.ETICHETTA_IMPORTA_FILE
import snastro.ui.testi.ETICHETTA_ORA_SCONOSCIUTA
import snastro.ui.testi.ETICHETTA_RIPROVA
import snastro.ui.testi.ETICHETTA_RITRASCRIVI
import snastro.ui.testi.ETICHETTA_SCEGLI_FILE
import snastro.ui.testi.ETICHETTA_TRASCRIVI
import snastro.ui.testi.MESSAGGIO_AUDIO_NON_DISPONIBILE
import snastro.ui.testi.MESSAGGIO_CONFERMA_ELIMINA_CON_TRASCRITTO
import snastro.ui.testi.MESSAGGIO_CONFERMA_ELIMINA_CON_TRASCRITTO_RESIDUO
import snastro.ui.testi.MESSAGGIO_CONFERMA_ELIMINA_PARTE_CON_TRASCRITTO
import snastro.ui.testi.MESSAGGIO_CONFERMA_ELIMINA_SENZA_TRASCRITTO
import snastro.ui.testi.MESSAGGIO_DATA_NON_VALIDA
import snastro.ui.testi.MESSAGGIO_FORMATI_AUDIO_SUPPORTATI
import snastro.ui.testi.MESSAGGIO_ORA_NON_VALIDA
import snastro.ui.testi.MESSAGGIO_REGISTRAZIONI_VUOTO
import snastro.ui.testi.MESSAGGIO_RILASCIA_PER_IMPORTARE
import snastro.ui.testi.SUGGERIMENTO_ORA_SCONOSCIUTA
import snastro.ui.testi.etichettaIdentificazione
import snastro.ui.testi.etichettaParte
import snastro.ui.testi.etichettaParteInCoda
import snastro.ui.testi.etichettaParteInCorso
import snastro.ui.testi.etichettaParteNonRiuscita
import snastro.ui.testi.etichettaRegistrazioni
import snastro.ui.testi.etichettaRitrascrizioneInAttesa
import snastro.ui.testi.etichettaRitrascrizioneInCorso
import snastro.ui.testi.etichettaTrascriviParti
import snastro.ui.testi.messaggioRitrascrizioneNonRiuscita
import snastro.ui.testi.titoloConfermaElimina
import snastro.ui.testi.titoloConfermaEliminaParte
import snastro.ui.testi.titoloIncontro
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle

private val DIMENSIONE_INDICATORE_PICCOLO = 18.dp
private val LARGHEZZA_CONFERMA_RITRASCRIVI = 320.dp
private val DIAMETRO_DROPZONE_ICONA = 28.dp
private val ALTEZZA_MINIMA_DROPZONE = 360.dp
private val SPESSORE_TRATTEGGIO_NORMALE = 1.dp
private val SPESSORE_TRATTEGGIO_OVER = 1.5.dp
private const val TRATTO_LUNGHEZZA = 8f
private const val TRATTO_INTERVALLO = 6f

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
 * NOTE (restyle scope): AC-574's "project name in `display`" is not rendered here (rework cycle 1:
 * confirmed out of scope — the name stays in the shell's sidebar, [RegistrazioniUiStato] carries none).
 */
@Composable
fun SchermataRegistrazioni(
    stato: RegistrazioniUiStato,
    azioni: AzioniRegistrazioni,
    scuro: Boolean = temaScuro(),
    riduciMovimento: Boolean? = null,
    sceltaFileAudio: SceltaFileAudio = SceltaFileAudio { emptyList() },
) {
    SchermataRegistrazioni(stato, azioni, scuro, riduciMovimento, dragIniziale = false, sceltaFileAudio)
}

/**
 * Render-check entry point (rework cycle 2, MED #4): [dragIniziale] starts the screen with an OS drag
 * already over the window, so the `over` fixture is the REAL screen — no native-drag simulation API
 * exists in the test harness. Production always goes through the public overload (`false`).
 */
@Suppress("LongParameterList") // render-check entry: the public overload's five parameters + the drag fixture
@Composable
internal fun SchermataRegistrazioni(
    stato: RegistrazioniUiStato,
    azioni: AzioniRegistrazioni,
    scuro: Boolean,
    riduciMovimento: Boolean?,
    dragIniziale: Boolean,
    sceltaFileAudio: SceltaFileAudio = SceltaFileAudio { emptyList() },
) {
    SnastroTema(scuro = scuro, riduciMovimento = riduciMovimento) {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (stato) {
                RegistrazioniUiStato.Caricamento -> IndicatoreCaricamentoRegistrazioni()
                is RegistrazioniUiStato.Dati -> ContenutoRegistrazioni(stato, azioni, dragIniziale, sceltaFileAudio)
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
private fun ContenutoRegistrazioni(
    stato: RegistrazioniUiStato.Dati,
    azioni: AzioniRegistrazioni,
    dragIniziale: Boolean,
    sceltaFileAudio: SceltaFileAudio,
) {
    var dragAttivo by remember { mutableStateOf(dragIniziale) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = SnastroMisure.space5, horizontal = SnastroMisure.space6)
            .dragAndDropTarget(
                shouldStartDragAndDrop = { !stato.importoInCorso },
                target = remember(azioni) { registrazioneDropTarget(azioni.importa) { dragAttivo = it } },
            )
            .testTag("registrazioni-drop-target")
            .verticalScroll(rememberScrollState()),
    ) {
        if (stato.righe.isNotEmpty()) {
            IntestazioneRegistrazioni(stato, azioni, sceltaFileAudio)
        } else {
            BarraImportazione(azioni, stato.importoInCorso, sceltaFileAudio)
        }
        // L485a: `errore` (import) and `erroreAggiornamento` (background refresh) are two SEPARATE
        // lifecycles on the presenter (only a success clears the latter; an unrelated refresh never
        // touches the former, M1) — AC-566 still allows only one banner on screen, so import takes
        // priority (it is the direct result of the user's own last action here). ADR 0020/AC-627:
        // `avviso` (the Elimina success notice) is a THIRD, lower-priority lifecycle — shown only when
        // no error banner is pending.
        when (val messaggio = stato.errore ?: stato.erroreAggiornamento) {
            null -> stato.avviso?.let {
                Spacer(modifier = Modifier.height(SnastroMisure.space3))
                BannerSn(
                    tipo = TipoBanner.Info,
                    titolo = stato.titoloAvviso,
                    testo = it,
                    azione = AzioneBanner(ETICHETTA_CHIUDI_ERRORE, azioni.chiudiAvviso),
                    modifier = Modifier.testTag("registrazioni-avviso"),
                )
            }
            else -> {
                Spacer(modifier = Modifier.height(SnastroMisure.space3))
                BannerSn(
                    tipo = TipoBanner.Errore,
                    titolo = ETICHETTA_IMPORTAZIONE_NON_RIUSCITA,
                    testo = messaggio,
                    azione = AzioneBanner(ETICHETTA_CHIUDI_ERRORE, azioni.chiudiErrore),
                    modifier = Modifier.testTag("registrazioni-errore"),
                )
            }
        }
        stato.dialogoImporta?.let {
            Spacer(modifier = Modifier.height(SnastroMisure.space3))
            DialogoImportaParti(it, azioni)
        }
        Spacer(modifier = Modifier.height(SnastroMisure.space4))
        if (stato.righe.isEmpty()) {
            DropZoneVuota(dragAttivo, stato.importoInCorso, azioni, sceltaFileAudio)
        } else {
            ElencoRegistrazioni(stato, azioni, inDrop = dragAttivo, sceltaFileAudio = sceltaFileAudio)
        }
    }
}

/** AC-574: "n registrazioni · <durata estesa totale>" (sum of `durataMs` — view arithmetic on the
 * rows already in state) + 'Importa audio…' `Primario`. Hidden when the list is empty (rework cycle 1
 * — a "0 registrazioni · 0 min" caption next to an empty `DropZone` says nothing useful). */
@Composable
private fun IntestazioneRegistrazioni(
    stato: RegistrazioniUiStato.Dati,
    azioni: AzioniRegistrazioni,
    sceltaFileAudio: SceltaFileAudio,
) {
    val colori = LocalSnastroColori.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        val durataTotale = stato.righe.sumOf { it.durataMs }
        Text(
            text = "${etichettaRegistrazioni(stato.righe.size)} · ${formattaDurataEstesa(durataTotale)}",
            style = LocalSnastroTipografia.current.caption,
            color = colori.inkMuted,
            modifier = Modifier.weight(1f),
        )
        BarraImportazione(azioni, stato.importoInCorso, sceltaFileAudio)
    }
}

@Composable
private fun BarraImportazione(azioni: AzioniRegistrazioni, importoInCorso: Boolean, sceltaFileAudio: SceltaFileAudio) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BottoneSn(
            etichetta = ETICHETTA_IMPORTA_FILE,
            onClick = { importaScelti(sceltaFileAudio, azioni) },
            variante = VarianteBottone.Primario,
            icona = Icona.Import,
            abilitato = !importoInCorso,
            modifier = Modifier.testTag("registrazioni-importa"),
        )
        if (importoInCorso) {
            CircularProgressIndicator(
                modifier = Modifier.padding(start = SnastroMisure.space2).width(DIMENSIONE_INDICATORE_PICCOLO)
                    .testTag("registrazioni-import-in-corso"),
            )
        }
    }
}

/**
 * AC-576: the empty-state `DropZone`, LARGE ([ALTEZZA_MINIMA_DROPZONE] — rework cycle 2: `fillMaxSize`
 * inside the screen's `verticalScroll` collapsed to its content) — Import 28dp, the empty message, the
 * supported formats and a `Secondario` "Scegli file…" (same [ETICHETTA_IMPORTA_FILE] command, disabled
 * while an import runs, like the header button). While an OS drag is over the window (`onEntered`/
 * `onExited` on [registrazioneDropTarget], pure view state) it switches to the `over` style —
 * `accentInk` 1.5dp dashed border, `accentSoft` fill, the Import icon in `accentInk`, "Rilascia per
 * importare".
 */
@Composable
private fun DropZoneVuota(
    inDrop: Boolean,
    importoInCorso: Boolean,
    azioni: AzioniRegistrazioni,
    sceltaFileAudio: SceltaFileAudio,
) {
    val colori = LocalSnastroColori.current
    val bordo = if (inDrop) colori.accentInk else colori.lineStrong
    val fondo = if (inDrop) colori.accentSoft else colori.sunken
    val spessore = if (inDrop) SPESSORE_TRATTEGGIO_OVER else SPESSORE_TRATTEGGIO_NORMALE
    val coloreIcona = if (inDrop) colori.accentInk else colori.inkMuted
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ALTEZZA_MINIMA_DROPZONE)
            .background(fondo, RoundedCornerShape(SnastroMisure.radiusCard))
            .bordoTratteggiato(bordo, spessore, SnastroMisure.radiusCard)
            .padding(SnastroMisure.space6)
            .testTag("registrazioni-vuoto"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IconaSn(Icona.Import, descrizione = null, tinta = coloreIcona, dimensione = DIAMETRO_DROPZONE_ICONA)
        Spacer(modifier = Modifier.height(SnastroMisure.space3))
        if (inDrop) {
            Text(
                text = MESSAGGIO_RILASCIA_PER_IMPORTARE,
                style = LocalSnastroTipografia.current.body,
                color = colori.ink,
            )
        } else {
            Text(text = MESSAGGIO_REGISTRAZIONI_VUOTO, style = LocalSnastroTipografia.current.body, color = colori.ink)
            Spacer(modifier = Modifier.height(SnastroMisure.space1))
            Text(
                text = MESSAGGIO_FORMATI_AUDIO_SUPPORTATI,
                style = LocalSnastroTipografia.current.caption,
                color = colori.inkMuted,
            )
            Spacer(modifier = Modifier.height(SnastroMisure.space3))
            BottoneSn(
                etichetta = ETICHETTA_SCEGLI_FILE,
                onClick = { importaScelti(sceltaFileAudio, azioni) },
                variante = VarianteBottone.Secondario,
                piccolo = true,
                abilitato = !importoInCorso,
                modifier = Modifier.testTag("registrazioni-scegli-file"),
            )
        }
    }
}

/** A dashed border (`over`/empty `DropZone`) — Compose's `Modifier.border` has no dash support. */
private fun Modifier.bordoTratteggiato(colore: Color, spessore: Dp, raggio: Dp): Modifier = drawBehind {
    val tratto = Stroke(
        width = spessore.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(TRATTO_LUNGHEZZA, TRATTO_INTERVALLO), 0f),
    )
    drawRoundRect(color = colore, style = tratto, cornerRadius = CornerRadius(raggio.toPx()))
}

/** AC-575/rework cycle 1 (composer finding #6): the container WRAPS its rows (a plain `Column`, not a
 * `LazyColumn` filling the remaining height) — the screen itself scrolls ([ContenutoRegistrazioni]).
 * L742d: [inDrop] (an OS drag currently over the window, non-empty list) switches the border to the same
 * `accentInk`/[SPESSORE_TRATTEGGIO_OVER] the empty [DropZoneVuota] uses — otherwise a drop over an
 * already-populated list gave no visual feedback at all. */
@Composable
private fun ElencoRegistrazioni(
    stato: RegistrazioniUiStato.Dati,
    azioni: AzioniRegistrazioni,
    inDrop: Boolean = false,
    sceltaFileAudio: SceltaFileAudio,
) {
    val colori = LocalSnastroColori.current
    val perId = stato.righe.associateBy { it.registrazioneId }
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("registrazioni-lista"),
        color = if (inDrop) colori.accentSoft else colori.raised,
        shape = RoundedCornerShape(SnastroMisure.radiusCard),
        border = BorderStroke(
            if (inDrop) SPESSORE_TRATTEGGIO_OVER else SPESSORE_TRATTEGGIO_NORMALE,
            if (inDrop) colori.accentInk else colori.line,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Rework cycle 2 (MED #2): keyed by id — the row-local state (title/date buffers, focus)
            // follows its Registrazione when a row is inserted/removed above it.
            stato.incontri.forEachIndexed { indice, incontro ->
                key(incontro.incontroId) {
                    if (indice > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(colori.line))
                    IncontroItem(incontro, incontro.parti.mapNotNull(perId::get), azioni, sceltaFileAudio)
                }
            }
        }
    }
}

/**
 * AC-I66..I68/INV-I3: a 1-part Incontro IS its Parte's row, today's, only its More menu gains 'Aggiungi parti…';
 * a multi-part one is [RigaIncontroItem] and, when expanded, its Parti as indented sub-rows in Parte order.
 */
@Composable
private fun IncontroItem(
    incontro: RigaIncontro,
    parti: List<RigaRegistrazione>,
    azioni: AzioniRegistrazioni,
    sceltaFileAudio: SceltaFileAudio,
) {
    if (parti.isEmpty()) return
    val aggiungiParti: () -> Unit = {
        val percorsi = sceltaFileAudio.scegli()
        if (percorsi.isNotEmpty()) azioni.aggiungiParti(incontro.incontroId, incontro.titolo, percorsi)
    }
    if (!incontro.multiParte || parti.size == 1) {
        RigaRegistrazioneItem(
            parti.first(),
            azioni,
            aggiungiParti.takeIf { incontro.aggiungiPartiDisponibile },
            incontro.incontroId,
        )
        return
    }
    val colori = LocalSnastroColori.current
    RigaIncontroItem(incontro, parti.first(), azioni, aggiungiParti)
    if (incontro.espanso) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = SnastroMisure.space6)
                .testTag("registrazioni-incontro-parti-${incontro.incontroId.valore}"),
        ) {
            parti.forEach { parte ->
                key(parte.registrazioneId) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(colori.line))
                    RigaRegistrazioneItem(parte, azioni, null, incontro.incontroId)
                }
            }
        }
    }
}

/** AC-I66: the collapsed row of a multi-part Incontro — chevron, '▶' of Parte 1, 'titolo · N parti', date · duration ·
 * badge, the aggregated state ([ColonnaIncontro]) and the More menu ('Aggiungi parti…'). A click opens S3 of
 * Parte 1. */
@Composable
private fun RigaIncontroItem(
    incontro: RigaIncontro,
    prima: RigaRegistrazione,
    azioni: AzioniRegistrazioni,
    aggiungiParti: () -> Unit,
) {
    val colori = LocalSnastroColori.current
    val id = incontro.incontroId.valore
    val sfondo = if (prima.riproduzione == StatoRiproduzioneRiga.InRiproduzione) colori.accentSoft else colori.raised
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(sfondo)
            .clickable(enabled = prima.trascrittoDisponibile) { azioni.apriRiga(prima.registrazioneId) }
            .padding(horizontal = SnastroMisure.space4, vertical = SnastroMisure.space3)
            .testTag("registrazioni-incontro-$id"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            BottoneIconaSn(
                icona = if (incontro.espanso) Icona.ChevronDown else Icona.ChevronRight,
                descrizione = if (incontro.espanso) ETICHETTA_COMPRIMI_PARTI else ETICHETTA_ESPANDI_PARTI,
                onClick = { azioni.espandiIncontro(incontro.incontroId) },
                modifier = Modifier.testTag("registrazioni-incontro-chevron-$id"),
            )
            ControlloRiproduzione(prima, azioni)
            Spacer(modifier = Modifier.width(SnastroMisure.space3))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = titoloIncontro(incontro.titolo, incontro.parti.size),
                    style = LocalSnastroTipografia.current.heading,
                    color = colori.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("registrazioni-incontro-titolo-$id"),
                )
                MetaIncontro(incontro)
            }
            Spacer(modifier = Modifier.width(SnastroMisure.space3))
            ColonnaIncontro(incontro, azioni)
            Spacer(modifier = Modifier.width(SnastroMisure.space2))
            MenuAltreAzioni("registrazioni-incontro-altre-azioni-$id", !incontro.operazioneInCorso) { chiudi ->
                VoceMenuAggiungiParti("registrazioni-menu-aggiungi-parti-$id") {
                    chiudi()
                    aggiungiParti()
                }
            }
        }
        incontro.errore?.let {
            MessaggioInlineErrore(
                it,
                { azioni.chiudiErroreIncontro(incontro.incontroId) },
                "registrazioni-errore-incontro-$id",
            )
        }
    }
}

@Composable
private fun MetaIncontro(incontro: RigaIncontro) {
    val colori = LocalSnastroColori.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SnastroMisure.space2),
    ) {
        Text(
            text = formattaData(incontro.data),
            style = LocalSnastroTipografia.current.caption,
            color = colori.inkMuted,
        )
        Separatore()
        Text(
            text = formattaDurata(incontro.durataMs),
            style = LocalSnastroTipografia.current.timecode,
            color = colori.inkMuted,
        )
        incontro.identificazione?.let {
            Separatore()
            BadgeIdentificazione(it, incontro.incontroId.valore)
        }
    }
}

/** AC-I67: the ONE 'Numero di persone' field and 'Trascrivi N parti' of an Incontro with Parti never started. */
@Composable
private fun AvvioIncontro(incontro: RigaIncontro, numParti: Int, azioni: AzioniRegistrazioni) {
    val id = incontro.incontroId.valore
    CampoNumeroPersone(
        valore = incontro.numeroPersone,
        onValoreCambiato = { azioni.modificaNumeroPersoneIncontro(incontro.incontroId, it) },
        errore = incontro.errore != null,
        abilitato = !incontro.operazioneInCorso,
        onInvio = { azioni.avviaElaborazioniIncontro(incontro.incontroId) },
        modifier = Modifier.testTag("registrazioni-incontro-numero-persone-$id"),
    )
    BottoneSn(
        etichetta = etichettaTrascriviParti(numParti),
        onClick = { azioni.avviaElaborazioniIncontro(incontro.incontroId) },
        variante = VarianteBottone.Primario,
        piccolo = true,
        abilitato = !incontro.operazioneInCorso,
        modifier = Modifier.testTag("registrazioni-incontro-trascrivi-$id"),
    )
}

/**
 * AC-I66: the aggregated state of a multi-part Incontro, one line like a row's ([ColonnaElaborazione]): a Parte in
 * progress / queued ('Annulla') / failed name the Parte; Parti never started offer the ONE 'Numero di persone'
 * field and 'Trascrivi N parti' (AC-I67); otherwise 'Trascritta' (or 'Da identificare' while Voci are unidentified).
 */
@Composable
private fun ColonnaIncontro(incontro: RigaIncontro, azioni: AzioniRegistrazioni) {
    val colori = LocalSnastroColori.current
    val id = incontro.incontroId.valore
    val tipografia = LocalSnastroTipografia.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SnastroMisure.space2),
        modifier = Modifier.testTag("registrazioni-incontro-stato-$id"),
    ) {
        when (val stato = incontro.stato) {
            is StatoIncontro.ParteInCorso -> Text(
                text = with(stato) { etichettaParteInCorso(numero, faseEtichetta, trascorsoMs, ritrascrizione) },
                style = tipografia.caption,
                color = colori.ink,
            )
            is StatoIncontro.ParteInCoda -> {
                Text(
                    text = etichettaParteInCoda(stato.numero, stato.posizione, stato.ritrascrizione),
                    style = tipografia.caption,
                    color = colori.ink,
                )
                BottoneSn(
                    etichetta = ETICHETTA_ANNULLA,
                    onClick = { azioni.annullaElaborazione(stato.registrazioneId) },
                    variante = VarianteBottone.Link,
                    piccolo = true,
                    modifier = Modifier.testTag("registrazioni-incontro-annulla-$id"),
                )
            }
            is StatoIncontro.ParteNonRiuscita ->
                ChipStato(TipoChipStato.NonRiuscita(etichettaParteNonRiuscita(stato.numero)))
            is StatoIncontro.DaTrascrivere -> AvvioIncontro(incontro, stato.numParti, azioni)
            StatoIncontro.Completata ->
                if ((incontro.identificazione?.numVociDaIdentificare ?: 0) > 0) {
                    ChipStato(TipoChipStato.Avviso(ETICHETTA_DA_IDENTIFICARE, Icona.People))
                } else {
                    ChipStato(TipoChipStato.Trascritta)
                }
            null -> Unit
        }
        if (incontro.operazioneInCorso) {
            CircularProgressIndicator(
                modifier = Modifier.width(DIMENSIONE_INDICATORE_PICCOLO)
                    .testTag("registrazioni-incontro-operazione-in-corso-$id"),
            )
        }
    }
}

@Composable
private fun RigaRegistrazioneItem(
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
 * ADR 0020 §6/AC-625: `BottoneIcona More` opening an `anteprime/Menu.html`-style menu — 'Ritrascrivi'
 * (only when [RigaRegistrazione.ritrascriviDisponibile], same field/flow as before, listed first like
 * S4's non-destructive-before-destructive order), 'Aggiungi parti…' (only on a 1-part Incontro's row: [aggiungiParti]
 * non-null) and 'Elimina…' (always, enabled/disabled per [RigaRegistrazione.eliminazione]) — same mechanism as S4's
 * `MenuAltreAzioniParlante`.
 */
@Composable
private fun MenuAzioniRegistrazione(
    riga: RigaRegistrazione,
    azioni: AzioniRegistrazioni,
    aggiungiParti: (() -> Unit)?,
    incontroId: IncontroId,
) {
    val id = riga.registrazioneId
    MenuAltreAzioni("registrazioni-altre-azioni-${id.valore}", !riga.operazioneInCorso) { chiudi ->
        if (riga.ritrascriviDisponibile) {
            DropdownMenuItem(
                text = { Text(ETICHETTA_RITRASCRIVI) },
                onClick = {
                    chiudi()
                    azioni.ritrascrivi(id)
                },
                modifier = Modifier.testTag("registrazioni-menu-ritrascrivi-${id.valore}"),
            )
        }
        aggiungiParti?.let {
            VoceMenuAggiungiParti("registrazioni-menu-aggiungi-parti-${incontroId.valore}") {
                chiudi()
                it()
            }
        }
        VoceMenuElimina(
            riga.eliminazione,
            onClick = {
                chiudi()
                azioni.elimina(id)
            },
            tag = "registrazioni-menu-elimina-${id.valore}",
        )
    }
}

/** The row's `More` button and its menu; [contenuto] receives the `chiudi` that also returns focus to the button. */
@Composable
private fun MenuAltreAzioni(
    tag: String,
    abilitato: Boolean,
    contenuto: @Composable ColumnScope.(chiudi: () -> Unit) -> Unit,
) {
    var espanso by remember { mutableStateOf(false) }
    // L742b (S4 precedent): focus returns to this same button once the menu closes.
    val richiestaFocus = remember { FocusRequester() }
    fun chiudi() {
        espanso = false
        richiestaFocus.requestFocus()
    }
    Box {
        BottoneIconaSn(
            icona = Icona.More,
            descrizione = ETICHETTA_ALTRE_AZIONI,
            onClick = { espanso = true },
            abilitato = abilitato,
            modifier = Modifier.focusRequester(richiestaFocus).testTag(tag),
        )
        DropdownMenu(expanded = espanso, onDismissRequest = ::chiudi) { contenuto(::chiudi) }
    }
}

@Composable
private fun VoceMenuAggiungiParti(tag: String, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(ETICHETTA_AGGIUNGI_PARTI) }, onClick = onClick, modifier = Modifier.testTag(tag))
}

/** AC-625: 'Elimina…' — enabled (danger text) or disabled with its caption as a second line, never
 * both rendered at once; a disabled item's `onClick` is unreachable (Compose `enabled = false`), the
 * presenter's own [RegistrazioniPresenter.elimina] guards the same rule independently. */
@Composable
private fun VoceMenuElimina(stato: StatoEliminazione, onClick: () -> Unit, tag: String) {
    val colori = LocalSnastroColori.current
    val disabilitato = stato is StatoEliminazione.NonDisponibile
    DropdownMenuItem(
        text = {
            Column {
                Text(ETICHETTA_ELIMINA, color = if (disabilitato) colori.inkFaint else colori.danger)
                if (stato is StatoEliminazione.NonDisponibile) {
                    Text(stato.motivo, style = LocalSnastroTipografia.current.caption, color = colori.inkFaint)
                }
            }
        },
        onClick = onClick,
        enabled = !disabilitato,
        modifier = Modifier.testTag(tag),
    )
}

/**
 * AC-626: replaces the row's own content — title = the question, body = one of two variants per
 * [RigaRegistrazione.trascrittoDisponibile] (styled like `anteprime/Dialog.html`, `Pericolo` primary,
 * same pattern as S4's `ConfermaEliminazioneParlante`/S2's own [ConfermaRitrascrivi]) — never an
 * OS-level modal dialog.
 */
@Composable
private fun ConfermaElimina(riga: RigaRegistrazione, azioni: AzioniRegistrazioni) {
    val id = riga.registrazioneId
    val colori = LocalSnastroColori.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SnastroMisure.space4, vertical = SnastroMisure.space3)
            .testTag("registrazioni-conferma-elimina-${id.valore}"),
    ) {
        Text(
            text = riga.parte?.let { titoloConfermaEliminaParte(it.numero, it.titoloIncontro) }
                ?: titoloConfermaElimina(riga.titolo),
            style = LocalSnastroTipografia.current.title,
            color = colori.ink,
        )
        Spacer(modifier = Modifier.height(SnastroMisure.space1))
        Text(
            text = if (riga.trascrittoDisponibile) {
                if (riga.parte != null) {
                    MESSAGGIO_CONFERMA_ELIMINA_PARTE_CON_TRASCRITTO
                } else {
                    MESSAGGIO_CONFERMA_ELIMINA_CON_TRASCRITTO
                }
            } else {
                MESSAGGIO_CONFERMA_ELIMINA_SENZA_TRASCRITTO
            },
            style = LocalSnastroTipografia.current.caption,
            color = colori.inkMuted,
        )
        if (riga.trascrittoDisponibile) {
            Spacer(modifier = Modifier.height(SnastroMisure.space1))
            Text(
                text = MESSAGGIO_CONFERMA_ELIMINA_CON_TRASCRITTO_RESIDUO,
                style = LocalSnastroTipografia.current.caption,
                color = colori.inkMuted,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = SnastroMisure.space2)) {
            BottoneSn(
                etichetta = ETICHETTA_CONFERMA_ELIMINAZIONE,
                onClick = { azioni.confermaElimina(id) },
                variante = VarianteBottone.Pericolo,
                abilitato = !riga.operazioneInCorso,
                modifier = Modifier.testTag("registrazioni-conferma-elimina-conferma-${id.valore}"),
            )
            Spacer(modifier = Modifier.width(SnastroMisure.space2))
            BottoneSn(
                etichetta = ETICHETTA_ANNULLA,
                onClick = { azioni.annullaElimina(id) },
                variante = VarianteBottone.Secondario,
                abilitato = !riga.operazioneInCorso,
                modifier = Modifier.testTag("registrazioni-annulla-elimina-${id.valore}"),
            )
        }
        riga.erroreRiga?.let {
            MessaggioInlineErrore(it, { azioni.chiudiErroreRiga(id) }, "registrazioni-errore-riga-${id.valore}")
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
private fun Separatore() {
    Text(text = "·", style = LocalSnastroTipografia.current.caption, color = LocalSnastroColori.current.inkMuted)
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

/**
 * AC-204/AC-345 (fetta Parlanti): the identification badge — "3 voci · 1 da identificare", or
 * "3 voci" alone once every Voce is identified (AC-345, never "· 0 da identificare"). Absent
 * entirely when [RigaRegistrazione.identificazione] is `null` (the row not yet known/failed —
 * [RegistrazioniPresenter] decides, this only renders what it is given). AC-575: no literal coloured
 * voice dots — [IdentificazioneRiga] carries only counts, no `VoceId`s to colour.
 */
@Composable
private fun BadgeIdentificazione(identificazione: IdentificazioneRiga, id: String) {
    val colori = LocalSnastroColori.current
    Text(
        text = etichettaIdentificazione(identificazione.numVoci, identificazione.numVociDaIdentificare),
        style = LocalSnastroTipografia.current.caption,
        color = if (identificazione.numVociDaIdentificare > 0) colori.accentInk else colori.inkMuted,
        modifier = Modifier.testTag("registrazioni-identificazione-$id"),
    )
}

/**
 * AC-575/rework cycle 1 (composer finding #10): the trailing status content is ONE line (chip, then
 * field, then button), vertically centred — never stacked. The one exception is [ConfermaRitrascrivi]
 * (AC-449), a dialog-like panel that REPLACES this row entirely while open.
 */
@Composable
private fun ColonnaElaborazione(stato: StatoElaborazioneRiga, riga: RigaRegistrazione, azioni: AzioniRegistrazioni) {
    val id = riga.registrazioneId
    if (stato == StatoElaborazioneRiga.Completata && riga.confermaRitrascrivi) {
        ConfermaRitrascrivi(riga, azioni)
        return
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SnastroMisure.space2),
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
            is StatoElaborazioneRiga.Fallita ->
                AvvioConNumeroPersone(
                    riga,
                    ETICHETTA_RIPROVA,
                    Icona.Retry,
                    VarianteBottone.Secondario,
                    azioni.modificaNumeroPersone,
                    azioni.avviaElaborazione,
                )
            StatoElaborazioneRiga.Completata -> ColonnaCompletata(riga, azioni)
        }
        if (riga.operazioneInCorso) {
            CircularProgressIndicator(
                modifier = Modifier.width(DIMENSIONE_INDICATORE_PICCOLO)
                    .testTag("registrazioni-operazione-in-corso-${id.valore}"),
            )
        }
    }
}

/** AC-475/rework cycle 1 (composer finding #8): the plain "In coda" chip already says the position
 * ("In coda · n") — the distinguishing raw caption is shown ONLY for a re-run (`ritrascrizione`),
 * which the chip alone cannot express. */
@Composable
private fun StatoInAttesa(
    stato: StatoElaborazioneRiga.InAttesa,
    riga: RigaRegistrazione,
    azioni: AzioniRegistrazioni,
) {
    val id = riga.registrazioneId
    ChipStato(TipoChipStato.InCoda(stato.posizione))
    if (stato.ritrascrizione) {
        Text(
            text = etichettaRitrascrizioneInAttesa(stato.posizione),
            style = LocalSnastroTipografia.current.caption,
            color = LocalSnastroColori.current.inkMuted,
        )
    }
    // AC-475: 'Annulla' on ANY IN_ATTESA row (plain or re-run) when the source is supplied.
    if (riga.annullabile) {
        BottoneSn(
            etichetta = ETICHETTA_ANNULLA,
            onClick = { azioni.annullaElaborazione(id) },
            variante = VarianteBottone.Link,
            piccolo = true,
            abilitato = !riga.operazioneInCorso,
            modifier = Modifier.testTag("registrazioni-annulla-${id.valore}"),
        )
    }
}

/** Same principle as [StatoInAttesa]: [ChipStato.TipoChipStato.InCorso] already shows the fase + the
 * elapsed time — the raw caption is shown only for a re-run. */
@Composable
private fun StatoInCorso(stato: StatoElaborazioneRiga.InCorso) {
    ChipStato(TipoChipStato.InCorso(stato.faseEtichetta, stato.trascorsoMs))
    if (stato.ritrascrizione) {
        Text(
            text = etichettaRitrascrizioneInCorso(stato.faseEtichetta, stato.trascorsoMs),
            style = LocalSnastroTipografia.current.caption,
            color = LocalSnastroColori.current.inkMuted,
        )
    }
}

/**
 * ADR 0018: the status chip plus, when applicable, AC-451's failed-re-run notice and AC-448/449's
 * 'Ritrascrivi' field/button. AC-575/rework cycle 1 (MED #11): the chip is the `Avviso` "Da
 * identificare" — not `Trascritta` — while the row still has unidentified Voci.
 */
@Composable
private fun ColonnaCompletata(riga: RigaRegistrazione, azioni: AzioniRegistrazioni) {
    val id = riga.registrazioneId
    val daIdentificare = (riga.identificazione?.numVociDaIdentificare ?: 0) > 0
    if (daIdentificare) {
        ChipStato(TipoChipStato.Avviso(ETICHETTA_DA_IDENTIFICARE, Icona.People))
    } else {
        ChipStato(TipoChipStato.Trascritta)
    }
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
            // ADR 0020 §6/AC-625 (b): the More menu is ALWAYS present (pre-release finding #83), so
            // 'Ritrascrivi' ALWAYS moves into it — only the prefilled field stays on the row (the
            // button would otherwise duplicate the menu item).
            mostraBottone = false,
        )
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
            text = riga.testiConfermaRitrascrivi().titolo,
            style = LocalSnastroTipografia.current.title,
            color = colori.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(SnastroMisure.space1))
        Text(
            text = riga.testiConfermaRitrascrivi().messaggio,
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
 * (AC-375/449) are the presenter's, shown as the row's `erroreRiga`. Enter in the field starts the
 * same command as the button (rework cycle 1, MED #12 — `CampoNumeroPersone.onInvio`), even when
 * [mostraBottone] is `false` (ADR 0020 §6: the field stays on the row once 'Ritrascrivi' moves into
 * the More menu, AC-625 (b) — Enter still runs the SAME validate-then-command flow either way).
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
    mostraBottone: Boolean = true,
) {
    val id = riga.registrazioneId
    CampoNumeroPersone(
        valore = riga.numeroPersone,
        onValoreCambiato = { onModifica(id, it) },
        errore = riga.erroreRiga != null,
        abilitato = !riga.operazioneInCorso,
        onInvio = { onAvvia(id) },
        modifier = Modifier.testTag("registrazioni-numero-persone-${id.valore}"),
    )
    if (mostraBottone) {
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

/**
 * AC-199..201/LOW/AC-576: an OS drag-and-drop of one or more files hands every SUCCESSFULLY decoded
 * path to [onFiles] (`snastro.ui.registrazioni` `percorsoDaUriFile`, H1: correct on non-ASCII paths —
 * a malformed `%` escape drops just that one file, never throws inside this AWT callback). Nothing
 * usable in the drop → `false` (rejects the drop, nothing imported). [onDragOverChange] drives the
 * empty `DropZone`'s `over` style — pure view state, set while an OS drag is over the window and
 * cleared the moment it leaves, is dropped, or ends.
 */
@OptIn(ExperimentalComposeUiApi::class)
private fun registrazioneDropTarget(onFiles: (List<String>) -> Unit, onDragOverChange: (Boolean) -> Unit) =
    object : DragAndDropTarget {
        override fun onEntered(event: DragAndDropEvent) {
            onDragOverChange(true)
        }

        override fun onExited(event: DragAndDropEvent) {
            onDragOverChange(false)
        }

        override fun onEnded(event: DragAndDropEvent) {
            onDragOverChange(false)
        }

        override fun onDrop(event: DragAndDropEvent): Boolean {
            onDragOverChange(false)
            val uri = (event.dragData() as? DragData.FilesList)?.readFiles().orEmpty()
            val percorsi = uri.mapNotNull(::percorsoDaUriFile)
            if (percorsi.isEmpty()) return false
            onFiles(percorsi)
            return true
        }
    }

/** 'Importa file audio…': the picked files go to the same [AzioniRegistrazioni.importa] as a drop (1 = single import,
 * 2+ = the AC-I70 dialog); nothing when the user cancelled. */
private fun importaScelti(sceltaFileAudio: SceltaFileAudio, azioni: AzioniRegistrazioni) {
    val percorsi = sceltaFileAudio.scegli()
    if (percorsi.isNotEmpty()) azioni.importa(percorsi)
}
