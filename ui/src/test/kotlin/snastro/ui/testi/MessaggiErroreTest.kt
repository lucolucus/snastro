package snastro.ui.testi

import snastro.kernel.ErroreDiProva
import snastro.kernel.ErroreDominio
import snastro.progetto.applicazione.porte.ErroreApplicazioneProgetto
import snastro.ui.ErroreSessione
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * AC-180 (partial — see [snastro.ui.testi] `MessaggiErrore.kt` KDoc and the worker's BOUNCED report):
 * maps one instance of every hierarchy `:ui` can currently import.
 */
class MessaggiErroreTest {
    @Test
    fun `ogni valore di ErroreSessione ha un messaggio`() {
        listOf(
            ErroreSessione.NomeProgettoVuoto,
            ErroreSessione.CartellaNonValida,
            ErroreSessione.ProgettoGiaAperto,
            ErroreSessione.DatabasePiuRecente,
        ).forEach { errore -> assertTrue(messaggioPer(errore).isNotBlank()) }
    }

    @Test
    fun `ogni valore di ErroreApplicazioneProgetto ha un messaggio`() {
        listOf(
            ErroreApplicazioneProgetto.AudioNonLeggibile("x"),
            ErroreApplicazioneProgetto.FormatoNonSupportato("x"),
            ErroreApplicazioneProgetto.CopiaFallita("x"),
        ).forEach { errore -> assertTrue(messaggioPer(errore).isNotBlank()) }
    }

    @Test
    fun `il punto di ingresso instrada ogni gerarchia raggiungibile`() {
        val errore: ErroreDominio = ErroreSessione.CartellaNonValida
        assertTrue(messaggioPer(errore).isNotBlank())
    }

    @Test
    fun `il punto di ingresso rifiuta un ErroreDominio non mappato`() {
        // ErroreDiProva (kernel testFixtures, CR-8 shape) stands in for "a hierarchy not yet wired
        // into the entry-point dispatcher" — RC-4: its only `else` is a programmer error, never a
        // generic user message.
        assertFailsWith<IllegalStateException> { messaggioPer(ErroreDiProva.Fallito("x")) }
    }
}
