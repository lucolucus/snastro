package snastro.ui.registrazione

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
import snastro.ui.lettore.StatoLettore
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

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
    fun `L573b un guasto di documento non impedisce il caricamento del trascritto`() = runTest {
        val presenter = presentatore(this, documento = { error("guasto simulato di documento") })
        advanceUntilIdle()

        val dati = assertIs<RegistrazioneUiStato.Dati>(presenter.stato.value)
        assertEquals(1, dati.segmenti.size)
        assertNull(dati.documentoPercorso)
    }

    @Test
    fun `L573b un guasto di lettore disponibile degrada ad audio non disponibile, non a Errore`() = runTest {
        val lettore = object : LettoreAudio {
            private val _stato = MutableStateFlow(StatoLettore(null, 0, false))
            override val stato: StateFlow<StatoLettore> = _stato.asStateFlow()

            override fun disponibile(id: RegistrazioneId): Boolean = error("guasto simulato di disponibile")

            override fun riproduciDa(id: RegistrazioneId, daMs: Long) = Unit

            override fun riproduciEstratto(e: EstrattoRef): Nothing = error("non usato in questo test")

            override fun pausa() = Unit
        }
        val presenter = presentatore(this, lettore = lettore)
        advanceUntilIdle()

        val dati = assertIs<RegistrazioneUiStato.Dati>(presenter.stato.value)
        assertEquals(false, dati.audioDisponibile)
        assertEquals(1, dati.segmenti.size)
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
