package snastro.sintesi.applicazione.porte

import snastro.kernel.Esito

/**
 * Technical port to the local LLM, runtime-neutral (boundary `tec-modello-linguistico`, ADR 0021 §4).
 * - **Blocking.** Never called inside a `UnitaDiLavoro` transaction (ADR 0012, ADR 0023 §5).
 * - The answer is raw and UNVERIFIED: every speaker is written `{V<n>}`; `fonti` / `responsabile` /
 *   `parlante` are segmentoId / voceId numbers whose validity the root checks ([INV-S4]), not this port.
 * - Once [annullato] returns true, or the calling thread is interrupted, it returns
 *   `Errore(ErroreApplicazioneSintesi.Annullato)` within a bounded time.
 * - Failures are [ErroreApplicazioneSintesi] values.
 */
public interface ModelloLinguistico {
    public fun riassumi(richiesta: RichiestaRiassunto, annullato: () -> Boolean): Esito<RispostaModello>
}
