package snastro.ui.registrazione

import androidx.compose.material3.Text
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
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.ui.lettore.LettoreUiStato
import snastro.ui.stile.SegnoScheda
import snastro.ui.testi.ETICHETTA_SCHEDA_RIASSUNTO
import snastro.ui.testi.ETICHETTA_SCHEDA_TRASCRIZIONE
import snastro.ui.testi.testoBannerVociDaIdentificare
import java.io.File
import java.time.LocalDate
import javax.imageio.ImageIO

private const val LARGHEZZA_GRANDE_PX = 1280
private const val ALTEZZA_GRANDE_PX = 800
private const val LARGHEZZA_PICCOLA_PX = 1024
private const val ALTEZZA_PICCOLA_PX = 640
private const val TAG_CONTENUTO_RIASSUNTO = "riassunto-slot-contenuto"

private val AZIONI_VUOTE = AzioniRegistrazione(
    riproduciDaInizio = {},
    pausa = {},
    riproduciSegmento = {},
    apriDocumento = {},
    mostraDocumentoNellaCartella = {},
    chiudiErrore = {},
    riprova = {},
)

private val DATA_1: LocalDate = LocalDate.of(2026, 3, 12)

private val UN_SEGMENTO = SegmentoRiga(
    segmentoId = SegmentoId(1),
    voceId = VoceId(1),
    etichettaVoce = "Voce 1",
    inizioMs = 0,
    fineMs = 2_000,
    testo = "Buongiorno a tutti.",
)

private val CONTENUTO_RIASSUNTO_DI_PROVA: @Composable () -> Unit = {
    Text("Contenuto Riassunto di prova", modifier = Modifier.testTag(TAG_CONTENUTO_RIASSUNTO))
}

private fun unaCartaDaIdentificare(numero: Int) = CartaVoce(
    voceId = VoceId(numero),
    titolo = "Voce $numero",
    contenuto = ContenutoCarta.DaIdentificare(StatoProposta.Caricamento, galleriaVuota = false),
)

private fun unPannello(daIdentificare: Int, estrattiDisponibili: Boolean = true) = PannelloVoci(
    carte = (1..daIdentificare).map { unaCartaDaIdentificare(it) },
    parlantiAttivi = emptyList(),
    unioni = emptyList(),
    estrattiDisponibili = estrattiDisponibili,
    unioneAbilitata = true,
)

@Suppress("LongParameterList") // one parameter per RegistrazioneUiStato.Dati field these fixtures vary
private fun unoStatoConSchede(
    schedaSelezionata: SchedaS3 = SchedaS3.TRASCRIZIONE,
    segnoRiassunto: SegnoScheda? = null,
    audioDisponibile: Boolean = true,
    pannello: PannelloVoci? = null,
    soloLettura: Boolean = false,
    bannerRitrascrizione: String? = null,
) = RegistrazioneUiStato.Dati(
    titolo = "Seduta del 12 marzo",
    dataRegistrazione = DATA_1,
    durataMs = 185_000,
    segmenti = listOf(UN_SEGMENTO),
    barra = LettoreUiStato.Inattivo,
    audioDisponibile = audioDisponibile,
    documentoPercorso = "/progetti/demo.snastro/documenti/2026-03-12 Seduta.md",
    pannello = pannello,
    soloLettura = soloLettura,
    bannerRitrascrizione = bannerRitrascrizione,
    contenutoRiassunto = CONTENUTO_RIASSUNTO_DI_PROVA,
    schedaSelezionata = schedaSelezionata,
    segnoRiassunto = segnoRiassunto,
)

/**
 * `:ui:renderCheck` (profile `ui_render_check`), AC-S124: every state the Riassunto tab's OWN chrome
 * introduces — both tabs, each [SegnoScheda] mark, with and without the ONE screen `Banner` the AC-S123
 * precedence table picks — at 1280×800/1024×640, light and a representative dark subset. The tab
 * BODY's own content is `scheda-riassunto`'s (a fixed placeholder here is enough to prove the slot is
 * reached and does not clip); [RegistrazioneRenderCheckTest] already covers every R0/R1/R2 state
 * (`contenutoRiassunto == null`, AC-S119) unmodified.
 */
@OptIn(ExperimentalTestApi::class)
@Tag("render")
class RegistrazioneSchedeRenderCheckTest {
    private val outputDir = File("build/render-check").apply { mkdirs() }

    @Test
    fun `AC-S120 scheda Trascrizione selezionata mostra il trascritto a 1280x800`() =
        verificaSchedaTrascrizione(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-S120 scheda Trascrizione selezionata mostra il trascritto a 1024x640`() =
        verificaSchedaTrascrizione(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-S120 scheda Riassunto selezionata mostra lo slot a 1280x800`() =
        verificaSchedaRiassunto(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-S120 scheda Riassunto selezionata mostra lo slot a 1024x640`() =
        verificaSchedaRiassunto(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-S120 scheda Riassunto selezionata scuro a 1280x800`() =
        verificaSchedaRiassunto(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX, scuro = true)

    @Test
    fun `AC-S120 scheda Riassunto selezionata scuro a 1024x640`() =
        verificaSchedaRiassunto(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX, scuro = true)

    @Test
    fun `AC-S122 segno InAttesa Clock a 1280x800`() =
        verificaSegno(SegnoScheda.InAttesa, "registrazione-segno-in-attesa", LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-S122 segno InAttesa Clock a 1024x640`() =
        verificaSegno(SegnoScheda.InAttesa, "registrazione-segno-in-attesa", LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-S122 segno InCorso pallino pulsante a 1280x800`() =
        verificaSegno(SegnoScheda.InCorso, "registrazione-segno-in-corso", LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-S122 segno InCorso pallino pulsante a 1024x640`() =
        verificaSegno(SegnoScheda.InCorso, "registrazione-segno-in-corso", LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-S122 segno InCorso scuro a 1280x800`() = verificaSegno(
        SegnoScheda.InCorso,
        "registrazione-segno-in-corso",
        LARGHEZZA_GRANDE_PX,
        ALTEZZA_GRANDE_PX,
        scuro = true,
    )

    @Test
    fun `AC-S123 banner audio mancante con le schede a 1280x800`() =
        verificaBannerAudioMancante(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-S123 banner audio mancante con le schede a 1024x640`() =
        verificaBannerAudioMancante(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-S123 banner audio mancante scuro a 1280x800`() =
        verificaBannerAudioMancante(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX, scuro = true)

    @Test
    fun `AC-S123 banner audio mancante scuro a 1024x640`() =
        verificaBannerAudioMancante(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX, scuro = true)

    @Test
    fun `AC-S123 banner voci da identificare con le schede a 1280x800`() =
        verificaBannerVociDaIdentificare(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-S123 banner voci da identificare con le schede a 1024x640`() =
        verificaBannerVociDaIdentificare(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-S123 il banner di sola lettura vince su audio mancante e voci da identificare a 1280x800`() =
        verificaPrecedenzaRitrascrizione(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-S123 il banner di sola lettura vince su audio mancante e voci da identificare a 1024x640`() =
        verificaPrecedenzaRitrascrizione(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    // D-0014 (pre-release finding #180, rework, LOW): with Riassunto selected AND a Voci panel that
    // WOULD otherwise show, the panel is hidden — the summary gets the full content area, nothing
    // squeezed/clipped (also closes finding #128's own "right panel only Voci" gap: the fixture
    // defaulted `pannello=null`, so this exact combination was never actually rendered before).
    @Test
    fun `AC-S120 rework scheda Riassunto selezionata nasconde il pannello Voci a 1280x800`() =
        runDesktopComposeUiTest(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX) {
            setContent {
                SchermataRegistrazione(
                    stato = unoStatoConSchede(schedaSelezionata = SchedaS3.RIASSUNTO, pannello = unPannello(2)),
                    azioni = AZIONI_VUOTE,
                    scuro = false,
                    riduciMovimento = true,
                )
            }
            onNodeWithTag(TAG_CONTENUTO_RIASSUNTO).assertIsDisplayed()
            onNodeWithTag("voci-pannello").assertDoesNotExist()
            catturaPng("registrazione-schede-riassunto-pannello-nascosto", LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)
        }

    // AC-S120 "the right panel shows only Voci (no Riassunto tab there)": with Trascrizione selected,
    // the panel stays exactly what it always was.
    @Test
    fun `AC-S120 rework scheda Trascrizione selezionata mostra ancora il pannello Voci a 1280x800`() =
        runDesktopComposeUiTest(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX) {
            setContent {
                SchermataRegistrazione(
                    stato = unoStatoConSchede(schedaSelezionata = SchedaS3.TRASCRIZIONE, pannello = unPannello(2)),
                    azioni = AZIONI_VUOTE,
                    scuro = false,
                    riduciMovimento = true,
                )
            }
            onNodeWithTag("voci-pannello").assertIsDisplayed()
            onNodeWithText(ETICHETTA_SCHEDA_TRASCRIZIONE).assertIsDisplayed()
            catturaPng("registrazione-schede-trascrizione-pannello", LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)
        }

    @Test
    fun `AC-S123 le schede senza alcun banner a 1280x800`() =
        // Same fixture as AC-S120's default (Trascrizione selected, no condition true) — one state,
        // two ACs it happens to satisfy at once; reused rather than duplicated (frugality).
        verificaSchedaTrascrizione(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-S123 le schede senza alcun banner a 1024x640`() =
        verificaSchedaTrascrizione(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    private fun verificaSchedaTrascrizione(width: Int, height: Int) = runDesktopComposeUiTest(width, height) {
        setContent {
            SchermataRegistrazione(
                stato = unoStatoConSchede(),
                azioni = AZIONI_VUOTE,
                scuro = false,
                riduciMovimento = true,
            )
        }
        onNodeWithText(ETICHETTA_SCHEDA_TRASCRIZIONE).assertIsDisplayed()
        onNodeWithText(ETICHETTA_SCHEDA_RIASSUNTO).assertIsDisplayed()
        onNodeWithText("Buongiorno a tutti.").assertIsDisplayed()
        catturaPng("registrazione-schede-trascrizione", width, height)
    }

    private fun verificaSchedaRiassunto(width: Int, height: Int, scuro: Boolean = false) =
        runDesktopComposeUiTest(width, height) {
            setContent {
                SchermataRegistrazione(
                    stato = unoStatoConSchede(schedaSelezionata = SchedaS3.RIASSUNTO),
                    azioni = AZIONI_VUOTE,
                    scuro = scuro,
                    riduciMovimento = true,
                )
            }
            onNodeWithTag(TAG_CONTENUTO_RIASSUNTO).assertIsDisplayed()
            onNodeWithText(ETICHETTA_SCHEDA_TRASCRIZIONE).assertIsDisplayed()
            catturaPng("registrazione-schede-riassunto", width, height, scuro)
        }

    private fun verificaSegno(segno: SegnoScheda, nomePng: String, width: Int, height: Int, scuro: Boolean = false) =
        runDesktopComposeUiTest(width, height) {
            setContent {
                SchermataRegistrazione(
                    stato = unoStatoConSchede(segnoRiassunto = segno),
                    azioni = AZIONI_VUOTE,
                    scuro = scuro,
                    riduciMovimento = true,
                )
            }
            // SchedeSn's own tag convention (`ui-kit-sintesi`, `SchedeSnTest`): "scheda-<indice>-segno",
            // indice 1 = Riassunto ([SchedaS3.RIASSUNTO.ordinal]) — merged into the tab's own semantics
            // node, so the same `useUnmergedTree` the kit's own test already needs.
            onNodeWithTag("scheda-${SchedaS3.RIASSUNTO.ordinal}-segno", useUnmergedTree = true).assertIsDisplayed()
            catturaPng(nomePng, width, height, scuro)
        }

    private fun verificaBannerAudioMancante(width: Int, height: Int, scuro: Boolean = false) =
        runDesktopComposeUiTest(width, height) {
            setContent {
                SchermataRegistrazione(
                    stato = unoStatoConSchede(audioDisponibile = false),
                    azioni = AZIONI_VUOTE,
                    scuro = scuro,
                    riduciMovimento = true,
                )
            }
            onNodeWithTag("registrazione-banner-audio-mancante").assertIsDisplayed()
            onNodeWithText(ETICHETTA_SCHEDA_TRASCRIZIONE).assertIsDisplayed()
            catturaPng("registrazione-banner-audio-mancante", width, height, scuro)
        }

    private fun verificaBannerVociDaIdentificare(width: Int, height: Int) = runDesktopComposeUiTest(width, height) {
        setContent {
            SchermataRegistrazione(
                stato = unoStatoConSchede(pannello = unPannello(daIdentificare = 2)),
                azioni = AZIONI_VUOTE,
                scuro = false,
                riduciMovimento = true,
            )
        }
        onNodeWithTag("registrazione-banner-voci-da-identificare").assertIsDisplayed()
        onNodeWithText(testoBannerVociDaIdentificare(2)).assertIsDisplayed()
        catturaPng("registrazione-banner-voci-da-identificare", width, height)
    }

    private fun verificaPrecedenzaRitrascrizione(width: Int, height: Int) = runDesktopComposeUiTest(width, height) {
        setContent {
            SchermataRegistrazione(
                stato = unoStatoConSchede(
                    soloLettura = true,
                    bannerRitrascrizione = "Ritrascrizione in corso: modifiche disabilitate fino al termine\n" +
                        "Questa trascrizione sarà sostituita quando la nuova sarà pronta.",
                    audioDisponibile = false,
                    pannello = unPannello(daIdentificare = 2, estrattiDisponibili = false),
                ),
                azioni = AZIONI_VUOTE,
                scuro = false,
                riduciMovimento = true,
            )
        }
        // Exactly one screen Banner (AC-S123): the read-only one, never the other two (pre-release
        // finding #128, rework: the absence of the other two was never actually asserted before).
        onNodeWithTag("registrazione-banner-ritrascrizione").assertIsDisplayed()
        onNodeWithTag("registrazione-banner-audio-mancante").assertDoesNotExist()
        onNodeWithTag("registrazione-banner-voci-da-identificare").assertDoesNotExist()
        catturaPng("registrazione-schede-precedenza-ritrascrizione", width, height)
    }

    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.catturaPng(nome: String, width: Int, height: Int, scuro: Boolean = false) {
        val suffisso = if (scuro) "-scuro" else ""
        val png = File(outputDir, "$nome-${width}x$height$suffisso.png")
        val bitmap = onRoot().captureToImage().toAwtImage()
        ImageIO.write(bitmap, "PNG", png)
        check(png.exists() && png.length() > 0) { "renderCheck: PNG not written: $png" }
    }
}
