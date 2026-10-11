package snastro.ui.registrazione

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import snastro.ui.SnastroTema
import snastro.ui.lettore.BarraLettore
import snastro.ui.lettore.CorsiaVoce
import snastro.ui.stile.BannerSn
import snastro.ui.stile.BottoneSn
import snastro.ui.stile.LocalSnastroColori
import snastro.ui.stile.LocalSnastroTipografia
import snastro.ui.stile.SchedeSn
import snastro.ui.stile.SnastroMisure
import snastro.ui.stile.TipoBanner
import snastro.ui.temaScuro
import snastro.ui.testi.ETICHETTA_ANNULLA
import snastro.ui.testi.ETICHETTA_RIPROVA
import snastro.ui.testi.ETICHETTA_SCHEDA_RIASSUNTO
import snastro.ui.testi.ETICHETTA_SCHEDA_TRASCRIZIONE
import snastro.ui.testi.MESSAGGIO_BANNER_AUDIO_MANCANTE
import snastro.ui.testi.MESSAGGIO_TRASCRITTO_VUOTO
import snastro.ui.testi.TITOLO_BANNER_AUDIO_MANCANTE
import snastro.ui.testi.testoBannerVociDaIdentificare

private val LARGHEZZA_MASSIMA_TRASCRITTO = 712.dp
private val LARGHEZZA_SOGLIA_IMPILAMENTO = 1_100.dp
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
