package snastro.ui.impostazioni

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import snastro.ui.testi.MESSAGGIO_ERRORE_SALVATAGGIO_IMPOSTAZIONI
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val PREDEFINITA = "/Users/io/Documents/snastro"

/** In-memory [PreferenzeApp]; [guasta] makes every `salva` fail as an IO fault would. */
private class PreferenzeFinte(var salvate: Preferenze = Preferenze(), var guasta: Boolean = false) : PreferenzeApp {
    var salvataggi = 0
        private set

    override fun leggi(): Preferenze = salvate

    override fun salva(preferenze: Preferenze) {
        salvataggi++
        if (guasta) throw IOException("disco pieno")
        salvate = preferenze
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ImpostazioniPresenterTest {
    private fun TestScope.presenter(preferenze: PreferenzeApp) =
        ImpostazioniPresenter(this, StandardTestDispatcher(testScheduler), preferenze, PREDEFINITA)

    @Test
    fun `parte dalle preferenze salvate, con la cartella predefinita se nessuna e salvata`() = runTest {
        val p = presenter(PreferenzeFinte(Preferenze(tema = TemaApp.SCURO)))

        assertEquals(TemaApp.SCURO, p.stato.value.tema)
        assertEquals(PREDEFINITA, p.stato.value.cartellaProgetti)
        assertFalse(p.stato.value.cartellaPersonalizzata)
        assertEquals(TemaApp.SCURO, p.preferenzeCorrenti.value.tema)
    }

    @Test
    fun `cambiare il tema lo salva e lo applica all app`() = runTest {
        val preferenze = PreferenzeFinte()
        val p = presenter(preferenze)

        p.azioni.cambiaTema(TemaApp.CHIARO)
        advanceUntilIdle()

        assertEquals(TemaApp.CHIARO, preferenze.salvate.tema)
        assertEquals(TemaApp.CHIARO, p.preferenzeCorrenti.value.tema)
        assertEquals(TemaApp.CHIARO, p.stato.value.tema)
    }

    @Test
    fun `la cartella scelta diventa quella dei nuovi progetti, Ripristina torna alla predefinita`() = runTest {
        val preferenze = PreferenzeFinte()
        val p = presenter(preferenze)

        p.azioni.cambiaCartellaProgetti("/Volumes/Dati/progetti")
        advanceUntilIdle()
        assertEquals("/Volumes/Dati/progetti", p.cartellaProgetti())
        assertTrue(p.stato.value.cartellaPersonalizzata)

        p.azioni.ripristinaCartellaProgetti()
        advanceUntilIdle()
        assertEquals(PREDEFINITA, p.cartellaProgetti())
        assertEquals(null, preferenze.salvate.cartellaProgetti)
        assertFalse(p.stato.value.cartellaPersonalizzata)
    }

    @Test
    fun `un salvataggio fallito mostra un messaggio e lascia i valori salvati`() = runTest {
        val preferenze = PreferenzeFinte(guasta = true)
        val p = presenter(preferenze)

        p.azioni.cambiaTema(TemaApp.SCURO)
        advanceUntilIdle()

        assertEquals(MESSAGGIO_ERRORE_SALVATAGGIO_IMPOSTAZIONI, p.stato.value.errore)
        assertEquals(TemaApp.SISTEMA, p.stato.value.tema)
        assertEquals(TemaApp.SISTEMA, p.preferenzeCorrenti.value.tema)

        p.azioni.chiudiErrore()
        assertEquals(null, p.stato.value.errore)
    }

    @Test
    fun `scegliere lo stesso valore non salva di nuovo`() = runTest {
        val preferenze = PreferenzeFinte()
        val p = presenter(preferenze)

        p.azioni.cambiaTema(TemaApp.SISTEMA)
        advanceUntilIdle()

        assertEquals(0, preferenze.salvataggi)
    }

    @Test
    fun `la sezione scelta resta dopo un salvataggio`() = runTest {
        val p = presenter(PreferenzeFinte())

        p.azioni.seleziona(SezioneImpostazioni.GENERALI)
        p.azioni.cambiaTema(TemaApp.SCURO)
        p.azioni.seleziona(SezioneImpostazioni.MODELLI)
        advanceUntilIdle()

        assertEquals(SezioneImpostazioni.MODELLI, p.stato.value.sezione)
    }
}
