// TooManyFunctions: one screen split into many small, single-purpose composables (RC-2 thin view) —
// the natural shape of a row with an editable name, a play control and its own inline confirmation.
@file:Suppress("TooManyFunctions")

package snastro.ui.parlanti

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import snastro.parlanti.applicazione.eventi.TipoParlanteVista
import snastro.ui.SnastroTema
import snastro.ui.testi.ETICHETTA_ANNULLA
import snastro.ui.testi.ETICHETTA_CHIUDI_ERRORE
import snastro.ui.testi.ETICHETTA_CONFERMA_ELIMINAZIONE
import snastro.ui.testi.ETICHETTA_ELIMINA
import snastro.ui.testi.ETICHETTA_PROMUOVI
import snastro.ui.testi.ETICHETTA_RIPROVA
import snastro.ui.testi.ETICHETTA_SEZIONE_ELIMINATI
import snastro.ui.testi.ETICHETTA_SEZIONE_OCCASIONALI
import snastro.ui.testi.ETICHETTA_SEZIONE_RICORRENTI
import snastro.ui.testi.MESSAGGIO_CONFERMA_ELIMINAZIONE_PARLANTE
import snastro.ui.testi.MESSAGGIO_PARLANTI_VUOTO
import snastro.ui.testi.etichettaDettaglioParlante

private val PADDING_SCHERMO = 24.dp
private val PADDING_SEZIONE = 16.dp
private val PADDING_RIGA = 8.dp
private val PADDING_RIGA_COMPRESSA = 4.dp
private val DIMENSIONE_INDICATORE_PICCOLO = 18.dp

/**
 * Thin view of S4 · Parlanti del Progetto (RC-2): only renders [stato] and forwards [azioni]'s
 * events. AC-225: the delete confirmation is rendered INLINE, in place of the row's own controls —
 * never an AWT/OS modal dialog, so the render-check (a single composable tree, `:ui:renderCheck`)
 * captures it exactly like every other state.
 */
@Composable
fun SchermataParlanti(stato: ParlantiUiStato, azioni: AzioniParlanti) {
    SnastroTema {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (stato) {
                ParlantiUiStato.Caricamento -> IndicatoreCaricamentoParlanti()
                is ParlantiUiStato.Dati -> ContenutoParlanti(stato, azioni)
                is ParlantiUiStato.Errore -> ErroreCaricamentoParlanti(stato.messaggio, azioni.riprova)
            }
        }
    }
}

@Composable
private fun IndicatoreCaricamentoParlanti() {
    Column(modifier = Modifier.fillMaxSize().padding(PADDING_SCHERMO)) {
        CircularProgressIndicator(modifier = Modifier.testTag("parlanti-indicatore-caricamento"))
    }
}

/** M5-style: the INITIAL load failed — a distinct state, never the AC-220 empty-catalogue message. */
@Composable
private fun ErroreCaricamentoParlanti(messaggio: String, onRiprova: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(PADDING_SCHERMO).testTag("parlanti-errore-caricamento"),
    ) {
        Text(text = messaggio, color = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.height(PADDING_SEZIONE))
        TextButton(onClick = onRiprova, modifier = Modifier.testTag("parlanti-riprova")) {
            Text(ETICHETTA_RIPROVA)
        }
    }
}

@Composable
private fun ContenutoParlanti(stato: ParlantiUiStato.Dati, azioni: AzioniParlanti) {
    Column(modifier = Modifier.fillMaxSize().padding(PADDING_SCHERMO).testTag("parlanti-contenuto")) {
        stato.errore?.let { MessaggioInlineErrore(it, azioni.chiudiErrore, "parlanti-errore") }
        if (stato.vuoto) {
            Text(text = MESSAGGIO_PARLANTI_VUOTO, modifier = Modifier.testTag("parlanti-vuoto"))
        } else {
            ListaParlanti(stato, azioni)
        }
    }
}

// AC-222: Ricorrenti / Occasionali / Eliminati, in this order; a group with no rows renders no header
// (frugality: no empty section clutter — the grouping itself is the AC, not a fixed set of headers).
@Composable
private fun ListaParlanti(stato: ParlantiUiStato.Dati, azioni: AzioniParlanti) {
    LazyColumn(modifier = Modifier.fillMaxSize().testTag("parlanti-lista")) {
        sezioneAttivi(stato.ricorrenti, ETICHETTA_SEZIONE_RICORRENTI, "ricorrenti", azioni)
        sezioneAttivi(stato.occasionali, ETICHETTA_SEZIONE_OCCASIONALI, "occasionali", azioni)
        if (stato.eliminati.isNotEmpty()) {
            item { TitoloSezione(ETICHETTA_SEZIONE_ELIMINATI, "eliminati") }
            items(stato.eliminati, key = { "eliminato-${it.parlanteId.valore}" }) { riga ->
                RigaParlanteEliminatoItem(riga)
            }
        }
    }
}

private fun LazyListScope.sezioneAttivi(
    righe: List<RigaParlante>,
    titolo: String,
    tag: String,
    azioni: AzioniParlanti,
) {
    if (righe.isEmpty()) return
    item { TitoloSezione(titolo, tag) }
    items(righe, key = { "$tag-${it.parlanteId.valore}" }) { riga -> RigaParlanteItem(riga, azioni) }
}

@Composable
private fun TitoloSezione(titolo: String, tag: String) {
    Text(
        text = titolo,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = PADDING_SEZIONE).testTag("parlanti-sezione-$tag"),
    )
}

@Composable
private fun RigaParlanteItem(riga: RigaParlante, azioni: AzioniParlanti) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = PADDING_RIGA)
            .testTag("parlanti-riga-${riga.parlanteId.valore}"),
    ) {
        if (riga.confermaEliminazione) {
            ConfermaEliminazioneParlante(riga, azioni)
        } else {
            RigaParlanteControlli(riga, azioni)
            riga.erroreRiga?.let {
                MessaggioInlineErrore(
                    it,
                    { azioni.chiudiErroreRiga(riga.parlanteId) },
                    "parlanti-errore-riga-${riga.parlanteId.valore}",
                )
            }
        }
    }
}

@Composable
private fun RigaParlanteControlli(riga: RigaParlante, azioni: AzioniParlanti) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        ControlloRiproduzioneParlante(riga, azioni)
        Spacer(modifier = Modifier.width(PADDING_RIGA))
        Column(modifier = Modifier.weight(1f)) {
            CampoNomeParlante(riga, azioni)
            Text(
                text = etichettaDettaglioParlante(riga.numImpronte, riga.numRegistrazioni, riga.ultimaApparizione),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("parlanti-dettaglio-${riga.parlanteId.valore}"),
            )
        }
        // AC-224: 'Promuovi' shown only for an occasionale row — a trivial `==` on already-known
        // data, exactly like RigaRegistrazioneItem's `apribile` (RC-2: the view forwards no decision
        // the presenter/read-model hasn't already made).
        if (riga.tipoParlante == TipoParlanteVista.OCCASIONALE) {
            TextButton(
                onClick = { azioni.promuovi(riga.parlanteId) },
                enabled = !riga.operazioneInCorso,
                modifier = Modifier.testTag("parlanti-promuovi-${riga.parlanteId.valore}"),
            ) { Text(ETICHETTA_PROMUOVI) }
        }
        TextButton(
            onClick = { azioni.chiediConfermaEliminazione(riga.parlanteId) },
            enabled = !riga.operazioneInCorso,
            modifier = Modifier.testTag("parlanti-elimina-${riga.parlanteId.valore}"),
        ) { Text(ETICHETTA_ELIMINA) }
        if (riga.operazioneInCorso) {
            CircularProgressIndicator(
                modifier = Modifier.padding(start = PADDING_RIGA).size(DIMENSIONE_INDICATORE_PICCOLO)
                    .testTag("parlanti-operazione-in-corso-${riga.parlanteId.valore}"),
            )
        }
    }
}

@Composable
private fun ControlloRiproduzioneParlante(riga: RigaParlante, azioni: AzioniParlanti) {
    // AC-226: '▶' disabled — no clickable action, dimmed colour — when the read-model has no excerpt.
    Text(
        text = "▶",
        color = if (riga.riproduzioneAbilitata) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier
            .clickable(enabled = riga.riproduzioneAbilitata) { azioni.riproduci(riga.parlanteId) }
            .testTag("parlanti-riproduzione-${riga.parlanteId.valore}"),
    )
}

/**
 * AC-223: the Nome, editable inline exactly like `CampoTitolo` (S2, AC-363): a local text buffer
 * resynced from [RigaParlante.nome] on every real change, submitted only on Enter or on losing
 * focus and only when it differs from the Nome shown. Disabled while a row operation is in flight
 * (M3); a refused rename (blank, name already used — [INV-16]) comes back as the row's inline
 * `erroreRiga` and the row keeps its old Nome.
 */
@Composable
private fun CampoNomeParlante(riga: RigaParlante, azioni: AzioniParlanti) {
    var testo by remember(riga.nome) { mutableStateOf(riga.nome) }
    var eraFocalizzato by remember(riga.nome) { mutableStateOf(false) }
    var inviato by remember(riga.nome) { mutableStateOf(false) }
    if (inviato && !riga.operazioneInCorso) {
        testo = riga.nome
        inviato = false
    }
    fun sottometti() {
        if (testo != riga.nome) {
            inviato = true
            azioni.rinomina(riga.parlanteId, testo)
        }
    }
    OutlinedTextField(
        value = testo,
        onValueChange = { testo = it },
        singleLine = true,
        enabled = !riga.operazioneInCorso,
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { sottometti() }),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { stato ->
                if (eraFocalizzato && !stato.isFocused) sottometti()
                eraFocalizzato = stato.isFocused
            }
            .onPreviewKeyEvent { evento ->
                if (evento.type == KeyEventType.KeyDown && evento.key == Key.Escape) {
                    testo = riga.nome
                    true
                } else {
                    false
                }
            }
            .testTag("parlanti-nome-${riga.parlanteId.valore}"),
    )
}

/** AC-225: the row's OWN controls are replaced by this panel — the privacy text plus Conferma/Annulla —
 * never an OS-level modal dialog. Annulla only flips [RigaParlante.confermaEliminazione] back (nothing
 * else changes); Conferma sends `EliminaParlante`. */
@Composable
private fun ConfermaEliminazioneParlante(riga: RigaParlante, azioni: AzioniParlanti) {
    Column(modifier = Modifier.fillMaxWidth().testTag("parlanti-conferma-eliminazione-${riga.parlanteId.valore}")) {
        Text(text = riga.nome, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = MESSAGGIO_CONFERMA_ELIMINAZIONE_PARLANTE,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = PADDING_RIGA)) {
            Button(
                onClick = { azioni.confermaEliminazione(riga.parlanteId) },
                enabled = !riga.operazioneInCorso,
                modifier = Modifier.testTag("parlanti-conferma-elimina-${riga.parlanteId.valore}"),
            ) { Text(ETICHETTA_CONFERMA_ELIMINAZIONE) }
            Spacer(modifier = Modifier.width(PADDING_RIGA))
            TextButton(
                onClick = { azioni.annullaEliminazione(riga.parlanteId) },
                enabled = !riga.operazioneInCorso,
                modifier = Modifier.testTag("parlanti-annulla-eliminazione-${riga.parlanteId.valore}"),
            ) { Text(ETICHETTA_ANNULLA) }
            if (riga.operazioneInCorso) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(start = PADDING_RIGA).size(DIMENSIONE_INDICATORE_PICCOLO),
                )
            }
        }
        riga.erroreRiga?.let {
            MessaggioInlineErrore(
                it,
                { azioni.chiudiErroreRiga(riga.parlanteId) },
                "parlanti-errore-riga-${riga.parlanteId.valore}",
            )
        }
    }
}

/** AC-222: "Eliminati" shows only the Nome — no impronte/registrazioni/estratto, no actions. */
@Composable
private fun RigaParlanteEliminatoItem(riga: RigaParlanteEliminato) {
    Text(
        text = riga.nome,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = PADDING_RIGA_COMPRESSA)
            .testTag("parlanti-eliminato-${riga.parlanteId.valore}"),
    )
}

@Composable
private fun MessaggioInlineErrore(messaggio: String, onChiudi: () -> Unit, tag: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = PADDING_RIGA).testTag(tag)) {
        Text(text = messaggio, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f, fill = false))
        Text(
            text = ETICHETTA_CHIUDI_ERRORE,
            modifier = Modifier.padding(start = PADDING_RIGA).clickable(onClick = onChiudi).testTag("$tag-chiudi"),
        )
    }
}
