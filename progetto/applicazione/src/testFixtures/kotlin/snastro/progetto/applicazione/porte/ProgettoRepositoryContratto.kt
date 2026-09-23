package snastro.progetto.applicazione.porte

import org.junit.jupiter.api.Test
import snastro.kernel.ErroreDiProva
import snastro.kernel.Esito
import snastro.kernel.ProgettoId
import snastro.kernel.UnitaDiLavoro
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.progetto.dominio.NomeProgetto
import snastro.progetto.dominio.Progetto
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Contract of [ProgettoRepository] (boundary `repo-progetto`): one Progetto per project database.
 * One subclass per implementation (`ProgettoRepositoryFinta` here, `ProgettoRepositorySql` in
 * `:progetto:adattatori`).
 */
public abstract class ProgettoRepositoryContratto {
    /** A fresh, empty project database: the repository under test and the unit of work it joins. */
    public interface Ambiente {
        public val progetti: ProgettoRepository
        public val unitaDiLavoro: UnitaDiLavoro
    }

    protected abstract fun ambiente(): Ambiente

    @Test
    public fun `AC-25 un database senza Progetto non ne trova nessuno`() {
        assertNull(ambiente().progetti.trova())
    }

    @Test
    public fun `AC-25 salva poi trova restituisce lo stesso Progetto`() {
        val a = ambiente()
        a.unitaDiLavoro.inTransazione {
            a.progetti.salva(unProgetto())
            Esito.Ok(Unit)
        }.atteso()
        val trovato = assertNotNull(a.progetti.trova())
        assertEquals(ProgettoId("id-1"), trovato.id)
        assertEquals("Consiglio comunale", trovato.nome.valore)
    }

    @Test
    public fun `AC-25 un salva annullato dalla transazione non lascia traccia`() {
        val a = ambiente()
        a.unitaDiLavoro.inTransazione<Unit> {
            a.progetti.salva(unProgetto())
            Esito.Errore(ErroreDiProva.Fallito("annullato"))
        }.erroreAtteso<ErroreDiProva.Fallito>()
        assertNull(a.progetti.trova())
    }

    private fun unProgetto(): Progetto =
        Progetto.crea(ProgettoId("id-1"), NomeProgetto.di("Consiglio comunale").atteso()).aggregato
}
