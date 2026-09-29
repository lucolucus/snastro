package snastro.ui.progetti

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import snastro.progetto.applicazione.letture.ProgettoVista
import snastro.ui.SnastroTema
import snastro.ui.formattaData
import snastro.ui.stile.AzioneBanner
import snastro.ui.stile.BannerSn
import snastro.ui.stile.BottoneIconaSn
import snastro.ui.stile.BottoneSn
import snastro.ui.stile.Icona
import snastro.ui.stile.IconaSn
import snastro.ui.stile.LocalSnastroColori
import snastro.ui.stile.LocalSnastroTipografia
import snastro.ui.stile.SnastroMisure
import snastro.ui.stile.TipoBanner
import snastro.ui.stile.VarianteBottone
import snastro.ui.temaScuro
import snastro.ui.testi.DESCRIZIONE_NUOVO_PROGETTO
import snastro.ui.testi.ETICHETTA_APRI_PROGETTO
import snastro.ui.testi.ETICHETTA_ERRORE_CARICAMENTO_PROGETTI
import snastro.ui.testi.ETICHETTA_IMPOSTAZIONI
import snastro.ui.testi.ETICHETTA_NUOVO_PROGETTO
import snastro.ui.testi.ETICHETTA_PROGETTI
import snastro.ui.testi.ETICHETTA_RIPROVA
import snastro.ui.testi.ETICHETTA_SNASTRO
import snastro.ui.testi.MESSAGGIO_PROGETTI_VUOTO
import snastro.ui.testi.etichettaRegistrazioni
import snastro.ui.testi.sottotitoloProgetti
import java.time.ZoneId

private val LARGHEZZA_MINIMA_CARD = 232.dp
private val ALTEZZA_CARD = 144.dp
private val LATO_TESSERA_ICONA = 40.dp
private val DIMENSIONE_INDICATORE_PICCOLO = 18.dp

/**
 * Thin view of S1 · Progetti (RC-2): only renders [stato] and forwards [azioni]'s events — the folder
 * pickers are [sceltaCartella] (L464d), a consumer-owned port `:avvio` implements over a
 * window-owned `java.awt.FileDialog`, never a `JFileChooser` built by this composable itself; its
 * result always goes straight to a plain state update or an [azioni] lambda, never a decision beyond
 * null-cancelled. [cartellaGenitorePredefinita] (ADR 0010: `~/Documents/snastro`, or the folder saved in
 * Impostazioni) is `:avvio`'s own injected default for the new-project panel — fix-batch-12 #4: never
 * `System.getProperty` inside this composable.
 *
 * Home layout: a header (brand, title, "Apri progetto…" and — when [onImpostazioni] is wired — the Impostazioni
 * button), then a grid of cards whose FIRST card is always "Nuovo progetto" (it opens the new-project panel over
 * the grid), followed by one card per project, or the empty message when there are none. The panel is shown while
 * the user fills it, and whenever a `crea` failed ([ProgettiUiStato.Dati.erroreCrea] is shown inside it).
 * [nuovoProgettoAperto] is the panel's initial state (render-check/test knob).
 */
@Suppress("LongParameterList") // state + actions + the two injected ports + the Impostazioni hook + the knobs
@Composable
fun SchermataProgetti(
    stato: ProgettiUiStato,
    azioni: AzioniProgetti,
    cartellaGenitorePredefinita: String,
    sceltaCartella: SceltaCartella,
    onImpostazioni: (() -> Unit)? = null,
    nuovoProgettoAperto: Boolean = false,
    scuro: Boolean = temaScuro(),
    riduciMovimento: Boolean? = null,
) {
    SnastroTema(scuro = scuro, riduciMovimento = riduciMovimento) {
        Surface(modifier = Modifier.fillMaxSize(), color = LocalSnastroColori.current.surface) {
            when (stato) {
                ProgettiUiStato.Caricamento -> IndicatoreCaricamentoProgetti()
                is ProgettiUiStato.Dati -> ContenutoProgetti(
                    stato = stato,
                    azioni = azioni,
                    cartellaGenitorePredefinita = cartellaGenitorePredefinita,
                    sceltaCartella = sceltaCartella,
                    onImpostazioni = onImpostazioni,
                    nuovoProgettoAperto = nuovoProgettoAperto,
                )
            }
        }
    }
}

@Composable
private fun IndicatoreCaricamentoProgetti() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.testTag("progetti-indicatore-caricamento"))
    }
}

@Suppress("LongParameterList") // forwards SchermataProgetti's own inputs
@Composable
private fun ContenutoProgetti(
    stato: ProgettiUiStato.Dati,
    azioni: AzioniProgetti,
    cartellaGenitorePredefinita: String,
    sceltaCartella: SceltaCartella,
    onImpostazioni: (() -> Unit)?,
    nuovoProgettoAperto: Boolean,
) {
    var pannelloAperto by remember { mutableStateOf(nuovoProgettoAperto) }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = SnastroMisure.space6, start = SnastroMisure.space6, end = SnastroMisure.space6),
        ) {
            Intestazione(stato, azioni, sceltaCartella, onImpostazioni)
            // L530d: the INITIAL elenco load's own failure — distinct from erroreCrea/erroreApri (those
            // are per-action, dismissible inline messages), a full-width banner with a Riprova action.
            stato.erroreElenco?.let {
                Spacer(modifier = Modifier.height(SnastroMisure.space4))
                BannerSn(
                    tipo = TipoBanner.Errore,
                    titolo = ETICHETTA_ERRORE_CARICAMENTO_PROGETTI,
                    testo = it,
                    azione = AzioneBanner(ETICHETTA_RIPROVA, azioni.riprova),
                    modifier = Modifier.testTag("progetti-errore-elenco"),
                )
            }
            stato.erroreApri?.let { MessaggioInlineErrore(it, azioni.chiudiErroreApri, "progetti-errore-apri") }
            Spacer(modifier = Modifier.height(SnastroMisure.space5))
            GrigliaProgetti(stato, abilitato = !stato.inCorso, onNuovo = { pannelloAperto = true }, apri = azioni.apri)
        }
        if (pannelloAperto || stato.erroreCrea != null) {
            PannelloNuovoProgetto(
                inCorso = stato.inCorso,
                erroreCrea = stato.erroreCrea,
                azioni = azioni,
                cartellaGenitorePredefinita = cartellaGenitorePredefinita,
                sceltaCartella = sceltaCartella,
                onChiudi = {
                    pannelloAperto = false
                    azioni.chiudiErroreCrea()
                },
            )
        }
    }
}

@Composable
private fun Intestazione(
    stato: ProgettiUiStato.Dati,
    azioni: AzioniProgetti,
    sceltaCartella: SceltaCartella,
    onImpostazioni: (() -> Unit)?,
) {
    val colori = LocalSnastroColori.current
    val tipografia = LocalSnastroTipografia.current
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SnastroMisure.space1),
            ) {
                IconaSn(Icona.Reel, descrizione = null, tinta = colori.accentInk, dimensione = SnastroMisure.iconS)
                Text(text = ETICHETTA_SNASTRO, style = tipografia.overline, color = colori.accentInk)
            }
            Spacer(modifier = Modifier.height(SnastroMisure.space1))
            Text(text = ETICHETTA_PROGETTI, style = tipografia.display, color = colori.ink)
            Text(
                text = sottotitoloProgetti(stato.progetti.size),
                style = tipografia.caption,
                color = colori.inkMuted,
                modifier = Modifier.testTag("progetti-sottotitolo"),
            )
        }
        if (stato.inCorso) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(end = SnastroMisure.space3)
                    .size(DIMENSIONE_INDICATORE_PICCOLO)
                    .testTag("progetti-operazione-in-corso"),
            )
        }
        BottoneSn(
            etichetta = ETICHETTA_APRI_PROGETTO,
            onClick = { sceltaCartella.scegli(ETICHETTA_APRI_PROGETTO)?.let { azioni.apri(it) } },
            variante = VarianteBottone.Secondario,
            icona = Icona.Import,
            abilitato = !stato.inCorso,
            modifier = Modifier.testTag("progetti-apri"),
        )
        if (onImpostazioni != null) {
            Spacer(modifier = Modifier.width(SnastroMisure.space2))
            BottoneIconaSn(
                icona = Icona.Settings,
                descrizione = ETICHETTA_IMPOSTAZIONI,
                onClick = onImpostazioni,
                modifier = Modifier.testTag("progetti-impostazioni"),
            )
        }
    }
}

/**
 * The grid: "Nuovo progetto" first, then one card per project (ElencoProgetti's own order, most recent first), or
 * the AC-192 empty message spanning the row. The grid itself scrolls; the header stays.
 */
@Composable
private fun GrigliaProgetti(
    stato: ProgettiUiStato.Dati,
    abilitato: Boolean,
    onNuovo: () -> Unit,
    apri: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(LARGHEZZA_MINIMA_CARD),
        horizontalArrangement = Arrangement.spacedBy(SnastroMisure.space4),
        verticalArrangement = Arrangement.spacedBy(SnastroMisure.space4),
        contentPadding = PaddingValues(bottom = SnastroMisure.space6),
        modifier = Modifier
            .fillMaxSize()
            .testTag(if (stato.progetti.isEmpty()) "progetti-griglia" else "progetti-lista"),
    ) {
        item(key = "nuovo") { CardNuovoProgetto(abilitato, onNuovo) }
        if (stato.progetti.isEmpty()) {
            item(key = "vuoto", span = { GridItemSpan(maxLineSpan) }) { ProgettiVuoto() }
        } else {
            items(stato.progetti, key = { it.progettoId.valore }) { CardProgetto(it, abilitato, apri) }
        }
    }
}

/** The grid's card chrome: [sfondo] (`raised`), 1dp `line` (`lineStrong` on hover), `radiusCard`, fixed height. */
@Composable
private fun CardGriglia(
    abilitato: Boolean,
    onClick: () -> Unit,
    tag: String,
    sfondo: Color,
    contenuto: @Composable () -> Unit,
) {
    val colori = LocalSnastroColori.current
    val interazione = remember { MutableInteractionSource() }
    val hover by interazione.collectIsHoveredAsState()
    val forma = RoundedCornerShape(SnastroMisure.radiusCard)
    Surface(
        shape = forma,
        color = sfondo,
        border = BorderStroke(1.dp, if (hover && abilitato) colori.lineStrong else colori.line),
        modifier = Modifier
            .fillMaxWidth()
            .height(ALTEZZA_CARD)
            .clip(forma)
            .hoverable(interazione)
            .clickable(enabled = abilitato, interactionSource = interazione, indication = null, onClick = onClick)
            .testTag(tag),
    ) {
        Box(modifier = Modifier.padding(SnastroMisure.space4)) { contenuto() }
    }
}

@Composable
private fun TesseraIcona(icona: Icona, sfondo: Color, tinta: Color) {
    Box(
        modifier = Modifier
            .size(LATO_TESSERA_ICONA)
            .background(sfondo, RoundedCornerShape(SnastroMisure.radiusControl)),
        contentAlignment = Alignment.Center,
    ) {
        IconaSn(icona, descrizione = null, tinta = tinta, dimensione = SnastroMisure.iconM)
    }
}

@Composable
private fun CardNuovoProgetto(abilitato: Boolean, onNuovo: () -> Unit) {
    val colori = LocalSnastroColori.current
    val tipografia = LocalSnastroTipografia.current
    CardGriglia(abilitato = abilitato, onClick = onNuovo, tag = "progetti-nuovo", sfondo = colori.accentSoft) {
        Column(modifier = Modifier.fillMaxSize()) {
            TesseraIcona(Icona.Plus, sfondo = colori.accent, tinta = colori.onAccent)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = ETICHETTA_NUOVO_PROGETTO,
                style = tipografia.heading.copy(fontWeight = FontWeight.SemiBold),
                color = colori.ink,
            )
            Text(text = DESCRIZIONE_NUOVO_PROGETTO, style = tipografia.caption, color = colori.inkMuted)
        }
    }
}

/** AC-573/AC-198: one project = one card (name `heading`, "n registrazioni · ultima attività" `caption`). */
@Composable
private fun CardProgetto(progetto: ProgettoVista, abilitato: Boolean, apri: (String) -> Unit) {
    val colori = LocalSnastroColori.current
    val tipografia = LocalSnastroTipografia.current
    CardGriglia(
        abilitato = abilitato,
        onClick = { apri(progetto.percorso) },
        tag = "progetti-riga-${progetto.progettoId.valore}",
        sfondo = colori.raised,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TesseraIcona(Icona.Reel, sfondo = colori.sunken, tinta = colori.accentInk)
                Spacer(modifier = Modifier.weight(1f))
                IconaSn(
                    Icona.ChevronRight,
                    descrizione = null,
                    tinta = colori.inkFaint,
                    dimensione = SnastroMisure.iconS,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = progetto.nome,
                style = tipografia.heading.copy(fontWeight = FontWeight.SemiBold),
                color = colori.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val dataUltimaAttivita = formattaData(progetto.ultimaAttivita.atZone(ZoneId.systemDefault()).toLocalDate())
            Text(
                text = "${etichettaRegistrazioni(progetto.numRegistrazioni)} · $dataUltimaAttivita",
                style = tipografia.caption,
                color = colori.inkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** AC-192: the empty message, under the always-present "Nuovo progetto" card. */
@Composable
private fun ProgettiVuoto() {
    val colori = LocalSnastroColori.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = SnastroMisure.space2).testTag("progetti-vuoto"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SnastroMisure.space2),
    ) {
        IconaSn(Icona.Reel, descrizione = null, tinta = colori.inkFaint, dimensione = SnastroMisure.iconM)
        Text(text = MESSAGGIO_PROGETTI_VUOTO, style = LocalSnastroTipografia.current.body, color = colori.inkMuted)
    }
}
