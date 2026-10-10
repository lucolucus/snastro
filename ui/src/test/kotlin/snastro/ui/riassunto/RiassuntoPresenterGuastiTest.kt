package snastro.ui.riassunto

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import snastro.sintesi.applicazione.letture.StatoModelloVista
import snastro.ui.causatoDa
import snastro.ui.registriLogUi
import snastro.ui.testi.MESSAGGIO_ERRORE_GENERICO
import java.util.logging.Level
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * CR-7 at the Riassunto tab's edges (ADR 0041 §3): a fault in a command, in starting the download or in playing a
 * Fonte is logged at WARNING with its cause, and M1(b)'s generic message is shown where the tab stays on screen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RiassuntoPresenterGuastiTest {
    @Test
    fun `CR-7 un riassumiCmd che lancia e registrato con la sua causa e mostra l errore generico`() =
        eseguiTest { a ->
            runCurrent()
            a.lanciaRiassumiCmd = true
            val registri = registriLogUi {
                a.presenter.azioni.riassumi()
                runCurrent()
            }

            val registrato = registri.single { it.causatoDa(a.guastoRiassumiCmd) }
            assertEquals(Level.WARNING, registrato.level)
            assertTrue("riassumi" in registrato.message, registrato.message)
            assertEquals(MESSAGGIO_ERRORE_GENERICO, dati(a.presenter).messaggioErrore)
        }

    // Pre-release finding #151 (rework, MED): three independent triggers (init, cambiamenti,
    // statoFacoltativi) each `launch`ed their OWN `ricarica()` — none cancelling another. Firing TWO
    // of them back-to-back (before draining the scheduler) used to let BOTH complete their own full
    // read chain; single-flight (`collectLatest` over one merged flow) cancels the older one, so at
    // most ONE reload ever reaches its last read (`posizioni()`) per settle.
    @Test
    fun `CR-7 un avvio del download che lancia e registrato con la sua causa e riprova a leggere`() =
        eseguiTest { a ->
            a.vistaCorrente = unaVista(modello = StatoModelloVista.NonInstallato(6_169_341_984))
            runCurrent()
            val guasto = IllegalStateException("guasto simulato del download")
            a.servizioModelli.guasto = guasto
            val letturePrima = a.chiamatePosizioni
            val registri = registriLogUi {
                a.presenter.azioni.scaricaModello()
                runCurrent()
            }

            val registrato = registri.single { it.causatoDa(guasto) }
            assertEquals(Level.WARNING, registrato.level)
            assertTrue("scarica" in registrato.message, registrato.message)
            assertEquals(letturePrima + 1, a.chiamatePosizioni, "dopo il guasto la vista non e' stata riletta")
        }

    @Test
    fun `CR-7 una riproduzione che fallisce e registrata con la sua causa`() = eseguiTest { a ->
        a.lanciaRiproduciDa = true
        val registri = registriLogUi {
            a.presenter.azioni.apriFonte(REG_3, 750_000)
            runCurrent()
        }

        val registrato = registri.single { it.causatoDa(a.guastoRiproduciDa) }
        assertEquals(Level.WARNING, registrato.level)
        assertTrue("riproduci" in registrato.message, registrato.message)
    }

    @Test
    fun `M1 un avvio del download che lancia mostra l errore generico dopo aver riletto la vista`() =
        eseguiTest { a ->
            a.vistaCorrente = unaVista(modello = StatoModelloVista.NonInstallato(6_169_341_984))
            runCurrent()
            val guasto = IllegalStateException("guasto simulato del download")
            a.servizioModelli.guasto = guasto
            val letturePrima = a.chiamatePosizioni
            val registri = registriLogUi {
                a.presenter.azioni.scaricaModello()
                runCurrent()
            }

            assertEquals(Level.WARNING, registri.single { it.causatoDa(guasto) }.level)
            assertEquals(letturePrima + 1, a.chiamatePosizioni, "dopo il guasto la vista non e' stata riletta")
            assertEquals(MESSAGGIO_ERRORE_GENERICO, dati(a.presenter).messaggioErrore)
        }

    @Test
    fun `M1 una fonte di questa Parte che non si riproduce mostra l errore generico`() = eseguiTest { a ->
        runCurrent()
        a.lanciaRiproduciDa = true
        val registri = registriLogUi {
            a.presenter.azioni.apriFonte(REG_1, 750_000)
            runCurrent()
        }

        assertEquals(Level.WARNING, registri.single { it.causatoDa(a.guastoRiproduciDa) }.level)
        assertEquals(listOf("play:${REG_1.valore}@750000"), a.eventi)
        assertEquals(MESSAGGIO_ERRORE_GENERICO, dati(a.presenter).messaggioErrore)
    }
}
