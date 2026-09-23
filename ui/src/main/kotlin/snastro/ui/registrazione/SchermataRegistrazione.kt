// TooManyFunctions: one screen split into many small, single-purpose composables (RC-2 thin view) —
// header, skeleton, empty/error states and the per-Segmento row each deserve their own function.
@file:Suppress("TooManyFunctions")

package snastro.ui.registrazione

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import snastro.ui.SnastroTema
import snastro.ui.formattaData
import snastro.ui.formattaDurata
import snastro.ui.lettore.BarraLettore
import snastro.ui.palette
import snastro.ui.testi.ETICHETTA_APRI_DOCUMENTO
import snastro.ui.testi.ETICHETTA_CHIUDI_ERRORE
import snastro.ui.testi.ETICHETTA_MOSTRA_CARTELLA
import snastro.ui.testi.ETICHETTA_RIPROVA
import snastro.ui.testi.MESSAGGIO_TRASCRITTO_VUOTO

private val PADDING_SCHERMO = 24.dp
private val PADDING_SEZIONE = 16.dp
private val PADDING_RIGA = 8.dp
private val DIMENSIONE_PALLINO = 10.dp
private val ALTEZZA_RIGA_SCHELETRO = 16.dp
private const val RIGHE_SCHELETRO = 4
private const val LARGHEZZA_SCHELETRO_PARI = 0.8f
private const val LARGHEZZA_SCHELETRO_DISPARI = 0.55f

/**
 * Thin view of S3 · Registrazione, READ-ONLY in R1 (RC-2): only renders [stato] and forwards
 * [azioni]'s events. No Voci panel, no selection, no Revisione UI (AC-402, explicit cut) — those
 * arrive with `schermata-registrazione-identificazione` (R2).
 */
@Composable
fun SchermataRegistrazione(stato: RegistrazioneUiStato, azioni: AzioniRegistrazione) {
    SnastroTema {
        Surface(modifier = Modifier.fillMaxSize()) {
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
    Column(modifier = Modifier.fillMaxSize().padding(PADDING_SCHERMO).testTag("registrazione-scheletro")) {
        repeat(RIGHE_SCHELETRO) { indice ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
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
    Column(
        modifier = Modifier.fillMaxSize().padding(PADDING_SCHERMO).testTag("registrazione-errore-caricamento"),
    ) {
        Text(text = messaggio, color = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.height(PADDING_SEZIONE))
        TextButton(onClick = onRiprova, modifier = Modifier.testTag("registrazione-riprova")) {
            Text(ETICHETTA_RIPROVA)
        }
    }
}

@Composable
private fun ContenutoRegistrazione(stato: RegistrazioneUiStato.Dati, azioni: AzioniRegistrazione) {
    Column(modifier = Modifier.fillMaxSize().padding(PADDING_SCHERMO)) {
        IntestazioneRegistrazione(stato, azioni)
        stato.errore?.let { MessaggioInlineErrore(it, azioni.chiudiErrore) }
        Spacer(modifier = Modifier.height(PADDING_SEZIONE))
        if (stato.segmenti.isEmpty()) {
            Text(text = MESSAGGIO_TRASCRITTO_VUOTO, modifier = Modifier.testTag("registrazione-vuoto"))
        } else {
            ElencoSegmenti(stato.segmenti, azioni)
        }
    }
}

@Composable
private fun IntestazioneRegistrazione(stato: RegistrazioneUiStato.Dati, azioni: AzioniRegistrazione) {
    Column {
        Text(
            text = stato.titolo,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.testTag("registrazione-titolo"),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = formattaData(stato.dataRegistrazione), modifier = Modifier.testTag("registrazione-data"))
            Spacer(modifier = Modifier.width(PADDING_RIGA))
            Text(text = formattaDurata(stato.durataMs), modifier = Modifier.testTag("registrazione-durata"))
        }
        Spacer(modifier = Modifier.height(PADDING_RIGA))
        BarraLettore(stato = stato.barra, onRiproduci = azioni.riproduciDaInizio, onPausa = azioni.pausa)
        Spacer(modifier = Modifier.height(PADDING_RIGA))
        Row {
            TextButton(
                onClick = azioni.apriDocumento,
                enabled = stato.documentoPercorso != null,
                modifier = Modifier.testTag("registrazione-apri-documento"),
            ) { Text(ETICHETTA_APRI_DOCUMENTO) }
            TextButton(
                onClick = azioni.mostraDocumentoNellaCartella,
                enabled = stato.documentoPercorso != null,
                modifier = Modifier.testTag("registrazione-mostra-cartella"),
            ) { Text(ETICHETTA_MOSTRA_CARTELLA) }
        }
    }
}

@Composable
private fun MessaggioInlineErrore(messaggio: String, onChiudi: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = PADDING_RIGA).testTag("registrazione-errore"),
    ) {
        Text(text = messaggio, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f, fill = false))
        Text(
            text = ETICHETTA_CHIUDI_ERRORE,
            modifier = Modifier.padding(start = PADDING_RIGA).clickable(onClick = onChiudi)
                .testTag("registrazione-errore-chiudi"),
        )
    }
}

@Composable
private fun ElencoSegmenti(segmenti: List<SegmentoRiga>, azioni: AzioniRegistrazione) {
    LazyColumn(modifier = Modifier.fillMaxSize().testTag("registrazione-lista")) {
        items(segmenti, key = { it.segmentoId.numero }) { segmento -> SegmentoItem(segmento, azioni) }
    }
}

/** AC-208: click/'▶' on the row plays from [SegmentoRiga.inizioMs]; the highlight ([SegmentoRiga.inRiproduzione])
 * is the presenter's own live reflection of the shared player, never decided here. */
@Composable
private fun SegmentoItem(segmento: SegmentoRiga, azioni: AzioniRegistrazione) {
    val sfondo = if (segmento.inRiproduzione) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .background(sfondo)
            .clickable { azioni.riproduciSegmento(segmento.segmentoId) }
            .padding(vertical = PADDING_RIGA)
            .testTag("registrazione-segmento-${segmento.segmentoId.numero}"),
    ) {
        Text(
            text = "▶",
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag("registrazione-segmento-riproduci-${segmento.segmentoId.numero}"),
        )
        Spacer(modifier = Modifier.width(PADDING_RIGA))
        Box(
            modifier = Modifier
                .size(DIMENSIONE_PALLINO)
                .background(color = palette(segmento.voceId), shape = CircleShape)
                .testTag("registrazione-pallino-${segmento.segmentoId.numero}"),
        )
        Spacer(modifier = Modifier.width(PADDING_RIGA))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = segmento.etichettaVoce,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.testTag("registrazione-voce-${segmento.segmentoId.numero}"),
                )
                Spacer(modifier = Modifier.width(PADDING_RIGA))
                Text(text = formattaDurata(segmento.inizioMs), style = MaterialTheme.typography.labelSmall)
            }
            Text(
                text = segmento.testo,
                modifier = Modifier.testTag("registrazione-testo-${segmento.segmentoId.numero}"),
            )
        }
    }
}
