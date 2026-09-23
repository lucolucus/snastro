package snastro.progetto.applicazione.porte

import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.Ripristinabile
import snastro.progetto.dominio.Registrazione

/**
 * In-memory [RegistrazioneRepository]; rolls back with `UnitaDiLavoroFinta` ([Ripristinabile]).
 * Like a database it stores a copy: changes made to an aggregate after [salva] are not persisted.
 */
public class RegistrazioneRepositoryFinta : RegistrazioneRepository, Ripristinabile {
    private val righe = linkedMapOf<RegistrazioneId, Registrazione>()

    override fun trova(id: RegistrazioneId): Registrazione? = righe[id]?.copia()

    override fun delProgetto(id: ProgettoId): List<Registrazione> =
        righe.values.filter { it.progettoId == id }.map { it.copia() }

    override fun salva(r: Registrazione) {
        righe[r.id] = r.copia()
    }

    override fun istantanea(): () -> Unit {
        val copia = righe.toMap()
        return {
            righe.clear()
            righe.putAll(copia)
        }
    }

    // `ricostituisci` is reserved to persistence adapters (CR-15): the public factory rebuilds the
    // same observable state and its event is dropped.
    private fun Registrazione.copia(): Registrazione =
        Registrazione.aggiungi(id, progettoId, titolo, riferimentoAudio, durataMs, dataRegistrazione, aggiuntaAlle)
            .aggregato
}
