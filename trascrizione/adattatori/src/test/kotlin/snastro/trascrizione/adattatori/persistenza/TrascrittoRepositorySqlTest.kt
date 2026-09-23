package snastro.trascrizione.adattatori.persistenza

import snastro.persistenza.SnastroDatabase
import snastro.persistenza.databaseInMemoria
import snastro.trascrizione.applicazione.porte.PredisposizioneTrascrizione
import snastro.trascrizione.applicazione.porte.TrascrittoRepository
import snastro.trascrizione.applicazione.porte.TrascrittoRepositoryContratto

/** D2 (dev-architecture-app.md#porta-contratto): the contract passes real-on-real (AC-110/113). */
class TrascrittoRepositorySqlTest : TrascrittoRepositoryContratto() {
    private lateinit var db: SnastroDatabase

    override fun repository(): TrascrittoRepository {
        db = databaseInMemoria()
        return TrascrittoRepositorySql(db)
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
