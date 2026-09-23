package snastro.ui.registrazioni

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import snastro.kernel.RegistrazioneId
import snastro.progetto.dominio.ErroreProgetto
import snastro.ui.testi.ETICHETTA_IMPORTA_FILE
import snastro.ui.testi.ETICHETTA_RIPROVA
import snastro.ui.testi.ETICHETTA_TRASCRIVI
import snastro.ui.testi.MESSAGGIO_AUDIO_NON_DISPONIBILE
import snastro.ui.testi.MESSAGGIO_ERRORE_CARICAMENTO
import snastro.ui.testi.MESSAGGIO_REGISTRAZIONI_VUOTO
import snastro.ui.testi.etichettaInAttesa
import snastro.ui.testi.messaggioPer
import java.io.File
import java.time.LocalDate
import javax.imageio.ImageIO

private const val LARGHEZZA_GRANDE_PX = 1280
private const val ALTEZZA_GRANDE_PX = 800
private const val LARGHEZZA_PICCOLA_PX = 1024
private const val ALTEZZA_PICCOLA_PX = 640

private val AZIONI_VUOTE = AzioniRegistrazioni(
    importa = {},
    modificaData = { _, _ -> },
    rinomina = { _, _ -> },
    riproduci = {},
    pausa = {},
    avviaElaborazione = {},
    apriRiga = {},
    chiudiErrore = {},
    chiudiErroreRiga = {},
    riprova = {},
)

private val REG_1 = RegistrazioneId("id-1")
private val DATA_1: LocalDate = LocalDate.of(2026, 3, 12)

private fun unaRiga(
    id: RegistrazioneId = REG_1,
    titolo: String = "Seduta del 12 marzo",
    elaborazione: StatoElaborazioneRiga? = null,
    riproduzione: StatoRiproduzioneRiga = StatoRiproduzioneRiga.Disponibile,
) = RigaRegistrazione(
    registrazioneId = id,
    titolo = titolo,
    dataRegistrazione = DATA_1,
    durataMs = 125_000,
    elaborazione = elaborazione,
    riproduzione = riproduzione,
)

/**
 * `:ui:renderCheck` (profile `ui_render_check`): every [RegistrazioniUiStato]/[RigaRegistrazione]
 * fixture at both sizes — sizing/overflow/contrast/state-rendering. R0 (AC-199/200/201/342/343): empty,
 * list with '▶', a playing row, an unavailable row, an import error, an editable (long) titolo with a
 * row-level rename error (AC-363). R1 (AC-203/344): the status
 * column, a failed/retry row, a NON_AVVIATA row with 'Trascrivi'. [SchermataRegistrazioni] renders
 * directly from fixture `UiStato` values (dev-architecture `#presenter`).
 */
@OptIn(ExperimentalTestApi::class)
@Tag("render")
class RegistrazioniRenderCheckTest {
    private val outputDir = File("build/render-check").apply { mkdirs() }

    @Test
    fun `AC-200 caricamento mostra un indicatore a 1280x800`() =
        verificaCaricamento(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-200 caricamento mostra un indicatore a 1024x640`() =
        verificaCaricamento(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-199 lista vuota mostra il messaggio dedicato a 1280x800`() =
        verificaVuoto(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-199 lista vuota mostra il messaggio dedicato a 1024x640`() =
        verificaVuoto(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `M5 un fallimento del caricamento iniziale mostra uno stato distinto con Riprova a 1280x800`() =
        verificaErroreCaricamento(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `M5 un fallimento del caricamento iniziale mostra uno stato distinto con Riprova a 1024x640`() =
        verificaErroreCaricamento(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-342 AC-343 la lista mostra il controllo di riproduzione a 1280x800`() =
        verificaLista(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-342 AC-343 la lista mostra il controllo di riproduzione a 1024x640`() =
        verificaLista(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-343 una riga in riproduzione mostra la pausa a 1280x800`() =
        verificaRigaInRiproduzione(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-343 una riga in riproduzione mostra la pausa a 1024x640`() =
        verificaRigaInRiproduzione(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-343 audio non disponibile disabilita il controllo a 1280x800`() =
        verificaAudioNonDisponibile(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-343 audio non disponibile disabilita il controllo a 1024x640`() =
        verificaAudioNonDisponibile(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-201 un errore di importazione e mostrato inline a 1280x800`() =
        verificaErroreImport(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-201 un errore di importazione e mostrato inline a 1024x640`() =
        verificaErroreImport(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-203 la colonna di stato mostra In coda a 1280x800`() =
        verificaColonnaStato(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-203 la colonna di stato mostra In coda a 1024x640`() =
        verificaColonnaStato(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-203 una riga fallita mostra il motivo e Riprova a 1280x800`() =
        verificaFallitaConRiprova(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-203 una riga fallita mostra il motivo e Riprova a 1024x640`() =
        verificaFallitaConRiprova(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-344 NON_AVVIATA mostra Trascrivi a 1280x800`() =
        verificaNonAvviataConTrascrivi(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-344 NON_AVVIATA mostra Trascrivi a 1024x640`() =
        verificaNonAvviataConTrascrivi(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-363 il titolo e un campo modificabile e l errore di rinomina e inline sulla riga a 1280x800`() =
        verificaTitoloModificabileConErrore(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-363 il titolo e un campo modificabile e l errore di rinomina e inline sulla riga a 1024x640`() =
        verificaTitoloModificabileConErrore(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    private fun verificaCaricamento(width: Int, height: Int) = runDesktopComposeUiTest(width, height) {
        setContent { SchermataRegistrazioni(stato = RegistrazioniUiStato.Caricamento, azioni = AZIONI_VUOTE) }
        onNodeWithTag("registrazioni-indicatore-caricamento").assertIsDisplayed()
        catturaPng("registrazioni-caricamento", width, height)
    }

    private fun verificaVuoto(width: Int, height: Int) = runDesktopComposeUiTest(width, height) {
        setContent {
            SchermataRegistrazioni(stato = RegistrazioniUiStato.Dati(righe = emptyList()), azioni = AZIONI_VUOTE)
        }
        onNodeWithText(MESSAGGIO_REGISTRAZIONI_VUOTO).assertIsDisplayed()
        onNodeWithText(ETICHETTA_IMPORTA_FILE).assertIsDisplayed()
        catturaPng("registrazioni-vuoto", width, height)
    }

    private fun verificaErroreCaricamento(width: Int, height: Int) = runDesktopComposeUiTest(width, height) {
        setContent {
            SchermataRegistrazioni(
                stato = RegistrazioniUiStato.Errore(MESSAGGIO_ERRORE_CARICAMENTO),
                azioni = AZIONI_VUOTE,
            )
        }
        onNodeWithTag("registrazioni-errore-caricamento").assertIsDisplayed()
        onNodeWithText(MESSAGGIO_ERRORE_CARICAMENTO).assertIsDisplayed()
        onNodeWithText(ETICHETTA_RIPROVA).assertIsDisplayed()
        catturaPng("registrazioni-errore-caricamento", width, height)
    }

    private fun verificaLista(width: Int, height: Int) = runDesktopComposeUiTest(width, height) {
        setContent {
            SchermataRegistrazioni(stato = RegistrazioniUiStato.Dati(righe = listOf(unaRiga())), azioni = AZIONI_VUOTE)
        }
        onNodeWithText("Seduta del 12 marzo").assertIsDisplayed()
        onNodeWithTag("registrazioni-riproduzione-${REG_1.valore}").assertIsDisplayed()
        catturaPng("registrazioni-lista", width, height)
    }

    private fun verificaRigaInRiproduzione(width: Int, height: Int) = runDesktopComposeUiTest(width, height) {
        setContent {
            SchermataRegistrazioni(
                stato = RegistrazioniUiStato.Dati(
                    righe = listOf(unaRiga(riproduzione = StatoRiproduzioneRiga.InRiproduzione)),
                ),
                azioni = AZIONI_VUOTE,
            )
        }
        onNodeWithTag("registrazioni-riproduzione-${REG_1.valore}").assertIsDisplayed()
        catturaPng("registrazioni-riga-in-riproduzione", width, height)
    }

    private fun verificaAudioNonDisponibile(width: Int, height: Int) = runDesktopComposeUiTest(width, height) {
        setContent {
            SchermataRegistrazioni(
                stato = RegistrazioniUiStato.Dati(
                    righe = listOf(unaRiga(riproduzione = StatoRiproduzioneRiga.NonDisponibile)),
                ),
                azioni = AZIONI_VUOTE,
            )
        }
        onNodeWithText(MESSAGGIO_AUDIO_NON_DISPONIBILE).assertIsDisplayed()
        catturaPng("registrazioni-audio-non-disponibile", width, height)
    }

    private fun verificaErroreImport(width: Int, height: Int) = runDesktopComposeUiTest(width, height) {
        val messaggio = "Il file audio non può essere letto."
        setContent {
            SchermataRegistrazioni(
                stato = RegistrazioniUiStato.Dati(righe = emptyList(), errore = messaggio),
                azioni = AZIONI_VUOTE,
            )
        }
        onNodeWithTag("registrazioni-errore").assertIsDisplayed()
        onNodeWithText(messaggio).assertIsDisplayed()
        catturaPng("registrazioni-errore-import", width, height)
    }

    private fun verificaColonnaStato(width: Int, height: Int) = runDesktopComposeUiTest(width, height) {
        setContent {
            SchermataRegistrazioni(
                stato = RegistrazioniUiStato.Dati(
                    righe = listOf(unaRiga(elaborazione = StatoElaborazioneRiga.InAttesa(2))),
                ),
                azioni = AZIONI_VUOTE,
            )
        }
        // The status Column is a plain (non-merge-boundary) node nested under the row's own
        // `clickable` — its testTag is folded into the row's merged node (Compose semantics merging);
        // `useUnmergedTree` reaches it directly, exactly as the failure's own hint suggests.
        onNodeWithTag("registrazioni-stato-${REG_1.valore}", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText(etichettaInAttesa(2)).assertIsDisplayed()
        catturaPng("registrazioni-colonna-stato", width, height)
    }

    private fun verificaFallitaConRiprova(width: Int, height: Int) = runDesktopComposeUiTest(width, height) {
        setContent {
            SchermataRegistrazioni(
                stato = RegistrazioniUiStato.Dati(
                    righe = listOf(unaRiga(elaborazione = StatoElaborazioneRiga.Fallita("audio illeggibile"))),
                ),
                azioni = AZIONI_VUOTE,
            )
        }
        onNodeWithText("audio illeggibile").assertIsDisplayed()
        onNodeWithText(ETICHETTA_RIPROVA).assertIsDisplayed()
        catturaPng("registrazioni-fallita-riprova", width, height)
    }

    private fun verificaNonAvviataConTrascrivi(width: Int, height: Int) = runDesktopComposeUiTest(width, height) {
        setContent {
            val righe = listOf(unaRiga(elaborazione = StatoElaborazioneRiga.NonAvviata))
            SchermataRegistrazioni(
                stato = RegistrazioniUiStato.Dati(righe = righe),
                azioni = AZIONI_VUOTE,
            )
        }
        onNodeWithText(ETICHETTA_TRASCRIVI).assertIsDisplayed()
        catturaPng("registrazioni-non-avviata-trascrivi", width, height)
    }

    private fun verificaTitoloModificabileConErrore(width: Int, height: Int) = runDesktopComposeUiTest(width, height) {
        val titoloLungo = "Consiglio comunale straordinario sul bilancio di previsione e sulle opere pubbliche " +
            "del quartiere nord, seduta pomeridiana con interventi dei cittadini"
        val errore = messaggioPer(ErroreProgetto.TitoloGiaUsato("Intervista"))
        setContent {
            SchermataRegistrazioni(
                stato = RegistrazioniUiStato.Dati(
                    righe = listOf(unaRiga(titolo = titoloLungo).copy(erroreRiga = errore)),
                ),
                azioni = AZIONI_VUOTE,
            )
        }
        onNodeWithTag("registrazioni-titolo-${REG_1.valore}", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertIsEnabled()
            .assertTextEquals(titoloLungo)
        onNodeWithTag("registrazioni-errore-riga-${REG_1.valore}", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText(errore).assertIsDisplayed()
        onNodeWithTag("registrazioni-data-${REG_1.valore}", useUnmergedTree = true).assertIsDisplayed()
        catturaPng("registrazioni-titolo-errore-riga", width, height)
    }

    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.catturaPng(nome: String, width: Int, height: Int) {
        val png = File(outputDir, "$nome-${width}x$height.png")
        val bitmap = onRoot().captureToImage().toAwtImage()
        ImageIO.write(bitmap, "PNG", png)
        check(png.exists() && png.length() > 0) { "renderCheck: PNG not written: $png" }
    }
}
