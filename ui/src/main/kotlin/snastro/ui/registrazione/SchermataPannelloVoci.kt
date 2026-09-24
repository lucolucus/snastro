// TooManyFunctions: the panel split into small single-purpose composables (RC-2 thin view).
@file:Suppress("TooManyFunctions")

package snastro.ui.registrazione

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
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
import snastro.kernel.ParlanteId
import snastro.kernel.VoceId
import snastro.parlanti.applicazione.eventi.TipoParlanteVista
import snastro.parlanti.applicazione.letture.Candidato
import snastro.parlanti.applicazione.letture.ParlanteAttivo
import snastro.parlanti.applicazione.letture.PropostaDiUnione
import snastro.ui.formattaDurata
import snastro.ui.stile.AzioneBanner
import snastro.ui.stile.BannerSn
import snastro.ui.stile.BottoneIconaSn
import snastro.ui.stile.BottoneSn
import snastro.ui.stile.CampoSn
import snastro.ui.stile.EtichettaVoce
import snastro.ui.stile.Icona
import snastro.ui.stile.LocalSnastroColori
import snastro.ui.stile.LocalSnastroTipografia
import snastro.ui.stile.MisuratoreFascia
import snastro.ui.stile.SnastroMisure
import snastro.ui.stile.TipoBanner
import snastro.ui.stile.VarianteBottone
import snastro.ui.testi.ETICHETTA_ALTRI
import snastro.ui.testi.ETICHETTA_ANNULLA
import snastro.ui.testi.ETICHETTA_CAMBIA
import snastro.ui.testi.ETICHETTA_CHIUDI_ERRORE
import snastro.ui.testi.ETICHETTA_CONFERMA
import snastro.ui.testi.ETICHETTA_CREA
import snastro.ui.testi.ETICHETTA_DAI_UN_NOME
import snastro.ui.testi.ETICHETTA_ESTRATTO
import snastro.ui.testi.ETICHETTA_NOME
import snastro.ui.testi.ETICHETTA_NUOVA_PERSONA
import snastro.ui.testi.ETICHETTA_OCCASIONALE
import snastro.ui.testi.ETICHETTA_RICORRENTE
import snastro.ui.testi.ETICHETTA_SALTA
import snastro.ui.testi.ETICHETTA_UNISCI
import snastro.ui.testi.ETICHETTA_UNISCI_CON
import snastro.ui.testi.MESSAGGIO_COMANDO_IN_ATTESA
import snastro.ui.testi.MESSAGGIO_ESTRATTI_NON_DISPONIBILI
import snastro.ui.testi.MESSAGGIO_PROPOSTA_IN_ATTESA
import snastro.ui.testi.SUGGERIMENTO_PRIMA_REGISTRAZIONE
import snastro.ui.testi.TITOLO_PANNELLO_VOCI
import snastro.ui.testi.testoConferma
import snastro.ui.testi.testoUnione

private val DIMENSIONE_INDICATORE = 18.dp
private val LARGHEZZA_NOME_CANDIDATO = 120.dp

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

/**
 * Thin view of S3's Voci panel (R2, RC-2): renders [pannello] — every enabled/disabled decision is the
 * presenter's ([CartaVoce.azioniAbilitate], [CartaVoce.confermaAbilitata], [PannelloVoci.estrattiDisponibili],
 * [PannelloVoci.unioneAbilitata]) — and forwards [azioni]. Only open/closed menus and the 'nuovo…' text
 * being typed are view-local. AC-584: the Riassunto tab is part B — [SchedaVoci] shows the single 'Voci'
 * tab of part A. [segmenti] is the transcript's own row list (already in `RegistrazioneUiStato`, no new
 * source) — AC-585's speaking-time caption sums each Voce's own durations from it (view arithmetic).
 */
@Composable
internal fun PannelloVociVista(
    pannello: PannelloVoci,
    segmenti: List<SegmentoRiga>,
    azioni: AzioniRegistrazione,
    modifier: Modifier = Modifier,
) {
    val colori = LocalSnastroColori.current
    val kDaIdentificare = pannello.carte.count { it.contenuto is ContenutoCarta.DaIdentificare }
    val durataPerVoce = remember(segmenti) {
        segmenti.groupBy { it.voceId }.mapValues { (_, segs) -> segs.sumOf { it.fineMs - it.inizioMs } }
    }
    // AC-213 + "at most one Primario per screen" (rework cycle 1, HIGH-3): only the FIRST DaIdentificare
    // card in list order gets its own action as Primario; every other one renders the same action
    // Secondario instead of stacking several Primario buttons on the same screen.
    val primaCartaAzione = pannello.carte.firstOrNull { it.contenuto is ContenutoCarta.DaIdentificare }?.voceId
    // AC-581: the tab chrome and the somiglianza card are ITEMS of the same LazyColumn, not a fixed
    // header measured before it — a header of unbounded height (the Calcolo/Anteprima phases can grow
    // past a single line) would otherwise starve the list of nearly all its space in the stacked
    // layout (a genuine render-check catch, not a hypothetical: it starved `voci-lista` down to a few
    // px and made `performScrollToNode` spin against a near-zero viewport).
    Box(modifier = modifier.testTag("voci-pannello")) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("voci-lista"),
            verticalArrangement = Arrangement.spacedBy(SnastroMisure.space3),
        ) {
            item {
                val titoloScheda = if (kDaIdentificare > 0) {
                    "$TITOLO_PANNELLO_VOCI · $kDaIdentificare da identificare"
                } else {
                    TITOLO_PANNELLO_VOCI
                }
                SchedaVoci(titoloScheda)
            }
            pannello.somiglianza?.let { s -> item { SezioneSomiglianza(s, azioni) } }
            if (!pannello.estrattiDisponibili) {
                item {
                    Text(
                        text = MESSAGGIO_ESTRATTI_NON_DISPONIBILI,
                        color = colori.danger,
                        style = LocalSnastroTipografia.current.caption,
                        modifier = Modifier.testTag("voci-estratti-non-disponibili"),
                    )
                }
            }
            items(pannello.unioni, key = { "unione-${it.voceA.numero}-${it.voceB.numero}" }) { unione ->
                BannerUnione(unione, pannello.unioneAbilitata, azioni)
            }
            items(pannello.carte, key = { it.voceId.numero }) { carta ->
                CartaVoceVista(
                    carta,
                    pannello,
                    durataPerVoce[carta.voceId] ?: 0L,
                    primario = carta.voceId == primaCartaAzione,
                    azioni,
                )
            }
        }
    }
}

/** AC-584: the right panel's segmented-control chrome — part A shows the single 'Voci' tab active
 * (the 'Riassunto' tab is part B, B3). */
@Composable
private fun SchedaVoci(testo: String) {
    val colori = LocalSnastroColori.current
    Surface(
        color = colori.sunken,
        shape = RoundedCornerShape(SnastroMisure.radiusControl),
        modifier = Modifier.fillMaxWidth().testTag("voci-schede"),
    ) {
        Box(modifier = Modifier.padding(3.dp).fillMaxWidth()) {
            Surface(
                color = colori.raised,
                shape = RoundedCornerShape(SnastroMisure.radiusControl),
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(modifier = Modifier.height(28.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(text = testo, style = LocalSnastroTipografia.current.label, color = colori.ink)
                }
            }
        }
    }
}

/** AC-216: one click merges (never automatic); it disappears when the presenter drops it. */
@Composable
private fun BannerUnione(unione: PropostaDiUnione, abilitata: Boolean, azioni: AzioniRegistrazione) {
    BannerSn(
        tipo = TipoBanner.Info,
        // The message IS the title (AC-216 has no separate heading); [ETICHETTA_UNISCI] names only
        // the action button below, so the two never collide on the same text (render-check AC-589).
        titolo = testoUnione(unione.voceA.numero, unione.voceB.numero, unione.nome),
        testo = "",
        modifier = Modifier.testTag("voci-unione-${unione.voceA.numero}-${unione.voceB.numero}"),
        azione = AzioneBanner(ETICHETTA_UNISCI) { azioni.unisci(unione.voceA, unione.voceB) }.takeIf { abilitata },
    )
}

@Composable
private fun CartaVoceVista(
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
            ContenutoCartaVista(carta, pannello, azioni)
            AttesaCarta(carta, azioni)
            carta.errore?.let { ErroreCarta(n, it) { azioni.chiudiErroreVoce(carta.voceId) } }
            AzioniCarta(carta, pannello, primario, azioni, onNuovo = { nuovoAperto = !nuovoAperto })
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
                        RigaCandidato(n, i, c, pannello.estrattiDisponibili) { azioni.riproduciEstratto(c.estratto) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RigaCandidato(
    n: Int,
    indice: Int,
    candidato: Candidato,
    estrattiDisponibili: Boolean,
    onEstratto: () -> Unit,
) {
    val colori = LocalSnastroColori.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("voce-$n-candidato-$indice")) {
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
 * to exist only on named cards). The merge items are gated ONLY on [PannelloVoci.unioneAbilitata], never
 * on the card's own `azioniAbilitate` (a queued re-run, ADR 0018, never blocks a merge). Keeps the
 * pre-restyle `voce-$n-cambia` tag: same entry point, now a single icon button on every card.
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
    val haUnisci = carta.altreVoci.isNotEmpty()
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
                        onClick = {
                            aperto = false
                            azioni.confermaParlante(carta.voceId, p.parlanteId)
                        },
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(ETICHETTA_NUOVA_PERSONA) },
                    onClick = {
                        aperto = false
                        onNuovo()
                    },
                )
            }
            if (haUnisci) {
                if (contenuto != null) HorizontalDivider()
                EtichettaGruppoMenu(ETICHETTA_UNISCI_CON)
                carta.altreVoci.forEach { v ->
                    DropdownMenuItem(
                        text = { EtichettaMenuVoce(v, pannello.carte) },
                        enabled = pannello.unioneAbilitata,
                        onClick = {
                            aperto = false
                            azioni.unisci(carta.voceId, v.voceId)
                        },
                    )
                }
            }
        }
    }
}

/** AC-586: a menu's own overline group label (`Menu.html`'s `.group`, e.g. 'Persone del progetto'). */
@Composable
private fun EtichettaGruppoMenu(testo: String) {
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
private fun EtichettaMenu(nome: String, tipo: String) {
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
private fun EtichettaMenuVoce(opzione: OpzioneVoce, carte: List<CartaVoce>) {
    val nome = (carte.find { it.voceId == opzione.voceId }?.contenuto as? ContenutoCarta.Attribuita)?.nome
    EtichettaVoce(voceId = opzione.voceId, nome = nome)
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
            Checkbox(
                checked = occasionale,
                onCheckedChange = { occasionale = it },
                modifier = Modifier.size(SnastroMisure.iconM).testTag("$prefisso-nuovo-occasionale"),
            )
            Spacer(Modifier.width(SnastroMisure.space2))
            Text(
                ETICHETTA_OCCASIONALE,
                style = LocalSnastroTipografia.current.body,
                modifier = Modifier.weight(1f),
            )
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

private fun etichettaTipo(tipo: TipoParlanteVista): String = when (tipo) {
    TipoParlanteVista.RICORRENTE -> ETICHETTA_RICORRENTE
    TipoParlanteVista.OCCASIONALE -> ETICHETTA_OCCASIONALE
}
