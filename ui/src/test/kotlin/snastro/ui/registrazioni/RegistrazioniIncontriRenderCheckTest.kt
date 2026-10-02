package snastro.ui.registrazioni

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.ui.testi.ETICHETTA_AGGIUNGI_PARTI
import snastro.ui.testi.ETICHETTA_ORA_SCONOSCIUTA
import snastro.ui.testi.ETICHETTA_TRASCRIVI
import snastro.ui.testi.MESSAGGIO_NUMERO_PERSONE_NON_VALIDO
import snastro.ui.testi.etichettaParteInCoda
import snastro.ui.testi.etichettaParteInCorso
import snastro.ui.testi.etichettaParteNonRiuscita
import snastro.ui.testi.etichettaTrascriviParti
import snastro.ui.testi.titoloIncontro
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import javax.imageio.ImageIO

private val INC = IncontroId("incontro-1")
private val PARTI = listOf(RegistrazioneId("parte-1"), RegistrazioneId("parte-2"), RegistrazioneId("parte-3"))
private val DATA = LocalDate.of(2026, 9, 30)
private const val TITOLO = "Riunione di staff"
private const val TITOLO_40 = "Riunione di coordinamento trimestrale 26" // 40 characters

private val AZIONI = AzioniRegistrazioni(
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
    elimina = {},
    annullaElimina = {},
    confermaElimina = {},
    chiudiAvviso = {},
    aggiungiParti = { _, _, _ -> },
    scegliImporta = {},
    confermaImporta = {},
    annullaImporta = {},
    espandiIncontro = {},
    modificaOraDiInizio = { _, _ -> },
    modificaNumeroPersoneIncontro = { _, _ -> },
    avviaElaborazioniIncontro = {},
    chiudiErroreIncontro = {},
)

private fun parte(
    n: Int,
    elaborazione: StatoElaborazioneRiga?,
    ora: LocalTime? = LocalTime.of(9 + n, 30),
    titolo: String = if (n == 1) TITOLO else "file $n",
) = RigaRegistrazione(
    registrazioneId = PARTI[n - 1],
    titolo = titolo,
    dataRegistrazione = DATA,
    durataMs = 3_600_000,
    elaborazione = elaborazione,
    trascrittoDisponibile = elaborazione == StatoElaborazioneRiga.Completata,
    ritrascriviDisponibile = elaborazione == StatoElaborazioneRiga.Completata,
    annullabile = elaborazione is StatoElaborazioneRiga.InAttesa,
    parte = ParteDiIncontro(n, titolo),
    oraDiInizio = ora,
)

@Suppress("LongParameterList") // one parameter per RigaIncontro field these fixtures vary
private fun incontro(
    parti: List<RigaRegistrazione>,
    titolo: String = TITOLO,
    espanso: Boolean = false,
    numeroPersone: String = "",
    errore: String? = null,
    badge: IdentificazioneRiga? = IdentificazioneRiga(4, 1),
    id: IncontroId = INC,
) = RigaIncontro(
    incontroId = id,
    titolo = titolo,
    data = DATA,
    durataMs = parti.sumOf { it.durataMs },
    parti = parti.map { it.registrazioneId },
    stato = statoAggregato(parti),
    identificazione = badge,
    numeroPersone = numeroPersone,
    errore = errore,
    espanso = espanso,
)

private fun stato(righe: List<RigaRegistrazione>, incontri: List<RigaIncontro>, erroreAggiornamento: String? = null) =
    RegistrazioniUiStato.Dati(righe = righe, incontri = incontri, erroreAggiornamento = erroreAggiornamento)

private val COMPLETATA = StatoElaborazioneRiga.Completata
private val DA_FARE = StatoElaborazioneRiga.NonAvviata

/**
 * `schermata-incontri` render-check (AC-I66..I69, INV-I3): a multi-part Incontro of S2 — collapsed in each aggregated
 * state, expanded, the long title, the More menu, the Incontro's own errors and a mixed list with a 1-part row — at
 * 1280x800 and 1024x640, light and dark. The 1-part rows equal today's PNGs: that is `RegistrazioniRenderCheckTest`'s
 * baseline check (INV-I3).
 */
@Suppress("LargeClass")
@OptIn(ExperimentalTestApi::class)
@Tag("render")
class RegistrazioniIncontriRenderCheckTest {
    private val outputDir = File("build/render-check").apply { mkdirs() }

    private val varianti = listOf(
        Triple(1280, 800, false),
        Triple(1280, 800, true),
        Triple(1024, 640, false),
        Triple(1024, 640, true),
    )

    private fun ComposeUiTest.mostra(stato: RegistrazioniUiStato.Dati, scuro: Boolean) = setContent {
        SchermataRegistrazioni(stato = stato, azioni = AZIONI, scuro = scuro, riduciMovimento = true)
    }

    private fun ComposeUiTest.cattura(nome: String, w: Int, h: Int, scuro: Boolean, ultimaRadice: Boolean = false) {
        val png = File(outputDir, "$nome${if (scuro) "-scuro" else ""}-${w}x$h.png")
        val radici = onAllNodes(isRoot()).fetchSemanticsNodes()
        val nodo = if (ultimaRadice) onAllNodes(isRoot())[radici.size - 1] else onRoot()
        ImageIO.write(nodo.captureToImage().toAwtImage(), "PNG", png)
        check(png.exists() && png.length() > 0) { "renderCheck: PNG not written: $png" }
    }

    private fun verifica(nome: String, stato: RegistrazioniUiStato.Dati, controlli: ComposeUiTest.() -> Unit) =
        varianti.forEach { (w, h, scuro) ->
            runDesktopComposeUiTest(w, h) {
                mostra(stato, scuro)
                controlli()
                cattura(nome, w, h, scuro)
            }
        }

    private fun ComposeUiTest.titoloVisibile(titolo: String = TITOLO, n: Int = 3) =
        onNodeWithText(titoloIncontro(titolo, n)).assertIsDisplayed()

    @Test
    fun `AC-I66 stato 1 una Parte in corso nomina la Parte`() {
        val parti = listOf(
            parte(1, COMPLETATA),
            parte(2, StatoElaborazioneRiga.InCorso("separazione voci", 192_000)),
            parte(3, DA_FARE),
        )
        verifica("incontri-stato-1-in-corso", stato(parti, listOf(incontro(parti)))) {
            titoloVisibile()
            onNodeWithText(etichettaParteInCorso(2, "separazione voci", 192_000, false)).assertIsDisplayed()
            onNodeWithTag("registrazioni-incontro-chevron-${INC.valore}").assertIsDisplayed()
        }
    }

    @Test
    fun `AC-I66 stato 2 una Parte in coda con Annulla`() {
        val parti = listOf(parte(1, COMPLETATA), parte(2, StatoElaborazioneRiga.InAttesa(1)), parte(3, DA_FARE))
        verifica("incontri-stato-2-in-coda", stato(parti, listOf(incontro(parti)))) {
            onNodeWithText(etichettaParteInCoda(2, 1, false)).assertIsDisplayed()
            onNodeWithTag("registrazioni-incontro-annulla-${INC.valore}").assertIsDisplayed()
        }
    }

    @Test
    fun `AC-I66 stato 3 una Parte non riuscita`() {
        val fallita = StatoElaborazioneRiga.Fallita("audio illeggibile")
        val parti = listOf(parte(1, COMPLETATA), parte(2, fallita), parte(3, DA_FARE))
        verifica("incontri-stato-3-non-riuscita", stato(parti, listOf(incontro(parti)))) {
            onNodeWithText(etichettaParteNonRiuscita(2)).assertIsDisplayed()
        }
    }

    @Test
    fun `AC-I66 stato 4 Trascrivi N parti col campo precompilato`() {
        val parti = listOf(parte(1, COMPLETATA), parte(2, DA_FARE), parte(3, DA_FARE))
        verifica("incontri-stato-4-trascrivi", stato(parti, listOf(incontro(parti, numeroPersone = "4")))) {
            onNodeWithText(etichettaTrascriviParti(2)).assertIsDisplayed()
            onNodeWithTag("registrazioni-incontro-numero-persone-${INC.valore}", true).assertIsDisplayed()
        }
    }

    @Test
    fun `AC-I67 il valore non valido mostra Da 1 a 10 sull'Incontro`() {
        val parti = listOf(parte(1, DA_FARE), parte(2, DA_FARE), parte(3, DA_FARE))
        val i = incontro(parti, numeroPersone = "11", errore = MESSAGGIO_NUMERO_PERSONE_NON_VALIDO, badge = null)
        verifica("incontri-numero-persone-non-valido", stato(parti, listOf(i))) {
            onNodeWithText(etichettaTrascriviParti(3)).assertIsDisplayed()
            onNodeWithText(MESSAGGIO_NUMERO_PERSONE_NON_VALIDO).assertIsDisplayed()
        }
    }

    @Test
    fun `AC-I66 stato 5 Completata`() {
        val parti = listOf(parte(1, COMPLETATA), parte(2, COMPLETATA), parte(3, COMPLETATA))
        val i = incontro(parti, badge = IdentificazioneRiga(4, 0))
        verifica("incontri-stato-5-completata", stato(parti, listOf(i))) {
            titoloVisibile()
            onNodeWithText("4 voci").assertIsDisplayed()
        }
    }

    @Test
    fun `AC-I68 riga espansa con una Parte senza ora una fallita e una completata`() {
        val parti = listOf(
            parte(1, COMPLETATA),
            parte(2, StatoElaborazioneRiga.Fallita("audio illeggibile"), ora = null),
            parte(3, COMPLETATA),
        )
        verifica("incontri-espansa", stato(parti, listOf(incontro(parti, espanso = true)))) {
            onNodeWithTag("registrazioni-incontro-parti-${INC.valore}").assertIsDisplayed()
            onNodeWithTag("registrazioni-ora-${PARTI[1].valore}").assertIsDisplayed()
            onNodeWithText(ETICHETTA_ORA_SCONOSCIUTA).assertIsDisplayed()
            onNodeWithText("Parte 1").assertIsDisplayed()
            onNodeWithText("Parte 3").assertIsDisplayed()
        }
    }

    @Test
    fun `AC-I68 una riga compressa non mostra le sotto-righe`() {
        val parti = listOf(parte(1, COMPLETATA), parte(2, COMPLETATA))
        verifica("incontri-compressa", stato(parti, listOf(incontro(parti)))) {
            onAllNodesWithTag("registrazioni-incontro-parti-${INC.valore}").assertCountEquals(0)
        }
    }

    @Test
    fun `AC-I66 un titolo di 40 caratteri con N parti resta su una riga con ellissi`() {
        val inCorso = StatoElaborazioneRiga.InCorso("separazione voci", 192_000)
        val parti = listOf(parte(1, inCorso, titolo = TITOLO_40), parte(2, DA_FARE), parte(3, DA_FARE))
        verifica("incontri-titolo-lungo", stato(parti, listOf(incontro(parti, titolo = TITOLO_40)))) {
            onNodeWithTag("registrazioni-incontro-titolo-${INC.valore}", useUnmergedTree = true).assertIsDisplayed()
            onNodeWithTag("registrazioni-incontro-stato-${INC.valore}", useUnmergedTree = true).assertIsDisplayed()
        }
    }

    @Test
    fun `AC-I69 un errore di aggiornamento convive con le righe degli Incontri`() {
        val parti = listOf(parte(1, COMPLETATA), parte(2, COMPLETATA))
        verifica(
            "incontri-errore-aggiornamento",
            stato(parti, listOf(incontro(parti)), erroreAggiornamento = "Non è stato possibile aggiornare l'elenco."),
        ) {
            onNodeWithTag("registrazioni-errore").assertIsDisplayed()
            titoloVisibile(n = 2)
        }
    }

    @Test
    fun `INV-I3 una lista mista una riga di una parte accanto a un incontro non ha chevron ne N parti`() {
        val uno = RigaRegistrazione(
            registrazioneId = RegistrazioneId("sola"),
            titolo = "Seduta del 12 marzo",
            dataRegistrazione = DATA.minusDays(30),
            durataMs = 125_000,
            elaborazione = COMPLETATA,
            trascrittoDisponibile = true,
            ritrascriviDisponibile = true,
        )
        val parti = listOf(parte(1, COMPLETATA), parte(2, DA_FARE))
        val lista = stato(parti + uno, listOf(incontro(parti, badge = null), RigaIncontro.singola(uno)))
        verifica("incontri-lista-mista", lista) {
            onNodeWithText("Seduta del 12 marzo").assertIsDisplayed()
            onAllNodesWithTag("registrazioni-incontro-chevron-sola").assertCountEquals(0)
            onAllNodes(hasText("Seduta del 12 marzo ·", substring = true)).assertCountEquals(0)
        }
    }

    @Test
    fun `AC-I66 il menu dell'Incontro offre Aggiungi parti`() = menuAperto(
        nome = "incontri-menu-aggiungi-parti",
        tag = "registrazioni-incontro-altre-azioni-${INC.valore}",
        stato = stato(
            listOf(parte(1, COMPLETATA), parte(2, COMPLETATA)),
            listOf(incontro(listOf(parte(1, COMPLETATA), parte(2, COMPLETATA)))),
        ),
        voce = "registrazioni-menu-aggiungi-parti-${INC.valore}",
    )

    @Test
    fun `INV-I3 il menu di una riga di una parte guadagna Aggiungi parti`() {
        val uno = RigaRegistrazione(
            registrazioneId = RegistrazioneId("sola"),
            titolo = "Seduta del 12 marzo",
            dataRegistrazione = DATA,
            durataMs = 125_000,
            elaborazione = StatoElaborazioneRiga.NonAvviata,
        )
        menuAperto(
            nome = "incontri-menu-una-parte-aggiungi-parti",
            tag = "registrazioni-altre-azioni-sola",
            stato = stato(listOf(uno), listOf(RigaIncontro.singola(uno))),
            voce = "registrazioni-menu-aggiungi-parti-sola",
        )
    }

    private fun menuAperto(nome: String, tag: String, stato: RegistrazioniUiStato.Dati, voce: String) =
        varianti.forEach { (w, h, scuro) ->
            runDesktopComposeUiTest(w, h) {
                mostra(stato, scuro)
                onNodeWithTag(tag, useUnmergedTree = true).performClick()
                onNodeWithTag(voce).assertIsDisplayed()
                onNodeWithText(ETICHETTA_AGGIUNGI_PARTI).assertIsDisplayed()
                cattura(nome, w, h, scuro, ultimaRadice = true)
            }
        }

    @Test
    fun `AC-I66 una riga di una parte non mostra Trascrivi N parti ma il suo Trascrivi`() {
        val uno = RigaRegistrazione(
            registrazioneId = RegistrazioneId("sola"),
            titolo = "Seduta del 12 marzo",
            dataRegistrazione = DATA,
            durataMs = 125_000,
            elaborazione = StatoElaborazioneRiga.NonAvviata,
        )
        verifica("incontri-una-parte-trascrivi", stato(listOf(uno), listOf(RigaIncontro.singola(uno)))) {
            onNodeWithText(ETICHETTA_TRASCRIVI).assertIsDisplayed()
            onAllNodes(hasText("parti", substring = true)).assertCountEquals(0)
        }
    }
}
