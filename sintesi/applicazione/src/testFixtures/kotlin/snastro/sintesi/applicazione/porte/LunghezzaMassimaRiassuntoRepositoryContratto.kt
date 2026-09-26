package snastro.sintesi.applicazione.porte

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import snastro.kernel.ProgettoId
import snastro.kernel.atteso
import snastro.sintesi.dominio.LunghezzaMassimaParole
import snastro.sintesi.dominio.LunghezzaMassimaRiassunto
import kotlin.test.assertEquals

/**
 * Consumer-driven contract of [LunghezzaMassimaRiassuntoRepository] (boundary `repo-sintesi`, ADR 0022, AC-S70):
 * no row → the default, `salva` is a per-Progetto upsert. One subclass per implementation (the Finta here,
 * `LunghezzaMassimaRiassuntoRepositorySql` in `:sintesi:adattatori`).
 */
public abstract class LunghezzaMassimaRiassuntoRepositoryContratto {
    /** A fresh, empty repository. */
    protected abstract fun repository(): LunghezzaMassimaRiassuntoRepository

    /** Hook for real stores: create the parent rows of every id the contract uses ([PREDISPOSIZIONE]). */
    protected open fun predisponi(predisposizione: PredisposizioneSintesi) {}

    private lateinit var repo: LunghezzaMassimaRiassuntoRepository

    @BeforeEach
    public fun preparaRepository() {
        repo = repository()
        predisponi(PREDISPOSIZIONE)
    }

    @Test
    public fun `AC-S70 senza riga trova restituisce la predefinita 2000 del Progetto`() {
        val letta = repo.trova(PROGETTO)

        assertEquals(PROGETTO, letta.progettoId)
        assertEquals(LunghezzaMassimaParole.PREDEFINITA, letta.parole.valore)
        assertEquals(2000, letta.parole.valore)
    }

    @Test
    public fun `AC-S70 salva 1500 poi trova restituisce 1500 e un altro Progetto non cambia`() {
        repo.salva(conParole(PROGETTO, 1500)).atteso()

        assertEquals(1500, repo.trova(PROGETTO).parole.valore)
        assertEquals(PROGETTO, repo.trova(PROGETTO).progettoId)
        assertEquals(2000, repo.trova(ALTRO_PROGETTO).parole.valore)
    }

    @Test
    public fun `AC-S70 salva e un upsert l ultimo valore vince`() {
        repo.salva(conParole(PROGETTO, 1500)).atteso()
        repo.salva(conParole(ALTRO_PROGETTO, 300)).atteso()

        repo.salva(conParole(PROGETTO, 2500)).atteso()

        assertEquals(2500, repo.trova(PROGETTO).parole.valore)
        assertEquals(300, repo.trova(ALTRO_PROGETTO).parole.valore)
    }

    private fun conParole(progetto: ProgettoId, parole: Int): LunghezzaMassimaRiassunto =
        LunghezzaMassimaRiassunto.predefinita(progetto).also { it.modifica(parole).atteso() }

    public companion object {
        public val PROGETTO: ProgettoId = ProgettoId("progetto-1")
        public val ALTRO_PROGETTO: ProgettoId = ProgettoId("progetto-2")

        /** Both Progetti, no Registrazione. */
        public val PREDISPOSIZIONE: PredisposizioneSintesi =
            PredisposizioneSintesi(setOf(PROGETTO, ALTRO_PROGETTO), emptyMap())
    }
}
