// TooManyFunctions: one screen split into many small, single-purpose composables (RC-2 thin view) —
// header, skeleton, empty/error states and the per-Segmento row each deserve their own function.
@file:Suppress("TooManyFunctions")

package snastro.ui.registrazione

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.parlanti.applicazione.eventi.TipoParlanteVista
import snastro.ui.SnastroTema
import snastro.ui.formattaData
import snastro.ui.formattaDurata
import snastro.ui.formattaDurataEstesa
import snastro.ui.lettore.BarraLettore
import snastro.ui.lettore.CorsiaVoce
import snastro.ui.stile.BannerSn
import snastro.ui.stile.BottoneIconaSn
import snastro.ui.stile.BottoneSn
import snastro.ui.stile.Icona
import snastro.ui.stile.IconaSn
import snastro.ui.stile.LocalSnastroColori
import snastro.ui.stile.LocalSnastroTipografia
import snastro.ui.stile.PallinoVoce
import snastro.ui.stile.SchedeSn
import snastro.ui.stile.SnastroMisure
import snastro.ui.stile.TipoBanner
import snastro.ui.stile.VarianteBottone
import snastro.ui.temaScuro
import snastro.ui.testi.DESCRIZIONE_SELEZIONA_FRASE
import snastro.ui.testi.ETICHETTA_ANNULLA
import snastro.ui.testi.ETICHETTA_APRI_SBOBINATURA
import snastro.ui.testi.ETICHETTA_BRICIOLA_REGISTRAZIONI
import snastro.ui.testi.ETICHETTA_DESELEZIONA
import snastro.ui.testi.ETICHETTA_DIVIDI_VOCE
import snastro.ui.testi.ETICHETTA_MOSTRA_CARTELLA
import snastro.ui.testi.ETICHETTA_NOMINA_FRASE
import snastro.ui.testi.ETICHETTA_NUOVA_VOCE
import snastro.ui.testi.ETICHETTA_RIASSEGNA_A
import snastro.ui.testi.ETICHETTA_RIPROVA
import snastro.ui.testi.ETICHETTA_SCHEDA_RIASSUNTO
import snastro.ui.testi.ETICHETTA_SCHEDA_TRASCRIZIONE
import snastro.ui.testi.ETICHETTA_TOGLI_CONFERMA
import snastro.ui.testi.MESSAGGIO_BANNER_AUDIO_MANCANTE
import snastro.ui.testi.MESSAGGIO_COMANDO_IN_ATTESA
import snastro.ui.testi.MESSAGGIO_TRASCRITTO_VUOTO
import snastro.ui.testi.TITOLO_BANNER_AUDIO_MANCANTE
import snastro.ui.testi.TOOLTIP_FRASE_CONFERMATA
import snastro.ui.testi.etichettaParte
import snastro.ui.testi.testoBannerVociDaIdentificare
import snastro.ui.testi.testoBriciolaIncontro
import snastro.ui.testi.testoParteDi
import snastro.ui.testi.testoPersone
import snastro.ui.testi.testoSelezione
import java.time.format.DateTimeFormatter

private val LARGHEZZA_MASSIMA_TRASCRITTO = 712.dp
private val LARGHEZZA_SOGLIA_IMPILAMENTO = 1_100.dp
private val LARGHEZZA_SELETTORE_PARTE_PER_PARTE = 96.dp
private val FORMATO_ORA: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val LARGHEZZA_GUTTER = 56.dp
private val DIMENSIONE_INDICATORE = 14.dp
private val DIMENSIONE_CASELLA = 16.dp
private val PADDING_RIGA_V = 10.dp
private val ALTEZZA_RIGA_SCHELETRO = 16.dp
private const val RIGHE_SCHELETRO = 4
private const val LARGHEZZA_SCHELETRO_PARI = 0.8f
private const val LARGHEZZA_SCHELETRO_DISPARI = 0.55f

/**
 * Thin view of S3 · Registrazione (RC-2): only renders [stato] and forwards [azioni]'s events. The Voci
 * panel ([PannelloVociVista], right) and the selection toolbar ([BarraSelezioneVista], above the
 * transcript) render once [RegistrazioneUiStato.Dati.pannello] is published (AC-402).
 *
 * [scuro]/[riduciMovimento] mirror [SnastroTema]'s own optional overrides (same defaults, same
 * call-site-unchanged guarantee for [RegistrazioneRoute]) — AC-589's render-check pins both per
 * fixture (light/dark, reduce-motion) the same way `StileRenderCheckTest` already does per component.
 */
@Composable
fun SchermataRegistrazione(
    stato: RegistrazioneUiStato,
    azioni: AzioniRegistrazione,
    scuro: Boolean = temaScuro(),
    riduciMovimento: Boolean? = null,
) {
    SnastroTema(scuro = scuro, riduciMovimento = riduciMovimento) {
        Surface(color = LocalSnastroColori.current.surface, modifier = Modifier.fillMaxSize()) {
            when (stato) {
                RegistrazioneUiStato.Caricamento -> ScheletroRegistrazione()
                is RegistrazioneUiStato.Dati -> ContenutoRegistrazione(stato, azioni)
                is RegistrazioneUiStato.ParteInAttesa -> ParteInAttesaRegistrazione(stato, azioni)
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

/** D-0051 (L198): a Parte with no Trascritto yet: breadcrumb, title and switcher as on any Parte, then the reason. */
@Composable
private fun ParteInAttesaRegistrazione(stato: RegistrazioneUiStato.ParteInAttesa, azioni: AzioniRegistrazione) {
    val colori = LocalSnastroColori.current
    val tipografia = LocalSnastroTipografia.current
    Column(
        modifier = Modifier.fillMaxSize().padding(SnastroMisure.space5).testTag("registrazione-parte-in-attesa"),
    ) {
        BriciolaParte(stato.parte)
        Text(text = stato.titolo, style = tipografia.display, color = colori.ink)
        SelettoreParte(stato.parte, azioni.vaiAllaParte)
        Spacer(modifier = Modifier.height(SnastroMisure.space5))
        Text(
            text = stato.messaggio,
            style = tipografia.body,
            color = colori.inkMuted,
            modifier = Modifier.testTag("registrazione-parte-in-attesa-messaggio"),
        )
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
        stato.bannerSchermata?.let {
            Spacer(modifier = Modifier.height(SnastroMisure.space3))
            BannerDiSchermata(it)
        }
        stato.errore?.let {
            Spacer(modifier = Modifier.height(SnastroMisure.space3))
            MessaggioInlineErrore(it, azioni.chiudiErrore)
        }
        Spacer(modifier = Modifier.height(SnastroMisure.space4))
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // D-0014 (pre-release finding #180): with the Riassunto tab selected, it gets the WHOLE
            // content area — the Voci panel ("Riassegna per somiglianza" included) is Trascritto-only
            // chrome, hidden here rather than squeezed alongside a summary that can run long and clip.
            if (stato.schedaSelezionata == SchedaS3.RIASSUNTO) {
                ColonnaTrascritto(stato, azioni, Modifier.fillMaxSize())
                // AC-581: below the threshold the panel stacks under the transcript (render-check at 1024x640).
            } else if (maxWidth < LARGHEZZA_SOGLIA_IMPILAMENTO) {
                // Both children must carry a `weight` here: an unweighted panel would be measured
                // BEFORE the weighted transcript and — since its own `voci-lista` is itself a
                // `weight(1f)` LazyColumn that greedily fills whatever ceiling it is offered — it would
                // claim the Column's entire height first, leaving nothing for the transcript (a real
                // regression caught only by running the render-check, not by "it compiles").
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(SnastroMisure.space4),
                ) {
                    // L750c: SKIPPED (see the worker's report) — an even 1:1 split leaves the transcript
                    // at only ~150dp at 1024x640, but growing it (weight 2:1, measured) shrinks the Voci
                    // panel below what several existing render-checks need (AC-411/412/413/454/530/533/
                    // 545/547, AC-213/215) to show a card's content without scrolling — the two already
                    // share a very tight ~418dp between them at this size. A real fix needs a broader
                    // pass on the stacked layout's other chrome, not a one-line weight change here.
                    ColonnaTrascritto(stato, azioni, Modifier.weight(1f).fillMaxWidth())
                    stato.pannello?.let {
                        PannelloVociVista(it, stato.segmenti, azioni, Modifier.weight(1f).fillMaxWidth())
                    }
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
                        PannelloVociVista(
                            it,
                            stato.segmenti,
                            azioni,
                            Modifier.width(SnastroMisure.pannello).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColonnaTrascritto(stato: RegistrazioneUiStato.Dati, azioni: AzioniRegistrazione, modifier: Modifier) {
    Column(modifier = modifier) {
        // Pre-release finding #83 (rework): [RegistrazioneUiStato.Dati.contenutoRiassunto] is
        // MANDATORY (ADR 0030 §1) — the running app always shows both tabs, so this no longer
        // branches on it being present at all.
        SchedeSn(
            schede = listOf(ETICHETTA_SCHEDA_TRASCRIZIONE, ETICHETTA_SCHEDA_RIASSUNTO),
            selezionata = stato.schedaSelezionata.ordinal,
            onSeleziona = { indice -> azioni.selezionaScheda(SchedaS3.entries[indice]) },
            segni = stato.segnoRiassunto?.let { mapOf(SchedaS3.RIASSUNTO.ordinal to it) }.orEmpty(),
            modifier = Modifier.testTag("registrazione-schede"),
        )
        Spacer(modifier = Modifier.height(SnastroMisure.space3))
        if (stato.schedaSelezionata == SchedaS3.RIASSUNTO) {
            stato.contenutoRiassunto()
        } else {
            stato.barraSelezione?.let { BarraSelezioneVista(it, stato.pannello?.carte.orEmpty(), azioni) }
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
}

/** AC-S123: dispatches the ONE screen banner the precedence table picked
 * ([RegistrazioneUiStato.Dati.bannerSchermata]) — [BannerSchermata.Ritrascrizione] keeps
 * [BannerRitrascrizione]'s pre-existing tag/wording untouched. */
@Composable
private fun BannerDiSchermata(banner: BannerSchermata) {
    when (banner) {
        is BannerSchermata.Ritrascrizione -> BannerRitrascrizione(banner.testo, banner.pannello)
        BannerSchermata.AudioMancante -> BannerSn(
            tipo = TipoBanner.Avviso,
            titolo = TITOLO_BANNER_AUDIO_MANCANTE,
            testo = MESSAGGIO_BANNER_AUDIO_MANCANTE,
            modifier = Modifier.fillMaxWidth().testTag("registrazione-banner-audio-mancante"),
        )
        is BannerSchermata.VociDaIdentificare -> BannerSn(
            tipo = TipoBanner.Info,
            titolo = testoBannerVociDaIdentificare(banner.numero),
            testo = "",
            modifier = Modifier.fillMaxWidth().testTag("registrazione-banner-voci-da-identificare"),
        )
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
private fun BarraSelezioneVista(barra: BarraSelezione, carte: List<CartaVoce>, azioni: AzioniRegistrazione) {
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
                    carte = carte,
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
        // AC-580: plain, non-interactive caption — this screen has no callback to actually navigate
        // back (the always-visible sidebar already offers that path); no chevron either, so nothing
        // implies a click that would do nothing (rework cycle 1, HIGH-1).
        BriciolaParte(stato.parte)
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
                stato.parte?.let { SelettoreParte(it, azioni.vaiAllaParte) }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SnastroMisure.space2),
            ) {
                BottoneSn(
                    ETICHETTA_APRI_SBOBINATURA,
                    onClick = azioni.apriSbobinatura,
                    abilitato = stato.sbobinaturaPercorso != null,
                    icona = Icona.Document,
                    modifier = Modifier.testTag("registrazione-apri-sbobinatura"),
                )
                BottoneIconaSn(
                    Icona.Reveal,
                    ETICHETTA_MOSTRA_CARTELLA,
                    onClick = azioni.mostraSbobinaturaNellaCartella,
                    abilitato = stato.sbobinaturaPercorso != null,
                    modifier = Modifier.testTag("registrazione-mostra-cartella"),
                )
            }
        }
    }
}

@Composable
private fun BriciolaParte(parte: IntestazioneParte?) {
    Text(
        text = parte?.let {
            "$ETICHETTA_BRICIOLA_REGISTRAZIONI › ${testoBriciolaIncontro(it.titoloIncontro, it.totale)}"
        } ?: ETICHETTA_BRICIOLA_REGISTRAZIONI,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = LocalSnastroTipografia.current.caption,
        color = LocalSnastroColori.current.inkMuted,
        modifier = Modifier.testTag("registrazione-briciole"),
    )
}

/** AC-580's caption meta line — sums view-only data already in [stato] (a Voce count and, only once the
 * Voci panel is published, how many are still to identify); no new source, purely display arithmetic. */
private fun testoIntestazione(stato: RegistrazioneUiStato.Dati): String {
    val prefisso = stato.parte?.let { "${testoParteDi(it.numero, it.totale)} · " }.orEmpty()
    val ora = stato.parte?.ora?.let { " ${it.format(FORMATO_ORA)}" }.orEmpty()
    val persone = stato.pannello?.carte?.size ?: stato.segmenti.map { it.voceId }.distinct().size
    val daIdentificare = stato.pannello?.carte?.count { it.contenuto is ContenutoCarta.DaIdentificare } ?: 0
    return "$prefisso${formattaData(stato.dataRegistrazione)}$ora · ${formattaDurataEstesa(stato.durataMs)} · " +
        testoPersone(persone, daIdentificare)
}

/** AC-I74: 'Parte 1 | Parte 2 | Parte 3', the current one selected; choosing another opens S3 of that Parte
 * (the per-window tab choice, [SelezioneSchedaS3], is kept by the composition). */
@Composable
private fun SelettoreParte(parte: IntestazioneParte, vaiAllaParte: (RegistrazioneId) -> Unit) {
    Spacer(modifier = Modifier.height(SnastroMisure.space2))
    // L200: the tabs keep their width and the strip scrolls when the header column has less room than N Parti need.
    val selezionata = parte.parti.indexOfFirst { it.numero == parte.numero }.coerceAtLeast(0)
    val scorrimento = rememberScrollState()
    // L254/L268: the step is the strip's measured width (in px, whatever the density) over the tab count. That is the
    // tab's own width because SchedeSn gives every tab `weight(1f)`, an equal share; unequal tabs would need each
    // tab's own position. L267: never divides by zero, even for an empty list the switcher does not show today.
    var larghezzaStriscia by remember { mutableIntStateOf(0) }
    val passo = larghezzaStriscia / parte.parti.size.coerceAtLeast(1)
    // L235: the selected Parte's tab is brought into view (scrollTo clamps to the strip's end).
    LaunchedEffect(selezionata, passo) { scorrimento.scrollTo(selezionata * passo) }
    Box(modifier = Modifier.fillMaxWidth().horizontalScroll(scorrimento)) {
        SchedeSn(
            schede = parte.parti.map { etichettaParte(it.numero) },
            selezionata = selezionata,
            onSeleziona = { indice ->
                parte.parti[indice].takeIf { it.numero != parte.numero }?.let { vaiAllaParte(it.registrazioneId) }
            },
            modifier = Modifier.width(LARGHEZZA_SELETTORE_PARTE_PER_PARTE * parte.parti.size)
                .onSizeChanged { larghezzaStriscia = it.width }
                .testTag("registrazione-parti"),
            prefissoTag = "parte",
        )
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
 * AC-452: the two-line banner (`\n`-joined, [snastro.ui.testi.MESSAGGIO_RITRASCRIZIONE_IN_CORSO])
 * while a re-run is queued/running. AC-454: [pannello] is the Voci panel's own third line
 * ([RegistrazioneUiStato.Dati.bannerRitrascrizionePannello]), `null` before the panel is published.
 */
@Composable
private fun BannerRitrascrizione(testo: String, pannello: String?) {
    // The kit's own Avviso banner (AC-588, MED-9): same texts as before (ADR 0017/0018), reflowed into
    // BannerSn's titolo (first line, bold) + testo (the rest, `pannello`'s own third line appended) —
    // never a hand-drawn copy of what the kit already owns.
    val righe = testo.split("\n", limit = 2)
    val corpo = buildString {
        righe.getOrNull(1)?.let { append(it) }
        pannello?.let {
            if (isNotEmpty()) append('\n')
            append(it)
        }
    }
    BannerSn(
        tipo = TipoBanner.Avviso,
        titolo = righe.first(),
        testo = corpo,
        modifier = Modifier.fillMaxWidth().testTag("registrazione-banner-ritrascrizione"),
    )
}

@Composable
private fun ElencoSegmenti(stato: RegistrazioneUiStato.Dati, azioni: AzioniRegistrazione) {
    // AC-209: the selection toggle exists only once the Voci panel is published (AC-402).
    val selezionabile = stato.pannello != null
    // AC-582/MED-8: "named" is decided from the Nome already in the panel's own state (a Voce's card
    // content is `ContenutoCarta.Attribuita` there), never by comparing `etichettaVoce` to a literal
    // "Voce n" fallback string (rework cycle 1).
    val vociConNome = stato.pannello?.carte
        ?.mapNotNull { carta -> carta.voceId.takeIf { carta.contenuto is ContenutoCarta.Attribuita } }
        ?.toSet()
        .orEmpty()
    LazyColumn(modifier = Modifier.fillMaxSize().testTag("registrazione-lista")) {
        itemsIndexed(stato.segmenti, key = { _, s -> s.segmentoId.numero }) { indice, segmento ->
            // AC-582: consecutive Segmenti of the same Voce hide the who line.
            val mostraChi = indice == 0 || stato.segmenti[indice - 1].voceId != segmento.voceId
            SegmentoItem(
                segmento,
                azioni,
                selezione = if (selezionabile) segmento.segmentoId in stato.selezione else null,
                mostraChi = mostraChi,
                haNome = segmento.voceId in vociConNome,
            )
        }
    }
}

/** AC-582: 56dp timecode gutter (a 16dp checked box replaces it once selected) · who line (hidden on a
 * consecutive Segmento of the same Voce) · text in the reading face. Click plays; hover/playing/selected
 * are the presenter's own [SegmentoRiga] flags, never re-decided here. */
// LongMethod/CyclomaticComplexMethod: one transcript row with every AC-582 visual branch (gutter,
// who line, pin, pending naming) inline — RC-2 thin view, splitting further would scatter one row.
// LongParameterList: one parameter per row-level decision the presenter/panel already made.
@Suppress("LongMethod", "CyclomaticComplexMethod", "LongParameterList")
@Composable
private fun SegmentoItem(
    segmento: SegmentoRiga,
    azioni: AzioniRegistrazione,
    selezione: Boolean?,
    mostraChi: Boolean,
    haNome: Boolean,
) {
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // L761c: a confirmed, consecutive row (who line hidden) used to lose its pin the
                        // moment it was selected — the checkbox replaced the whole gutter content that
                        // drew it. Same rule as the unselected branch below: draw it next to whatever
                        // sits in the gutter, here the checkbox.
                        if (!mostraChi && segmento.confermato) {
                            PuntinaConfermata(n)
                            Spacer(modifier = Modifier.width(SnastroMisure.space1))
                        }
                        CasellaSelezionata(segmento.segmentoId, azioni)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // AC-528/AC-582: a confirmed Segmento keeps its pin even when the who line is
                        // hidden (a consecutive row of the same Voce) — drawn here, next to the timecode,
                        // instead of silently disappearing (rework cycle 1, HIGH-4).
                        if (!mostraChi && segmento.confermato) {
                            PuntinaConfermata(n)
                            Spacer(modifier = Modifier.width(SnastroMisure.space1))
                        }
                        Text(
                            text = formattaDurata(segmento.inizioMs),
                            style = tipografia.timecode,
                            color = if (segmento.inRiproduzione) colori.accentInk else colori.inkMuted,
                            modifier = if (selezione != null) {
                                // AC-209/AC-582 accessibility (rework cycle 1, MED-7): the same toggle
                                // [azioni.selezionaSegmento] already offers, exposed as a real checkbox
                                // (`role`, `value`, a label) — not just a bare clickable text.
                                Modifier
                                    .toggleable(
                                        value = false,
                                        role = Role.Checkbox,
                                        onValueChange = { azioni.selezionaSegmento(segmento.segmentoId) },
                                    )
                                    .semantics { contentDescription = DESCRIZIONE_SELEZIONA_FRASE }
                                    .testTag("registrazione-seleziona-$n")
                            } else {
                                Modifier
                            },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(SnastroMisure.space3))
            Column(modifier = Modifier.weight(1f)) {
                if (mostraChi) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PallinoVoce(voceId = segmento.voceId, conNome = haNome)
                        Spacer(modifier = Modifier.width(SnastroMisure.space1))
                        Text(
                            text = segmento.etichettaVoce,
                            style = if (haNome) {
                                tipografia.label.copy(fontWeight = FontWeight.SemiBold)
                            } else {
                                tipografia.label
                            },
                            color = if (haNome) colori.ink else colori.inkMuted,
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
            .toggleable(
                value = true,
                role = Role.Checkbox,
                onValueChange = { azioni.selezionaSegmento(id) },
            )
            .semantics { contentDescription = DESCRIZIONE_SELEZIONA_FRASE }
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
    if (frase.confermato) {
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
