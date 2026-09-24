package snastro.parlanti.adattatori.persistenza

import snastro.parlanti.applicazione.porte.AttribuzioneRepository
import snastro.parlanti.applicazione.porte.AttribuzioneRepositoryContratto
import snastro.parlanti.applicazione.porte.PredisposizioneParlanti
import snastro.persistenza.SnastroDatabase
import snastro.persistenza.databaseInMemoria

/** D2 (dev-architecture-app.md#porta-contratto): the contract passes real-on-real (AC-118). */
class AttribuzioneRepositorySqlTest : AttribuzioneRepositoryContratto() {
    private lateinit var db: SnastroDatabase

    override fun repository(): AttribuzioneRepository {
        db = databaseInMemoria()
        return AttribuzioneRepositorySql(db)
    }

    /**
     * Every id the contract uses, parent-first (deferred FKs to `voce`, immediate FK to `parlante`):
     * the Attribuzione contract references Parlanti WITHOUT saving them itself (PredisposizioneParlanti).
     */
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
        predisposizione.parlanti.forEach { (parlanteId, progettoId) ->
            db.parlanteQueries.inserisci(
                id = parlanteId.valore,
                progettoId = progettoId.valore,
                nome = parlanteId.valore,
                nomeNormalizzato = parlanteId.valore.lowercase(),
                tipo = "ricorrente",
                stato = "attivo",
            )
        }
    }
}
