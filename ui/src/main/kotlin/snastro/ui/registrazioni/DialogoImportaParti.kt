package snastro.ui.registrazioni

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import snastro.ui.stile.BottoneSn
import snastro.ui.stile.CardSn
import snastro.ui.stile.LocalSnastroColori
import snastro.ui.stile.LocalSnastroTipografia
import snastro.ui.stile.SnastroMisure
import snastro.ui.stile.VarianteBottone
import snastro.ui.testi.ETICHETTA_ANNULLA_IMPORTA
import snastro.ui.testi.ETICHETTA_IMPORTA
import snastro.ui.testi.etichettaIncontriSeparati
import snastro.ui.testi.etichettaUnIncontro
import snastro.ui.testi.titoloDialogoImporta
import java.io.File

private val LARGHEZZA_DIALOGO_IMPORTA = 520.dp
private val ALTEZZA_MASSIMA_ELENCO_FILE = 160.dp

/**
 * AC-I70/AC-I71: the import dialog of 2+ files (styled like `anteprime/Dialog.html`, rendered inline like the
 * other confirmations — never an OS modal). Thin view: the files in selection order, the two choices, the
 * all-or-nothing [DialogoImporta.errore], and 'Importa' / 'Annulla' forwarding to [azioni].
 */
@Composable
internal fun DialogoImportaParti(dialogo: DialogoImporta, azioni: AzioniRegistrazioni) {
    val colori = LocalSnastroColori.current
    val tipografia = LocalSnastroTipografia.current
    val n = dialogo.percorsi.size
    CardSn(
        modifier = Modifier
            .widthIn(max = LARGHEZZA_DIALOGO_IMPORTA)
            .fillMaxWidth()
            .testTag("registrazioni-dialogo-importa"),
    ) {
        Text(text = titoloDialogoImporta(n), style = tipografia.title, color = colori.ink)
        Spacer(modifier = Modifier.height(SnastroMisure.space3))
        Column(
            modifier = Modifier
                .heightIn(max = ALTEZZA_MASSIMA_ELENCO_FILE)
                .verticalScroll(rememberScrollState())
                .testTag("registrazioni-dialogo-importa-file"),
        ) {
            dialogo.percorsi.forEachIndexed { i, percorso ->
                Text(
                    text = "${i + 1}. ${File(percorso).name}",
                    style = tipografia.body,
                    color = colori.inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.height(SnastroMisure.space3))
        Scelta(
            etichettaUnIncontro(n),
            dialogo.scelta == SceltaImporta.UnIncontro,
            "registrazioni-importa-un-incontro",
        ) { azioni.scegliImporta(SceltaImporta.UnIncontro) }
        Scelta(
            etichettaIncontriSeparati(n),
            dialogo.scelta == SceltaImporta.IncontriSeparati,
            "registrazioni-importa-separati",
        ) { azioni.scegliImporta(SceltaImporta.IncontriSeparati) }
        dialogo.errore?.let {
            Spacer(modifier = Modifier.height(SnastroMisure.space2))
            Text(
                text = it,
                style = tipografia.body,
                color = colori.danger,
                modifier = Modifier.testTag("registrazioni-dialogo-importa-errore"),
            )
        }
        Spacer(modifier = Modifier.height(SnastroMisure.space3))
        BottoniDialogo(dialogo, azioni)
    }
}

@Composable
private fun Scelta(etichetta: String, selezionata: Boolean, tag: String, onScegli: () -> Unit) {
    val colori = LocalSnastroColori.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selezionata, onClick = onScegli, role = Role.RadioButton)
            .testTag(tag),
    ) {
        RadioButton(
            selected = selezionata,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = colori.accent, unselectedColor = colori.inkMuted),
        )
        Spacer(modifier = Modifier.width(SnastroMisure.space2))
        Text(text = etichetta, style = LocalSnastroTipografia.current.body, color = colori.ink)
    }
}

@Composable
private fun BottoniDialogo(dialogo: DialogoImporta, azioni: AzioniRegistrazioni) {
    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
        BottoneSn(
            etichetta = ETICHETTA_ANNULLA_IMPORTA,
            onClick = azioni.annullaImporta,
            variante = VarianteBottone.Secondario,
            abilitato = !dialogo.invioInCorso,
            modifier = Modifier.testTag("registrazioni-importa-annulla"),
        )
        Spacer(modifier = Modifier.width(SnastroMisure.space2))
        BottoneSn(
            etichetta = ETICHETTA_IMPORTA,
            onClick = azioni.confermaImporta,
            variante = VarianteBottone.Primario,
            abilitato = !dialogo.invioInCorso,
            modifier = Modifier.testTag("registrazioni-importa-conferma"),
        )
    }
}
