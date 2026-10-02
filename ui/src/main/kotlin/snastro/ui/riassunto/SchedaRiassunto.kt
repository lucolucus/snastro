// TooManyFunctions: the tab split into small single-purpose composables (RC-2 thin view), the same
// rationale as `SchermataPannelloVoci.kt`.
@file:Suppress("TooManyFunctions")

package snastro.ui.riassunto

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import snastro.kernel.VoceId
import snastro.sintesi.applicazione.letture.VoceVista
import snastro.ui.stile.BottoneSn
import snastro.ui.stile.CampoSn
import snastro.ui.stile.ChipStato
import snastro.ui.stile.EmptyState
import snastro.ui.stile.EtichettaVoce
import snastro.ui.stile.FonteChipDati
import snastro.ui.stile.GruppoFonti
import snastro.ui.stile.Icona
import snastro.ui.stile.IconaSn
import snastro.ui.stile.LocalSnastroColori
import snastro.ui.stile.LocalSnastroTipografia
import snastro.ui.stile.SnastroMisure
import snastro.ui.stile.TESTO_NON_PIU_PRESENTE
import snastro.ui.stile.TipoChipStato
import snastro.ui.stile.VarianteBottone
import snastro.ui.testi.AVVISO_SUPERATO
import snastro.ui.testi.CAPTION_SALVATO
import snastro.ui.testi.ETICHETTA_ANNULLA
import snastro.ui.testi.ETICHETTA_ARGOMENTO
import snastro.ui.testi.ETICHETTA_CAMBIA
import snastro.ui.testi.ETICHETTA_COPIA_RIASSUNTO
import snastro.ui.testi.ETICHETTA_IN_CORSO
import snastro.ui.testi.ETICHETTA_RIASSUMI
import snastro.ui.testi.ETICHETTA_RIASSUMI_DI_NUOVO
import snastro.ui.testi.ETICHETTA_RIASSUNTO_COPIATO
import snastro.ui.testi.ETICHETTA_RIPROVA
import snastro.ui.testi.ETICHETTA_SALVA
import snastro.ui.testi.MESSAGGIO_NESSUN_RIASSUNTO
import snastro.ui.testi.NOTA_DURATA_RIASSUNTO
import snastro.ui.testi.PLACEHOLDER_ARGOMENTO
import snastro.ui.testi.PRIVACY_RIASSUNTO
import snastro.ui.testi.testoLunghezzaMassima
import java.awt.datatransfer.StringSelection

private val ALTEZZA_RIGA_SCHELETRO: Dp = 16.dp
private val ALTEZZA_BARRA_PROGRESSO: Dp = 6.dp
private val LARGHEZZA_CAMPO_LUNGHEZZA: Dp = 88.dp
private const val RIGHE_SCHELETRO = 3
private const val LARGHEZZA_SCHELETRO_PARI = 0.9f
private const val LARGHEZZA_SCHELETRO_DISPARI = 0.6f

private const val TAG_RADICE = "riassunto"
private const val TAG_CARICAMENTO = "riassunto-scheletro"
private const val TAG_CONTENUTO = "riassunto-contenuto"
private const val TAG_COPIA = "riassunto-copia"
private const val TAG_BARRA = "riassunto-barra"
private const val TAG_RIASSUMI_DI_NUOVO = "riassunto-riassumi-di-nuovo"
private const val TAG_MODULO = "riassunto-modulo"
private const val TAG_MODULO_ANNULLA = "riassunto-modulo-annulla"
private const val DURATA_COPIATO_MS = 2_000L
private const val TAG_SOMMARIO = "riassunto-sommario"
private const val TAG_OMESSI = "riassunto-omessi"
private const val TAG_METADATI = "riassunto-metadati"
private const val TAG_SUPERATO = "riassunto-superato"
private const val TAG_VOCE_NON_PRESENTE = "riassunto-voce-non-presente"
private const val TAG_INTESTAZIONE = "riassunto-intestazione"
private const val TAG_ARGOMENTO = "riassunto-argomento"
private const val TAG_BOTTONE_RIASSUMI = "riassunto-bottone-principale"
private const val TAG_SCARICA_MODELLO = "riassunto-scarica-modello"
private const val TAG_DOWNLOAD_PROGRESSO = "riassunto-download-progresso"
private const val TAG_DOWNLOAD_FALLITO = "riassunto-download-fallito"
private const val TAG_IN_CODA = "riassunto-in-coda"
private const val TAG_IN_CORSO = "riassunto-in-corso"
private const val TAG_FALLITO = "riassunto-fallito"
private const val TAG_NON_DISPONIBILE = "riassunto-non-disponibile"
private const val TAG_LUNGHEZZA_MASSIMA = "riassunto-lunghezza-massima"
private const val TAG_LUNGHEZZA_MASSIMA_CAMBIA = "riassunto-lunghezza-massima-cambia"
private const val TAG_LUNGHEZZA_MASSIMA_CAMPO = "riassunto-lunghezza-massima-campo"
private const val TAG_LUNGHEZZA_MASSIMA_SALVA = "riassunto-lunghezza-massima-salva"
private const val TAG_LUNGHEZZA_MASSIMA_ANNULLA = "riassunto-lunghezza-massima-annulla"
private const val TAG_LUNGHEZZA_MASSIMA_SALVATO = "riassunto-lunghezza-massima-salvato"
private const val TAG_PRIVACY = "riassunto-privacy"
private const val TAG_MESSAGGIO_ERRORE = "riassunto-messaggio-errore"

/**
 * Thin view of S3's Riassunto tab (RC-2): only renders [stato] and forwards [azioni]'s events — no
 * decision the presenter did not already make. Layout (dev-architecture `#presenter`,
 * `:ui:renderCheck` per-state, AC-S140), a single, ALWAYS scrollable [Column] with a bounded width
 * (the S3 host already gives this composable a weighted, width-capped container), never a
 * `LazyRow`/`horizontalScroll` ancestor of [GruppoFonti]/[FonteChip] (lesson `FonteChip.kt:54`,
 * pre-release finding #113: `weight(1f, fill = false)` needs a bounded parent):
 * - **no shown Riassunto**: the ONE action area ([RiassuntoUiStato.Dati.areaAzione]) is the tab's body.
 * - **a shown Riassunto** (2026-09-30, user: the bottom "Riassumi di nuovo" could not be found under a long
 *   Riassunto): a top bar ([BarraRiassunto]: metadata, "Copia", "Riassumi di nuovo") comes FIRST, then the
 *   action area — for [AreaAzione.Azionabile] only the form the bar opened ([RiassuntoUiStato.Dati.moduloAperto]),
 *   otherwise the model/queue/failure state — and the Riassunto BELOW it. No action ever sits past the content.
 *
 * The privacy line is the tab's own last line, always present once loaded (AC-S139), with the
 * [RiassuntoUiStato.Dati.messaggioErrore] inline notice (a race's `ErroreSintesi`, e.g.
 * `RiassuntoGiaAperto`) right above it — decoupled from [areaAzione] because a race can land on a
 * DIFFERENT area variant than the one the user clicked from.
 */
@Composable
fun SchedaRiassunto(stato: RiassuntoUiStato, azioni: AzioniRiassunto, modifier: Modifier = Modifier) {
    when (stato) {
        RiassuntoUiStato.Caricamento -> Scheletro(modifier)
        is RiassuntoUiStato.Dati -> ContenutoTab(stato, azioni, modifier)
    }
}

/** AC-S136 (state 12): a skeleton shaped like the tab-to-come, not a blank screen. */
@Composable
private fun Scheletro(modifier: Modifier) {
    val colori = LocalSnastroColori.current
    Column(modifier = modifier.fillMaxWidth().padding(SnastroMisure.space4).testTag(TAG_CARICAMENTO)) {
        repeat(RIGHE_SCHELETRO) { indice ->
            Surface(
                color = colori.sunken,
                shape = RoundedCornerShape(SnastroMisure.radiusControl),
                modifier = Modifier
                    .fillMaxWidth(if (indice % 2 == 0) LARGHEZZA_SCHELETRO_PARI else LARGHEZZA_SCHELETRO_DISPARI)
                    .height(ALTEZZA_RIGA_SCHELETRO)
                    .padding(vertical = 4.dp),
            ) {}
        }
    }
}

@Composable
private fun ContenutoTab(stato: RiassuntoUiStato.Dati, azioni: AzioniRiassunto, modifier: Modifier) {
    val area = stato.areaAzione
    val contenuto = stato.contenuto
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(SnastroMisure.space4)
            .testTag(TAG_RADICE),
        verticalArrangement = Arrangement.spacedBy(SnastroMisure.space4),
    ) {
        // AC-S139 (pre-release finding #155, rework, MED): [messaggioErrore] sits right after
        // WHICHEVER area copy actually rendered — always above the content, never below the fold.
        if (contenuto == null) {
            AreaAzioneVista(area, stato, azioni)
        } else {
            BarraRiassunto(contenuto, riassumiDiNuovo = area is AreaAzione.Azionabile && !stato.moduloAperto, azioni)
            when {
                area !is AreaAzione.Azionabile -> AreaAzioneVista(area, stato, azioni)
                stato.moduloAperto -> ModuloRiassumiDiNuovo(stato, azioni)
            }
        }
        MessaggioErroreVista(stato.messaggioErrore)
        contenuto?.let {
            stato.intestazioneTesto?.let { testo -> IntestazioneIncontro(testo) }
            if (it.superato) AvvisoSuperato()
            SelectionContainer { TestoContenuto(it, azioni) }
        }
        Text(
            text = PRIVACY_RIASSUNTO,
            style = LocalSnastroTipografia.current.caption,
            color = LocalSnastroColori.current.inkMuted,
            modifier = Modifier.testTag(TAG_PRIVACY),
        )
    }
}

@Composable
private fun MessaggioErroreVista(messaggio: String?) {
    messaggio?.let {
        MessaggioTonale(it, LocalSnastroColori.current.danger, Icona.Alert, Modifier.testTag(TAG_MESSAGGIO_ERRORE))
    }
}

/** Multi-part only: "Riassunto dell'incontro · N parti", above the shown Riassunto's text. */
@Composable
private fun IntestazioneIncontro(testo: String) {
    Text(
        text = testo,
        style = LocalSnastroTipografia.current.heading,
        color = LocalSnastroColori.current.ink,
        modifier = Modifier.testTag(TAG_INTESTAZIONE),
    )
}

/** AC-S133: a warning notice INSIDE the tab — never [snastro.ui.stile.BannerSn] (ux: "not a screen Banner"). */
@Composable
private fun AvvisoSuperato() {
    val colori = LocalSnastroColori.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SnastroMisure.space2),
        modifier = Modifier.testTag(TAG_SUPERATO),
    ) {
        IconaSn(Icona.Retry, descrizione = null, tinta = colori.warning, dimensione = SnastroMisure.iconS)
        Text(text = AVVISO_SUPERATO, style = LocalSnastroTipografia.current.body, color = colori.warning)
    }
}

/**
 * The shown Riassunto's top bar: its metadata on the left, the actions on the right — "Copia" (the whole
 * Riassunto as text, [testoRiassuntoDaCopiare]) and, when a new request can start, "Riassumi di nuovo",
 * which opens the form ([AzioniRiassunto.apriModulo]) instead of hiding it under the content.
 */
@Composable
private fun BarraRiassunto(contenuto: ContenutoUi, riassumiDiNuovo: Boolean, azioni: AzioniRiassunto) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SnastroMisure.space2),
        modifier = Modifier.fillMaxWidth().testTag(TAG_BARRA),
    ) {
        Text(
            text = contenuto.metadatiTesto,
            style = LocalSnastroTipografia.current.caption,
            color = LocalSnastroColori.current.inkMuted,
            modifier = Modifier.weight(1f).testTag(TAG_METADATI),
        )
        BottoneCopia(contenuto)
        if (riassumiDiNuovo) {
            BottoneSn(
                etichetta = ETICHETTA_RIASSUMI_DI_NUOVO,
                onClick = azioni.apriModulo,
                variante = VarianteBottone.Primario,
                piccolo = true,
                icona = Icona.Retry,
                modifier = Modifier.testTag(TAG_RIASSUMI_DI_NUOVO),
            )
        }
    }
}

/** The form "Riassumi di nuovo" opened: Argomento + lunghezza massima + "Annulla" / "Riassumi". */
@Composable
private fun ModuloRiassumiDiNuovo(stato: RiassuntoUiStato.Dati, azioni: AzioniRiassunto) {
    Surface(
        color = LocalSnastroColori.current.raised,
        shape = RoundedCornerShape(SnastroMisure.radiusControl),
        modifier = Modifier.fillMaxWidth().testTag(TAG_MODULO),
    ) {
        Column(modifier = Modifier.padding(SnastroMisure.space4)) {
            FormRiassumi(stato, azioni, ETICHETTA_RIASSUMI, annulla = azioni.chiudiModulo)
        }
    }
}

/** Puts the Riassunto on the system clipboard; the label reads "Copiato" for a moment after. */
@Composable
private fun BottoneCopia(contenuto: ContenutoUi) {
    val appunti = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copiato by remember(contenuto) { mutableStateOf(false) }
    LaunchedEffect(copiato) {
        if (copiato) {
            delay(DURATA_COPIATO_MS)
            copiato = false
        }
    }
    BottoneSn(
        etichetta = if (copiato) ETICHETTA_RIASSUNTO_COPIATO else ETICHETTA_COPIA_RIASSUNTO,
        onClick = {
            scope.launch {
                appunti.setClipEntry(ClipEntry(StringSelection(testoRiassuntoDaCopiare(contenuto))))
                copiato = true
            }
        },
        variante = VarianteBottone.Secondario,
        piccolo = true,
        icona = if (copiato) Icona.Check else Icona.Copy,
        modifier = Modifier.testTag(TAG_COPIA),
    )
}

@Composable
private fun TestoContenuto(contenuto: ContenutoUi, azioni: AzioniRiassunto) {
    Column(
        verticalArrangement = Arrangement.spacedBy(SnastroMisure.space4),
        modifier = Modifier.testTag(TAG_CONTENUTO),
    ) {
        val colori = LocalSnastroColori.current
        val tipografia = LocalSnastroTipografia.current
        contenuto.sommario?.let {
            Text(
                text = it,
                style = tipografia.abstract,
                color = colori.ink,
                // 2026-09-30, user: the 640dp prose cap left the Sommario on the left of a wide tab — it fills it now.
                modifier = Modifier.testTag(TAG_SOMMARIO),
            )
        }
        if (contenuto.decisioni.isNotEmpty()) {
            Sezione("Decisioni") { contenuto.decisioni.forEach { ElementoRiga(it, azioni) } }
        }
        if (contenuto.azioni.isNotEmpty()) {
            Sezione("Azioni") { contenuto.azioni.forEach { AzioneRiga(it, azioni) } }
        }
        if (contenuto.questioniAperte.isNotEmpty()) {
            Sezione("Questioni aperte") { contenuto.questioniAperte.forEach { ElementoRiga(it, azioni) } }
        }
        if (contenuto.puntiChiave.isNotEmpty()) {
            Sezione("Punti chiave") { contenuto.puntiChiave.forEach { PuntoChiaveRiga(it, azioni) } }
        }
        contenuto.omessiTesto?.let {
            Text(it, style = tipografia.caption, color = colori.inkMuted, modifier = Modifier.testTag(TAG_OMESSI))
        }
    }
}

@Composable
private fun Sezione(titolo: String, righe: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SnastroMisure.space2)) {
        Text(titolo, style = LocalSnastroTipografia.current.overline, color = LocalSnastroColori.current.inkMuted)
        righe()
    }
}

@Composable
private fun ElementoRiga(elemento: ElementoUi, azioni: AzioniRiassunto) {
    Column(verticalArrangement = Arrangement.spacedBy(SnastroMisure.space1)) {
        Text(
            text = "• ${elemento.testo}",
            style = LocalSnastroTipografia.current.body,
            color = LocalSnastroColori.current.ink,
        )
        if (elemento.fonti.isNotEmpty()) FontiRiga(elemento.fonti, azioni)
    }
}

/** AC-S132: "→ " + Responsabile (voice dot + Nome) when bound, nothing otherwise. */
@Composable
private fun AzioneRiga(azione: AzioneUi, azioni: AzioniRiassunto) {
    Column(verticalArrangement = Arrangement.spacedBy(SnastroMisure.space1)) {
        Text("• ${azione.testo}", style = LocalSnastroTipografia.current.body, color = LocalSnastroColori.current.ink)
        azione.responsabile?.let { responsabile ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SnastroMisure.space1),
            ) {
                Text("→", style = LocalSnastroTipografia.current.body, color = LocalSnastroColori.current.inkMuted)
                Voce(responsabile)
            }
        }
        if (azione.fonti.isNotEmpty()) FontiRiga(azione.fonti, azioni)
    }
}

/** AC-S132: speaker (dot + Nome) BEFORE the text, when bound. */
@Composable
private fun PuntoChiaveRiga(punto: PuntoChiaveUi, azioni: AzioniRiassunto) {
    Column(verticalArrangement = Arrangement.spacedBy(SnastroMisure.space1)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SnastroMisure.space2),
        ) {
            punto.parlante?.let { Voce(it) }
            Text(
                text = punto.testo,
                style = LocalSnastroTipografia.current.body,
                color = LocalSnastroColori.current.ink,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        if (punto.fonti.isNotEmpty()) FontiRiga(punto.fonti, azioni)
    }
}

/** AC-I81: a clickable chip hands its Parte and minute to the presenter. */
@Composable
private fun FontiRiga(fonti: List<FonteChipDati>, azioni: AzioniRiassunto) {
    GruppoFonti(fonti, onFonte = { f -> f.registrazioneId?.let { r -> f.inizioMs?.let { azioni.apriFonte(r, it) } } })
}

/** INV-I13: a Voce no longer present is muted text with no dot and no Nome. */
@Composable
private fun Voce(voce: VoceVista) {
    if (voce.presente) {
        EtichettaVoce(voceId = VoceId(voce.voceId), nome = voce.nome)
    } else {
        Text(
            text = "${voce.etichetta} · $TESTO_NON_PIU_PRESENTE",
            style = LocalSnastroTipografia.current.label,
            color = LocalSnastroColori.current.inkMuted,
            modifier = Modifier.testTag(TAG_VOCE_NON_PRESENTE),
        )
    }
}

@Composable
private fun AreaAzioneVista(area: AreaAzione, stato: RiassuntoUiStato.Dati, azioni: AzioniRiassunto) {
    when (area) {
        is AreaAzione.ScaricaModello -> AreaScaricaModello(area, azioni)
        is AreaAzione.Scaricando -> AreaScaricando(area)
        is AreaAzione.DownloadFallito -> AreaDownloadFallito(area, azioni)
        is AreaAzione.InCoda -> AreaInCoda(area)
        is AreaAzione.InCorso -> AreaInCorso(area)
        is AreaAzione.Fallito -> AreaFallito(area, stato, azioni)
        is AreaAzione.NonDisponibile -> AreaNonDisponibile(area)
        is AreaAzione.Azionabile -> AreaAzionabile(area, stato, azioni)
    }
}

/** AC-S125: state 1 — [EmptyState] (pre-release finding #152), no Argomento field, no Riassumi
 * (Q-S1 = download only). */
@Composable
private fun AreaScaricaModello(area: AreaAzione.ScaricaModello, azioni: AzioniRiassunto) {
    EmptyState(
        messaggio = area.messaggio,
        azione = {
            BottoneSn(
                etichetta = area.etichettaBottone,
                onClick = azioni.scaricaModello,
                variante = VarianteBottone.Primario,
                modifier = Modifier.testTag(TAG_SCARICA_MODELLO),
            )
        },
    )
}

/** AC-S126: state 2 — a determinate progress bar, nothing else actionable. */
@Composable
private fun AreaScaricando(area: AreaAzione.Scaricando) {
    val colori = LocalSnastroColori.current
    Column(modifier = Modifier.fillMaxWidth().testTag(TAG_DOWNLOAD_PROGRESSO)) {
        Text(area.testo, style = LocalSnastroTipografia.current.body, color = colori.ink)
        Spacer(Modifier.height(SnastroMisure.space2))
        BarraProgresso(area.avanzamento)
    }
}

@Composable
private fun BarraProgresso(avanzamento: Float) {
    val colori = LocalSnastroColori.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ALTEZZA_BARRA_PROGRESSO)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(avanzamento, 0f..1f) }
            .background(colori.sunken, RoundedCornerShape(ALTEZZA_BARRA_PROGRESSO / 2)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(avanzamento)
                .height(ALTEZZA_BARRA_PROGRESSO)
                .background(colori.accent, RoundedCornerShape(ALTEZZA_BARRA_PROGRESSO / 2)),
        )
    }
}

/** AC-S127: state 3 — danger text + Riprova. */
@Composable
private fun AreaDownloadFallito(area: AreaAzione.DownloadFallito, azioni: AzioniRiassunto) {
    val colori = LocalSnastroColori.current
    Column(
        verticalArrangement = Arrangement.spacedBy(SnastroMisure.space3),
        modifier = Modifier.testTag(TAG_DOWNLOAD_FALLITO),
    ) {
        MessaggioTonale(area.messaggio, colori.danger, Icona.Alert)
        BottoneSn(ETICHETTA_RIPROVA, onClick = azioni.scaricaModello, variante = VarianteBottone.Secondario)
    }
}

/** AC-S130: state 6 — a real [StatusChip][ChipStato] (pre-release finding #152: was plain text),
 * Argomento/Riassumi hidden, no Annulla. */
@Composable
private fun AreaInCoda(area: AreaAzione.InCoda) {
    ChipStato(TipoChipStato.InCoda(area.posizione), modifier = Modifier.testTag(TAG_IN_CODA))
}

/** AC-S131: state 7 — the SAME [ChipStato]/[TipoChipStato.InCorso] S2 already shows (AC-S46: no new
 * type), so its pulsing dot comes for free (pre-release finding #152: was plain text, no dot) +
 * the "circa 3 minuti" note. */
@Composable
private fun AreaInCorso(area: AreaAzione.InCorso) {
    val colori = LocalSnastroColori.current
    Column(modifier = Modifier.testTag(TAG_IN_CORSO)) {
        ChipStato(TipoChipStato.InCorso(ETICHETTA_IN_CORSO, area.trascorsoMs))
        Text(NOTA_DURATA_RIASSUNTO, style = LocalSnastroTipografia.current.caption, color = colori.inkMuted)
    }
}

/** AC-S134: state 10 — danger message + Argomento (prefilled) + "Riprova"; previous Riassunto stays above. */
@Composable
private fun AreaFallito(area: AreaAzione.Fallito, stato: RiassuntoUiStato.Dati, azioni: AzioniRiassunto) {
    Column(verticalArrangement = Arrangement.spacedBy(SnastroMisure.space3), modifier = Modifier.testTag(TAG_FALLITO)) {
        MessaggioTonale(area.messaggio, LocalSnastroColori.current.danger, Icona.Alert)
        FormRiassumi(stato, azioni, ETICHETTA_RIPROVA)
    }
}

/** AC-S129: state 5 — the button disabled, the reason as caption; no Argomento field. */
@Composable
private fun AreaNonDisponibile(area: AreaAzione.NonDisponibile) {
    val colori = LocalSnastroColori.current
    Column(
        verticalArrangement = Arrangement.spacedBy(SnastroMisure.space2),
        modifier = Modifier.testTag(TAG_NON_DISPONIBILE),
    ) {
        BottoneSn(ETICHETTA_RIASSUMI, onClick = {}, variante = VarianteBottone.Primario, abilitato = false)
        Text(area.messaggio, style = LocalSnastroTipografia.current.caption, color = colori.inkMuted)
    }
}

/** AC-S128/S132/S133/S137/S138: states 4/8/9 — Argomento + lunghezza massima + Riassumi/di nuovo.
 * State 4 ([!area.nuovo]) leads with [EmptyState] (pre-release finding #152, ux row 4). */
@Composable
private fun AreaAzionabile(area: AreaAzione.Azionabile, stato: RiassuntoUiStato.Dati, azioni: AzioniRiassunto) {
    Column(verticalArrangement = Arrangement.spacedBy(SnastroMisure.space3)) {
        if (!area.nuovo) EmptyState(messaggio = MESSAGGIO_NESSUN_RIASSUNTO)
        FormRiassumi(stato, azioni, ETICHETTA_RIASSUMI)
    }
}

@Composable
private fun FormRiassumi(
    stato: RiassuntoUiStato.Dati,
    azioni: AzioniRiassunto,
    etichettaBottone: String,
    annulla: (() -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SnastroMisure.space3)) {
        CampoSn(
            valore = stato.argomento.valore,
            onValoreCambiato = azioni.cambiaArgomento,
            etichetta = ETICHETTA_ARGOMENTO,
            placeholder = PLACEHOLDER_ARGOMENTO,
            aiuto = stato.argomento.contatore,
            errore = stato.argomento.errore,
            modifier = Modifier.testTag(TAG_ARGOMENTO),
        )
        LunghezzaMassimaVista(stato.lunghezzaMassima, azioni)
        Row(horizontalArrangement = Arrangement.spacedBy(SnastroMisure.space2)) {
            BottoneSn(
                etichetta = etichettaBottone,
                onClick = azioni.riassumi,
                variante = VarianteBottone.Primario,
                abilitato = stato.argomento.errore == null,
                modifier = Modifier.testTag(TAG_BOTTONE_RIASSUMI),
            )
            annulla?.let {
                BottoneSn(
                    ETICHETTA_ANNULLA,
                    onClick = it,
                    variante = VarianteBottone.Secondario,
                    modifier = Modifier.testTag(TAG_MODULO_ANNULLA),
                )
            }
        }
    }
}

@Composable
private fun LunghezzaMassimaVista(stato: LunghezzaMassimaUiStato, azioni: AzioniRiassunto) {
    when (stato) {
        is LunghezzaMassimaUiStato.Testo -> LunghezzaMassimaTesto(stato, azioni)
        is LunghezzaMassimaUiStato.Modifica -> LunghezzaMassimaModifica(stato, azioni)
        is LunghezzaMassimaUiStato.Salvato -> LunghezzaMassimaSalvato(stato)
    }
}

@Composable
private fun LunghezzaMassimaTesto(stato: LunghezzaMassimaUiStato.Testo, azioni: AzioniRiassunto) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SnastroMisure.space2),
    ) {
        Text(
            testoLunghezzaMassima(stato.parole),
            style = LocalSnastroTipografia.current.caption,
            color = LocalSnastroColori.current.inkMuted,
            modifier = Modifier.testTag(TAG_LUNGHEZZA_MASSIMA),
        )
        BottoneSn(
            ETICHETTA_CAMBIA,
            onClick = azioni.modificaLunghezzaMassima,
            variante = VarianteBottone.Link,
            modifier = Modifier.testTag(TAG_LUNGHEZZA_MASSIMA_CAMBIA),
        )
    }
}

@Composable
private fun LunghezzaMassimaModifica(stato: LunghezzaMassimaUiStato.Modifica, azioni: AzioniRiassunto) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SnastroMisure.space2),
    ) {
        CampoSn(
            valore = stato.valore,
            onValoreCambiato = azioni.cambiaLunghezzaMassima,
            errore = stato.errore,
            piccolo = true,
            modifier = Modifier.width(LARGHEZZA_CAMPO_LUNGHEZZA).testTag(TAG_LUNGHEZZA_MASSIMA_CAMPO),
        )
        BottoneSn(
            ETICHETTA_SALVA,
            onClick = azioni.salvaLunghezzaMassima,
            variante = VarianteBottone.Secondario,
            piccolo = true,
            modifier = Modifier.testTag(TAG_LUNGHEZZA_MASSIMA_SALVA),
        )
        BottoneSn(
            ETICHETTA_ANNULLA,
            onClick = azioni.annullaLunghezzaMassima,
            variante = VarianteBottone.Fantasma,
            piccolo = true,
            modifier = Modifier.testTag(TAG_LUNGHEZZA_MASSIMA_ANNULLA),
        )
    }
}

@Composable
private fun LunghezzaMassimaSalvato(stato: LunghezzaMassimaUiStato.Salvato) {
    val colori = LocalSnastroColori.current
    val tipografia = LocalSnastroTipografia.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SnastroMisure.space2),
    ) {
        Text(testoLunghezzaMassima(stato.parole), style = tipografia.caption, color = colori.inkMuted)
        Text(
            CAPTION_SALVATO,
            style = tipografia.caption,
            color = colori.accentInk,
            modifier = Modifier.testTag(TAG_LUNGHEZZA_MASSIMA_SALVATO),
        )
    }
}

@Composable
private fun MessaggioTonale(testo: String, colore: Color, icona: Icona, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SnastroMisure.space2),
        modifier = modifier,
    ) {
        IconaSn(icona, descrizione = null, tinta = colore, dimensione = SnastroMisure.iconS)
        Text(testo, style = LocalSnastroTipografia.current.body, color = colore)
    }
}
