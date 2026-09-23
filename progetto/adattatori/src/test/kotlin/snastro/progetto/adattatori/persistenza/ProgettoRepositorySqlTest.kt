package snastro.progetto.adattatori.persistenza

import snastro.persistenza.UnitaDiLavoroSql
import snastro.persistenza.databaseInMemoria
import snastro.progetto.applicazione.porte.ProgettoRepositoryContratto

/** D2 (dev-architecture-app.md#porta-contratto): the contract passes real-on-real (AC-109). */
class ProgettoRepositorySqlTest : ProgettoRepositoryContratto() {
    override fun ambiente(): Ambiente {
        val db = databaseInMemoria()
        return object : Ambiente {
            override val progetti = ProgettoRepositorySql(db)
            override val unitaDiLavoro = UnitaDiLavoroSql(db)
        }
    }
}
