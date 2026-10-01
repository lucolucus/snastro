package snastro.sintesi.applicazione.politiche

import snastro.kernel.DispatcherEventi
import snastro.kernel.Esito
import snastro.kernel.IncontroId
import snastro.kernel.poi
import snastro.sintesi.applicazione.eventi.RiassuntoEliminato
import snastro.sintesi.applicazione.porte.RiassuntoRepository

/**
 * Policy `ApplicaEliminazioneRegistrazioneSintesi` — the Sintesi half of [INV-28] (ADR 0020 §7, amended by
 * ADR 0024 §1-2), run SYNCHRONOUSLY inside `EliminaRegistrazione`'s deleting transaction, after step 4's
 * Trascrizione/Parlanti subscribers. `abbonato-progetto-sintesi` (`:sintesi:adattatori ..eventi`) subscribes to the
 * published `RegistrazioneEliminata` and calls [applica]; this class never imports it
 * (`:sintesi:applicazione` may not depend on `:progetto:applicazione`).
 *
 * [INV-S8]: every `Riassunto` of the Registrazione, in any state (`in_attesa`, `in_corso`, `pronto`, `fallito`),
 * is removed with its elements and Fonti. **It never vetoes**: an `in_corso` run is left to finish into the
 * compare-and-set of [RiassuntoRepository.concludi], which will find no row and write nothing (ADR 0022 §4). A
 * repository [Esito.Errore] (an infrastructure fault) is returned unchanged, so the deleting transaction rolls
 * back — the only way this policy can doom the command.
 *
 * `RiassuntoEliminato` is published iff at least one row was removed. Structural only: it never references
 * `ModelloLinguistico`, never decodes nor extracts (ADR 0012 Amendment (d) pattern).
 */
public class ApplicaEliminazioneRegistrazioneSintesiPolitica(
    private val riassunti: RiassuntoRepository,
    private val eventi: DispatcherEventi,
) {
    /**
     * [incontroId] is the Incontro of the deleted Registrazione, resolved by the caller (ADR 0033 §4.1). TRANSITION:
     * every Riassunto of the Incontro goes, exact while every Incontro has one Parte (its deletion ends the Incontro,
     * ADR 0038); the non-last-Parte rule (D-0003) comes with eliminazione-parte-sintesi (wave 4).
     */
    public fun applica(incontroId: IncontroId): Esito<Unit> =
        riassunti.rimuoviDiIncontro(incontroId).poi { rimossi ->
            if (rimossi > 0) eventi.pubblica(RiassuntoEliminato(incontroId))
            Esito.Ok(Unit)
        }
}
