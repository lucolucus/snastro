package snastro.parlanti.applicazione.letture

import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId
import snastro.parlanti.applicazione.eventi.TipoParlanteVista
import snastro.parlanti.applicazione.porte.ParlanteRepository
import snastro.parlanti.dominio.TipoParlante

/**
 * `parlanti-attivi` ('altri ▾'): the attivo Parlanti of a Progetto, ordered by Nome. Read-only: no
 * rule lives here (RC-1) — [ParlanteRepository] owns the data, [Parlante.attivo] the predicate.
 */
public class ParlantiAttivi(
    private val parlanti: ParlanteRepository,
) {
    /** AC-174: the attivo Parlanti of [progettoId], ordered by Nome. */
    public fun parlanti(progettoId: ProgettoId): List<ParlanteAttivo> =
        parlanti.delProgetto(progettoId)
            .filter { it.attivo }
            .sortedBy { it.nome.valore }
            .map { ParlanteAttivo(it.id, it.nome.valore, it.tipo.vista()) }
}

/** One row of [ParlantiAttivi.parlanti]: AC-174. */
public data class ParlanteAttivo(
    val parlanteId: ParlanteId,
    val nome: String,
    val tipoParlante: TipoParlanteVista,
)

private fun TipoParlante.vista(): TipoParlanteVista =
    when (this) {
        TipoParlante.RICORRENTE -> TipoParlanteVista.RICORRENTE
        TipoParlante.OCCASIONALE -> TipoParlanteVista.OCCASIONALE
    }
