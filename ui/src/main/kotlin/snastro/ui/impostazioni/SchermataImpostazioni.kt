package snastro.ui.impostazioni

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import snastro.ui.SnastroTema
import snastro.ui.progetti.SceltaCartella
import snastro.ui.stile.AzioneBanner
import snastro.ui.stile.BannerSn
import snastro.ui.stile.BottoneSn
import snastro.ui.stile.CampoSn
import snastro.ui.stile.CardSn
import snastro.ui.stile.EmptyState
import snastro.ui.stile.Icona
import snastro.ui.stile.IconaSn
import snastro.ui.stile.LocalSnastroColori
import snastro.ui.stile.LocalSnastroTipografia
import snastro.ui.stile.SchedeSn
import snastro.ui.stile.SnastroMisure
import snastro.ui.stile.TipoBanner
import snastro.ui.stile.VarianteBottone
import snastro.ui.temaScuro
import snastro.ui.testi.DESCRIZIONE_CARTELLA_PROGETTI
import snastro.ui.testi.DESCRIZIONE_TEMA
import snastro.ui.testi.ETICHETTA_ANNULLA
import snastro.ui.testi.ETICHETTA_CAMBIA_CARTELLA_PROGETTI
import snastro.ui.testi.ETICHETTA_CHIUDI_ERRORE
import snastro.ui.testi.ETICHETTA_IMPOSTAZIONI
import snastro.ui.testi.ETICHETTA_INDIETRO
import snastro.ui.testi.ETICHETTA_PAROLE
import snastro.ui.testi.ETICHETTA_RIPRISTINA_PREDEFINITA
import snastro.ui.testi.ETICHETTA_SALVA
import snastro.ui.testi.ETICHETTA_SALVATA
import snastro.ui.testi.MESSAGGIO_RIASSUNTO_SENZA_PROGETTO
import snastro.ui.testi.TITOLO_ASPETTO
import snastro.ui.testi.TITOLO_CARTELLA_PROGETTI
import snastro.ui.testi.TITOLO_LUNGHEZZA_RIASSUNTO
import snastro.ui.testi.descrizioneLunghezzaRiassunto
import snastro.ui.testi.etichetta

private val LARGHEZZA_SEZIONI = 208.dp
private val LARGHEZZA_CONTENUTO_MAX = 640.dp
private val LARGHEZZA_SCHEDE_TEMA = 300.dp
private val LARGHEZZA_CAMPO_PAROLE = 120.dp
private val ALTEZZA_VOCE_SEZIONE = 34.dp
private val DIMENSIONE_INDICATORE_PICCOLO = 18.dp

/**
 * The open project's part of Impostazioni › Riassunto: [nomeProgetto] names it, [stato]/[azioni] are its
 * [LunghezzaRiassuntoPresenter]'s. `null` (no project open) renders a hint instead of the editor.
 */
class RiassuntoImpostazioni(
    val nomeProgetto: String,
    val stato: LunghezzaRiassuntoUiStato,
    val azioni: AzioniLunghezzaRiassunto,
)

/**
 * Thin view of the Impostazioni screen (RC-2): a left list of [SezioneImpostazioni] and the selected section —
 * Generali (theme, folder of new projects), Riassunto (the open project's lunghezza massima, [riassunto]) and
 * Modelli e licenze ([modelli], S5 plugged in as is). [onIndietro], when given, shows a back button (the screen is
 * then full-window, reached from S1); inside a project the shell's sidebar is the way out, so it is `null`.
 */
@Suppress("LongParameterList") // state + actions + the project part + the S5 slot + the port + the knobs
@Composable
fun SchermataImpostazioni(
    stato: ImpostazioniUiStato,
    azioni: AzioniImpostazioni,
    riassunto: RiassuntoImpostazioni?,
    sceltaCartella: SceltaCartella,
    modelli: @Composable () -> Unit,
    onIndietro: (() -> Unit)? = null,
    scuro: Boolean = temaScuro(),
    riduciMovimento: Boolean? = null,
) {
    SnastroTema(scuro = scuro, riduciMovimento = riduciMovimento) {
        val colori = LocalSnastroColori.current
        Surface(modifier = Modifier.fillMaxSize().testTag("impostazioni"), color = colori.surface) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = SnastroMisure.space5, horizontal = SnastroMisure.space6),
            ) {
                Intestazione(onIndietro)
                Spacer(modifier = Modifier.height(SnastroMisure.space5))
                stato.errore?.let {
                    BannerSn(
                        tipo = TipoBanner.Errore,
                        titolo = ETICHETTA_IMPOSTAZIONI,
                        testo = it,
                        azione = AzioneBanner(ETICHETTA_CHIUDI_ERRORE, azioni.chiudiErrore),
                        modifier = Modifier.testTag("impostazioni-errore"),
                    )
                    Spacer(modifier = Modifier.height(SnastroMisure.space4))
                }
                Row(modifier = Modifier.fillMaxSize()) {
                    ElencoSezioni(stato.sezione, azioni.seleziona)
                    Spacer(modifier = Modifier.width(SnastroMisure.space6))
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        when (stato.sezione) {
                            SezioneImpostazioni.GENERALI -> SezioneGenerali(stato, azioni, sceltaCartella)
                            SezioneImpostazioni.RIASSUNTO -> SezioneRiassunto(riassunto)
                            SezioneImpostazioni.MODELLI -> Box(modifier = Modifier.testTag("impostazioni-modelli")) {
                                modelli()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Intestazione(onIndietro: (() -> Unit)?) {
    val colori = LocalSnastroColori.current
    Column {
        if (onIndietro != null) {
            BottoneSn(
                etichetta = ETICHETTA_INDIETRO,
                onClick = onIndietro,
                variante = VarianteBottone.Fantasma,
                icona = Icona.ChevronLeft,
                piccolo = true,
                modifier = Modifier.testTag("impostazioni-indietro"),
            )
            Spacer(modifier = Modifier.height(SnastroMisure.space2))
        }
        Text(text = ETICHETTA_IMPOSTAZIONI, style = LocalSnastroTipografia.current.display, color = colori.ink)
    }
}

@Composable
private fun ElencoSezioni(selezionata: SezioneImpostazioni, onSeleziona: (SezioneImpostazioni) -> Unit) {
    Column(
        modifier = Modifier.width(LARGHEZZA_SEZIONI),
        verticalArrangement = Arrangement.spacedBy(SnastroMisure.space1),
    ) {
        SezioneImpostazioni.entries.forEach { sezione ->
            VoceSezione(sezione, selezionata = sezione == selezionata, onClick = { onSeleziona(sezione) })
        }
    }
}

/** Same chrome as the shell's nav item (AC-572): active = `raised` fill + `line` outline + `accentInk` icon. */
@Composable
private fun VoceSezione(sezione: SezioneImpostazioni, selezionata: Boolean, onClick: () -> Unit) {
    val colori = LocalSnastroColori.current
    val icona = when (sezione) {
        SezioneImpostazioni.GENERALI -> Icona.Settings
        SezioneImpostazioni.RIASSUNTO -> Icona.Summary
        SezioneImpostazioni.MODELLI -> Icona.Cube
    }
    val forma = RoundedCornerShape(SnastroMisure.radiusControl)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ALTEZZA_VOCE_SEZIONE)
            .clip(forma)
            .background(if (selezionata) colori.raised else Color.Transparent, forma)
            .let { if (selezionata) it.border(1.dp, colori.line, forma) else it }
            .clickable(onClick = onClick)
            .padding(horizontal = SnastroMisure.space3)
            .testTag("impostazioni-sezione-${sezione.name.lowercase()}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SnastroMisure.space2),
    ) {
        IconaSn(
            icona,
            descrizione = null,
            tinta = if (selezionata) colori.accentInk else colori.inkMuted,
            dimensione = SnastroMisure.iconM,
        )
        Text(
            text = etichetta(sezione),
            style = LocalSnastroTipografia.current.body.copy(
                fontWeight = if (selezionata) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = colori.ink,
        )
    }
}

@Composable
private fun ColonnaSezione(contenuto: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .widthIn(max = LARGHEZZA_CONTENUTO_MAX)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(SnastroMisure.space4),
    ) { contenuto() }
}

@Composable
private fun TitoloCard(titolo: String, descrizione: String) {
    val colori = LocalSnastroColori.current
    Text(text = titolo, style = LocalSnastroTipografia.current.heading, color = colori.ink)
    Spacer(modifier = Modifier.height(SnastroMisure.space1))
    Text(text = descrizione, style = LocalSnastroTipografia.current.caption, color = colori.inkMuted)
    Spacer(modifier = Modifier.height(SnastroMisure.space3))
}

@Composable
private fun SezioneGenerali(stato: ImpostazioniUiStato, azioni: AzioniImpostazioni, sceltaCartella: SceltaCartella) {
    ColonnaSezione {
        CardSn(modifier = Modifier.fillMaxWidth().testTag("impostazioni-tema")) {
            TitoloCard(TITOLO_ASPETTO, DESCRIZIONE_TEMA)
            val temi = TemaApp.entries
            SchedeSn(
                schede = temi.map { etichetta(it) },
                selezionata = temi.indexOf(stato.tema),
                onSeleziona = { azioni.cambiaTema(temi[it]) },
                modifier = Modifier.width(LARGHEZZA_SCHEDE_TEMA),
            )
        }
        CardSn(modifier = Modifier.fillMaxWidth().testTag("impostazioni-cartella-progetti")) {
            TitoloCard(TITOLO_CARTELLA_PROGETTI, DESCRIZIONE_CARTELLA_PROGETTI)
            PercorsoCartella(stato.cartellaProgetti)
            Spacer(modifier = Modifier.height(SnastroMisure.space3))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BottoneSn(
                    etichetta = ETICHETTA_CAMBIA_CARTELLA_PROGETTI,
                    onClick = {
                        sceltaCartella.scegli(TITOLO_CARTELLA_PROGETTI)?.let(azioni.cambiaCartellaProgetti)
                    },
                    variante = VarianteBottone.Secondario,
                    modifier = Modifier.testTag("impostazioni-cambia-cartella"),
                )
                if (stato.cartellaPersonalizzata) {
                    Spacer(modifier = Modifier.width(SnastroMisure.space2))
                    BottoneSn(
                        etichetta = ETICHETTA_RIPRISTINA_PREDEFINITA,
                        onClick = azioni.ripristinaCartellaProgetti,
                        variante = VarianteBottone.Link,
                        modifier = Modifier.testTag("impostazioni-ripristina-cartella"),
                    )
                }
            }
        }
    }
}

@Composable
private fun PercorsoCartella(percorso: String) {
    val colori = LocalSnastroColori.current
    val forma = RoundedCornerShape(SnastroMisure.radiusControl)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colori.sunken, forma)
            .padding(horizontal = SnastroMisure.space3, vertical = SnastroMisure.space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SnastroMisure.space2),
    ) {
        IconaSn(Icona.Reveal, descrizione = null, tinta = colori.inkMuted, dimensione = SnastroMisure.iconS)
        Text(
            text = percorso,
            style = LocalSnastroTipografia.current.body,
            color = colori.ink,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
            modifier = Modifier.testTag("impostazioni-percorso-cartella"),
        )
    }
}

@Composable
private fun SezioneRiassunto(riassunto: RiassuntoImpostazioni?) {
    ColonnaSezione {
        if (riassunto == null) {
            CardSn(modifier = Modifier.fillMaxWidth().testTag("impostazioni-riassunto-senza-progetto")) {
                EmptyState(MESSAGGIO_RIASSUNTO_SENZA_PROGETTO)
            }
            return@ColonnaSezione
        }
        CardSn(modifier = Modifier.fillMaxWidth().testTag("impostazioni-riassunto")) {
            when (val s = riassunto.stato) {
                LunghezzaRiassuntoUiStato.Caricamento ->
                    CircularProgressIndicator(modifier = Modifier.testTag("impostazioni-riassunto-caricamento"))
                is LunghezzaRiassuntoUiStato.Errore -> Text(
                    text = s.messaggio,
                    style = LocalSnastroTipografia.current.body,
                    color = LocalSnastroColori.current.danger,
                )
                is LunghezzaRiassuntoUiStato.Dati -> EditorLunghezza(riassunto.nomeProgetto, s, riassunto.azioni)
            }
        }
    }
}

@Composable
private fun EditorLunghezza(
    nomeProgetto: String,
    stato: LunghezzaRiassuntoUiStato.Dati,
    azioni: AzioniLunghezzaRiassunto,
) {
    val colori = LocalSnastroColori.current
    TitoloCard(TITOLO_LUNGHEZZA_RIASSUNTO, descrizioneLunghezzaRiassunto(nomeProgetto, stato.minimo, stato.massimo))
    Row(verticalAlignment = Alignment.Top) {
        CampoSn(
            valore = stato.testo,
            onValoreCambiato = azioni.cambia,
            etichetta = ETICHETTA_PAROLE,
            errore = stato.errore,
            abilitato = !stato.inCorso,
            modifier = Modifier.width(LARGHEZZA_CAMPO_PAROLE).testTag("impostazioni-campo-parole"),
        )
    }
    Spacer(modifier = Modifier.height(SnastroMisure.space3))
    Row(verticalAlignment = Alignment.CenterVertically) {
        BottoneSn(
            etichetta = ETICHETTA_SALVA,
            onClick = azioni.salva,
            variante = VarianteBottone.Primario,
            abilitato = stato.modificata && !stato.inCorso,
            modifier = Modifier.testTag("impostazioni-salva-parole"),
        )
        if (stato.modificata && !stato.inCorso) {
            Spacer(modifier = Modifier.width(SnastroMisure.space2))
            BottoneSn(
                etichetta = ETICHETTA_ANNULLA,
                onClick = azioni.ripristina,
                variante = VarianteBottone.Fantasma,
                modifier = Modifier.testTag("impostazioni-annulla-parole"),
            )
        }
        if (stato.inCorso) {
            CircularProgressIndicator(
                modifier = Modifier.padding(start = SnastroMisure.space2).size(DIMENSIONE_INDICATORE_PICCOLO),
            )
        }
        if (stato.conferma && !stato.modificata) {
            Spacer(modifier = Modifier.width(SnastroMisure.space3))
            IconaSn(Icona.Check, descrizione = null, tinta = colori.accentInk, dimensione = SnastroMisure.iconS)
            Spacer(modifier = Modifier.width(SnastroMisure.space1))
            Text(
                text = ETICHETTA_SALVATA,
                style = LocalSnastroTipografia.current.caption,
                color = colori.inkMuted,
                modifier = Modifier.testTag("impostazioni-parole-salvata"),
            )
        }
    }
}
