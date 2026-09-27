package snastro.avvio.r3

import snastro.kernel.Esito
import snastro.sintesi.applicazione.porte.ErroreApplicazioneSintesi
import snastro.sintesi.applicazione.porte.ModelloLinguistico
import snastro.sintesi.applicazione.porte.RichiestaRiassunto
import snastro.sintesi.applicazione.porte.RispostaModello

/**
 * The PLACEHOLDER [ModelloLinguistico] of R3 under [snastro.avvio.r1.SceltaMl.FINTE] (`--smoke`, the gate: no
 * natives, no models; the real adapter is [modelloLinguisticoR3]'s REALI choice): every run answers
 * `ModelloNonDisponibile`, so a claimed Riassunto ends `fallito` `modello_non_disponibile` (ADR 0021 §4).
 */
internal object ModelloLinguisticoNonDisponibile : ModelloLinguistico {
    override fun riassumi(richiesta: RichiestaRiassunto, annullato: () -> Boolean): Esito<RispostaModello> =
        Esito.Errore(ErroreApplicazioneSintesi.ModelloNonDisponibile)
}
