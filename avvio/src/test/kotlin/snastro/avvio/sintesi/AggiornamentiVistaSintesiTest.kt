package snastro.avvio.sintesi

import snastro.kernel.DispatcherEventiInMemoria
import snastro.kernel.ErroreDiProva
import snastro.kernel.Esito
import snastro.kernel.EventoPubblicato
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.UnitaDiLavoroFinta
import snastro.sintesi.applicazione.eventi.LunghezzaMassimaRiassuntoModificata
import snastro.sintesi.applicazione.eventi.RiassuntoAvviato
import snastro.sintesi.applicazione.eventi.RiassuntoEliminato
import snastro.sintesi.applicazione.eventi.RiassuntoFallito
import snastro.sintesi.applicazione.eventi.RiassuntoPronto
import snastro.sintesi.applicazione.eventi.RiassuntoRichiesto
import snastro.ui.Cambiamento
import kotlin.test.Test
import kotlin.test.assertEquals

/** AC-S144: the after-commit mapping of every Sintesi event (never on rollback). */
class AggiornamentiVistaSintesiTest {
    private val dispatcher = DispatcherEventiInMemoria(UnitaDiLavoroFinta())
    private var avanzate = 0
    private val annullate = mutableListOf<RegistrazioneId>()
    private val aggiornamenti =
        AggiornamentiVistaSintesi({ avanzate++ }, { annullate += it }).also(dispatcher::registraDopoCommit)

    private fun pubblica(evento: EventoPubblicato, esito: Esito<Unit> = Esito.Ok(Unit)) {
        dispatcher.unitaDiLavoro.inTransazione {
            dispatcher.pubblica(evento)
            esito
        }
    }

    private fun ultimo(): Cambiamento? = aggiornamenti.cambiamenti.replayCache.lastOrNull()

    @Test
    fun `AC-S144 RiassuntoRichiesto fa avanzare la coda e ogni evento Sintesi e un Cambiamento dopo il commit`() {
        pubblica(RiassuntoRichiesto(R))
        assertEquals(1, avanzate)
        assertEquals(Cambiamento(R), ultimo())

        listOf(RiassuntoAvviato(R2), RiassuntoPronto(R), RiassuntoFallito(R2, "errore_modello")).forEach { e ->
            pubblica(e)
            assertEquals(Cambiamento((e as? RiassuntoFallito)?.registrazioneId ?: registrazioneDi(e)), ultimo())
        }
        pubblica(LunghezzaMassimaRiassuntoModificata(ProgettoId("p-1")))
        assertEquals(Cambiamento(null), ultimo())
        assertEquals(1, avanzate, "solo RiassuntoRichiesto segnala la coda")
        assertEquals(emptyList(), annullate)
    }

    @Test
    fun `AC-S144 RiassuntoEliminato chiede l'annullamento del Riassunto in corso di quella Registrazione`() {
        pubblica(RiassuntoEliminato(R))
        assertEquals(listOf(R), annullate)
        assertEquals(Cambiamento(R), ultimo())
    }

    @Test
    fun `AC-S144 su rollback nessun segnale, nessun annullamento, nessun Cambiamento`() {
        pubblica(RiassuntoEliminato(R), Esito.Errore(ErroreDiProva.Fallito("rollback")))
        pubblica(RiassuntoRichiesto(R), Esito.Errore(ErroreDiProva.Fallito("rollback")))
        assertEquals(emptyList(), annullate)
        assertEquals(0, avanzate)
        assertEquals(null, ultimo())
    }

    private fun registrazioneDi(e: EventoPubblicato): RegistrazioneId = when (e) {
        is RiassuntoAvviato -> e.registrazioneId
        is RiassuntoPronto -> e.registrazioneId
        else -> error("inatteso: $e")
    }

    private companion object {
        val R = RegistrazioneId("r-1")
        val R2 = RegistrazioneId("r-2")
    }
}
