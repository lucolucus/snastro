// TooManyFunctions: one Voce card split into small single-purpose composables (RC-2 thin view, screen-split) —
// its head, content, candidate row, wait, error, actions and 'More' menu are one flow, over the threshold by one.
@file:Suppress("TooManyFunctions")

package snastro.ui.registrazione

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import snastro.parlanti.applicazione.letture.Candidato
import snastro.ui.formattaDurata
import snastro.ui.stile.BottoneIconaSn
import snastro.ui.stile.BottoneSn
import snastro.ui.stile.EtichettaVoce
import snastro.ui.stile.Icona
import snastro.ui.stile.LocalSnastroColori
import snastro.ui.stile.LocalSnastroTipografia
import snastro.ui.stile.MisuratoreFascia
import snastro.ui.stile.SnastroMisure
import snastro.ui.stile.VarianteBottone
import snastro.ui.testi.ETICHETTA_ALTRI
import snastro.ui.testi.ETICHETTA_ANNULLA
import snastro.ui.testi.ETICHETTA_CAMBIA
import snastro.ui.testi.ETICHETTA_CHIUDI_ERRORE
import snastro.ui.testi.ETICHETTA_CONFERMA
import snastro.ui.testi.ETICHETTA_DAI_UN_NOME
import snastro.ui.testi.ETICHETTA_ESTRATTO
import snastro.ui.testi.ETICHETTA_GRUPPO_ALTRE_PARTI
import snastro.ui.testi.ETICHETTA_GRUPPO_QUESTA_PARTE
import snastro.ui.testi.ETICHETTA_NUOVA_PERSONA
import snastro.ui.testi.ETICHETTA_SALTA
import snastro.ui.testi.ETICHETTA_UNISCI_CON
import snastro.ui.testi.MESSAGGIO_COMANDO_IN_ATTESA
import snastro.ui.testi.MESSAGGIO_PROPOSTA_IN_ATTESA
import snastro.ui.testi.SUGGERIMENTO_PRIMA_REGISTRAZIONE
import snastro.ui.testi.testoAncheInParti
import snastro.ui.testi.testoConferma
import snastro.ui.testi.testoEstrattoParte

private val DIMENSIONE_INDICATORE = 18.dp
private val LARGHEZZA_NOME_CANDIDATO = 120.dp

@Composable
internal fun CartaVoceVista(
    carta: CartaVoce,
    pannello: PannelloVoci,
    durata: Long,
    primario: Boolean,
    azioni: AzioniRegistrazione,
) {
    val colori = LocalSnastroColori.current
    val n = carta.voceId.numero
    var nuovoAperto by remember(n) { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(SnastroMisure.radiusCard),
        color = colori.raised,
        border = BorderStroke(1.dp, colori.line),
        modifier = Modifier.fillMaxWidth().testTag("voce-$n"),
    ) {
        Column(
            modifier = Modifier.padding(SnastroMisure.space4),
            verticalArrangement = Arrangement.spacedBy(SnastroMisure.space3),
        ) {
            IntestazioneCarta(carta, pannello, durata, azioni, onNuovo = { nuovoAperto = !nuovoAperto })
            if (carta.altreParti.isNotEmpty()) {
                Text(
                    testoAncheInParti(carta.altreParti),
                    style = LocalSnastroTipografia.current.caption,
                    color = LocalSnastroColori.current.inkMuted,
                    modifier = Modifier.testTag("voce-$n-altre-parti"),
                )
            }
            ContenutoCartaVista(carta, pannello, azioni)
            AttesaCarta(carta, azioni)
            carta.errore?.let { ErroreCarta(n, it) { azioni.chiudiErroreVoce(carta.voceId) } }
            // L761d: this card's own 'nuovo…' form, once open, shows its OWN Primario 'Crea' below — this
            // card's action row is demoted to Secondario so the two don't both render as Primario at once.
            AzioniCarta(carta, pannello, primario && !nuovoAperto, azioni, onNuovo = { nuovoAperto = !nuovoAperto })
            if (nuovoAperto && carta.azioniAbilitate) {
                ModuloNuovo("voce-$n") { nome, tipo ->
                    nuovoAperto = false
                    azioni.nuovoParlante(carta.voceId, nome, tipo)
                }
            }
        }
    }
}

/**
 * AC-585: `EtichettaVoce` + [durata] (the sum of this Voce's Segmenti durations, view arithmetic on
 * state — no new source) + a Fantasma 'Estratto'. Every card's head — named or not — also carries its
 * own 'More' ([MenuAltreAzioniCarta]: 'Cambia' on named cards, 'Unisci con…' on every card, rework cycle
 * 1 HIGH-2) — [AzioniCarta] never duplicates it.
 */
@Composable
private fun IntestazioneCarta(
    carta: CartaVoce,
    pannello: PannelloVoci,
    durata: Long,
    azioni: AzioniRegistrazione,
    onNuovo: () -> Unit,
) {
    val colori = LocalSnastroColori.current
    val contenuto = carta.contenuto as? ContenutoCarta.Attribuita
    val nome = contenuto?.nome
    val n = carta.voceId.numero
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SnastroMisure.space1),
    ) {
        Box(
            modifier = Modifier.weight(1f)
                .let { if (nome != null) it.testTag("voce-$n-nome") else it },
        ) { EtichettaVoce(voceId = carta.voceId, nome = nome) }
        if (durata > 0) {
            Text(
                text = formattaDurata(durata),
                style = LocalSnastroTipografia.current.timecode,
                color = colori.inkMuted,
                modifier = Modifier.testTag("voce-$n-tempo"),
            )
        }
        if (nome == null) {
            BottoneSn(
                ETICHETTA_ESTRATTO,
                onClick = { azioni.riproduciEstrattoVoce(carta.voceId) },
                abilitato = pannello.estrattiDisponibili,
                variante = VarianteBottone.Fantasma,
                piccolo = true,
                icona = Icona.Listen,
                modifier = Modifier.testTag("voce-$n-estratto"),
            )
        } else {
            BottoneIconaSn(
                Icona.Listen,
                ETICHETTA_ESTRATTO,
                onClick = { azioni.riproduciEstrattoVoce(carta.voceId) },
                abilitato = pannello.estrattiDisponibili,
                piccolo = true,
                modifier = Modifier.testTag("voce-$n-estratto"),
            )
        }
        MenuAltreAzioniCarta(carta, contenuto, pannello, onNuovo, azioni)
    }
}

@Composable
private fun ContenutoCartaVista(carta: CartaVoce, pannello: PannelloVoci, azioni: AzioniRegistrazione) {
    val colori = LocalSnastroColori.current
    val n = carta.voceId.numero
    when (val contenuto = carta.contenuto) {
        ContenutoCarta.Caricamento -> Indicatore("voce-$n-caricamento")
        is ContenutoCarta.Errore -> Text(
            text = contenuto.messaggio,
            color = colori.danger,
            style = LocalSnastroTipografia.current.body,
        )
        // AC-585: a named card's head is compact — the Nome already sits in `IntestazioneCarta`.
        is ContenutoCarta.Attribuita -> Unit
        is ContenutoCarta.DaIdentificare -> when (val proposta = contenuto.proposta) {
            StatoProposta.Caricamento -> Indicatore("voce-$n-proposta-caricamento")
            StatoProposta.InAttesa -> Text(
                MESSAGGIO_PROPOSTA_IN_ATTESA,
                style = LocalSnastroTipografia.current.caption,
                color = colori.inkMuted,
                modifier = Modifier.testTag("voce-$n-proposta-in-attesa"),
            )
            is StatoProposta.Errore -> Text(
                proposta.messaggio,
                color = colori.danger,
                style = LocalSnastroTipografia.current.body,
            )
            is StatoProposta.Pronta -> Column(verticalArrangement = Arrangement.spacedBy(SnastroMisure.space1)) {
                if (contenuto.galleriaVuota) {
                    Text(
                        SUGGERIMENTO_PRIMA_REGISTRAZIONE,
                        style = LocalSnastroTipografia.current.caption,
                        color = colori.inkMuted,
                        modifier = Modifier.testTag("voce-$n-prima-registrazione"),
                    )
                } else if (proposta.candidati.isNotEmpty()) {
                    Text("Sembra", style = LocalSnastroTipografia.current.overline, color = colori.inkMuted)
                    proposta.candidati.forEachIndexed { i, c ->
                        RigaCandidato(n, i, c, pannello.estrattiDisponibili, pannello.parteDelloEstratto(c.estratto)) {
                            azioni.riproduciEstratto(c.estratto)
                        }
                    }
                }
            }
        }
    }
}

@Suppress("LongParameterList") // one parameter per input of the row
@Composable
private fun RigaCandidato(
    n: Int,
    indice: Int,
    candidato: Candidato,
    estrattiDisponibili: Boolean,
    parteEstratto: Int?,
    onEstratto: () -> Unit,
) {
    val colori = LocalSnastroColori.current
    Column(modifier = Modifier.testTag("voce-$n-candidato-$indice")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.width(LARGHEZZA_NOME_CANDIDATO)) {
                Text(
                    candidato.nome,
                    style = LocalSnastroTipografia.current.label.copy(fontWeight = FontWeight.SemiBold),
                    color = colori.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    etichettaTipo(candidato.tipoParlante),
                    style = LocalSnastroTipografia.current.caption,
                    color = colori.inkMuted,
                )
            }
            Spacer(Modifier.width(SnastroMisure.space2))
            MisuratoreFascia(candidato.fascia, Modifier.testTag("voce-$n-candidato-$indice-fascia"))
            Spacer(Modifier.weight(1f))
            BottoneIconaSn(
                Icona.Listen,
                ETICHETTA_ESTRATTO,
                onClick = onEstratto,
                abilitato = estrattiDisponibili,
                piccolo = true,
            )
        }
        // AC-I78: an extract that is not from the open Parte says which one.
        parteEstratto?.let {
            Text(
                testoEstrattoParte(it),
                style = LocalSnastroTipografia.current.caption,
                color = colori.inkMuted,
                modifier = Modifier.testTag("voce-$n-candidato-$indice-parte"),
            )
        }
    }
}

/** AC-411/AC-412/AC-413: in corso → a progress indicator; past the threshold → the wait label + 'Annulla'. */
@Composable
private fun AttesaCarta(carta: CartaVoce, azioni: AzioniRegistrazione) {
    val colori = LocalSnastroColori.current
    val n = carta.voceId.numero
    when (carta.inCorso) {
        null -> Unit
        AttesaComando.IN_CORSO -> Indicatore("voce-$n-in-corso")
        AttesaComando.IN_ATTESA -> Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.testTag("voce-$n-in-attesa"),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(DIMENSIONE_INDICATORE))
            Spacer(Modifier.width(SnastroMisure.space2))
            Text(
                MESSAGGIO_COMANDO_IN_ATTESA,
                style = LocalSnastroTipografia.current.caption,
                color = colori.inkMuted,
                modifier = Modifier.weight(1f),
            )
            BottoneSn(
                ETICHETTA_ANNULLA,
                onClick = { azioni.annullaComando(carta.voceId) },
                variante = VarianteBottone.Link,
                modifier = Modifier.testTag("voce-$n-annulla"),
            )
        }
    }
}

@Composable
private fun Indicatore(tag: String) {
    CircularProgressIndicator(modifier = Modifier.padding(vertical = 4.dp).size(DIMENSIONE_INDICATORE).testTag(tag))
}

@Composable
private fun ErroreCarta(n: Int, messaggio: String, onChiudi: () -> Unit) {
    val colori = LocalSnastroColori.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("voce-$n-errore")) {
        Text(
            messaggio,
            color = colori.danger,
            style = LocalSnastroTipografia.current.body,
            modifier = Modifier.weight(1f),
        )
        Text(
            ETICHETTA_CHIUDI_ERRORE,
            style = LocalSnastroTipografia.current.label,
            color = colori.accentInk,
            modifier = Modifier.padding(start = SnastroMisure.space2).clickable(onClick = onChiudi)
                .testTag("voce-$n-errore-chiudi"),
        )
    }
}

/** Only [ContenutoCarta.DaIdentificare] has its own actions row — a named card's 'More' already sits
 * in [IntestazioneCarta] ('compact head only', AC-585). [primario] marks the ONE card whose action may
 * render Primario (rework cycle 1, HIGH-3: at most one Primario per screen). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AzioniCarta(
    carta: CartaVoce,
    pannello: PannelloVoci,
    primario: Boolean,
    azioni: AzioniRegistrazione,
    onNuovo: () -> Unit,
) {
    val contenuto = carta.contenuto as? ContenutoCarta.DaIdentificare ?: return
    FlowRow(
        verticalArrangement = Arrangement.Center,
        horizontalArrangement = Arrangement.spacedBy(SnastroMisure.space2),
    ) {
        AzioniDaIdentificare(carta, contenuto, pannello, carta.azioniAbilitate, primario, azioni, onNuovo)
    }
}

/**
 * AC-213/AC-585: [primario] picks which of this card's own actions is the Primario one — 'Dai un nome'
 * always is (the one action of a first-recording card); otherwise 'È <Nome>' is Primario UNLESS
 * `nuovoEvidenziato` (all Candidati 'nessuna'), in which case 'Nuova persona' takes the Primario spot
 * instead (AC-213) — the other of the two stays Secondario either way, and both stay Secondario when
 * [primario] is `false` (a later card in the list, HIGH-3).
 */
@Suppress("LongParameterList") // one parameter per action's own inputs (carta/contenuto/pannello read separately)
@Composable
private fun AzioniDaIdentificare(
    carta: CartaVoce,
    contenuto: ContenutoCarta.DaIdentificare,
    pannello: PannelloVoci,
    abilitate: Boolean,
    primario: Boolean,
    azioni: AzioniRegistrazione,
    onNuovo: () -> Unit,
) {
    val voceId = carta.voceId
    val n = voceId.numero
    val proposta = contenuto.proposta as? StatoProposta.Pronta
    if (contenuto.galleriaVuota) {
        // AC-585: first recording — no 'È <Nome>', no 'Altri' (no attivo Parlante exists yet).
        BottoneSn(
            ETICHETTA_DAI_UN_NOME,
            onClick = onNuovo,
            abilitato = abilitate,
            variante = if (primario) VarianteBottone.Primario else VarianteBottone.Secondario,
            piccolo = true,
            icona = Icona.Plus,
            modifier = Modifier.testTag("voce-$n-nuovo"),
        )
    } else {
        val primo = proposta?.candidati?.firstOrNull()
        val nuovoEvidenziato = proposta?.nuovoEvidenziato == true
        BottoneSn(
            primo?.let { testoConferma(it.nome) } ?: ETICHETTA_CONFERMA,
            onClick = { azioni.conferma(voceId) },
            abilitato = carta.confermaAbilitata,
            variante = if (primario && !nuovoEvidenziato) VarianteBottone.Primario else VarianteBottone.Secondario,
            piccolo = true,
            icona = Icona.Check,
            modifier = Modifier.testTag("voce-$n-conferma"),
        )
        MenuParlanti("voce-$n-altri", ETICHETTA_ALTRI, pannello.parlantiAttivi, abilitate, icona = Icona.ChevronDown) {
            azioni.confermaParlante(voceId, it)
        }
        BottoneSn(
            ETICHETTA_NUOVA_PERSONA,
            onClick = onNuovo,
            abilitato = abilitate,
            variante = if (primario && nuovoEvidenziato) VarianteBottone.Primario else VarianteBottone.Secondario,
            piccolo = true,
            icona = Icona.Plus,
            modifier = Modifier.testTag("voce-$n-nuovo"),
        )
    }
    BottoneSn(
        ETICHETTA_SALTA,
        onClick = { azioni.salta(voceId) },
        abilitato = abilitate,
        variante = VarianteBottone.Link,
        piccolo = true,
        modifier = Modifier.testTag("voce-$n-salta"),
    )
}

/**
 * AC-585/AC-216: EVERY card's own 'More' — 'Cambia' (the other attivo Parlanti, named cards only) then,
 * after a divider, 'Unisci con…' (this Voce's own siblings, ANY card — rework cycle 1, HIGH-2: it used
 * to exist only on named cards). AC-414 (L761a): a command pending on THIS card already turns [haCambia]
 * off (it folds in [CartaVoce.azioniAbilitate]) — the 'Cambia' entries are disabled by it too, so a
 * pending card never shows a dead, clickable-looking item the presenter would just drop. 'Unisci con…'
 * does NOT share that gate: its own items stay enabled by [PannelloVoci.unioneAbilitata] alone (a
 * PANEL-wide flag, never this one card's own pending state), and the button itself is kept open by
 * `unioneAbilitata && haUnisci` even when [haCambia] is `false` — a queued re-run (ADR 0018) never blocks
 * a merge. Keeps the pre-restyle `voce-$n-cambia` tag: same entry point, now a single icon button on
 * every card.
 */
@Composable
private fun MenuAltreAzioniCarta(
    carta: CartaVoce,
    contenuto: ContenutoCarta.Attribuita?,
    pannello: PannelloVoci,
    onNuovo: () -> Unit,
    azioni: AzioniRegistrazione,
) {
    var aperto by remember { mutableStateOf(false) }
    val n = carta.voceId.numero
    val haCambia = contenuto != null && carta.azioniAbilitate
    val haUnisci = carta.altreVoci.isNotEmpty() || carta.vociAltreParti.isNotEmpty()
    val altriParlanti = contenuto?.let { c -> pannello.parlantiAttivi.filter { it.parlanteId != c.parlanteId } }
        .orEmpty()
    Box {
        BottoneIconaSn(
            Icona.More,
            "Altre azioni",
            onClick = { aperto = true },
            abilitato = haCambia || (pannello.unioneAbilitata && haUnisci),
            piccolo = true,
            modifier = Modifier.testTag("voce-$n-cambia"),
        )
        MenuSn(expanded = aperto, onDismissRequest = { aperto = false }) {
            if (contenuto != null) {
                EtichettaGruppoMenu(ETICHETTA_CAMBIA)
                altriParlanti.forEach { p ->
                    DropdownMenuItem(
                        text = { EtichettaMenu(p.nome, etichettaTipo(p.tipoParlante)) },
                        // L761a/AC-414: a command pending on THIS card must not offer a 'Cambia' that the
                        // presenter would just drop (`invia` already ignores it) — disabled, not dead.
                        enabled = haCambia,
                        onClick = {
                            aperto = false
                            azioni.confermaParlante(carta.voceId, p.parlanteId)
                        },
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(ETICHETTA_NUOVA_PERSONA) },
                    enabled = haCambia,
                    onClick = {
                        aperto = false
                        onNuovo()
                    },
                )
            }
            if (haUnisci) {
                if (contenuto != null) HorizontalDivider()
                EtichettaGruppoMenu(ETICHETTA_UNISCI_CON)
                VociDaUnire(carta, pannello, azioni) { aperto = false }
            }
        }
    }
}

/**
 * AC-I77: the items of 'Unisci con…' — once the Incontro has Voci in other Parti, 'In questa parte' first, then
 * 'In altre parti' (each with its 'parte n'); over one Parte the flat list of today.
 */
@Composable
private fun VociDaUnire(carta: CartaVoce, pannello: PannelloVoci, azioni: AzioniRegistrazione, chiudi: () -> Unit) {
    val conGruppi = carta.vociAltreParti.isNotEmpty()
    if (conGruppi && carta.altreVoci.isNotEmpty()) EtichettaGruppoMenu(ETICHETTA_GRUPPO_QUESTA_PARTE)
    (carta.altreVoci + carta.vociAltreParti).forEachIndexed { i, v ->
        if (conGruppi && i == carta.altreVoci.size) EtichettaGruppoMenu(ETICHETTA_GRUPPO_ALTRE_PARTI)
        DropdownMenuItem(
            text = { EtichettaMenuVoce(v, pannello.carte) },
            enabled = pannello.unioneAbilitata,
            onClick = {
                chiudi()
                azioni.unisci(carta.voceId, v.voceId)
            },
            modifier = Modifier.testTag("voce-${carta.voceId.numero}-unisci-${v.voceId.numero}"),
        )
    }
}
