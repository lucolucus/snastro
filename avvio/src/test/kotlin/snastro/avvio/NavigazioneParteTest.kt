package snastro.avvio

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import snastro.avvio.progetto.AmbienteProgetto
import snastro.avvio.progetto.CollaboratoriProgetto
import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.kernel.atteso
import snastro.progetto.applicazione.comandi.Destinazione
import snastro.progetto.applicazione.comandi.EliminaRegistrazione
import snastro.supporto.test.attendiFinche
import snastro.supporto.test.pausaInTempoReale
import snastro.ui.lettore.LettoreAudio
import snastro.ui.lettore.LettoreAudioFinta
import snastro.ui.lettore.StatoLettore
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * AC-I90 on the BUILT graph ([AmbienteProgetto] under the app's own [ContenutoApp]): an Incontro of three transcribed
 * Parti; S3 of one Parte with the Parte switcher, the page of another Parte after one is deleted, and the Fonte chip
 * of another Parte.
 */
@OptIn(ExperimentalTestApi::class)
class NavigazioneParteTest {
    @TempDir
    lateinit var radice: Path

    @Test
    fun `AC-I90 il selettore apre la Parte giusta, eliminare un altra Parte ricarica, la aperta torna a S2`() =
        runDesktopComposeUiTest {
            AmbienteProgetto(radice).use { ambiente ->
                val (incontro, parti) = incontroDiTreParti(ambiente)
                val (p1, p2, p3) = parti
                setContent { ContenutoApp(ambiente.grafo(), sceltaCartella = { null }) }

                attendi("la riga dell'Incontro") { esiste("registrazioni-incontro-chevron-${incontro.valore}") }
                onNodeWithTag("registrazioni-incontro-chevron-${incontro.valore}").performClick()
                apriRiga(p1)
                meta("Parte 1 di 3")

                // The switcher: 'Parte 2' opens S3 of the second Parte (and that page is p2's own).
                onNodeWithTag("parte-1").performClick()
                meta("Parte 2 di 3")
                onNodeWithTag("parte-0").performClick()
                meta("Parte 1 di 3")
                onNodeWithTag("parte-1").performClick()
                meta("Parte 2 di 3")

                // Deleting ANOTHER Parte (never transcribed: no Trascrizione event could refresh the open page):
                // the open page (Parte 2) reloads and renumbers its Incontro.
                ambiente.collaboratori.eliminaRegistrazione(EliminaRegistrazione(p3)).atteso()
                meta("Parte 2 di 2")

                // Deleting the OPEN Parte: the app returns to S2.
                ambiente.collaboratori.eliminaRegistrazione(EliminaRegistrazione(p2)).atteso()
                attendi("S2 dopo l'eliminazione della Parte aperta") {
                    esiste("registrazioni-lista") && !esiste("registrazione-parti")
                }
                assertEquals(listOf(p1), ambiente.collaboratori.incontri().single().parti.map { it.registrazioneId })
            }
        }

    @Test
    fun `AC-I90 il chip di un altra Parte apre la pagina di quella Parte, il chip della Parte aperta no`() {
        AmbienteProgetto(radice).use { ambiente ->
            val (_, parti) = incontroDiTreParti(ambiente)
            val (p1, _, p3) = parti
            val aperte = mutableListOf<RegistrazioneId>()
            val scope = ambiente.parlanti.scopeSchermata(ambiente.collaboratori.scope)
            // The chip plays from the Parte's audio first: a finta player (the real one decodes through FFmpeg).
            val lettore = LettoreAudioFinta()
            val presenter = costruisciRiassuntoPresenter(
                ambiente.grafo(),
                conLettore(ambiente.collaboratori, lettore),
                p1,
                scope,
            ) { aperte += it }

            runBlocking(ambiente.dispatcherUi) { presenter.azioni.apriFonte(p1, 0L) }
            pausaInTempoReale(ATTESA_NESSUNA_APERTURA, motivo = "il chip della stessa Parte non apre nulla")
            assertEquals(emptyList(), aperte)
            // RELEASE CHECK I2 (L193): a "parte 3 · 12:30" chip on Parte 1's Riassunto plays Parte 3 from 12:30 and
            // opens Parte 3's page (the Riassunto tab stays: ONE selection per window, AC-S121 in ContenutoAppTest).
            runBlocking(ambiente.dispatcherUi) { presenter.azioni.apriFonte(p3, MINUTO_12_30_MS) }

            attendiFinche(timeout = 10.seconds, messaggio = "la pagina della Parte 3") { aperte.isNotEmpty() }
            assertEquals(listOf(p3), aperte)
            assertEquals(StatoLettore(p3, MINUTO_12_30_MS, inRiproduzione = true), lettore.stato.value)
        }
    }

    private fun conLettore(c: CollaboratoriProgetto, lettore: LettoreAudio) = CollaboratoriProgetto(
        progettoId = c.progettoId,
        registrazioni = c.registrazioni,
        incontri = c.incontri,
        aggiungiRegistrazione = c.aggiungiRegistrazione,
        modificaDataRegistrazione = c.modificaDataRegistrazione,
        modificaOraDiInizio = c.modificaOraDiInizio,
        rinominaRegistrazione = c.rinominaRegistrazione,
        eliminaRegistrazione = c.eliminaRegistrazione,
        lettoreAudio = lettore,
        pulizia = c.pulizia,
        trascrizione = c.trascrizione,
        parlanti = c.parlanti,
        sintesi = c.sintesi,
        sbobinatura = c.sbobinatura,
        avviaElaborazione = c.avviaElaborazione,
        posizioniNellaCoda = c.posizioniNellaCoda,
        aggiornamentiVista = c.aggiornamentiVista,
        scope = c.scope,
    )

    /** Three Parti of one Incontro, in the Incontro's own order: the first two transcribed, the third not. */
    private fun incontroDiTreParti(ambiente: AmbienteProgetto): Pair<IncontroId, List<RegistrazioneId>> {
        val prima = ambiente.importa()
        val incontro = ambiente.incontroDi(prima)
        repeat(2) { ambiente.importaIn(Destinazione.Incontro(incontro)).atteso() }
        val parti = ambiente.collaboratori.incontri().single().parti.map { it.registrazioneId }
        parti.forEach { ambiente.rendiLeggibile(it) }
        parti.take(2).forEach { ambiente.trascrivi(it) }
        return incontro to parti
    }

    private fun ComposeUiTest.apriRiga(id: RegistrazioneId) {
        attendi("riga di ${id.valore}") { esiste("registrazioni-riga-${id.valore}") }
        onNodeWithTag("registrazioni-riga-${id.valore}").performSemanticsAction(SemanticsActions.OnClick)
        attendi("S3 di ${id.valore}") { esiste("registrazione-parti") }
    }

    private fun ComposeUiTest.meta(testo: String) {
        attendi("la pagina '$testo'") {
            esiste("registrazione-meta") && runCatching {
                onNodeWithTag("registrazione-meta", useUnmergedTree = true).assertTextContains(testo, substring = true)
            }.isSuccess
        }
    }

    private fun ComposeUiTest.esiste(tag: String): Boolean =
        onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.attendi(messaggio: String, condizione: () -> Boolean) =
        attendiFinche(timeout = 10.seconds, messaggio = messaggio) {
            waitForIdle()
            condizione()
        }

    private companion object {
        val ATTESA_NESSUNA_APERTURA = 300.milliseconds
        const val MINUTO_12_30_MS = 750_000L
    }
}
