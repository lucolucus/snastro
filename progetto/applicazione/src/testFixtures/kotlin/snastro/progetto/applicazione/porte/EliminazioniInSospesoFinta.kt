package snastro.progetto.applicazione.porte

import snastro.kernel.RegistrazioneId
import snastro.kernel.Ripristinabile

/**
 * In-memory [EliminazioniInSospeso]: [elenco] in insertion order (the real table orders by `eliminata_alle`, then
 * id); rolls back with `UnitaDiLavoroFinta` ([Ripristinabile]).
 */
public class EliminazioniInSospesoFinta : EliminazioniInSospeso, Ripristinabile {
    private val righe = linkedMapOf<RegistrazioneId, EliminazioneInSospeso>()

    override fun registra(e: EliminazioneInSospeso) {
        check(e.registrazioneId !in righe) { "gia in sospeso: ${e.registrazioneId}" } // the PRIMARY KEY
        righe[e.registrazioneId] = e
    }

    override fun elenco(): List<EliminazioneInSospeso> = righe.values.toList()

    override fun concludi(id: RegistrazioneId) {
        righe.remove(id)
    }

    override fun istantanea(): () -> Unit {
        val copia = righe.toMap()
        return {
            righe.clear()
            righe.putAll(copia)
        }
    }
}
