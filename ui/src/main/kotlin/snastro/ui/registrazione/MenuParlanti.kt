package snastro.ui.registrazione

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import snastro.kernel.ParlanteId
import snastro.kernel.VoceId
import snastro.parlanti.applicazione.eventi.TipoParlanteVista
import snastro.parlanti.applicazione.letture.ParlanteAttivo
import snastro.ui.stile.BottoneSn
import snastro.ui.stile.CampoSn
import snastro.ui.stile.EtichettaVoce
import snastro.ui.stile.Icona
import snastro.ui.stile.LocalSnastroColori
import snastro.ui.stile.LocalSnastroTipografia
import snastro.ui.stile.SnastroMisure
import snastro.ui.stile.VarianteBottone
import snastro.ui.testi.ETICHETTA_CREA
import snastro.ui.testi.ETICHETTA_NOME
import snastro.ui.testi.ETICHETTA_NUOVA_PERSONA
import snastro.ui.testi.ETICHETTA_OCCASIONALE
import snastro.ui.testi.ETICHETTA_RICORRENTE
import snastro.ui.testi.testoParti

/**
 * AC-586: every dropdown menu's own container — raised, `radiusDialog`, a hairline `line` border (the
 * kit has no menu component of its own; this reuses its tokens, not a hand-rolled colour/shape).
 */
@Composable
internal fun MenuSn(expanded: Boolean, onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val colori = LocalSnastroColori.current
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(SnastroMisure.radiusDialog),
        containerColor = colori.raised,
        border = BorderStroke(1.dp, colori.line),
        content = content,
    )
}

/** AC-586: a menu's own overline group label (`Menu.html`'s `.group`, e.g. 'Persone del progetto'). */
@Composable
internal fun EtichettaGruppoMenu(testo: String) {
    Text(
        text = testo,
        style = LocalSnastroTipografia.current.overline,
        color = LocalSnastroColori.current.inkMuted,
        modifier = Modifier.padding(horizontal = SnastroMisure.space2, vertical = SnastroMisure.space1),
    )
}

/**
 * AC-586: a Parlante menu item — name + the type as its trailing caption (`Menu.html`'s `.aux`). No
 * colour dot here: a Parlante (unlike a Voce) carries no [VoceId] of its own in this view's state
 * (it may be attributed to several Voci across recordings, or to none yet) — inventing one would be a
 * false colour, not a simplification.
 */
@Composable
internal fun EtichettaMenu(nome: String, tipo: String) {
    val colori = LocalSnastroColori.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(nome, style = LocalSnastroTipografia.current.label, color = colori.ink, modifier = Modifier.weight(1f))
        Text(tipo, style = LocalSnastroTipografia.current.caption, color = colori.inkMuted)
    }
}

/**
 * AC-586: an [OpzioneVoce] menu item — this one DOES have a [VoceId], so it is genuinely
 * `EtichettaVoce`-shaped (dot + name/"Voce n"), matching `Menu.html`. "Named" is read off [carte]'s own
 * [ContenutoCarta.Attribuita] (the Nome already in state), never guessed from the label string
 * (rework cycle 1, MED-8 — a fallback string is an implementation detail, not a state a view decides on).
 */
@Composable
internal fun EtichettaMenuVoce(opzione: OpzioneVoce, carte: List<CartaVoce>) {
    val nome = (carte.find { it.voceId == opzione.voceId }?.contenuto as? ContenutoCarta.Attribuita)?.nome
        ?: opzione.nome
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.weight(1f, fill = false)) { EtichettaVoce(voceId = opzione.voceId, nome = nome) }
        // AC-I77: a Voce of another Parte carries the Parti it speaks in.
        if (opzione.parti.isNotEmpty()) {
            Text(
                testoParti(opzione.parti),
                style = LocalSnastroTipografia.current.caption,
                color = LocalSnastroColori.current.inkMuted,
                modifier = Modifier.padding(start = SnastroMisure.space2),
            )
        }
    }
}

/** AC-586: 'Riassegna a'/'Altri'/'Dai un nome a questa frase' menus — one item per Parlante
 * (EtichettaVoce-shaped + type), [ETICHETTA_NUOVA_PERSONA] after a divider when [conNuovo] is offered
 * (same text as the card's own action button — shared constant, one less string to keep in sync; the
 * AC-586 mockup's own '…' suffix is dropped so both places read identically). */
@Suppress("LongParameterList") // one parameter per menu attribute + its two callbacks
@Composable
internal fun MenuParlanti(
    tag: String,
    etichetta: String,
    parlanti: List<ParlanteAttivo>,
    abilitato: Boolean,
    icona: Icona? = null,
    conNuovo: (() -> Unit)? = null,
    onScelta: (ParlanteId) -> Unit,
) {
    var aperto by remember { mutableStateOf(false) }
    Box {
        BottoneSn(
            etichetta,
            onClick = { aperto = true },
            abilitato = abilitato,
            variante = VarianteBottone.Secondario,
            piccolo = true,
            icona = icona,
            modifier = Modifier.testTag(tag),
        )
        MenuSn(expanded = aperto, onDismissRequest = { aperto = false }) {
            parlanti.forEach { p ->
                DropdownMenuItem(
                    text = { EtichettaMenu(p.nome, etichettaTipo(p.tipoParlante)) },
                    onClick = {
                        aperto = false
                        onScelta(p.parlanteId)
                    },
                )
            }
            conNuovo?.let { nuovo ->
                if (parlanti.isNotEmpty()) HorizontalDivider()
                DropdownMenuItem(text = { Text(ETICHETTA_NUOVA_PERSONA) }, onClick = {
                    aperto = false
                    nuovo()
                })
            }
        }
    }
}

/** AC-586: 'Riassegna a'/'Unisci con' menus over Voci — one item per [OpzioneVoce], '<extra>' (e.g.
 * 'nuova voce') after a divider. */
@Suppress("LongParameterList") // one parameter per menu attribute + its callback
@Composable
internal fun MenuVoci(
    tag: String,
    etichetta: String,
    voci: List<OpzioneVoce>,
    abilitato: Boolean,
    icona: Icona? = null,
    extra: String? = null,
    carte: List<CartaVoce> = emptyList(),
    onScelta: (VoceId?) -> Unit,
) {
    var aperto by remember { mutableStateOf(false) }
    Box {
        BottoneSn(
            etichetta,
            onClick = { aperto = true },
            abilitato = abilitato && (voci.isNotEmpty() || extra != null),
            variante = VarianteBottone.Secondario,
            piccolo = true,
            icona = icona,
            modifier = Modifier.testTag(tag),
        )
        MenuSn(expanded = aperto, onDismissRequest = { aperto = false }) {
            voci.forEach { v ->
                DropdownMenuItem(text = { EtichettaMenuVoce(v, carte) }, onClick = {
                    aperto = false
                    onScelta(v.voceId)
                })
            }
            extra?.let { testo ->
                if (voci.isNotEmpty()) HorizontalDivider()
                DropdownMenuItem(text = { Text(testo) }, onClick = {
                    aperto = false
                    onScelta(null)
                })
            }
        }
    }
}

/** 'nuovo…': a Nome field, `ricorrente` preselected with an `occasionale` toggle (Q-7); tags under [prefisso]. */
@Composable
internal fun ModuloNuovo(prefisso: String, onCrea: (String, TipoParlanteVista) -> Unit) {
    var nome by remember(prefisso) { mutableStateOf("") }
    var occasionale by remember(prefisso) { mutableStateOf(false) }
    Column(
        modifier = Modifier.testTag("$prefisso-modulo-nuovo"),
        verticalArrangement = Arrangement.spacedBy(SnastroMisure.space2),
    ) {
        CampoSn(
            valore = nome,
            onValoreCambiato = { nome = it },
            etichetta = ETICHETTA_NOME,
            piccolo = true,
            modifier = Modifier.fillMaxWidth().testTag("$prefisso-nuovo-nome"),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            // L718: the checkbox alone was only SnastroMisure.iconM (20dp) — too small a touch target.
            // The WHOLE row (checkbox + label) is the toggle now; the Checkbox itself only renders the
            // visual (its own onCheckedChange is null, so it never double-toggles under the Row's own).
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
                    .toggleable(
                        value = occasionale,
                        role = Role.Checkbox,
                        onValueChange = { occasionale = it },
                    )
                    .testTag("$prefisso-nuovo-occasionale"),
            ) {
                Checkbox(
                    checked = occasionale,
                    onCheckedChange = null,
                    modifier = Modifier.size(SnastroMisure.iconM),
                )
                Spacer(Modifier.width(SnastroMisure.space2))
                Text(ETICHETTA_OCCASIONALE, style = LocalSnastroTipografia.current.body)
            }
            BottoneSn(
                ETICHETTA_CREA,
                onClick = {
                    onCrea(nome, if (occasionale) TipoParlanteVista.OCCASIONALE else TipoParlanteVista.RICORRENTE)
                },
                variante = VarianteBottone.Primario,
                piccolo = true,
                modifier = Modifier.testTag("$prefisso-nuovo-crea"),
            )
        }
    }
}

internal fun etichettaTipo(tipo: TipoParlanteVista): String = when (tipo) {
    TipoParlanteVista.RICORRENTE -> ETICHETTA_RICORRENTE
    TipoParlanteVista.OCCASIONALE -> ETICHETTA_OCCASIONALE
}
