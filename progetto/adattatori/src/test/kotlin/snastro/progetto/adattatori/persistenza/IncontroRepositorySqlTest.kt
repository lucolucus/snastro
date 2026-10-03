package snastro.progetto.adattatori.persistenza

import snastro.kernel.ProgettoId
import snastro.persistenza.UnitaDiLavoroSql
import snastro.persistenza.databaseInMemoria
import snastro.progetto.applicazione.porte.IncontroRepositoryContratto

/** D2 (dev-architecture-app.md#porta-contratto): the contract real-on-real, on the same database as the Parti. */
class IncontroRepositorySqlTest : IncontroRepositoryContratto() {
    override fun ambiente(): Ambiente {
        val db = databaseInMemoria()
        val progetto = ProgettoId("progetto-1")
        db.progettoQueries.inserisci(progetto.valore, "Progetto di prova")
        return object : Ambiente {
            override val incontri = IncontroRepositorySql(db)
            override val registrazioni = RegistrazioneRepositorySql(db)
            override val unitaDiLavoro = UnitaDiLavoroSql(db)
            override val progettoId = progetto
        }
    }
}
