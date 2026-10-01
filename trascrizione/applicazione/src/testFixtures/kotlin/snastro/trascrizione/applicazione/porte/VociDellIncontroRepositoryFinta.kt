package snastro.trascrizione.applicazione.porte

import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.kernel.Ripristinabile
import snastro.trascrizione.dominio.Trascritto
import snastro.trascrizione.dominio.VociDellIncontro

/**
 * In-memory [VociDellIncontroRepository] (passes `VociDellIncontroRepositoryContratto`): stores and returns detached
 * copies ([VociDellIncontro.copia]; reconstitution is reserved to persistence adapters, CR-15), so no caller ever
 * aliases the stored state and a rollback of `UnitaDiLavoroFinta` also undoes in-place changes. Like the store's
 * primary key on a Parte, a Parte held by two roots is refused loudly. [Ripristinabile]: pass it to
 * `UnitaDiLavoroFinta`.
 */
public class VociDellIncontroRepositoryFinta : VociDellIncontroRepository, Ripristinabile {
    private val radici = LinkedHashMap<IncontroId, VociDellIncontro>()

    override fun trova(id: IncontroId): VociDellIncontro? = radici[id]?.copia()

    override fun salva(root: VociDellIncontro) {
        val parti = root.trascritti.map { it.registrazioneId }
        val altrove = radici.values.filter { it.incontroId != root.incontroId }.flatMap { it.trascritti }
        check(altrove.none { it.registrazioneId in parti }) { "una Parte di ${root.incontroId} e' in un'altra radice" }
        radici[root.incontroId] = root.copia()
    }

    override fun rimuovi(id: IncontroId) {
        radici.remove(id)
    }

    override fun trascritto(r: RegistrazioneId): Trascritto? = radici.values.firstNotNullOfOrNull { it.trascritto(r) }

    override fun conTrascritto(): List<RegistrazioneId> =
        radici.values.flatMap { radice -> radice.trascritti.map { it.registrazioneId } }

    override fun istantanea(): () -> Unit {
        val salvate = LinkedHashMap(radici)
        return {
            radici.clear()
            radici.putAll(salvate)
        }
    }
}
