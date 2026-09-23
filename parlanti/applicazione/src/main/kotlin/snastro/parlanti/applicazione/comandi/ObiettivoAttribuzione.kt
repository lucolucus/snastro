package snastro.parlanti.applicazione.comandi

import snastro.kernel.ParlanteId
import snastro.parlanti.dominio.TipoParlante

/** The target `Parlante` of a [ConfermaAttribuzione]: an existing one, or a brand new Nome. */
public sealed interface ObiettivoAttribuzione {
    public data class ParlanteEsistente(val id: ParlanteId) : ObiettivoAttribuzione

    /** [tipo] defaults to `ricorrente`; the user may choose `occasionale` (AC-85). */
    public data class NuovoParlante(val nome: String, val tipo: TipoParlante = TipoParlante.RICORRENTE) :
        ObiettivoAttribuzione
}
