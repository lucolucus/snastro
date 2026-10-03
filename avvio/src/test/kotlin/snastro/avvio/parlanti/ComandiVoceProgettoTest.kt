package snastro.avvio.parlanti

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import snastro.kernel.ConsegnaDopoCommitFallita
import snastro.kernel.Esito
import snastro.kernel.ParlanteId
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.unIncontroDi
import snastro.ui.registrazione.ComandiVoce
import snastro.ui.registrazione.ComandiVoceContratto
import snastro.ui.registrazione.ComandoVoce
import snastro.ui.registrazione.ErroreComandoVoce
import snastro.ui.registrazione.FraseRef
import snastro.ui.registrazione.PassiNominaFrase
import java.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** D2 (AC-418): the per-project adapter honours the consumer's [ComandiVoce] contract unchanged. */
class ComandiVoceProgettoTest : ComandiVoceContratto() {
    override fun con(
        progetto: CoroutineScope,
        clock: Clock,
        esecutoreFrase: suspend (FraseRef, PassiNominaFrase) -> Esito<Unit>,
        esecutore: suspend (ComandoVoce) -> Esito<Unit>,
    ): ComandiVoce = ComandiVoceProgetto(progetto, clock, esecutore, esecutoreFrase)

    private val consegnaFallita = ConsegnaDopoCommitFallita(IllegalStateException("aggiornamento della vista fallito"))

    @Test
    fun `L237 un comando confermato il cui abbonato dopo-commit lancia resta un successo`() = runTest {
        val porta = ComandiVoceProgetto(CoroutineScope(StandardTestDispatcher(testScheduler)), Clock.systemUTC(), {
            throw consegnaFallita
        })
        val voce = VoceRef(unIncontroDi(RegistrazioneId("id-1")), VoceId(1))
        assertEquals(Esito.Ok(Unit), porta.esegui(ComandoVoce.Conferma(voce, ParlanteId("p-1"))))
    }

    @Test
    fun `L237 nominaFrase con una consegna dopo-commit fallita resta NonRiuscito, i passi seguenti non girano`() =
        runTest {
            val porta = ComandiVoceProgetto(
                CoroutineScope(StandardTestDispatcher(testScheduler)),
                Clock.systemUTC(),
                { Esito.Ok(Unit) },
                { _, _ -> throw consegnaFallita },
            )
            val errore = porta.nominaFrase(RegistrazioneId("id-1"), SegmentoId(3), PassiNominaFrase.SoloConferma)
            assertEquals(ErroreComandoVoce.NonRiuscito, assertIs<Esito.Errore>(errore).errore)
        }
}
