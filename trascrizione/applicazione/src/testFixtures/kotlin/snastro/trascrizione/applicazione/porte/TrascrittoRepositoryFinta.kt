package snastro.trascrizione.applicazione.porte

import snastro.kernel.RegistrazioneId
import snastro.kernel.Ripristinabile
import snastro.trascrizione.dominio.Trascritto

/**
 * In-memory [TrascrittoRepository]: stores and returns private copies rebuilt through the aggregate's own
 * API (`crea` + Revisione, see [copia]; reconstitution is reserved to persistence adapters, CR-15), so no
 * caller ever aliases the stored state and a rollback of `UnitaDiLavoroFinta` also undoes in-place changes.
 * [Ripristinabile]: pass it to `UnitaDiLavoroFinta`.
 */
public class TrascrittoRepositoryFinta : TrascrittoRepository, Ripristinabile {
    private val righe = LinkedHashMap<RegistrazioneId, Trascritto>()

    override fun trova(id: RegistrazioneId): Trascritto? = righe[id]?.copia()

    override fun conTrascritto(): List<RegistrazioneId> = righe.keys.toList()

    override fun salva(t: Trascritto) {
        righe[t.registrazioneId] = t.copia()
    }

    override fun istantanea(): () -> Unit {
        val salvate = LinkedHashMap(righe)
        return {
            righe.clear()
            righe.putAll(salvate)
        }
    }
}
