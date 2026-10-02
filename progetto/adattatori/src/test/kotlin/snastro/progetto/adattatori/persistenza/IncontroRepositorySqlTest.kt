package snastro.progetto.adattatori.persistenza

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import snastro.kernel.IncontroId
import snastro.kernel.ProgettoId
import snastro.persistenza.UnitaDiLavoroSql
import snastro.persistenza.databaseInMemoria
import snastro.progetto.applicazione.porte.IncontroRepositoryContratto
import snastro.progetto.dominio.Incontro
import kotlin.test.assertEquals

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

    @Test
    fun `salvare un Incontro esistente con un altro Progetto fallisce e non cambia la riga`() {
        val a = ambiente()
        val id = IncontroId("incontro-x")
        a.incontri.salva(Incontro.nuovo(id, a.progettoId))
        assertThrows<IllegalStateException> { a.incontri.salva(Incontro.nuovo(id, ProgettoId("altro"))) }
        a.incontri.salva(Incontro.nuovo(id, a.progettoId)) // same Progetto: idempotent
        assertEquals(a.progettoId, a.incontri.trova(id)?.progettoId)
    }
}
