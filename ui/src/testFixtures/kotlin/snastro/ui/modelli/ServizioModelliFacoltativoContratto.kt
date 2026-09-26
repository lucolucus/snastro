package snastro.ui.modelli

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Consumer-driven contract of `tec-modelli-ui-facoltativo` (owned here, ADR 0025, in-process): the
 * fake below proves it green on its own (D1); `:avvio`'s real implementation over
 * `ProvisioningModelli.scarica(id)` (block `modello-facoltativo-avvio`) subclasses this too (D2). Only
 * pins what every implementation must guarantee regardless of how it is backed — AC-S32's "the two
 * flows never mix" and a safe no-op on an already-installed id (mirrors [ServizioModelliContratto]'s
 * "un servizio gia pronti non lancia su scarica"). The real-time, per-entry progress of an actual
 * download (AC-S33) is TIMED/NETWORKED: exercised at the presenter level against the fake's own
 * test-only `emettiFacoltativo` ([snastro.ui.modelli.ModelliPresenterTest]), not here.
 */
abstract class ServizioModelliFacoltativoContratto {
    protected abstract fun con(facoltativi: Map<String, StatoModelloFacoltativo> = emptyMap()): ServizioModelli

    @Test
    fun `AC-S32 scaricaFacoltativo non altera StatoModelli, che resta un flow separato`() {
        val servizio = con()
        val statoPrima = servizio.stato.value

        servizio.scaricaFacoltativo("id-facoltativo")

        assertEquals(statoPrima, servizio.stato.value)
    }

    @Test
    fun `un id gia Installato non e riscaricato`() {
        val servizio = con(mapOf("id-facoltativo" to StatoModelloFacoltativo.Installato))

        servizio.scaricaFacoltativo("id-facoltativo")

        assertEquals(StatoModelloFacoltativo.Installato, servizio.statoFacoltativi.value["id-facoltativo"])
    }

    @Test
    fun `scaricaFacoltativo di un id non altera lo stato tracciato di un altro id`() {
        val altro = StatoModelloFacoltativo.NonInstallato(dimensioneByte = 1_000)
        val servizio = con(mapOf("altro-id" to altro))

        servizio.scaricaFacoltativo("id-facoltativo")

        assertEquals(altro, servizio.statoFacoltativi.value["altro-id"])
    }
}
