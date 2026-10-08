package snastro.ui.registrazioni

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import snastro.ui.SnastroTema
import snastro.ui.formattaDurataEstesa
import snastro.ui.stile.AzioneBanner
import snastro.ui.stile.BannerSn
import snastro.ui.stile.BottoneSn
import snastro.ui.stile.LocalSnastroColori
import snastro.ui.stile.LocalSnastroTipografia
import snastro.ui.stile.SnastroMisure
import snastro.ui.stile.TipoBanner
import snastro.ui.stile.VarianteBottone
import snastro.ui.temaScuro
import snastro.ui.testi.ETICHETTA_CHIUDI_ERRORE
import snastro.ui.testi.ETICHETTA_IMPORTAZIONE_NON_RIUSCITA
import snastro.ui.testi.ETICHETTA_RIPROVA
import snastro.ui.testi.etichettaRegistrazioni

internal val DIMENSIONE_INDICATORE_PICCOLO = 18.dp

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
