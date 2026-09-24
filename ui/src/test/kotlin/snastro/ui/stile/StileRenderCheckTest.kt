package snastro.ui.stile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import snastro.kernel.VoceId
import snastro.parlanti.applicazione.porte.Fascia
import snastro.ui.SnastroTema
import java.io.File
import javax.imageio.ImageIO

private const val LARGHEZZA_PX = 640
private const val ALTEZZA_PX = 480

/**
 * `:ui:renderCheck` (profile `ui_render_check`, AC-571): every shared `stile` component fixture in
 * BOTH [ColoriChiari] and [ColoriScuri] — PNGs `build/render-check/stile-<componente>-<tema>.png`.
 * Sizing/overflow/contrast/state-rendering per component (AC-561..570); the contrast pairs of
 * README §Colore are checked in [SnastroContrastoTest] — a plain unit test, no render harness
 * needed for a numeric ratio.
 */
@OptIn(ExperimentalTestApi::class)
@Tag("render")
class StileRenderCheckTest {
    private val outputDir = File("build/render-check").apply { mkdirs() }

    private fun sfondo(scuro: Boolean) = if (scuro) ColoriScuri.ground else ColoriChiari.ground

    private fun fissaggio(nomeComponente: String, scuro: Boolean, contenuto: @Composable () -> Unit) =
        runDesktopComposeUiTest(LARGHEZZA_PX, ALTEZZA_PX) {
            setContent {
                SnastroTema(scuro = scuro) {
                    Surface(color = sfondo(scuro)) {
                        Column(Modifier.padding(SnastroMisure.space4)) { contenuto() }
                    }
                }
            }
            catturaPng(nomeComponente, scuro)
        }

    @Test
    fun `AC-562 bottoni chiaro`() = verificaBottoni(scuro = false)

    @Test
    fun `AC-562 bottoni scuro`() = verificaBottoni(scuro = true)

    private fun verificaBottoni(scuro: Boolean) = fissaggio("bottoni", scuro) {
        Row(horizontalArrangement = Arrangement.spacedBy(SnastroMisure.space2)) {
            BottoneSn("Trascrivi", {}, variante = VarianteBottone.Primario, icona = Icona.Play)
            BottoneSn("Importa audio", {}, variante = VarianteBottone.Secondario)
            BottoneSn("Salta", {}, variante = VarianteBottone.Fantasma)
            BottoneSn("Annulla", {}, variante = VarianteBottone.Link)
            BottoneSn("Elimina", {}, variante = VarianteBottone.Pericolo)
            BottoneSn("Trascrivi", {}, variante = VarianteBottone.Primario, abilitato = false)
        }
    }

    @Test
    fun `AC-563 bottone icona chiaro`() = verificaBottoneIcona(scuro = false)

    @Test
    fun `AC-563 bottone icona scuro`() = verificaBottoneIcona(scuro = true)

    private fun verificaBottoneIcona(scuro: Boolean) = fissaggio("bottone-icona", scuro) {
        Row(horizontalArrangement = Arrangement.spacedBy(SnastroMisure.space2)) {
            BottoneIconaSn(Icona.More, "Altre azioni", {})
            BottoneIconaSn(Icona.Trash, "Elimina", {}, piccolo = true)
            BottoneIconaSn(Icona.Edit, "Modifica", {}, abilitato = false)
        }
    }

    @Test
    fun `AC-564 pulsante play chiaro`() = verificaPlay(scuro = false)

    @Test
    fun `AC-564 pulsante play scuro`() = verificaPlay(scuro = true)

    private fun verificaPlay(scuro: Boolean) = fissaggio("play", scuro) {
        Row(horizontalArrangement = Arrangement.spacedBy(SnastroMisure.space2)) {
            BottonePlay(inRiproduzione = false, onClick = {}, grande = true)
            BottonePlay(inRiproduzione = true, onClick = {}, grande = true)
            BottonePlay(inRiproduzione = false, onClick = {}, grande = false)
            BottonePlay(inRiproduzione = false, onClick = {}, grande = true, abilitato = false)
        }
    }

    @Test
    fun `AC-565 chip di stato chiaro`() = verificaChip(scuro = false)

    @Test
    fun `AC-565 chip di stato scuro`() = verificaChip(scuro = true)

    private fun verificaChip(scuro: Boolean) = fissaggio("chip-stato", scuro) {
        Column(verticalArrangement = Arrangement.spacedBy(SnastroMisure.space2)) {
            ChipStato(TipoChipStato.DaTrascrivere)
            ChipStato(TipoChipStato.InCoda(2))
            ChipStato(TipoChipStato.InCorso("Separazione voci", 192_000))
            ChipStato(TipoChipStato.Trascritta)
            ChipStato(TipoChipStato.NonRiuscita)
            ChipStato(TipoChipStato.Avviso("Da identificare", Icona.Alert))
        }
    }

    @Test
    fun `AC-565 il testo di InCoda mostra la posizione`() = runDesktopComposeUiTest {
        setContent { SnastroTema { ChipStato(TipoChipStato.InCoda(2)) } }
        onNodeWithText("In coda · 2").assertIsDisplayed()
    }

    @Test
    fun `AC-565 InCorso mostra la fase e il tempo trascorso in formato AC-557`() = runDesktopComposeUiTest {
        setContent { SnastroTema { ChipStato(TipoChipStato.InCorso("Separazione voci", 192_000)) } }
        onNodeWithText("Separazione voci").assertIsDisplayed()
        onNodeWithText("3:12").assertIsDisplayed()
    }

    @Test
    fun `AC-566 banner chiaro`() = verificaBanner(scuro = false)

    @Test
    fun `AC-566 banner scuro`() = verificaBanner(scuro = true)

    private fun verificaBanner(scuro: Boolean) = fissaggio("banner", scuro) {
        Column(verticalArrangement = Arrangement.spacedBy(SnastroMisure.space3)) {
            BannerSn(TipoBanner.Info, "1 voce da identificare", "Guarda il pannello Voci per dare un nome.")
            BannerSn(
                TipoBanner.Avviso,
                "Sola lettura",
                "La trascrizione è in corso di aggiornamento.",
                azione = AzioneBanner("Annulla") {},
            )
            BannerSn(TipoBanner.Errore, "Importazione non riuscita", "Il file audio non si apre.")
        }
    }

    @Test
    fun `AC-567 campi chiaro`() = verificaCampi(scuro = false)

    @Test
    fun `AC-567 campi scuro`() = verificaCampi(scuro = true)

    private fun verificaCampi(scuro: Boolean) = fissaggio("campi", scuro) {
        Column(verticalArrangement = Arrangement.spacedBy(SnastroMisure.space3)) {
            CampoSn(valore = "", onValoreCambiato = {}, etichetta = "Nome", placeholder = "Marco")
            CampoSn(valore = "Marco", onValoreCambiato = {}, etichetta = "Nome", errore = "Questo nome è già in uso.")
            CampoNumeroPersone(valore = "", onValoreCambiato = {})
            CampoNumeroPersone(valore = "3", onValoreCambiato = {})
        }
    }

    @Test
    fun `AC-568 card chiaro`() = verificaCard(scuro = false)

    @Test
    fun `AC-568 card scuro`() = verificaCard(scuro = true)

    private fun verificaCard(scuro: Boolean) = fissaggio("card", scuro) {
        CardSn { EtichettaVoce(VoceId(1), nome = "Marco") }
    }

    @Test
    fun `AC-569 fascia chiaro`() = verificaFascia(scuro = false)

    @Test
    fun `AC-569 fascia scuro`() = verificaFascia(scuro = true)

    private fun verificaFascia(scuro: Boolean) = fissaggio("fascia", scuro) {
        Column(verticalArrangement = Arrangement.spacedBy(SnastroMisure.space2)) {
            MisuratoreFascia(Fascia.FORTE)
            MisuratoreFascia(Fascia.DEBOLE)
            MisuratoreFascia(Fascia.NESSUNA)
        }
    }

    @Test
    fun `AC-569 le fasce mostrano solo la parola, mai un numero o una percentuale`() = runDesktopComposeUiTest {
        setContent {
            SnastroTema {
                Column {
                    MisuratoreFascia(Fascia.FORTE)
                    MisuratoreFascia(Fascia.DEBOLE)
                    MisuratoreFascia(Fascia.NESSUNA)
                }
            }
        }
        onNodeWithText("forte").assertIsDisplayed()
        onNodeWithText("debole").assertIsDisplayed()
        onNodeWithText("nessuna").assertIsDisplayed()
    }

    @Test
    fun `AC-561 voce chiaro`() = verificaVoce(scuro = false)

    @Test
    fun `AC-561 voce scuro`() = verificaVoce(scuro = true)

    private fun verificaVoce(scuro: Boolean) = fissaggio("voce", scuro) {
        Column(verticalArrangement = Arrangement.spacedBy(SnastroMisure.space2)) {
            EtichettaVoce(VoceId(1), nome = "Marco")
            EtichettaVoce(VoceId(4), nome = null)
        }
    }

    @Test
    fun `AC-561 una Voce senza nome mostra Voce n`() = runDesktopComposeUiTest {
        setContent { SnastroTema { EtichettaVoce(VoceId(4), nome = null) } }
        onNodeWithText("Voce 4").assertIsDisplayed()
    }

    @Test
    fun `AC-570 focus chiaro`() = verificaFocus(scuro = false)

    @Test
    fun `AC-570 focus scuro`() = verificaFocus(scuro = true)

    private fun verificaFocus(scuro: Boolean) = runDesktopComposeUiTest(LARGHEZZA_PX, ALTEZZA_PX) {
        setContent {
            SnastroTema(scuro = scuro) {
                Surface(color = sfondo(scuro)) {
                    Column(
                        modifier = Modifier.padding(SnastroMisure.space4),
                        verticalArrangement = Arrangement.spacedBy(SnastroMisure.space3),
                    ) {
                        BottoneSn(
                            etichetta = "Trascrivi",
                            onClick = {},
                            variante = VarianteBottone.Primario,
                            modifier = Modifier.testTag("focus-bottone"),
                        )
                        CampoSn(
                            valore = "",
                            onValoreCambiato = {},
                            etichetta = "Nome",
                            modifier = Modifier.testTag("focus-campo"),
                        )
                    }
                }
            }
        }
        onNodeWithTag("focus-bottone").requestFocus()
        onNodeWithTag("focus-campo").requestFocus()
        catturaPng("focus", scuro)
    }

    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.catturaPng(nomeComponente: String, scuro: Boolean) {
        val tema = if (scuro) "scuro" else "chiaro"
        val png = File(outputDir, "stile-$nomeComponente-$tema.png")
        val bitmap = onRoot().captureToImage().toAwtImage()
        ImageIO.write(bitmap, "PNG", png)
        check(png.exists() && png.length() > 0) { "renderCheck: PNG not written: $png" }
    }
}
