package snastro.documento.adattatori.eventi

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import snastro.documento.applicazione.politiche.RigenerazioneDocumentoPolitica
import snastro.documento.applicazione.porte.LettoreNomiFinta
import snastro.documento.applicazione.porte.LettoreTrascrittoFinta
import snastro.documento.applicazione.porte.ScrittoreDocumentoFinta
import snastro.kernel.RegistrazioneId
import snastro.supporto.Segnalazione
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** AC-C67 (ADR 0030 §1): [AbbonatoDocumentoEventi] is a value — constructing it launches nothing until avvia(scope). */
@OptIn(ExperimentalCoroutinesApi::class)
class AbbonatoDocumentoEventiAvvioTest {
    @Test
    fun `AC-C67 costruito non lancia alcun lavoro, lo sweep gira solo dopo avvia`() = runTest {
        val elencate = AtomicInteger()
        val politica = RigenerazioneDocumentoPolitica(
            LettoreTrascrittoFinta(emptyMap()),
            LettoreNomiFinta(),
            ScrittoreDocumentoFinta(),
        )
        val abbonato = AbbonatoDocumentoEventi(
            politica,
            { emptyList<RegistrazioneId>().also { elencate.incrementAndGet() } },
            Segnalazione { _, _ -> },
        )

        runCurrent() // backgroundScope's own tasks: advanceUntilIdle ignores them
        assertEquals(0, elencate.get(), "nessuno sweep prima di avvia")
        assertTrue(backgroundScope.coroutineContext.job.children.none(), "nessun lavoro in corso prima di avvia")

        val lavoro = abbonato.avvia(backgroundScope)
        runCurrent() // backgroundScope's own tasks: advanceUntilIdle ignores them

        assertTrue(lavoro.isActive, "avvia lancia il lavoro nello scope dato")
        assertEquals(1, elencate.get(), "lo sweep richiesto alla costruzione gira una volta, dopo avvia")
    }
}
