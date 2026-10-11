package snastro.ui.registrazione

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import snastro.parlanti.applicazione.eventi.TipoParlanteVista
import snastro.ui.stile.BottoneIconaSn
import snastro.ui.stile.BottoneSn
import snastro.ui.stile.Icona
import snastro.ui.stile.LocalSnastroColori
import snastro.ui.stile.LocalSnastroTipografia
import snastro.ui.stile.SnastroMisure
import snastro.ui.stile.VarianteBottone
import snastro.ui.testi.ETICHETTA_DESELEZIONA
import snastro.ui.testi.ETICHETTA_DIVIDI_VOCE
import snastro.ui.testi.ETICHETTA_NOMINA_FRASE
import snastro.ui.testi.ETICHETTA_NUOVA_VOCE
import snastro.ui.testi.ETICHETTA_RIASSEGNA_A
import snastro.ui.testi.ETICHETTA_TOGLI_CONFERMA
import snastro.ui.testi.testoSelezione

/** AC-209..211: the selection toolbar — 'Riassegna a' (other Voci + 'nuova voce') and 'Dividi voce'
 * (disabled with its explanation on the whole Voce, INV-10); ADR 0019 §6: with ONE Segmento selected,
 * 'Dai un nome a questa frase' (attivo Parlanti, then 'nuovo…') and 'Togli conferma' on a confirmed one.
 * Every decision is the presenter's; the actions wrap (FlowRow) so nothing clips at 1024x640. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BarraSelezioneVista(barra: BarraSelezione, carte: List<CartaVoce>, azioni: AzioniRegistrazione) {
    val colori = LocalSnastroColori.current
    var nuovoAperto by remember(barra.frase?.segmentoId) { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(SnastroMisure.radiusCard),
        color = colori.raised,
        border = BorderStroke(1.dp, colori.line),
        shadowElevation = SnastroMisure.space1,
        modifier = Modifier.fillMaxWidth().padding(bottom = SnastroMisure.space3)
            .testTag("registrazione-barra-selezione"),
    ) {
        Column(modifier = Modifier.padding(SnastroMisure.space3)) {
            FlowRow(
                verticalArrangement = Arrangement.Center,
                horizontalArrangement = Arrangement.spacedBy(SnastroMisure.space2),
            ) {
                Text(
                    text = testoSelezione(barra.numeroSegmenti, barra.etichetta),
                    style = LocalSnastroTipografia.current.label,
                    color = colori.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                barra.frase?.let { AzioniFrase(it, azioni) { nuovoAperto = !nuovoAperto } }
                MenuRiassegna(barra, carte, azioni)
                BottoneDividi(barra, azioni)
                BottoneIconaSn(
                    Icona.Close,
                    ETICHETTA_DESELEZIONA,
                    onClick = azioni.deseleziona,
                    piccolo = true,
                )
            }
            if (nuovoAperto && barra.frase?.abilitata == true) {
                ModuloNuovo("registrazione-frase") { nome, tipo ->
                    nuovoAperto = false
                    azioni.nominaFrase(ObiettivoNome.Nuovo(nome, ricorrente = tipo == TipoParlanteVista.RICORRENTE))
                }
            }
            barra.spiegazioneDividi?.let {
                Text(
                    text = it,
                    style = LocalSnastroTipografia.current.caption,
                    color = colori.inkMuted,
                    modifier = Modifier.testTag("registrazione-dividi-spiegazione"),
                )
            }
        }
    }
}

/** AC-211: 'Riassegna a ▾' — the other Voci of this Parte, then 'nuova voce'. */
@Composable
private fun MenuRiassegna(barra: BarraSelezione, carte: List<CartaVoce>, azioni: AzioniRegistrazione) {
    MenuVoci(
        "registrazione-riassegna",
        ETICHETTA_RIASSEGNA_A,
        barra.destinazioni,
        barra.abilitata,
        icona = Icona.Reassign,
        extra = ETICHETTA_NUOVA_VOCE,
        carte = carte,
        onScelta = azioni.riassegnaA,
    )
}

/** AC-210: 'Dividi voce', disabled on the whole Voce (INV-10; its explanation sits below the row). */
@Composable
private fun BottoneDividi(barra: BarraSelezione, azioni: AzioniRegistrazione) {
    BottoneSn(
        ETICHETTA_DIVIDI_VOCE,
        onClick = azioni.dividiVoce,
        abilitato = barra.abilitata && barra.dividiAbilitato,
        variante = VarianteBottone.Secondario,
        piccolo = true,
        icona = Icona.Split,
        modifier = Modifier.testTag("registrazione-dividi"),
    )
}

/** AC-526/AC-528: 'Dai un nome a questa frase' (attivo Parlanti, then 'nuovo…') and 'Togli conferma'. */
@Composable
private fun AzioniFrase(frase: MenuFrase, azioni: AzioniRegistrazione, onNuovo: () -> Unit) {
    MenuParlanti(
        "registrazione-nomina-frase",
        ETICHETTA_NOMINA_FRASE,
        frase.parlanti,
        frase.abilitata,
        icona = Icona.Person,
        conNuovo = onNuovo,
    ) { azioni.nominaFrase(ObiettivoNome.Esistente(it)) }
    if (frase.confermato) {
        BottoneSn(
            ETICHETTA_TOGLI_CONFERMA,
            onClick = azioni.togliConferma,
            abilitato = frase.abilitata,
            variante = VarianteBottone.Secondario,
            piccolo = true,
            icona = Icona.Pin,
            modifier = Modifier.testTag("registrazione-togli-conferma"),
        )
    }
}
