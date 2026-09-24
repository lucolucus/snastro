package snastro.ui.registrazione

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import snastro.ui.testi.ETICHETTA_ANNULLA
import snastro.ui.testi.ETICHETTA_APPLICA
import snastro.ui.testi.ETICHETTA_CHIUDI
import snastro.ui.testi.ETICHETTA_CHIUDI_ERRORE
import snastro.ui.testi.ETICHETTA_RIASSEGNA_SOMIGLIANZA
import snastro.ui.testi.ETICHETTA_RICALCOLA
import snastro.ui.testi.MESSAGGIO_COMANDO_IN_ATTESA

private val SPAZIO = 8.dp
private val ALTEZZA_MASSIMA_RIGHE = 120.dp

/**
 * Thin view of the 'Riassegna per somiglianza' area in the Voci panel header (ADR 0019 §6 + Amendment
 * (b).2/(b).6, RC-2): renders [p] — every text and enabled flag is the presenter's — and forwards
 * [azioni]. The preview lines scroll inside a capped height (sizing at 1024x640).
 */
@Composable
internal fun SezioneSomiglianza(p: PannelloSomiglianza, azioni: AzioniRegistrazione) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = SPAZIO).testTag("somiglianza")) {
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
    OutlinedButton(
        onClick = azioni.calcolaSomiglianza,
        enabled = p.abilitato,
        modifier = Modifier.testTag("somiglianza-avvia"),
    ) { Text(ETICHETTA_RIASSEGNA_SOMIGLIANZA) }
    p.suggerimento?.let { Riga(it, "somiglianza-suggerimento") }
}

/** AC-531: 'Confronto le frasi… n di N' + a determinate bar; past the threshold the wait line; 'Annulla'. */
@Composable
private fun Calcolo(fase: FaseSomiglianza.Calcolo, azioni: AzioniRegistrazione) {
    Text(fase.testo, modifier = Modifier.testTag("somiglianza-calcolo"))
    val avanzamento = if (fase.totale > 0) fase.fatti.toFloat() / fase.totale else 0f
    LinearProgressIndicator(
        progress = { avanzamento },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("somiglianza-barra"),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (fase.inAttesa) {
            Text(
                MESSAGGIO_COMANDO_IN_ATTESA,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f).testTag("somiglianza-in-attesa"),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        TextButton(onClick = azioni.annullaSomiglianza, modifier = Modifier.testTag("somiglianza-annulla")) {
            Text(ETICHETTA_ANNULLA)
        }
    }
}

/** AC-545/AC-546: the preview — title, one line per group, 'Applica' / 'Annulla' (or 'Chiudi' when N = 0). */
@Composable
private fun Anteprima(fase: FaseSomiglianza.Anteprima, azioni: AzioniRegistrazione) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(SPAZIO),
        modifier = Modifier.fillMaxWidth().testTag("somiglianza-anteprima"),
    ) {
        Column(modifier = Modifier.padding(SPAZIO)) {
            Text(
                fase.titolo,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("somiglianza-anteprima-titolo"),
            )
            Column(modifier = Modifier.heightIn(max = ALTEZZA_MASSIMA_RIGHE).verticalScroll(rememberScrollState())) {
                fase.righe.forEachIndexed { i, riga ->
                    Text(
                        riga,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("somiglianza-riga-$i"),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                if (fase.applicabile) {
                    TextButton(
                        onClick = azioni.annullaSomiglianza,
                        enabled = !fase.inApplicazione,
                        modifier = Modifier.testTag("somiglianza-annulla"),
                    ) { Text(ETICHETTA_ANNULLA) }
                    Spacer(Modifier.width(SPAZIO))
                    Button(
                        onClick = azioni.applicaSomiglianza,
                        enabled = !fase.inApplicazione,
                        modifier = Modifier.testTag("somiglianza-applica"),
                    ) { Text(ETICHETTA_APPLICA) }
                } else {
                    TextButton(onClick = azioni.annullaSomiglianza, modifier = Modifier.testTag("somiglianza-chiudi")) {
                        Text(ETICHETTA_CHIUDI)
                    }
                }
            }
        }
    }
}

/** AC-533/AC-547: the dismissible result or error line; [conRicalcola] ≠ `null` shows 'Ricalcola' (enabled or not). */
@Composable
private fun Messaggio(testo: String, errore: Boolean, conRicalcola: Boolean?, azioni: AzioniRegistrazione) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("somiglianza-messaggio")) {
        Text(
            testo,
            color = if (errore) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        conRicalcola?.let { abilitato ->
            TextButton(
                onClick = azioni.calcolaSomiglianza,
                enabled = abilitato,
                modifier = Modifier.testTag("somiglianza-ricalcola"),
            ) { Text(ETICHETTA_RICALCOLA) }
        }
        Text(
            ETICHETTA_CHIUDI_ERRORE,
            modifier = Modifier.padding(start = SPAZIO).clickable(onClick = azioni.annullaSomiglianza)
                .testTag("somiglianza-messaggio-chiudi"),
        )
    }
}

@Composable
private fun Riga(testo: String, tag: String) {
    Text(
        testo,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp).testTag(tag),
    )
}
