package snastro.sintesi.adattatori.eventi

import io.mockk.spyk
import io.mockk.verify
import snastro.kernel.AbbonatoDopoCommit
import snastro.kernel.AbbonatoSincrono
import snastro.kernel.DispatcherEventiInMemoria
import snastro.kernel.Esito
import snastro.kernel.EventoPubblicato
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.atteso
import snastro.progetto.applicazione.eventi.RegistrazioneEliminata
import snastro.sintesi.applicazione.politiche.ApplicaEliminazioneRegistrazioneSintesiPolitica
import snastro.sintesi.applicazione.porte.RiassuntoRepository
import snastro.sintesi.applicazione.porte.RiassuntoRepositoryFinta
import snastro.sintesi.applicazione.porte.unRiassunto
import snastro.sintesi.dominio.ErroreSintesi
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

/**
 * [AbbonatoProgettoSintesi] (AC-S117/AC-S118): a REAL [DispatcherEventiInMemoria] over a REAL [UnitaDiLavoroFinta]
 * whose participant is the `Ripristinabile` [RiassuntoRepositoryFinta], so a doomed transaction is genuinely rolled
 * back; the policy's own rule coverage is [ApplicaEliminazioneRegistrazioneSintesiPolitica]'s own test — this
 * proves the value's shape, routing, transaction placement and doom, mirroring
 * `AbbonatoEliminazioneRegistrazioneTest` (Trascrizione).
 */
class AbbonatoProgettoSintesiTest {
    @Test
    fun `AC-S117 e un valore AbbonatoSincrono, mai dopo commit, e costruirlo non registra nulla`() {
        val riassunti = RiassuntoRepositoryFinta()
        val dispatcher = spyk(DispatcherEventiInMemoria(UnitaDiLavoroFinta(riassunti)))

        val abbonato: Any =
            AbbonatoProgettoSintesi(ApplicaEliminazioneRegistrazioneSintesiPolitica(riassunti, dispatcher))

        assertIs<AbbonatoSincrono>(abbonato)
        assertFalse(abbonato is AbbonatoDopoCommit)
        verify(exactly = 0) { dispatcher.registraSincrono(any()) } // ADR 0030 §1, AC-C67: the composition registers
        verify(exactly = 0) { dispatcher.registraDopoCommit(any()) }
    }

    @Test
    fun `AC-S117 ricevi ignora ogni evento diverso da RegistrazioneEliminata, mai chiama la politica`() {
        val riassunti = RiassuntoRepositoryFinta()
        val dispatcher = DispatcherEventiInMemoria(UnitaDiLavoroFinta(riassunti))
        val politica = spyk(ApplicaEliminazioneRegistrazioneSintesiPolitica(riassunti, dispatcher))
        dispatcher.registraSincrono(AbbonatoProgettoSintesi(politica))

        val esito = dispatcher.unitaDiLavoro.inTransazione {
            dispatcher.pubblica(object : EventoPubblicato {})
            Esito.Ok(Unit)
        }

        esito.atteso()
        verify(exactly = 0) { politica.applica(any()) }
    }

    @Test
    fun `AC-S117 RegistrazioneEliminata applica la politica col suo registrazioneId, dentro la transazione`() {
        val riassunti = RiassuntoRepositoryFinta()
        riassunti.salva(unRiassunto("vecchio", REG)).atteso()
        val dispatcher = DispatcherEventiInMemoria(UnitaDiLavoroFinta(riassunti))
        val politica = spyk(ApplicaEliminazioneRegistrazioneSintesiPolitica(riassunti, dispatcher))
        dispatcher.registraSincrono(AbbonatoProgettoSintesi(politica))

        val esito = dispatcher.unitaDiLavoro.inTransazione {
            dispatcher.pubblica(eliminata(REG))
            Esito.Ok(Unit)
        }

        esito.atteso()
        verify(exactly = 1) { politica.applica(REG) }
        assertEquals(emptyList(), riassunti.diRegistrazione(REG), "l'effetto della politica e davvero applicato")
    }

    @Test
    fun `AC-S118 un Errore della politica e restituito invariato e annulla tutta la transazione (rollback)`() {
        val riassunti = RiassuntoRepositoryFinta()
        riassunti.salva(unRiassunto("esistente", REG)).atteso()
        val dispatcher = DispatcherEventiInMemoria(UnitaDiLavoroFinta(riassunti))
        val guasto = Esito.Errore(ErroreSintesi.RiassuntoNonTrovato("x"))
        val riassuntiGuasti = object : RiassuntoRepository by riassunti {
            override fun rimuoviDiRegistrazione(r: RegistrazioneId): Esito<Int> = guasto
        }
        dispatcher.registraSincrono(
            AbbonatoProgettoSintesi(ApplicaEliminazioneRegistrazioneSintesiPolitica(riassuntiGuasti, dispatcher)),
        )

        val esito = dispatcher.unitaDiLavoro.inTransazione {
            riassunti.salva(unRiassunto("altra-reg", ALTRA)) // una scrittura della stessa transazione, prima del veto
            dispatcher.pubblica(eliminata(REG))
            Esito.Ok(Unit)
        }

        assertEquals(guasto, esito, "EliminaRegistrazione fallisce con l'Errore della politica, invariato")
        assertEquals(1, riassunti.diRegistrazione(REG).size, "rollback: la riga originale di REG resta")
        assertEquals(
            emptyList(),
            riassunti.diRegistrazione(ALTRA),
            "rollback: annulla anche la scrittura precedente al veto",
        )
    }

    private fun eliminata(r: RegistrazioneId) =
        RegistrazioneEliminata(r, PROGETTO, "Seduta", DATA, RiferimentoAudio("audio/${r.valore}.m4a"))

    private companion object {
        val PROGETTO = ProgettoId("progetto-1")
        val REG = RegistrazioneId("registrazione-1")
        val ALTRA = RegistrazioneId("registrazione-2")
        val DATA: LocalDate = LocalDate.of(2026, 9, 25)
    }
}
