package snastro.avvio.r3

import snastro.avvio.ElementoInCoda
import snastro.avvio.FonteCoda
import snastro.avvio.RisultatoTentativo
import snastro.avvio.TipoElementoCoda
import snastro.kernel.Esito
import snastro.sintesi.applicazione.comandi.EseguiProssimoRiassunto
import snastro.sintesi.applicazione.comandi.RisultatoRiassunto
import snastro.sintesi.applicazione.letture.RiassuntiInAttesa

/**
 * The Riassunto source of the shared queue (ADR 0023 §1/§2, boundary `riassunti-in-coda`): the wiring-site
 * translation between Sintesi's own [RiassuntiInAttesa] / `EseguiProssimoRiassunto` and the primitive shape
 * [snastro.avvio.CodaCondivisa] speaks.
 * - [FonteCoda.teste] reads [RiassuntiInAttesa.elenco]'s FIFO `(richiestoAlle, id)` excluding `esclusi` — the SAME
 *   "oldest eligible" the claim takes (carry-over 2);
 * - [FonteCoda.prossima] claims through [esegui] (`primaDi` = the Elaborazione head's instant: strict `<`, an
 *   Elaborazione wins an equal millisecond), inside [EsecuzioniRiassunto.perRun] — a fresh cancellation flag per
 *   claim (AC-S161);
 * - [FonteCoda.recupera] is `RecuperaRiassuntiInterrotti` (AC-S145);
 * - never [FonteCoda.trattenuta]: a Riassunto is never held for the LLM model (ADR 0023 §2, Q-S1);
 * - [FonteCoda.annulla] / [FonteCoda.interrompi]: [EsecuzioniRiassunto.annulla] (guarded) /
 *   [EsecuzioniRiassunto.interrompi] (unconditional).
 */
internal fun fonteCodaRiassunto(
    elenco: RiassuntiInAttesa,
    esegui: (EseguiProssimoRiassunto) -> Esito<RisultatoRiassunto>,
    recupera: () -> Unit,
    esecuzioni: EsecuzioniRiassunto,
): FonteCoda = FonteCoda(
    tipo = TipoElementoCoda.RIASSUNTO,
    teste = { esclusi ->
        elenco.elenco().firstOrNull { it.riassuntoId !in esclusi }
            ?.let { ElementoInCoda(it.riassuntoId, it.registrazioneId.valore, it.richiestoAlle) }
    },
    prossima = { esclusi, limite ->
        val esito = esecuzioni.perRun { esegui(EseguiProssimoRiassunto(esclusi, primaDi = limite)) }
        when (esito) {
            is Esito.Ok -> when (val r = esito.valore) {
                RisultatoRiassunto.Nessuno -> RisultatoTentativo.Nessuno
                is RisultatoRiassunto.Avviato -> RisultatoTentativo.Avviata(r.id.valore)
            }
            // the claim transaction failed: its head (if one was saved) counts toward its exclusion
            is Esito.Errore ->
                esecuzioni.ultimoReclamato?.let(RisultatoTentativo::Rifiutata) ?: RisultatoTentativo.Nessuno
        }
    },
    ultimaTentata = { esecuzioni.ultimoReclamato },
    recupera = recupera,
    trattenuta = { false },
    annulla = { _ -> esecuzioni.annulla() },
    interrompi = esecuzioni::interrompi,
)
