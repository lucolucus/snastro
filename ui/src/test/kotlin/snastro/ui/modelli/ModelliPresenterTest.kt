package snastro.ui.modelli

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import snastro.ui.testi.MESSAGGIO_ERRORE_GENERICO
import snastro.ui.testi.messaggioPer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * [ServizioModelli] whose `scarica` throws instead of updating `stato` — a hand-written fake
 * (RC-9/CR-17), not a MockK stub, mirroring `LettorePresenterTest`'s `LettoreAudioCheEsplode`.
 */
private class ServizioModelliCheEsplode(iniziale: StatoModelli) : ServizioModelli {
    private val _stato = MutableStateFlow(iniziale)
    override val stato: StateFlow<StatoModelli> = _stato.asStateFlow()
    override fun scarica(): Nothing = error("errore imprevisto")
    override fun licenze(): List<LicenzaVista> = emptyList()

    private val _statoFacoltativi = MutableStateFlow<Map<String, StatoModelloFacoltativo>>(emptyMap())
    override val statoFacoltativi: StateFlow<Map<String, StatoModelloFacoltativo>> = _statoFacoltativi.asStateFlow()
    override fun scaricaFacoltativo(id: String): Nothing = error("errore imprevisto")
}

@OptIn(ExperimentalCoroutinesApi::class)
class ModelliPresenterTest {
    private fun presentatore(scope: TestScope, servizio: ServizioModelli): ModelliPresenter {
        val dispatcher = StandardTestDispatcher(scope.testScheduler)
        return ModelliPresenter(CoroutineScope(dispatcher), dispatcher, servizio)
    }

    @Test
    fun `AC-227 stato iniziale rispecchia i modelli mancanti`() = runTest {
        val fake = ServizioModelliFinta(iniziale = StatoModelli.Mancanti(numero = 3, totaleByte = 900_000_000))
        val presenter = presentatore(this, fake)

        assertEquals(ModelliUiStato.Mancanti(3, 900_000_000), presenter.stato.value)
    }

    @Test
    fun `AC-228 un avanzamento del download e riflesso nello stato`() = runTest {
        val fake = ServizioModelliFinta(iniziale = StatoModelli.Mancanti(numero = 1, totaleByte = 487_170_055))
        val presenter = presentatore(this, fake)

        fake.emetti(StatoModelli.InDownload("asr-parakeet-tdt-0.6b-v3-int8", 100_000_000, 487_170_055))
        advanceUntilIdle()

        assertEquals(
            ModelliUiStato.InDownload("asr-parakeet-tdt-0.6b-v3-int8", 100_000_000, 487_170_055),
            presenter.stato.value,
        )
    }

    @Test
    fun `AC-229 un hash non valido mostra il messaggio dedicato`() = runTest {
        val fake = ServizioModelliFinta(iniziale = StatoModelli.Mancanti(numero = 1, totaleByte = 1_000))
        val presenter = presentatore(this, fake)
        val errore = ErroreServizioModelli.HashNonValido("asr-parakeet-tdt-0.6b-v3-int8")

        fake.emetti(StatoModelli.Errore(errore))
        advanceUntilIdle()

        assertEquals(ModelliUiStato.Errore(messaggioPer(errore)), presenter.stato.value)
    }

    @Test
    fun `AC-230 rete assente mostra il messaggio dedicato`() = runTest {
        val fake = ServizioModelliFinta(iniziale = StatoModelli.Mancanti(numero = 1, totaleByte = 1_000))
        val presenter = presentatore(this, fake)

        fake.emetti(StatoModelli.Errore(ErroreServizioModelli.ReteAssente))
        advanceUntilIdle()

        assertEquals(
            ModelliUiStato.Errore(messaggioPer(ErroreServizioModelli.ReteAssente)),
            presenter.stato.value,
        )
    }

    @Test
    fun `AC-231 AC-232 pronti mostra le licenze del catalogo`() = runTest {
        val licenze = listOf(unaLicenzaVista(nome = "Parakeet TDT 0.6B v3"), unaLicenzaVista(nome = "Silero VAD"))
        val fake = ServizioModelliFinta(iniziale = StatoModelli.Pronti, licenzeIniziali = licenze)
        val presenter = presentatore(this, fake)

        assertEquals(ModelliUiStato.Pronti(licenze), presenter.stato.value)
    }

    @Test
    fun `AC-227 AC-229 scarica invoca il servizio e riflette il suo esito`() = runTest {
        val fake = ServizioModelliFinta(
            iniziale = StatoModelli.Mancanti(numero = 1, totaleByte = 1_000),
            licenzeIniziali = listOf(unaLicenzaVista()),
            risultatoScarica = StatoModelli.Pronti,
        )
        val presenter = presentatore(this, fake)

        presenter.azioni.scarica()
        advanceUntilIdle()

        assertIs<ModelliUiStato.Pronti>(presenter.stato.value)
    }

    @Test
    fun `un eccezione imprevista di scarica mostra un errore generico senza restare bloccato`() = runTest {
        val fake = ServizioModelliCheEsplode(iniziale = StatoModelli.Mancanti(numero = 1, totaleByte = 1_000))
        val presenter = presentatore(this, fake)

        presenter.azioni.scarica()
        advanceUntilIdle()

        assertEquals(ModelliUiStato.Errore(MESSAGGIO_ERRORE_GENERICO), presenter.stato.value)
    }

    @Test
    fun `AC-S33 nessun modello facoltativo tracciato non mostra nessuna riga`() = runTest {
        val fake = ServizioModelliFinta(iniziale = StatoModelli.Pronti)
        val presenter = presentatore(this, fake)

        assertNull(presenter.etichettaModelloLinguisticoPiede.value)
    }

    @Test
    fun `AC-S33 il modello opzionale in download mostra la riga del piede in GB decimali`() = runTest {
        val fake = ServizioModelliFinta(iniziale = StatoModelli.Pronti)
        val presenter = presentatore(this, fake)

        fake.emettiFacoltativo("llm", StatoModelloFacoltativo.InDownload(2_100_000_000, 6_169_341_984))
        advanceUntilIdle()

        assertEquals("Modello di linguaggio: 2,1 di 6,2 GB", presenter.etichettaModelloLinguisticoPiede.value)
    }

    @Test
    fun `AC-S33 NonInstallato non mostra nessuna riga`() = runTest {
        val fake = ServizioModelliFinta(iniziale = StatoModelli.Pronti)
        val presenter = presentatore(this, fake)

        fake.emettiFacoltativo("llm", StatoModelloFacoltativo.NonInstallato(6_169_341_984))
        advanceUntilIdle()

        assertNull(presenter.etichettaModelloLinguisticoPiede.value)
    }

    @Test
    fun `AC-S33 Installato non mostra nessuna riga`() = runTest {
        val fake = ServizioModelliFinta(iniziale = StatoModelli.Pronti)
        val presenter = presentatore(this, fake)

        fake.emettiFacoltativo("llm", StatoModelloFacoltativo.Installato)
        advanceUntilIdle()

        assertNull(presenter.etichettaModelloLinguisticoPiede.value)
    }
}
