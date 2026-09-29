package snastro.avvio.coda

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.jupiter.api.Timeout
import snastro.kernel.RegistrazioneId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A122 (MED, regression): a source whose [FonteCoda.teste] ignores `esclusi` must never spin
 * [enumeraViaTeste] — nor [CodaCondivisa.istantanea], which calls it as [FonteCoda.tutti]'s default fallback —
 * forever. Without the `!visti.add(prossimo.id)` guard, every case here loops without end; the
 * [Timeout] on a SEPARATE thread is what turns that into a FAILING test instead of a hung JVM (removing
 * the guard on a throwaway copy was proven RED against these exact two cases before this fix landed).
 */
class EnumeraViaTesteTest {
    @Test
    @Timeout(value = 5, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    fun `A122 enumeraViaTeste si ferma anche se teste ignora esclusi e ripete sempre lo stesso id`() {
        val sempreLoStesso = ElementoInCoda("id-1", "reg-1", Instant.EPOCH)

        val risultato = enumeraViaTeste { sempreLoStesso } // ignora esclusi: offre sempre "id-1"

        assertEquals(listOf(sempreLoStesso), risultato)
    }

    @Test
    @Timeout(value = 5, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    fun `A122 enumeraViaTeste si ferma anche se teste ignora esclusi e cicla tra due id`() {
        val ciclo = listOf("A", "B")
        var chiamate = 0

        val risultato = enumeraViaTeste { _ ->
            val id = ciclo[chiamate % ciclo.size] // ignora esclusi: A, B, A, B, ... a oltranza
            chiamate++
            ElementoInCoda(id, "reg-$id", Instant.EPOCH)
        }

        assertEquals(listOf("A", "B"), risultato.map { it.id })
    }

    @Test
    @Timeout(value = 5, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    fun `A122 istantanea si ferma anche se una fonte ignora esclusi nel suo teste (tutti di default)`() {
        val fonteGuasta = FonteCoda(
            tipo = TipoElementoCoda.ELABORAZIONE,
            teste = { ElementoInCoda("id-1", "reg-1", Instant.EPOCH) }, // ignora sempre esclusi
            prossima = { _, _ -> RisultatoTentativo.Nessuno },
            ultimaTentata = { null },
            recupera = {},
            trattenuta = { false },
            // tutti NON sovrascritto: usa il fallback di default enumeraViaTeste — il percorso di produzione che
            // una fonte reale priva di una listing a query singola prenderebbe (CodaCondivisa.kt#tutti).
        )
        val scope = CoroutineScope(SupervisorJob())
        val coda = codaAvviata(scope = scope, fonti = listOf(fonteGuasta))
        scope.cancel() // solo istantanea() e' esercitata: nessun tick necessario (come per D2 di PosizioniNellaCoda)
        coda.fermaEAttendi(1_000)

        val istantanea = coda.istantanea()

        assertEquals(mapOf(RegistrazioneId("reg-1") to 1), istantanea.elaborazioni)
    }
}
