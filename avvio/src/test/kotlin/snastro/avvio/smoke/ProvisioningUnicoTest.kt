package snastro.avvio.smoke

import org.junit.jupiter.api.io.TempDir
import snastro.avvio.modelli.ServizioModelliProvisioning
import snastro.avvio.progetto.SondaCostruzioni
import snastro.avvio.sintesi.DisponibilitaModelloLinguisticoAvvio
import java.nio.file.Path
import kotlin.jvm.internal.CallableReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * AC-C75 (pre-release L56/L143): ONE `ProvisioningModelli` per app. Under `--smoke` ([grafoSmoke], the very graph
 * `eseguiSmoke` builds) the swapped models service and `DisponibilitaModelloLinguisticoAvvio` read the SAME instance —
 * observed on the constructions the graph runs ([SondaCostruzioni]) and on the bound references they received.
 */
class ProvisioningUnicoTest {
    @TempDir
    lateinit var radice: Path

    @Test
    fun `AC-C75 sotto smoke servizio modelli e DisponibilitaModelloLinguisticoAvvio usano la STESSA Provisioning`() {
        val (grafo, costruzioni) = SondaCostruzioni.durante {
            grafoSmoke(radice.resolve("registro"), radice.resolve("modelli"))
        }

        val provisioning = costruzioni.istanze("snastro.modelli.ProvisioningModelli").single()
        val servizio = costruzioni.di { n -> n == ServizioModelliProvisioning::class.java.name }.single()
        val disponibilita = costruzioni.di { n -> n == DisponibilitaModelloLinguisticoAvvio::class.java.name }.single()
        val daServizio = servizio.argomenti.filterIsInstance<CallableReference>().map { r -> r.boundReceiver }
        val daDisponibilita = disponibilita.argomenti.filterIsInstance<CallableReference>().map { r -> r.boundReceiver }

        assertEquals(PROVISIONING_DEL_SERVIZIO, daServizio.size)
        assertTrue(daServizio.all { r -> r === provisioning }, "il servizio modelli legge la ProvisioningModelli unica")
        assertSame(provisioning, daDisponibilita.single(), "Disponibilita legge la STESSA ProvisioningModelli")
        assertSame(grafo.servizioModelli, servizio.istanza, "S5 e la scheda Riassunto: il servizio scambiato")
        assertSame(grafo.sessione.app.disponibilita, disponibilita.istanza, "la Disponibilita dei progetti aperti")
    }

    private companion object {
        /** `ServizioModelliProvisioning.di`: pronti, mancanti, scarica, installata, scarica(id). */
        const val PROVISIONING_DEL_SERVIZIO = 5
    }
}
