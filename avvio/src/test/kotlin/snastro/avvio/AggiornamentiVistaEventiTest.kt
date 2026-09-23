package snastro.avvio

import snastro.kernel.DispatcherEventiInMemoria
import snastro.kernel.ErroreDiProva
import snastro.kernel.Esito
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.UnitaDiLavoroFinta
import snastro.progetto.applicazione.eventi.DataRegistrazioneModificata
import snastro.progetto.applicazione.eventi.ProgettoCreato
import snastro.progetto.applicazione.eventi.RegistrazioneAggiunta
import snastro.ui.Cambiamento
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** AC-242: a completed command produces a [Cambiamento] on [AggiornamentiVista] for its Registrazione. */
class AggiornamentiVistaEventiTest {
    private val delegata = DispatcherEventiInMemoria(UnitaDiLavoroFinta())
    private val aggiornamenti = AggiornamentiVistaEventi(delegata)

    @Test
    fun `AC-242 RegistrazioneAggiunta dopo il commit produce un Cambiamento per la sua Registrazione`() {
        val id = RegistrazioneId("id-1")
        delegata.unitaDiLavoro.inTransazione {
            delegata.pubblica(RegistrazioneAggiunta(id, ProgettoId("p-1")))
            Esito.Ok(Unit)
        }

        assertEquals(Cambiamento(id), aggiornamenti.cambiamenti.replayCache.lastOrNull())
    }

    @Test
    fun `AC-242 DataRegistrazioneModificata dopo il commit produce un Cambiamento per la sua Registrazione`() {
        val id = RegistrazioneId("id-2")
        val precedente = LocalDate.parse("2026-01-01")
        val nuova = LocalDate.parse("2026-02-02")
        delegata.unitaDiLavoro.inTransazione {
            delegata.pubblica(DataRegistrazioneModificata(id, precedente, nuova))
            Esito.Ok(Unit)
        }

        assertEquals(Cambiamento(id), aggiornamenti.cambiamenti.replayCache.lastOrNull())
    }

    @Test
    fun `AC-242 nessun Cambiamento su rollback`() {
        val id = RegistrazioneId("id-3")
        delegata.unitaDiLavoro.inTransazione {
            delegata.pubblica(RegistrazioneAggiunta(id, ProgettoId("p-1")))
            Esito.Errore(ErroreDiProva.Fallito("boom"))
        }

        assertNull(aggiornamenti.cambiamenti.replayCache.lastOrNull())
    }

    @Test
    fun `AC-242 un evento pubblicato non riguardante una Registrazione non produce Cambiamento`() {
        delegata.unitaDiLavoro.inTransazione {
            delegata.pubblica(ProgettoCreato(ProgettoId("p-1"), "Prova"))
            Esito.Ok(Unit)
        }

        assertNull(aggiornamenti.cambiamenti.replayCache.lastOrNull())
    }
}
