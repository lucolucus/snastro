package snastro.ui.registrazione

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import snastro.kernel.EstrattoRef
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.trascrizione.applicazione.letture.SegmentoTrascrittoView
import snastro.trascrizione.applicazione.letture.TrascrittoView
import snastro.trascrizione.applicazione.letture.VoceTrascrittoView
import snastro.ui.ApriEsterno
import snastro.ui.ApriEsternoFinta
import snastro.ui.lettore.LettoreAudio
import snastro.ui.lettore.LettoreAudioFinta
import snastro.ui.lettore.LettoreUiStato
import snastro.ui.lettore.StatoLettore
import java.time.LocalDate
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val REG_1 = RegistrazioneId("id-1")
private val DATA_1: LocalDate = LocalDate.of(2026, 3, 12)

private fun unaVista(segmenti: List<SegmentoTrascrittoView>) = TrascrittoView(
    registrazioneId = REG_1,
    titolo = "Seduta del 12 marzo",
    dataRegistrazione = DATA_1,
    durataMs = 10_000,
    segmenti = segmenti,
    voci = segmenti.map { it.voceId }.distinct().map { VoceTrascrittoView(it, "Voce ${it.numero}") },
)

private fun unSegmento(id: SegmentoId, inizioMs: Long, fineMs: Long) =
    SegmentoTrascrittoView(id, VoceId(1), inizioMs, fineMs, "Segmento ${id.numero}")

/** A [LettoreAudio] that only RECORDS every `riproduciDa` call it actually receives (RC-9) — the point of
 * L573a is that a superseded click's call must never reach the port AT ALL, not merely "be ignored". */
private class LettoreAudioRegistraChiamate : LettoreAudio {
    private val _stato = MutableStateFlow(StatoLettore(null, 0, false))
    override val stato: StateFlow<StatoLettore> = _stato.asStateFlow()
    val chiamateRiproduciDa: MutableList<Long> = mutableListOf()

    override fun disponibile(id: RegistrazioneId): Boolean = true

    override fun riproduciDa(id: RegistrazioneId, daMs: Long) {
        chiamateRiproduciDa += daMs
        _stato.value = StatoLettore(id, daMs, inRiproduzione = true)
    }

    override fun riproduciEstratto(e: EstrattoRef): Nothing = error("non usato in questo test")

    override fun pausa() = Unit
}

/**
 * Pre-release fix batch B3: [RegistrazionePresenter] items untestable from the existing
 * `RegistrazionePresenterTest` (owned by another batch) — a NEW file, per the composer's file split.
 * L573a/L665a are the two "write the failing test first" delicate parts; L573a's test below is exactly
 * that (it fails red against the pre-fix `avvia`, green once `avviaLettore` supersedes it).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RegistrazionePresenterPreRilascioTest {
    @Suppress("LongParameterList")
    private fun presentatore(
        scope: TestScope,
        trascritto: () -> TrascrittoView? = { unaVista(listOf(unSegmento(SegmentoId(1), 0, 1_000))) },
        documento: () -> String? = { null },
        lettore: LettoreAudio = LettoreAudioFinta(),
        apriEsterno: ApriEsterno = ApriEsternoFinta(),
    ): RegistrazionePresenter {
        val dispatcher = StandardTestDispatcher(scope.testScheduler)
        return RegistrazionePresenter(
            CoroutineScope(dispatcher),
            dispatcher,
            REG_1,
            trascritto,
            documento,
            lettore,
            apriEsterno,
        )
    }

    @Test
    fun `L573a due click veloci su Segmenti diversi eseguono solo l ultimo, mai il primo in ritardo`() = runTest {
        val fake = LettoreAudioRegistraChiamate()
        val presenter = presentatore(
            this,
            lettore = fake,
            trascritto = {
                unaVista(
                    listOf(
                        unSegmento(SegmentoId(1), inizioMs = 0, fineMs = 1_000),
                        unSegmento(SegmentoId(2), inizioMs = 4_000, fineMs = 5_000),
                    ),
                )
            },
        )
        advanceUntilIdle()

        // Two quick clicks, back to back — NEITHER command's coroutine has run yet at this point.
        presenter.azioni.riproduciSegmento(SegmentoId(1))
        presenter.azioni.riproduciSegmento(SegmentoId(2))
        advanceUntilIdle()

        // L573a: the FIRST click is cancelled before its own `riproduciDa` ever runs — a real port call
        // already changes what is audible, so "its result is ignored" would not be enough.
        assertEquals(listOf(4_000L), fake.chiamateRiproduciDa)
        assertEquals(StatoLettore(REG_1, 4_000, inRiproduzione = true), fake.stato.value)
    }

    @Test
    fun `L573a HIGH un play lento su un dispatcher reale non perde una pausa arrivata subito dopo`() {
        val eseguitori = Executors.newFixedThreadPool(4)
        val ioReale = eseguitori.asCoroutineDispatcher()
        try {
            val entrataPlay = CountDownLatch(1)
            val viaLiberaPlay = CountDownLatch(1)
            val chiamate = Collections.synchronizedList(mutableListOf<String>())
            val fake = object : LettoreAudio {
                private val _stato = MutableStateFlow(StatoLettore(null, 0, false))
                override val stato: StateFlow<StatoLettore> = _stato.asStateFlow()

                override fun disponibile(id: RegistrazioneId) = true

                override fun riproduciDa(id: RegistrazioneId, daMs: Long) {
                    chiamate += "play"
                    entrataPlay.countDown()
                    // Simulates a real, SLOW port call (ffmpeg-backed decode, AC-241) — on the real,
                    // multi-threaded `io` this presenter used to be given directly, `pausa`'s own call
                    // could run CONCURRENTLY with this one, or even finish FIRST. The single lane
                    // (rework cycle 1, HIGH), not cancellation, is what must keep them in click order.
                    assertTrue(viaLiberaPlay.await(5, TimeUnit.SECONDS), "timeout in attesa del via libera")
                    _stato.value = StatoLettore(id, daMs, inRiproduzione = true)
                }

                override fun riproduciEstratto(e: EstrattoRef): Nothing = error("non usato in questo test")

                override fun pausa() {
                    chiamate += "pausa"
                    _stato.update { it.copy(inRiproduzione = false) }
                }
            }
            val scope = CoroutineScope(SupervisorJob() + ioReale)
            val presenter = RegistrazionePresenter(
                scope,
                ioReale,
                REG_1,
                trascritto = { unaVista(listOf(unSegmento(SegmentoId(1), 0, 1_000))) },
                documento = { null },
                lettore = fake,
                apriEsterno = ApriEsternoFinta(),
            )
            attendi { presenter.stato.value is RegistrazioneUiStato.Dati }

            presenter.azioni.riproduciSegmento(SegmentoId(1))
            assertTrue(entrataPlay.await(5, TimeUnit.SECONDS), "il play non e entrato in riproduciDa")

            // `pausa` arrives WHILE the play call is still blocked inside `riproduciDa`, on a real
            // thread pool.
            presenter.azioni.pausa()
            viaLiberaPlay.countDown()

            attendi { chiamate == listOf("play", "pausa") }
            // HIGH: the final state must be paused — a lost pause (play winning late, or the two
            // racing) would leave `inRiproduzione = true` here.
            attendi {
                val barra = (presenter.stato.value as? RegistrazioneUiStato.Dati)?.barra
                barra is LettoreUiStato.Pronto && !barra.inRiproduzione
            }
            assertEquals(listOf("play", "pausa"), chiamate)
        } finally {
            eseguitori.shutdownNow()
        }
    }

    /** Polls (real wall-clock, no virtual time here — a real dispatcher backs this test). */
    private fun attendi(timeoutMs: Long = 5_000, condizione: () -> Boolean) {
        val scadenza = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (System.nanoTime() < scadenza) {
            if (condizione()) return
            Thread.sleep(10)
        }
        error("timeout in attesa della condizione")
    }

    @Test
    fun `L573b un guasto di documento non impedisce il caricamento del trascritto`() = runTest {
        val presenter = presentatore(this, documento = { error("guasto simulato di documento") })
        advanceUntilIdle()

        val dati = assertIs<RegistrazioneUiStato.Dati>(presenter.stato.value)
        assertEquals(1, dati.segmenti.size)
        assertNull(dati.documentoPercorso)
    }

    @Test
    fun `L573b un guasto di lettore disponibile e Errore transitoria, non NonDisponibile, e resta riprovabile`() =
        runTest {
            val lettore = object : LettoreAudio {
                private val _stato = MutableStateFlow(StatoLettore(null, 0, false))
                override val stato: StateFlow<StatoLettore> = _stato.asStateFlow()

                override fun disponibile(id: RegistrazioneId): Boolean = error("guasto simulato di disponibile")

                override fun riproduciDa(id: RegistrazioneId, daMs: Long) {
                    _stato.value = StatoLettore(id, daMs, inRiproduzione = true)
                }

                override fun riproduciEstratto(e: EstrattoRef): Nothing = error("non usato in questo test")

                override fun pausa() = Unit
            }
            val presenter = presentatore(this, lettore = lettore)
            advanceUntilIdle()

            // (rework cycle 1, MED): a THROWN disponibile() says nothing about the source itself — it
            // must not read as "confirmed unavailable" (NonDisponibile disables retry). Errore keeps the
            // control enabled, and `audioDisponibile` stays `true` so a click actually reaches the port.
            val dati = assertIs<RegistrazioneUiStato.Dati>(presenter.stato.value)
            assertEquals(true, dati.audioDisponibile)
            assertIs<LettoreUiStato.Errore>(dati.barra)
            assertEquals(1, dati.segmenti.size)

            // The retry: since audioDisponibile is true, the click is not swallowed at the guard.
            presenter.azioni.riproduciSegmento(SegmentoId(1))
            advanceUntilIdle()
            assertEquals(StatoLettore(REG_1, 0, inRiproduzione = true), lettore.stato.value)
            // A real tick for THIS Registrazione settles the question — the bar now shows it plainly.
            val dopo = assertIs<RegistrazioneUiStato.Dati>(presenter.stato.value)
            assertIs<LettoreUiStato.Pronto>(dopo.barra)
        }

    @Test
    fun `L573d l evidenziazione resta sulla posizione anche dopo la pausa`() = runTest {
        val lettore = LettoreAudioFinta()
        val presenter = presentatore(this, lettore = lettore)
        advanceUntilIdle()

        lettore.emetti(StatoLettore(REG_1, 500, inRiproduzione = true))
        advanceUntilIdle()
        assertEquals(true, (presenter.stato.value as RegistrazioneUiStato.Dati).segmenti.single().inRiproduzione)

        // Same position, now paused — the highlight must stay (L573d: by position, not by inRiproduzione).
        lettore.emetti(StatoLettore(REG_1, 500, inRiproduzione = false))
        advanceUntilIdle()
        assertEquals(true, (presenter.stato.value as RegistrazioneUiStato.Dati).segmenti.single().inRiproduzione)
    }

    @Test
    fun `L573f riprova mostra subito Caricamento, non l Errore precedente`() = runTest {
        var primaChiamata = true
        val presenter = presentatore(
            this,
            trascritto = {
                if (primaChiamata) {
                    primaChiamata = false
                    null
                } else {
                    unaVista(listOf(unSegmento(SegmentoId(1), 0, 1_000)))
                }
            },
        )
        advanceUntilIdle()
        assertIs<RegistrazioneUiStato.Errore>(presenter.stato.value)

        presenter.azioni.riprova()
        // Before the dispatcher even advances, the retry must already show Caricamento (L573f).
        assertEquals(RegistrazioneUiStato.Caricamento, presenter.stato.value)
        advanceUntilIdle()
        assertIs<RegistrazioneUiStato.Dati>(presenter.stato.value)
    }
}
