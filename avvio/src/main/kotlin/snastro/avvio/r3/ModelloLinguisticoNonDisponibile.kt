package snastro.avvio.r3

import snastro.kernel.Esito
import snastro.sintesi.applicazione.porte.ErroreApplicazioneSintesi
import snastro.sintesi.applicazione.porte.ModelloLinguistico
import snastro.sintesi.applicazione.porte.RichiestaRiassunto
import snastro.sintesi.applicazione.porte.RispostaModello

/**
 * The PLACEHOLDER [ModelloLinguistico] of R3 (carry-over 7) until `modello-linguistico-llama` binds the real
 * adapter here: every run answers `ModelloNonDisponibile`, so a claimed Riassunto ends `fallito`
 * `modello_non_disponibile` (ADR 0021 §4) — the tab, the queue and the download UI stay demonstrable.
 */
internal object ModelloLinguisticoNonDisponibile : ModelloLinguistico {
    override fun riassumi(richiesta: RichiestaRiassunto, annullato: () -> Boolean): Esito<RispostaModello> =
        Esito.Errore(ErroreApplicazioneSintesi.ModelloNonDisponibile)
}
