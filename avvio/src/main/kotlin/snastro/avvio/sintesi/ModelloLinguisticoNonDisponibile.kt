package snastro.avvio.sintesi

import snastro.kernel.Esito
import snastro.sintesi.applicazione.porte.ErroreApplicazioneSintesi
import snastro.sintesi.applicazione.porte.ModelloLinguistico
import snastro.sintesi.applicazione.porte.RichiestaRiassunto
import snastro.sintesi.applicazione.porte.RispostaModello

/**
 * The PLACEHOLDER [ModelloLinguistico] under [snastro.avvio.trascrizione.SceltaMl.FINTE] (`--smoke`, the gate: no
 * natives, no models; the real adapter is [modelloLinguistico]'s REALI choice): every run answers
 * `ModelloNonDisponibile`, so a claimed Riassunto ends `fallito` `modello_non_disponibile` (ADR 0021 §4).
 */
internal object ModelloLinguisticoNonDisponibile : ModelloLinguistico {
    override fun riassumi(richiesta: RichiestaRiassunto, annullato: () -> Boolean): Esito<RispostaModello> =
        Esito.Errore(ErroreApplicazioneSintesi.ModelloNonDisponibile)
}
