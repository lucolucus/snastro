package snastro.ui.riassunto

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import snastro.sintesi.applicazione.letture.StatoModelloVista
import snastro.ui.Cambiamento
import snastro.ui.causatoDa
import snastro.ui.registriLogUi
import snastro.ui.testi.MESSAGGIO_ERRORE_GENERICO
import java.util.logging.Level
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * CR-7 at the Riassunto tab's edges (ADR 0041 §3): a fault in a command, in starting the download or in playing a
 * Fonte is logged at WARNING with its cause, and M1(b)'s generic message is shown where the tab stays on screen. A
 * fault reading the view never ends the reload collector (conventions non-fatal-faults, Reload collectors); an `Error`
 * from the committing lunghezza massima command propagates (D-0065).
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

    @Test
    fun `CR-7 una vista che lancia al primo caricamento mostra Errore e un Cambiamento successivo ricarica`() =
        eseguiTest { a ->
            val guasto = IllegalStateException("vista illeggibile")
            a.guastoVista = guasto
            val registri = registriLogUi { runCurrent() }

            val registrato = registri.single { it.causatoDa(guasto) }
            assertEquals(Level.WARNING, registrato.level)
            assertTrue("riassunto" in registrato.message, registrato.message)
            assertEquals(RiassuntoUiStato.Errore(MESSAGGIO_ERRORE_GENERICO), a.presenter.stato.value)

            a.guastoVista = null
            a.aggiornamenti.emetti(Cambiamento(REG_1))
            runCurrent()
            assertNull(dati(a.presenter).messaggioErrore, "il collettore delle ricariche e' finito col guasto")
        }

    @Test
    fun `CR-7 una vista che lancia con i Dati mostrati li tiene e mostra l errore generico`() = eseguiTest { a ->
        runCurrent()
        val mostrati = dati(a.presenter)
        val guasto = IllegalStateException("vista illeggibile")
        a.guastoVista = guasto
        val registri = registriLogUi {
            a.aggiornamenti.emetti(Cambiamento(REG_1))
            runCurrent()
        }

        assertEquals(Level.WARNING, registri.single { it.causatoDa(guasto) }.level)
        assertEquals(mostrati.copy(messaggioErrore = MESSAGGIO_ERRORE_GENERICO), dati(a.presenter))
    }

    @Test
    fun `Riprova dopo un primo caricamento fallito ricarica la vista`() = eseguiTest { a ->
        a.guastoVista = IllegalStateException("vista illeggibile")
        registriLogUi { runCurrent() }
        assertIs<RiassuntoUiStato.Errore>(a.presenter.stato.value)

        a.guastoVista = null
        a.presenter.azioni.riprova()
        assertEquals(RiassuntoUiStato.Caricamento, a.presenter.stato.value)
        runCurrent()
        assertNull(dati(a.presenter).messaggioErrore)
    }

    @Test
    fun `CR-7 una modifica della lunghezza massima che lancia mostra l errore generico e Salva resta usabile`() =
        eseguiTest { a ->
            runCurrent()
            val guasto = IllegalStateException("guasto SQL")
            a.guastoLunghezza = guasto
            a.presenter.azioni.modificaLunghezzaMassima()
            a.presenter.azioni.cambiaLunghezzaMassima("1500")
            val registri = registriLogUi {
                a.presenter.azioni.salvaLunghezzaMassima()
                runCurrent()
            }

            val registrato = registri.single { it.causatoDa(guasto) }
            assertEquals(Level.WARNING, registrato.level)
            assertTrue("lunghezza" in registrato.message, registrato.message)
            val modifica = assertIs<LunghezzaMassimaUiStato.Modifica>(dati(a.presenter).lunghezzaMassima)
            assertEquals(MESSAGGIO_ERRORE_GENERICO, modifica.errore)

            a.guastoLunghezza = null
            a.presenter.azioni.salvaLunghezzaMassima()
            runCurrent()
            assertEquals(listOf(1500, 1500), a.chiamateLunghezza)
        }

    @Test
    fun `D-0065 un Error della modifica della lunghezza massima si propaga`() = eseguiTest { a ->
        runCurrent()
        val guasto = AssertionError("errore di programmazione dopo il commit")
        a.guastoLunghezza = guasto
        a.presenter.azioni.modificaLunghezzaMassima()
        a.presenter.azioni.cambiaLunghezzaMassima("1500")
        a.presenter.azioni.salvaLunghezzaMassima()
        runCurrent()

        assertTrue(a.sfuggito(guasto), "${a.sfuggite}")
    }
}
