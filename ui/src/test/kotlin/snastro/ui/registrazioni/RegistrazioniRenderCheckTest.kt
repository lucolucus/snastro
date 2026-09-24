package snastro.ui.registrazioni

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import snastro.kernel.ElaborazioneId
import snastro.kernel.RegistrazioneId
import snastro.progetto.dominio.ErroreProgetto
import snastro.ui.testi.ETICHETTA_ANNULLA
import snastro.ui.testi.ETICHETTA_IMPORTA_FILE
import snastro.ui.testi.ETICHETTA_RIPROVA
import snastro.ui.testi.ETICHETTA_RITRASCRIVI
import snastro.ui.testi.ETICHETTA_TRASCRIVI
import snastro.ui.testi.MESSAGGIO_AUDIO_NON_DISPONIBILE
import snastro.ui.testi.MESSAGGIO_CONFERMA_RITRASCRIVI
import snastro.ui.testi.MESSAGGIO_ERRORE_CARICAMENTO
import snastro.ui.testi.MESSAGGIO_NUMERO_PERSONE_NON_VALIDO
import snastro.ui.testi.MESSAGGIO_REGISTRAZIONI_VUOTO
import snastro.ui.testi.etichettaIdentificazione
import snastro.ui.testi.etichettaInAttesa
import snastro.ui.testi.etichettaRitrascrizioneInCorso
import snastro.ui.testi.messaggioPer
import snastro.ui.testi.messaggioRitrascrizioneNonRiuscita
import snastro.ui.testi.titoloConfermaRitrascrivi
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
    modificaNumeroPersone = { _, _ -> },
    apriRiga = {},
    chiudiErrore = {},
    chiudiErroreRiga = {},
    riprova = {},
    ritrascrivi = {},
    annullaRitrascrivi = {},
    confermaRitrascrivi = {},
    annullaElaborazione = {},
)

private val REG_1 = RegistrazioneId("id-1")
private val REG_2 = RegistrazioneId("id-2")
private val DATA_1: LocalDate = LocalDate.of(2026, 3, 12)

@Suppress("LongParameterList") // one parameter per RigaRegistrazione field these fixtures vary
private fun unaRiga(
    id: RegistrazioneId = REG_1,
    titolo: String = "Seduta del 12 marzo",
    elaborazione: StatoElaborazioneRiga? = null,
    riproduzione: StatoRiproduzioneRiga = StatoRiproduzioneRiga.Disponibile,
    identificazione: IdentificazioneRiga? = null,
    trascrittoDisponibile: Boolean = false,
    elaborazioneId: ElaborazioneId? = null,
    ritrascriviDisponibile: Boolean = false,
    confermaRitrascrivi: Boolean = false,
    ritrascrizioneFallita: String? = null,
    annullabile: Boolean = false,
) = RigaRegistrazione(
    registrazioneId = id,
    titolo = titolo,
    dataRegistrazione = DATA_1,
    durataMs = 125_000,
    elaborazione = elaborazione,
    riproduzione = riproduzione,
    identificazione = identificazione,
    trascrittoDisponibile = trascrittoDisponibile,
    elaborazioneId = elaborazioneId,
    ritrascriviDisponibile = ritrascriviDisponibile,
    confermaRitrascrivi = confermaRitrascrivi,
    ritrascrizioneFallita = ritrascrizioneFallita,
    annullabile = annullabile,
)

/**
 * `:ui:renderCheck` (profile `ui_render_check`): every [RegistrazioniUiStato]/[RigaRegistrazione]
 * fixture at both sizes — sizing/overflow/contrast/state-rendering. R0 (AC-199/200/201/342/343): empty,
 * list with '▶', a playing row, an unavailable row, an import error, an editable (long) titolo with a
 * row-level rename error (AC-363). R1 (AC-203/344): the status
 * column, a failed/retry row with its prefilled 'Numero di persone' field, a NON_AVVIATA row with the empty
 * field + 'Trascrivi' (no 'Trascrivi tutte'), an invalid field with its inline message (ADR 0014,
 * AC-372/375/376). R2 (AC-204/345, fetta Parlanti): a badge with `numVociDaIdentificare > 0`, a fully
 * identified row (badge reduced to just the total, never "· 0 da identificare"), and a mix of rows
 * with/without a badge in the same list (source absent for one row, e.g. no Trascritto yet).
 * [SchermataRegistrazioni] renders
 * directly from fixture `UiStato` values (dev-architecture `#presenter`).
 */
@Suppress("LargeClass") // AC-575: every state light+dark at both sizes — one @Test pair per fixture
@OptIn(ExperimentalTestApi::class)
@Tag("render")
class RegistrazioniRenderCheckTest {
    private val outputDir = File("build/render-check").apply { mkdirs() }

    @Test
    fun `AC-200 caricamento mostra un indicatore a 1280x800`() =
        verificaCaricamento(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-200 caricamento mostra un indicatore a 1280x800 (scuro)`() =
        verificaCaricamento(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX, scuro = true)

    @Test
    fun `AC-200 caricamento mostra un indicatore a 1024x640`() =
        verificaCaricamento(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-200 caricamento mostra un indicatore a 1024x640 (scuro)`() =
        verificaCaricamento(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX, scuro = true)

    @Test
    fun `AC-199 lista vuota mostra il messaggio dedicato a 1280x800`() =
        verificaVuoto(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-199 lista vuota mostra il messaggio dedicato a 1280x800 (scuro)`() =
        verificaVuoto(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX, scuro = true)

    @Test
    fun `AC-199 lista vuota mostra il messaggio dedicato a 1024x640`() =
        verificaVuoto(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-199 lista vuota mostra il messaggio dedicato a 1024x640 (scuro)`() =
        verificaVuoto(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX, scuro = true)

    @Test
    fun `M5 un fallimento del caricamento iniziale mostra uno stato distinto con Riprova a 1280x800`() =
        verificaErroreCaricamento(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `M5 un fallimento del caricamento iniziale mostra uno stato distinto con Riprova a 1280x800 (scuro)`() =
        verificaErroreCaricamento(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX, scuro = true)

    @Test
    fun `M5 un fallimento del caricamento iniziale mostra uno stato distinto con Riprova a 1024x640`() =
        verificaErroreCaricamento(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `M5 un fallimento del caricamento iniziale mostra uno stato distinto con Riprova a 1024x640 (scuro)`() =
        verificaErroreCaricamento(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX, scuro = true)

    @Test
    fun `AC-342 AC-343 la lista mostra il controllo di riproduzione a 1280x800`() =
        verificaLista(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-342 AC-343 la lista mostra il controllo di riproduzione a 1280x800 (scuro)`() =
        verificaLista(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX, scuro = true)

    @Test
    fun `AC-342 AC-343 la lista mostra il controllo di riproduzione a 1024x640`() =
        verificaLista(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-342 AC-343 la lista mostra il controllo di riproduzione a 1024x640 (scuro)`() =
        verificaLista(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX, scuro = true)

    @Test
    fun `AC-343 una riga in riproduzione mostra la pausa a 1280x800`() =
        verificaRigaInRiproduzione(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-343 una riga in riproduzione mostra la pausa a 1280x800 (scuro)`() =
        verificaRigaInRiproduzione(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX, scuro = true)

    @Test
    fun `AC-343 una riga in riproduzione mostra la pausa a 1024x640`() =
        verificaRigaInRiproduzione(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-343 una riga in riproduzione mostra la pausa a 1024x640 (scuro)`() =
        verificaRigaInRiproduzione(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX, scuro = true)

    @Test
    fun `AC-343 audio non disponibile disabilita il controllo a 1280x800`() =
        verificaAudioNonDisponibile(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-343 audio non disponibile disabilita il controllo a 1280x800 (scuro)`() =
        verificaAudioNonDisponibile(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX, scuro = true)

    @Test
    fun `AC-343 audio non disponibile disabilita il controllo a 1024x640`() =
        verificaAudioNonDisponibile(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-343 audio non disponibile disabilita il controllo a 1024x640 (scuro)`() =
        verificaAudioNonDisponibile(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX, scuro = true)

    @Test
    fun `AC-201 un errore di importazione e mostrato inline a 1280x800`() =
        verificaErroreImport(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-201 un errore di importazione e mostrato inline a 1280x800 (scuro)`() =
        verificaErroreImport(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX, scuro = true)

    @Test
    fun `AC-201 un errore di importazione e mostrato inline a 1024x640`() =
        verificaErroreImport(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-201 un errore di importazione e mostrato inline a 1024x640 (scuro)`() =
        verificaErroreImport(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX, scuro = true)

    @Test
    fun `AC-203 la colonna di stato mostra In coda a 1280x800`() =
        verificaColonnaStato(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-203 la colonna di stato mostra In coda a 1280x800 (scuro)`() =
        verificaColonnaStato(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX, scuro = true)

    @Test
    fun `AC-203 la colonna di stato mostra In coda a 1024x640`() =
        verificaColonnaStato(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-203 la colonna di stato mostra In coda a 1024x640 (scuro)`() =
        verificaColonnaStato(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX, scuro = true)

    @Test
    fun `AC-203 AC-376 una riga fallita mostra il motivo, il campo precompilato e Riprova a 1280x800`() =
        verificaFallitaConRiprova(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-203 AC-376 una riga fallita mostra il motivo, il campo precompilato e Riprova a 1280x800 (scuro)`() =
        verificaFallitaConRiprova(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX, scuro = true)

    @Test
    fun `AC-203 AC-376 una riga fallita mostra il motivo, il campo precompilato e Riprova a 1024x640`() =
        verificaFallitaConRiprova(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-203 AC-376 una riga fallita mostra il motivo, il campo precompilato e Riprova a 1024x640 (scuro)`() =
        verificaFallitaConRiprova(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX, scuro = true)

    @Test
    fun `AC-344 AC-372 NON_AVVIATA mostra il campo vuoto e Trascrivi, senza Trascrivi tutte a 1280x800`() =
        verificaNonAvviataConTrascrivi(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-344 AC-372 NON_AVVIATA mostra il campo vuoto e Trascrivi, senza Trascrivi tutte a 1280x800 (scuro)`() =
        verificaNonAvviataConTrascrivi(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX, scuro = true)

    @Test
    fun `AC-344 AC-372 NON_AVVIATA mostra il campo vuoto e Trascrivi, senza Trascrivi tutte a 1024x640`() =
        verificaNonAvviataConTrascrivi(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-344 AC-372 NON_AVVIATA mostra il campo vuoto e Trascrivi, senza Trascrivi tutte a 1024x640 (scuro)`() =
        verificaNonAvviataConTrascrivi(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX, scuro = true)

    @Test
    fun `AC-375 un numero di persone non valido mostra il messaggio inline a 1280x800`() =
        verificaNumeroPersoneNonValido(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-375 un numero di persone non valido mostra il messaggio inline a 1280x800 (scuro)`() =
        verificaNumeroPersoneNonValido(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX, scuro = true)

    @Test
    fun `AC-375 un numero di persone non valido mostra il messaggio inline a 1024x640`() =
        verificaNumeroPersoneNonValido(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-375 un numero di persone non valido mostra il messaggio inline a 1024x640 (scuro)`() =
        verificaNumeroPersoneNonValido(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX, scuro = true)

    @Test
    fun `AC-363 il titolo e un campo modificabile e l errore di rinomina e inline sulla riga a 1280x800`() =
        verificaTitoloModificabileConErrore(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-363 il titolo e un campo modificabile e l errore di rinomina e inline sulla riga a 1280x800 (scuro)`() =
        verificaTitoloModificabileConErrore(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX, scuro = true)

    @Test
    fun `AC-363 il titolo e un campo modificabile e l errore di rinomina e inline sulla riga a 1024x640`() =
        verificaTitoloModificabileConErrore(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-363 il titolo e un campo modificabile e l errore di rinomina e inline sulla riga a 1024x640 (scuro)`() =
        verificaTitoloModificabileConErrore(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX, scuro = true)

    @Test
    fun `AC-204 il badge con Voci da identificare a 1280x800`() =
        verificaBadgeIdentificazione(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-204 il badge con Voci da identificare a 1280x800 (scuro)`() =
        verificaBadgeIdentificazione(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX, scuro = true)

    @Test
    fun `AC-204 il badge con Voci da identificare a 1024x640`() =
        verificaBadgeIdentificazione(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-204 il badge con Voci da identificare a 1024x640 (scuro)`() =
        verificaBadgeIdentificazione(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX, scuro = true)

    @Test
    fun `AC-345 una riga completamente identificata mostra solo il totale delle voci a 1280x800`() =
        verificaBadgeCompleto(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-345 una riga completamente identificata mostra solo il totale delle voci a 1280x800 (scuro)`() =
        verificaBadgeCompleto(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX, scuro = true)

    @Test
    fun `AC-345 una riga completamente identificata mostra solo il totale delle voci a 1024x640`() =
        verificaBadgeCompleto(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-345 una riga completamente identificata mostra solo il totale delle voci a 1024x640 (scuro)`() =
        verificaBadgeCompleto(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX, scuro = true)

    @Test
    fun `AC-204 AC-345 un mix di righe con e senza badge a 1280x800`() =
        verificaBadgeMix(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-204 AC-345 un mix di righe con e senza badge a 1280x800 (scuro)`() =
        verificaBadgeMix(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX, scuro = true)

    @Test
    fun `AC-204 AC-345 un mix di righe con e senza badge a 1024x640`() =
        verificaBadgeMix(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-204 AC-345 un mix di righe con e senza badge a 1024x640 (scuro)`() =
        verificaBadgeMix(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX, scuro = true)

    @Test
    fun `AC-449 la conferma inline di Ritrascrivi a 1280x800`() =
        verificaConfermaRitrascrivi(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-449 la conferma inline di Ritrascrivi a 1280x800 (scuro)`() =
        verificaConfermaRitrascrivi(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX, scuro = true)

    @Test
    fun `AC-449 la conferma inline di Ritrascrivi a 1024x640`() =
        verificaConfermaRitrascrivi(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-449 la conferma inline di Ritrascrivi a 1024x640 (scuro)`() =
        verificaConfermaRitrascrivi(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX, scuro = true)

    @Test
    fun `AC-450 una riga Ritrascrizione in corso a 1280x800`() =
        verificaRitrascrizioneInCorso(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-450 una riga Ritrascrizione in corso a 1280x800 (scuro)`() =
        verificaRitrascrizioneInCorso(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX, scuro = true)

    @Test
    fun `AC-450 una riga Ritrascrizione in corso a 1024x640`() =
        verificaRitrascrizioneInCorso(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-450 una riga Ritrascrizione in corso a 1024x640 (scuro)`() =
        verificaRitrascrizioneInCorso(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX, scuro = true)

    @Test
    fun `AC-451 la notifica di ritrascrizione non riuscita a 1280x800`() =
        verificaRitrascrizioneNonRiuscita(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-451 la notifica di ritrascrizione non riuscita a 1280x800 (scuro)`() =
        verificaRitrascrizioneNonRiuscita(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX, scuro = true)

    @Test
    fun `AC-451 la notifica di ritrascrizione non riuscita a 1024x640`() =
        verificaRitrascrizioneNonRiuscita(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-451 la notifica di ritrascrizione non riuscita a 1024x640 (scuro)`() =
        verificaRitrascrizioneNonRiuscita(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX, scuro = true)

    @Test
    fun `AC-475 una riga In coda con Annulla a 1280x800`() =
        verificaInCodaConAnnulla(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX)

    @Test
    fun `AC-475 una riga In coda con Annulla a 1280x800 (scuro)`() =
        verificaInCodaConAnnulla(LARGHEZZA_GRANDE_PX, ALTEZZA_GRANDE_PX, scuro = true)

    @Test
    fun `AC-475 una riga In coda con Annulla a 1024x640`() =
        verificaInCodaConAnnulla(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX)

    @Test
    fun `AC-475 una riga In coda con Annulla a 1024x640 (scuro)`() =
        verificaInCodaConAnnulla(LARGHEZZA_PICCOLA_PX, ALTEZZA_PICCOLA_PX, scuro = true)

    private fun verificaCaricamento(width: Int, height: Int, scuro: Boolean = false) =
        runDesktopComposeUiTest(width, height) {
            setContent {
                SchermataRegistrazioni(
                    stato = RegistrazioniUiStato.Caricamento,
                    azioni = AZIONI_VUOTE,
                    scuro = scuro,
                    riduciMovimento = true,
                )
            }
            onNodeWithTag("registrazioni-indicatore-caricamento").assertIsDisplayed()
            catturaPng("registrazioni-caricamento", width, height, scuro)
        }

    private fun verificaVuoto(width: Int, height: Int, scuro: Boolean = false) =
        runDesktopComposeUiTest(width, height) {
            setContent {
                SchermataRegistrazioni(
                    stato = RegistrazioniUiStato.Dati(righe = emptyList()),
                    azioni = AZIONI_VUOTE,
                    scuro = scuro,
                    riduciMovimento = true,
                )
            }
            onNodeWithText(MESSAGGIO_REGISTRAZIONI_VUOTO).assertIsDisplayed()
            onNodeWithText(ETICHETTA_IMPORTA_FILE).assertIsDisplayed()
            catturaPng("registrazioni-vuoto", width, height, scuro)
        }

    private fun verificaErroreCaricamento(width: Int, height: Int, scuro: Boolean = false) =
        runDesktopComposeUiTest(width, height) {
            setContent {
                SchermataRegistrazioni(
                    stato = RegistrazioniUiStato.Errore(MESSAGGIO_ERRORE_CARICAMENTO),
                    azioni = AZIONI_VUOTE,
                    scuro = scuro,
                    riduciMovimento = true,
                )
            }
            onNodeWithTag("registrazioni-errore-caricamento").assertIsDisplayed()
            onNodeWithText(MESSAGGIO_ERRORE_CARICAMENTO).assertIsDisplayed()
            onNodeWithText(ETICHETTA_RIPROVA).assertIsDisplayed()
            catturaPng("registrazioni-errore-caricamento", width, height, scuro)
        }

    private fun verificaLista(width: Int, height: Int, scuro: Boolean = false) =
        runDesktopComposeUiTest(width, height) {
            setContent {
                SchermataRegistrazioni(
                    stato = RegistrazioniUiStato.Dati(righe = listOf(unaRiga())),
                    azioni = AZIONI_VUOTE,
                    scuro = scuro,
                    riduciMovimento = true,
                )
            }
            onNodeWithText("Seduta del 12 marzo").assertIsDisplayed()
            onNodeWithTag("registrazioni-riproduzione-${REG_1.valore}").assertIsDisplayed()
            catturaPng("registrazioni-lista", width, height, scuro)
        }

    private fun verificaRigaInRiproduzione(width: Int, height: Int, scuro: Boolean = false) =
        runDesktopComposeUiTest(width, height) {
            setContent {
                SchermataRegistrazioni(
                    stato = RegistrazioniUiStato.Dati(
                        righe = listOf(unaRiga(riproduzione = StatoRiproduzioneRiga.InRiproduzione)),
                    ),
                    azioni = AZIONI_VUOTE,
                    scuro = scuro,
                    riduciMovimento = true,
                )
            }
            onNodeWithTag("registrazioni-riproduzione-${REG_1.valore}").assertIsDisplayed()
            catturaPng("registrazioni-riga-in-riproduzione", width, height, scuro)
        }

    private fun verificaAudioNonDisponibile(width: Int, height: Int, scuro: Boolean = false) =
        runDesktopComposeUiTest(width, height) {
            setContent {
                SchermataRegistrazioni(
                    stato = RegistrazioniUiStato.Dati(
                        righe = listOf(unaRiga(riproduzione = StatoRiproduzioneRiga.NonDisponibile)),
                    ),
                    azioni = AZIONI_VUOTE,
                    scuro = scuro,
                    riduciMovimento = true,
                )
            }
            onNodeWithText(MESSAGGIO_AUDIO_NON_DISPONIBILE).assertIsDisplayed()
            catturaPng("registrazioni-audio-non-disponibile", width, height, scuro)
        }

    private fun verificaErroreImport(width: Int, height: Int, scuro: Boolean = false) =
        runDesktopComposeUiTest(width, height) {
            val messaggio = "Il file audio non può essere letto."
            setContent {
                SchermataRegistrazioni(
                    stato = RegistrazioniUiStato.Dati(righe = emptyList(), errore = messaggio),
                    azioni = AZIONI_VUOTE,
                    scuro = scuro,
                    riduciMovimento = true,
                )
            }
            onNodeWithTag("registrazioni-errore").assertIsDisplayed()
            onNodeWithText(messaggio).assertIsDisplayed()
            catturaPng("registrazioni-errore-import", width, height, scuro)
        }

    private fun verificaColonnaStato(width: Int, height: Int, scuro: Boolean = false) =
        runDesktopComposeUiTest(width, height) {
            setContent {
                SchermataRegistrazioni(
                    stato = RegistrazioniUiStato.Dati(
                        righe = listOf(unaRiga(elaborazione = StatoElaborazioneRiga.InAttesa(2))),
                    ),
                    azioni = AZIONI_VUOTE,
                    scuro = scuro,
                    riduciMovimento = true,
                )
            }
            // The status Column is a plain (non-merge-boundary) node nested under the row's own
            // `clickable` — its testTag is folded into the row's merged node (Compose semantics merging);
            // `useUnmergedTree` reaches it directly, exactly as the failure's own hint suggests.
            onNodeWithTag("registrazioni-stato-${REG_1.valore}", useUnmergedTree = true).assertIsDisplayed()
            onNodeWithText(etichettaInAttesa(2)).assertIsDisplayed()
            catturaPng("registrazioni-colonna-stato", width, height, scuro)
        }

    private fun verificaFallitaConRiprova(width: Int, height: Int, scuro: Boolean = false) =
        runDesktopComposeUiTest(width, height) {
            setContent {
                SchermataRegistrazioni(
                    stato = RegistrazioniUiStato.Dati(
                        righe = listOf(
                            unaRiga(elaborazione = StatoElaborazioneRiga.Fallita("audio illeggibile"))
                                .copy(numeroPersone = "3"),
                        ),
                    ),
                    azioni = AZIONI_VUOTE,
                    scuro = scuro,
                    riduciMovimento = true,
                )
            }
            onNodeWithText("audio illeggibile").assertIsDisplayed()
            onNodeWithTag("registrazioni-numero-persone-${REG_1.valore}", useUnmergedTree = true)
                .assertIsDisplayed()
                .assertIsEnabled()
            // `CampoNumeroPersone`'s own doc: the caller's testTag sits on its outer `TooltipArea`, not on
            // the merge-boundary node that actually carries the field's text — reach that one directly.
            onNode(
                hasSetTextAction() and hasAnyAncestor(hasTestTag("registrazioni-numero-persone-${REG_1.valore}")),
                useUnmergedTree = true,
            ).assertTextEquals("3")
            // AC-575/AC-567: `CampoNumeroPersone` has "no visible label" by design (a tooltip instead) —
            // `ETICHETTA_NUMERO_PERSONE` no longer renders as a Text node next to the field.
            onNodeWithText(ETICHETTA_RIPROVA).assertIsDisplayed()
            catturaPng("registrazioni-fallita-riprova", width, height, scuro)
        }

    private fun verificaNonAvviataConTrascrivi(width: Int, height: Int, scuro: Boolean = false) =
        runDesktopComposeUiTest(width, height) {
            setContent {
                val righe = listOf(unaRiga(elaborazione = StatoElaborazioneRiga.NonAvviata))
                SchermataRegistrazioni(
                    stato = RegistrazioniUiStato.Dati(righe = righe),
                    azioni = AZIONI_VUOTE,
                    scuro = scuro,
                    riduciMovimento = true,
                )
            }
            onNodeWithTag("registrazioni-numero-persone-${REG_1.valore}", useUnmergedTree = true)
                .assertIsDisplayed()
                .assertIsEnabled()
            onNodeWithText(ETICHETTA_TRASCRIVI).assertIsDisplayed()
            onAllNodesWithText("Trascrivi tutte", substring = true).assertCountEquals(0)
            catturaPng("registrazioni-non-avviata-trascrivi", width, height, scuro)
        }

    private fun verificaNumeroPersoneNonValido(width: Int, height: Int, scuro: Boolean = false) =
        runDesktopComposeUiTest(width, height) {
            setContent {
                val riga = unaRiga(elaborazione = StatoElaborazioneRiga.NonAvviata)
                    .copy(numeroPersone = "11", erroreRiga = MESSAGGIO_NUMERO_PERSONE_NON_VALIDO)
                SchermataRegistrazioni(
                    stato = RegistrazioniUiStato.Dati(righe = listOf(riga)),
                    azioni = AZIONI_VUOTE,
                    scuro = scuro,
                    riduciMovimento = true,
                )
            }
            onNodeWithTag("registrazioni-numero-persone-${REG_1.valore}", useUnmergedTree = true).assertIsDisplayed()
            onNodeWithText(MESSAGGIO_NUMERO_PERSONE_NON_VALIDO).assertIsDisplayed()
            onNodeWithText(ETICHETTA_TRASCRIVI).assertIsDisplayed()
            catturaPng("registrazioni-numero-persone-non-valido", width, height, scuro)
        }

    private fun verificaTitoloModificabileConErrore(width: Int, height: Int, scuro: Boolean = false) =
        runDesktopComposeUiTest(width, height) {
            val titoloLungo = "Consiglio comunale straordinario sul bilancio di previsione e sulle opere pubbliche " +
                "del quartiere nord, seduta pomeridiana con interventi dei cittadini"
            val errore = messaggioPer(ErroreProgetto.TitoloGiaUsato("Intervista"))
            setContent {
                SchermataRegistrazioni(
                    stato = RegistrazioniUiStato.Dati(
                        righe = listOf(unaRiga(titolo = titoloLungo).copy(erroreRiga = errore)),
                    ),
                    azioni = AZIONI_VUOTE,
                    scuro = scuro,
                    riduciMovimento = true,
                )
            }
            onNodeWithTag("registrazioni-titolo-${REG_1.valore}", useUnmergedTree = true)
                .assertIsDisplayed()
                .assertIsEnabled()
                .assertTextEquals(titoloLungo)
            onNodeWithTag("registrazioni-errore-riga-${REG_1.valore}", useUnmergedTree = true).assertIsDisplayed()
            onNodeWithText(errore).assertIsDisplayed()
            onNodeWithTag("registrazioni-data-${REG_1.valore}", useUnmergedTree = true).assertIsDisplayed()
            catturaPng("registrazioni-titolo-errore-riga", width, height, scuro)
        }

    private fun verificaBadgeIdentificazione(width: Int, height: Int, scuro: Boolean = false) =
        runDesktopComposeUiTest(width, height) {
            setContent {
                SchermataRegistrazioni(
                    stato = RegistrazioniUiStato.Dati(
                        righe = listOf(
                            unaRiga(
                                elaborazione = StatoElaborazioneRiga.Completata,
                                identificazione = IdentificazioneRiga(numVoci = 3, numVociDaIdentificare = 1),
                            ),
                        ),
                    ),
                    azioni = AZIONI_VUOTE,
                    scuro = scuro,
                    riduciMovimento = true,
                )
            }
            onNodeWithTag("registrazioni-identificazione-${REG_1.valore}", useUnmergedTree = true).assertIsDisplayed()
            onNodeWithText(etichettaIdentificazione(3, 1)).assertIsDisplayed()
            catturaPng("registrazioni-badge-identificazione", width, height, scuro)
        }

    private fun verificaBadgeCompleto(width: Int, height: Int, scuro: Boolean = false) =
        runDesktopComposeUiTest(width, height) {
            setContent {
                SchermataRegistrazioni(
                    stato = RegistrazioniUiStato.Dati(
                        righe = listOf(
                            unaRiga(
                                elaborazione = StatoElaborazioneRiga.Completata,
                                identificazione = IdentificazioneRiga(numVoci = 3, numVociDaIdentificare = 0),
                            ),
                        ),
                    ),
                    azioni = AZIONI_VUOTE,
                    scuro = scuro,
                    riduciMovimento = true,
                )
            }
            onNodeWithTag("registrazioni-identificazione-${REG_1.valore}", useUnmergedTree = true).assertIsDisplayed()
            onNodeWithText(etichettaIdentificazione(3, 0)).assertIsDisplayed()
            onAllNodesWithText("da identificare", substring = true).assertCountEquals(0)
            catturaPng("registrazioni-badge-completamente-identificata", width, height, scuro)
        }

    private fun verificaBadgeMix(width: Int, height: Int, scuro: Boolean = false) =
        runDesktopComposeUiTest(width, height) {
            setContent {
                SchermataRegistrazioni(
                    stato = RegistrazioniUiStato.Dati(
                        righe = listOf(
                            unaRiga(
                                id = REG_1,
                                titolo = "Seduta del 12 marzo",
                                elaborazione = StatoElaborazioneRiga.Completata,
                                identificazione = IdentificazioneRiga(numVoci = 3, numVociDaIdentificare = 1),
                            ),
                            unaRiga(
                                id = REG_2,
                                titolo = "Riunione del 20 marzo",
                                elaborazione = StatoElaborazioneRiga.InAttesa(1),
                                identificazione = null, // AC-345: nessun Trascritto ancora, nessun badge
                            ),
                        ),
                    ),
                    azioni = AZIONI_VUOTE,
                    scuro = scuro,
                    riduciMovimento = true,
                )
            }
            onNodeWithTag("registrazioni-identificazione-${REG_1.valore}", useUnmergedTree = true).assertIsDisplayed()
            onNodeWithText(etichettaIdentificazione(3, 1)).assertIsDisplayed()
            onAllNodesWithTag("registrazioni-identificazione-${REG_2.valore}", useUnmergedTree = true)
                .assertCountEquals(0)
            catturaPng("registrazioni-badge-mix", width, height, scuro)
        }

    private fun verificaConfermaRitrascrivi(width: Int, height: Int, scuro: Boolean = false) =
        runDesktopComposeUiTest(width, height) {
            setContent {
                SchermataRegistrazioni(
                    stato = RegistrazioniUiStato.Dati(
                        righe = listOf(
                            unaRiga(
                                elaborazione = StatoElaborazioneRiga.Completata,
                                trascrittoDisponibile = true,
                                ritrascriviDisponibile = true,
                                confermaRitrascrivi = true,
                            ),
                        ),
                    ),
                    azioni = AZIONI_VUOTE,
                    scuro = scuro,
                    riduciMovimento = true,
                )
            }
            onNodeWithTag("registrazioni-conferma-ritrascrivi-${REG_1.valore}", useUnmergedTree = true)
                .assertIsDisplayed()
            onNodeWithText(titoloConfermaRitrascrivi("Seduta del 12 marzo")).assertIsDisplayed()
            onNodeWithText(MESSAGGIO_CONFERMA_RITRASCRIVI).assertIsDisplayed()
            onNodeWithTag("registrazioni-conferma-ritrascrivi-conferma-${REG_1.valore}", useUnmergedTree = true)
                .assertIsDisplayed()
            onNodeWithTag("registrazioni-annulla-ritrascrivi-${REG_1.valore}", useUnmergedTree = true)
                .assertIsDisplayed()
            catturaPng("registrazioni-conferma-ritrascrivi", width, height, scuro)
        }

    private fun verificaRitrascrizioneInCorso(width: Int, height: Int, scuro: Boolean = false) =
        runDesktopComposeUiTest(width, height) {
            setContent {
                SchermataRegistrazioni(
                    stato = RegistrazioniUiStato.Dati(
                        righe = listOf(
                            unaRiga(
                                elaborazione = StatoElaborazioneRiga.InCorso(
                                    faseEtichetta = "separazione voci",
                                    trascorsoMs = 192_000,
                                    ritrascrizione = true,
                                ),
                                trascrittoDisponibile = true,
                            ),
                        ),
                    ),
                    azioni = AZIONI_VUOTE,
                    scuro = scuro,
                    riduciMovimento = true,
                )
            }
            onNodeWithText(etichettaRitrascrizioneInCorso("separazione voci", 192_000)).assertIsDisplayed()
            onAllNodesWithTag("registrazioni-annulla-${REG_1.valore}", useUnmergedTree = true).assertCountEquals(0)
            catturaPng("registrazioni-ritrascrizione-in-corso", width, height, scuro)
        }

    private fun verificaRitrascrizioneNonRiuscita(width: Int, height: Int, scuro: Boolean = false) =
        runDesktopComposeUiTest(width, height) {
            setContent {
                SchermataRegistrazioni(
                    stato = RegistrazioniUiStato.Dati(
                        righe = listOf(
                            unaRiga(
                                elaborazione = StatoElaborazioneRiga.Completata,
                                trascrittoDisponibile = true,
                                ritrascriviDisponibile = true,
                                ritrascrizioneFallita = "audio illeggibile",
                            ).copy(numeroPersone = "4"),
                        ),
                    ),
                    azioni = AZIONI_VUOTE,
                    scuro = scuro,
                    riduciMovimento = true,
                )
            }
            // AC-575: the 'Completata' state now renders as the design system's `ChipStato.Trascritta`
            // ("Trascritta", not the former plain-text "Completata" — `ETICHETTA_COMPLETATA` retired).
            onNodeWithText("Trascritta").assertIsDisplayed()
            onNodeWithText(messaggioRitrascrizioneNonRiuscita("audio illeggibile")).assertIsDisplayed()
            onNodeWithText(ETICHETTA_RITRASCRIVI).assertIsDisplayed()
            catturaPng("registrazioni-ritrascrizione-non-riuscita", width, height, scuro)
        }

    private fun verificaInCodaConAnnulla(width: Int, height: Int, scuro: Boolean = false) =
        runDesktopComposeUiTest(width, height) {
            setContent {
                SchermataRegistrazioni(
                    stato = RegistrazioniUiStato.Dati(
                        righe = listOf(
                            unaRiga(
                                elaborazione = StatoElaborazioneRiga.InAttesa(2),
                                elaborazioneId = ElaborazioneId("elaborazione-1"),
                                annullabile = true,
                            ),
                        ),
                    ),
                    azioni = AZIONI_VUOTE,
                    scuro = scuro,
                    riduciMovimento = true,
                )
            }
            onNodeWithText(etichettaInAttesa(2)).assertIsDisplayed()
            onNodeWithTag("registrazioni-annulla-${REG_1.valore}", useUnmergedTree = true)
                .assertIsDisplayed()
                .assertIsEnabled()
            onNodeWithText(ETICHETTA_ANNULLA).assertIsDisplayed()
            catturaPng("registrazioni-in-coda-annulla", width, height, scuro)
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
