package snastro.avvio.trascrizione

import snastro.persistenza.SnastroDatabase
import snastro.persistenza.databaseInMemoria
import snastro.trascrizione.adattatori.persistenza.ElaborazioneRepositorySql
import snastro.trascrizione.applicazione.letture.ElaborazioniInAttesaContratto
import snastro.trascrizione.applicazione.porte.ElaborazioneRepository
import snastro.trascrizione.applicazione.porte.PredisposizioneTrascrizione

/**
 * A41, D2 (dev-architecture-app.md#porta-contratto): [ElaborazioniInAttesaContratto] over the REAL
 * [ElaborazioneRepositorySql] — `avvio-coda-condivisa` (this module) re-runs the FIFO/id-order contract
 * end to end over the real store, instead of trusting only `:trascrizione:applicazione`'s Finta-backed D1.
 */
class ElaborazioniInAttesaDaSqlTest : ElaborazioniInAttesaContratto() {
    private lateinit var db: SnastroDatabase

    override fun repository(): ElaborazioneRepository {
        db = databaseInMemoria()
        return ElaborazioneRepositorySql(db)
    }

    override fun predisponi(predisposizione: PredisposizioneTrascrizione) {
        predisposizione.progetti.forEach { db.progettoQueries.inserisci(it.valore, "Progetto di prova") }
        predisposizione.registrazioni.forEach { (registrazioneId, progettoId) ->
            db.registrazioneQueries.inserisci(
                id = registrazioneId.valore,
                progettoId = progettoId.valore,
                titolo = "Registrazione di prova",
                riferimentoAudio = "audio/${registrazioneId.valore}.wav",
                durataMs = 600_000L,
                dataRegistrazione = "2026-09-23",
                aggiuntaAlle = 0L,
            )
        }
    }
}
