package snastro.ui.registrazione

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.parlanti.applicazione.eventi.TipoParlanteVista
import snastro.parlanti.applicazione.letture.PropostaDiUnione
import snastro.parlanti.applicazione.porte.Fascia
import snastro.parlanti.dominio.ErroreParlanti
import snastro.ui.lettore.LettoreUiStato
import snastro.ui.stile.ColoriChiari
import snastro.ui.testi.AVVISO_TUTTA_LA_VOCE
import snastro.ui.testi.ETICHETTA_ANNULLA
import snastro.ui.testi.ETICHETTA_BRICIOLA_REGISTRAZIONI
import snastro.ui.testi.ETICHETTA_CALCOLA
import snastro.ui.testi.ETICHETTA_CAMBIA
import snastro.ui.testi.ETICHETTA_DAI_UN_NOME
import snastro.ui.testi.ETICHETTA_DIVIDI_VOCE
import snastro.ui.testi.ETICHETTA_MOSTRA_CARTELLA
import snastro.ui.testi.ETICHETTA_NUOVA_PERSONA
import snastro.ui.testi.ETICHETTA_RICORRENTE
import snastro.ui.testi.ETICHETTA_TOGLI_CONFERMA
import snastro.ui.testi.ETICHETTA_UNISCI_CON
import snastro.ui.testi.MESSAGGIO_COMANDO_IN_ATTESA
import snastro.ui.testi.MESSAGGIO_ERRORE_VOCI
import snastro.ui.testi.MESSAGGIO_ESTRATTI_NON_DISPONIBILI
import snastro.ui.testi.MESSAGGIO_PROPOSTA_IN_ATTESA
import snastro.ui.testi.MESSAGGIO_RITRASCRIZIONE_IN_CORSO
import snastro.ui.testi.MESSAGGIO_RITRASCRIZIONE_PERSA
import snastro.ui.testi.SPIEGAZIONE_DIVIDI_INTERA_VOCE
import snastro.ui.testi.SUGGERIMENTO_PRIMA_REGISTRAZIONE
import snastro.ui.testi.SUGGERIMENTO_RIFERIMENTI_INSUFFICIENTI
import snastro.ui.testi.TITOLO_PANNELLO_VOCI
import snastro.ui.testi.TOOLTIP_FRASE_CONFERMATA
import snastro.ui.testi.messaggioPer
import snastro.ui.testi.testoPersone
import snastro.ui.testi.testoSelezione
import java.awt.image.BufferedImage
import java.io.File
import java.time.LocalDate
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val W_GRANDE = 1280
private const val H_GRANDE = 800
private const val W_PICCOLA = 1024
private const val H_PICCOLA = 640

/** rework cycle 1, HIGH-3: how far in from a `BottoneSn`'s own left edge to sample its fill color —
 * inside the smallest horizontal padding it ever uses (`piccolo`'s 10.dp), clear of its icon/label. */
private const val PROFONDITA_CAMPIONE_BOTTONE = 3f

private val contieneCifre = SemanticsMatcher("contiene cifre") { nodo ->
    nodo.config.getOrNull(SemanticsProperties.Text).orEmpty().any { t -> t.text.any(Char::isDigit) }
}

private const val NOME_LUNGO = "Maria Antonietta Bellavista-Scognamiglio"

private fun riga(n: Int, voce: Int, etichetta: String, testo: String) = SegmentoRiga(
    segmentoId = SegmentoId(n),
    voceId = VoceId(voce),
    etichettaVoce = etichetta,
    inizioMs = n * 4_000L,
    fineMs = n * 4_000L + 3_500,
    testo = testo,
)

private fun opzioni(vararg escluse: Int) =
    listOf(OpzioneVoce(VoceId(1), "Marco"), OpzioneVoce(VoceId(2), "Voce 2"), OpzioneVoce(VoceId(3), "Voce 3"))
        .filter { it.voceId.numero !in escluse }

private fun daIdentificare(proposta: StatoProposta, galleriaVuota: Boolean = false) =
    ContenutoCarta.DaIdentificare(proposta, galleriaVuota)

private val CARTA_ATTRIBUITA = CartaVoce(
    VoceId(1),
    "Voce 1",
    ContenutoCarta.Attribuita(MARCO.parlanteId, "Marco", TipoParlanteVista.RICORRENTE),
    altreVoci = opzioni(1),
)

private val CARTA_CANDIDATI = CartaVoce(
    VoceId(2),
    "Voce 2",
    daIdentificare(
        StatoProposta.Pronta(
            listOf(
                unCandidato(MARCO, Fascia.FORTE),
                unCandidato(GIULIA.copy(nome = NOME_LUNGO), Fascia.DEBOLE),
            ),
            nuovoEvidenziato = false,
        ),
    ),
    altreVoci = opzioni(2),
)

private val CARTA_NESSUNA = CartaVoce(
    VoceId(3),
    "Voce 3",
    daIdentificare(StatoProposta.Pronta(listOf(unCandidato(GIULIA, Fascia.NESSUNA)), nuovoEvidenziato = true)),
    altreVoci = opzioni(3),
)

private fun pannello(
    carte: List<CartaVoce>,
    unioni: List<PropostaDiUnione> = emptyList(),
    estratti: Boolean = true,
    somiglianza: PannelloSomiglianza? = null,
) = PannelloVoci(
    carte,
    listOf(GIULIA, MARCO),
    unioni,
    estrattiDisponibili = estratti,
    unioneAbilitata = true,
    somiglianza = somiglianza,
)

/** ADR 0019 §6: the 'Riassegna per somiglianza' header area in [fase], with the Amendment (b).6 lines. */
private fun somiglianza(
    fase: FaseSomiglianza,
    abilitato: Boolean = fase == FaseSomiglianza.Inattiva,
    suggerimento: String? = null,
) = PannelloSomiglianza(
    abilitato = abilitato,
    suggerimento = suggerimento,
    riferimenti = "Riferimenti: Marco (frasi confermate) · $NOME_LUNGO (tutta la voce)",
    avvisoTuttaLaVoce = AVVISO_TUTTA_LA_VOCE,
    nonToccate = null,
    fase = fase,
)

private val CARTE_TRE = listOf(CARTA_ATTRIBUITA, CARTA_CANDIDATI, CARTA_NESSUNA)

/** AC-531/AC-545: while computing or previewing every card action and the merge are disabled. */
private fun bloccato(s: PannelloSomiglianza) =
    pannello(CARTE_TRE.map { it.copy(soloLettura = true) }, somiglianza = s).copy(unioneAbilitata = false)

@Suppress("LongParameterList") // one parameter per RegistrazioneUiStato.Dati field these fixtures vary
private fun stato(
    pannello: PannelloVoci,
    selezione: Set<SegmentoId> = emptySet(),
    barra: BarraSelezione? = null,
    errore: String? = null,
    soloLettura: Boolean = false,
    bannerRitrascrizionePannello: String? = null,
    confermati: Set<Int> = emptySet(),
) = RegistrazioneUiStato.Dati(
    titolo = "Seduta del 12 marzo",
    dataRegistrazione = LocalDate.of(2026, 3, 12),
    durataMs = 185_000,
    segmenti = listOf(
        riga(1, 1, "Marco", "Buongiorno a tutti, iniziamo con il punto sull'ordine del giorno di oggi."),
        riga(2, 2, "Voce 2", "Salve. Io avrei una domanda sul bilancio che abbiamo ricevuto ieri sera."),
        riga(3, 1, "Marco", "Certo, prego."),
        riga(4, 3, "Voce 3", "Va bene, allora prendo nota."),
        riga(5, 2, "Voce 2", "Grazie."),
    ).map { if (it.segmentoId.numero in confermati) it.copy(confermato = true) else it },
    barra = LettoreUiStato.Inattivo,
    audioDisponibile = pannello.estrattiDisponibili,
    documentoPercorso = "/progetti/demo.snastro/documenti/2026-03-12 Seduta.md",
    errore = errore,
    pannello = pannello,
    selezione = selezione,
    barraSelezione = barra,
    soloLettura = soloLettura,
    bannerRitrascrizione = if (soloLettura) MESSAGGIO_RITRASCRIZIONE_IN_CORSO else null,
    bannerRitrascrizionePannello = bannerRitrascrizionePannello,
)

/**
 * `:ui:renderCheck` of the R2 Voci panel + Revisione toolbar (schermata-registrazione-identificazione):
 * every state at 1280x800 and 1024x640 — sizing (fixed-width panel, transcript takes the rest),
 * overflow (long Nome ellipsized, action buttons wrap), contrast, state rendering. PNGs in
 * `build/render-check/registrazione-voci-*`.
 */
@OptIn(ExperimentalTestApi::class)
@Tag("render")
@Suppress("TooManyFunctions", "LargeClass") // one test per tests_nl/AC item, rework cycle 1 added more
class RegistrazioneVociRenderCheckTest {
    private val outputDir = File("build/render-check").apply { mkdirs() }

    private fun scena(
        nome: String,
        stato: RegistrazioneUiStato.Dati,
        azioni: AzioniRegistrazione = AzioniRegistrazione({}, {}, {}, {}, {}, {}, {}),
        scuro: Boolean = false,
        verifica: ComposeUiTest.() -> Unit,
    ) = listOf(W_GRANDE to H_GRANDE, W_PICCOLA to H_PICCOLA).forEach { (w, h) ->
        runDesktopComposeUiTest(w, h) {
            setContent { SchermataRegistrazione(stato, azioni, scuro = scuro, riduciMovimento = true) }
            onNodeWithTag("voci-pannello").assertIsDisplayed()
            onNodeWithTag("registrazione-lista").assertIsDisplayed()
            verifica()
            val suffisso = if (scuro) "-scuro" else ""
            val png = File(outputDir, "registrazione-voci-$nome-${w}x$h$suffisso.png")
            ImageIO.write(immagineDellaScena(w, h), "PNG", png)
            check(png.exists() && png.length() > 0) { "renderCheck: PNG not written: $png" }
        }
    }

    /** Every root of the scene layered in order — an open menu is a second root (its popup layer). */
    private fun ComposeUiTest.immagineDellaScena(w: Int, h: Int): BufferedImage {
        val radici = onAllNodes(isRoot())
        val immagine = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = immagine.createGraphics()
        repeat(radici.fetchSemanticsNodes().size) { i ->
            g.drawImage(radici[i].captureToImage().toAwtImage(), 0, 0, null)
        }
        g.dispose()
        return immagine
    }

    @Test
    fun `AC-214 card non identificata con Candidati FORTE e DEBOLE come barre, mai numeri`() =
        scena("candidati", stato(pannello(listOf(CARTA_ATTRIBUITA, CARTA_CANDIDATI, CARTA_NESSUNA)))) {
            // AC-581 stacks the panel under the transcript below 1100dp — its own list is shorter there,
            // so each row past the first fold is reached the same way AC-416 already reaches its third
            // card, one `performScrollToNode` per target (a card taller than the viewport itself).
            onNodeWithTag("voci-lista").performScrollToNode(hasTestTag("voce-2-candidato-0-fascia"))
            onNodeWithTag("voce-2-candidato-0-fascia").assertIsDisplayed()
            onNodeWithTag("voci-lista").performScrollToNode(hasTestTag("voce-2-candidato-1-fascia"))
            onNodeWithTag("voce-2-candidato-1-fascia").assertIsDisplayed()
            onNodeWithTag("voci-lista").performScrollToNode(hasTestTag("voce-2-conferma"))
            onNodeWithTag("voce-2-conferma").assertIsEnabled()
            // AC-214: nothing inside the Candidato rows reads as a number.
            val cifre = onAllNodes(hasAnyAncestor(hasTestTag("voce-2-candidato-0")) and contieneCifre)
            assertEquals(0, cifre.fetchSemanticsNodes().size)
        }

    @Test
    fun `AC-219 card attribuita con Nome e cambia, senza salta`() =
        scena("attribuita", stato(pannello(listOf(CARTA_ATTRIBUITA, CARTA_CANDIDATI)))) {
            onNodeWithTag("voce-1-nome").assertIsDisplayed()
            onNodeWithTag("voce-1-cambia").assertIsDisplayed()
            assertEquals(0, onAllNodes(hasTestTag("voce-1-salta")).fetchSemanticsNodes().size)
            onAllNodesWithText("Marco", substring = true)[0].assertIsDisplayed()
        }

    @Test
    fun `AC-454 pannello in sola lettura disabilita le azioni delle card, estratto resta usabile`() =
        scena(
            "sola-lettura",
            stato(
                pannello(
                    listOf(
                        CARTA_ATTRIBUITA.copy(soloLettura = true),
                        CARTA_CANDIDATI.copy(soloLettura = true),
                    ),
                ).copy(unioneAbilitata = false),
                soloLettura = true,
                bannerRitrascrizionePannello = MESSAGGIO_RITRASCRIZIONE_PERSA,
            ),
        ) {
            onNodeWithTag("registrazione-banner-ritrascrizione").assertIsDisplayed()
            onNodeWithText(
                MESSAGGIO_RITRASCRIZIONE_IN_CORSO.substringBefore("\n"),
                substring = true,
            ).assertIsDisplayed()
            // AC-588: BannerSn (the kit) renders `titolo` + `testo` as its own two Text nodes — the
            // R2 third line lives inside the SAME `testo` node as the R1 second line (joined by '\n'),
            // so it is found as a substring, not an exact match (rework cycle 1, MED-9).
            onNodeWithText(MESSAGGIO_RITRASCRIZIONE_PERSA, substring = true).assertIsDisplayed()
            onNodeWithTag("voce-1-cambia").assertIsNotEnabled()
            onNodeWithTag("voce-2-conferma").assertIsNotEnabled()
            onNodeWithTag("voce-2-salta").assertIsNotEnabled()
            onNodeWithTag("voce-1-estratto").assertIsEnabled() // AC-454: '▶ estratto' stays usable
        }

    @Test
    fun `AC-412 AC-413 card in attesa dell elaborazione con Annulla, azioni disabilitate`() {
        var annullata: VoceId? = null
        val inAttesa = CARTA_CANDIDATI.copy(inCorso = AttesaComando.IN_ATTESA)
        val azioni = AzioniRegistrazione({}, {}, {}, {}, {}, {}, {}, annullaComando = { annullata = it })
        scena("comando-in-attesa", stato(pannello(listOf(CARTA_ATTRIBUITA, inAttesa, CARTA_NESSUNA))), azioni) {
            onNodeWithTag("voci-lista").performScrollToNode(hasTestTag("voce-2"))
            onNodeWithText(MESSAGGIO_COMANDO_IN_ATTESA).assertIsDisplayed()
            onNodeWithTag("voce-2-conferma").assertIsNotEnabled()
            onNodeWithTag("voce-2-salta").assertIsNotEnabled()
            onNodeWithTag("voce-1-cambia").assertIsEnabled() // the other cards stay usable (AC-411)
            onNodeWithText(ETICHETTA_ANNULLA).performClick()
        }
        assertEquals(VoceId(2), annullata)
    }

    @Test
    fun `AC-411 card in corso mostra l indicatore prima della soglia`() =
        scena(
            "comando-in-corso",
            stato(pannello(listOf(CARTA_CANDIDATI.copy(inCorso = AttesaComando.IN_CORSO), CARTA_NESSUNA))),
        ) {
            onNodeWithTag("voce-2-in-corso").assertIsDisplayed()
            assertEquals(0, onAllNodes(hasText(ETICHETTA_ANNULLA)).fetchSemanticsNodes().size)
        }

    @Test
    fun `AC-416 AC-405 Proposta in attesa senza Annulla, caricamento per card e galleria vuota`() =
        scena(
            "proposta-in-attesa",
            stato(
                pannello(
                    listOf(
                        CartaVoce(VoceId(1), "Voce 1", daIdentificare(StatoProposta.InAttesa), altreVoci = opzioni(1)),
                        CartaVoce(
                            VoceId(2),
                            "Voce 2",
                            daIdentificare(StatoProposta.Caricamento),
                            altreVoci = opzioni(2),
                        ),
                        CartaVoce(VoceId(3), "Voce 3", ContenutoCarta.Caricamento, altreVoci = opzioni(3)),
                    ),
                ),
            ),
        ) {
            onNodeWithText(MESSAGGIO_PROPOSTA_IN_ATTESA).assertIsDisplayed()
            onNodeWithTag("voce-1-salta").assertIsEnabled()
            onNodeWithTag("voce-1-altri").assertIsEnabled()
            onNodeWithTag("voce-1-conferma").assertIsNotEnabled()
            onNodeWithTag("voci-lista").performScrollToNode(hasTestTag("voce-2"))
            onNodeWithTag("voce-2-proposta-caricamento").assertIsDisplayed()
            onNodeWithTag("voci-lista").performScrollToNode(hasTestTag("voce-3-caricamento"))
            onNodeWithTag("voce-3-caricamento").assertIsDisplayed()
        }

    @Test
    fun `AC-212 galleria vuota solo nuovo e salta con il suggerimento`() =
        scena(
            "galleria-vuota",
            stato(
                pannello(
                    listOf(
                        CartaVoce(
                            VoceId(1),
                            "Voce 1",
                            daIdentificare(StatoProposta.Pronta(emptyList(), false), galleriaVuota = true),
                            altreVoci = opzioni(1),
                        ),
                    ),
                ).copy(parlantiAttivi = emptyList()),
            ),
        ) {
            onNodeWithText(SUGGERIMENTO_PRIMA_REGISTRAZIONE).assertIsDisplayed()
            assertEquals(0, onAllNodes(hasTestTag("voce-1-altri")).fetchSemanticsNodes().size)
            onNodeWithTag("voce-1-nuovo").assertIsEnabled()
            onNodeWithTag("voce-1-salta").assertIsEnabled()
        }

    @Test
    fun `AC-216 banner di proposta di unione`() {
        var unite: Pair<VoceId, VoceId>? = null
        val azioni = AzioniRegistrazione({}, {}, {}, {}, {}, {}, {}, unisci = { a, b -> unite = a to b })
        val attribuita3 = CartaVoce(
            VoceId(3),
            "Voce 3",
            ContenutoCarta.Attribuita(MARCO.parlanteId, "Marco", TipoParlanteVista.RICORRENTE),
            altreVoci = opzioni(3),
        )
        scena(
            "unione",
            stato(
                pannello(
                    listOf(CARTA_ATTRIBUITA, CARTA_CANDIDATI, attribuita3),
                    listOf(PropostaDiUnione(VoceId(1), VoceId(3), MARCO.parlanteId, "Marco")),
                ),
            ),
            azioni,
        ) {
            onNodeWithText("Voce 1 e Voce 3 sono entrambe Marco").assertIsDisplayed()
            onNodeWithTag("voci-unione-1-3").assertIsDisplayed()
            onNodeWithText("Unisci").performClick()
        }
        assertEquals(VoceId(1) to VoceId(3), unite)
    }

    @Test
    fun `AC-209 AC-210 AC-211 selezione di Segmenti con Riassegna e Dividi voce`() =
        scena(
            "selezione",
            stato(
                pannello(listOf(CARTA_ATTRIBUITA, CARTA_CANDIDATI, CARTA_NESSUNA)),
                selezione = setOf(SegmentoId(2)),
                barra = BarraSelezione(VoceId(2), "Voce 2", 1, true, null, opzioni(2), abilitata = true),
            ),
        ) {
            onNodeWithTag("registrazione-barra-selezione").assertIsDisplayed()
            onNodeWithTag("registrazione-riassegna").assertIsEnabled()
            onNodeWithTag("registrazione-dividi").assertIsEnabled()
            onNodeWithTag("registrazione-seleziona-2").assertIsDisplayed()
        }

    @Test
    fun `AC-210 Dividi voce disabilitato con la spiegazione sull intera Voce`() =
        scena(
            "selezione-intera-voce",
            stato(
                pannello(listOf(CARTA_ATTRIBUITA, CARTA_CANDIDATI)),
                selezione = setOf(SegmentoId(2), SegmentoId(5)),
                barra = BarraSelezione(VoceId(2), "Voce 2", 2, false, SPIEGAZIONE_DIVIDI_INTERA_VOCE, opzioni(2), true),
            ),
        ) {
            onNodeWithText(ETICHETTA_DIVIDI_VOCE).assertIsNotEnabled()
            onNodeWithText(SPIEGAZIONE_DIVIDI_INTERA_VOCE).assertIsDisplayed()
        }

    @Test
    fun `AC-215 AC-404 AC-403 errori inline sulla card e nel trascritto, estratti disabilitati`() =
        scena(
            "errore",
            stato(
                pannello(
                    listOf(
                        CARTA_ATTRIBUITA,
                        CARTA_CANDIDATI.copy(errore = messaggioPer(ErroreParlanti.NomeGiaInUso("Marco"))),
                        CartaVoce(VoceId(3), "Voce 3", ContenutoCarta.Errore(MESSAGGIO_ERRORE_VOCI)),
                    ),
                    estratti = false,
                ),
                errore = "Questo segmento non può essere riassegnato a questa voce.",
            ),
        ) {
            // The transcript-side assertions never depend on the panel's own scroll position — checked
            // before scrolling `voci-lista`, so they never race with it.
            onNodeWithTag("registrazione-errore").assertIsDisplayed()
            onNodeWithText(MESSAGGIO_ESTRATTI_NON_DISPONIBILI).assertIsDisplayed()
            onNodeWithTag("voci-lista").performScrollToNode(hasTestTag("voce-2-errore"))
            onNodeWithTag("voce-2-errore").assertIsDisplayed()
            onNodeWithTag("voce-2-estratto").assertIsNotEnabled()
            onNodeWithTag("voci-lista").performScrollToNode(hasTestTag("voce-2-conferma"))
            onNodeWithTag("voce-2-conferma").assertIsEnabled()
        }

    // --- ADR 0019 §6 + Amendment (b): 'Dai un nome a questa frase', pin, 'Riassegna per somiglianza' ---

    private fun barraFrase(confermato: Boolean) = BarraSelezione(
        VoceId(2),
        "Voce 2",
        1,
        true,
        null,
        opzioni(2),
        abilitata = true,
        frase = MenuFrase(SegmentoId(2), listOf(GIULIA.copy(nome = NOME_LUNGO), MARCO), confermato, abilitata = true),
    )

    @Test
    fun `AC-526 menu Dai un nome a questa frase con i Parlanti attivi e nuovo`() {
        var scelto: ObiettivoNome? = null
        val azioni = AzioniRegistrazione({}, {}, {}, {}, {}, {}, {}, nominaFrase = { scelto = it })
        scena(
            "frase-menu",
            stato(pannello(CARTE_TRE), selezione = setOf(SegmentoId(2)), barra = barraFrase(confermato = false)),
            azioni,
        ) {
            onNodeWithTag("registrazione-nomina-frase").assertIsEnabled().performClick()
            // AC-586: the menu item's own row is `EtichettaMenu` — name + type as two Text nodes, not
            // one merged string; the row itself (their common clickable ancestor) carries the action.
            onNode(
                hasAnyDescendant(hasText("Marco")) and
                    hasAnyDescendant(hasText(ETICHETTA_RICORRENTE)) and
                    hasClickAction(),
                useUnmergedTree = true,
            ).assertIsDisplayed()
            onAllNodesWithText(ETICHETTA_NUOVA_PERSONA).onLast().assertIsDisplayed()
            assertEquals(0, onAllNodes(hasTestTag("registrazione-togli-conferma")).fetchSemanticsNodes().size)
        }
        assertEquals(null, scelto)
    }

    @Test
    fun `AC-526 nuovo apre il modulo Nome con occasionale sotto la barra`() {
        var scelto: ObiettivoNome? = null
        val azioni = AzioniRegistrazione({}, {}, {}, {}, {}, {}, {}, nominaFrase = { scelto = it })
        scena(
            "frase-nuovo",
            stato(pannello(CARTE_TRE), selezione = setOf(SegmentoId(2)), barra = barraFrase(confermato = false)),
            azioni,
        ) {
            onNodeWithTag("registrazione-nomina-frase").performClick()
            onAllNodesWithText(ETICHETTA_NUOVA_PERSONA).onLast().performClick()
            onNodeWithTag("registrazione-frase-modulo-nuovo").assertIsDisplayed()
            // CampoSn's outer testTag sits on the label+field+helper group; the settable text node is
            // its own descendant (dev-architecture note on this block's dispatch).
            onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("registrazione-frase-nuovo-nome")))
                .performTextInput("Dario")
            onNodeWithTag("registrazione-frase-nuovo-crea").performClick()
        }
        assertEquals(ObiettivoNome.Nuovo("Dario", ricorrente = true), scelto)
    }

    @Test
    fun `AC-528 frase confermata con la puntina e Togli conferma`() {
        var tolta = false
        val azioni = AzioniRegistrazione({}, {}, {}, {}, {}, {}, {}, togliConferma = { tolta = true })
        scena(
            "frase-confermata",
            stato(
                pannello(CARTE_TRE),
                selezione = setOf(SegmentoId(2)),
                barra = barraFrase(confermato = true),
                confermati = setOf(1, 2),
            ),
            azioni,
        ) {
            onNodeWithTag("registrazione-confermato-2", useUnmergedTree = true).assertIsDisplayed()
            onNode(
                hasContentDescription(TOOLTIP_FRASE_CONFERMATA) and hasTestTag("registrazione-confermato-1"),
                useUnmergedTree = true,
            )
                .assertIsDisplayed()
            onNodeWithText(ETICHETTA_TOGLI_CONFERMA).assertIsEnabled().performClick()
        }
        assertEquals(true, tolta)
    }

    @Test
    fun `AC-529 frase in attesa dell elaborazione con Annulla sulla riga`() {
        var annullata: SegmentoId? = null
        val azioni = AzioniRegistrazione({}, {}, {}, {}, {}, {}, {}, annullaFrase = { annullata = it })
        val base = stato(pannello(CARTE_TRE))
        val conAttesa = base.copy(
            segmenti = base.segmenti.map {
                if (it.segmentoId.numero == 2) it.copy(attesaFrase = AttesaComando.IN_ATTESA) else it
            },
        )
        scena("frase-in-attesa", conAttesa, azioni) {
            onNodeWithTag("registrazione-frase-in-attesa-2", useUnmergedTree = true).assertIsDisplayed()
            onNodeWithTag("registrazione-frase-annulla-2", useUnmergedTree = true).performClick()
        }
        assertEquals(SegmentoId(2), annullata)
    }

    @Test
    fun `AC-530 pulsante abilitato con le righe dei riferimenti`() =
        scena("somiglianza-pronto", stato(pannello(CARTE_TRE, somiglianza = somiglianza(FaseSomiglianza.Inattiva)))) {
            onNodeWithTag("somiglianza-avvia").assertIsEnabled()
            onNodeWithTag("somiglianza-riferimenti").assertIsDisplayed()
            onNodeWithText(AVVISO_TUTTA_LA_VOCE).assertIsDisplayed()
        }

    @Test
    fun `AC-530 RiferimentiInsufficienti pulsante disabilitato con Dai un nome ad almeno due persone`() =
        scena(
            "somiglianza-riferimenti-insufficienti",
            stato(
                pannello(
                    CARTE_TRE,
                    somiglianza = somiglianza(
                        FaseSomiglianza.Inattiva,
                        abilitato = false,
                        suggerimento = SUGGERIMENTO_RIFERIMENTI_INSUFFICIENTI,
                    ).copy(
                        riferimenti = "Riferimenti: Marco (frasi confermate)",
                        avvisoTuttaLaVoce = null,
                        nonToccate = "Non toccate: Giulia",
                    ),
                ),
            ),
        ) {
            onNodeWithTag("somiglianza-avvia").assertIsNotEnabled()
            onNodeWithTag("somiglianza-suggerimento").assertIsDisplayed()
            onNodeWithText(SUGGERIMENTO_RIFERIMENTI_INSUFFICIENTI).assertIsDisplayed()
            onNodeWithTag("somiglianza-non-toccate").assertIsDisplayed()
        }

    @Test
    fun `AC-531 confronto in corso con barra, attesa e Annulla`() {
        var annullato = false
        val azioni = AzioniRegistrazione({}, {}, {}, {}, {}, {}, {}, annullaSomiglianza = { annullato = true })
        val calcolo = FaseSomiglianza.Calcolo("Confronto le frasi… 312 di 1024", 312, 1024, inAttesa = true)
        scena(
            "somiglianza-calcolo",
            stato(bloccato(somiglianza(calcolo))),
            azioni,
        ) {
            // AC-581: `somiglianza-annulla` sits in the same scrollable `voci-lista` as the cards now
            // (a fixed header would starve the list of height in the stacked layout) — click it while
            // still in view, before scrolling past it to reach the cards below.
            onNodeWithText("Confronto le frasi… 312 di 1024").assertIsDisplayed()
            onNodeWithTag("somiglianza-barra").assertIsDisplayed()
            onNodeWithTag("somiglianza-in-attesa").assertIsDisplayed()
            onNodeWithTag("somiglianza-annulla").performClick()
            onNodeWithTag("voci-lista").performScrollToNode(hasTestTag("voce-2"))
            onNodeWithTag("voce-2-conferma").assertIsNotEnabled()
            onNodeWithTag("voci-lista").performScrollToNode(hasTestTag("voce-1-estratto"))
            onNodeWithTag("voce-1-estratto").assertIsEnabled()
        }
        assertEquals(true, annullato)
    }

    @Test
    fun `AC-545 anteprima con Applica e Annulla`() {
        var applicato = false
        val azioni = AzioniRegistrazione({}, {}, {}, {}, {}, {}, {}, applicaSomiglianza = { applicato = true })
        val anteprima = FaseSomiglianza.Anteprima(
            "Sposterò 12 frasi, 3 incerte restano dove sono",
            listOf("Voce 3 → Marco: 8", "Voce 4 → Marco: 1", "Voce 3 → $NOME_LUNGO: 3"),
            applicabile = true,
            inApplicazione = false,
        )
        scena(
            "somiglianza-anteprima",
            stato(bloccato(somiglianza(anteprima))),
            azioni,
        ) {
            onNodeWithText("Sposterò 12 frasi, 3 incerte restano dove sono").assertIsDisplayed()
            onNodeWithTag("somiglianza-riga-2").assertIsDisplayed()
            onNodeWithTag("somiglianza-annulla").assertIsEnabled()
            onNodeWithTag("somiglianza-applica").assertIsEnabled().performClick()
        }
        assertEquals(true, applicato)
    }

    @Test
    fun `AC-545 anteprima con N zero ha solo Chiudi`() =
        scena(
            "somiglianza-anteprima-vuota",
            stato(
                pannello(
                    CARTE_TRE,
                    somiglianza = somiglianza(
                        FaseSomiglianza.Anteprima(
                            "Nessuna frase da spostare (1 incerta resta dove è)",
                            emptyList(),
                            applicabile = false,
                            inApplicazione = false,
                        ),
                    ),
                ),
            ),
        ) {
            onNodeWithTag("somiglianza-chiudi").assertIsEnabled()
            assertEquals(0, onAllNodes(hasTestTag("somiglianza-applica")).fetchSemanticsNodes().size)
        }

    @Test
    fun `AC-546 durante l applicazione i pulsanti sono disabilitati`() =
        scena(
            "somiglianza-applicazione",
            stato(
                pannello(
                    CARTE_TRE,
                    somiglianza = somiglianza(
                        FaseSomiglianza.Anteprima(
                            "Sposterò 2 frasi, 0 incerte restano dove sono",
                            listOf("Voce 3 → Marco: 2"),
                            applicabile = true,
                            inApplicazione = true,
                        ),
                    ),
                ),
            ),
        ) {
            onNodeWithTag("somiglianza-applica").assertIsNotEnabled()
            onNodeWithTag("somiglianza-annulla").assertIsNotEnabled()
        }

    @Test
    fun `AC-547 TrascrittoCambiato con Ricalcola`() {
        var ricalcolato = false
        val azioni = AzioniRegistrazione({}, {}, {}, {}, {}, {}, {}, calcolaSomiglianza = { ricalcolato = true })
        val errore =
            FaseSomiglianza.Errore("La trascrizione è cambiata dopo il confronto: ricalcola l'anteprima", true)
        val stato = stato(pannello(CARTE_TRE, somiglianza = somiglianza(errore, abilitato = true)))
        scena("somiglianza-trascritto-cambiato", stato, azioni) {
            onNodeWithText("La trascrizione è cambiata dopo il confronto: ricalcola l'anteprima").assertIsDisplayed()
            onNodeWithTag("somiglianza-ricalcola").assertIsEnabled().performClick()
        }
        assertEquals(true, ricalcolato)
    }

    @Test
    fun `AC-533 esito dopo Applica`() =
        scena(
            "somiglianza-esito",
            stato(
                pannello(
                    CARTE_TRE,
                    somiglianza = somiglianza(
                        FaseSomiglianza.Esito("12 frasi spostate, 3 incerte (rimaste dov'erano)"),
                        abilitato = true,
                    ),
                ),
            ),
        ) {
            onNodeWithText("12 frasi spostate, 3 incerte (rimaste dov'erano)").assertIsDisplayed()
            onNodeWithTag("somiglianza-messaggio-chiudi").assertIsDisplayed()
        }

    // --- AC-589: light fixtures already cover every state above; these are their `-scuro` companions
    // (named + unnamed Voce with a Fascia forte/debole proposal, a confirmed pin, a selection with its
    // toolbar, the reassign preview open) — same PNG names, `-scuro` suffix, both sizes. ---

    @Test
    fun `AC-589 card non identificata con proposta scuro`() =
        scena(
            "candidati",
            stato(pannello(listOf(CARTA_ATTRIBUITA, CARTA_CANDIDATI, CARTA_NESSUNA))),
            scuro = true,
        ) {
            onNodeWithTag("voci-lista").performScrollToNode(hasTestTag("voce-2-candidato-0-fascia"))
            onNodeWithTag("voce-2-candidato-0-fascia").assertIsDisplayed()
        }

    @Test
    fun `AC-589 card attribuita scuro`() =
        scena("attribuita", stato(pannello(listOf(CARTA_ATTRIBUITA, CARTA_CANDIDATI))), scuro = true) {
            onNodeWithTag("voce-1-nome").assertIsDisplayed()
        }

    @Test
    fun `AC-589 selezione scuro`() =
        scena(
            "selezione",
            stato(
                pannello(listOf(CARTA_ATTRIBUITA, CARTA_CANDIDATI, CARTA_NESSUNA)),
                selezione = setOf(SegmentoId(2)),
                barra = BarraSelezione(VoceId(2), "Voce 2", 1, true, null, opzioni(2), abilitata = true),
            ),
            scuro = true,
        ) {
            onNodeWithTag("registrazione-barra-selezione").assertIsDisplayed()
        }

    @Test
    fun `AC-589 frase confermata scuro`() =
        scena(
            "frase-confermata",
            stato(
                pannello(CARTE_TRE),
                selezione = setOf(SegmentoId(2)),
                barra = barraFrase(confermato = true),
                confermati = setOf(1, 2),
            ),
            scuro = true,
        ) {
            onNodeWithTag("registrazione-confermato-2", useUnmergedTree = true).assertIsDisplayed()
        }

    @Test
    fun `AC-589 anteprima riassegnazione scuro`() =
        scena(
            "somiglianza-anteprima",
            stato(
                bloccato(
                    somiglianza(
                        FaseSomiglianza.Anteprima(
                            "Sposterò 12 frasi, 3 incerte restano dove sono",
                            listOf("Voce 3 → Marco: 8", "Voce 4 → Marco: 1", "Voce 3 → $NOME_LUNGO: 3"),
                            applicabile = true,
                            inApplicazione = false,
                        ),
                    ),
                ),
            ),
            scuro = true,
        ) {
            onNodeWithText("Sposterò 12 frasi, 3 incerte restano dove sono").assertIsDisplayed()
        }

    // --- rework cycle 1: HIGH-2/3/4, MED-5/7 + the verifier's AC-579..AC-588 coverage checklist ---

    /** rework cycle 1, HIGH-3 (AC-213): `BottoneSn`'s Primario/Secondario variant only differs in fill
     * COLOR — no semantics-level signal — so proving the swap needs an actual pixel read. Sampled a few
     * px in from the button's own left edge (inside its padding, clear of the icon/label glyphs) against
     * the two known non-hover fill colors (`accent` for Primario, `raised` for Secondario). */
    private fun ComposeUiTest.coloreBottone(tag: String): Int {
        val bounds = onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val x = (bounds.left + PROFONDITA_CAMPIONE_BOTTONE).toInt()
        val y = bounds.center.y.toInt()
        return onRoot().captureToImage().toAwtImage().getRGB(x, y)
    }

    @Test
    fun `AC-213 nuovoEvidenziato false rende E Nome Primario e Nuova persona Secondario`() =
        scena("ac213-conferma-primario", stato(pannello(listOf(CARTA_CANDIDATI)))) {
            assertEquals(ColoriChiari.accent.toArgb(), coloreBottone("voce-2-conferma"))
            assertEquals(ColoriChiari.raised.toArgb(), coloreBottone("voce-2-nuovo"))
        }

    @Test
    fun `AC-213 nuovoEvidenziato true rende Nuova persona Primario e E Nome Secondario`() =
        scena("ac213-nuovo-primario", stato(pannello(listOf(CARTA_NESSUNA)))) {
            assertEquals(ColoriChiari.accent.toArgb(), coloreBottone("voce-3-nuovo"))
            assertEquals(ColoriChiari.raised.toArgb(), coloreBottone("voce-3-conferma"))
        }

    @Test
    fun `AC-213 al piu un Primario per schermata, solo la prima card da identificare lo riceve`() =
        scena("ac213-un-solo-primario", stato(pannello(listOf(CARTA_CANDIDATI, CARTA_NESSUNA)))) {
            // voce-2 (first DaIdentificare card in list order, nuovoEvidenziato=false) keeps its own
            // Primario on 'conferma' — same assertion as the single-card case above.
            assertEquals(ColoriChiari.accent.toArgb(), coloreBottone("voce-2-conferma"))
            onNodeWithTag("voci-lista").performScrollToNode(hasTestTag("voce-3-nuovo"))
            // voce-3 is SECOND: even though its own nuovoEvidenziato=true would normally make 'nuovo'
            // Primario, only one Primario is allowed on screen — it renders Secondario instead.
            assertEquals(ColoriChiari.raised.toArgb(), coloreBottone("voce-3-nuovo"))
        }

    @Test
    fun `AC-580 Unisci con disponibile su una card non ancora identificata, gate solo su unioneAbilitata`() {
        var unite: Pair<VoceId, VoceId>? = null
        val azioni = AzioniRegistrazione({}, {}, {}, {}, {}, {}, {}, unisci = { a, b -> unite = a to b })
        scena(
            "unisci-non-identificata",
            // soloLettura=true drives azioniAbilitate/confermaAbilitata to false on the card — 'Altre
            // azioni' must stay enabled anyway, gated only by pannello.unioneAbilitata (true here).
            stato(pannello(listOf(CARTA_CANDIDATI.copy(soloLettura = true)))),
            azioni,
        ) {
            onNodeWithTag("voce-2-cambia").assertIsEnabled().performClick()
            onNodeWithText(ETICHETTA_UNISCI_CON).assertIsDisplayed()
            // altreVoci = opzioni(2) = Voce 1 and Voce 3 (item 8: named only via `carte`, and no card
            // for either exists in this pannello, so both render as "Voce n", their own labels unused).
            // "Voce 1" is picked over "Voce 3": the default `stato()` transcript's own Segmento 4
            // (Voce 3, unrelated to this menu) already shows that exact text in its who-line, so only
            // "Voce 1" is unambiguous on this screen.
            onNodeWithText("Voce 1").performClick()
        }
        assertEquals(VoceId(2) to VoceId(1), unite)
    }

    @Test
    fun `AC-581 sotto i 1100dp il pannello Voci sta sotto il trascritto, non accanto`() =
        runDesktopComposeUiTest(W_PICCOLA, H_PICCOLA) {
            setContent {
                SchermataRegistrazione(
                    stato(pannello(listOf(CARTA_ATTRIBUITA))),
                    AzioniRegistrazione({}, {}, {}, {}, {}, {}, {}),
                    riduciMovimento = true,
                )
            }
            val trascritto = onNodeWithTag("registrazione-lista").fetchSemanticsNode().boundsInRoot
            val pannello = onNodeWithTag("voci-pannello").fetchSemanticsNode().boundsInRoot
            assertTrue(
                pannello.top >= trascritto.bottom,
                "atteso il pannello Voci sotto il trascritto (stacked, <1100dp): " +
                    "pannello.top=${pannello.top} trascritto.bottom=${trascritto.bottom}",
            )
        }

    @Test
    fun `AC-582 la puntina della frase confermata resta visibile anche quando il chi e nascosto`() {
        val base = stato(pannello(listOf(CARTA_ATTRIBUITA)))
        val consecutivi = base.copy(
            segmenti = listOf(
                riga(1, 1, "Marco", "Prima frase di Marco."),
                riga(2, 1, "Marco", "Seconda frase, stessa Voce di seguito.").copy(confermato = true),
            ),
        )
        scena("ac582-pin-consecutivo", consecutivi) {
            // second row: same Voce as the row right above it → who-line hidden (AC-582)...
            assertEquals(0, onAllNodes(hasTestTag("registrazione-voce-2")).fetchSemanticsNodes().size)
            // ...but it is still confirmed, so the pin (AC-528) must not silently disappear (HIGH-4).
            onNodeWithTag("registrazione-confermato-2", useUnmergedTree = true).assertIsDisplayed()
        }
    }

    @Test
    fun `AC-582 selezione esposta come Checkbox, non selezionata e selezionata`() =
        scena(
            "ac582-seleziona-checkbox",
            stato(pannello(listOf(CARTA_ATTRIBUITA)), selezione = setOf(SegmentoId(2))),
        ) {
            // Segmento 1: unselected — the timecode itself carries the toggle, off.
            onNodeWithTag("registrazione-seleziona-1", useUnmergedTree = true)
                .assert(isToggleable())
                .assertIsOff()
            // Segmento 2: selected — the 16dp checked box replaces the timecode, on.
            onNodeWithTag("registrazione-seleziona-2", useUnmergedTree = true)
                .assert(isToggleable())
                .assertIsOn()
        }

    @Test
    fun `AC-583 la barra di selezione mostra n segmenti di Voce e i suoi pulsanti`() =
        scena(
            "ac583-barra-testo",
            stato(
                pannello(listOf(CARTA_ATTRIBUITA, CARTA_CANDIDATI)),
                selezione = setOf(SegmentoId(2)),
                barra = BarraSelezione(VoceId(2), "Voce 2", 1, true, null, opzioni(2), abilitata = true),
            ),
        ) {
            onNodeWithTag("registrazione-barra-selezione").assertIsDisplayed()
            onNodeWithText(testoSelezione(1, "Voce 2")).assertIsDisplayed()
            onNodeWithTag("registrazione-riassegna").assertIsEnabled()
            onNodeWithTag("registrazione-dividi").assertIsEnabled()
        }

    @Test
    fun `AC-584 la scheda Voci mostra il titolo con k da identificare e il pulsante Calcola`() =
        scena(
            "ac584-scheda-titolo",
            stato(pannello(CARTE_TRE, somiglianza = somiglianza(FaseSomiglianza.Inattiva))),
        ) {
            // CARTE_TRE: CARTA_CANDIDATI and CARTA_NESSUNA are DaIdentificare → 2.
            onNodeWithText("$TITOLO_PANNELLO_VOCI · 2 da identificare").assertIsDisplayed()
            onNodeWithText(ETICHETTA_CALCOLA).assertIsDisplayed()
        }

    @Test
    fun `AC-585 card con candidato mostra E Nome e il tempo di parola sommato dai Segmenti`() =
        scena("ac585-nome-e-tempo", stato(pannello(listOf(CARTA_CANDIDATI)))) {
            // default `stato()` fixture: Voce 2 has Segmenti 2 and 5, 3500ms each → 7000ms → "0:07".
            onNodeWithTag("voce-2-tempo").assertTextEquals("0:07")
            onAllNodesWithText("Marco", substring = true)[0].assertIsDisplayed()
        }

    @Test
    fun `AC-585 prima registrazione senza candidati mostra Dai un nome e il tempo di parola`() =
        scena(
            "ac585-prima-registrazione",
            stato(
                pannello(
                    listOf(
                        CartaVoce(
                            VoceId(1),
                            "Voce 1",
                            daIdentificare(StatoProposta.Pronta(emptyList(), false), galleriaVuota = true),
                            altreVoci = opzioni(1),
                        ),
                    ),
                ).copy(parlantiAttivi = emptyList()),
            ),
        ) {
            // default `stato()` fixture: Voce 1 has Segmenti 1 and 3, 3500ms each → 7000ms → "0:07".
            onNodeWithTag("voce-1-tempo").assertTextEquals("0:07")
            onNodeWithText(ETICHETTA_DAI_UN_NOME).assertIsDisplayed()
        }

    @Test
    fun `AC-585 il menu Altre azioni offre Cambia sulle card gia con un nome`() =
        scena("ac585-menu-cambia", stato(pannello(listOf(CARTA_ATTRIBUITA)))) {
            onNodeWithTag("voce-1-cambia").assertIsEnabled().performClick()
            onNodeWithText(ETICHETTA_CAMBIA).assertIsDisplayed()
        }

    @Test
    fun `AC-587 l anteprima di riassegnazione mostra una riga per ogni spostamento`() =
        scena(
            "ac587-anteprima-righe",
            stato(
                bloccato(
                    somiglianza(
                        FaseSomiglianza.Anteprima(
                            "Sposterò 3 frasi, 0 incerte restano dove sono",
                            listOf("Voce 2 → Marco: 2", "Voce 3 → Marco: 1"),
                            applicabile = true,
                            inApplicazione = false,
                        ),
                    ),
                ),
            ),
        ) {
            onNodeWithTag("somiglianza-riga-0").assertIsDisplayed()
            onNodeWithText("Voce 2 → Marco: 2").assertIsDisplayed()
            onNodeWithTag("somiglianza-riga-1").assertIsDisplayed()
            onNodeWithText("Voce 3 → Marco: 1").assertIsDisplayed()
        }

    @Test
    fun `AC-588 il banner di sola lettura usa BannerSn con le sue tre righe`() =
        scena(
            "ac588-banner-tre-righe",
            stato(
                pannello(listOf(CARTA_ATTRIBUITA.copy(soloLettura = true))).copy(unioneAbilitata = false),
                soloLettura = true,
                bannerRitrascrizionePannello = MESSAGGIO_RITRASCRIZIONE_PERSA,
            ),
        ) {
            onNodeWithTag("registrazione-banner-ritrascrizione").assertIsDisplayed()
            onNodeWithText(
                MESSAGGIO_RITRASCRIZIONE_IN_CORSO.substringBefore("\n"),
                substring = true,
            ).assertIsDisplayed()
            onNodeWithText(
                MESSAGGIO_RITRASCRIZIONE_IN_CORSO.substringAfter("\n"),
                substring = true,
            ).assertIsDisplayed()
            onNodeWithText(MESSAGGIO_RITRASCRIZIONE_PERSA, substring = true).assertIsDisplayed()
        }

    @Test
    fun `AC-580 intestazione mostra titolo, meta con persone e da identificare, e i suoi pulsanti`() =
        scena("ac580-intestazione", stato(pannello(CARTE_TRE))) {
            onNodeWithTag("registrazione-briciole").assertIsDisplayed()
            onNodeWithText(ETICHETTA_BRICIOLA_REGISTRAZIONI).assertIsDisplayed()
            onNodeWithTag("registrazione-titolo").assertTextEquals("Seduta del 12 marzo")
            // CARTE_TRE: 3 persone, 2 da identificare (CARTA_CANDIDATI + CARTA_NESSUNA).
            onNodeWithText(testoPersone(3, 2), substring = true).assertIsDisplayed()
            onNodeWithTag("registrazione-apri-documento").assertIsEnabled()
            onNode(hasContentDescription(ETICHETTA_MOSTRA_CARTELLA)).assertIsDisplayed()
        }
}
