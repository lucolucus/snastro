package snastro.avvio.accettazione

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import snastro.avvio.progetto.AmbienteProgetto
import snastro.avvio.progetto.SessioneProgettoImpl
import snastro.avvio.progetto.componentiDiProva
import snastro.kernel.atteso
import snastro.progetto.applicazione.porte.RegistroProgettiFinta
import snastro.supporto.test.attendiFinche
import snastro.trascrizione.applicazione.letture.StatoElaborazioneVista
import snastro.ui.ShellUiStato
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * R0 · elimina il progetto aperto (EX-4, EX-5): the shell's presenter — its header menu's "Elimina progetto" — over
 * the REAL [SessioneProgettoImpl], real project folders and, for EX-5, the real single composition with its shared
 * queue running a transcription.
 *
 * Substituted boundaries: the per-user registry ([RegistroProgettiFinta]), the user's home, ML/audio
 * ([componentiDiProva]; EX-5's [AmbienteProgetto] fakes the same edges, its diarization held by a latch so the
 * transcription stays running), id generator and clock. The meeting's date in EX-5 is the fake probe's, not
 * 2026-10-07: the rule does not read it. The presenters run on [BancoS1]'s dispatcher, shared with `runTest` and
 * advanced explicitly, their scope cancelled in teardown; EX-4's session is built by [conSessione], EX-5's environment
 * is closed when the test ends.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EliminaProgettoApertoTest : BancoS1() {
    @Test
    fun `EX-4 Elimina progetto da skatefest aperto lo chiude, torna alla lista e lo cancella`() =
        conSessione(registro, dispatcher) { sessione ->
            sessione.crea(documenti.toString(), "mangu 2").atteso()
            sessione.chiudi()
            val skatefest = sessione.crea(documenti.toString(), "skatefest").atteso()
            attendiRegistrati(registro, "mangu 2", "skatefest")
            val shell = shell(sessione)

            elimina(shell, "skatefest")

            assertNull(sessione.corrente.value, "il progetto si chiude")
            assertEquals(ShellUiStato.SenzaProgetto(), shell.stato.value, "si torna alla lista")
            assertFalse(Files.exists(Path.of(skatefest.percorso)), "skatefest.snastro non esiste piu")
            assertEquals(listOf("mangu 2"), nomi(lista(sessione)), "S1 riaperta")
        }

    @Test
    fun `EX-5 con una trascrizione in corso Elimina progetto e rifiutato, niente chiuso e niente cancellato`() {
        AmbienteProgetto(casa, nome = "skatefest").use { ambiente ->
            val barriera = CountDownLatch(1).also { ambiente.diarizzatoreScriptato.barriera = it }
            try {
                val registrazione = ambiente.importaEAvvia()
                attendiFinche(timeout = 10.seconds, messaggio = "trascrizione in corso") {
                    ambiente.stato(registrazione) == StatoElaborazioneVista.IN_CORSO
                }
                attendiRegistrati(ambiente.registro, "skatefest")
                val primaDiEliminare = Files.list(Path.of(ambiente.progetto.percorso)).use { it.count() }

                runTest(dispatcher) {
                    val shell = shell(ambiente.sessione)

                    elimina(shell, "skatefest")

                    val stato = assertIs<ShellUiStato.ConProgetto>(shell.stato.value, "niente chiuso")
                    assertEquals(
                        "C'è un'elaborazione in corso: aspetta che finisca prima di eliminare il progetto.",
                        assertNotNull(stato.conferma).errore,
                    )
                }
                assertEquals(ambiente.progetto, ambiente.sessione.corrente.value, "il progetto resta aperto")
                assertEquals(StatoElaborazioneVista.IN_CORSO, ambiente.stato(registrazione))
                assertTrue(Files.isRegularFile(Path.of(ambiente.progetto.percorso, "progetto.db")), "niente cancellato")
                assertEquals(primaDiEliminare, Files.list(Path.of(ambiente.progetto.percorso)).use { it.count() })
                assertEquals(listOf("skatefest"), ambiente.registro.elenco().map { it.nome })
            } finally {
                barriera.countDown()
            }
        }
    }
}
