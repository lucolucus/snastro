// TooManyFunctions: one screen split into many small, single-purpose composables (RC-2 thin view) —
// the natural shape of a row with an editable name, a play control and its own inline confirmation.
@file:Suppress("TooManyFunctions")

package snastro.ui.parlanti

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
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
import snastro.ui.stile.BottoneIconaSn
import snastro.ui.stile.BottonePlay
import snastro.ui.stile.BottoneSn
import snastro.ui.stile.CardSn
import snastro.ui.stile.Icona
import snastro.ui.stile.LocalSnastroColori
import snastro.ui.stile.LocalSnastroTipografia
import snastro.ui.stile.SnastroMisure
import snastro.ui.stile.VarianteBottone
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
import snastro.ui.testi.titoloConfermaEliminazioneParlante

private val DIMENSIONE_INDICATORE_PICCOLO = 18.dp
private val DIAMETRO_PALLINO_NEUTRO = 10.dp
private val SPESSORE_ANELLO_NEUTRO = 2.dp

/**
 * Thin view of S4 · Parlanti del Progetto (RC-2): only renders [stato] and forwards [azioni]'s
 * events. AC-225: the delete confirmation is rendered INLINE, styled like `anteprime/Dialog.html`
 * (title = the question, body = what is lost, `Pericolo` primary) but in place of the row's own
 * controls — never an AWT/OS modal dialog, so the render-check (a single composable tree,
 * `:ui:renderCheck`) captures it exactly like every other state.
 */
@Composable
fun SchermataParlanti(
    stato: ParlantiUiStato,
    azioni: AzioniParlanti,
    scuro: Boolean = isSystemInDarkTheme(),
    riduciMovimento: Boolean? = null,
) {
    SnastroTema(scuro = scuro, riduciMovimento = riduciMovimento) {
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
    Column(
        modifier = Modifier.fillMaxSize()
            .padding(vertical = SnastroMisure.space5, horizontal = SnastroMisure.space6),
    ) {
        CircularProgressIndicator(modifier = Modifier.testTag("parlanti-indicatore-caricamento"))
    }
}

/** M5-style: the INITIAL load failed — a distinct state, never the AC-220 empty-catalogue message. */
@Composable
private fun ErroreCaricamentoParlanti(messaggio: String, onRiprova: () -> Unit) {
    val colori = LocalSnastroColori.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = SnastroMisure.space5, horizontal = SnastroMisure.space6)
            .testTag("parlanti-errore-caricamento"),
    ) {
        Text(text = messaggio, color = colori.danger, style = LocalSnastroTipografia.current.body)
        Spacer(modifier = Modifier.height(SnastroMisure.space4))
        BottoneSn(
            etichetta = ETICHETTA_RIPROVA,
            onClick = onRiprova,
            variante = VarianteBottone.Secondario,
            modifier = Modifier.testTag("parlanti-riprova"),
        )
    }
}

@Composable
private fun ContenutoParlanti(stato: ParlantiUiStato.Dati, azioni: AzioniParlanti) {
    val colori = LocalSnastroColori.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = SnastroMisure.space5, horizontal = SnastroMisure.space6)
            .testTag("parlanti-contenuto"),
    ) {
        stato.errore?.let { MessaggioInlineErrore(it, azioni.chiudiErrore, "parlanti-errore") }
        if (stato.vuoto) {
            Text(
                text = MESSAGGIO_PARLANTI_VUOTO,
                style = LocalSnastroTipografia.current.body,
                color = colori.inkMuted,
                modifier = Modifier.testTag("parlanti-vuoto"),
            )
        } else {
            ListaParlanti(stato, azioni)
        }
    }
}

// AC-222: Ricorrenti / Occasionali / Eliminati, in this order; a group with no rows renders no header
// (frugality: no empty section clutter — the grouping itself is the AC, not a fixed set of headers).
@Composable
private fun ListaParlanti(stato: ParlantiUiStato.Dati, azioni: AzioniParlanti) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("parlanti-lista"),
        verticalArrangement = Arrangement.spacedBy(SnastroMisure.space1),
    ) {
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

/** AC-577: "Ricorrenti"/"Occasionali"/"Eliminati" as `overline` headers (uppercase applied by the
 * style, never typed uppercase in the string, README §Tipografia). */
@Composable
private fun TitoloSezione(titolo: String, tag: String) {
    Text(
        text = titolo,
        style = LocalSnastroTipografia.current.overline,
        color = LocalSnastroColori.current.inkMuted,
        modifier = Modifier.padding(top = SnastroMisure.space4, bottom = SnastroMisure.space1)
            .testTag("parlanti-sezione-$tag"),
    )
}

@Composable
private fun RigaParlanteItem(riga: RigaParlante, azioni: AzioniParlanti) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = SnastroMisure.space2)
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
        PallinoNeutro()
        Spacer(modifier = Modifier.width(SnastroMisure.space2))
        BottonePlay(
            inRiproduzione = false,
            onClick = { azioni.riproduci(riga.parlanteId) },
            grande = false,
            abilitato = riga.riproduzioneAbilitata,
            modifier = Modifier.testTag("parlanti-riproduzione-${riga.parlanteId.valore}"),
        )
        Spacer(modifier = Modifier.width(SnastroMisure.space2))
        Column(modifier = Modifier.weight(1f)) {
            CampoNomeParlante(riga, azioni)
            Text(
                text = etichettaDettaglioParlante(riga.numImpronte, riga.numRegistrazioni, riga.ultimaApparizione),
                style = LocalSnastroTipografia.current.caption,
                color = LocalSnastroColori.current.inkMuted,
                modifier = Modifier.testTag("parlanti-dettaglio-${riga.parlanteId.valore}"),
            )
        }
        // AC-224: 'Promuovi' shown only for an occasionale row — a trivial `==` on already-known
        // data, exactly like RigaRegistrazioneItem's `apribile` (RC-2: the view forwards no decision
        // the presenter/read-model hasn't already made).
        if (riga.tipoParlante == TipoParlanteVista.OCCASIONALE) {
            BottoneSn(
                etichetta = ETICHETTA_PROMUOVI,
                onClick = { azioni.promuovi(riga.parlanteId) },
                variante = VarianteBottone.Link,
                piccolo = true,
                abilitato = !riga.operazioneInCorso,
                modifier = Modifier.testTag("parlanti-promuovi-${riga.parlanteId.valore}"),
            )
        }
        BottoneIconaSn(
            icona = Icona.Trash,
            descrizione = ETICHETTA_ELIMINA,
            onClick = { azioni.chiediConfermaEliminazione(riga.parlanteId) },
            abilitato = !riga.operazioneInCorso,
            modifier = Modifier.testTag("parlanti-elimina-${riga.parlanteId.valore}"),
        )
        if (riga.operazioneInCorso) {
            CircularProgressIndicator(
                modifier = Modifier.padding(start = SnastroMisure.space2).size(DIMENSIONE_INDICATORE_PICCOLO)
                    .testTag("parlanti-operazione-in-corso-${riga.parlanteId.valore}"),
            )
        }
    }
}

/** AC-577: a neutral (`inkMuted`) 2dp ring — people have no voice colour outside a recording, unlike
 * [snastro.ui.stile.PallinoVoce] (which needs a [snastro.kernel.VoceId] this screen has none of). */
@Composable
private fun PallinoNeutro() {
    val colori = LocalSnastroColori.current
    Box(
        Modifier.size(DIAMETRO_PALLINO_NEUTRO).border(SPESSORE_ANELLO_NEUTRO, colori.inkMuted, CircleShape),
    )
}

/**
 * AC-223: the Nome, editable inline exactly like `CampoTitolo` (S2, AC-363): a local text buffer
 * resynced from [RigaParlante.nome] on every real change, submitted only on Enter or on losing
 * focus and only when it differs from the Nome shown. Disabled while a row operation is in flight
 * (M3); a refused rename (blank, name already used — [INV-16]) comes back as the row's inline
 * `erroreRiga` and the row keeps its old Nome. AC-577: renders as plain `heading` text at rest (no
 * boxed field) — same borderless-field pattern as S2's editable date/title.
 */
@Composable
private fun CampoNomeParlante(riga: RigaParlante, azioni: AzioniParlanti) {
    val colori = LocalSnastroColori.current
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
    BasicTextField(
        value = testo,
        onValueChange = { testo = it },
        singleLine = true,
        enabled = !riga.operazioneInCorso,
        textStyle = LocalSnastroTipografia.current.heading.copy(
            color = if (riga.operazioneInCorso) colori.inkFaint else colori.ink,
        ),
        cursorBrush = SolidColor(colori.accentInk),
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

/**
 * AC-225/AC-577: styled like `anteprime/Dialog.html` (title = the question, body = what is lost,
 * `Pericolo` primary) but rendered INLINE, replacing the row's own controls — never an OS-level
 * modal dialog (a real popup window would not be part of the captured composable tree).
 */
@Composable
private fun ConfermaEliminazioneParlante(riga: RigaParlante, azioni: AzioniParlanti) {
    val colori = LocalSnastroColori.current
    CardSn(
        modifier = Modifier.fillMaxWidth().testTag("parlanti-conferma-eliminazione-${riga.parlanteId.valore}"),
    ) {
        Text(
            text = titoloConfermaEliminazioneParlante(riga.nome),
            style = LocalSnastroTipografia.current.title,
            color = colori.ink,
        )
        Spacer(modifier = Modifier.height(SnastroMisure.space2))
        Text(
            text = MESSAGGIO_CONFERMA_ELIMINAZIONE_PARLANTE,
            style = LocalSnastroTipografia.current.body,
            color = colori.inkMuted,
        )
        Spacer(modifier = Modifier.height(SnastroMisure.space3))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BottoneSn(
                etichetta = ETICHETTA_ANNULLA,
                onClick = { azioni.annullaEliminazione(riga.parlanteId) },
                variante = VarianteBottone.Secondario,
                abilitato = !riga.operazioneInCorso,
                modifier = Modifier.testTag("parlanti-annulla-eliminazione-${riga.parlanteId.valore}"),
            )
            Spacer(modifier = Modifier.width(SnastroMisure.space2))
            BottoneSn(
                etichetta = ETICHETTA_CONFERMA_ELIMINAZIONE,
                onClick = { azioni.confermaEliminazione(riga.parlanteId) },
                variante = VarianteBottone.Pericolo,
                abilitato = !riga.operazioneInCorso,
                modifier = Modifier.testTag("parlanti-conferma-elimina-${riga.parlanteId.valore}"),
            )
            if (riga.operazioneInCorso) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(start = SnastroMisure.space2).size(DIMENSIONE_INDICATORE_PICCOLO),
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
        style = LocalSnastroTipografia.current.body,
        color = LocalSnastroColori.current.inkMuted,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = SnastroMisure.space1)
            .testTag("parlanti-eliminato-${riga.parlanteId.valore}"),
    )
}

@Composable
private fun MessaggioInlineErrore(messaggio: String, onChiudi: () -> Unit, tag: String) {
    val colori = LocalSnastroColori.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = SnastroMisure.space2).testTag(tag),
    ) {
        Text(
            text = messaggio,
            color = colori.danger,
            style = LocalSnastroTipografia.current.caption,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            text = ETICHETTA_CHIUDI_ERRORE,
            color = colori.accentInk,
            style = LocalSnastroTipografia.current.label,
            modifier = Modifier
                .padding(start = SnastroMisure.space2)
                .clickable(onClick = onChiudi)
                .testTag("$tag-chiudi"),
        )
    }
}
