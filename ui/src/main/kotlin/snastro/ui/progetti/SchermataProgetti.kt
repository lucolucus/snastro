package snastro.ui.progetti

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import snastro.progetto.applicazione.letture.ProgettoVista
import snastro.ui.SnastroTema
import snastro.ui.formattaData
import snastro.ui.stile.BottoneSn
import snastro.ui.stile.CampoSn
import snastro.ui.stile.CardSn
import snastro.ui.stile.Icona
import snastro.ui.stile.IconaSn
import snastro.ui.stile.LocalSnastroColori
import snastro.ui.stile.LocalSnastroTipografia
import snastro.ui.stile.SnastroMisure
import snastro.ui.stile.VarianteBottone
import snastro.ui.testi.ETICHETTA_APRI_PROGETTO
import snastro.ui.testi.ETICHETTA_CAMBIA_CARTELLA
import snastro.ui.testi.ETICHETTA_CHIUDI_ERRORE
import snastro.ui.testi.ETICHETTA_CREA
import snastro.ui.testi.ETICHETTA_NOME_PROGETTO
import snastro.ui.testi.ETICHETTA_NUOVO_PROGETTO
import snastro.ui.testi.ETICHETTA_PROGETTI
import snastro.ui.testi.MESSAGGIO_PROGETTI_VUOTO
import snastro.ui.testi.etichettaRegistrazioni
import java.time.ZoneId
import javax.swing.JFileChooser

private val LARGHEZZA_CAMPO_NOME = 240.dp
private val DIMENSIONE_INDICATORE_PICCOLO = 18.dp

/**
 * Thin view of S1 · Progetti (RC-2): only renders [stato] and forwards [azioni]'s events — the
 * folder pickers below are OS integration, not a decision ([sceltaCartella] always hands its result
 * straight to an [azioni] lambda, never branches on it beyond null-cancelled). [cartellaGenitorePredefinita]
 * (ADR 0010: `~/Documents/snastro`) is `:avvio`'s own injected default for the new-project form's
 * initial value — fix-batch-12 #4: never `System.getProperty` inside this composable.
 *
 * AC-573: title + the "Nuovo progetto" `CardSn` form + "Apri progetto…" `Secondario`, then the list
 * (each project as its own `CardSn`) or an `EmptyState`-style placeholder when there are none — the
 * two actions stay reachable in both cases (they sit above the list, never duplicated inside it).
 */
@Composable
fun SchermataProgetti(
    stato: ProgettiUiStato,
    azioni: AzioniProgetti,
    cartellaGenitorePredefinita: String,
    scuro: Boolean = isSystemInDarkTheme(),
    riduciMovimento: Boolean? = null,
) {
    SnastroTema(scuro = scuro, riduciMovimento = riduciMovimento) {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (stato) {
                ProgettiUiStato.Caricamento -> IndicatoreCaricamentoProgetti()
                is ProgettiUiStato.Dati -> ContenutoProgetti(stato, azioni, cartellaGenitorePredefinita)
            }
        }
    }
}

@Composable
private fun IndicatoreCaricamentoProgetti() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.testTag("progetti-indicatore-caricamento"))
    }
}

@Composable
private fun ContenutoProgetti(
    stato: ProgettiUiStato.Dati,
    azioni: AzioniProgetti,
    cartellaGenitorePredefinita: String,
) {
    val colori = LocalSnastroColori.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = SnastroMisure.space5, horizontal = SnastroMisure.space6),
    ) {
        Text(text = ETICHETTA_PROGETTI, style = LocalSnastroTipografia.current.display, color = colori.ink)
        Spacer(modifier = Modifier.height(SnastroMisure.space5))
        FormNuovoProgetto(
            inCorso = stato.inCorso,
            erroreCrea = stato.erroreCrea,
            azioni = azioni,
            cartellaGenitorePredefinita = cartellaGenitorePredefinita,
        )
        Spacer(modifier = Modifier.height(SnastroMisure.space4))
        AzioneApriProgetto(inCorso = stato.inCorso, erroreApri = stato.erroreApri, azioni = azioni)
        Spacer(modifier = Modifier.height(SnastroMisure.space5))
        if (stato.progetti.isEmpty()) {
            ProgettiVuoto()
        } else {
            ElencoProgettiLista(stato.progetti, abilitato = !stato.inCorso, apri = azioni.apri)
        }
    }
}

@Composable
private fun FormNuovoProgetto(
    inCorso: Boolean,
    erroreCrea: String?,
    azioni: AzioniProgetti,
    cartellaGenitorePredefinita: String,
) {
    val colori = LocalSnastroColori.current
    var cartella by remember { mutableStateOf(cartellaGenitorePredefinita) }
    var nome by remember { mutableStateOf("") }

    CardSn(modifier = Modifier.fillMaxWidth()) {
        Text(text = ETICHETTA_NUOVO_PROGETTO, style = LocalSnastroTipografia.current.heading, color = colori.ink)
        Spacer(modifier = Modifier.height(SnastroMisure.space3))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CampoSn(
                valore = nome,
                onValoreCambiato = { nome = it },
                etichetta = ETICHETTA_NOME_PROGETTO,
                abilitato = !inCorso,
                modifier = Modifier.width(LARGHEZZA_CAMPO_NOME).testTag("progetti-campo-nome"),
            )
            BottoneSn(
                etichetta = ETICHETTA_CAMBIA_CARTELLA,
                onClick = { sceltaCartella(cartella)?.let { cartella = it } },
                variante = VarianteBottone.Link,
                abilitato = !inCorso,
                modifier = Modifier.padding(start = SnastroMisure.space3),
            )
        }
        Spacer(modifier = Modifier.height(SnastroMisure.space1))
        Text(
            text = cartella,
            style = LocalSnastroTipografia.current.caption,
            color = colori.inkMuted,
            modifier = Modifier.testTag("progetti-cartella"),
        )
        Spacer(modifier = Modifier.height(SnastroMisure.space3))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BottoneSn(
                etichetta = ETICHETTA_CREA,
                onClick = { azioni.crea(cartella, nome) },
                variante = VarianteBottone.Primario,
                abilitato = !inCorso,
                modifier = Modifier.testTag("progetti-crea"),
            )
            if (inCorso) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(start = SnastroMisure.space2).size(DIMENSIONE_INDICATORE_PICCOLO)
                        .testTag("progetti-operazione-in-corso"),
                )
            }
        }
        erroreCrea?.let { MessaggioInlineErrore(it, azioni.chiudiErroreCrea, "progetti-errore-crea") }
    }
}

@Composable
private fun AzioneApriProgetto(inCorso: Boolean, erroreApri: String?, azioni: AzioniProgetti) {
    Column {
        BottoneSn(
            etichetta = ETICHETTA_APRI_PROGETTO,
            onClick = { sceltaCartella(System.getProperty("user.home").orEmpty())?.let { azioni.apri(it) } },
            variante = VarianteBottone.Secondario,
            abilitato = !inCorso,
            modifier = Modifier.testTag("progetti-apri"),
        )
        erroreApri?.let { MessaggioInlineErrore(it, azioni.chiudiErroreApri, "progetti-errore-apri") }
    }
}

@Composable
private fun MessaggioInlineErrore(messaggio: String, onChiudi: () -> Unit, tag: String) {
    val colori = LocalSnastroColori.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = SnastroMisure.space2).testTag(tag),
    ) {
        Text(
            text = messaggio,
            color = colori.danger,
            style = LocalSnastroTipografia.current.caption,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            text = ETICHETTA_CHIUDI_ERRORE,
            color = colori.accentInk,
            style = LocalSnastroTipografia.current.label,
            modifier = Modifier
                .padding(start = SnastroMisure.space2)
                .clickable(onClick = onChiudi)
                .testTag("$tag-chiudi"),
        )
    }
}

/** AC-573: EmptyState (Reel icon, [MESSAGGIO_PROGETTI_VUOTO]) — the two actions stay above, reachable
 * whether the list is empty or not, so they are not duplicated here. */
@Composable
private fun ProgettiVuoto() {
    val colori = LocalSnastroColori.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = SnastroMisure.space6).testTag("progetti-vuoto"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconaSn(Icona.Reel, descrizione = null, tinta = colori.inkFaint, dimensione = SnastroMisure.space6)
        Spacer(modifier = Modifier.height(SnastroMisure.space2))
        Text(text = MESSAGGIO_PROGETTI_VUOTO, style = LocalSnastroTipografia.current.body, color = colori.inkMuted)
    }
}

@Composable
private fun ElencoProgettiLista(progetti: List<ProgettoVista>, abilitato: Boolean, apri: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("progetti-lista"),
        verticalArrangement = Arrangement.spacedBy(SnastroMisure.space3),
    ) {
        items(progetti, key = { it.progettoId.valore }) { progetto ->
            RigaProgetto(progetto, abilitato, apri)
        }
    }
}

/** AC-573: one project = one `CardSn` (name `heading`, "n registrazioni · ultima attività" `caption`). */
@Composable
private fun RigaProgetto(progetto: ProgettoVista, abilitato: Boolean, apri: (String) -> Unit) {
    val colori = LocalSnastroColori.current
    CardSn(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = abilitato) { apri(progetto.percorso) }
            .testTag("progetti-riga-${progetto.progettoId.valore}"),
    ) {
        Text(text = progetto.nome, style = LocalSnastroTipografia.current.heading, color = colori.ink)
        val dataUltimaAttivita = formattaData(progetto.ultimaAttivita.atZone(ZoneId.systemDefault()).toLocalDate())
        Text(
            text = "${etichettaRegistrazioni(progetto.numRegistrazioni)} · $dataUltimaAttivita",
            style = LocalSnastroTipografia.current.caption,
            color = colori.inkMuted,
        )
    }
}

/** Native directory picker (frugality rung 3: platform-native over a hand-rolled dialog); `null` = cancelled. */
private fun sceltaCartella(cartellaIniziale: String): String? {
    val selettore = JFileChooser(cartellaIniziale.ifBlank { null }).apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
    }
    return if (selettore.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        selettore.selectedFile.absolutePath
    } else {
        null
    }
}
