package snastro.ui.registrazioni

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import snastro.kernel.RegistrazioneId
import snastro.progetto.applicazione.letture.RegistrazioneDelProgettoVista
import snastro.ui.AggiornamentiVistaFinta
import snastro.ui.Cambiamento
import snastro.ui.lettore.LettoreAudio
import snastro.ui.lettore.LettoreAudioFinta
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val REG_1 = RegistrazioneId("id-1")
private val ORA_FISSA: Instant = Instant.parse("2026-09-23T10:00:00Z")

private fun rigaVista(id: RegistrazioneId, titolo: String = "Seduta") =
    RegistrazioneDelProgettoVista(id, titolo, LocalDate.of(2026, 3, 12), durataMs = 60_000)

/**
 * [RegistrazioniPresenter] races that need REAL concurrency to reproduce deterministically (split out
 * of [RegistrazioniPresenterTest] — CR-9/detekt `LargeClass`): L478a (a refresh in flight must not
 * revert a player-state change that lands mid-flight) and L485b (an older success must not be hidden by
 * a newer load that already failed). Real threads, same technique as `ShellPresenterTest`'s
 * `fix-batch-16` test: only real threads let a test control which of two independently-scheduled
 * reactions a shared single-threaded executor runs first (`runTest`'s virtual dispatcher does not
 * redispatch — hence does not interleave — when `io` and `scope` share one `TestDispatcher` instance).
 */
class RegistrazioniPresenterConcorrenzaTest {
    // --- L478a: a refresh in flight must never revert a player-state change that lands mid-flight ----

    /** L478a: blocks the SECOND call (the refresh triggered below, never the initial load) right where
     * `costruisciRighe` calls `disponibile` — strictly AFTER it already snapshotted `lettore.stato.value`
     * for this refresh — so the test can apply a real pause and let it reach `rifletti` BEFORE releasing
     * this refresh to merge its own (by-then-stale) snapshot. */
    private class LettoreAudioCheAttendeAllaSecondaChiamata(
        private val delegato: LettoreAudioFinta,
        private val ingresso: CountDownLatch,
        private val viaLibera: CountDownLatch,
    ) : LettoreAudio by delegato {
        private val chiamate = AtomicInteger(0)

        override fun disponibile(id: RegistrazioneId): Boolean {
            if (chiamate.incrementAndGet() == 2) {
                ingresso.countDown()
                viaLibera.await(ATTESA_S, TimeUnit.SECONDS)
            }
            return delegato.disponibile(id)
        }
    }

    @Test
    fun `L478a un refresh in volo non annulla una pausa applicata mentre e in costruzione`() {
        val esecutoreUi = Executors.newSingleThreadExecutor()
        val esecutoreIo = Executors.newSingleThreadExecutor()
        val scope = CoroutineScope(SupervisorJob() + esecutoreUi.asCoroutineDispatcher())
        val lettoreReale = LettoreAudioFinta()
        val ingresso = CountDownLatch(1)
        val viaLibera = CountDownLatch(1)
        val lettore = LettoreAudioCheAttendeAllaSecondaChiamata(lettoreReale, ingresso, viaLibera)
        val aggiornamenti = AggiornamentiVistaFinta()
        try {
            val presenter = RegistrazioniPresenter(
                scope = scope,
                io = esecutoreIo.asCoroutineDispatcher(),
                registrazioni = { listOf(rigaVista(REG_1)) },
                aggiungiRegistrazione = { error("non atteso in questo test") },
                modificaDataRegistrazione = { error("non atteso in questo test") },
                rinominaRegistrazione = { error("non atteso in questo test") },
                lettore = lettore,
                aggiornamenti = aggiornamenti,
                clock = Clock.fixed(ORA_FISSA, ZoneOffset.UTC),
            )
            attendiFinche { presenter.stato.value is RegistrazioniUiStato.Dati } // initial load, chiamata #1

            lettoreReale.riproduciDa(REG_1, 0)
            attendiFinche {
                (presenter.stato.value as? RegistrazioniUiStato.Dati)
                    ?.righe?.singleOrNull()?.riproduzione == StatoRiproduzioneRiga.InRiproduzione
            }

            // R15 background refresh (never `riprova()`, which is the Errore-only retry action and
            // — L485e — would force the state back to `Caricamento`, wiping the row this test needs).
            aggiornamenti.emetti(Cambiamento(REG_1)) // triggers carica() #2 — its disponibile() blocks (chiamata #2)
            assertTrue(ingresso.await(ATTESA_S, TimeUnit.SECONDS))

            // The refresh already snapshotted `lettore.stato.value` as InRiproduzione. Pausing now — and
            // letting `rifletti` (queued on `esecutoreUi` by this very state change) run BEFORE the
            // refresh resumes there — reproduces the exact race: does the refresh's OWN merge, running
            // second, clobber what `rifletti` just corrected? A plain `attendiFinche` on the FINAL value
            // would be fooled by `rifletti` alone momentarily setting it right before the refresh's own
            // (buggy, pre-fix) merge clobbers it again — so this waits out BOTH: a barrier task on
            // `esecutoreIo` only runs once `costruisciRighe` (the whole blocked call) has returned, which
            // is also the point the refresh's continuation is dispatched back to `esecutoreUi`; a second
            // barrier there then only runs once EVERY task queued ahead of it (`rifletti`'s reaction to
            // the pause, then the refresh's own `aggiornaConNuoveRighe`) has actually completed.
            lettoreReale.pausa()
            viaLibera.countDown()
            esecutoreIo.submit(Callable {}).get(ATTESA_S, TimeUnit.SECONDS)
            esecutoreUi.submit(Callable {}).get(ATTESA_S, TimeUnit.SECONDS)

            val riga = assertIs<RegistrazioniUiStato.Dati>(presenter.stato.value).righe.single()
            assertEquals(StatoRiproduzioneRiga.Disponibile, riga.riproduzione)
        } finally {
            viaLibera.countDown()
            scope.cancel()
            esecutoreUi.shutdownNow()
            esecutoreIo.shutdownNow()
        }
    }

    // --- L485b: an older success must not be hidden by a newer load that already failed -------------

    @Test
    fun `L485b un successo piu vecchio si applica anche se un caricamento piu nuovo e gia fallito`() {
        val esecutoreUi = Executors.newSingleThreadExecutor()
        // TWO threads: the (older, blocked) initial load and the (newer, fast-failing) refresh below
        // must run CONCURRENTLY, never serialised behind each other on a single IO thread.
        val esecutoreIo = Executors.newFixedThreadPool(2)
        val scope = CoroutineScope(SupervisorJob() + esecutoreUi.asCoroutineDispatcher())
        val ingresso = CountDownLatch(1)
        val viaLibera = CountDownLatch(1)
        val chiamate = AtomicInteger(0)
        val aggiornamenti = AggiornamentiVistaFinta()
        try {
            val presenter = RegistrazioniPresenter(
                scope = scope,
                io = esecutoreIo.asCoroutineDispatcher(),
                registrazioni = {
                    when (chiamate.incrementAndGet()) {
                        1 -> { // the OLDER call: the presenter's own initial load — blocks until released
                            ingresso.countDown()
                            viaLibera.await(ATTESA_S, TimeUnit.SECONDS)
                            listOf(rigaVista(REG_1, titolo = "Dal caricamento iniziale"))
                        }
                        else -> error("guasto del refresh piu nuovo") // the NEWER refresh: fails right away
                    }
                },
                aggiungiRegistrazione = { error("non atteso in questo test") },
                modificaDataRegistrazione = { error("non atteso in questo test") },
                rinominaRegistrazione = { error("non atteso in questo test") },
                lettore = LettoreAudioFinta(),
                aggiornamenti = aggiornamenti,
                clock = Clock.fixed(ORA_FISSA, ZoneOffset.UTC),
            )
            // `init` already launched the initial load (chiamata #1); it is now blocked.
            assertTrue(ingresso.await(ATTESA_S, TimeUnit.SECONDS))

            // A background refresh (R15), NEWER than the still-in-flight initial load, resolves FIRST
            // (it never blocks) and fails — while the OLDER call has not produced a result yet at all.
            aggiornamenti.emetti(Cambiamento(REG_1)) // chiamata #2
            attendiFinche { presenter.stato.value is RegistrazioniUiStato.Errore }

            viaLibera.countDown() // release the OLDER (initial) load: it now succeeds
            attendiFinche { presenter.stato.value is RegistrazioniUiStato.Dati }

            // L485b: the older success is APPLIED (not dropped just because a newer attempt already
            // started and failed) — the presenter leaves the M5 Errore screen once real data is in.
            val dati = assertIs<RegistrazioniUiStato.Dati>(presenter.stato.value)
            assertEquals(listOf("Dal caricamento iniziale"), dati.righe.map { it.titolo })
            assertNull(dati.erroreAggiornamento) // L485a: the success clears it too
        } finally {
            viaLibera.countDown()
            scope.cancel()
            esecutoreUi.shutdownNow()
            esecutoreIo.shutdownNow()
        }
    }

    private fun attendiFinche(condizione: () -> Boolean) {
        val scadenza = System.currentTimeMillis() + ATTESA_S * MS_PER_S
        while (!condizione()) {
            check(System.currentTimeMillis() < scadenza) { "timeout in attesa dello stato atteso" }
            Thread.sleep(PASSO_MS)
        }
    }

    private companion object {
        const val ATTESA_S = 5L
        const val MS_PER_S = 1_000L
        const val PASSO_MS = 10L
    }
}
