package snastro.ui.modelli

import org.junit.jupiter.api.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Consumer-driven contract of [ServizioModelli] (`tec-modelli-ui`, in-process): the fake below proves
 * it green on its own (D1); `:avvio`'s real implementation over `ProvisioningModelli` (block
 * `avvio-composizione`, AC-329) subclasses this too (D2), seeding its own scenario the way
 * `:modelli`'s `ProvisioningModelliTest`/`ArchivioDiProva` already seed a cache dir + a local test
 * server. Only pins what every implementation must guarantee regardless of how it is backed: [stato]
 * and [ServizioModelli.licenze] agree with whether the catalogue is fully installed ([pronti]), and a
 * [ServizioModelli.scarica] on an already-[pronti] service is a safe no-op — the real-time,
 * per-model progress of an actual download (AC-228) is TIMED and NETWORKED: exercised at the
 * presenter level against the fake's own test-only `emetti` ([ModelliPresenterTest]), not here.
 */
abstract class ServizioModelliContratto {
    protected abstract fun con(pronti: Boolean): ServizioModelli

    @Test
    fun `AC-231 AC-232 pronti riflette Pronti in stato e licenze non vuote`() {
        val servizio = con(pronti = true)
        assertIs<StatoModelli.Pronti>(servizio.stato.value)
        assertTrue(servizio.licenze().isNotEmpty())
    }

    @Test
    fun `un servizio gia pronti non lancia su scarica e resta Pronti`() {
        val servizio = con(pronti = true)
        servizio.scarica()
        assertIs<StatoModelli.Pronti>(servizio.stato.value)
    }

    @Test
    fun `AC-227 mancanti riflette almeno una voce e una dimensione da scaricare`() {
        val servizio = con(pronti = false)
        val stato = assertIs<StatoModelli.Mancanti>(servizio.stato.value)
        assertTrue(stato.numero > 0)
        assertTrue(stato.totaleByte > 0)
    }
}
