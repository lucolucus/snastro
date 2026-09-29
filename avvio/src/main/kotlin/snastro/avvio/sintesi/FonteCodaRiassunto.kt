package snastro.avvio.sintesi

import snastro.avvio.coda.ElementoInCoda
import snastro.avvio.coda.FonteCoda
import snastro.avvio.coda.RisultatoTentativo
import snastro.avvio.coda.TipoElementoCoda
import snastro.kernel.Esito
import snastro.sintesi.applicazione.comandi.EseguiProssimoRiassunto
import snastro.sintesi.applicazione.comandi.RisultatoRiassunto
import snastro.sintesi.applicazione.letture.RiassuntiInAttesa

/**
 * The Riassunto source of the shared queue (ADR 0023 §1/§2, boundary `riassunti-in-coda`): the wiring-site
 * translation between Sintesi's own [RiassuntiInAttesa] / `EseguiProssimoRiassunto` and the primitive shape
 * [snastro.avvio.coda.CodaCondivisa] speaks.
 * - [FonteCoda.teste] reads [RiassuntiInAttesa.elenco]'s FIFO `(richiestoAlle, id)` excluding `esclusi` — the SAME
 *   "oldest eligible" the claim takes (carry-over 2);
 * - [FonteCoda.prossima] claims through [esegui] (`primaDi` = the Elaborazione head's instant: strict `<`, an
 *   Elaborazione wins an equal millisecond), inside [EsecuzioniRiassunto.perRun] — a fresh cancellation flag per
 *   claim (AC-S161);
 * - [FonteCoda.recupera] is `RecuperaRiassuntiInterrotti` (AC-S145);
 * - never [FonteCoda.trattenuta]: a Riassunto is never held for the LLM model (ADR 0023 §2, Q-S1);
 * - [FonteCoda.interrompi]: [EsecuzioniRiassunto.interrompi] (unconditional); the best-effort cancellation of a
 *   deleted Riassunto is [EsecuzioniRiassunto.annulla], called by `ModuloSintesi` directly (ADR 0030 §1).
 */
internal fun fonteCodaRiassunto(
    elenco: RiassuntiInAttesa,
    esegui: (EseguiProssimoRiassunto) -> Esito<RisultatoRiassunto>,
    recupera: () -> Unit,
    esecuzioni: EsecuzioniRiassunto,
): FonteCoda {
    fun tutti(): List<ElementoInCoda> =
        elenco.elenco().map { ElementoInCoda(it.riassuntoId, it.registrazioneId.valore, it.richiestoAlle) }
    fun testaAttuale(esclusi: Set<String>) = elenco.elenco().firstOrNull { it.riassuntoId !in esclusi }
    return FonteCoda(
        tipo = TipoElementoCoda.RIASSUNTO,
        teste = { esclusi ->
            testaAttuale(esclusi)?.let { ElementoInCoda(it.riassuntoId, it.registrazioneId.valore, it.richiestoAlle) }
        },
        prossima = { esclusi, limite ->
            val esito = esecuzioni.perRun { esegui(EseguiProssimoRiassunto(esclusi, primaDi = limite)) }
            when (esito) {
                is Esito.Ok -> when (val r = esito.valore) {
                    RisultatoRiassunto.Nessuno -> RisultatoTentativo.Nessuno
                    is RisultatoRiassunto.Avviato -> RisultatoTentativo.Avviata(r.id.valore)
                }
                // the claim (or completion) transaction failed: its head (if one was saved) counts toward its
                // exclusion; an Errore reached BEFORE salva(in_corso) leaves ultimoReclamato null — fall back to
                // the CURRENT head itself (A182: otherwise a poison head is never excluded and spins the whole
                // queue forever, contradicting AC-S61).
                is Esito.Errore ->
                    (esecuzioni.ultimoReclamato ?: testaAttuale(esclusi)?.riassuntoId)
                        ?.let(RisultatoTentativo::Rifiutata) ?: RisultatoTentativo.Nessuno
            }
        },
        ultimaTentata = { esecuzioni.ultimoReclamato },
        recupera = recupera,
        trattenuta = { false },
        interrompi = esecuzioni::interrompi,
        tutti = ::tutti, // A124: un'unica lettura (RiassuntiInAttesa.elenco gia' la offre), non N ri-letture
    )
}
