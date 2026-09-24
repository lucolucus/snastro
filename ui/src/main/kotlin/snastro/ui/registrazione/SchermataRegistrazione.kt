// TooManyFunctions: one screen split into many small, single-purpose composables (RC-2 thin view) —
// header, skeleton, empty/error states and the per-Segmento row each deserve their own function.
@file:Suppress("TooManyFunctions")

package snastro.ui.registrazione

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import snastro.kernel.SegmentoId
import snastro.parlanti.applicazione.eventi.TipoParlanteVista
import snastro.ui.SnastroTema
import snastro.ui.formattaData
import snastro.ui.formattaDurata
import snastro.ui.formattaDurataEstesa
import snastro.ui.lettore.BarraLettore
import snastro.ui.lettore.CorsiaVoce
import snastro.ui.stile.BottoneIconaSn
import snastro.ui.stile.BottoneSn
import snastro.ui.stile.Icona
import snastro.ui.stile.IconaSn
import snastro.ui.stile.LocalSnastroColori
import snastro.ui.stile.LocalSnastroTipografia
import snastro.ui.stile.PallinoVoce
import snastro.ui.stile.SnastroMisure
import snastro.ui.stile.VarianteBottone
import snastro.ui.testi.ETICHETTA_ANNULLA
import snastro.ui.testi.ETICHETTA_APRI_DOCUMENTO
import snastro.ui.testi.ETICHETTA_BRICIOLA_REGISTRAZIONI
import snastro.ui.testi.ETICHETTA_DESELEZIONA
import snastro.ui.testi.ETICHETTA_DIVIDI_VOCE
import snastro.ui.testi.ETICHETTA_MOSTRA_CARTELLA
import snastro.ui.testi.ETICHETTA_NOMINA_FRASE
import snastro.ui.testi.ETICHETTA_NUOVA_VOCE
import snastro.ui.testi.ETICHETTA_RIASSEGNA_A
import snastro.ui.testi.ETICHETTA_RIPROVA
import snastro.ui.testi.ETICHETTA_RITRASCRIVI
import snastro.ui.testi.ETICHETTA_TOGLI_CONFERMA
import snastro.ui.testi.MESSAGGIO_COMANDO_IN_ATTESA
import snastro.ui.testi.MESSAGGIO_TRASCRITTO_VUOTO
import snastro.ui.testi.TOOLTIP_FRASE_CONFERMATA
import snastro.ui.testi.testoPersone
import snastro.ui.testi.testoSelezione

private val LARGHEZZA_MASSIMA_TRASCRITTO = 712.dp
private val LARGHEZZA_SOGLIA_IMPILAMENTO = 1_100.dp
private val LARGHEZZA_GUTTER = 56.dp
private val DIMENSIONE_INDICATORE = 14.dp
private val DIMENSIONE_CASELLA = 16.dp
private val PADDING_RIGA_V = 10.dp
private val ALTEZZA_RIGA_SCHELETRO = 16.dp
private const val RIGHE_SCHELETRO = 4
private const val LARGHEZZA_SCHELETRO_PARI = 0.8f
private const val LARGHEZZA_SCHELETRO_DISPARI = 0.55f

/**
 * Thin view of S3 · Registrazione (RC-2): only renders [stato] and forwards [azioni]'s events. R1 is
 * read-only (AC-402): with [RegistrazioneUiStato.Dati.pannello] `null` there is no Voci panel, no
 * selection, no Revisione UI. R2 (`schermata-registrazione-identificazione`) adds them on the right
 * ([PannelloVociVista]) and above the transcript ([BarraSelezioneVista]).
 *
 * [scuro]/[riduciMovimento] mirror [SnastroTema]'s own optional overrides (same defaults, same
 * call-site-unchanged guarantee for [RegistrazioneRoute]) — AC-589's render-check pins both per
 * fixture (light/dark, reduce-motion) the same way `StileRenderCheckTest` already does per component.
 */
@Composable
fun SchermataRegistrazione(
    stato: RegistrazioneUiStato,
    azioni: AzioniRegistrazione,
    scuro: Boolean = isSystemInDarkTheme(),
    riduciMovimento: Boolean? = null,
) {
    SnastroTema(scuro = scuro, riduciMovimento = riduciMovimento) {
        Surface(color = LocalSnastroColori.current.surface, modifier = Modifier.fillMaxSize()) {
            when (stato) {
                RegistrazioneUiStato.Caricamento -> ScheletroRegistrazione()
                is RegistrazioneUiStato.Dati -> ContenutoRegistrazione(stato, azioni)
                is RegistrazioneUiStato.Errore -> ErroreCaricamentoRegistrazione(stato.messaggio, azioni.riprova)
            }
        }
    }
}

/** AC-207: a skeleton shaped like the transcript-to-come, not a blank screen. */
@Composable
private fun ScheletroRegistrazione() {
    val colori = LocalSnastroColori.current
    Column(
        modifier = Modifier.fillMaxSize().padding(SnastroMisure.space5).testTag("registrazione-scheletro"),
    ) {
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
private fun ErroreCaricamentoRegistrazione(messaggio: String, onRiprova: () -> Unit) {
    val colori = LocalSnastroColori.current
    Column(
        modifier = Modifier.fillMaxSize().padding(SnastroMisure.space5).testTag("registrazione-errore-caricamento"),
    ) {
        Text(text = messaggio, color = colori.danger, style = LocalSnastroTipografia.current.body)
        Spacer(modifier = Modifier.height(SnastroMisure.space4))
        BottoneSn(ETICHETTA_RIPROVA, onClick = onRiprova, modifier = Modifier.testTag("registrazione-riprova"))
    }
}

@Composable
private fun ContenutoRegistrazione(stato: RegistrazioneUiStato.Dati, azioni: AzioniRegistrazione) {
    Column(modifier = Modifier.fillMaxSize().padding(SnastroMisure.space5)) {
        IntestazioneRegistrazione(stato, azioni)
        Spacer(modifier = Modifier.height(SnastroMisure.space4))
        BarraLettore(
            stato = stato.barra,
            onRiproduci = azioni.riproduciDaInizio,
            onPausa = azioni.pausa,
            durataMs = stato.durataMs,
            corsie = stato.segmenti.map { CorsiaVoce(it.voceId, it.inizioMs, it.fineMs) },
        )
        stato.bannerRitrascrizione?.let {
            Spacer(modifier = Modifier.height(SnastroMisure.space3))
            BannerRitrascrizione(it, stato.bannerRitrascrizionePannello)
        }
        stato.errore?.let {
            Spacer(modifier = Modifier.height(SnastroMisure.space3))
            MessaggioInlineErrore(it, azioni.chiudiErrore)
        }
        Spacer(modifier = Modifier.height(SnastroMisure.space4))
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // AC-581: below the threshold the panel stacks under the transcript (render-check at 1024x640).
            if (maxWidth < LARGHEZZA_SOGLIA_IMPILAMENTO) {
                // Both children must carry a `weight` here: an unweighted panel would be measured
                // BEFORE the weighted transcript and — since its own `voci-lista` is itself a
                // `weight(1f)` LazyColumn that greedily fills whatever ceiling it is offered — it would
                // claim the Column's entire height first, leaving nothing for the transcript (a real
                // regression caught only by running the render-check, not by "it compiles").
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(SnastroMisure.space4),
                ) {
                    ColonnaTrascritto(stato, azioni, Modifier.weight(1f).fillMaxWidth())
                    stato.pannello?.let { PannelloVociVista(it, azioni, Modifier.weight(1f).fillMaxWidth()) }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(SnastroMisure.space5),
                ) {
                    ColonnaTrascritto(
                        stato,
                        azioni,
                        Modifier.weight(1f).widthIn(max = LARGHEZZA_MASSIMA_TRASCRITTO).fillMaxHeight(),
                    )
                    stato.pannello?.let {
                        PannelloVociVista(it, azioni, Modifier.width(SnastroMisure.pannello).fillMaxHeight())
                    }
                }
            }
        }
    }
}

@Composable
private fun ColonnaTrascritto(stato: RegistrazioneUiStato.Dati, azioni: AzioniRegistrazione, modifier: Modifier) {
    Column(modifier = modifier) {
        stato.barraSelezione?.let { BarraSelezioneVista(it, azioni) }
        if (stato.segmenti.isEmpty()) {
            Text(
                text = MESSAGGIO_TRASCRITTO_VUOTO,
                style = LocalSnastroTipografia.current.body,
                color = LocalSnastroColori.current.inkMuted,
                modifier = Modifier.testTag("registrazione-vuoto"),
            )
        } else {
            ElencoSegmenti(stato, azioni)
        }
    }
}

/** AC-209..211: the selection toolbar — 'Riassegna a' (other Voci + 'nuova voce') and 'Dividi voce'
 * (disabled with its explanation on the whole Voce, INV-10); ADR 0019 §6: with ONE Segmento selected,
 * 'Dai un nome a questa frase' (attivo Parlanti, then 'nuovo…') and 'Togli conferma' on a confirmed one.
 * Every decision is the presenter's; the actions wrap (FlowRow) so nothing clips at 1024x640. */
// LongMethod: one Compose toolbar with every AC-209..211/ADR 0019 §6 branch inline (RC-2 thin view —
// splitting further would scatter one cohesive layout across unrelated small functions).
@Suppress("LongMethod")
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BarraSelezioneVista(barra: BarraSelezione, azioni: AzioniRegistrazione) {
    val colori = LocalSnastroColori.current
    var nuovoAperto by remember(barra.frase?.segmentoId) { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(SnastroMisure.radiusCard),
        color = colori.raised,
        border = BorderStroke(1.dp, colori.line),
        shadowElevation = SnastroMisure.space1,
        modifier = Modifier.fillMaxWidth().padding(bottom = SnastroMisure.space3)
            .testTag("registrazione-barra-selezione"),
    ) {
        Column(modifier = Modifier.padding(SnastroMisure.space3)) {
            FlowRow(
                verticalArrangement = Arrangement.Center,
                horizontalArrangement = Arrangement.spacedBy(SnastroMisure.space2),
            ) {
                Text(
                    text = testoSelezione(barra.numeroSegmenti, barra.etichetta),
                    style = LocalSnastroTipografia.current.label,
                    color = colori.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                barra.frase?.let { AzioniFrase(it, azioni) { nuovoAperto = !nuovoAperto } }
                MenuVoci(
                    "registrazione-riassegna",
                    ETICHETTA_RIASSEGNA_A,
                    barra.destinazioni,
                    barra.abilitata,
                    icona = Icona.Reassign,
                    extra = ETICHETTA_NUOVA_VOCE,
                    onScelta = azioni.riassegnaA,
                )
                BottoneSn(
                    ETICHETTA_DIVIDI_VOCE,
                    onClick = azioni.dividiVoce,
                    abilitato = barra.abilitata && barra.dividiAbilitato,
                    variante = VarianteBottone.Secondario,
                    piccolo = true,
                    icona = Icona.Split,
                    modifier = Modifier.testTag("registrazione-dividi"),
                )
                BottoneIconaSn(
                    Icona.Close,
                    ETICHETTA_DESELEZIONA,
                    onClick = azioni.deseleziona,
                    piccolo = true,
                )
            }
            if (nuovoAperto && barra.frase?.abilitata == true) {
                ModuloNuovo("registrazione-frase") { nome, tipo ->
                    nuovoAperto = false
                    azioni.nominaFrase(ObiettivoNome.Nuovo(nome, ricorrente = tipo == TipoParlanteVista.RICORRENTE))
                }
            }
            barra.spiegazioneDividi?.let {
                Text(
                    text = it,
                    style = LocalSnastroTipografia.current.caption,
                    color = colori.inkMuted,
                    modifier = Modifier.testTag("registrazione-dividi-spiegazione"),
                )
            }
        }
    }
}

@Composable
private fun IntestazioneRegistrazione(stato: RegistrazioneUiStato.Dati, azioni: AzioniRegistrazione) {
    val colori = LocalSnastroColori.current
    val tipografia = LocalSnastroTipografia.current
    Column {
        // AC-580: 'Registrazioni ›' duplicates the always-visible sidebar entry point — a real "go
        // back" wire-up needs a callback threaded from the composition root (`avvio`, outside this
        // block's `snastro.ui.registrazione`/`snastro.ui.lettore` scope; [AzioniRegistrazione] stays
        // unchanged here). Left as a static breadcrumb until that block adds it; see the report.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.testTag("registrazione-briciole"),
        ) {
            Text(text = ETICHETTA_BRICIOLA_REGISTRAZIONI, style = tipografia.caption, color = colori.inkMuted)
            IconaSn(Icona.ChevronRight, descrizione = null, tinta = colori.inkMuted, dimensione = SnastroMisure.iconS)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stato.titolo,
                    style = tipografia.display,
                    color = colori.ink,
                    modifier = Modifier.testTag("registrazione-titolo"),
                )
                Text(
                    text = testoIntestazione(stato),
                    style = tipografia.caption,
                    color = colori.inkMuted,
                    modifier = Modifier.testTag("registrazione-meta"),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SnastroMisure.space2),
            ) {
                BottoneSn(
                    ETICHETTA_APRI_DOCUMENTO,
                    onClick = azioni.apriDocumento,
                    abilitato = stato.documentoPercorso != null,
                    icona = Icona.Document,
                    modifier = Modifier.testTag("registrazione-apri-documento"),
                )
                BottoneIconaSn(
                    Icona.Reveal,
                    ETICHETTA_MOSTRA_CARTELLA,
                    onClick = azioni.mostraDocumentoNellaCartella,
                    abilitato = stato.documentoPercorso != null,
                    modifier = Modifier.testTag("registrazione-mostra-cartella"),
                )
                MenuAltreAzioni(stato.soloLettura)
            }
        }
    }
}

/** AC-580's caption meta line — sums view-only data already in [stato] (a Voce count and, only when the
 * R2 panel is present, how many are still to identify); no new source, purely display arithmetic. */
private fun testoIntestazione(stato: RegistrazioneUiStato.Dati): String {
    val persone = stato.pannello?.carte?.size ?: stato.segmenti.map { it.voceId }.distinct().size
    val daIdentificare = stato.pannello?.carte?.count { it.contenuto is ContenutoCarta.DaIdentificare } ?: 0
    return "${formattaData(stato.dataRegistrazione)} · ${formattaDurataEstesa(stato.durataMs)} · " +
        testoPersone(persone, daIdentificare)
}

/**
 * AC-580: the header's 'More' menu — 'Ritrascrivi' only, disabled while already read-only for a
 * re-run. Triggering a re-run is a `RegistrazioniPresenter`/`avvio` capability
 * ([snastro.ui.registrazioni.AzioniRegistrazioni.ritrascrivi], outside this screen's own
 * [AzioniRegistrazione] — which stays unchanged): the item stays a no-op here until a future block
 * threads that command down to S3; see the report.
 */
@Composable
private fun MenuAltreAzioni(soloLettura: Boolean) {
    var aperto by remember { mutableStateOf(false) }
    Box {
        BottoneIconaSn(
            Icona.More,
            ETICHETTA_RITRASCRIVI,
            onClick = { aperto = true },
            modifier = Modifier.testTag("registrazione-altre-azioni"),
        )
        MenuSn(expanded = aperto, onDismissRequest = { aperto = false }) {
            DropdownMenuItem(
                text = { Text(ETICHETTA_RITRASCRIVI) },
                enabled = !soloLettura,
                onClick = { aperto = false },
            )
        }
    }
}

@Composable
private fun MessaggioInlineErrore(messaggio: String, onChiudi: () -> Unit) {
    val colori = LocalSnastroColori.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.testTag("registrazione-errore"),
    ) {
        Text(
            text = messaggio,
            color = colori.danger,
            style = LocalSnastroTipografia.current.body,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            text = ETICHETTA_ANNULLA,
            style = LocalSnastroTipografia.current.label,
            color = colori.accentInk,
            modifier = Modifier.padding(start = SnastroMisure.space2).clickable(onClick = onChiudi)
                .testTag("registrazione-errore-chiudi"),
        )
    }
}

/**
 * AC-452: the R1 two-line banner (`\n`-joined, [snastro.ui.testi.MESSAGGIO_RITRASCRIZIONE_IN_CORSO])
 * while a re-run is queued/running. AC-454: [pannello] is the R2 panel's own third line
 * ([RegistrazioneUiStato.Dati.bannerRitrascrizionePannello]), `null` without the panel block.
 */
@Composable
private fun BannerRitrascrizione(testo: String, pannello: String?) {
    val colori = LocalSnastroColori.current
    Surface(
        color = colori.warningSoft,
        shape = RoundedCornerShape(SnastroMisure.radiusCard),
        modifier = Modifier.fillMaxWidth().testTag("registrazione-banner-ritrascrizione"),
    ) {
        Row(modifier = Modifier.padding(horizontal = SnastroMisure.space4, vertical = SnastroMisure.space3)) {
            IconaSn(Icona.Retry, descrizione = null, tinta = colori.warning, dimensione = SnastroMisure.iconM)
            Spacer(modifier = Modifier.width(SnastroMisure.space3))
            Column {
                Text(text = testo, style = LocalSnastroTipografia.current.body, color = colori.ink)
                pannello?.let {
                    Text(
                        text = it,
                        style = LocalSnastroTipografia.current.body,
                        color = colori.ink,
                        modifier = Modifier.testTag("registrazione-banner-ritrascrizione-pannello"),
                    )
                }
            }
        }
    }
}

@Composable
private fun ElencoSegmenti(stato: RegistrazioneUiStato.Dati, azioni: AzioniRegistrazione) {
    // AC-209: the selection toggle exists only with the R2 panel (AC-402: none in R1).
    val selezionabile = stato.pannello != null
    LazyColumn(modifier = Modifier.fillMaxSize().testTag("registrazione-lista")) {
        itemsIndexed(stato.segmenti, key = { _, s -> s.segmentoId.numero }) { indice, segmento ->
            // AC-582: consecutive Segmenti of the same Voce hide the who line.
            val mostraChi = indice == 0 || stato.segmenti[indice - 1].voceId != segmento.voceId
            SegmentoItem(
                segmento,
                azioni,
                selezione = if (selezionabile) segmento.segmentoId in stato.selezione else null,
                mostraChi = mostraChi,
            )
        }
    }
}

/** AC-582: 56dp timecode gutter (a 16dp checked box replaces it once selected) · who line (hidden on a
 * consecutive Segmento of the same Voce) · text in the reading face. Click plays; hover/playing/selected
 * are the presenter's own [SegmentoRiga] flags, never re-decided here. */
// LongMethod/CyclomaticComplexMethod: one transcript row with every AC-582 visual branch (gutter,
// who line, pin, pending naming) inline — RC-2 thin view, splitting further would scatter one row.
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
private fun SegmentoItem(segmento: SegmentoRiga, azioni: AzioniRegistrazione, selezione: Boolean?, mostraChi: Boolean) {
    val colori = LocalSnastroColori.current
    val tipografia = LocalSnastroTipografia.current
    val interazione = remember { MutableInteractionSource() }
    val hover by interazione.collectIsHoveredAsState()
    val selezionato = selezione == true
    val sfondo = when {
        selezionato || segmento.inRiproduzione -> colori.accentSoft
        hover -> colori.sunken
        else -> Color.Transparent
    }
    val n = segmento.segmentoId.numero
    Surface(
        onClick = { azioni.riproduciSegmento(segmento.segmentoId) },
        shape = RoundedCornerShape(SnastroMisure.radiusControl),
        color = sfondo,
        contentColor = colori.ink,
        border = if (selezionato) BorderStroke(1.5.dp, colori.accentInk) else null,
        interactionSource = interazione,
        modifier = Modifier.fillMaxWidth().testTag("registrazione-segmento-$n"),
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.padding(horizontal = SnastroMisure.space3, vertical = PADDING_RIGA_V),
        ) {
            Box(modifier = Modifier.width(LARGHEZZA_GUTTER), contentAlignment = Alignment.TopEnd) {
                if (selezione != null && selezionato) {
                    CasellaSelezionata(segmento.segmentoId, azioni)
                } else {
                    Text(
                        text = formattaDurata(segmento.inizioMs),
                        style = tipografia.timecode,
                        color = if (segmento.inRiproduzione) colori.accentInk else colori.inkMuted,
                        modifier = if (selezione != null) {
                            Modifier.clickable { azioni.selezionaSegmento(segmento.segmentoId) }
                                .testTag("registrazione-seleziona-$n")
                        } else {
                            Modifier
                        },
                    )
                }
            }
            Spacer(modifier = Modifier.width(SnastroMisure.space3))
            Column(modifier = Modifier.weight(1f)) {
                if (mostraChi) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PallinoVoce(voceId = segmento.voceId, conNome = segmento.haNome)
                        Spacer(modifier = Modifier.width(SnastroMisure.space1))
                        Text(
                            text = segmento.etichettaVoce,
                            style = if (segmento.haNome) {
                                tipografia.label.copy(fontWeight = FontWeight.SemiBold)
                            } else {
                                tipografia.label
                            },
                            color = if (segmento.haNome) colori.ink else colori.inkMuted,
                            modifier = Modifier.testTag("registrazione-voce-$n"),
                        )
                        if (segmento.confermato) PuntinaConfermata(n)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                }
                Text(
                    text = segmento.testo,
                    style = tipografia.transcript,
                    color = colori.ink,
                    modifier = Modifier.testTag("registrazione-testo-$n"),
                )
                segmento.attesaFrase?.let { AttesaFrase(segmento, it, azioni) }
            }
        }
    }
}

/** [SegmentoRiga.etichettaVoce] is the presenter's own fallback ("Voce n") when the Voce has no Nome
 * yet (`etichettaDiVoce`) — the same convention every fixture in this codebase already relies on. */
private val SegmentoRiga.haNome: Boolean
    get() = etichettaVoce != "Voce ${voceId.numero}"

/** AC-582: the 16dp checked box (accent fill, onAccent Check) that replaces the timecode once selected;
 * clicking it toggles the selection off (same [AzioniRegistrazione.selezionaSegmento], which already
 * toggles membership). */
@Composable
private fun CasellaSelezionata(id: SegmentoId, azioni: AzioniRegistrazione) {
    val colori = LocalSnastroColori.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(DIMENSIONE_CASELLA)
            .clip(RoundedCornerShape(SnastroMisure.radiusControl))
            .background(colori.accent)
            .clickable { azioni.selezionaSegmento(id) }
            .testTag("registrazione-seleziona-${id.numero}"),
    ) {
        IconaSn(Icona.Check, descrizione = null, tinta = colori.onAccent, dimensione = SnastroMisure.iconS)
    }
}

/** AC-526/AC-528: 'Dai un nome a questa frase' (attivo Parlanti, then 'nuovo…') and 'Togli conferma'. */
@Composable
private fun AzioniFrase(frase: MenuFrase, azioni: AzioniRegistrazione, onNuovo: () -> Unit) {
    MenuParlanti(
        "registrazione-nomina-frase",
        ETICHETTA_NOMINA_FRASE,
        frase.parlanti,
        frase.abilitata,
        icona = Icona.Person,
        conNuovo = onNuovo,
    ) { azioni.nominaFrase(ObiettivoNome.Esistente(it)) }
    if (frase.confermato && frase.togliConfermaDisponibile) {
        BottoneSn(
            ETICHETTA_TOGLI_CONFERMA,
            onClick = azioni.togliConferma,
            abilitato = frase.abilitata,
            variante = VarianteBottone.Secondario,
            piccolo = true,
            icona = Icona.Pin,
            modifier = Modifier.testTag("registrazione-togli-conferma"),
        )
    }
}

/** AC-528: the pin of a confirmed Segmento, with its tooltip (also its accessible description). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PuntinaConfermata(n: Int) {
    val colori = LocalSnastroColori.current
    TooltipArea(
        tooltip = {
            Surface(color = colori.ink, shape = RoundedCornerShape(SnastroMisure.radiusControl)) {
                Text(
                    TOOLTIP_FRASE_CONFERMATA,
                    color = colori.surface,
                    modifier = Modifier.padding(SnastroMisure.space2),
                )
            }
        },
    ) {
        Box(
            modifier = Modifier
                .padding(start = SnastroMisure.space1)
                .semantics { contentDescription = TOOLTIP_FRASE_CONFERMATA }
                .testTag("registrazione-confermato-$n"),
        ) {
            IconaSn(Icona.Pin, descrizione = null, tinta = colori.accentInk, dimensione = SnastroMisure.iconS)
        }
    }
}

/** AC-529: a pending naming of this row — an indicator at once; past the threshold the wait line + 'Annulla'. */
@Composable
private fun AttesaFrase(segmento: SegmentoRiga, attesa: AttesaComando, azioni: AzioniRegistrazione) {
    val colori = LocalSnastroColori.current
    val n = segmento.segmentoId.numero
    when (attesa) {
        AttesaComando.IN_CORSO -> CircularProgressIndicator(
            modifier = Modifier.padding(top = 2.dp).size(DIMENSIONE_INDICATORE)
                .testTag("registrazione-frase-in-corso-$n"),
        )
        AttesaComando.IN_ATTESA -> Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.testTag("registrazione-frase-in-attesa-$n"),
        ) {
            Text(MESSAGGIO_COMANDO_IN_ATTESA, style = LocalSnastroTipografia.current.caption, color = colori.inkMuted)
            BottoneSn(
                ETICHETTA_ANNULLA,
                onClick = { azioni.annullaFrase(segmento.segmentoId) },
                variante = VarianteBottone.Link,
                modifier = Modifier.testTag("registrazione-frase-annulla-$n"),
            )
        }
    }
}
