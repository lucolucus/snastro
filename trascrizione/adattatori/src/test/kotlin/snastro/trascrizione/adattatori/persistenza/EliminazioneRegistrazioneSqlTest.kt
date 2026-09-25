package snastro.trascrizione.adattatori.persistenza

import snastro.kernel.ElaborazioneId
import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.persistenza.SnastroDatabase
import snastro.persistenza.UnitaDiLavoroSql
import snastro.persistenza.databaseInMemoria
import snastro.trascrizione.dominio.ErroreTrascrizione.ElaborazioneNonTrovata
import snastro.trascrizione.dominio.StatoElaborazione.COMPLETATA
import snastro.trascrizione.dominio.StatoElaborazione.FALLITA
import snastro.trascrizione.dominio.unTrascritto
import snastro.trascrizione.dominio.unaElaborazione
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * ADR 0020 on SQL (AC-620): `TrascrittoRepositorySql.rimuovi` deletes segmento, voce, trascritto (the voce FK is
 * immediate, so any other order fails) and `ElaborazioneRepositorySql.rimuoviDiRegistrazione` every Elaborazione —
 * both inside the CALLER's transaction. The contracts (AC-619) run in the `*RepositorySqlTest` subclasses.
 */
class EliminazioneRegistrazioneSqlTest {
    private val db = databaseInMemoria().seminato(listOf(R, ALTRA))
    private val trascritti = TrascrittoRepositorySql(db)
    private val elaborazioni = ElaborazioneRepositorySql(db)
    private val uow = UnitaDiLavoroSql(db)

    init {
        for (r in listOf(R, ALTRA)) {
            elaborazioni.salva(unaElaborazione(FALLITA, ElaborazioneId("fallita-${r.valore}"), r)).atteso()
            elaborazioni.salva(unaElaborazione(COMPLETATA, ElaborazioneId("completata-${r.valore}"), r)).atteso()
            trascritti.salva(unTrascritto(voci = 3, segmentiPerVoce = 2, registrazioneId = r))
        }
    }

    @Test
    fun `AC-620 dopo rimuovi e rimuoviDiRegistrazione le righe segmento voce trascritto elaborazione di r sono 0`() {
        uow.inTransazione {
            trascritti.rimuovi(R)
            elaborazioni.rimuoviDiRegistrazione(R)
            Esito.Ok(Unit)
        }.atteso()

        assertEquals(Righe(0, 0, 0, 0), righe(R))
        assertEquals(Righe(segmenti = 6, voci = 3, trascritti = 1, elaborazioni = 2), righe(ALTRA))
    }

    @Test
    fun `AC-620 rimuovi e rimuoviDiRegistrazione in una transazione annullata non tolgono nulla`() {
        uow.inTransazione<Unit> {
            trascritti.rimuovi(R)
            elaborazioni.rimuoviDiRegistrazione(R)
            Esito.Errore(ElaborazioneNonTrovata(ElaborazioneId("annullata")))
        }.erroreAtteso<ElaborazioneNonTrovata>()

        assertEquals(Righe(segmenti = 6, voci = 3, trascritti = 1, elaborazioni = 2), righe(R))
    }

    private data class Righe(val segmenti: Int, val voci: Int, val trascritti: Int, val elaborazioni: Int)

    private fun righe(r: RegistrazioneId) = Righe(
        segmenti = db.segmentoQueries.trovaDiTrascritto(r.valore).executeAsList().size,
        voci = db.voceQueries.trovaDiTrascritto(r.valore).executeAsList().size,
        trascritti = listOfNotNull(db.trascrittoQueries.trovaPerRegistrazione(r.valore).executeAsOneOrNull()).size,
        elaborazioni = db.elaborazioneQueries.trovaDiRegistrazione(r.valore).executeAsList().size,
    )

    private companion object {
        val R = RegistrazioneId("registrazione-1")
        val ALTRA = RegistrazioneId("registrazione-2")
    }
}

/** The Progetto and Registrazione rows the Trascrizione FKs need (seeded raw: Progetto has no port here). */
internal fun SnastroDatabase.seminato(registrazioni: List<RegistrazioneId>): SnastroDatabase = apply {
    progettoQueries.inserisci("progetto-1", "Progetto di prova")
    registrazioni.forEach {
        registrazioneQueries.inserisci(
            id = it.valore,
            progettoId = "progetto-1",
            titolo = "Registrazione ${it.valore}",
            riferimentoAudio = "audio/${it.valore}.wav",
            durataMs = 600_000L,
            dataRegistrazione = "2026-09-25",
            aggiuntaAlle = 0L,
        )
    }
}
