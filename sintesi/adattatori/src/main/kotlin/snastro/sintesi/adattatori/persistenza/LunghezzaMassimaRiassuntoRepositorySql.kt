package snastro.sintesi.adattatori.persistenza

import snastro.kernel.Esito
import snastro.kernel.ProgettoId
import snastro.kernel.RicostituzioneDaPersistenza
import snastro.persistenza.SnastroDatabase
import snastro.sintesi.applicazione.porte.LunghezzaMassimaRiassuntoRepository
import snastro.sintesi.dominio.LunghezzaMassimaParole
import snastro.sintesi.dominio.LunghezzaMassimaRiassunto

/**
 * [LunghezzaMassimaRiassuntoRepository] on the generated [SnastroDatabase] queries
 * (dev-architecture-app.md#repository, ADR 0022 §2): only `impostazioniSintesiQueries`
 * (ADR 0021 §8, AC-S114). `impostazioni_sintesi` has no owned child rows to replace: [salva] is a
 * single-row `INSERT OR REPLACE` (`sostituisci`, mirroring `ImprontaVocale.sq`'s own upsert), always
 * inside the caller's transaction, never opening one (ADR 0012).
 */
public class LunghezzaMassimaRiassuntoRepositorySql(private val db: SnastroDatabase) :
    LunghezzaMassimaRiassuntoRepository {
    @OptIn(RicostituzioneDaPersistenza::class)
    override fun trova(p: ProgettoId): LunghezzaMassimaRiassunto {
        val riga = db.impostazioniSintesiQueries.trova(p.valore).executeAsOneOrNull()
            ?: return LunghezzaMassimaRiassunto.predefinita(p)
        val valore = riga.lunghezza_massima_riassunto_parole
        val parole = LunghezzaMassimaParole.di(valore.toInt()).dalDatabase("parole fuori intervallo: $valore")
        return LunghezzaMassimaRiassunto.ricostituisci(p, parole)
    }

    override fun salva(l: LunghezzaMassimaRiassunto): Esito<Unit> {
        db.impostazioniSintesiQueries.sostituisci(
            progettoId = l.progettoId.valore,
            lunghezzaMassimaRiassuntoParole = l.parole.valore.toLong(),
        )
        return Esito.Ok(Unit)
    }
}
