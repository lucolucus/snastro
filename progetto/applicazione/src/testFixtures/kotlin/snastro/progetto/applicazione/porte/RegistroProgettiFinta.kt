package snastro.progetto.applicazione.porte

import java.time.Instant

/** In-memory [RegistroProgetti], keyed by percorso. */
public class RegistroProgettiFinta : RegistroProgetti {
    private val voci = linkedMapOf<String, VoceRegistro>()

    override fun elenco(): List<VoceRegistro> = voci.values.sortedByDescending { it.ultimaAttivita }

    override fun registra(v: VoceRegistro) {
        voci[v.percorso] = v
    }

    override fun aggiorna(percorso: String, numRegistrazioni: Int, ultimaAttivita: Instant) {
        voci.computeIfPresent(percorso) { _, v ->
            v.copy(numRegistrazioni = numRegistrazioni, ultimaAttivita = ultimaAttivita)
        }
    }

    override fun rimuovi(percorso: String) {
        voci.remove(percorso)
    }
}
