package snastro.parlanti.adattatori.persistenza

import snastro.kernel.ParlanteId
import snastro.parlanti.applicazione.porte.ParlanteRepository
import snastro.parlanti.applicazione.porte.ParlanteRepositoryContratto
import snastro.parlanti.applicazione.porte.PredisposizioneParlanti
import snastro.persistenza.SnastroDatabase
import snastro.persistenza.databaseInMemoria

/** D2 (dev-architecture-app.md#porta-contratto): the contract passes real-on-real (AC-114/115/117/118). */
class ParlanteRepositorySqlTest : ParlanteRepositoryContratto() {
    private lateinit var db: SnastroDatabase

    override fun repository(): ParlanteRepository {
        db = databaseInMemoria()
        return ParlanteRepositorySql(db)
    }

    override fun righeImpronte(id: ParlanteId): Int =
        db.improntaVocaleQueries.trovaDiParlante(id.valore).executeAsList().size

    /** Every id the contract's Attribuzione-free scenarios use, parent-first (deferred FKs to `voce`). */
    override fun predisponi(predisposizione: PredisposizioneParlanti) {
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
            db.trascrittoQueries.inserisci(
                registrazioneId = registrazioneId.valore,
                prossimaVoce = 1L,
                prossimoSegmento = 1L,
            )
        }
        predisposizione.voci.forEach { v ->
            db.voceQueries.inserisci(registrazioneId = v.registrazioneId.valore, numero = v.voceId.numero.toLong())
        }
    }
}
