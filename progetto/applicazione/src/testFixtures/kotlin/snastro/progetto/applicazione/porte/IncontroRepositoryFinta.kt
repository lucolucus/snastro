package snastro.progetto.applicazione.porte

import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.kernel.Ripristinabile
import snastro.progetto.dominio.Incontro

/**
 * In-memory [IncontroRepository] over the same state as [registrazioni] (the Parti), like the two SQL tables it
 * mirrors; rolls back with `UnitaDiLavoroFinta` ([Ripristinabile]). [rimuovi] refuses while a Parte exists, as the
 * immediate FK `registrazione → incontro` does; [salva] refuses an existing id under another Progetto, as the adapter does.
 */
public class IncontroRepositoryFinta(private val registrazioni: RegistrazioneRepositoryFinta) :
    IncontroRepository,
    Ripristinabile {
    private val righe = linkedMapOf<IncontroId, Incontro>()

    override fun trova(id: IncontroId): Incontro? = righe[id]

    override fun salva(i: Incontro) {
        val esistente = righe.putIfAbsent(i.id, i)
        check(esistente == null || esistente.progettoId == i.progettoId) {
            "Incontro ${i.id.valore} appartiene gia' a un altro Progetto (${esistente?.progettoId?.valore})"
        }
    }

    override fun rimuovi(id: IncontroId) {
        check(registrazioni.partiDi(id).isEmpty()) {
            "FOREIGN KEY constraint failed: Incontro ${id.valore} ha ancora una Parte (INV-I1)"
        }
        righe.remove(id)
    }

    override fun partiDi(id: IncontroId): List<RegistrazioneId> = registrazioni.partiDi(id)

    override fun istantanea(): () -> Unit {
        val copia = righe.toMap()
        return {
            righe.clear()
            righe.putAll(copia)
        }
    }
}
