package snastro.ui.modelli

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Consumer-driven contract of `tec-modelli-ui-facoltativo` (owned here, ADR 0025, in-process): the
 * fake below proves it green on its own (D1); `:avvio`'s real implementation over
 * `ProvisioningModelli.scarica(id)` (block `modello-facoltativo-avvio`) subclasses this too (D2). Only
 * pins what every implementation must guarantee regardless of how it is backed — AC-S32's "the two
 * flows never mix" and a safe no-op on an already-installed id (mirrors [ServizioModelliContratto]'s
 * "un servizio gia pronti non lancia su scarica"). The real-time, per-entry progress of an actual
 * download (AC-S33) is TIMED/NETWORKED: exercised at the presenter level against the fake's own
 * test-only `emettiFacoltativo` ([snastro.ui.modelli.ModelliPresenterTest]), not here.
 *
 * [con] is seeded by CATALOGUE + FILESYSTEM shape (a declared size per id, which of them are already
 * installed), never by directly poking a [StatoModelloFacoltativo] value: a `ProvisioningModelli`-backed
 * D2 has no such setter (`modello-facoltativo-avvio`, code-review finding on the previous shape —
 * `con(facoltativi)` wasn't reproducible without one).
 */
abstract class ServizioModelliFacoltativoContratto {
    /**
     * A supplier with one optional catalogue entry per [dimensioniByte] (id → declared size),
     * pre-installed iff its id is in [installati].
     */
    protected abstract fun con(
        dimensioniByte: Map<String, Long> = emptyMap(),
        installati: Set<String> = emptySet(),
        alTentativoDiScarico: (String) -> Unit = {},
    ): ServizioModelli

    @Test
    fun `AC-S32 scaricaFacoltativo non altera StatoModelli, che resta un flow separato`() {
        val servizio = con()
        val statoPrima = servizio.stato.value

        servizio.scaricaFacoltativo("id-facoltativo")

        assertEquals(statoPrima, servizio.stato.value)
    }

    // A144: an id the catalogue never declared is NOT the same as "installed" — a D1 marking it
    // Installato while D2/the real adapter fails would give a consumer tested only on D1 a false green.
    @Test
    fun `AC-S32 scaricaFacoltativo di un id sconosciuto al catalogo restituisce Errore, mai Installato`() {
        val servizio = con(dimensioniByte = mapOf("id-facoltativo" to 2_000))

        servizio.scaricaFacoltativo("id-sconosciuto")

        val stato = servizio.statoFacoltativi.value["id-sconosciuto"]
        assertTrue(
            stato is StatoModelloFacoltativo.Errore && stato.errore is ErroreServizioModelli.DownloadFallito,
            "atteso Errore(DownloadFallito), ottenuto $stato",
        )
    }

    @Test
    fun `un id gia Installato non e riscaricato`() {
        val tentativi = mutableListOf<String>()
        val servizio = con(
            dimensioniByte = mapOf("id-facoltativo" to 2_000),
            installati = setOf("id-facoltativo"),
            alTentativoDiScarico = { tentativi += it },
        )

        servizio.scaricaFacoltativo("id-facoltativo")

        assertEquals(StatoModelloFacoltativo.Installato, servizio.statoFacoltativi.value["id-facoltativo"])
        // A145: only the FINAL state was checked before — a wrong implementation that re-runs the
        // download unconditionally (relying on it happening to land back on Installato) would pass that
        // alone; nothing may even be ATTEMPTED for an id already Installato.
        assertEquals(emptyList(), tentativi, "un id gia' Installato non deve tentare un nuovo download")
    }

    @Test
    fun `scaricaFacoltativo di un id non altera lo stato tracciato di un altro id`() {
        val servizio = con(dimensioniByte = mapOf("altro-id" to 1_000, "id-facoltativo" to 2_000))

        servizio.scaricaFacoltativo("id-facoltativo")

        assertEquals(StatoModelloFacoltativo.NonInstallato(1_000), servizio.statoFacoltativi.value["altro-id"])
    }

    @Test
    fun `dopo un download riuscito lo stato resta Installato, un secondo scaricaFacoltativo e un no-op`() {
        val tentativi = mutableListOf<String>()
        val servizio = con(dimensioniByte = mapOf("id-facoltativo" to 2_000), alTentativoDiScarico = { tentativi += it })

        servizio.scaricaFacoltativo("id-facoltativo")
        assertEquals(StatoModelloFacoltativo.Installato, servizio.statoFacoltativi.value["id-facoltativo"])
        assertEquals(listOf("id-facoltativo"), tentativi)

        servizio.scaricaFacoltativo("id-facoltativo") // terminal state: a repeat call changes nothing
        assertEquals(StatoModelloFacoltativo.Installato, servizio.statoFacoltativi.value["id-facoltativo"])
        // A145: the SECOND call must not even attempt a new download, not just "happen to" land Installato.
        assertEquals(listOf("id-facoltativo"), tentativi, "il secondo scaricaFacoltativo non deve ritentare")
    }
}
