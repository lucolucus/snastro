package snastro.ui.stile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import snastro.kernel.VoceId
import snastro.parlanti.applicazione.porte.Fascia
import snastro.ui.SnastroTema
import java.io.File
import javax.imageio.ImageIO

private const val LARGHEZZA_PX = 640
private const val ALTEZZA_PX = 480
private const val LARGHEZZA_GRUPPO_FONTI_PX = 1024
private const val ALTEZZA_GRUPPO_FONTI_PX = 640
private const val NOME_LUNGO_GRUPPO_FONTI =
    "Un nome molto lungo di piu' di quaranta caratteri per forzare l andata a capo"

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

    // riduciMovimento is always pinned `true` here (never left to auto-detection): a captured PNG
    // is a still frame regardless, but pinning also means no fixture ever builds a running
    // `rememberInfiniteTransition` (AC-565's pulse) inside a headless Compose UI test.
    private fun fissaggio(nomeComponente: String, scuro: Boolean, contenuto: @Composable () -> Unit) =
        runDesktopComposeUiTest(LARGHEZZA_PX, ALTEZZA_PX) {
            setContent {
                SnastroTema(scuro = scuro, riduciMovimento = true) {
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
            ChipStato(TipoChipStato.NonRiuscita())
            // AC-S46: the Riassunto tab's own override — "Non riuscito", not S2's "Non riuscita".
            ChipStato(TipoChipStato.NonRiuscita(testo = "Non riuscito"))
            ChipStato(TipoChipStato.Avviso("Da identificare", Icona.Alert))
        }
    }

    @Test
    fun `AC-565 il testo di InCoda mostra la posizione`() = runDesktopComposeUiTest {
        setContent { SnastroTema(riduciMovimento = true) { ChipStato(TipoChipStato.InCoda(2)) } }
        onNodeWithText("In coda · 2").assertIsDisplayed()
    }

    // Pre-release finding #152 (rework): [TipoChipStato.InCoda.posizione] can be `null` (the
    // Riassunto tab's own queue, unlike S2's, may not know its position yet) — the chip falls back
    // to the plain label instead of e.g. "In coda · null".
    @Test
    fun `AC-565 rework il testo di InCoda senza posizione nota`() = runDesktopComposeUiTest {
        setContent { SnastroTema(riduciMovimento = true) { ChipStato(TipoChipStato.InCoda(null)) } }
        onNodeWithText("In coda").assertIsDisplayed()
    }

    @Test
    fun `AC-565 InCorso mostra la fase e il tempo trascorso in formato AC-557`() = runDesktopComposeUiTest {
        setContent {
            SnastroTema(riduciMovimento = true) { ChipStato(TipoChipStato.InCorso("Separazione voci", 192_000)) }
        }
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
    fun `AC-570 focus bottone chiaro`() = verificaFocusBottone(scuro = false)

    @Test
    fun `AC-570 focus bottone scuro`() = verificaFocusBottone(scuro = true)

    // One Primario alone, focused — a separate fixture from the field (a shared setContent that
    // requests focus on two nodes in turn leaves only the LAST one focused: the button's ring never
    // actually rendered before).
    private fun verificaFocusBottone(scuro: Boolean) = runDesktopComposeUiTest(LARGHEZZA_PX, ALTEZZA_PX) {
        setContent {
            SnastroTema(scuro = scuro, riduciMovimento = true) {
                Surface(color = sfondo(scuro)) {
                    Column(Modifier.padding(SnastroMisure.space4)) {
                        BottoneSn(
                            etichetta = "Trascrivi",
                            onClick = {},
                            variante = VarianteBottone.Primario,
                            modifier = Modifier.testTag("focus-bottone"),
                        )
                    }
                }
            }
        }
        onNodeWithTag("focus-bottone").requestFocus()
        catturaPng("focus-bottone", scuro)
    }

    @Test
    fun `AC-570 focus campo chiaro`() = verificaFocusCampo(scuro = false)

    @Test
    fun `AC-570 focus campo scuro`() = verificaFocusCampo(scuro = true)

    private fun verificaFocusCampo(scuro: Boolean) = runDesktopComposeUiTest(LARGHEZZA_PX, ALTEZZA_PX) {
        setContent {
            SnastroTema(scuro = scuro, riduciMovimento = true) {
                Surface(color = sfondo(scuro)) {
                    Column(Modifier.padding(SnastroMisure.space4)) {
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
        // CampoSn merges label+input into ONE semantics node at its "focus-campo" tag (review
        // MED-7's outer container) — but a text field's RequestFocus/SetText actions live on its
        // OWN merge boundary node underneath, not on that outer node. Reach it directly.
        onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("focus-campo"))).requestFocus()
        catturaPng("focus-campo", scuro)
    }

    @Test
    fun `AC-S47 fonte chip chiaro`() = verificaFonteChip(scuro = false)

    @Test
    fun `AC-S47 fonte chip scuro`() = verificaFonteChip(scuro = true)

    // AC-S43: named, unnamed (ring dot, "Voce n"), and an h:mm:ss timecode in the same fixture.
    private fun verificaFonteChip(scuro: Boolean) = fissaggio("fonte-chip", scuro) {
        Row(horizontalArrangement = Arrangement.spacedBy(SnastroMisure.space2)) {
            FonteChip(voceId = 1, nome = "Marco", inizioMs = 65_000)
            FonteChip(voceId = 4, nome = null, inizioMs = 30_000)
            FonteChip(voceId = 2, nome = "Ada", inizioMs = 3_725_000)
        }
    }

    @Test
    fun `AC-S47 gruppo fonti a capo chiaro`() = verificaGruppoFontiACapo(scuro = false)

    @Test
    fun `AC-S47 gruppo fonti a capo scuro`() = verificaGruppoFontiACapo(scuro = true)

    // AC-S44: 5 chips, one with a 40+ character name, in a narrow column — proves the wrap
    // visually (the programmatic proof is GruppoFontiTest). Window per the AC: 1024x640.
    private fun verificaGruppoFontiACapo(scuro: Boolean) =
        runDesktopComposeUiTest(LARGHEZZA_GRUPPO_FONTI_PX, ALTEZZA_GRUPPO_FONTI_PX) {
            setContent {
                SnastroTema(scuro = scuro, riduciMovimento = true) {
                    Surface(color = sfondo(scuro)) {
                        Column(Modifier.padding(SnastroMisure.space4).width(320.dp)) {
                            GruppoFonti(
                                fonti = listOf(
                                    FonteChipDati(1, NOME_LUNGO_GRUPPO_FONTI, 0),
                                    FonteChipDati(2, "Bea", 60_000),
                                    FonteChipDati(3, null, 120_000),
                                    FonteChipDati(4, "Cesare", 180_000),
                                    FonteChipDati(5, "Dora", 240_000),
                                ),
                            )
                        }
                    }
                }
            }
            catturaPng("gruppo-fonti", scuro)
        }

    @Test
    fun `AC-S47 schede sn chiaro`() = verificaSchedeSn(scuro = false)

    @Test
    fun `AC-S47 schede sn scuro`() = verificaSchedeSn(scuro = true)

    // AC-S45: both states (selected/unselected) with each SegnoScheda mark.
    private fun verificaSchedeSn(scuro: Boolean) = fissaggio("schede-sn", scuro) {
        Column(verticalArrangement = Arrangement.spacedBy(SnastroMisure.space3)) {
            SchedeSn(schede = listOf("Trascrizione", "Riassunto"), selezionata = 0, onSeleziona = {})
            SchedeSn(
                schede = listOf("Trascrizione", "Riassunto"),
                selezionata = 1,
                onSeleziona = {},
                segni = mapOf(1 to SegnoScheda.InAttesa),
            )
            SchedeSn(
                schede = listOf("Trascrizione", "Riassunto"),
                selezionata = 1,
                onSeleziona = {},
                segni = mapOf(1 to SegnoScheda.InCorso),
            )
        }
    }

    @Test
    fun `AC-S46 NonRiuscita usa il testo predefinito Non riuscita`() = runDesktopComposeUiTest {
        setContent { SnastroTema(riduciMovimento = true) { ChipStato(TipoChipStato.NonRiuscita()) } }
        onNodeWithText("Non riuscita").assertIsDisplayed()
    }

    @Test
    fun `AC-S46 NonRiuscita accetta un testo alternativo per la scheda Riassunto`() = runDesktopComposeUiTest {
        setContent {
            SnastroTema(riduciMovimento = true) { ChipStato(TipoChipStato.NonRiuscita(testo = "Non riuscito")) }
        }
        onNodeWithText("Non riuscito").assertIsDisplayed()
    }

    @Test
    fun `AC-S46 InCorso riusa il tipo esistente per Sto riassumendo, nessun nuovo tipo`() = runDesktopComposeUiTest {
        setContent {
            SnastroTema(riduciMovimento = true) { ChipStato(TipoChipStato.InCorso("Sto riassumendo", 72_000)) }
        }
        onNodeWithText("Sto riassumendo").assertIsDisplayed()
        onNodeWithText("1:12").assertIsDisplayed()
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
