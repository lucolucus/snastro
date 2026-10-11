package snastro.ui.registrazione

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import snastro.kernel.RegistrazioneId
import snastro.ui.formattaData
import snastro.ui.formattaDurataEstesa
import snastro.ui.stile.BottoneIconaSn
import snastro.ui.stile.BottoneSn
import snastro.ui.stile.Icona
import snastro.ui.stile.LocalSnastroColori
import snastro.ui.stile.LocalSnastroTipografia
import snastro.ui.stile.SchedeSn
import snastro.ui.stile.SnastroMisure
import snastro.ui.testi.ETICHETTA_APRI_SBOBINATURA
import snastro.ui.testi.ETICHETTA_BRICIOLA_REGISTRAZIONI
import snastro.ui.testi.ETICHETTA_MOSTRA_CARTELLA
import snastro.ui.testi.etichettaParte
import snastro.ui.testi.testoBriciolaIncontro
import snastro.ui.testi.testoParteDi
import snastro.ui.testi.testoPersone
import java.time.format.DateTimeFormatter

private val LARGHEZZA_SELETTORE_PARTE_PER_PARTE = 96.dp
private val FORMATO_ORA: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
internal fun IntestazioneRegistrazione(stato: RegistrazioneUiStato.Dati, azioni: AzioniRegistrazione) {
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
internal fun BriciolaParte(parte: IntestazioneParte?) {
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
internal fun SelettoreParte(parte: IntestazioneParte, vaiAllaParte: (RegistrazioneId) -> Unit) {
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
