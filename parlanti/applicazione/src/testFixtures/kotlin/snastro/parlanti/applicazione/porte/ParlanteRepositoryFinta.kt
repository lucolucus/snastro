package snastro.parlanti.applicazione.porte

import snastro.kernel.Esito
import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId
import snastro.kernel.Ripristinabile
import snastro.parlanti.dominio.ErroreParlanti
import snastro.parlanti.dominio.Impronta
import snastro.parlanti.dominio.Nome
import snastro.parlanti.dominio.Parlante

/**
 * In-memory [ParlanteRepository]: refuses a second `attivo` normalized [Nome] per Progetto like the partial
 * unique index `parlante_nome_attivo_unico` (INV-16, ADR 0007). Stores and returns private copies (an
 * [Impronta] is immutable), so no caller ever aliases the stored state. [Ripristinabile]: pass it to `UnitaDiLavoroFinta`.
 */
public class ParlanteRepositoryFinta : ParlanteRepository, Ripristinabile {
    private val righe = LinkedHashMap<ParlanteId, Parlante>()

    override fun trova(id: ParlanteId): Parlante? = righe[id]?.copia()

    override fun delProgetto(id: ProgettoId): List<Parlante> =
        righe.values.filter { it.progettoId == id }.map { it.copia() }

    override fun nomeAttivoInUso(progettoId: ProgettoId, nome: Nome, escluso: ParlanteId?): Boolean =
        righe.values.any {
            it.id != escluso && it.progettoId == progettoId && it.attivo && it.nome.normalizzato == nome.normalizzato
        }

    override fun salva(p: Parlante): Esito<Unit> {
        if (p.attivo && nomeAttivoInUso(p.progettoId, p.nome, escluso = p.id)) {
            return Esito.Errore(ErroreParlanti.NomeGiaInUso(p.nome.valore))
        }
        righe[p.id] = p.copia()
        return Esito.Ok(Unit)
    }

    override fun rimuovi(id: ParlanteId) {
        righe.remove(id)
    }

    /** Stored print rows of [id] (the `impronta_vocale` count of the SQL store); 0 if absent. */
    public fun righeImpronte(id: ParlanteId): Int = righe[id]?.impronte?.size ?: 0

    override fun istantanea(): () -> Unit {
        val salvate = LinkedHashMap(righe)
        return {
            righe.clear()
            righe.putAll(salvate)
        }
    }

    /** A copy rebuilt through the aggregate's own API (reconstitution is reserved to persistence adapters, CR-15). */
    private fun Parlante.copia(): Parlante {
        val copia = Parlante.crea(id, progettoId, nome, tipo).aggregato
        impronte.forEach {
            check(copia.registraImpronta(it.voceRef, it.impronta) is Esito.Ok)
        }
        if (eliminato) check(copia.elimina() is Esito.Ok)
        return copia
    }
}
