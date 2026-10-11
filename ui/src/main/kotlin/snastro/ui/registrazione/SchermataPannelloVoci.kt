package snastro.ui.registrazione

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import snastro.parlanti.applicazione.letture.CoppiaTraParti
import snastro.parlanti.applicazione.letture.PropostaDiUnione
import snastro.ui.stile.AzioneBanner
import snastro.ui.stile.BannerSn
import snastro.ui.stile.BottoneSn
import snastro.ui.stile.Icona
import snastro.ui.stile.LocalSnastroColori
import snastro.ui.stile.LocalSnastroTipografia
import snastro.ui.stile.SchedeSn
import snastro.ui.stile.SnastroMisure
import snastro.ui.stile.TipoBanner
import snastro.ui.stile.VarianteBottone
import snastro.ui.testi.ETICHETTA_UNISCI
import snastro.ui.testi.MESSAGGIO_ESTRATTI_NON_DISPONIBILI
import snastro.ui.testi.TITOLO_PANNELLO_VOCI
import snastro.ui.testi.descrizioneAscoltaVoce
import snastro.ui.testi.testoTraParti
import snastro.ui.testi.testoUnione

/**
 * Thin view of S3's Voci panel (RC-2): renders [pannello] — every enabled/disabled decision is the
 * presenter's ([CartaVoce.azioniAbilitate], [CartaVoce.confermaAbilitata], [PannelloVoci.estrattiDisponibili],
 * [PannelloVoci.unioneAbilitata]) — and forwards [azioni]. Only open/closed menus and the 'nuovo…' text
 * being typed are view-local. AC-584: the Riassunto tab is part B — [snastro.ui.stile.SchedeSn]
 * (moved there, AC-S45) shows the single 'Voci' tab of part A. [segmenti] is the transcript's own
 * row list (already in `RegistrazioneUiStato`, no new source) — AC-585's speaking-time caption sums
 * each Voce's own durations from it (view arithmetic).
 */
@Composable
internal fun PannelloVociVista(
    pannello: PannelloVoci,
    segmenti: List<SegmentoRiga>,
    azioni: AzioniRegistrazione,
    modifier: Modifier = Modifier,
) {
    val colori = LocalSnastroColori.current
    val kDaIdentificare = pannello.carte.count { it.contenuto is ContenutoCarta.DaIdentificare }
    val durataPerVoce = remember(segmenti) {
        segmenti.groupBy { it.voceId }.mapValues { (_, segs) -> segs.sumOf { it.fineMs - it.inizioMs } }
    }
    // AC-213 + "at most one Primario per screen" (rework cycle 1, HIGH-3): only the FIRST DaIdentificare
    // card in list order gets its own action as Primario; every other one renders the same action
    // Secondario instead of stacking several Primario buttons on the same screen. L761d: while the
    // Somiglianza header is computing or previewing, ITS OWN 'Applica' (also Primario) is the true
    // primary action on the screen — no card gets one then, whatever `pannello.carte` says. A card's own
    // 'nuovo…' form (its 'Crea' is Primario too) demotes THAT card alone, in `CartaVoceVista`.
    val faseSomiglianza = pannello.somiglianza?.fase
    val formSomiglianzaAperta = faseSomiglianza is FaseSomiglianza.Calcolo ||
        faseSomiglianza is FaseSomiglianza.Anteprima
    val primaCartaAzione = if (formSomiglianzaAperta) {
        null
    } else {
        pannello.carte.firstOrNull { it.contenuto is ContenutoCarta.DaIdentificare }?.voceId
    }
    // AC-581: the tab chrome and the somiglianza card are ITEMS of the same LazyColumn, not a fixed
    // header measured before it — a header of unbounded height (the Calcolo/Anteprima phases can grow
    // past a single line) would otherwise starve the list of nearly all its space in the stacked
    // layout (a genuine render-check catch, not a hypothetical: it starved `voci-lista` down to a few
    // px and made `performScrollToNode` spin against a near-zero viewport).
    Box(modifier = modifier.testTag("voci-pannello")) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("voci-lista"),
            verticalArrangement = Arrangement.spacedBy(SnastroMisure.space3),
        ) {
            item {
                val titoloScheda = if (kDaIdentificare > 0) {
                    "$TITOLO_PANNELLO_VOCI · $kDaIdentificare da identificare"
                } else {
                    TITOLO_PANNELLO_VOCI
                }
                SchedeSn(
                    schede = listOf(titoloScheda),
                    selezionata = 0,
                    onSeleziona = {},
                    modifier = Modifier.testTag("voci-schede"),
                )
            }
            pannello.somiglianza?.let { s -> item { SezioneSomiglianza(s, azioni) } }
            if (!pannello.estrattiDisponibili) {
                item {
                    Text(
                        text = MESSAGGIO_ESTRATTI_NON_DISPONIBILI,
                        color = colori.danger,
                        style = LocalSnastroTipografia.current.caption,
                        modifier = Modifier.testTag("voci-estratti-non-disponibili"),
                    )
                }
            }
            bannerUnioni(pannello, azioni)
            items(pannello.carte, key = { it.voceId.numero }) { carta ->
                CartaVoceVista(
                    carta,
                    pannello,
                    durataPerVoce[carta.voceId] ?: 0L,
                    primario = carta.voceId == primaCartaAzione,
                    azioni,
                )
            }
        }
    }
}

/** The merge banners: the Proposta di unione first, then the cross-Parte one (at most one per screen, AC-I84). */
private fun LazyListScope.bannerUnioni(pannello: PannelloVoci, azioni: AzioniRegistrazione) {
    items(pannello.unioni, key = { "unione-${it.voceA.numero}-${it.voceB.numero}" }) { unione ->
        BannerUnione(unione, pannello.unioneAbilitata, azioni)
    }
    pannello.traParti?.let { coppia ->
        item(key = "tra-parti-${coppia.voceA.numero}-${coppia.voceB.numero}") {
            BannerTraParti(coppia, pannello, azioni)
        }
    }
}

/** AC-216: one click merges (never automatic); it disappears when the presenter drops it. */
@Composable
private fun BannerUnione(unione: PropostaDiUnione, abilitata: Boolean, azioni: AzioniRegistrazione) {
    BannerSn(
        tipo = TipoBanner.Info,
        // The message IS the title (AC-216 has no separate heading); [ETICHETTA_UNISCI] names only
        // the action button below, so the two never collide on the same text (render-check AC-589).
        titolo = testoUnione(unione.voceA.numero, unione.voceB.numero, unione.nome),
        testo = "",
        modifier = Modifier.testTag("voci-unione-${unione.voceA.numero}-${unione.voceB.numero}"),
        azione = AzioneBanner(ETICHETTA_UNISCI) { azioni.unisci(unione.voceA, unione.voceB) }.takeIf { abilitata },
    )
}

/**
 * AC-I83: never automatic, no 'No'; one '▶' per Voce plays its own estratto; the Voce whose first Parte is
 * earlier survives (D-0057).
 */
@Composable
private fun BannerTraParti(coppia: CoppiaTraParti, pannello: PannelloVoci, azioni: AzioniRegistrazione) {
    val a = coppia.voceA.numero
    val b = coppia.voceB.numero
    BannerSn(
        tipo = TipoBanner.Info,
        titolo = testoTraParti(a, coppia.parteA, b),
        testo = "",
        modifier = Modifier.testTag("voci-tra-parti-$a-$b"),
        azione = AzioneBanner(ETICHETTA_UNISCI) { azioni.unisci(coppia.voceA, coppia.voceB) }
            .takeIf { pannello.unioneAbilitata },
        extra = {
            Row(horizontalArrangement = Arrangement.spacedBy(SnastroMisure.space2)) {
                BottoneSn(
                    "Voce $b",
                    onClick = { azioni.riproduciEstratto(coppia.estrattoB) },
                    abilitato = pannello.estrattiDisponibili,
                    variante = VarianteBottone.Fantasma,
                    piccolo = true,
                    icona = Icona.Listen,
                    modifier = Modifier.testTag("voci-tra-parti-estratto-$b")
                        .semantics(mergeDescendants = true) { contentDescription = descrizioneAscoltaVoce(b) },
                )
                BottoneSn(
                    "Voce $a",
                    onClick = { azioni.riproduciEstratto(coppia.estrattoA) },
                    abilitato = pannello.estrattiDisponibili,
                    variante = VarianteBottone.Fantasma,
                    piccolo = true,
                    icona = Icona.Listen,
                    modifier = Modifier.testTag("voci-tra-parti-estratto-$a")
                        .semantics(mergeDescendants = true) { contentDescription = descrizioneAscoltaVoce(a) },
                )
            }
        },
    )
}
