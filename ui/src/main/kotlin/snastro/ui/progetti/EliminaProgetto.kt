package snastro.ui.progetti

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import snastro.progetto.applicazione.letture.ProgettoVista
import snastro.ui.stile.BottoneSn
import snastro.ui.stile.CampoSn
import snastro.ui.stile.LocalSnastroColori
import snastro.ui.stile.LocalSnastroTipografia
import snastro.ui.stile.MenuAltreAzioni
import snastro.ui.stile.SnastroMisure
import snastro.ui.stile.VarianteBottone
import snastro.ui.testi.ETICHETTA_ANNULLA
import snastro.ui.testi.ETICHETTA_CONFERMA_ELIMINAZIONE
import snastro.ui.testi.ETICHETTA_ELIMINA
import snastro.ui.testi.ETICHETTA_TOGLI_DALLA_LISTA
import snastro.ui.testi.MESSAGGIO_CONFERMA_ELIMINA_PROGETTO
import snastro.ui.testi.etichettaNomeDaDigitare
import snastro.ui.testi.titoloConfermaEliminaProgetto

/**
 * R0: a project card's "⋯" menu — "Elimina…" (danger text), which asks to confirm the delete, or, for a project whose
 * folder is not found, "Togli dalla lista" only (EX-7, EX-8).
 */
@Composable
internal fun MenuProgetto(progetto: ProgettoVista, abilitato: Boolean, azioni: AzioniProgetti) {
    val id = progetto.progettoId.valore
    MenuAltreAzioni("progetti-altre-azioni-$id", abilitato) { chiudi ->
        if (progetto.cartellaTrovata) {
            DropdownMenuItem(
                text = { Text(ETICHETTA_ELIMINA, color = LocalSnastroColori.current.danger) },
                onClick = {
                    chiudi()
                    azioni.chiediElimina(progetto)
                },
                modifier = Modifier.testTag("progetti-menu-elimina-$id"),
            )
        } else {
            DropdownMenuItem(
                text = { Text(ETICHETTA_TOGLI_DALLA_LISTA) },
                onClick = {
                    chiudi()
                    azioni.togliDallaLista(progetto)
                },
                modifier = Modifier.testTag("progetti-menu-togli-$id"),
            )
        }
    }
}

/**
 * R0 (EX-1..EX-5): the delete confirmation of [conferma]'s project, from S1's list or the open project's header — the
 * question, what it does, the field where the exact name is typed, the failed delete's message, "Annulla"
 * ([onAnnulla]) and "Elimina" ([onConferma] with the typed name; `Pericolo`, enabled only by the exact name:
 * [ConfermaEliminazione.confermataDa]). Like the new-project panel, the typed name is local UI state, fresh each time
 * the confirmation opens; nothing can be pressed while [inCorso].
 */
@Composable
internal fun ConfermaEliminaProgetto(
    conferma: ConfermaEliminazione,
    inCorso: Boolean,
    onAnnulla: () -> Unit,
    onConferma: (nomeDigitato: String) -> Unit,
) {
    val colori = LocalSnastroColori.current
    val tipografia = LocalSnastroTipografia.current
    val nomeProgetto = conferma.nome
    var nome by remember(conferma.percorso) { mutableStateOf("") }
    PannelloSuVelo(chiudibile = !inCorso, onChiudi = onAnnulla, tag = "progetti-conferma-elimina") {
        Text(text = titoloConfermaEliminaProgetto(nomeProgetto), style = tipografia.title, color = colori.ink)
        Spacer(modifier = Modifier.height(SnastroMisure.space2))
        Text(text = MESSAGGIO_CONFERMA_ELIMINA_PROGETTO, style = tipografia.body, color = colori.inkMuted)
        Spacer(modifier = Modifier.height(SnastroMisure.space4))
        CampoSn(
            valore = nome,
            onValoreCambiato = { nome = it },
            etichetta = etichettaNomeDaDigitare(nomeProgetto),
            abilitato = !inCorso,
            modifier = Modifier.fillMaxWidth().testTag("progetti-campo-nome-elimina"),
        )
        conferma.errore?.let {
            Spacer(modifier = Modifier.height(SnastroMisure.space2))
            Text(
                text = it,
                style = tipografia.caption,
                color = colori.danger,
                modifier = Modifier.testTag("progetti-errore-elimina"),
            )
        }
        Spacer(modifier = Modifier.height(SnastroMisure.space5))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(modifier = Modifier.weight(1f))
            BottoneSn(
                etichetta = ETICHETTA_ANNULLA,
                onClick = onAnnulla,
                variante = VarianteBottone.Fantasma,
                abilitato = !inCorso,
                modifier = Modifier.testTag("progetti-conferma-elimina-annulla"),
            )
            Spacer(modifier = Modifier.width(SnastroMisure.space2))
            BottoneSn(
                etichetta = ETICHETTA_CONFERMA_ELIMINAZIONE,
                onClick = { onConferma(nome) },
                variante = VarianteBottone.Pericolo,
                abilitato = !inCorso && conferma.confermataDa(nome),
                modifier = Modifier.testTag("progetti-conferma-elimina-conferma"),
            )
        }
    }
}
