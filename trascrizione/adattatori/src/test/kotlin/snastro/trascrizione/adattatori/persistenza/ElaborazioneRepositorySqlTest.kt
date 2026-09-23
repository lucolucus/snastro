package snastro.trascrizione.adattatori.persistenza

import snastro.persistenza.SnastroDatabase
import snastro.persistenza.databaseInMemoria
import snastro.trascrizione.applicazione.porte.ElaborazioneRepository
import snastro.trascrizione.applicazione.porte.ElaborazioneRepositoryContratto
import snastro.trascrizione.applicazione.porte.PredisposizioneTrascrizione

/** D2 (dev-architecture-app.md#porta-contratto): the contract passes real-on-real (AC-110/111/113/378). */
class ElaborazioneRepositorySqlTest : ElaborazioneRepositoryContratto() {
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
