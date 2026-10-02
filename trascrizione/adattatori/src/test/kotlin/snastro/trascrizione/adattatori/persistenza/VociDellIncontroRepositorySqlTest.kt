package snastro.trascrizione.adattatori.persistenza

import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.persistenza.SnastroDatabase
import snastro.persistenza.UnitaDiLavoroSql
import snastro.persistenza.databaseInMemoria
import snastro.trascrizione.applicazione.porte.LettoreRegistrazioneFinta
import snastro.trascrizione.applicazione.porte.PredisposizioneTrascrizione
import snastro.trascrizione.applicazione.porte.RegistrazioneVista
import snastro.trascrizione.applicazione.porte.VociDellIncontroRepository
import snastro.trascrizione.applicazione.porte.VociDellIncontroRepositoryContratto

/**
 * D2 (dev-architecture-app.md#porta-contratto): the contract passes real-on-real (AC-110/113, AC-I21). The store holds
 * an Incontro with two Parti as soon as its rows exist, so the multi-Parte cases run here too: the parent rows are
 * seeded raw (Progetto has no port here) and the Parti are read through a [LettoreRegistrazioneFinta] over the same
 * seed, never through the `registrazione` table (ADR 0033 §4.1).
 */
class VociDellIncontroRepositorySqlTest : VociDellIncontroRepositoryContratto() {

    private lateinit var db: SnastroDatabase
    private val viste = mutableMapOf<RegistrazioneId, RegistrazioneVista>()
    private val ordine = mutableMapOf<IncontroId, List<RegistrazioneId>>()

    override fun repository(): VociDellIncontroRepository {
        db = databaseInMemoria()
        return VociDellIncontroRepositorySql(db, UnitaDiLavoroSql(db), LettoreRegistrazioneFinta(viste, ordine))
    }

    override fun predisponi(predisposizione: PredisposizioneTrascrizione) {
        viste.putAll(predisposizione.viste())
        ordine.putAll(predisposizione.incontri)
        db.seminaPredisposizione(predisposizione)
    }
}
