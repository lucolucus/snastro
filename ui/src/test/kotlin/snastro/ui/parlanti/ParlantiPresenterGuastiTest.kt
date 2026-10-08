package snastro.ui.parlanti

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import snastro.kernel.Esito
import snastro.kernel.ParlanteId
import snastro.parlanti.applicazione.comandi.RinominaParlante
import snastro.parlanti.applicazione.eventi.TipoParlanteVista
import snastro.parlanti.applicazione.letture.ParlanteDelProgetto
import snastro.parlanti.applicazione.letture.StatoParlanteVista
import snastro.ui.AggiornamentiVistaFinta
import snastro.ui.Cambiamento
import snastro.ui.causatoDa
import snastro.ui.lettore.LettoreAudioFinta
import snastro.ui.registriLogUi
import snastro.ui.testi.MESSAGGIO_ERRORE_GENERICO
import java.util.logging.Level
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private val PARLANTE = ParlanteId("id-1")

private val MARCO = ParlanteDelProgetto(
    PARLANTE,
    "Marco",
    TipoParlanteVista.RICORRENTE,
    StatoParlanteVista.ATTIVO,
    numImpronte = 2,
    numIncontri = 1,
    ultimaApparizione = null,
    estratto = null,
)

/**
 * CR-7 at S4's edges (ADR 0041 §3): a thrown rinomina and a thrown refresh are logged at WARNING with their cause,
 * and the screen still shows the generic message.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ParlantiPresenterGuastiTest {
    private fun presentatore(
        scope: TestScope,
        parlanti: () -> List<ParlanteDelProgetto>,
        rinomina: (RinominaParlante) -> Esito<Unit> = { error("rinomina non atteso in questo test") },
        aggiornamenti: AggiornamentiVistaFinta = AggiornamentiVistaFinta(),
    ): ParlantiPresenter {
        val dispatcher = StandardTestDispatcher(scope.testScheduler)
        return ParlantiPresenter(
            CoroutineScope(dispatcher),
            dispatcher,
            parlanti,
            rinomina,
            { error("promuovi non atteso in questo test") },
            { error("elimina non atteso in questo test") },
            LettoreAudioFinta(),
            aggiornamenti,
        )
    }

    @Test
    fun `CR-7 una rinomina che lancia e registrata con la sua causa e la riga mostra l errore generico`() = runTest {
        val guasto = IllegalStateException("guasto SQL")
        val presenter = presentatore(this, parlanti = { listOf(MARCO) }, rinomina = { throw guasto })
        advanceUntilIdle()

        val registri = registriLogUi {
            presenter.azioni.rinomina(PARLANTE, "Marco Rossi")
            advanceUntilIdle()
        }

        assertEquals(Level.WARNING, registri.single { it.causatoDa(guasto) }.level)
        val riga = assertIs<ParlantiUiStato.Dati>(presenter.stato.value).ricorrenti.single()
        assertEquals(MESSAGGIO_ERRORE_GENERICO, riga.erroreRiga)
        assertEquals(false, riga.operazioneInCorso)
    }

    @Test
    fun `CR-7 un aggiornamento che lancia e registrato con la sua causa e le righe note restano`() = runTest {
        val guasto = IllegalStateException("catalogo illeggibile")
        var rotto = false
        val aggiornamenti = AggiornamentiVistaFinta()
        val presenter = presentatore(
            this,
            parlanti = { if (rotto) throw guasto else listOf(MARCO) },
            aggiornamenti = aggiornamenti,
        )
        advanceUntilIdle()

        rotto = true
        val registri = registriLogUi {
            aggiornamenti.emetti(Cambiamento(null))
            advanceUntilIdle()
        }

        assertEquals(Level.WARNING, registri.single { it.causatoDa(guasto) }.level)
        val dati = assertIs<ParlantiUiStato.Dati>(presenter.stato.value)
        assertEquals(MESSAGGIO_ERRORE_GENERICO, dati.errore)
        assertEquals(listOf(PARLANTE), dati.ricorrenti.map { it.parlanteId })
    }
}
