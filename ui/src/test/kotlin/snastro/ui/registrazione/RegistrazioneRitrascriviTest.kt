package snastro.ui.registrazione

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import snastro.kernel.ElaborazioneId
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.trascrizione.applicazione.letture.SegmentoTrascrittoView
import snastro.trascrizione.applicazione.letture.StatoElaborazioneVista
import snastro.trascrizione.applicazione.letture.StatoRegistrazioneVista
import snastro.trascrizione.applicazione.letture.TrascrittoView
import snastro.trascrizione.applicazione.letture.VoceTrascrittoView
import snastro.ui.AggiornamentiVistaFinta
import snastro.ui.ApriEsternoFinta
import snastro.ui.Cambiamento
import snastro.ui.lettore.LettoreAudioFinta
import snastro.ui.testi.MESSAGGIO_RITRASCRIZIONE_IN_CORSO
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

private val REG_1 = RegistrazioneId("id-1")
private val DATA_1: LocalDate = LocalDate.of(2026, 3, 12)

private fun unaVista(generazione: Int = 1) = TrascrittoView(
    registrazioneId = REG_1,
    titolo = "Seduta del 12 marzo",
    dataRegistrazione = DATA_1,
    durataMs = 125_000,
    segmenti = listOf(SegmentoTrascrittoView(SegmentoId(1), VoceId(1), 0, 2_000, "generazione $generazione")),
    voci = listOf(VoceTrascrittoView(VoceId(1), "Voce 1")),
)

private fun statoVista(stato: StatoElaborazioneVista) = StatoRegistrazioneVista(
    registrazioneId = REG_1,
    stato = stato,
    fase = null,
    avviataAlle = null,
    motivoFallimento = null,
    numVoci = 1,
    numeroPersone = null,
    trascrittoDisponibile = true,
    elaborazioneId = ElaborazioneId("elaborazione-1"),
)

/**
 * ADR 0018 Amendment (b) §2 (AC-452/453): the `stati` source — the read-only flag + banner while a
 * re-run is queued/running, and the reload on the Cambiamento a replacement publishes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RegistrazioneRitrascriviTest {
    @Suppress("LongParameterList") // one parameter per RegistrazionePresenter collaborator this file exercises
    private fun presentatore(
        scope: TestScope,
        trascritto: () -> TrascrittoView? = { unaVista() },
        stati: () -> StatoRegistrazioneVista? = { null },
        aggiornamenti: AggiornamentiVistaFinta = AggiornamentiVistaFinta(),
    ): RegistrazionePresenter {
        val dispatcher = StandardTestDispatcher(scope.testScheduler)
        val scopeCoroutine = CoroutineScope(dispatcher)
        return RegistrazionePresenter(
            scope = scopeCoroutine,
            io = dispatcher,
            registrazioneId = REG_1,
            trascritto = trascritto,
            documento = { null },
            lettore = LettoreAudioFinta(),
            apriEsterno = ApriEsternoFinta(),
            parlanti = unaSorgentiParlantiInerte(scopeCoroutine),
            stati = stati,
            aggiornamenti = aggiornamenti,
            riassunto = SorgenteRiassuntoS3(contenuto = {}, segno = { flowOf(null) }),
            selezioneSchedaS3 = SelezioneSchedaS3(),
        )
    }

    private val RegistrazionePresenter.dati get() = assertIs<RegistrazioneUiStato.Dati>(stato.value)

    @Test
    fun `AC-452 quando la sorgente stati non restituisce nulla la schermata non e mai in sola lettura`() = runTest {
        val presenter = presentatore(this, stati = { null })
        advanceUntilIdle()

        assertEquals(false, presenter.dati.soloLettura)
        assertNull(presenter.dati.bannerRitrascrizione)
    }

    @Test
    fun `AC-452 IN_ATTESA o IN_CORSO mettono la schermata in sola lettura con il banner`() = runTest {
        listOf(StatoElaborazioneVista.IN_ATTESA, StatoElaborazioneVista.IN_CORSO).forEach { stato ->
            val presenter = presentatore(this, stati = { statoVista(stato) })
            advanceUntilIdle()

            assertEquals(true, presenter.dati.soloLettura, "$stato")
            assertEquals(MESSAGGIO_RITRASCRIZIONE_IN_CORSO, presenter.dati.bannerRitrascrizione, "$stato")
        }
    }

    @Test
    fun `AC-452 COMPLETATA o FALLITA non sono sola lettura, nessun banner`() = runTest {
        listOf(StatoElaborazioneVista.COMPLETATA, StatoElaborazioneVista.FALLITA).forEach { stato ->
            val presenter = presentatore(this, stati = { statoVista(stato) })
            advanceUntilIdle()

            assertEquals(false, presenter.dati.soloLettura, "$stato")
            assertNull(presenter.dati.bannerRitrascrizione, "$stato")
        }
    }

    @Test
    fun `AC-452 lettura e riproduzione restano possibili in sola lettura`() = runTest {
        val lettore = LettoreAudioFinta()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scopeCoroutine = CoroutineScope(dispatcher)
        val presenter = RegistrazionePresenter(
            scope = scopeCoroutine,
            io = dispatcher,
            registrazioneId = REG_1,
            trascritto = { unaVista() },
            documento = { "/progetti/demo.snastro/documenti/seduta.md" },
            lettore = lettore,
            apriEsterno = ApriEsternoFinta(),
            parlanti = unaSorgentiParlantiInerte(scopeCoroutine),
            stati = { statoVista(StatoElaborazioneVista.IN_CORSO) },
            aggiornamenti = AggiornamentiVistaFinta(),
            riassunto = SorgenteRiassuntoS3(contenuto = {}, segno = { flowOf(null) }),
            selezioneSchedaS3 = SelezioneSchedaS3(),
        )
        advanceUntilIdle()

        assertEquals(true, presenter.dati.soloLettura)
        presenter.azioni.riproduciSegmento(SegmentoId(1))
        advanceUntilIdle()
        assertEquals(true, lettore.stato.value.inRiproduzione)
        assertEquals(1, presenter.dati.segmenti.size)
    }

    // --- AC-453: reload on the Cambiamento after a replacement --------------------------------------

    @Test
    fun `AC-453 il Cambiamento dopo una sostituzione ricarica i nuovi Segmenti`() = runTest {
        var generazione = 1
        var statoCorrente = StatoElaborazioneVista.IN_CORSO
        val aggiornamenti = AggiornamentiVistaFinta()
        val presenter = presentatore(
            this,
            trascritto = { unaVista(generazione) },
            stati = { statoVista(statoCorrente) },
            aggiornamenti = aggiornamenti,
        )
        advanceUntilIdle()
        assertEquals("generazione 1", presenter.dati.segmenti.single().testo)
        assertEquals(true, presenter.dati.soloLettura)

        generazione = 2
        statoCorrente = StatoElaborazioneVista.COMPLETATA
        aggiornamenti.emetti(Cambiamento(REG_1))
        advanceUntilIdle()

        assertEquals("generazione 2", presenter.dati.segmenti.single().testo)
        assertEquals(false, presenter.dati.soloLettura)
        assertNull(presenter.dati.bannerRitrascrizione)
    }

    @Test
    fun `AC-453 un Cambiamento di un altra Registrazione non ricarica`() = runTest {
        var chiamate = 0
        val aggiornamenti = AggiornamentiVistaFinta()
        val presenter = presentatore(
            this,
            trascritto = {
                chiamate++
                unaVista()
            },
            aggiornamenti = aggiornamenti,
        )
        advanceUntilIdle()
        val chiamateDopoCarico = chiamate

        aggiornamenti.emetti(Cambiamento(RegistrazioneId("id-altra")))
        advanceUntilIdle()

        assertEquals(chiamateDopoCarico, chiamate)
    }
}
