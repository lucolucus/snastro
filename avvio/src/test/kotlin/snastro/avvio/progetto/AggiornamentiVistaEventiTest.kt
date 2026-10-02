package snastro.avvio.progetto

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import snastro.kernel.DispatcherEventiInMemoria
import snastro.kernel.ErroreDiProva
import snastro.kernel.Esito
import snastro.kernel.EventoPubblicato
import snastro.kernel.IncontroId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.unIncontroDi
import snastro.progetto.applicazione.eventi.DataRegistrazioneModificata
import snastro.progetto.applicazione.eventi.OraDiInizioModificata
import snastro.progetto.applicazione.eventi.ProgettoCreato
import snastro.progetto.applicazione.eventi.RegistrazioneAggiunta
import snastro.progetto.applicazione.eventi.RegistrazioneRinominata
import snastro.ui.Cambiamento
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** AC-242: a completed command produces a [Cambiamento] on [AggiornamentiVista] for its Registrazione. */
class AggiornamentiVistaEventiTest {
    private val delegata = DispatcherEventiInMemoria(UnitaDiLavoroFinta())
    private val aggiornamenti = AggiornamentiVistaEventi { null }.also(delegata::registraDopoCommit)

    @Test
    fun `AC-242 RegistrazioneAggiunta dopo il commit produce un Cambiamento per la sua Registrazione`() {
        val id = RegistrazioneId("id-1")
        delegata.unitaDiLavoro.inTransazione {
            delegata.pubblica(RegistrazioneAggiunta(id, ProgettoId("p-1"), unIncontroDi(id)))
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
            delegata.pubblica(DataRegistrazioneModificata(id, precedente, nuova, unIncontroDi(id)))
            Esito.Ok(Unit)
        }

        assertEquals(Cambiamento(id), aggiornamenti.cambiamenti.replayCache.lastOrNull())
    }

    @Test
    fun `AC-366 RegistrazioneRinominata dopo il commit produce un Cambiamento per la sua Registrazione`() {
        val id = RegistrazioneId("id-4")
        delegata.unitaDiLavoro.inTransazione {
            delegata.pubblica(RegistrazioneRinominata(id, "Vecchio", "Nuovo"))
            Esito.Ok(Unit)
        }

        assertEquals(Cambiamento(id), aggiornamenti.cambiamenti.replayCache.lastOrNull())
    }

    @Test
    fun `AC-242 nessun Cambiamento su rollback`() {
        val id = RegistrazioneId("id-3")
        delegata.unitaDiLavoro.inTransazione {
            delegata.pubblica(RegistrazioneAggiunta(id, ProgettoId("p-1"), unIncontroDi(id)))
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

    @Test
    fun `AC-I90 un evento che cambia le Parti di un Incontro rinfresca ogni Parte, anche la S3 aperta di un altra`() {
        val a = RegistrazioneId("a")
        val b = RegistrazioneId("b")
        val incontro = IncontroId("incontro-1")
        val raccolti = raccogli(AggiornamentiVistaEventi { listOf(a, b) })

        pubblica(RegistrazioneAggiunta(b, ProgettoId("p-1"), incontro))
        pubblica(OraDiInizioModificata(b, incontro, null, LocalTime.of(9, 0)))
        pubblica(DataRegistrazioneModificata(b, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-02"), incontro))

        assertEquals(setOf(Cambiamento(a), Cambiamento(b)), raccolti.toSet())
        assertEquals(6, raccolti.size)
    }

    @Test
    fun `AC-I90 l evento stesso rinfresca la sua Registrazione anche se l Incontro non elenca Parti`() {
        val b = RegistrazioneId("b")
        val raccolti = raccogli(AggiornamentiVistaEventi { null })

        pubblica(OraDiInizioModificata(b, IncontroId("incontro-1"), null, null))

        assertEquals(listOf(Cambiamento(b)), raccolti)
    }

    private fun raccogli(aggiornamenti: AggiornamentiVistaEventi): MutableList<Cambiamento> {
        delegata.registraDopoCommit(aggiornamenti)
        val raccolti = CopyOnWriteArrayList<Cambiamento>()
        CoroutineScope(Dispatchers.Unconfined).launch { aggiornamenti.cambiamenti.collect(raccolti::add) }
        return raccolti
    }

    private fun pubblica(evento: EventoPubblicato) {
        delegata.unitaDiLavoro.inTransazione {
            delegata.pubblica(evento)
            Esito.Ok(Unit)
        }
    }
}
