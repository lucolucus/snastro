package snastro.ui.registrazioni

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import snastro.ui.stile.BottoneSn
import snastro.ui.stile.Icona
import snastro.ui.stile.IconaSn
import snastro.ui.stile.LocalSnastroColori
import snastro.ui.stile.LocalSnastroTipografia
import snastro.ui.stile.SnastroMisure
import snastro.ui.stile.VarianteBottone
import snastro.ui.testi.ETICHETTA_IMPORTA_FILE
import snastro.ui.testi.ETICHETTA_SCEGLI_FILE
import snastro.ui.testi.MESSAGGIO_FORMATI_AUDIO_SUPPORTATI
import snastro.ui.testi.MESSAGGIO_REGISTRAZIONI_VUOTO
import snastro.ui.testi.MESSAGGIO_RILASCIA_PER_IMPORTARE

internal val SPESSORE_TRATTEGGIO_NORMALE = 1.dp
internal val SPESSORE_TRATTEGGIO_OVER = 1.5.dp
private val DIAMETRO_DROPZONE_ICONA = 28.dp
private val ALTEZZA_MINIMA_DROPZONE = 360.dp
private const val TRATTO_LUNGHEZZA = 8f
private const val TRATTO_INTERVALLO = 6f

@Composable
internal fun BarraImportazione(azioni: AzioniRegistrazioni, importoInCorso: Boolean, sceltaFileAudio: SceltaFileAudio) {
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
internal fun DropZoneVuota(
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

/**
 * AC-199..201/LOW/AC-576: an OS drag-and-drop of one or more files hands every SUCCESSFULLY decoded
 * path to [onFiles] (`snastro.ui.registrazioni` `percorsoDaUriFile`, H1: correct on non-ASCII paths —
 * a malformed `%` escape drops just that one file, never throws inside this AWT callback). Nothing
 * usable in the drop → `false` (rejects the drop, nothing imported). [onDragOverChange] drives the
 * empty `DropZone`'s `over` style — pure view state, set while an OS drag is over the window and
 * cleared the moment it leaves, is dropped, or ends.
 */
@OptIn(ExperimentalComposeUiApi::class)
internal fun registrazioneDropTarget(onFiles: (List<String>) -> Unit, onDragOverChange: (Boolean) -> Unit) =
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
