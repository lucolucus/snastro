package snastro.ui.registrazioni

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import snastro.kernel.IncontroId
import snastro.ui.stile.BottoneSn
import snastro.ui.stile.LocalSnastroColori
import snastro.ui.stile.LocalSnastroTipografia
import snastro.ui.stile.MenuAltreAzioni
import snastro.ui.stile.MessaggioInlineErrore
import snastro.ui.stile.SnastroMisure
import snastro.ui.stile.VarianteBottone
import snastro.ui.testi.ETICHETTA_AGGIUNGI_PARTI
import snastro.ui.testi.ETICHETTA_ANNULLA
import snastro.ui.testi.ETICHETTA_CONFERMA_ELIMINAZIONE
import snastro.ui.testi.ETICHETTA_ELIMINA
import snastro.ui.testi.ETICHETTA_RITRASCRIVI
import snastro.ui.testi.MESSAGGIO_CONFERMA_ELIMINA_CON_TRASCRITTO
import snastro.ui.testi.MESSAGGIO_CONFERMA_ELIMINA_CON_TRASCRITTO_RESIDUO
import snastro.ui.testi.MESSAGGIO_CONFERMA_ELIMINA_PARTE_CON_TRASCRITTO
import snastro.ui.testi.MESSAGGIO_CONFERMA_ELIMINA_SENZA_TRASCRITTO
import snastro.ui.testi.titoloConfermaElimina
import snastro.ui.testi.titoloConfermaEliminaParte
import snastro.ui.testi.titoloIncontro

/**
 * ADR 0020 §6/AC-625: `BottoneIcona More` opening an `anteprime/Menu.html`-style menu — 'Ritrascrivi'
 * (only when [RigaRegistrazione.ritrascriviDisponibile], same field/flow as before, listed first like
 * S4's non-destructive-before-destructive order), 'Aggiungi parti…' (only on a 1-part Incontro's row: [aggiungiParti]
 * non-null) and 'Elimina…' (always, enabled/disabled per [RigaRegistrazione.eliminazione]) — same mechanism as S4's
 * `MenuAltreAzioniParlante`.
 */
@Composable
internal fun MenuAzioniRegistrazione(
    riga: RigaRegistrazione,
    azioni: AzioniRegistrazioni,
    aggiungiParti: (() -> Unit)?,
    incontroId: IncontroId,
) {
    val id = riga.registrazioneId
    MenuAltreAzioni("registrazioni-altre-azioni-${id.valore}", !riga.operazioneInCorso) { chiudi ->
        if (riga.ritrascriviDisponibile) {
            DropdownMenuItem(
                text = { Text(ETICHETTA_RITRASCRIVI) },
                onClick = {
                    chiudi()
                    azioni.ritrascrivi(id)
                },
                modifier = Modifier.testTag("registrazioni-menu-ritrascrivi-${id.valore}"),
            )
        }
        aggiungiParti?.let {
            VoceMenuAggiungiParti("registrazioni-menu-aggiungi-parti-${incontroId.valore}") {
                chiudi()
                it()
            }
        }
        VoceMenuElimina(
            riga.eliminazione,
            onClick = {
                chiudi()
                azioni.elimina(id)
            },
            tag = "registrazioni-menu-elimina-${id.valore}",
        )
    }
}

@Composable
internal fun VoceMenuAggiungiParti(tag: String, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(ETICHETTA_AGGIUNGI_PARTI) }, onClick = onClick, modifier = Modifier.testTag(tag))
}

/** AC-625: 'Elimina…' — enabled (danger text) or disabled with its caption as a second line, never
 * both rendered at once; a disabled item's `onClick` is unreachable (Compose `enabled = false`), the
 * presenter's own [RegistrazioniPresenter.elimina] guards the same rule independently. */
@Composable
private fun VoceMenuElimina(stato: StatoEliminazione, onClick: () -> Unit, tag: String) {
    val colori = LocalSnastroColori.current
    val disabilitato = stato is StatoEliminazione.NonDisponibile
    DropdownMenuItem(
        text = {
            Column {
                Text(ETICHETTA_ELIMINA, color = if (disabilitato) colori.inkFaint else colori.danger)
                if (stato is StatoEliminazione.NonDisponibile) {
                    Text(stato.motivo, style = LocalSnastroTipografia.current.caption, color = colori.inkFaint)
                }
            }
        },
        onClick = onClick,
        enabled = !disabilitato,
        modifier = Modifier.testTag(tag),
    )
}

/**
 * AC-626: replaces the row's own content — title = the question, body = one of two variants per
 * [RigaRegistrazione.trascrittoDisponibile] (styled like `anteprime/Dialog.html`, `Pericolo` primary,
 * same pattern as S4's `ConfermaEliminazioneParlante`/S2's own [ConfermaRitrascrivi]) — never an
 * OS-level modal dialog.
 */
@Composable
internal fun ConfermaElimina(riga: RigaRegistrazione, azioni: AzioniRegistrazioni) {
    val id = riga.registrazioneId
    val colori = LocalSnastroColori.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SnastroMisure.space4, vertical = SnastroMisure.space3)
            .testTag("registrazioni-conferma-elimina-${id.valore}"),
    ) {
        Text(
            text = riga.parte?.let { titoloConfermaEliminaParte(it.numero, it.titoloIncontro) }
                ?: titoloConfermaElimina(riga.titolo),
            style = LocalSnastroTipografia.current.title,
            color = colori.ink,
        )
        Spacer(modifier = Modifier.height(SnastroMisure.space1))
        Text(
            text = if (riga.trascrittoDisponibile) {
                if (riga.parte != null) {
                    MESSAGGIO_CONFERMA_ELIMINA_PARTE_CON_TRASCRITTO
                } else {
                    MESSAGGIO_CONFERMA_ELIMINA_CON_TRASCRITTO
                }
            } else {
                MESSAGGIO_CONFERMA_ELIMINA_SENZA_TRASCRITTO
            },
            style = LocalSnastroTipografia.current.caption,
            color = colori.inkMuted,
        )
        if (riga.trascrittoDisponibile) {
            Spacer(modifier = Modifier.height(SnastroMisure.space1))
            Text(
                text = MESSAGGIO_CONFERMA_ELIMINA_CON_TRASCRITTO_RESIDUO,
                style = LocalSnastroTipografia.current.caption,
                color = colori.inkMuted,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = SnastroMisure.space2)) {
            BottoneSn(
                etichetta = ETICHETTA_CONFERMA_ELIMINAZIONE,
                onClick = { azioni.confermaElimina(id) },
                variante = VarianteBottone.Pericolo,
                abilitato = !riga.operazioneInCorso,
                modifier = Modifier.testTag("registrazioni-conferma-elimina-conferma-${id.valore}"),
            )
            Spacer(modifier = Modifier.width(SnastroMisure.space2))
            BottoneSn(
                etichetta = ETICHETTA_ANNULLA,
                onClick = { azioni.annullaElimina(id) },
                variante = VarianteBottone.Secondario,
                abilitato = !riga.operazioneInCorso,
                modifier = Modifier.testTag("registrazioni-annulla-elimina-${id.valore}"),
            )
        }
        riga.erroreRiga?.let {
            MessaggioInlineErrore(it, { azioni.chiudiErroreRiga(id) }, "registrazioni-errore-riga-${id.valore}")
        }
    }
}
