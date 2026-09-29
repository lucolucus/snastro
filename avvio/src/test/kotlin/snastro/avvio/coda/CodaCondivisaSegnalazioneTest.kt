package snastro.avvio.coda

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import snastro.supporto.test.conScopeDiProva
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [CodaCondivisa]'s `segnalaSfuggito` hook and the [StackOverflowError] carve-out (ADR 0028 §7.5,
 * AC-C57/AC-C58) — split out of [CodaCondivisaTest] (`LargeClass`), same green-on-its-own shape: every
 * scenario is scripted lambdas, no fake port/repository (`avvio/src/main` never imports a context's own
 * types).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CodaCondivisaSegnalazioneTest {
    @Test
    fun `AC-C57 un RuntimeException e segnalato una volta via segnalaSfuggito, la coda prosegue con il successivo`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val segnalati = mutableListOf<Throwable>()
            val completate = mutableListOf<String>()
            val primaVolta = AtomicBoolean(true)

            codaAvviata(
                scope = backgroundScope,
                fonti = listOf(
                    FonteCoda(
                        tipo = TipoElementoCoda.ELABORAZIONE,
                        teste = { if (completate.isEmpty()) ElementoInCoda("id-1", "reg-1", Instant.EPOCH) else null },
                        prossima = { _, _ ->
                            if (primaVolta.compareAndSet(true, false)) lanciaRuntimeExceptionDiProva()
                            completate += "id-1"
                            RisultatoTentativo.Avviata("id-1")
                        },
                        ultimaTentata = { "id-1" },
                        recupera = {},
                        trattenuta = { false },
                    ),
                ),
                segnalaSfuggito = { segnalati += it },
                dispatcherSingoloThread = dispatcher,
            )
            advanceTimeBy(3_000)
            runCurrent()

            assertEquals(1, segnalati.size, "segnalato esattamente una volta")
            assertTrue(segnalati.single() is RuntimeException)
            assertEquals(listOf("id-1"), completate, "la coda prosegue con l'elemento successivo (AC-S61)")
        }

    @Test
    fun `AC-C57 uno StackOverflowError non e ingoiato, e segnalato al gestore, la coda si ferma`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val catturatiDalGestore = mutableListOf<Throwable>()
        val gestoreDiProva = CoroutineExceptionHandler { _, e -> catturatiDalGestore += e }
        val scope = CoroutineScope(SupervisorJob() + dispatcher + gestoreDiProva)
        val chiamate = AtomicInteger(0)

        codaAvviata(
            scope = scope,
            fonti = listOf(
                FonteCoda(
                    tipo = TipoElementoCoda.ELABORAZIONE,
                    teste = { ElementoInCoda("id-1", "reg-1", Instant.EPOCH) },
                    prossima = { _, _ ->
                        chiamate.incrementAndGet()
                        throw StackOverflowError("di prova")
                    },
                    ultimaTentata = { null },
                    recupera = {},
                    trattenuta = { false },
                ),
            ),
            dispatcherSingoloThread = dispatcher,
        )
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()
        scope.cancel()

        assertEquals(1, chiamate.get(), "lo StackOverflowError non e' mai ritentato: la coda si e' fermata")
        assertEquals(1, catturatiDalGestore.size, "segnalato esattamente una volta, attraverso il gestore")
        assertTrue(catturatiDalGestore.single() is StackOverflowError)
    }

    @Test
    @Suppress("MaxLineLength", "MaximumLineLength", "ArgumentListWrapping") // the test name alone crosses 120 columns
    fun `AC-C58 un interrompi che lancia e catturato e segnalato, fermaEAttendi completa comunque`() = conScopeDiProva { scope ->
        val bloccato = CountDownLatch(1)
        val maiSbloccato = CountDownLatch(1)
        val segnalati = mutableListOf<Throwable>()

        val coda = codaAvviata(
            scope = scope,
            fonti = listOf(
                FonteCoda(
                    tipo = TipoElementoCoda.ELABORAZIONE,
                    teste = { ElementoInCoda("id-1", "reg-1", Instant.EPOCH) },
                    prossima = { _, _ ->
                        bloccato.countDown()
                        maiSbloccato.await()
                        error("mai raggiunto")
                    },
                    ultimaTentata = { null },
                    recupera = {},
                    trattenuta = { false },
                    interrompi = { throw IllegalStateException("interrompi guasto") },
                ),
            ),
            segnalaSfuggito = { segnalati += it },
        )
        assertTrue(bloccato.await(10, TimeUnit.SECONDS), "la chiamata bloccante avrebbe dovuto partire")

        scope.cancel()
        val fermato = coda.fermaEAttendi(10_000)

        assertTrue(fermato, "lo shutdown completa comunque (DB chiuso, lock rilasciato), anche se interrompi lancia")
        assertEquals(1, segnalati.size, "l'interrompi guasto e' catturato e segnalato esattamente una volta")
        assertTrue(segnalati.single() is IllegalStateException)
    }

    @Test
    fun `AC-C58 un ultimaTentata che lancia e catturato e segnalato, la coda riprogramma comunque`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val segnalati = mutableListOf<Throwable>()

        codaAvviata(
            scope = backgroundScope,
            fonti = listOf(
                FonteCoda(
                    tipo = TipoElementoCoda.ELABORAZIONE,
                    teste = { ElementoInCoda("id-1", "reg-1", Instant.EPOCH) },
                    prossima = { _, _ -> lanciaRuntimeExceptionDiProva() },
                    ultimaTentata = { throw IllegalStateException("ultimaTentata guasta") },
                    recupera = {},
                    trattenuta = { false },
                ),
            ),
            segnalaSfuggito = { segnalati += it },
            dispatcherSingoloThread = dispatcher,
        )
        advanceTimeBy(3_000)
        runCurrent()

        assertTrue(segnalati.any { it is IllegalStateException }, "ultimaTentata guasta e' catturata e segnalata")
        assertTrue(segnalati.any { it is RuntimeException }, "anche la fuga originale (prossima) resta segnalata")
    }

    @Test
    fun `AC-C58 un segnalaBloccato che lancia e catturato e segnalato, gli altri elementi proseguono`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val segnalati = mutableListOf<Throwable>()
        val tentativi = AtomicInteger(0)
        val completate = mutableListOf<String>()

        codaAvviata(
            scope = backgroundScope,
            fonti = listOf(
                FonteCoda(
                    tipo = TipoElementoCoda.ELABORAZIONE,
                    teste = { esclusi ->
                        when {
                            "vecchia" !in esclusi -> ElementoInCoda("vecchia", "reg-vecchia", Instant.EPOCH)
                            completate.isEmpty() -> ElementoInCoda("recente", "reg-recente", Instant.EPOCH)
                            else -> null
                        }
                    },
                    prossima = { esclusi, _ ->
                        when {
                            "vecchia" !in esclusi -> {
                                tentativi.incrementAndGet()
                                RisultatoTentativo.Rifiutata("vecchia")
                            }
                            completate.isEmpty() -> {
                                completate += "recente"
                                RisultatoTentativo.Avviata("recente")
                            }
                            else -> RisultatoTentativo.Nessuno
                        }
                    },
                    ultimaTentata = { null },
                    recupera = {},
                    trattenuta = { false },
                ),
            ),
            segnalaBloccato = { throw IllegalStateException("segnalaBloccato guasto") },
            segnalaSfuggito = { segnalati += it },
            dispatcherSingoloThread = dispatcher,
        )
        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(3, tentativi.get(), "esclusa dopo 3 tentativi, come AC-313")
        assertTrue(segnalati.any { it is IllegalStateException }, "il segnalaBloccato guasto e' catturato e segnalato")
        assertEquals(listOf("recente"), completate, "gli altri elementi proseguono nonostante il guasto")
    }

    /** AC-C57 pins the exact type `RuntimeException` (not a subtype) — the one sanctioned throw of it, here. */
    @Suppress("TooGenericExceptionThrown")
    private fun lanciaRuntimeExceptionDiProva(): Nothing = throw RuntimeException("di prova")
}
