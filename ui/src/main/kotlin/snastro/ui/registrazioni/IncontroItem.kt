package snastro.ui.registrazioni

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import snastro.ui.formattaData
import snastro.ui.formattaDurata
import snastro.ui.stile.BottoneIconaSn
import snastro.ui.stile.BottoneSn
import snastro.ui.stile.CampoNumeroPersone
import snastro.ui.stile.ChipStato
import snastro.ui.stile.Icona
import snastro.ui.stile.LocalSnastroColori
import snastro.ui.stile.LocalSnastroTipografia
import snastro.ui.stile.MenuAltreAzioni
import snastro.ui.stile.MessaggioInlineErrore
import snastro.ui.stile.SnastroMisure
import snastro.ui.stile.TipoChipStato
import snastro.ui.stile.VarianteBottone
import snastro.ui.testi.ETICHETTA_ANNULLA
import snastro.ui.testi.ETICHETTA_COMPRIMI_PARTI
import snastro.ui.testi.ETICHETTA_DA_IDENTIFICARE
import snastro.ui.testi.ETICHETTA_ESPANDI_PARTI
import snastro.ui.testi.etichettaParteInCoda
import snastro.ui.testi.etichettaParteInCorso
import snastro.ui.testi.etichettaParteNonRiuscita
import snastro.ui.testi.etichettaTrascriviParti
import snastro.ui.testi.titoloIncontro

/**
 * AC-I66..I68/INV-I3: a 1-part Incontro IS its Parte's row, today's, only its More menu gains 'Aggiungi parti…';
 * a multi-part one is [RigaIncontroItem] and, when expanded, its Parti as indented sub-rows in Parte order.
 */
@Composable
internal fun IncontroItem(
    incontro: RigaIncontro,
    parti: List<RigaRegistrazione>,
    azioni: AzioniRegistrazioni,
    sceltaFileAudio: SceltaFileAudio,
) {
    if (parti.isEmpty()) return
    val aggiungiParti: () -> Unit = {
        val percorsi = sceltaFileAudio.scegli()
        if (percorsi.isNotEmpty()) azioni.aggiungiParti(incontro.incontroId, incontro.titolo, percorsi)
    }
    if (!incontro.multiParte || parti.size == 1) {
        RigaRegistrazioneItem(
            parti.first(),
            azioni,
            aggiungiParti.takeIf { incontro.aggiungiPartiDisponibile },
            incontro.incontroId,
        )
        return
    }
    val colori = LocalSnastroColori.current
    RigaIncontroItem(incontro, parti.first(), azioni, aggiungiParti)
    if (incontro.espanso) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = SnastroMisure.space6)
                .testTag("registrazioni-incontro-parti-${incontro.incontroId.valore}"),
        ) {
            parti.forEach { parte ->
                key(parte.registrazioneId) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(colori.line))
                    RigaRegistrazioneItem(parte, azioni, null, incontro.incontroId)
                }
            }
        }
    }
}

/** AC-I66: the collapsed row of a multi-part Incontro — chevron, '▶' of Parte 1, 'titolo · N parti', date · duration ·
 * badge, the aggregated state ([ColonnaIncontro]) and the More menu ('Aggiungi parti…'). A click opens S3 of
 * Parte 1. */
@Composable
private fun RigaIncontroItem(
    incontro: RigaIncontro,
    prima: RigaRegistrazione,
    azioni: AzioniRegistrazioni,
    aggiungiParti: () -> Unit,
) {
    val colori = LocalSnastroColori.current
    val id = incontro.incontroId.valore
    val sfondo = if (prima.riproduzione == StatoRiproduzioneRiga.InRiproduzione) colori.accentSoft else colori.raised
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(sfondo)
            .clickable(enabled = prima.trascrittoDisponibile) { azioni.apriRiga(prima.registrazioneId) }
            .padding(horizontal = SnastroMisure.space4, vertical = SnastroMisure.space3)
            .testTag("registrazioni-incontro-$id"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            BottoneIconaSn(
                icona = if (incontro.espanso) Icona.ChevronDown else Icona.ChevronRight,
                descrizione = if (incontro.espanso) ETICHETTA_COMPRIMI_PARTI else ETICHETTA_ESPANDI_PARTI,
                onClick = { azioni.espandiIncontro(incontro.incontroId) },
                modifier = Modifier.testTag("registrazioni-incontro-chevron-$id"),
            )
            ControlloRiproduzione(prima, azioni)
            Spacer(modifier = Modifier.width(SnastroMisure.space3))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = titoloIncontro(incontro.titolo, incontro.parti.size),
                    style = LocalSnastroTipografia.current.heading,
                    color = colori.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("registrazioni-incontro-titolo-$id"),
                )
                MetaIncontro(incontro)
            }
            Spacer(modifier = Modifier.width(SnastroMisure.space3))
            ColonnaIncontro(incontro, azioni)
            Spacer(modifier = Modifier.width(SnastroMisure.space2))
            MenuAltreAzioni("registrazioni-incontro-altre-azioni-$id", !incontro.operazioneInCorso) { chiudi ->
                VoceMenuAggiungiParti("registrazioni-menu-aggiungi-parti-$id") {
                    chiudi()
                    aggiungiParti()
                }
            }
        }
        incontro.errore?.let {
            MessaggioInlineErrore(
                it,
                { azioni.chiudiErroreIncontro(incontro.incontroId) },
                "registrazioni-errore-incontro-$id",
            )
        }
    }
}

@Composable
private fun MetaIncontro(incontro: RigaIncontro) {
    val colori = LocalSnastroColori.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SnastroMisure.space2),
    ) {
        Text(
            text = formattaData(incontro.data),
            style = LocalSnastroTipografia.current.caption,
            color = colori.inkMuted,
        )
        Separatore()
        Text(
            text = formattaDurata(incontro.durataMs),
            style = LocalSnastroTipografia.current.timecode,
            color = colori.inkMuted,
        )
        incontro.identificazione?.let {
            Separatore()
            BadgeIdentificazione(it, incontro.incontroId.valore)
        }
    }
}

/** AC-I67: the ONE 'Numero di persone' field and 'Trascrivi N parti' of an Incontro with Parti never started. */
@Composable
private fun AvvioIncontro(incontro: RigaIncontro, numParti: Int, azioni: AzioniRegistrazioni) {
    val id = incontro.incontroId.valore
    CampoNumeroPersone(
        valore = incontro.numeroPersone,
        onValoreCambiato = { azioni.modificaNumeroPersoneIncontro(incontro.incontroId, it) },
        errore = incontro.errore != null,
        abilitato = !incontro.operazioneInCorso,
        onInvio = { azioni.avviaElaborazioniIncontro(incontro.incontroId) },
        modifier = Modifier.testTag("registrazioni-incontro-numero-persone-$id"),
    )
    BottoneSn(
        etichetta = etichettaTrascriviParti(numParti),
        onClick = { azioni.avviaElaborazioniIncontro(incontro.incontroId) },
        variante = VarianteBottone.Primario,
        piccolo = true,
        abilitato = !incontro.operazioneInCorso,
        modifier = Modifier.testTag("registrazioni-incontro-trascrivi-$id"),
    )
}

/**
 * AC-I66: the aggregated state of a multi-part Incontro, one line like a row's ([ColonnaElaborazione]): a Parte in
 * progress / queued ('Annulla') / failed name the Parte; Parti never started offer the ONE 'Numero di persone'
 * field and 'Trascrivi N parti' (AC-I67); otherwise 'Trascritta' (or 'Da identificare' while Voci are unidentified).
 */
@Composable
private fun ColonnaIncontro(incontro: RigaIncontro, azioni: AzioniRegistrazioni) {
    val colori = LocalSnastroColori.current
    val id = incontro.incontroId.valore
    val tipografia = LocalSnastroTipografia.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SnastroMisure.space2),
        modifier = Modifier.testTag("registrazioni-incontro-stato-$id"),
    ) {
        when (val stato = incontro.stato) {
            is StatoIncontro.ParteInCorso -> Text(
                text = with(stato) { etichettaParteInCorso(numero, faseEtichetta, trascorsoMs, ritrascrizione) },
                style = tipografia.caption,
                color = colori.ink,
            )
            is StatoIncontro.ParteInCoda -> {
                Text(
                    text = etichettaParteInCoda(stato.numero, stato.posizione, stato.ritrascrizione),
                    style = tipografia.caption,
                    color = colori.ink,
                )
                BottoneSn(
                    etichetta = ETICHETTA_ANNULLA,
                    onClick = { azioni.annullaElaborazione(stato.registrazioneId) },
                    variante = VarianteBottone.Link,
                    piccolo = true,
                    modifier = Modifier.testTag("registrazioni-incontro-annulla-$id"),
                )
            }
            is StatoIncontro.ParteNonRiuscita ->
                ChipStato(TipoChipStato.NonRiuscita(etichettaParteNonRiuscita(stato.numero)))
            is StatoIncontro.DaTrascrivere -> AvvioIncontro(incontro, stato.numParti, azioni)
            StatoIncontro.Completata ->
                if ((incontro.identificazione?.numVociDaIdentificare ?: 0) > 0) {
                    ChipStato(TipoChipStato.Avviso(ETICHETTA_DA_IDENTIFICARE, Icona.People))
                } else {
                    ChipStato(TipoChipStato.Trascritta)
                }
            null -> Unit
        }
        if (incontro.operazioneInCorso) {
            CircularProgressIndicator(
                modifier = Modifier.width(DIMENSIONE_INDICATORE_PICCOLO)
                    .testTag("registrazioni-incontro-operazione-in-corso-$id"),
            )
        }
    }
}
