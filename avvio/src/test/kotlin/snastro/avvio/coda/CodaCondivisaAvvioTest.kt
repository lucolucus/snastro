package snastro.avvio.coda

import kotlinx.coroutines.cancel
import snastro.supporto.test.conScopeDiProva
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ADR 0030 §1 steps 4–6 (AC-C70/AC-C71) at the queue's own seam: building a [CodaCondivisa] launches nothing,
 * [CodaCondivisa.recupera] runs every source's recovery on the caller's thread (step 5) and [CodaCondivisa.avvia]
 * then claims without running it again; a ring of the [Campanello] handed to it wakes the queue.
 */
class CodaCondivisaAvvioTest {
    @Test
    fun `AC-C71 costruita non lancia nulla, recupera gira ogni fonte, avvia non la ripete prima del reclamo`(): Unit =
        conScopeDiProva { scope ->
            val ordine = CopyOnWriteArrayList<String>()
            val reclamato = CountDownLatch(1)
            val fonte = { tipo: TipoElementoCoda, id: String ->
                FonteCoda(
                    tipo = tipo,
                    teste = { esclusi ->
                        val preso = id in esclusi || "$id reclamato" in ordine
                        if (preso) null else ElementoInCoda(id, "r-$id", Instant.EPOCH)
                    },
                    prossima = { _, _ ->
                        ordine += "$id reclamato"
                        reclamato.countDown()
                        RisultatoTentativo.Avviata(id)
                    },
                    ultimaTentata = { null },
                    recupera = { ordine += "recupera $id su ${Thread.currentThread().name}" },
                    trattenuta = { false },
                )
            }
            val coda =
                CodaCondivisa(listOf(fonte(TipoElementoCoda.ELABORAZIONE, "e"), fonte(TipoElementoCoda.RIASSUNTO, "r")))
            assertEquals(emptyList(), ordine.toList(), "costruita: nulla gira")

            coda.recupera()
            val qui = Thread.currentThread().name
            assertEquals(listOf("recupera e su $qui", "recupera r su $qui"), ordine.toList())

            coda.avvia(scope)
            assertTrue(reclamato.await(ATTESA_S, TimeUnit.SECONDS), "il primo reclamo arriva")
            assertEquals(2, ordine.count { it.startsWith("recupera") }, "avvia non ripete il recupero: $ordine")
            scope.cancel()
            coda.fermaEAttendi(ATTESA_S * MS_PER_S)
        }

    @Test
    fun `AC-C70 un suono del Campanello consegnato alla coda la sveglia`(): Unit = conScopeDiProva { scope ->
        val campanello = Campanello()
        val testa = CopyOnWriteArrayList<ElementoInCoda>()
        val eseguito = CountDownLatch(1)
        val fonte = FonteCoda(
            tipo = TipoElementoCoda.RIASSUNTO,
            teste = { esclusi -> testa.firstOrNull { it.id !in esclusi } },
            prossima = { _, _ ->
                val e = testa.removeFirstOrNull()
                if (e != null) eseguito.countDown()
                e?.let { RisultatoTentativo.Avviata(it.id) } ?: RisultatoTentativo.Nessuno
            },
            ultimaTentata = { null },
            recupera = {},
            trattenuta = { false },
        )
        val coda = CodaCondivisa(listOf(fonte), campanello)
        coda.avvia(scope)

        testa += ElementoInCoda("r1", "reg-1", Instant.EPOCH) // a Riassunto enqueued by its module…
        campanello.suona() // …which rings the SAME handle the queue was built with

        assertTrue(eseguito.await(ATTESA_S, TimeUnit.SECONDS), "la coda si sveglia ed esegue il Riassunto")
        scope.cancel()
        coda.fermaEAttendi(ATTESA_S * MS_PER_S)
    }

    private companion object {
        const val ATTESA_S = 10L
        const val MS_PER_S = 1_000L
    }
}
