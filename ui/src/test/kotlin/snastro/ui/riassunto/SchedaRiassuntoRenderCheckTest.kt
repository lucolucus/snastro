package snastro.ui.riassunto

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import snastro.sintesi.applicazione.letture.AzioneVista
import snastro.sintesi.applicazione.letture.ElementoVista
import snastro.sintesi.applicazione.letture.FonteVista
import snastro.sintesi.applicazione.letture.ParteTestoVista
import snastro.sintesi.applicazione.letture.PuntoChiaveVista
import snastro.sintesi.applicazione.letture.RiassuntoMostrato
import snastro.sintesi.applicazione.letture.VoceVista
import snastro.ui.SnastroTema
import snastro.ui.stile.LocalSnastroColori
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.assertTrue

private const val LARGA = 1280
private const val ALTA = 800
private const val PICCOLA_LARGA = 1024
private const val PICCOLA_ALTA = 640

/** Rework FAIL 3: tall enough that AC-S140's rich fixture never needs to scroll — the WHOLE tab,
 * bottom action area included, lands inside one capture. */
private const val ALTEZZA_COMPLETA = 2_400

// [SchedaRiassunto]'s own tags are `private`: mirrored here (the same literals) rather than exposed,
// same pattern this file already uses for "riassunto-privacy"/"riassunto-scheletro" below.
private const val TAG_CONTENUTO_TEST = "riassunto-contenuto"
private const val TAG_SCARICA_MODELLO_TEST = "riassunto-scarica-modello"
private const val TAG_NON_DISPONIBILE_TEST = "riassunto-non-disponibile"
private const val TAG_IN_CODA_TEST = "riassunto-in-coda"
private const val TAG_IN_CORSO_TEST = "riassunto-in-corso"
private const val TAG_FALLITO_TEST = "riassunto-fallito"
private const val TAG_BOTTONE_RIASSUMI_TEST = "riassunto-bottone-principale"
private const val TAG_MESSAGGIO_ERRORE_TEST = "riassunto-messaggio-errore"

/** AC-S139: [snastro.ui.testi.messaggioPer]'s own mapping of `ErroreSintesi.RiassuntoGiaAperto`. */
private const val MESSAGGIO_ERRORE_RIASSUNTO_GIA_APERTO =
    "C'è già un riassunto in coda o in corso per questa registrazione."

private val AZIONI_VUOTE = AzioniRiassunto(
    cambiaArgomento = {},
    riassumi = {},
    scaricaModello = {},
    modificaLunghezzaMassima = {},
    cambiaLunghezzaMassima = {},
    salvaLunghezzaMassima = {},
    annullaLunghezzaMassima = {},
)

private fun unArgomento(valore: String = "") = ArgomentoUiStato(valore, "${valore.length}/200", null)

@Suppress("LongParameterList")
private fun unDati(
    modello: ModelloUi = ModelloUi.Installato,
    richiesta: RichiestaUi? = null,
    fallimentoTesto: String? = null,
    nonDisponibileTesto: String? = null,
    contenuto: ContenutoUi? = null,
    argomento: ArgomentoUiStato = unArgomento(),
    lunghezzaMassima: LunghezzaMassimaUiStato = LunghezzaMassimaUiStato.Testo(2_000),
    messaggioErrore: String? = null,
) = RiassuntoUiStato.Dati(
    modello = modello,
    richiesta = richiesta,
    fallimentoTesto = fallimentoTesto,
    nonDisponibileTesto = nonDisponibileTesto,
    contenuto = contenuto,
    argomento = argomento,
    lunghezzaMassima = lunghezzaMassima,
    messaggioErrore = messaggioErrore,
)

private fun unaVoce(numero: Int, nome: String?) = VoceVista(numero, "Voce $numero", nome)

private fun unTesto(testo: String) = listOf(ParteTestoVista.Testo(testo))

private fun unaFonte(segmentoId: Int, voce: VoceVista, inizioMs: Long) = FonteVista(segmentoId, voce, inizioMs)

private fun statoModelloNonInstallato() = unDati(
    modello = ModelloUi.NonInstallato(
        "Per riassumere serve il modello di linguaggio (6,2 GB), da scaricare una volta sola.",
        "Scarica il modello (6,2 GB)",
    ),
)

private fun statoScaricando() =
    unDati(modello = ModelloUi.InDownload("Scarico il modello… 2,1 di 6,2 GB", 0.34f))

private fun statoDownloadFallito() =
    unDati(modello = ModelloUi.DownloadFallito("Non c'è abbastanza spazio sul disco (servono 6,2 GB)."))

private fun statoNonDisponibile() = unDati(
    nonDisponibileTesto = "La registrazione è troppo lunga per il riassunto (oltre 1 h 10 circa).",
)

private fun statoInCoda() = unDati(richiesta = RichiestaUi.InAttesa("In coda · 2"))

private fun statoInCorso() = unDati(richiesta = RichiestaUi.InCorso("Sto riassumendo… 1:12"))

/** AC-S140: a minimal shown Riassunto — content-order assertions (states 1/5/6/7 "con contenuto")
 * only need SOME content, not the rich AC-S140 fixture below. */
private fun contenutoSemplice(): ContenutoUi = contenutoUi(
    RiassuntoMostrato(
        argomento = null,
        lunghezzaMassimaParole = 2_000,
        superato = false,
        omessi = 0,
        sommario = unTesto("Un riassunto già mostrato prima di questo stato."),
        decisioni = listOf(ElementoVista(unTesto("Decisione precedente"), emptyList())),
        azioni = emptyList(),
        questioniAperte = emptyList(),
        puntiChiave = emptyList(),
    ),
)

/** Exactly 40 characters (AC-S140: "a 40-character Nome inside a FonteChip", not only as Responsabile). */
private const val NOME_QUARANTA_CARATTERI = "Alessandra Bianchi Quaranta Caratteri Ok"

/** AC-S140's own fixture: ≥ 12 Decisioni, 5 Fonti on one element, a 40-char Nome (both as a
 * Responsabile AND inside a FonteChip), an unattributed "Voce 3" (ring dot), and an empty Sommario
 * with elements present. */
private fun contenutoRicco(superato: Boolean = false): ContenutoUi {
    val nomeLungo = "Una persona con un nome davvero lunghissimo qui"
    val cinqueFonti = (1..5).map { unaFonte(it, unaVoce(1, "Marco"), it * 60_000L) }
    val decisioni = (1..12).map { indice ->
        ElementoVista(unTesto("Decisione numero $indice"), if (indice == 1) cinqueFonti else emptyList())
    }
    val azioni = listOf(
        AzioneVista(unTesto("Preparare il prototipo"), emptyList(), unaVoce(1, nomeLungo)),
        AzioneVista(unTesto("Nessun responsabile per questa azione"), emptyList(), null),
    )
    val puntiChiave = listOf(
        PuntoChiaveVista(
            unTesto("Punto con parlante non attribuito"),
            listOf(unaFonte(6, unaVoce(5, NOME_QUARANTA_CARATTERI), 360_000L)),
            unaVoce(3, null),
        ),
    )
    val mostrato = RiassuntoMostrato(
        argomento = "Via Roquel",
        lunghezzaMassimaParole = 2_000,
        superato = superato,
        omessi = 3,
        sommario = null,
        decisioni = decisioni,
        azioni = azioni,
        questioniAperte = listOf(ElementoVista(unTesto("Serve ancora un playtest?"), emptyList())),
        puntiChiave = puntiChiave,
    )
    return contenutoUi(mostrato)
}

private fun statoPronto() = unDati(contenuto = contenutoRicco())

private fun statoSuperato() = unDati(contenuto = contenutoRicco(superato = true))

private fun statoFallito() = unDati(
    fallimentoTesto = "Il riassunto non è riuscito: errore del modello.",
    argomento = unArgomento("Via Roquel"),
    contenuto = contenutoRicco(),
)

// AC-S125/S129/S130/S131 (rework FAIL 2): the SAME base states, but with a shown Riassunto already
// present — states 1/5 keep the shown Riassunto ABOVE (ux row 1: "this block replaces only the
// action area"; AC-S129: "stays visible above"); states 6/7 keep it BELOW (ux rows 6/7: "the shown
// Riassunto stays below").
private fun statoModelloNonInstallatoConContenuto() =
    statoModelloNonInstallato().copy(contenuto = contenutoSemplice())

private fun statoNonDisponibileConContenuto() = statoNonDisponibile().copy(contenuto = contenutoSemplice())

private fun statoInCodaConContenuto() = statoInCoda().copy(contenuto = contenutoSemplice())

private fun statoInCorsoConContenuto() = statoInCorso().copy(contenuto = contenutoSemplice())

/** AC-S139: a `Riassumi` answered with a race's `ErroreSintesi` — rendered inline, above the privacy line. */
private fun statoConMessaggioErrore() = unDati(messaggioErrore = MESSAGGIO_ERRORE_RIASSUNTO_GIA_APERTO)

/**
 * AC-S140: every state 1..12 at 1280×800/1024×640, light and a representative dark subset — a
 * `pronto` fixture with ≥ 12 Decisioni, 5 Fonti on one element, a 40-character Nome, an unattributed
 * "Voce 3" (ring dot), and an empty Sommario with elements present. [SchedaRiassunto] renders
 * directly from fixture [RiassuntoUiStato] values (dev-architecture `#presenter`).
 */
@OptIn(ExperimentalTestApi::class)
@Tag("render")
@Suppress("TooManyFunctions", "LargeClass") // AC-S140's full 1..12 × size × theme matrix, rework cycle 1
class SchedaRiassuntoRenderCheckTest {
    private val outputDir = File("build/render-check").apply { mkdirs() }

    @Test
    fun `AC-S136 state 12 caricamento a 1280x800`() =
        verifica("caricamento", RiassuntoUiStato.Caricamento, LARGA, ALTA)

    @Test
    fun `AC-S136 state 12 caricamento a 1024x640`() =
        verifica("caricamento", RiassuntoUiStato.Caricamento, PICCOLA_LARGA, PICCOLA_ALTA)

    @Test
    fun `AC-S136 state 12 caricamento scuro a 1280x800`() =
        verifica("caricamento", RiassuntoUiStato.Caricamento, LARGA, ALTA, scuro = true)

    @Test
    fun `AC-S136 state 12 caricamento scuro a 1024x640`() =
        verifica("caricamento", RiassuntoUiStato.Caricamento, PICCOLA_LARGA, PICCOLA_ALTA, scuro = true)

    @Test
    fun `AC-S125 state 1 modello non installato a 1280x800`() =
        verifica("modello-non-installato", statoModelloNonInstallato(), LARGA, ALTA)

    @Test
    fun `AC-S125 state 1 modello non installato a 1024x640`() =
        verifica("modello-non-installato", statoModelloNonInstallato(), PICCOLA_LARGA, PICCOLA_ALTA)

    @Test
    fun `AC-S125 state 1 modello non installato scuro a 1280x800`() =
        verifica("modello-non-installato", statoModelloNonInstallato(), LARGA, ALTA, scuro = true)

    @Test
    fun `AC-S125 state 1 modello non installato scuro a 1024x640`() =
        verifica("modello-non-installato", statoModelloNonInstallato(), PICCOLA_LARGA, PICCOLA_ALTA, scuro = true)

    @Test
    fun `AC-S125 state 1 con Riassunto mostrato l area di download resta sotto a 1280x800`() =
        verifica("modello-non-installato-con-contenuto", statoModelloNonInstallatoConContenuto(), LARGA, ALTA) {
            assertAreaSotto(TAG_SCARICA_MODELLO_TEST)
        }

    @Test
    fun `AC-S125 state 1 con Riassunto mostrato l area di download resta sotto a 1024x640`() =
        verifica(
            "modello-non-installato-con-contenuto",
            statoModelloNonInstallatoConContenuto(),
            PICCOLA_LARGA,
            PICCOLA_ALTA,
        ) { assertAreaSotto(TAG_SCARICA_MODELLO_TEST) }

    @Test
    fun `AC-S126 state 2 download in corso a 1280x800`() =
        verifica("download-in-corso", statoScaricando(), LARGA, ALTA)

    @Test
    fun `AC-S126 state 2 download in corso a 1024x640`() =
        verifica("download-in-corso", statoScaricando(), PICCOLA_LARGA, PICCOLA_ALTA)

    @Test
    fun `AC-S126 state 2 download in corso scuro a 1280x800`() =
        verifica("download-in-corso", statoScaricando(), LARGA, ALTA, scuro = true)

    @Test
    fun `AC-S126 state 2 download in corso scuro a 1024x640`() =
        verifica("download-in-corso", statoScaricando(), PICCOLA_LARGA, PICCOLA_ALTA, scuro = true)

    @Test
    fun `AC-S127 state 3 download fallito a 1280x800`() =
        verifica("download-fallito", statoDownloadFallito(), LARGA, ALTA)

    @Test
    fun `AC-S127 state 3 download fallito a 1024x640`() =
        verifica("download-fallito", statoDownloadFallito(), PICCOLA_LARGA, PICCOLA_ALTA)

    @Test
    fun `AC-S127 state 3 download fallito scuro a 1280x800`() =
        verifica("download-fallito", statoDownloadFallito(), LARGA, ALTA, scuro = true)

    @Test
    fun `AC-S127 state 3 download fallito scuro a 1024x640`() =
        verifica("download-fallito", statoDownloadFallito(), PICCOLA_LARGA, PICCOLA_ALTA, scuro = true)

    @Test
    fun `AC-S128 state 4 nessun riassunto a 1280x800`() = verifica("nessun-riassunto", unDati(), LARGA, ALTA)

    @Test
    fun `AC-S128 state 4 nessun riassunto a 1024x640`() =
        verifica("nessun-riassunto", unDati(), PICCOLA_LARGA, PICCOLA_ALTA)

    @Test
    fun `AC-S128 state 4 nessun riassunto scuro a 1280x800`() =
        verifica("nessun-riassunto", unDati(), LARGA, ALTA, scuro = true)

    @Test
    fun `AC-S128 state 4 nessun riassunto scuro a 1024x640`() =
        verifica("nessun-riassunto", unDati(), PICCOLA_LARGA, PICCOLA_ALTA, scuro = true)

    @Test
    fun `AC-S129 state 5 non disponibile a 1280x800`() =
        verifica("non-disponibile", statoNonDisponibile(), LARGA, ALTA)

    @Test
    fun `AC-S129 state 5 non disponibile a 1024x640`() =
        verifica("non-disponibile", statoNonDisponibile(), PICCOLA_LARGA, PICCOLA_ALTA)

    @Test
    fun `AC-S129 state 5 non disponibile scuro a 1280x800`() =
        verifica("non-disponibile", statoNonDisponibile(), LARGA, ALTA, scuro = true)

    @Test
    fun `AC-S129 state 5 non disponibile scuro a 1024x640`() =
        verifica("non-disponibile", statoNonDisponibile(), PICCOLA_LARGA, PICCOLA_ALTA, scuro = true)

    @Test
    fun `AC-S129 state 5 con Riassunto mostrato l area resta sotto a 1280x800`() =
        verifica("non-disponibile-con-contenuto", statoNonDisponibileConContenuto(), LARGA, ALTA) {
            assertAreaSotto(TAG_NON_DISPONIBILE_TEST)
        }

    @Test
    fun `AC-S129 state 5 con Riassunto mostrato l area resta sotto a 1024x640`() =
        verifica(
            "non-disponibile-con-contenuto",
            statoNonDisponibileConContenuto(),
            PICCOLA_LARGA,
            PICCOLA_ALTA,
        ) { assertAreaSotto(TAG_NON_DISPONIBILE_TEST) }

    @Test
    fun `AC-S130 state 6 in coda a 1280x800`() = verifica("in-coda", statoInCoda(), LARGA, ALTA)

    @Test
    fun `AC-S130 state 6 in coda a 1024x640`() =
        verifica("in-coda", statoInCoda(), PICCOLA_LARGA, PICCOLA_ALTA)

    @Test
    fun `AC-S130 state 6 in coda scuro a 1280x800`() =
        verifica("in-coda", statoInCoda(), LARGA, ALTA, scuro = true)

    @Test
    fun `AC-S130 state 6 in coda scuro a 1024x640`() =
        verifica("in-coda", statoInCoda(), PICCOLA_LARGA, PICCOLA_ALTA, scuro = true)

    @Test
    fun `AC-S130 state 6 con Riassunto mostrato lo stato resta sopra a 1280x800`() =
        verifica("in-coda-con-contenuto", statoInCodaConContenuto(), LARGA, ALTA) {
            assertStatoSopra(TAG_IN_CODA_TEST)
        }

    @Test
    fun `AC-S130 state 6 con Riassunto mostrato lo stato resta sopra a 1024x640`() =
        verifica("in-coda-con-contenuto", statoInCodaConContenuto(), PICCOLA_LARGA, PICCOLA_ALTA) {
            assertStatoSopra(TAG_IN_CODA_TEST)
        }

    @Test
    fun `AC-S131 state 7 in corso a 1280x800`() = verifica("in-corso", statoInCorso(), LARGA, ALTA)

    @Test
    fun `AC-S131 state 7 in corso a 1024x640`() =
        verifica("in-corso", statoInCorso(), PICCOLA_LARGA, PICCOLA_ALTA)

    @Test
    fun `AC-S131 state 7 in corso scuro a 1280x800`() =
        verifica("in-corso", statoInCorso(), LARGA, ALTA, scuro = true)

    @Test
    fun `AC-S131 state 7 in corso scuro a 1024x640`() =
        verifica("in-corso", statoInCorso(), PICCOLA_LARGA, PICCOLA_ALTA, scuro = true)

    @Test
    fun `AC-S131 state 7 con Riassunto mostrato lo stato resta sopra a 1280x800`() =
        verifica("in-corso-con-contenuto", statoInCorsoConContenuto(), LARGA, ALTA) {
            assertStatoSopra(TAG_IN_CORSO_TEST)
        }

    @Test
    fun `AC-S131 state 7 con Riassunto mostrato lo stato resta sopra a 1024x640`() =
        verifica("in-corso-con-contenuto", statoInCorsoConContenuto(), PICCOLA_LARGA, PICCOLA_ALTA) {
            assertStatoSopra(TAG_IN_CORSO_TEST)
        }

    @Test
    fun `AC-S132 AC-S140 state 8 pronto ricco a 1280x800`() = verifica("pronto", statoPronto(), LARGA, ALTA)

    @Test
    fun `AC-S132 AC-S140 state 8 pronto ricco a 1024x640`() =
        verifica("pronto", statoPronto(), PICCOLA_LARGA, PICCOLA_ALTA)

    @Test
    fun `AC-S132 AC-S140 state 8 pronto ricco scuro a 1280x800`() =
        verifica("pronto", statoPronto(), LARGA, ALTA, scuro = true)

    @Test
    fun `AC-S132 AC-S140 state 8 pronto ricco scuro a 1024x640`() =
        verifica("pronto", statoPronto(), PICCOLA_LARGA, PICCOLA_ALTA, scuro = true)

    /**
     * Rework FAIL 3: at 1024×640 the rich fixture's bottom action area falls past the viewport — a
     * canvas tall enough for the WHOLE tab (no scroll needed) makes it inspectable in one PNG, instead
     * of only `assertExists`-proving it composed.
     */
    @Test
    fun `AC-S140 state 8 pronto ricco altezza completa mostra l area azione a 1024`() =
        verifica("pronto-altezza-completa", statoPronto(), PICCOLA_LARGA, ALTEZZA_COMPLETA) {
            onNodeWithTag(TAG_BOTTONE_RIASSUMI_TEST).assertIsDisplayed()
        }

    @Test
    fun `AC-S133 state 9 superato a 1280x800`() = verifica("superato", statoSuperato(), LARGA, ALTA)

    @Test
    fun `AC-S133 state 9 superato a 1024x640`() =
        verifica("superato", statoSuperato(), PICCOLA_LARGA, PICCOLA_ALTA)

    @Test
    fun `AC-S133 state 9 superato scuro a 1280x800`() =
        verifica("superato", statoSuperato(), LARGA, ALTA, scuro = true)

    @Test
    fun `AC-S133 state 9 superato scuro a 1024x640`() =
        verifica("superato", statoSuperato(), PICCOLA_LARGA, PICCOLA_ALTA, scuro = true)

    @Test
    fun `AC-S140 state 9 superato altezza completa mostra l area azione a 1024`() =
        verifica("superato-altezza-completa", statoSuperato(), PICCOLA_LARGA, ALTEZZA_COMPLETA) {
            onNodeWithTag(TAG_BOTTONE_RIASSUMI_TEST).assertIsDisplayed()
        }

    @Test
    fun `AC-S134 state 10 fallito a 1280x800`() =
        verifica("fallito", statoFallito(), LARGA, ALTA) { assertStatoSopra(TAG_FALLITO_TEST) }

    @Test
    fun `AC-S134 state 10 fallito a 1024x640`() =
        verifica("fallito", statoFallito(), PICCOLA_LARGA, PICCOLA_ALTA) { assertStatoSopra(TAG_FALLITO_TEST) }

    @Test
    fun `AC-S134 state 10 fallito scuro a 1280x800`() =
        verifica("fallito", statoFallito(), LARGA, ALTA, scuro = true)

    @Test
    fun `AC-S134 state 10 fallito scuro a 1024x640`() =
        verifica("fallito", statoFallito(), PICCOLA_LARGA, PICCOLA_ALTA, scuro = true)

    @Test
    fun `AC-S139 un ErroreSintesi mostra il messaggio inline sopra la riga privacy a 1280x800`() =
        verifica("messaggio-errore", statoConMessaggioErrore(), LARGA, ALTA) {
            onNodeWithTag(TAG_MESSAGGIO_ERRORE_TEST).assertIsDisplayed()
            onNodeWithText(MESSAGGIO_ERRORE_RIASSUNTO_GIA_APERTO).assertIsDisplayed()
        }

    @Test
    fun `AC-S139 un ErroreSintesi mostra il messaggio inline sopra la riga privacy a 1024x640`() =
        verifica("messaggio-errore", statoConMessaggioErrore(), PICCOLA_LARGA, PICCOLA_ALTA) {
            onNodeWithTag(TAG_MESSAGGIO_ERRORE_TEST).assertIsDisplayed()
        }

    /** AC-S129/AC-S125 (rework FAIL 2): the bottom-placed area never overlaps the content above it. */
    private fun ComposeUiTest.assertAreaSotto(tagArea: String) {
        val contenuto = onNodeWithTag(TAG_CONTENUTO_TEST).getUnclippedBoundsInRoot()
        val area = onNodeWithTag(tagArea).getUnclippedBoundsInRoot()
        assertTrue(area.top >= contenuto.bottom, "l'area azione ($tagArea) deve restare sotto il Riassunto mostrato")
    }

    /** AC-S130/AC-S131/AC-S134 (rework FAIL 2): the top-placed status never overlaps the content below it. */
    private fun ComposeUiTest.assertStatoSopra(tagStato: String) {
        val contenuto = onNodeWithTag(TAG_CONTENUTO_TEST).getUnclippedBoundsInRoot()
        val stato = onNodeWithTag(tagStato).getUnclippedBoundsInRoot()
        assertTrue(stato.bottom <= contenuto.top, "lo stato ($tagStato) deve restare sopra il Riassunto mostrato")
    }

    @Suppress("LongParameterList") // one parameter per render dimension (size/theme/extra assertion), like unDati above
    private fun verifica(
        nome: String,
        stato: RiassuntoUiStato,
        width: Int,
        height: Int,
        scuro: Boolean = false,
        asserzioni: ComposeUiTest.() -> Unit = {},
    ) = runDesktopComposeUiTest(width, height) {
        setContent {
            SnastroTema(scuro = scuro, riduciMovimento = true) {
                // Mirrors the real host (`SchermataRegistrazione`'s own top-level `Surface`) — this
                // composable is embedded content and paints no background of its own.
                Surface(color = LocalSnastroColori.current.surface, modifier = Modifier.fillMaxSize()) {
                    SchedaRiassunto(stato, AZIONI_VUOTE)
                }
            }
        }
        // A rich `pronto`/`superato`/`fallito` fixture legitimately scrolls the privacy line out of
        // the viewport (AC-S140's own ≥ 12 Decisioni case) — `assertExists` proves it composed at
        // all (never silently dropped), the PNG is what a human reads for clipping/overflow.
        if (stato is RiassuntoUiStato.Dati) {
            onNodeWithTag("riassunto-privacy").assertExists()
            onNodeWithText("Il riassunto si fa sul tuo computer: nessun testo esce.").assertExists()
        } else {
            onNodeWithTag("riassunto-scheletro").assertIsDisplayed()
        }
        asserzioni()
        val suffisso = if (scuro) "-scuro" else ""
        val png = File(outputDir, "riassunto-$nome$suffisso-${width}x$height.png")
        val bitmap = onRoot().captureToImage().toAwtImage()
        ImageIO.write(bitmap, "PNG", png)
        check(png.exists() && png.length() > 0) { "renderCheck: PNG not written: $png" }
    }
}
