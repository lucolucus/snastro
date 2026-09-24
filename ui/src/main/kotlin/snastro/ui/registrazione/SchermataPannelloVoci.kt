// TooManyFunctions: the panel split into small single-purpose composables (RC-2 thin view).
@file:Suppress("TooManyFunctions")

package snastro.ui.registrazione

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import snastro.kernel.ParlanteId
import snastro.kernel.VoceId
import snastro.parlanti.applicazione.eventi.TipoParlanteVista
import snastro.parlanti.applicazione.letture.Candidato
import snastro.parlanti.applicazione.letture.ParlanteAttivo
import snastro.parlanti.applicazione.letture.PropostaDiUnione
import snastro.parlanti.applicazione.porte.Fascia
import snastro.ui.palette
import snastro.ui.stile.LocalSnastroColori
import snastro.ui.testi.DESCRIZIONE_FASCIA_DEBOLE
import snastro.ui.testi.DESCRIZIONE_FASCIA_FORTE
import snastro.ui.testi.DESCRIZIONE_FASCIA_NESSUNA
import snastro.ui.testi.ETICHETTA_ALTRI
import snastro.ui.testi.ETICHETTA_ANNULLA
import snastro.ui.testi.ETICHETTA_CAMBIA
import snastro.ui.testi.ETICHETTA_CHIUDI_ERRORE
import snastro.ui.testi.ETICHETTA_CONFERMA
import snastro.ui.testi.ETICHETTA_CREA
import snastro.ui.testi.ETICHETTA_ESTRATTO
import snastro.ui.testi.ETICHETTA_NOME
import snastro.ui.testi.ETICHETTA_NUOVO
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
import snastro.ui.testi.testoUnione

/** Fixed width of the Voci panel: the transcript takes the rest (sizing, render-check at 1024x640). */
internal val LARGHEZZA_PANNELLO_VOCI = 360.dp
private val SPAZIO = 8.dp
private val PADDING_CARTA = 12.dp
private val DIMENSIONE_PALLINO = 10.dp
private val DIMENSIONE_INDICATORE = 18.dp
private val LARGHEZZA_TACCA_FASCIA = 14.dp
private val ALTEZZA_TACCA_FASCIA = 8.dp
private val LARGHEZZA_NOME_CANDIDATO = 120.dp
private const val TACCHE_FASCIA = 3

/**
 * Thin view of S3's Voci panel (R2, RC-2): renders [pannello] — every enabled/disabled decision is the
 * presenter's ([CartaVoce.azioniAbilitate], [CartaVoce.confermaAbilitata], [PannelloVoci.estrattiDisponibili],
 * [PannelloVoci.unioneAbilitata]) — and forwards [azioni]. Only open/closed menus and the 'nuovo…' text
 * being typed are view-local.
 */
@Composable
internal fun PannelloVociVista(pannello: PannelloVoci, azioni: AzioniRegistrazione, modifier: Modifier = Modifier) {
    Column(modifier = modifier.testTag("voci-pannello")) {
        Text(TITOLO_PANNELLO_VOCI, style = MaterialTheme.typography.titleMedium)
        pannello.somiglianza?.let { SezioneSomiglianza(it, azioni) }
        if (!pannello.estrattiDisponibili) {
            Text(
                text = MESSAGGIO_ESTRATTI_NON_DISPONIBILI,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("voci-estratti-non-disponibili"),
            )
        }
        Spacer(Modifier.height(SPAZIO))
        LazyColumn(modifier = Modifier.fillMaxSize().testTag("voci-lista")) {
            items(pannello.unioni, key = { "unione-${it.voceA.numero}-${it.voceB.numero}" }) { unione ->
                BannerUnione(unione, pannello.unioneAbilitata, azioni)
            }
            items(pannello.carte, key = { it.voceId.numero }) { carta -> CartaVoceVista(carta, pannello, azioni) }
        }
    }
}

/** AC-216: one click merges (never automatic); it disappears when the presenter drops it. */
@Composable
private fun BannerUnione(unione: PropostaDiUnione, abilitata: Boolean, azioni: AzioniRegistrazione) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(SPAZIO),
        modifier = Modifier.fillMaxWidth().padding(bottom = SPAZIO)
            .testTag("voci-unione-${unione.voceA.numero}-${unione.voceB.numero}"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = PADDING_CARTA)) {
            Text(
                text = testoUnione(unione.voceA.numero, unione.voceB.numero, unione.nome),
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { azioni.unisci(unione.voceA, unione.voceB) }, enabled = abilitata) {
                Text(ETICHETTA_UNISCI)
            }
        }
    }
}

@Composable
private fun CartaVoceVista(carta: CartaVoce, pannello: PannelloVoci, azioni: AzioniRegistrazione) {
    val n = carta.voceId.numero
    var nuovoAperto by remember(n) { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(SPAZIO),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(bottom = SPAZIO).testTag("voce-$n"),
    ) {
        Column(modifier = Modifier.padding(PADDING_CARTA)) {
            IntestazioneCarta(carta, pannello.estrattiDisponibili, azioni)
            ContenutoCartaVista(carta, pannello.estrattiDisponibili, azioni)
            AttesaCarta(carta, azioni)
            carta.errore?.let { ErroreCarta(n, it) { azioni.chiudiErroreVoce(carta.voceId) } }
            AzioniCarta(carta, pannello, azioni, onNuovo = { nuovoAperto = !nuovoAperto })
            if (nuovoAperto && carta.azioniAbilitate) {
                ModuloNuovo("voce-$n") { nome, tipo ->
                    nuovoAperto = false
                    azioni.nuovoParlante(carta.voceId, nome, tipo)
                }
            }
        }
    }
}

@Composable
private fun IntestazioneCarta(carta: CartaVoce, estrattiDisponibili: Boolean, azioni: AzioniRegistrazione) {
    val colori = LocalSnastroColori.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(DIMENSIONE_PALLINO).background(palette(carta.voceId, colori), CircleShape))
        Spacer(Modifier.width(SPAZIO))
        Text(carta.titolo, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        TextButton(
            onClick = { azioni.riproduciEstrattoVoce(carta.voceId) },
            enabled = estrattiDisponibili,
            modifier = Modifier.testTag("voce-${carta.voceId.numero}-estratto"),
        ) { Text(ETICHETTA_ESTRATTO) }
    }
}

@Composable
private fun ContenutoCartaVista(carta: CartaVoce, estrattiDisponibili: Boolean, azioni: AzioniRegistrazione) {
    val n = carta.voceId.numero
    when (val contenuto = carta.contenuto) {
        ContenutoCarta.Caricamento -> Indicatore("voce-$n-caricamento")
        is ContenutoCarta.Errore -> Text(contenuto.messaggio, color = MaterialTheme.colorScheme.error)
        is ContenutoCarta.Attribuita -> Text(
            text = "${contenuto.nome} · ${etichettaTipo(contenuto.tipo)}",
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("voce-$n-nome"),
        )
        is ContenutoCarta.DaIdentificare -> when (val proposta = contenuto.proposta) {
            StatoProposta.Caricamento -> Indicatore("voce-$n-proposta-caricamento")
            StatoProposta.InAttesa -> Text(
                MESSAGGIO_PROPOSTA_IN_ATTESA,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("voce-$n-proposta-in-attesa"),
            )
            is StatoProposta.Errore -> Text(proposta.messaggio, color = MaterialTheme.colorScheme.error)
            is StatoProposta.Pronta -> {
                if (contenuto.galleriaVuota) {
                    Text(
                        SUGGERIMENTO_PRIMA_REGISTRAZIONE,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("voce-$n-prima-registrazione"),
                    )
                }
                proposta.candidati.forEachIndexed { i, c ->
                    RigaCandidato(n, i, c, estrattiDisponibili) { azioni.riproduciEstratto(c.estratto) }
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
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("voce-$n-candidato-$indice")) {
        Text(
            candidato.nome,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(LARGHEZZA_NOME_CANDIDATO),
        )
        Text(etichettaTipo(candidato.tipoParlante), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.width(SPAZIO))
        BarraFascia(candidato.fascia, Modifier.testTag("voce-$n-candidato-$indice-fascia"))
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onEstratto, enabled = estrattiDisponibili) { Text("▶") }
    }
}

/** AC-214: the Fascia as a bar (filled notches), never a number; the description is for accessibility. */
@Composable
private fun BarraFascia(fascia: Fascia, modifier: Modifier = Modifier) {
    val (piene, descrizione) = when (fascia) {
        Fascia.FORTE -> TACCHE_FASCIA to DESCRIZIONE_FASCIA_FORTE
        Fascia.DEBOLE -> 2 to DESCRIZIONE_FASCIA_DEBOLE
        Fascia.NESSUNA -> 0 to DESCRIZIONE_FASCIA_NESSUNA
    }
    Row(modifier = modifier.semantics { contentDescription = descrizione }) {
        repeat(TACCHE_FASCIA) { i ->
            val colore = if (i < piene) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            Box(
                Modifier.padding(end = 2.dp).size(LARGHEZZA_TACCA_FASCIA, ALTEZZA_TACCA_FASCIA)
                    .background(colore, RoundedCornerShape(2.dp)),
            )
        }
    }
}

/** AC-411/AC-412/AC-413: in corso → a progress indicator; past the threshold → the wait label + 'Annulla'. */
@Composable
private fun AttesaCarta(carta: CartaVoce, azioni: AzioniRegistrazione) {
    val n = carta.voceId.numero
    when (carta.inCorso) {
        null -> Unit
        AttesaComando.IN_CORSO -> Indicatore("voce-$n-in-corso")
        AttesaComando.IN_ATTESA -> Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.testTag("voce-$n-in-attesa"),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(DIMENSIONE_INDICATORE))
            Spacer(Modifier.width(SPAZIO))
            Text(MESSAGGIO_COMANDO_IN_ATTESA, modifier = Modifier.weight(1f))
            TextButton(
                onClick = { azioni.annullaComando(carta.voceId) },
                modifier = Modifier.testTag("voce-$n-annulla"),
            ) { Text(ETICHETTA_ANNULLA) }
        }
    }
}

@Composable
private fun Indicatore(tag: String) {
    CircularProgressIndicator(modifier = Modifier.padding(vertical = 4.dp).size(DIMENSIONE_INDICATORE).testTag(tag))
}

@Composable
private fun ErroreCarta(n: Int, messaggio: String, onChiudi: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("voce-$n-errore")) {
        Text(messaggio, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
        Text(
            ETICHETTA_CHIUDI_ERRORE,
            modifier = Modifier.padding(start = SPAZIO).clickable(onClick = onChiudi).testTag("voce-$n-errore-chiudi"),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AzioniCarta(carta: CartaVoce, pannello: PannelloVoci, azioni: AzioniRegistrazione, onNuovo: () -> Unit) {
    val n = carta.voceId.numero
    val abilitate = carta.azioniAbilitate
    FlowRow(verticalArrangement = Arrangement.Center) {
        when (val contenuto = carta.contenuto) {
            is ContenutoCarta.DaIdentificare -> {
                TextButton(
                    onClick = { azioni.conferma(carta.voceId) },
                    enabled = carta.confermaAbilitata,
                    modifier = Modifier.testTag("voce-$n-conferma"),
                ) { Text(ETICHETTA_CONFERMA) }
                if (!contenuto.galleriaVuota) {
                    MenuParlanti("voce-$n-altri", ETICHETTA_ALTRI, pannello.parlantiAttivi, abilitate) {
                        azioni.confermaParlante(carta.voceId, it)
                    }
                }
                val evidenziato = (contenuto.proposta as? StatoProposta.Pronta)?.nuovoEvidenziato == true
                PulsanteNuovo(n, evidenziato, abilitate, onNuovo)
                TextButton(
                    onClick = { azioni.salta(carta.voceId) },
                    enabled = abilitate,
                    modifier = Modifier.testTag("voce-$n-salta"),
                ) { Text(ETICHETTA_SALTA) }
            }
            is ContenutoCarta.Attribuita -> {
                MenuParlanti(
                    "voce-$n-cambia",
                    ETICHETTA_CAMBIA,
                    pannello.parlantiAttivi.filter { it.parlanteId != contenuto.parlanteId },
                    abilitate,
                    conNuovo = onNuovo,
                ) { azioni.confermaParlante(carta.voceId, it) }
            }
            ContenutoCarta.Caricamento, is ContenutoCarta.Errore -> Unit
        }
        MenuVoci("voce-$n-unisci-con", ETICHETTA_UNISCI_CON, carta.altreVoci, pannello.unioneAbilitata) { rimossa ->
            rimossa?.let { azioni.unisci(carta.voceId, it) }
        }
    }
}

@Composable
private fun PulsanteNuovo(n: Int, evidenziato: Boolean, abilitato: Boolean, onClick: () -> Unit) {
    val tag = Modifier.testTag("voce-$n-nuovo")
    // AC-213: all Candidati 'nessuna' → 'nuovo…' is the visually preferred action.
    if (evidenziato) {
        Button(onClick = onClick, enabled = abilitato, modifier = tag) { Text(ETICHETTA_NUOVO) }
    } else {
        TextButton(onClick = onClick, enabled = abilitato, modifier = tag) { Text(ETICHETTA_NUOVO) }
    }
}

@Suppress("LongParameterList") // one parameter per menu attribute + its two callbacks
@Composable
internal fun MenuParlanti(
    tag: String,
    etichetta: String,
    parlanti: List<ParlanteAttivo>,
    abilitato: Boolean,
    conNuovo: (() -> Unit)? = null,
    onScelta: (ParlanteId) -> Unit,
) {
    var aperto by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { aperto = true }, enabled = abilitato, modifier = Modifier.testTag(tag)) {
            Text(etichetta)
        }
        DropdownMenu(expanded = aperto, onDismissRequest = { aperto = false }) {
            parlanti.forEach { p ->
                DropdownMenuItem(
                    text = { Text("${p.nome} · ${etichettaTipo(p.tipoParlante)}") },
                    onClick = {
                        aperto = false
                        onScelta(p.parlanteId)
                    },
                )
            }
            conNuovo?.let { nuovo ->
                DropdownMenuItem(text = { Text(ETICHETTA_NUOVO) }, onClick = {
                    aperto = false
                    nuovo()
                })
            }
        }
    }
}

@Suppress("LongParameterList") // one parameter per menu attribute + its callback
@Composable
internal fun MenuVoci(
    tag: String,
    etichetta: String,
    voci: List<OpzioneVoce>,
    abilitato: Boolean,
    extra: String? = null,
    onScelta: (VoceId?) -> Unit,
) {
    var aperto by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { aperto = true },
            enabled = abilitato && (voci.isNotEmpty() || extra != null),
            modifier = Modifier.testTag(tag),
        ) { Text(etichetta) }
        DropdownMenu(expanded = aperto, onDismissRequest = { aperto = false }) {
            voci.forEach { v ->
                DropdownMenuItem(text = { Text(v.etichetta) }, onClick = {
                    aperto = false
                    onScelta(v.voceId)
                })
            }
            extra?.let { testo ->
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
    Column(modifier = Modifier.testTag("$prefisso-modulo-nuovo")) {
        OutlinedTextField(
            value = nome,
            onValueChange = { nome = it },
            label = { Text(ETICHETTA_NOME) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("$prefisso-nuovo-nome"),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = occasionale, onCheckedChange = { occasionale = it })
            Text(ETICHETTA_OCCASIONALE, modifier = Modifier.weight(1f))
            Button(
                onClick = {
                    onCrea(nome, if (occasionale) TipoParlanteVista.OCCASIONALE else TipoParlanteVista.RICORRENTE)
                },
                modifier = Modifier.testTag("$prefisso-nuovo-crea"),
            ) { Text(ETICHETTA_CREA) }
        }
    }
}

private fun etichettaTipo(tipo: TipoParlanteVista): String = when (tipo) {
    TipoParlanteVista.RICORRENTE -> ETICHETTA_RICORRENTE
    TipoParlanteVista.OCCASIONALE -> ETICHETTA_OCCASIONALE
}
