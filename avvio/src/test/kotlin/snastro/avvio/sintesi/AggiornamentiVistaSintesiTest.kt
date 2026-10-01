package snastro.avvio.sintesi

import snastro.kernel.DispatcherEventiInMemoria
import snastro.kernel.ErroreDiProva
import snastro.kernel.Esito
import snastro.kernel.EventoPubblicato
import snastro.kernel.IncontroId
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
    private val annullate = mutableListOf<IncontroId>()

    // ADR 0033 §4.1: each Incontro of the test has its one Parte; I_CESSATO no longer has any.
    private val aggiornamenti =
        AggiornamentiVistaSintesi({ avanzate++ }, { annullate += it }, { i -> parti[i] })
            .also(dispatcher::registraDopoCommit)

    private fun pubblica(evento: EventoPubblicato, esito: Esito<Unit> = Esito.Ok(Unit)) {
        dispatcher.unitaDiLavoro.inTransazione {
            dispatcher.pubblica(evento)
            esito
        }
    }

    private fun ultimo(): Cambiamento? = aggiornamenti.cambiamenti.replayCache.lastOrNull()

    @Test
    fun `AC-S144 RiassuntoRichiesto fa avanzare la coda e ogni evento Sintesi e un Cambiamento dopo il commit`() {
        pubblica(RiassuntoRichiesto(I))
        assertEquals(1, avanzate)
        assertEquals(Cambiamento(R), ultimo())

        listOf(RiassuntoAvviato(I2), RiassuntoPronto(I), RiassuntoFallito(I2, "errore_modello")).forEach { e ->
            pubblica(e)
            assertEquals(Cambiamento(parti.getValue(incontroDi(e)).single()), ultimo())
        }
        pubblica(LunghezzaMassimaRiassuntoModificata(ProgettoId("p-1")))
        assertEquals(Cambiamento(null), ultimo())
        assertEquals(1, avanzate, "solo RiassuntoRichiesto segnala la coda")
        assertEquals(emptyList(), annullate)
    }

    @Test
    fun `AC-S144 RiassuntoEliminato chiede l'annullamento del Riassunto in corso di quella Registrazione`() {
        pubblica(RiassuntoEliminato(I))
        assertEquals(listOf(I), annullate)
        assertEquals(Cambiamento(R), ultimo())
    }

    @Test
    fun `AC-S144 su rollback nessun segnale, nessun annullamento, nessun Cambiamento`() {
        pubblica(RiassuntoEliminato(I), Esito.Errore(ErroreDiProva.Fallito("rollback")))
        pubblica(RiassuntoRichiesto(I), Esito.Errore(ErroreDiProva.Fallito("rollback")))
        assertEquals(emptyList(), annullate)
        assertEquals(0, avanzate)
        assertEquals(null, ultimo())
    }

    @Test
    fun `AC-S144 un evento di un Incontro cessato aggiorna tutte le viste`() {
        pubblica(RiassuntoEliminato(I_CESSATO))
        assertEquals(listOf(I_CESSATO), annullate)
        assertEquals(Cambiamento(null), ultimo())
    }

    private fun incontroDi(e: EventoPubblicato): IncontroId = when (e) {
        is RiassuntoAvviato -> e.incontroId
        is RiassuntoPronto -> e.incontroId
        is RiassuntoFallito -> e.incontroId
        else -> error("inatteso: $e")
    }

    private companion object {
        val R = RegistrazioneId("r-1")
        val R2 = RegistrazioneId("r-2")
        val I = IncontroId("i-1")
        val I2 = IncontroId("i-2")
        val I_CESSATO = IncontroId("i-cessato")
        val parti = mapOf(I to listOf(R), I2 to listOf(R2))
    }
}
