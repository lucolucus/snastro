// TooManyFunctions: one screen split into many small, single-purpose composables (RC-2 thin view) —
// the natural shape of a sidebar with a project selector, nav items and a footer, plus the S1 slot.
@file:Suppress("TooManyFunctions")

package snastro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import snastro.ui.stile.BottoneIconaSn
import snastro.ui.stile.Icona
import snastro.ui.stile.IconaSn
import snastro.ui.stile.LocalSnastroColori
import snastro.ui.stile.LocalSnastroTipografia
import snastro.ui.stile.SnastroMisure
import snastro.ui.testi.ETICHETTA_CHIUDI_ERRORE
import snastro.ui.testi.ETICHETTA_CHIUDI_PROGETTO
import snastro.ui.testi.ETICHETTA_IMPOSTAZIONI
import snastro.ui.testi.ETICHETTA_TUTTO_IN_LOCALE
import snastro.ui.testi.etichetta

private val PADDING_MESSAGGIO = 24.dp
private val ALTEZZA_VOCE_NAV = 34.dp
private val PADDING_ORIZZONTALE_PROGETTO = 10.dp
private val PADDING_VERTICALE_PROGETTO = 8.dp
private val PADDING_ORIZZONTALE_VOCE = 10.dp

/**
 * Thin view of the app shell (RC-2): only renders [stato] and forwards [azioni]'s events. `contenuto`
 * hosts the screen of the selected section (or S1 when no Progetto is open) — later `ui` blocks plug
 * their real screens into these slots. AC-572: window `ground`, a 232dp sidebar with the project
 * selector + nav + footer, content on `surface` (each plugged-in screen supplies its own padding).
 * [impostazioniSelezionate] (rework cycle 2): the composition root's S5 is on screen — the footer is then the
 * highlighted place, no nav item is, and any nav click leaves S5 (via [onRegistrazioniSelezionata]).
 * [statoModelloLinguisticoPiede] (AC-S33, ADR 0025): the optional model's download line, already
 * formatted by `snastro.ui.modelli.ModelliPresenter.etichettaModelloLinguisticoPiede` — `null` renders
 * no line (`NonInstallato`/`Installato`/no optional entry at all); this composable decides nothing
 * about WHEN to show it (RC-2).
 * [scuro]/[riduciMovimento] are render-check/test knobs (AC-571-style) — every existing call site
 * (production `ShellRoute`, every prior test) keeps the exact previous behaviour via these defaults.
 */
// one parameter per documented slot/knob (content slots + nav hooks + render-check knobs)
@Suppress("LongParameterList")
@Composable
fun SchermataShell(
    stato: ShellUiStato,
    azioni: AzioniShell,
    contenutoSenzaProgetto: @Composable () -> Unit = {},
    contenuto: @Composable (ShellUiStato.ConProgetto) -> Unit = {},
    onRegistrazioniSelezionata: (() -> Unit)? = null,
    onImpostazioni: (() -> Unit)? = null,
    impostazioniSelezionate: Boolean = false,
    statoModelloLinguisticoPiede: String? = null,
    scuro: Boolean = temaScuro(),
    riduciMovimento: Boolean? = null,
) {
    SnastroTema(scuro = scuro, riduciMovimento = riduciMovimento) {
        val colori = LocalSnastroColori.current
        Surface(modifier = Modifier.fillMaxSize(), color = colori.ground) {
            when (stato) {
                // H1: the error is an overlay banner, never a replacement of S1 / the nav — see
                // BannerErroreApertura.
                is ShellUiStato.SenzaProgetto ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        contenutoSenzaProgetto()
                        stato.erroreApertura?.let { BannerErroreApertura(it, azioni.chiudiErrore) }
                    }
                ShellUiStato.Caricamento -> IndicatoreCaricamento()
                is ShellUiStato.ConProgetto ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            NavigazioneShell(
                                stato = stato,
                                azioni = azioni,
                                onRegistrazioniSelezionata = onRegistrazioniSelezionata,
                                onImpostazioni = onImpostazioni,
                                impostazioniSelezionate = impostazioniSelezionate,
                                statoModelloLinguisticoPiede = statoModelloLinguisticoPiede,
                                modifier = Modifier.width(SnastroMisure.sidebar).fillMaxHeight(),
                            )
                            Box(modifier = Modifier.weight(1f).fillMaxHeight()) { contenuto(stato) }
                        }
                        stato.erroreApertura?.let { BannerErroreApertura(it, azioni.chiudiErrore) }
                    }
            }
        }
    }
}

@Composable
private fun IndicatoreCaricamento() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.testTag("shell-indicatore-caricamento"))
    }
}

/**
 * Dismissible banner over the current state (H1): [SchermataShell] overlays it on S1 or on the
 * ConProgetto nav, it never replaces either — `chiudiErrore` (AC-181) is always reachable. Styled with
 * the design tokens directly (`dangerSoft`/`danger`) rather than `BannerSn`: a one-line dismissible
 * strip has no title to give it (`BannerSn` always needs one), so a bespoke small banner is the
 * frugal choice here (rung 6) instead of forcing a fake heading onto the shared component.
 */
@Composable
private fun BannerErroreApertura(messaggio: String, onChiudi: () -> Unit) {
    val colori = LocalSnastroColori.current
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Surface(
            color = colori.dangerSoft,
            shape = RoundedCornerShape(SnastroMisure.radiusCard),
            modifier = Modifier.padding(PADDING_MESSAGGIO).testTag("shell-errore-apertura"),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(SnastroMisure.space4),
            ) {
                IconaSn(Icona.Alert, descrizione = null, tinta = colori.danger, dimensione = SnastroMisure.iconM)
                Spacer(modifier = Modifier.width(SnastroMisure.space2))
                Text(
                    text = messaggio,
                    color = colori.danger,
                    style = LocalSnastroTipografia.current.body,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = ETICHETTA_CHIUDI_ERRORE,
                    color = colori.accentInk,
                    style = LocalSnastroTipografia.current.label,
                    modifier = Modifier
                        .padding(start = SnastroMisure.space3)
                        .clickable(onClick = onChiudi)
                        .testTag("shell-chiudi-errore"),
                )
            }
        }
    }
}

/** AC-572: 232dp sidebar on `ground`, 1dp `line` right border, `space3` padding, `space1` gap. */
@Suppress("LongParameterList") // state + actions + the composition root's nav hooks + modifier
@Composable
private fun NavigazioneShell(
    stato: ShellUiStato.ConProgetto,
    azioni: AzioniShell,
    onRegistrazioniSelezionata: (() -> Unit)?,
    onImpostazioni: (() -> Unit)?,
    impostazioniSelezionate: Boolean,
    statoModelloLinguisticoPiede: String?,
    modifier: Modifier = Modifier,
) {
    val colori = LocalSnastroColori.current
    Column(
        modifier = modifier
            .background(colori.ground)
            .bordoDestro(colori.line)
            .padding(SnastroMisure.space3),
        verticalArrangement = Arrangement.spacedBy(SnastroMisure.space1),
    ) {
        SelettoreProgetto(nome = stato.progetto.nome, onChiudi = azioni.chiudi)
        DestinazioneShell.entries.filter { it in stato.destinazioniDisponibili }.forEach { destinazione ->
            val giaSelezionata = destinazione == stato.destinazioneSelezionata
            VoceNavigazione(
                destinazione = destinazione,
                selezionata = giaSelezionata && !impostazioniSelezionate,
                onClick = {
                    azioni.seleziona(destinazione)
                    // Re-clicking the ALREADY-selected 'Registrazioni' item goes back to its own top, and
                    // ANY nav click out of S5 leaves S5 for good (no hidden S5 left behind for the next
                    // 'Registrazioni' click) — switching INTO Registrazioni from Parlanti otherwise
                    // preserves the place it was left at (the shell's own "keeps where the user was").
                    val tornaAllElenco = impostazioniSelezionate ||
                        (destinazione == DestinazioneShell.REGISTRAZIONI && giaSelezionata)
                    if (tornaAllElenco) onRegistrazioniSelezionata?.invoke()
                },
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        PiedeSidebar(
            onImpostazioni = onImpostazioni,
            selezionato = impostazioniSelezionate,
            statoModelloLinguisticoPiede = statoModelloLinguisticoPiede,
        )
    }
}

/**
 * AC-572/rework cycle 1 (HIGH #2): Reel icon (`accentInk`) + project name (`heading`, ellipsised) —
 * no longer clickable itself (a bare name + a bare chevron gave no visible affordance and silently
 * closed the project). "Chiudi progetto" is now its own `BottoneIcona Close` at the row's end.
 */
@Composable
private fun SelettoreProgetto(nome: String, onChiudi: () -> Unit) {
    val colori = LocalSnastroColori.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PADDING_ORIZZONTALE_PROGETTO, vertical = PADDING_VERTICALE_PROGETTO)
            .testTag("shell-selettore-progetto"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SnastroMisure.space2),
    ) {
        IconaSn(Icona.Reel, descrizione = null, tinta = colori.accentInk, dimensione = SnastroMisure.iconM)
        Text(
            text = nome,
            style = LocalSnastroTipografia.current.heading.copy(fontWeight = FontWeight.SemiBold),
            color = colori.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        BottoneIconaSn(
            icona = Icona.Close,
            descrizione = ETICHETTA_CHIUDI_PROGETTO,
            onClick = onChiudi,
            piccolo = true,
            modifier = Modifier.testTag("shell-chiudi-progetto"),
        )
    }
}

/** AC-572: 34dp nav item; active = `raised` fill + `line` outline + `accentInk` icon + 600 weight. */
@Composable
private fun VoceNavigazione(destinazione: DestinazioneShell, selezionata: Boolean, onClick: () -> Unit) {
    val colori = LocalSnastroColori.current
    val icona = when (destinazione) {
        DestinazioneShell.REGISTRAZIONI -> Icona.Waveform
        DestinazioneShell.PARLANTI -> Icona.People
    }
    val sfondo = if (selezionata) colori.raised else Color.Transparent
    val coloreIcona = if (selezionata) colori.accentInk else colori.inkMuted
    val peso = if (selezionata) FontWeight.SemiBold else FontWeight.Normal
    val forma = RoundedCornerShape(SnastroMisure.radiusControl)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ALTEZZA_VOCE_NAV)
            .clip(forma)
            .background(sfondo, forma)
            .let { if (selezionata) it.border(1.dp, colori.line, forma) else it }
            .clickable(onClick = onClick)
            .padding(horizontal = PADDING_ORIZZONTALE_VOCE)
            .testTag("shell-nav-${destinazione.name.lowercase()}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SnastroMisure.space2),
    ) {
        IconaSn(icona, descrizione = null, tinta = coloreIcona, dimensione = SnastroMisure.iconM)
        Text(
            text = etichetta(destinazione),
            style = LocalSnastroTipografia.current.body.copy(fontWeight = peso),
            color = colori.ink,
        )
    }
}

/**
 * The sidebar's footer (AC-572, reworked for Impostazioni): the Impostazioni entry, styled as a nav item so it is
 * found at a glance (S5 is its 'Modelli e licenze' section; [selezionato] gives it the active style while
 * Impostazioni is shown), then the 'Tutto in locale' privacy line (no readiness claim the shell state cannot back).
 * Unwired ([onImpostazioni] `null`) it is just the privacy line. [statoModelloLinguisticoPiede] (AC-S33, ADR 0025):
 * a line ABOVE the entry, only while the optional model is downloading — never for `NonInstallato`/`Installato`
 * (the mapping to `null` already excludes those, see [SchermataShell]'s KDoc).
 */
@Composable
private fun PiedeSidebar(
    onImpostazioni: (() -> Unit)?,
    selezionato: Boolean,
    statoModelloLinguisticoPiede: String? = null,
) {
    val colori = LocalSnastroColori.current
    val tipografia = LocalSnastroTipografia.current
    val forma = RoundedCornerShape(SnastroMisure.radiusControl)
    Column(verticalArrangement = Arrangement.spacedBy(SnastroMisure.space2)) {
        if (statoModelloLinguisticoPiede != null) {
            Text(
                text = statoModelloLinguisticoPiede,
                style = tipografia.caption,
                color = colori.inkMuted,
                modifier = Modifier.padding(horizontal = PADDING_ORIZZONTALE_VOCE)
                    .testTag("shell-piede-modello-linguistico"),
            )
        }
        if (onImpostazioni != null) {
            // The Impostazioni entry: the SAME chrome as a nav item (VoceNavigazione), so it reads as a place to go.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ALTEZZA_VOCE_NAV)
                    .clip(forma)
                    .let {
                        if (selezionato) it.background(colori.raised, forma).border(1.dp, colori.line, forma) else it
                    }
                    .clickable(onClick = onImpostazioni)
                    .padding(horizontal = PADDING_ORIZZONTALE_VOCE)
                    .testTag("shell-piede"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SnastroMisure.space2),
            ) {
                IconaSn(
                    Icona.Settings,
                    descrizione = null,
                    tinta = if (selezionato) colori.accentInk else colori.inkMuted,
                    dimensione = SnastroMisure.iconM,
                )
                Text(
                    text = ETICHETTA_IMPOSTAZIONI,
                    style = tipografia.body.copy(
                        fontWeight = if (selezionato) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    color = colori.ink,
                )
            }
        }
        Text(
            text = ETICHETTA_TUTTO_IN_LOCALE,
            style = tipografia.caption,
            color = colori.inkFaint,
            modifier = Modifier.padding(horizontal = PADDING_ORIZZONTALE_VOCE)
                .let { if (onImpostazioni == null) it.testTag("shell-piede") else it },
        )
    }
}

/** A 1dp line on the trailing edge only — the sidebar's own separator from the content area. */
private fun Modifier.bordoDestro(colore: Color): Modifier = drawWithContent {
    drawContent()
    drawLine(
        color = colore,
        start = Offset(size.width, 0f),
        end = Offset(size.width, size.height),
        strokeWidth = 1.dp.toPx(),
    )
}
