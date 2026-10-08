package snastro.ui.registrazioni

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import snastro.kernel.RegistrazioneId
import snastro.ui.stile.BottoneSn
import snastro.ui.stile.CampoNumeroPersone
import snastro.ui.stile.ChipStato
import snastro.ui.stile.Icona
import snastro.ui.stile.LocalSnastroColori
import snastro.ui.stile.LocalSnastroTipografia
import snastro.ui.stile.SnastroMisure
import snastro.ui.stile.TipoChipStato
import snastro.ui.stile.VarianteBottone
import snastro.ui.testi.ETICHETTA_ANNULLA
import snastro.ui.testi.ETICHETTA_DA_IDENTIFICARE
import snastro.ui.testi.ETICHETTA_RIPROVA
import snastro.ui.testi.ETICHETTA_RITRASCRIVI
import snastro.ui.testi.ETICHETTA_TRASCRIVI
import snastro.ui.testi.etichettaIdentificazione
import snastro.ui.testi.etichettaRitrascrizioneInAttesa
import snastro.ui.testi.etichettaRitrascrizioneInCorso
import snastro.ui.testi.messaggioRitrascrizioneNonRiuscita

private val LARGHEZZA_CONFERMA_RITRASCRIVI = 320.dp

/**
 * AC-204/AC-345 (fetta Parlanti): the identification badge — "3 voci · 1 da identificare", or
 * "3 voci" alone once every Voce is identified (AC-345, never "· 0 da identificare"). Absent
 * entirely when [RigaRegistrazione.identificazione] is `null` (the row not yet known/failed —
 * [RegistrazioniPresenter] decides, this only renders what it is given). AC-575: no literal coloured
 * voice dots — [IdentificazioneRiga] carries only counts, no `VoceId`s to colour.
 */
@Composable
internal fun BadgeIdentificazione(identificazione: IdentificazioneRiga, id: String) {
    val colori = LocalSnastroColori.current
    Text(
        text = etichettaIdentificazione(identificazione.numVoci, identificazione.numVociDaIdentificare),
        style = LocalSnastroTipografia.current.caption,
        color = if (identificazione.numVociDaIdentificare > 0) colori.accentInk else colori.inkMuted,
        modifier = Modifier.testTag("registrazioni-identificazione-$id"),
    )
}

/**
 * AC-575/rework cycle 1 (composer finding #10): the trailing status content is ONE line (chip, then
 * field, then button), vertically centred — never stacked. The one exception is [ConfermaRitrascrivi]
 * (AC-449), a dialog-like panel that REPLACES this row entirely while open.
 */
@Composable
internal fun ColonnaElaborazione(stato: StatoElaborazioneRiga, riga: RigaRegistrazione, azioni: AzioniRegistrazioni) {
    val id = riga.registrazioneId
    if (stato == StatoElaborazioneRiga.Completata && riga.confermaRitrascrivi) {
        ConfermaRitrascrivi(riga, azioni)
        return
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SnastroMisure.space2),
        modifier = Modifier.testTag("registrazioni-stato-${id.valore}"),
    ) {
        when (stato) {
            StatoElaborazioneRiga.NonAvviata ->
                AvvioConNumeroPersone(
                    riga,
                    ETICHETTA_TRASCRIVI,
                    null,
                    VarianteBottone.Primario,
                    azioni.modificaNumeroPersone,
                    azioni.avviaElaborazione,
                )
            is StatoElaborazioneRiga.InAttesa -> StatoInAttesa(stato, riga, azioni)
            is StatoElaborazioneRiga.InCorso -> StatoInCorso(stato)
            is StatoElaborazioneRiga.Fallita ->
                AvvioConNumeroPersone(
                    riga,
                    ETICHETTA_RIPROVA,
                    Icona.Retry,
                    VarianteBottone.Secondario,
                    azioni.modificaNumeroPersone,
                    azioni.avviaElaborazione,
                )
            StatoElaborazioneRiga.Completata -> ColonnaCompletata(riga, azioni)
        }
        if (riga.operazioneInCorso) {
            CircularProgressIndicator(
                modifier = Modifier.width(DIMENSIONE_INDICATORE_PICCOLO)
                    .testTag("registrazioni-operazione-in-corso-${id.valore}"),
            )
        }
    }
}

/** AC-475/rework cycle 1 (composer finding #8): the plain "In coda" chip already says the position
 * ("In coda · n") — the distinguishing raw caption is shown ONLY for a re-run (`ritrascrizione`),
 * which the chip alone cannot express. */
@Composable
private fun StatoInAttesa(
    stato: StatoElaborazioneRiga.InAttesa,
    riga: RigaRegistrazione,
    azioni: AzioniRegistrazioni,
) {
    val id = riga.registrazioneId
    ChipStato(TipoChipStato.InCoda(stato.posizione))
    if (stato.ritrascrizione) {
        Text(
            text = etichettaRitrascrizioneInAttesa(stato.posizione),
            style = LocalSnastroTipografia.current.caption,
            color = LocalSnastroColori.current.inkMuted,
        )
    }
    // AC-475: 'Annulla' on ANY IN_ATTESA row (plain or re-run) when the source is supplied.
    if (riga.annullabile) {
        BottoneSn(
            etichetta = ETICHETTA_ANNULLA,
            onClick = { azioni.annullaElaborazione(id) },
            variante = VarianteBottone.Link,
            piccolo = true,
            abilitato = !riga.operazioneInCorso,
            modifier = Modifier.testTag("registrazioni-annulla-${id.valore}"),
        )
    }
}

/** Same principle as [StatoInAttesa]: [ChipStato.TipoChipStato.InCorso] already shows the fase + the
 * elapsed time — the raw caption is shown only for a re-run. */
@Composable
private fun StatoInCorso(stato: StatoElaborazioneRiga.InCorso) {
    ChipStato(TipoChipStato.InCorso(stato.faseEtichetta, stato.trascorsoMs))
    if (stato.ritrascrizione) {
        Text(
            text = etichettaRitrascrizioneInCorso(stato.faseEtichetta, stato.trascorsoMs),
            style = LocalSnastroTipografia.current.caption,
            color = LocalSnastroColori.current.inkMuted,
        )
    }
}

/**
 * ADR 0018: the status chip plus, when applicable, AC-451's failed-re-run notice and AC-448/449's
 * 'Ritrascrivi' field/button. AC-575/rework cycle 1 (MED #11): the chip is the `Avviso` "Da
 * identificare" — not `Trascritta` — while the row still has unidentified Voci.
 */
@Composable
private fun ColonnaCompletata(riga: RigaRegistrazione, azioni: AzioniRegistrazioni) {
    val id = riga.registrazioneId
    val daIdentificare = (riga.identificazione?.numVociDaIdentificare ?: 0) > 0
    if (daIdentificare) {
        ChipStato(TipoChipStato.Avviso(ETICHETTA_DA_IDENTIFICARE, Icona.People))
    } else {
        ChipStato(TipoChipStato.Trascritta)
    }
    riga.ritrascrizioneFallita?.let {
        Text(
            text = messaggioRitrascrizioneNonRiuscita(it),
            color = LocalSnastroColori.current.danger,
            style = LocalSnastroTipografia.current.caption,
            modifier = Modifier.testTag("registrazioni-ritrascrizione-fallita-${id.valore}"),
        )
    }
    if (riga.ritrascriviDisponibile) {
        AvvioConNumeroPersone(
            riga,
            ETICHETTA_RITRASCRIVI,
            null,
            VarianteBottone.Secondario,
            azioni.modificaNumeroPersone,
            azioni.ritrascrivi,
            // ADR 0020 §6/AC-625 (b): the More menu is ALWAYS present (pre-release finding #83), so
            // 'Ritrascrivi' ALWAYS moves into it — only the prefilled field stays on the row (the
            // button would otherwise duplicate the menu item).
            mostraBottone = false,
        )
    }
}

/**
 * AC-449: the row's OWN 'Ritrascrivi' field/button are replaced by this panel — styled like
 * `anteprime/Dialog.html` (title = the question, body = what is lost, `Primario` "Ritrascrivi" —
 * the design system's own example is not a destructive/`Pericolo` action) — never an OS-level modal
 * dialog (same mechanism as S4's `ConfermaEliminazioneParlante`). Wrapped to
 * [LARGHEZZA_CONFERMA_RITRASCRIVI] so the long text wraps inside the row's trailing column instead of
 * overflowing it.
 */
@Composable
private fun ConfermaRitrascrivi(riga: RigaRegistrazione, azioni: AzioniRegistrazioni) {
    val id = riga.registrazioneId
    val colori = LocalSnastroColori.current
    Column(
        modifier = Modifier
            .width(LARGHEZZA_CONFERMA_RITRASCRIVI)
            .testTag("registrazioni-conferma-ritrascrivi-${id.valore}"),
    ) {
        Text(
            text = riga.testiConfermaRitrascrivi().titolo,
            style = LocalSnastroTipografia.current.title,
            color = colori.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(SnastroMisure.space1))
        Text(
            text = riga.testiConfermaRitrascrivi().messaggio,
            style = LocalSnastroTipografia.current.caption,
            color = colori.inkMuted,
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = SnastroMisure.space2)) {
            BottoneSn(
                etichetta = ETICHETTA_RITRASCRIVI,
                onClick = { azioni.confermaRitrascrivi(id) },
                variante = VarianteBottone.Primario,
                abilitato = !riga.operazioneInCorso,
                modifier = Modifier.testTag("registrazioni-conferma-ritrascrivi-conferma-${id.valore}"),
            )
            Spacer(modifier = Modifier.width(SnastroMisure.space2))
            BottoneSn(
                etichetta = ETICHETTA_ANNULLA,
                onClick = { azioni.annullaRitrascrivi(id) },
                variante = VarianteBottone.Secondario,
                abilitato = !riga.operazioneInCorso,
                modifier = Modifier.testTag("registrazioni-annulla-ritrascrivi-${id.valore}"),
            )
        }
    }
}

/**
 * ADR 0014: the plain 'Numero di persone' field (empty = automatic) next to a start/retry button
 * [etichetta] — shared by 'Trascrivi'/'Riprova'/'Ritrascrivi' ([onAvvia] carries which command). Its
 * text is presenter state ([RigaRegistrazione.numeroPersone]); validation and the inline message
 * (AC-375/449) are the presenter's, shown as the row's `erroreRiga`. Enter in the field starts the
 * same command as the button (rework cycle 1, MED #12 — `CampoNumeroPersone.onInvio`), even when
 * [mostraBottone] is `false` (ADR 0020 §6: the field stays on the row once 'Ritrascrivi' moves into
 * the More menu, AC-625 (b) — Enter still runs the SAME validate-then-command flow either way).
 */
@Suppress("LongParameterList") // one parameter per documented knob shared by Trascrivi/Riprova/Ritrascrivi
@Composable
private fun AvvioConNumeroPersone(
    riga: RigaRegistrazione,
    etichetta: String,
    icona: Icona?,
    variante: VarianteBottone,
    onModifica: (RegistrazioneId, String) -> Unit,
    onAvvia: (RegistrazioneId) -> Unit,
    mostraBottone: Boolean = true,
) {
    val id = riga.registrazioneId
    CampoNumeroPersone(
        valore = riga.numeroPersone,
        onValoreCambiato = { onModifica(id, it) },
        errore = riga.erroreRiga != null,
        abilitato = !riga.operazioneInCorso,
        onInvio = { onAvvia(id) },
        modifier = Modifier.testTag("registrazioni-numero-persone-${id.valore}"),
    )
    if (mostraBottone) {
        BottoneSn(
            etichetta = etichetta,
            onClick = { onAvvia(id) },
            variante = variante,
            piccolo = true,
            icona = icona,
            abilitato = !riga.operazioneInCorso,
        )
    }
}
