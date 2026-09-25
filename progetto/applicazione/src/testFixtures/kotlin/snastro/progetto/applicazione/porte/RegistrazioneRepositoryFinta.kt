package snastro.progetto.applicazione.porte

import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.Ripristinabile
import snastro.progetto.dominio.Registrazione
import java.time.Instant

/**
 * In-memory [RegistrazioneRepository]; rolls back with `UnitaDiLavoroFinta` ([Ripristinabile]).
 * Like a database it stores a copy: changes made to an aggregate after [salva] are not persisted.
 */
public class RegistrazioneRepositoryFinta : RegistrazioneRepository, Ripristinabile {
    private val righe = linkedMapOf<RegistrazioneId, Registrazione>()

    override fun trova(id: RegistrazioneId): Registrazione? = righe[id]?.copia()

    override fun delProgetto(id: ProgettoId): List<Registrazione> =
        righe.values.filter { it.progettoId == id }.map { it.copia() }

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
    // same observable state and its event is dropped.
    // `aggiuntaAlle` is floored to the millisecond (L496a): the real repository stores epoch millis,
    // so the fake mirrors that round-trip instead of silently keeping a precision no adapter offers.
    private fun Registrazione.copia(): Registrazione =
        Registrazione.aggiungi(
            id,
            progettoId,
            titolo,
            riferimentoAudio,
            durataMs,
            dataRegistrazione,
            Instant.ofEpochMilli(aggiuntaAlle.toEpochMilli()),
        ).aggregato
}
