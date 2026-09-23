package snastro.progetto.applicazione.porte

import snastro.kernel.ProgettoId
import java.time.Instant

/** In-memory [RegistroProgetti], keyed by percorso. */
public class RegistroProgettiFinta : RegistroProgetti {
    private val voci = linkedMapOf<String, VoceRegistro>()

    override fun elenco(): List<VoceRegistro> = voci.values.sortedByDescending { it.ultimaAttivita }

    override fun registra(v: VoceRegistro) {
        voci[v.percorso] = v
    }

    override fun aggiorna(progettoId: ProgettoId, numRegistrazioni: Int, ultimaAttivita: Instant) {
        voci.replaceAll { _, v ->
            if (v.progettoId == progettoId) {
                v.copy(numRegistrazioni = numRegistrazioni, ultimaAttivita = ultimaAttivita)
            } else {
                v
            }
        }
    }

    override fun rimuovi(percorso: String) {
        voci.remove(percorso)
    }
}
