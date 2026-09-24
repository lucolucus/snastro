package snastro.ui.registrazione

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import snastro.kernel.ElaborazioneId
import snastro.kernel.Esito
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.parlanti.applicazione.eventi.TipoParlanteVista
import snastro.parlanti.applicazione.letture.VoceIdentificata
import snastro.parlanti.dominio.ErroreParlanti
import snastro.trascrizione.applicazione.letture.StatoElaborazioneVista
import snastro.trascrizione.applicazione.letture.StatoRegistrazioneVista
import snastro.ui.Cambiamento
import snastro.ui.testi.MESSAGGIO_RITRASCRIZIONE_PERSA
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun statoVista(stato: StatoElaborazioneVista, trascrittoDisponibile: Boolean = true) = StatoRegistrazioneVista(
    registrazioneId = REG,
    stato = stato,
    fase = null,
    avviataAlle = null,
    motivoFallimento = null,
    posizioneInCoda = null,
    numVoci = if (trascrittoDisponibile) 3 else null,
    numeroPersone = null,
    trascrittoDisponibile = trascrittoDisponibile,
    elaborazioneId = ElaborazioneId("elaborazione-1"),
)

/**
 * ADR 0018 Amendment (b) §2 (AC-454/455/461): the Voci panel disabled while S3 is read-only (a re-run
 * queued/running over the Trascritto shown), the third banner line, and re-enabling on the re-run's
 * end (failure or cancellation). Split from `RegistrazioneIdentificazioneTest`, which covers the panel
 * with `stati` absent (never read-only).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RegistrazioneVociRitrascriviTest {
    private fun TestScope.ambiente() =
        AmbienteVoci(CoroutineScope(StandardTestDispatcher(testScheduler)), OrologioVirtuale(testScheduler))

    private fun TestScope.avvia(a: AmbienteVoci): RegistrazionePresenter {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return a.presenter(CoroutineScope(dispatcher), dispatcher, conStati = true)
    }

    private val RegistrazionePresenter.dati get() = assertIs<RegistrazioneUiStato.Dati>(stato.value)

    private fun RegistrazionePresenter.carta(voce: VoceId): CartaVoce =
        assertNotNull(dati.pannello).carte.single { it.voceId == voce }

    @Test
    fun `AC-454 in sola lettura le card sono disabilitate e nessun comando e inviato`() = runTest {
        val a = ambiente().apply { statoElaborazione = statoVista(StatoElaborazioneVista.IN_CORSO) }
        val presenter = avvia(a)
        advanceUntilIdle()

        assertEquals(true, presenter.dati.soloLettura)
        assertFalse(presenter.carta(V1).azioniAbilitate)

        presenter.azioni.conferma(V1)
        presenter.azioni.confermaParlante(V1, MARCO.parlanteId)
        presenter.azioni.nuovoParlante(V1, "Anna", TipoParlanteVista.OCCASIONALE)
        presenter.azioni.salta(V2)
        advanceUntilIdle()

        assertTrue(a.comandi.eseguiti.isEmpty())
    }

    @Test
    fun `AC-454 in sola lettura la selezione e la Revisione non inviano comandi`() = runTest {
        val a = ambiente().apply { statoElaborazione = statoVista(StatoElaborazioneVista.IN_ATTESA) }
        val presenter = avvia(a)
        advanceUntilIdle()

        presenter.azioni.selezionaSegmento(SegmentoId(1))
        presenter.azioni.selezionaSegmento(SegmentoId(3))
        presenter.azioni.dividiVoce()
        presenter.azioni.riassegnaA(V2)
        presenter.azioni.unisci(V1, V2)
        advanceUntilIdle()

        assertTrue(a.revisioni.isEmpty())
    }

    @Test
    fun `AC-454 nessun lavoro di Proposta e avviato in sola lettura`() = runTest {
        val a = ambiente().apply { statoElaborazione = statoVista(StatoElaborazioneVista.IN_CORSO) }
        val presenter = avvia(a)
        advanceUntilIdle()

        assertEquals(true, presenter.dati.soloLettura)
        assertTrue(a.chiamateProposta.isEmpty())
    }

    @Test
    fun `AC-454 estratto e riproduzione restano possibili in sola lettura`() = runTest {
        val a = ambiente().apply { statoElaborazione = statoVista(StatoElaborazioneVista.IN_CORSO) }
        val presenter = avvia(a)
        advanceUntilIdle()

        presenter.azioni.riproduciEstrattoVoce(V1)
        advanceUntilIdle()

        assertEquals(true, a.lettore.stato.value.inRiproduzione)
    }

    @Test
    fun `AC-455 un Conferma pendente prima della sostituzione risolve VoceCambiata e il pannello si riabilita`() =
        runTest {
            val a = ambiente()
            a.comandi.trattieni = true
            val presenter = avvia(a)
            advanceUntilIdle()
            presenter.azioni.conferma(V1) // pending BEFORE the re-run is even queued
            advanceUntilIdle()

            a.statoElaborazione = statoVista(StatoElaborazioneVista.IN_CORSO)
            a.aggiornamenti.emetti(Cambiamento(REG))
            advanceUntilIdle()
            assertEquals(true, presenter.dati.soloLettura)

            a.esitoComando = { Esito.Errore(ErroreParlanti.VoceCambiata(ref(V1))) }
            a.comandi.rilascia(ref(V1))
            advanceUntilIdle()

            assertEquals("La voce è cambiata nel frattempo: riprova.", presenter.carta(V1).errore)

            // the re-run completes: read-only ends, new Voci, all 'da identificare' again
            a.statoElaborazione = statoVista(StatoElaborazioneVista.COMPLETATA)
            a.aggiornamenti.emetti(Cambiamento(REG))
            advanceUntilIdle()

            assertEquals(false, presenter.dati.soloLettura)
            assertTrue(presenter.carta(V1).azioniAbilitate)
        }

    @Test
    fun `AC-461 il fallimento del re-run riabilita il pannello sugli stessi Voci e Nomi`() = runTest {
        val a = ambiente().apply {
            identificate = listOf(VoceIdentificata(V1, MARCO.parlanteId, "Marco", TipoParlanteVista.RICORRENTE))
            statoElaborazione = statoVista(StatoElaborazioneVista.IN_CORSO)
        }
        val presenter = avvia(a)
        advanceUntilIdle()
        assertEquals(true, presenter.dati.soloLettura)
        assertEquals("Marco", assertIs<ContenutoCarta.Attribuita>(presenter.carta(V1).contenuto).nome)

        a.statoElaborazione = statoVista(StatoElaborazioneVista.FALLITA)
        a.aggiornamenti.emetti(Cambiamento(REG))
        advanceUntilIdle()

        assertEquals(false, presenter.dati.soloLettura)
        assertNull(presenter.dati.bannerRitrascrizionePannello)
        assertEquals("Marco", assertIs<ContenutoCarta.Attribuita>(presenter.carta(V1).contenuto).nome)
        assertTrue(presenter.carta(V1).azioniAbilitate)
        assertTrue(a.chiamateProposta.isNotEmpty()) // the Proposta job starts again
    }

    @Test
    fun `AC-461 l annullamento del re-run in coda riabilita il pannello`() = runTest {
        val a = ambiente().apply { statoElaborazione = statoVista(StatoElaborazioneVista.IN_ATTESA) }
        val presenter = avvia(a)
        advanceUntilIdle()
        assertEquals(true, presenter.dati.soloLettura)

        a.statoElaborazione = statoVista(StatoElaborazioneVista.COMPLETATA)
        a.aggiornamenti.emetti(Cambiamento(REG))
        advanceUntilIdle()

        assertEquals(false, presenter.dati.soloLettura)
        assertTrue(presenter.carta(V1).azioniAbilitate)
    }

    @Test
    fun `AC-454 il terzo banner del pannello compare solo in sola lettura`() = runTest {
        val a = ambiente()
        val presenter = avvia(a)
        advanceUntilIdle()
        assertNull(presenter.dati.bannerRitrascrizionePannello)

        a.statoElaborazione = statoVista(StatoElaborazioneVista.IN_CORSO)
        a.aggiornamenti.emetti(Cambiamento(REG))
        advanceUntilIdle()

        assertEquals(MESSAGGIO_RITRASCRIZIONE_PERSA, presenter.dati.bannerRitrascrizionePannello)
    }
}
