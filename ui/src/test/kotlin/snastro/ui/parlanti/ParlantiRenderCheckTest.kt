package snastro.ui.parlanti

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import snastro.kernel.ParlanteId
import snastro.parlanti.applicazione.eventi.TipoParlanteVista
import snastro.parlanti.dominio.ErroreParlanti
import snastro.ui.testi.ETICHETTA_ANNULLA
import snastro.ui.testi.ETICHETTA_CONFERMA_ELIMINAZIONE
import snastro.ui.testi.ETICHETTA_SEZIONE_ELIMINATI
import snastro.ui.testi.ETICHETTA_SEZIONE_OCCASIONALI
import snastro.ui.testi.ETICHETTA_SEZIONE_RICORRENTI
import snastro.ui.testi.MESSAGGIO_CONFERMA_ELIMINAZIONE_PARLANTE
import snastro.ui.testi.MESSAGGIO_PARLANTI_VUOTO
import snastro.ui.testi.messaggioPer
import java.io.File
import java.time.LocalDate
import javax.imageio.ImageIO

private const val LARGHEZZA_GRANDE_PX = 1280
private const val ALTEZZA_GRANDE_PX = 800
private const val LARGHEZZA_PICCOLA_PX = 1024
private const val ALTEZZA_PICCOLA_PX = 640

private val AZIONI_VUOTE = AzioniParlanti(
    rinomina = { _, _ -> },
    promuovi = {},
    riproduci = {},
    chiediConfermaEliminazione = {},
    annullaEliminazione = {},
    confermaEliminazione = {},
    chiudiErroreRiga = {},
    chiudiErrore = {},
    riprova = {},
)

private val PARLANTE_1 = ParlanteId("id-1")
private val PARLANTE_2 = ParlanteId("id-2")
private val PARLANTE_ELIMINATO = ParlanteId("id-3")
private val DATA_1: LocalDate = LocalDate.of(2026, 3, 12)

@Suppress("LongParameterList") // one parameter per RigaParlante field this fixture builder covers
private fun unaRiga(
    id: ParlanteId = PARLANTE_1,
    nome: String = "Marco",
    tipoParlante: TipoParlanteVista = TipoParlanteVista.RICORRENTE,
    riproduzioneAbilitata: Boolean = true,
    operazioneInCorso: Boolean = false,
    erroreRiga: String? = null,
    confermaEliminazione: Boolean = false,
) = RigaParlante(
    parlanteId = id,
    nome = nome,
    tipoParlante = tipoParlante,
    numImpronte = 3,
    numRegistrazioni = 2,
    ultimaApparizione = DATA_1,
    riproduzioneAbilitata = riproduzioneAbilitata,
    operazioneInCorso = operazioneInCorso,
    erroreRiga = erroreRiga,
    confermaEliminazione = confermaEliminazione,
)

/**
 * `:ui:renderCheck` (profile `ui_render_check`): sizing/overflow/contrast/state-rendering at both
 * sizes, both themes, for the states the block spec pins — loading (AC-221), empty (AC-220), the list
 * with an eliminato row (AC-222), a rename-in-progress row with an inline error (AC-223), and the
 * delete confirmation (AC-225/577). [SchermataParlanti] renders directly from fixture `UiStato` values
 * (dev-architecture `#presenter`), exactly like [snastro.ui.registrazioni.RegistrazioniRenderCheckTest].
 */
@OptIn(ExperimentalTestApi::class)
@Tag("render")
class ParlantiRenderCheckTest {
    private val outputDir = File("build/render-check").apply { mkdirs() }

    @Test
    fun `AC-221 caricamento mostra un indicatore a 1280x800`() =
        verificaCaricamento(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-221 caricamento mostra un indicatore a 1024x640`() =
        verificaCaricamento(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-221 caricamento mostra un indicatore a 1280x800 (scuro)`() =
        verificaCaricamento(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX, scuro = true)

    @Test
    fun `AC-221 caricamento mostra un indicatore a 1024x640 (scuro)`() =
        verificaCaricamento(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX, scuro = true)

    @Test
    fun `AC-220 lista vuota mostra il messaggio dedicato a 1280x800`() =
        verificaVuoto(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-220 lista vuota mostra il messaggio dedicato a 1024x640`() =
        verificaVuoto(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-220 lista vuota mostra il messaggio dedicato a 1280x800 (scuro)`() =
        verificaVuoto(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX, scuro = true)

    @Test
    fun `AC-220 lista vuota mostra il messaggio dedicato a 1024x640 (scuro)`() =
        verificaVuoto(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX, scuro = true)

    @Test
    fun `AC-222 la lista mostra Ricorrenti Occasionali ed Eliminati a 1280x800`() =
        verificaLista(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-222 la lista mostra Ricorrenti Occasionali ed Eliminati a 1024x640`() =
        verificaLista(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-222 la lista mostra Ricorrenti Occasionali ed Eliminati a 1280x800 (scuro)`() =
        verificaLista(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX, scuro = true)

    @Test
    fun `AC-222 la lista mostra Ricorrenti Occasionali ed Eliminati a 1024x640 (scuro)`() =
        verificaLista(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX, scuro = true)

    @Test
    fun `AC-577 il menu Altre azioni mostra Promuovi ed Elimina a 1280x800`() =
        verificaMenuAltreAzioni(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-577 il menu Altre azioni mostra Promuovi ed Elimina a 1024x640`() =
        verificaMenuAltreAzioni(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-577 il menu Altre azioni mostra Promuovi ed Elimina a 1280x800 (scuro)`() =
        verificaMenuAltreAzioni(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX, scuro = true)

    @Test
    fun `AC-577 il menu Altre azioni mostra Promuovi ed Elimina a 1024x640 (scuro)`() =
        verificaMenuAltreAzioni(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX, scuro = true)

    @Test
    fun `AC-223 rinomina in corso con un errore inline a 1280x800`() =
        verificaRinominaConErrore(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-223 rinomina in corso con un errore inline a 1024x640`() =
        verificaRinominaConErrore(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-223 rinomina in corso con un errore inline a 1280x800 (scuro)`() =
        verificaRinominaConErrore(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX, scuro = true)

    @Test
    fun `AC-223 rinomina in corso con un errore inline a 1024x640 (scuro)`() =
        verificaRinominaConErrore(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX, scuro = true)

    @Test
    fun `AC-225 la finestra di eliminazione riporta il testo privacy a 1280x800`() =
        verificaConfermaEliminazione(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-225 la finestra di eliminazione riporta il testo privacy a 1024x640`() =
        verificaConfermaEliminazione(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-225 la finestra di eliminazione riporta il testo privacy a 1280x800 (scuro)`() =
        verificaConfermaEliminazione(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX, scuro = true)

    @Test
    fun `AC-225 la finestra di eliminazione riporta il testo privacy a 1024x640 (scuro)`() =
        verificaConfermaEliminazione(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX, scuro = true)

    private fun verificaCaricamento(width: Int, height: Int, scuro: Boolean = false) =
        runDesktopComposeUiTest(width, height) {
            setContent {
                SchermataParlanti(
                    stato = ParlantiUiStato.Caricamento,
                    azioni = AZIONI_VUOTE,
                    scuro = scuro,
                    riduciMovimento = true,
                )
            }
            onNodeWithTag("parlanti-indicatore-caricamento").assertIsDisplayed()
            catturaPng("parlanti-caricamento", width, height, scuro)
        }

    private fun verificaVuoto(width: Int, height: Int, scuro: Boolean = false) =
        runDesktopComposeUiTest(width, height) {
            setContent {
                SchermataParlanti(
                    stato = ParlantiUiStato.Dati(),
                    azioni = AZIONI_VUOTE,
                    scuro = scuro,
                    riduciMovimento = true,
                )
            }
            onNodeWithText(MESSAGGIO_PARLANTI_VUOTO).assertIsDisplayed()
            catturaPng("parlanti-vuoto", width, height, scuro)
        }

    private fun verificaLista(width: Int, height: Int, scuro: Boolean = false) =
        runDesktopComposeUiTest(width, height) {
            setContent {
                SchermataParlanti(
                    stato = ParlantiUiStato.Dati(
                        ricorrenti = listOf(unaRiga(id = PARLANTE_1, nome = "Marco")),
                        occasionali = listOf(
                            unaRiga(
                                id = PARLANTE_2,
                                nome = "Ospite del 12/03/2026",
                                tipoParlante = TipoParlanteVista.OCCASIONALE,
                                riproduzioneAbilitata = false,
                            ),
                        ),
                        eliminati = listOf(RigaParlanteEliminato(PARLANTE_ELIMINATO, "Luca")),
                    ),
                    azioni = AZIONI_VUOTE,
                    scuro = scuro,
                    riduciMovimento = true,
                )
            }
            // AC-577 rework cycle 1 (MED #13): overline section headers render UPPERCASE (applied by
            // the composable, never typed uppercase in the constants — README §Tipografia).
            onNodeWithText(ETICHETTA_SEZIONE_RICORRENTI.uppercase()).assertIsDisplayed()
            onNodeWithText(ETICHETTA_SEZIONE_OCCASIONALI.uppercase()).assertIsDisplayed()
            onNodeWithText(ETICHETTA_SEZIONE_ELIMINATI.uppercase()).assertIsDisplayed()
            onNodeWithText("Luca").assertIsDisplayed()
            onNodeWithTag("parlanti-riproduzione-${PARLANTE_1.valore}", useUnmergedTree = true).assertIsEnabled()
            onNodeWithTag("parlanti-riproduzione-${PARLANTE_2.valore}", useUnmergedTree = true).assertIsNotEnabled()
            // AC-577 rework cycle 1 (#4): Promuovi/Elimina moved into the `More` menu (see
            // `verificaMenuAltreAzioni`) — the row itself shows the Edit/More icon buttons.
            onNodeWithTag("parlanti-modifica-${PARLANTE_1.valore}", useUnmergedTree = true).assertIsDisplayed()
            onNodeWithTag("parlanti-altre-azioni-${PARLANTE_1.valore}", useUnmergedTree = true).assertIsDisplayed()
            catturaPng("parlanti-lista", width, height, scuro)
        }

    /** AC-577 rework cycle 1 (#4): `BottoneIcona More` opens a `Menu.html`-style menu — "Promuovi a
     * ricorrente" only for an occasionale row, "Elimina…" always, both the SAME commands as before. */
    private fun verificaMenuAltreAzioni(width: Int, height: Int, scuro: Boolean = false) =
        runDesktopComposeUiTest(width, height) {
            setContent {
                SchermataParlanti(
                    stato = ParlantiUiStato.Dati(
                        occasionali = listOf(unaRiga(id = PARLANTE_2, tipoParlante = TipoParlanteVista.OCCASIONALE)),
                    ),
                    azioni = AZIONI_VUOTE,
                    scuro = scuro,
                    riduciMovimento = true,
                )
            }
            onNodeWithTag("parlanti-altre-azioni-${PARLANTE_2.valore}", useUnmergedTree = true).performClick()
            onNodeWithTag("parlanti-promuovi-${PARLANTE_2.valore}").assertIsDisplayed()
            onNodeWithTag("parlanti-elimina-${PARLANTE_2.valore}").assertIsDisplayed()
            // `DropdownMenu` opens its own Popup layer — a second semantics root distinct from the
            // window's own, so `onRoot()` (which requires exactly one) cannot be used once it is open;
            // the LAST root is the freshly-opened menu.
            val radici = onAllNodes(isRoot()).fetchSemanticsNodes()
            val png = File(outputDir, "parlanti-menu-altre-azioni${if (scuro) "-scuro" else ""}-${width}x$height.png")
            val bitmap = onAllNodes(isRoot())[radici.size - 1].captureToImage().toAwtImage()
            ImageIO.write(bitmap, "PNG", png)
            check(png.exists() && png.length() > 0) { "renderCheck: PNG not written: $png" }
        }

    private fun verificaRinominaConErrore(width: Int, height: Int, scuro: Boolean = false) =
        runDesktopComposeUiTest(width, height) {
            val errore = messaggioPer(ErroreParlanti.NomeGiaInUso("Anna"))
            setContent {
                SchermataParlanti(
                    stato = ParlantiUiStato.Dati(
                        ricorrenti = listOf(unaRiga(operazioneInCorso = true, erroreRiga = errore)),
                    ),
                    azioni = AZIONI_VUOTE,
                    scuro = scuro,
                    riduciMovimento = true,
                )
            }
            onNodeWithTag("parlanti-nome-${PARLANTE_1.valore}", useUnmergedTree = true)
                .assertIsDisplayed()
                .assertIsNotEnabled()
            onNodeWithTag("parlanti-operazione-in-corso-${PARLANTE_1.valore}", useUnmergedTree = true)
                .assertIsDisplayed()
            onNodeWithTag("parlanti-errore-riga-${PARLANTE_1.valore}", useUnmergedTree = true).assertIsDisplayed()
            onNodeWithText(errore).assertIsDisplayed()
            catturaPng("parlanti-rinomina-errore", width, height, scuro)
        }

    private fun verificaConfermaEliminazione(width: Int, height: Int, scuro: Boolean = false) =
        runDesktopComposeUiTest(width, height) {
            setContent {
                SchermataParlanti(
                    stato = ParlantiUiStato.Dati(ricorrenti = listOf(unaRiga(confermaEliminazione = true))),
                    azioni = AZIONI_VUOTE,
                    scuro = scuro,
                    riduciMovimento = true,
                )
            }
            onNodeWithTag("parlanti-conferma-eliminazione-${PARLANTE_1.valore}", useUnmergedTree = true)
                .assertIsDisplayed()
            onNodeWithText(MESSAGGIO_CONFERMA_ELIMINAZIONE_PARLANTE).assertIsDisplayed()
            onNodeWithText(ETICHETTA_CONFERMA_ELIMINAZIONE).assertIsDisplayed()
            onNodeWithText(ETICHETTA_ANNULLA).assertIsDisplayed()
            catturaPng("parlanti-conferma-eliminazione", width, height, scuro)
        }

    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.catturaPng(nome: String, width: Int, height: Int, scuro: Boolean = false) {
        val suffisso = if (scuro) "-scuro" else ""
        val png = File(outputDir, "$nome$suffisso-${width}x$height.png")
        val bitmap = onRoot().captureToImage().toAwtImage()
        ImageIO.write(bitmap, "PNG", png)
        check(png.exists() && png.length() > 0) { "renderCheck: PNG not written: $png" }
    }
}
