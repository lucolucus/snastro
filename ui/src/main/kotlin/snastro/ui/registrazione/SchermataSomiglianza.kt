package snastro.ui.registrazione

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import snastro.ui.stile.BottoneSn
import snastro.ui.stile.CardSn
import snastro.ui.stile.LocalSnastroColori
import snastro.ui.stile.LocalSnastroTipografia
import snastro.ui.stile.SnastroMisure
import snastro.ui.stile.VarianteBottone
import snastro.ui.testi.ETICHETTA_ANNULLA
import snastro.ui.testi.ETICHETTA_APPLICA
import snastro.ui.testi.ETICHETTA_CALCOLA
import snastro.ui.testi.ETICHETTA_CHIUDI
import snastro.ui.testi.ETICHETTA_CHIUDI_ERRORE
import snastro.ui.testi.ETICHETTA_RIASSEGNA_SOMIGLIANZA
import snastro.ui.testi.ETICHETTA_RICALCOLA
import snastro.ui.testi.MESSAGGIO_COMANDO_IN_ATTESA

private val ALTEZZA_MASSIMA_RIGHE = 120.dp
private val ALTEZZA_BARRA_PROGRESSO = 6.dp

/**
 * Thin view of the 'Riassegna per somiglianza' area in the Voci panel header (ADR 0019 §6 + Amendment
 * (b).2/(b).6, RC-2, AC-584): a CardSn — its own title heading, then EITHER the Secondario 'Calcola'
 * trigger OR the running/preview state — renders [p] (every text and enabled flag is the presenter's)
 * and forwards [azioni]. The preview lines scroll inside a capped height (sizing at 1024x640).
 */
@Composable
internal fun SezioneSomiglianza(p: PannelloSomiglianza, azioni: AzioniRegistrazione) {
    val colori = LocalSnastroColori.current
    val tipografia = LocalSnastroTipografia.current
    CardSn(modifier = Modifier.fillMaxWidth().testTag("somiglianza")) {
        Text(text = ETICHETTA_RIASSEGNA_SOMIGLIANZA, style = tipografia.heading, color = colori.ink)
        Spacer(Modifier.height(SnastroMisure.space2))
        when (val fase = p.fase) {
            is FaseSomiglianza.Calcolo -> Calcolo(fase, azioni)
            is FaseSomiglianza.Anteprima -> Anteprima(fase, azioni)
            else -> Pulsante(p, azioni)
        }
        p.riferimenti?.let { Riga(it, "somiglianza-riferimenti") }
        p.avvisoTuttaLaVoce?.let { Riga(it, "somiglianza-avviso") }
        p.nonToccate?.let { Riga(it, "somiglianza-non-toccate") }
        when (val fase = p.fase) {
            is FaseSomiglianza.Esito -> Messaggio(fase.testo, errore = false, conRicalcola = null, azioni)
            is FaseSomiglianza.Errore ->
                Messaggio(fase.testo, errore = true, conRicalcola = p.abilitato.takeIf { fase.ricalcola }, azioni)
            else -> Unit
        }
    }
}

@Composable
private fun Pulsante(p: PannelloSomiglianza, azioni: AzioniRegistrazione) {
    BottoneSn(
        ETICHETTA_CALCOLA,
        onClick = azioni.calcolaSomiglianza,
        abilitato = p.abilitato,
        variante = VarianteBottone.Secondario,
        piccolo = true,
        modifier = Modifier.testTag("somiglianza-avvia"),
    )
    p.suggerimento?.let { Riga(it, "somiglianza-suggerimento") }
}

/** AC-531/AC-587: 'Confronto le frasi… n di N' + a determinate ProgressBar; past the threshold the wait
 * line; 'Annulla'. */
@Composable
private fun Calcolo(fase: FaseSomiglianza.Calcolo, azioni: AzioniRegistrazione) {
    val colori = LocalSnastroColori.current
    Text(
        fase.testo,
        style = LocalSnastroTipografia.current.body,
        color = colori.ink,
        modifier = Modifier.testTag("somiglianza-calcolo"),
    )
    Spacer(Modifier.height(SnastroMisure.space1))
    val avanzamento = if (fase.totale > 0) fase.fatti.toFloat() / fase.totale else 0f
    // A plain two-`Box` bar, not `LinearProgressIndicator`: this Material3 version's determinate
    // indicator carries its own built-in transition (no `LocalRiduciMovimento` hook to pin it off) —
    // `AC-587`'s bar is static art, not a component that needs live progress semantics here.
    Box(
        modifier = Modifier.fillMaxWidth().height(ALTEZZA_BARRA_PROGRESSO).clip(CircleShape)
            .background(colori.sunken).testTag("somiglianza-barra"),
    ) {
        Box(modifier = Modifier.fillMaxWidth(avanzamento).fillMaxHeight().clip(CircleShape).background(colori.accent))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (fase.inAttesa) {
            Text(
                MESSAGGIO_COMANDO_IN_ATTESA,
                style = LocalSnastroTipografia.current.caption,
                color = colori.inkMuted,
                modifier = Modifier.weight(1f).testTag("somiglianza-in-attesa"),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        BottoneSn(
            ETICHETTA_ANNULLA,
            onClick = azioni.annullaSomiglianza,
            variante = VarianteBottone.Link,
            modifier = Modifier.testTag("somiglianza-annulla"),
        )
    }
}

/** AC-545/AC-546/AC-587: the preview — title, one line per group (scrolls past a capped height), then
 * the references caption, then 'Applica'/'Annulla' (or 'Chiudi' when N = 0). */
@Composable
private fun Anteprima(fase: FaseSomiglianza.Anteprima, azioni: AzioniRegistrazione) {
    val colori = LocalSnastroColori.current
    Column(
        verticalArrangement = Arrangement.spacedBy(SnastroMisure.space2),
        modifier = Modifier.testTag("somiglianza-anteprima"),
    ) {
        Text(
            fase.titolo,
            color = colori.ink,
            style = LocalSnastroTipografia.current.body,
            modifier = Modifier.testTag("somiglianza-anteprima-titolo"),
        )
        Column(modifier = Modifier.heightIn(max = ALTEZZA_MASSIMA_RIGHE).verticalScroll(rememberScrollState())) {
            fase.righe.forEachIndexed { i, riga ->
                Text(
                    riga,
                    color = colori.ink,
                    style = LocalSnastroTipografia.current.caption.copy(
                        fontFamily = LocalSnastroTipografia.current.timecode.fontFamily,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("somiglianza-riga-$i"),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            if (fase.applicabile) {
                BottoneSn(
                    ETICHETTA_ANNULLA,
                    onClick = azioni.annullaSomiglianza,
                    abilitato = !fase.inApplicazione,
                    variante = VarianteBottone.Secondario,
                    piccolo = true,
                    modifier = Modifier.testTag("somiglianza-annulla"),
                )
                Spacer(Modifier.width(SnastroMisure.space2))
                BottoneSn(
                    ETICHETTA_APPLICA,
                    onClick = azioni.applicaSomiglianza,
                    abilitato = !fase.inApplicazione,
                    variante = VarianteBottone.Primario,
                    piccolo = true,
                    modifier = Modifier.testTag("somiglianza-applica"),
                )
            } else {
                BottoneSn(
                    ETICHETTA_CHIUDI,
                    onClick = azioni.annullaSomiglianza,
                    variante = VarianteBottone.Secondario,
                    piccolo = true,
                    modifier = Modifier.testTag("somiglianza-chiudi"),
                )
            }
        }
    }
}

/** AC-533/AC-547: the dismissible result/error line; [conRicalcola] ≠ `null` shows 'Ricalcola' (enabled or not). */
@Composable
private fun Messaggio(
    testo: String,
    errore: Boolean,
    conRicalcola: Boolean?,
    azioni: AzioniRegistrazione,
) {
    val colori = LocalSnastroColori.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("somiglianza-messaggio")) {
        Text(
            testo,
            color = if (errore) colori.danger else colori.ink,
            style = LocalSnastroTipografia.current.caption,
            modifier = Modifier.weight(1f),
        )
        conRicalcola?.let { abilitato ->
            BottoneSn(
                ETICHETTA_RICALCOLA,
                onClick = azioni.calcolaSomiglianza,
                abilitato = abilitato,
                variante = VarianteBottone.Link,
                modifier = Modifier.testTag("somiglianza-ricalcola"),
            )
        }
        Text(
            ETICHETTA_CHIUDI_ERRORE,
            style = LocalSnastroTipografia.current.label,
            color = colori.accentInk,
            modifier = Modifier.padding(start = SnastroMisure.space2).clickable(onClick = azioni.annullaSomiglianza)
                .testTag("somiglianza-messaggio-chiudi"),
        )
    }
}

@Composable
private fun Riga(testo: String, tag: String) {
    Text(
        testo,
        style = LocalSnastroTipografia.current.caption,
        color = LocalSnastroColori.current.inkMuted,
        modifier = Modifier.padding(top = 2.dp).testTag(tag),
    )
}
