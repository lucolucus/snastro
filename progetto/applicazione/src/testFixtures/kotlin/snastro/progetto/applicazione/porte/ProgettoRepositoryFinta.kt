package snastro.progetto.applicazione.porte

import snastro.kernel.Ripristinabile
import snastro.progetto.dominio.Progetto

/** In-memory [ProgettoRepository]; rolls back with `UnitaDiLavoroFinta` ([Ripristinabile]). */
public class ProgettoRepositoryFinta : ProgettoRepository, Ripristinabile {
    // Progetto is immutable: keeping the reference is keeping the saved state.
    private var salvato: Progetto? = null

    override fun trova(): Progetto? = salvato

    override fun salva(p: Progetto) {
        val presente = salvato
        check(presente == null || presente.id == p.id) {
            "il database ha gia il Progetto ${presente?.id?.valore}: non puo salvarne un altro (${p.id.valore})"
        }
        salvato = p
    }

    override fun istantanea(): () -> Unit {
        val copia = salvato
        return { salvato = copia }
    }
}
