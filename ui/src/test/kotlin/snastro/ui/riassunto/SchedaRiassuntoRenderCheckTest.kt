package snastro.ui.riassunto

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
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

private const val LARGA = 1280
private const val ALTA = 800
private const val PICCOLA_LARGA = 1024
private const val PICCOLA_ALTA = 640

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

/** AC-S140's own fixture: ≥ 12 Decisioni, 5 Fonti on one element, a 40-char Nome, an unattributed
 * "Voce 3" (ring dot), and an empty Sommario with elements present. */
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
        PuntoChiaveVista(unTesto("Punto con parlante non attribuito"), emptyList(), unaVoce(3, null)),
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

/**
 * AC-S140: every state 1..12 at 1280×800/1024×640, light and a representative dark subset — a
 * `pronto` fixture with ≥ 12 Decisioni, 5 Fonti on one element, a 40-character Nome, an unattributed
 * "Voce 3" (ring dot), and an empty Sommario with elements present. [SchedaRiassunto] renders
 * directly from fixture [RiassuntoUiStato] values (dev-architecture `#presenter`).
 */
@OptIn(ExperimentalTestApi::class)
@Tag("render")
class SchedaRiassuntoRenderCheckTest {
    private val outputDir = File("build/render-check").apply { mkdirs() }

    @Test
    fun `AC-S136 state 12 caricamento a 1280x800`() =
        verifica("caricamento", RiassuntoUiStato.Caricamento, LARGA, ALTA)

    @Test
    fun `AC-S136 state 12 caricamento a 1024x640`() =
        verifica("caricamento", RiassuntoUiStato.Caricamento, PICCOLA_LARGA, PICCOLA_ALTA)

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
    fun `AC-S126 state 2 download in corso a 1280x800`() =
        verifica("download-in-corso", statoScaricando(), LARGA, ALTA)

    @Test
    fun `AC-S126 state 2 download in corso a 1024x640`() =
        verifica("download-in-corso", statoScaricando(), PICCOLA_LARGA, PICCOLA_ALTA)

    @Test
    fun `AC-S127 state 3 download fallito a 1280x800`() =
        verifica("download-fallito", statoDownloadFallito(), LARGA, ALTA)

    @Test
    fun `AC-S127 state 3 download fallito a 1024x640`() =
        verifica("download-fallito", statoDownloadFallito(), PICCOLA_LARGA, PICCOLA_ALTA)

    @Test
    fun `AC-S128 state 4 nessun riassunto a 1280x800`() = verifica("nessun-riassunto", unDati(), LARGA, ALTA)

    @Test
    fun `AC-S128 state 4 nessun riassunto a 1024x640`() =
        verifica("nessun-riassunto", unDati(), PICCOLA_LARGA, PICCOLA_ALTA)

    @Test
    fun `AC-S128 state 4 nessun riassunto scuro a 1280x800`() =
        verifica("nessun-riassunto", unDati(), LARGA, ALTA, scuro = true)

    @Test
    fun `AC-S129 state 5 non disponibile a 1280x800`() =
        verifica("non-disponibile", statoNonDisponibile(), LARGA, ALTA)

    @Test
    fun `AC-S129 state 5 non disponibile a 1024x640`() =
        verifica("non-disponibile", statoNonDisponibile(), PICCOLA_LARGA, PICCOLA_ALTA)

    @Test
    fun `AC-S130 state 6 in coda a 1280x800`() = verifica("in-coda", statoInCoda(), LARGA, ALTA)

    @Test
    fun `AC-S130 state 6 in coda a 1024x640`() =
        verifica("in-coda", statoInCoda(), PICCOLA_LARGA, PICCOLA_ALTA)

    @Test
    fun `AC-S131 state 7 in corso a 1280x800`() = verifica("in-corso", statoInCorso(), LARGA, ALTA)

    @Test
    fun `AC-S131 state 7 in corso a 1024x640`() =
        verifica("in-corso", statoInCorso(), PICCOLA_LARGA, PICCOLA_ALTA)

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

    @Test
    fun `AC-S133 state 9 superato a 1280x800`() = verifica("superato", statoSuperato(), LARGA, ALTA)

    @Test
    fun `AC-S133 state 9 superato a 1024x640`() =
        verifica("superato", statoSuperato(), PICCOLA_LARGA, PICCOLA_ALTA)

    @Test
    fun `AC-S134 state 10 fallito a 1280x800`() = verifica("fallito", statoFallito(), LARGA, ALTA)

    @Test
    fun `AC-S134 state 10 fallito a 1024x640`() =
        verifica("fallito", statoFallito(), PICCOLA_LARGA, PICCOLA_ALTA)

    private fun verifica(nome: String, stato: RiassuntoUiStato, width: Int, height: Int, scuro: Boolean = false) =
        runDesktopComposeUiTest(width, height) {
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
            val suffisso = if (scuro) "-scuro" else ""
            val png = File(outputDir, "riassunto-$nome$suffisso-${width}x$height.png")
            val bitmap = onRoot().captureToImage().toAwtImage()
            ImageIO.write(bitmap, "PNG", png)
            check(png.exists() && png.length() > 0) { "renderCheck: PNG not written: $png" }
        }
}
