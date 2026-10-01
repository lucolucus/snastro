package snastro.trascrizione.adattatori.eventi

import snastro.kernel.DispatcherEventiInMemoria
import snastro.kernel.ElaborazioneId
import snastro.kernel.Esito
import snastro.kernel.EventoPubblicato
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.kernel.unIncontroDi
import snastro.persistenza.UnitaDiLavoroSql
import snastro.persistenza.databaseInMemoria
import snastro.progetto.applicazione.eventi.RegistrazioneEliminata
import snastro.progetto.applicazione.eventi.RegistrazioneRinominata
import snastro.trascrizione.adattatori.persistenza.ElaborazioneRepositorySql
import snastro.trascrizione.adattatori.persistenza.repositorySql
import snastro.trascrizione.adattatori.persistenza.seminato
import snastro.trascrizione.applicazione.politiche.ApplicaEliminazioneRegistrazionePolitica
import snastro.trascrizione.applicazione.porte.ElaborazioneRepository
import snastro.trascrizione.applicazione.porte.ElaborazioneRepositoryFinta
import snastro.trascrizione.applicazione.porte.VociDellIncontroRepositoryFinta
import snastro.trascrizione.applicazione.porte.ogniRegistrazioneNota
import snastro.trascrizione.dominio.ErroreTrascrizione.ElaborazioneGiaAperta
import snastro.trascrizione.dominio.StatoElaborazione.COMPLETATA
import snastro.trascrizione.dominio.StatoElaborazione.IN_ATTESA
import snastro.trascrizione.dominio.unaElaborazione
import snastro.trascrizione.dominio.unaRadice
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * [AbbonatoEliminazioneRegistrazione] (AC-612): a REAL [DispatcherEventiInMemoria] over a REAL [UnitaDiLavoroFinta]
 * whose participants are the `Ripristinabile` Finte, so a doomed transaction is genuinely rolled back; the policy's
 * own rule coverage is `ApplicaEliminazioneRegistrazionePoliticaTest`'s — this proves routing, placement and doom.
 */
class AbbonatoEliminazioneRegistrazioneTest {
    private val elaborazioni = ElaborazioneRepositoryContata(ElaborazioneRepositoryFinta())
    private val trascritti = VociDellIncontroRepositoryFinta()
    private val dispatcher = DispatcherEventiInMemoria(UnitaDiLavoroFinta(elaborazioni.delegato, trascritti)).also {
        val politica = ApplicaEliminazioneRegistrazionePolitica(elaborazioni, trascritti, ogniRegistrazioneNota())
        it.registraSincrono(AbbonatoEliminazioneRegistrazione(politica, ogniRegistrazioneNota()))
    }

    @Test
    fun `AC-612 RegistrazioneEliminata applica la politica dentro la transazione che la pubblica`() {
        elaborazioni.salva(unaElaborazione(COMPLETATA, ElaborazioneId("completata"), R)).atteso()
        trascritti.salva(unaRadice(registrazioneId = R))

        pubblicaInTransazione(eliminata(R)).atteso()

        assertEquals(emptyList(), elaborazioni.diRegistrazione(R))
        assertNull(trascritti.trascritto(R))
    }

    @Test
    fun `AC-612 l Errore della politica condanna la transazione e la annulla tutta`() {
        elaborazioni.salva(unaElaborazione(IN_ATTESA, ElaborazioneId("in-coda"), R)).atteso()
        trascritti.salva(unaRadice(registrazioneId = ALTRA))

        val esito = dispatcher.unitaDiLavoro.inTransazione {
            trascritti.rimuovi(unIncontroDi(ALTRA)) // a write of the same command, before the veto
            dispatcher.pubblica(eliminata(R))
            Esito.Ok(Unit)
        }

        assertEquals(ElaborazioneGiaAperta(R), esito.erroreAtteso<ElaborazioneGiaAperta>())
        assertNotNull(
            trascritti.trascritto(ALTRA),
            "il rollback ripristina la scrittura fatta prima del veto",
        )
        assertEquals(listOf("in-coda"), elaborazioni.diRegistrazione(R).map { it.id.valore })
    }

    @Test
    fun `AC-612 ogni altro evento e Ok senza chiamare la politica`() {
        val altro = RegistrazioneRinominata(R, "Vecchio", "Nuovo")

        pubblicaInTransazione(altro).atteso()
        pubblicaInTransazione(object : EventoPubblicato {}).atteso()

        assertEquals(0, elaborazioni.letture, "la politica rilegge sempre le Elaborazioni: non e stata chiamata")
    }

    /** The veto chain on a REAL SQLite [UnitaDiLavoroSql] (deferred LOW of eli-a): an open Elaborazione → nothing. */
    @Test
    fun `AC-612 INV-28 su SQLite un Elaborazione in attesa veta la RegistrazioneEliminata e nessuna riga cambia`() {
        val db = databaseInMemoria().seminato(listOf(R))
        val uow = UnitaDiLavoroSql(db)
        val elaborazioniSql = ElaborazioneRepositorySql(db)
        val trascrittiSql = repositorySql(db, uow)
        val sql = DispatcherEventiInMemoria(uow).also {
            val politica =
                ApplicaEliminazioneRegistrazionePolitica(elaborazioniSql, trascrittiSql, ogniRegistrazioneNota())
            it.registraSincrono(AbbonatoEliminazioneRegistrazione(politica, ogniRegistrazioneNota()))
        }
        elaborazioniSql.salva(unaElaborazione(COMPLETATA, ElaborazioneId("completata"), R)).atteso()
        trascrittiSql.salva(unaRadice(registrazioneId = R))
        elaborazioniSql.salva(unaElaborazione(IN_ATTESA, ElaborazioneId("in-coda"), R)).atteso()
        val prima = trascrittiSql.trascritto(R)?.segmenti

        val esito = sql.unitaDiLavoro.inTransazione {
            sql.pubblica(eliminata(R))
            db.registrazioneQueries.elimina(R.valore) // as EliminaRegistrazione does after pubblica: the FK throws
            Esito.Ok(Unit)
        }

        assertEquals(ElaborazioneGiaAperta(R), esito.erroreAtteso<ElaborazioneGiaAperta>())
        assertEquals(setOf("completata", "in-coda"), elaborazioniSql.diRegistrazione(R).map { it.id.valore }.toSet())
        assertEquals(prima, trascrittiSql.trascritto(R)?.segmenti)
        assertNotNull(db.registrazioneQueries.trovaPerId(R.valore).executeAsOneOrNull())
    }

    private fun pubblicaInTransazione(evento: EventoPubblicato): Esito<Unit> = dispatcher.unitaDiLavoro.inTransazione {
        dispatcher.pubblica(evento)
        Esito.Ok(Unit)
    }

    private fun eliminata(r: RegistrazioneId) =
        RegistrazioneEliminata(r, PROGETTO, "Seduta", DATA, RiferimentoAudio("audio/${r.valore}.m4a"))

    /** Counts the reads the policy always starts with ([diRegistrazione]). */
    private class ElaborazioneRepositoryContata(
        val delegato: ElaborazioneRepositoryFinta,
    ) : ElaborazioneRepository by delegato {
        var letture = 0

        override fun diRegistrazione(id: RegistrazioneId) = delegato.diRegistrazione(id).also { letture++ }
    }

    private companion object {
        val PROGETTO = ProgettoId("progetto-1")
        val R = RegistrazioneId("registrazione-1")
        val ALTRA = RegistrazioneId("registrazione-2")
        val DATA: LocalDate = LocalDate.of(2026, 9, 25)
    }
}
