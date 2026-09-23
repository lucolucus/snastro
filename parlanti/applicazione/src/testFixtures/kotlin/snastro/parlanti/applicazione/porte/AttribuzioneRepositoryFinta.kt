package snastro.parlanti.applicazione.porte

import snastro.kernel.ParlanteId
import snastro.kernel.RegistrazioneId
import snastro.kernel.Ripristinabile
import snastro.kernel.VoceRef
import snastro.parlanti.dominio.Attribuzione

/**
 * In-memory [AttribuzioneRepository] keyed by [VoceRef] like `PRIMARY KEY (registrazione_id, voce_id)`
 * (ADR 0007): `salva` replaces. Stores and returns private copies. [Ripristinabile]: pass it to `UnitaDiLavoroFinta`.
 */
public class AttribuzioneRepositoryFinta : AttribuzioneRepository, Ripristinabile {
    private val righe = LinkedHashMap<VoceRef, Attribuzione>()

    override fun trova(v: VoceRef): Attribuzione? = righe[v]?.copia()

    override fun diRegistrazione(id: RegistrazioneId): List<Attribuzione> =
        righe.values.filter { it.voceRef.registrazioneId == id }.map { it.copia() }

    override fun diParlante(id: ParlanteId): List<Attribuzione> =
        righe.values.filter { it.parlanteId == id }.map { it.copia() }

    override fun salva(a: Attribuzione) {
        righe[a.voceRef] = a.copia()
    }

    override fun rimuovi(v: VoceRef) {
        righe.remove(v)
    }

    override fun istantanea(): () -> Unit {
        val salvate = LinkedHashMap(righe)
        return {
            righe.clear()
            righe.putAll(salvate)
        }
    }

    private fun Attribuzione.copia(): Attribuzione = Attribuzione.conferma(voceRef, progettoId, parlanteId).aggregato
}
