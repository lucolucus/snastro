package snastro.progetto.adattatori.persistenza

import snastro.kernel.ProgettoId
import snastro.kernel.RicostituzioneDaPersistenza
import snastro.persistenza.SnastroDatabase
import snastro.progetto.applicazione.porte.ProgettoRepository
import snastro.progetto.dominio.Progetto
import migrations.Progetto as ProgettoRiga

/**
 * [ProgettoRepository] on the generated [SnastroDatabase] queries (dev-architecture-app.md#repository):
 * one row, `progetto` has no own id-based upsert query, so [salva] reads the current row first to
 * decide insert vs. update — the same "one Progetto per project database" rule
 * [snastro.progetto.applicazione.porte.ProgettoRepositoryFinta] enforces in memory. Never opens its
 * own transaction: the caller's [snastro.kernel.UnitaDiLavoro] does (ADR 0012).
 */
public class ProgettoRepositorySql(private val db: SnastroDatabase) : ProgettoRepository {
    override fun trova(): Progetto? = db.progettoQueries.trova().executeAsOneOrNull()?.inDominio()

    override fun salva(p: Progetto) {
        val esistente = db.progettoQueries.trova().executeAsOneOrNull()
        when {
            esistente == null -> db.progettoQueries.inserisci(p.id.valore, p.nome.valore)
            esistente.id == p.id.valore -> db.progettoQueries.aggiorna(p.nome.valore, p.id.valore)
            else -> error(
                "il database ha gia il Progetto ${esistente.id}: non puo salvarne un altro (${p.id.valore})",
            )
        }
    }
}

/** The database is trusted, nothing is re-validated (CR-15). */
@OptIn(RicostituzioneDaPersistenza::class)
private fun ProgettoRiga.inDominio(): Progetto = Progetto.ricostituisci(ProgettoId(id), nome)
