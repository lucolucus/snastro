package snastro.ui.registrazione

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import snastro.kernel.ElaborazioneId
import snastro.kernel.Esito
import snastro.kernel.EstrattoRef
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.kernel.unIncontroDi
import snastro.parlanti.applicazione.eventi.TipoParlanteVista
import snastro.parlanti.applicazione.letture.CoppiaTraParti
import snastro.parlanti.applicazione.letture.PropostaDiUnione
import snastro.parlanti.applicazione.letture.VoceIdentificata
import snastro.trascrizione.applicazione.comandi.UnisciVoci
import snastro.trascrizione.applicazione.letture.StatoElaborazioneVista
import snastro.trascrizione.applicazione.letture.StatoRegistrazioneVista
import snastro.ui.testi.testoTraParti
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val PARTE_1 = RegistrazioneId("id-1")
private val PARTE_2 = RegistrazioneId("id-2")

private fun unaCoppia() = CoppiaTraParti(
    voceA = V1,
    parteA = 1,
    estrattoA = EstrattoRef(PARTE_1, listOf(IntervalloMs(0, 500))),
    voceB = V3,
    parteB = 2,
    estrattoB = EstrattoRef(PARTE_2, listOf(IntervalloMs(1_000, 1_500))),
)

/**
 * AC-I83/AC-I84 (ADR 0036 §3, UX S3): the cross-Parte banner of the Voci panel — the second banner kind,
 * published by [StatoVoci] into [PannelloVoci.traParti].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RegistrazioneTraPartiTest {
    private fun TestScope.ambiente() =
        AmbienteVoci(CoroutineScope(StandardTestDispatcher(testScheduler)), OrologioVirtuale(testScheduler))

    private fun TestScope.avvia(a: AmbienteVoci, conStati: Boolean = false): RegistrazionePresenter {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return a.presenter(CoroutineScope(dispatcher), dispatcher, conStati)
    }

    private val RegistrazionePresenter.dati get() = assertIs<RegistrazioneUiStato.Dati>(stato.value)

    @Test
    fun `AC-I83 una coppia e il banner con i due estratti e Unisci tiene la Voce della parte precedente`() = runTest {
        val a = ambiente().apply { traParti = listOf(unaCoppia()) }
        val presenter = avvia(a)
        advanceUntilIdle()

        val pannello = assertNotNull(presenter.dati.pannello)
        assertEquals(unaCoppia(), pannello.traParti)
        assertTrue(pannello.unioneAbilitata)
        assertEquals(listOf(unIncontroDi(REG)), a.chiamateTraParti)
        assertEquals("Voce 3 e Voce 1 (parte 1) sembrano la stessa persona", testoTraParti(1, 1, 3))

        presenter.azioni.unisci(V1, V3)
        advanceUntilIdle()
        val atteso = UnisciVoci(REG, sopravvive = V1, rimossa = V3, incontroDelleVoci = INCONTRO_REG)
        assertEquals(listOf<Any>(atteso), a.revisioni)
    }

    @Test
    fun `AC-I83 piu coppie mostrano un solo banner, il primo`() = runTest {
        val seconda = unaCoppia().copy(voceA = V2, voceB = V3)
        val a = ambiente().apply { traParti = listOf(unaCoppia(), seconda) }
        val presenter = avvia(a)
        advanceUntilIdle()
        assertEquals(unaCoppia(), assertNotNull(presenter.dati.pannello).traParti)
    }

    @Test
    fun `AC-I84 con una Proposta di unione c e solo quel banner e la proposta tra parti non e calcolata`() = runTest {
        val a = ambiente().apply {
            identificate = listOf(
                VoceIdentificata(V1, MARCO.parlanteId, "Marco", TipoParlanteVista.RICORRENTE),
                VoceIdentificata(V2),
                VoceIdentificata(V3, MARCO.parlanteId, "Marco", TipoParlanteVista.RICORRENTE),
            )
            unioni = listOf(PropostaDiUnione(V1, V3, MARCO.parlanteId, "Marco"))
            traParti = listOf(unaCoppia())
        }
        val presenter = avvia(a)
        advanceUntilIdle()

        val pannello = assertNotNull(presenter.dati.pannello)
        assertEquals(a.unioni, pannello.unioni)
        assertNull(pannello.traParti)
        assertTrue(a.chiamateTraParti.isEmpty())
    }

    @Test
    fun `AC-I84 in sola lettura nessun banner tra parti e nessun calcolo richiesto`() = runTest {
        val a = ambiente().apply {
            traParti = listOf(unaCoppia())
            statoElaborazione = StatoRegistrazioneVista(
                registrazioneId = REG,
                stato = StatoElaborazioneVista.IN_CORSO,
                fase = null,
                avviataAlle = null,
                motivoFallimento = null,
                numVoci = 3,
                numeroPersone = null,
                trascrittoDisponibile = true,
                elaborazioneId = ElaborazioneId("elaborazione-1"),
            )
        }
        val presenter = avvia(a, conStati = true)
        advanceUntilIdle()

        assertEquals(true, presenter.dati.soloLettura)
        assertNull(assertNotNull(presenter.dati.pannello).traParti)
        assertTrue(a.chiamateTraParti.isEmpty())
    }

    @Test
    fun `AC-I84 la coppia sparisce al ricaricamento dopo l unione`() = runTest {
        val a = ambiente().apply { traParti = listOf(unaCoppia()) }
        val presenter = avvia(a)
        advanceUntilIdle()
        assertNotNull(assertNotNull(presenter.dati.pannello).traParti)

        a.esitoRevisione = {
            a.traParti = emptyList()
            Esito.Ok(Unit)
        }
        presenter.azioni.unisci(V1, V3)
        advanceUntilIdle()

        assertNull(assertNotNull(presenter.dati.pannello).traParti)
    }

    @Test
    fun `AC-I84 L189 dopo una Revisione la coppia non e piu offerta gia durante la rilettura`() = runTest {
        val a = ambiente().apply { traParti = listOf(unaCoppia()) }
        val presenter = avvia(a)
        advanceUntilIdle()
        assertNotNull(assertNotNull(presenter.dati.pannello).traParti)

        var durante: CoppiaTraParti? = unaCoppia()
        a.esitoRevisione = {
            a.allaLetturaTrascritto = { durante = presenter.dati.pannello?.traParti }
            Esito.Ok(Unit)
        }
        presenter.azioni.unisci(V1, V3)
        advanceUntilIdle()

        assertNull(durante)
    }

    @Test
    fun `AC-I84 un calcolo fallito non mostra banner e il pannello resta utilizzabile`() = runTest {
        val a = ambiente().apply { traPartiRotta = true }
        val presenter = avvia(a)
        advanceUntilIdle()
        val pannello = assertNotNull(presenter.dati.pannello)
        assertNull(pannello.traParti)
        assertEquals(3, pannello.carte.size)
    }
}
