package snastro.ui.progetti

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import snastro.ui.stile.BottoneSn
import snastro.ui.stile.CampoSn
import snastro.ui.stile.LocalSnastroColori
import snastro.ui.stile.LocalSnastroTipografia
import snastro.ui.stile.SnastroMisure
import snastro.ui.stile.VarianteBottone
import snastro.ui.testi.ETICHETTA_ANNULLA
import snastro.ui.testi.ETICHETTA_CONFERMA_ELIMINAZIONE
import snastro.ui.testi.MESSAGGIO_CONFERMA_ELIMINA_PROGETTO
import snastro.ui.testi.etichettaNomeDaDigitare
import snastro.ui.testi.titoloConfermaEliminaProgetto

/**
 * R0 (EX-1..EX-3): the delete confirmation of [conferma]'s project — the question, what it does, the field where the
 * exact name is typed, "Annulla" and "Elimina" (`Pericolo`, enabled only when [ConfermaEliminazione.confermabile]).
 * The typed name is the presenter's state, not this view's.
 */
@Composable
internal fun ConfermaEliminaProgetto(conferma: ConfermaEliminazione, azioni: AzioniProgetti) {
    val colori = LocalSnastroColori.current
    val tipografia = LocalSnastroTipografia.current
    val nome = conferma.progetto.nome
    PannelloSuVelo(chiudibile = true, onChiudi = azioni.annullaElimina, tag = "progetti-conferma-elimina") {
        Text(text = titoloConfermaEliminaProgetto(nome), style = tipografia.title, color = colori.ink)
        Spacer(modifier = Modifier.height(SnastroMisure.space2))
        Text(text = MESSAGGIO_CONFERMA_ELIMINA_PROGETTO, style = tipografia.body, color = colori.inkMuted)
        Spacer(modifier = Modifier.height(SnastroMisure.space4))
        CampoSn(
            valore = conferma.nomeDigitato,
            onValoreCambiato = azioni.digitaNomeElimina,
            etichetta = etichettaNomeDaDigitare(nome),
            modifier = Modifier.fillMaxWidth().testTag("progetti-campo-nome-elimina"),
        )
        Spacer(modifier = Modifier.height(SnastroMisure.space5))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(modifier = Modifier.weight(1f))
            BottoneSn(
                etichetta = ETICHETTA_ANNULLA,
                onClick = azioni.annullaElimina,
                variante = VarianteBottone.Fantasma,
                modifier = Modifier.testTag("progetti-conferma-elimina-annulla"),
            )
            Spacer(modifier = Modifier.width(SnastroMisure.space2))
            BottoneSn(
                etichetta = ETICHETTA_CONFERMA_ELIMINAZIONE,
                onClick = azioni.confermaElimina,
                variante = VarianteBottone.Pericolo,
                abilitato = conferma.confermabile,
                modifier = Modifier.testTag("progetti-conferma-elimina-conferma"),
            )
        }
    }
}
