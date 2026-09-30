package snastro.avvio

import kotlinx.coroutines.CoroutineScope
import snastro.supporto.test.attendiFinche
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

/**
 * AC-C73 (ADR 0030 §1, ADR 0017 §3): [ArrestoProgetto] over recording fakes — the reverse order of `avvia`, ONE shared
 * deadline however long a `ferma` blocks, `poi` (the database close + lock release) only once every `ferma` returned.
 */
class ArrestoProgettoTest {
    private val registro = CopyOnWriteArrayList<String>()

    /** A fake [Avviabile] recording its `avvia`/`ferma`, whose `ferma` runs [durante] (e.g. blocks). */
    private inner class Registrante(private val nome: String, private val durante: () -> Unit = {}) : Avviabile {
        override fun avvia(scope: CoroutineScope) {
            registro += "avvia $nome"
        }

        override fun ferma() {
            durante()
            registro += "ferma $nome"
        }
    }

    @Test
    fun `AC-C73 ferma la coda e i moduli nell ordine inverso di avvia, poi chiude`() {
        val avviati = listOf("trascrizione", "sbobinatura", "parlanti", "sintesi", "progetto", "coda")
            .map(::Registrante)
        avviati.forEach { it.avvia(CoroutineScope(EmptyCoroutineContext)) }
        registro.clear()

        val inTempo = ArrestoProgetto(SCADENZA).arresta(avviati) { registro += "poi" }

        assertTrue(inTempo)
        assertEquals(
            listOf("coda", "progetto", "sintesi", "parlanti", "sbobinatura", "trascrizione")
                .map { "ferma $it" } + "poi",
            registro.toList(),
        )
    }

    @Test
    fun `AC-C73 un ferma che blocca oltre la scadenza non allunga l arresto, e poi attende la sua fine`() {
        val sblocca = CountDownLatch(1)
        val poiFatto = AtomicBoolean(false)
        val avviati = listOf(
            Registrante("primo"),
            Registrante("bloccato") { sblocca.await(ATTESA_S, TimeUnit.SECONDS) },
            Registrante("ultimo"),
        )
        val scadenza = 300.milliseconds

        val inizio = System.nanoTime()
        val inTempo = ArrestoProgetto(scadenza).arresta(avviati) { poiFatto.set(true) }
        val durata = (System.nanoTime() - inizio).nanoseconds

        assertFalse(inTempo, "la scadenza e passata con un ferma ancora bloccato")
        assertTrue(durata < scadenza + MARGINE, "l'arresto non si allunga oltre la scadenza: $durata")
        assertEquals(listOf("ferma ultimo"), registro.toList(), "in ordine inverso, fino a quello bloccato")
        assertFalse(poiFatto.get(), "mai il database chiuso sotto un lavoro vivo (fix-batch-16 MED-1)")

        sblocca.countDown()
        attendiFinche(messaggio = "poi alla fine dell'ultimo lavoro") { poiFatto.get() }
        assertEquals(listOf("ferma ultimo", "ferma bloccato", "ferma primo"), registro.toList())
    }

    @Test
    fun `un ferma che lancia e segnalato, gli altri si fermano comunque e poi gira`() {
        val poiFatto = AtomicBoolean(false)
        val guasto = Registrante("guasto") { error("ferma guasto") }
        val avviati = listOf(Registrante("primo"), guasto, Registrante("ultimo"))

        val inTempo = ArrestoProgetto(SCADENZA).arresta(avviati) { poiFatto.set(true) }

        assertTrue(inTempo)
        assertEquals(listOf("ferma ultimo", "ferma primo"), registro.toList())
        assertTrue(poiFatto.get())
    }

    private companion object {
        val SCADENZA = 5.seconds
        val MARGINE = 1.seconds
        const val ATTESA_S = 30L
    }
}
