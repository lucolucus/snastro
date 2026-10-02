package snastro.progetto.applicazione.porte

import snastro.kernel.IncontroId
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

    /**
     * The Parti of the Incontro [id], for [IncontroRepositoryFinta.partiDi] (one state, two ports): in REVERSE
     * insertion order, so a caller that relies on the unguaranteed order fails here too.
     */
    public fun partiDi(id: IncontroId): List<RegistrazioneId> =
        righe.values.filter { it.incontroId == id }.map { it.id }.asReversed()

    override fun titoliDelProgetto(id: ProgettoId): List<String> =
        righe.values.filter { it.progettoId == id }.map { it.titolo }

    override fun salva(r: Registrazione) {
        righe[r.id] = r.copia()
    }

    override fun rimuovi(id: RegistrazioneId) {
        righe.remove(id)
    }

    override fun istantanea(): () -> Unit {
        val copia = righe.toMap()
        return {
            righe.clear()
            righe.putAll(copia)
        }
    }

    // `ricostituisci` is reserved to persistence adapters (CR-15): the public factory rebuilds the
    // same observable state (`aggiuntaAlle` is already to the millisecond, like the stored epoch millis) and its
    // event is dropped.
    private fun Registrazione.copia(): Registrazione =
        Registrazione.aggiungi(
            id,
            progettoId,
            incontroId,
            titolo,
            riferimentoAudio,
            durataMs,
            dataRegistrazione,
            aggiuntaAlle,
            oraDiInizio,
        ).aggregato
}
