package snastro.avvio.trascrizione

import snastro.avvio.coda.ElementoInCoda
import snastro.avvio.coda.FonteCoda
import snastro.avvio.coda.RisultatoTentativo
import snastro.avvio.coda.TipoElementoCoda
import snastro.kernel.ElaborazioneId
import snastro.kernel.Esito
import snastro.trascrizione.applicazione.comandi.EseguiProssimaElaborazione
import snastro.trascrizione.applicazione.comandi.EseguiProssimaElaborazioneServizio
import snastro.trascrizione.applicazione.comandi.RisultatoAvanzamento
import snastro.trascrizione.applicazione.letture.ElaborazioniInAttesa

/**
 * The wiring-site translation between Trascrizione's own sources (`elaborazioni-in-coda`'s
 * [ElaborazioniInAttesa], [EseguiProssimaElaborazioneServizio]) and the Published-Language shape
 * [snastro.avvio.coda.CodaCondivisa] speaks (primitive ids, no `:trascrizione` type):
 * - [teste] reads [ElaborazioniInAttesa.elenco]'s own FIFO order (`creataAlle`, ties by id),
 *   excluding [esclusi] — the SAME "oldest eligible" contract [prossima] claims against;
 * - [prossima] claims through [servizio], mapping `esclusi` to [ElaborazioneId]s and [limite] to
 *   `nonDopo` (ADR 0023 §2: the OTHER source's head instant; `null` when it has none — AC-S20/AC-S55). A head
 *   rejected by the `nonDopo` bound maps to [RisultatoAvanzamento.NessunElemento] exactly like an empty queue
 *   (carry-over 1): the shared queue's own [snastro.avvio.coda.CodaCondivisa] is what re-ticks immediately instead
 *   of treating that as drained — this wiring stays a plain, honest translation of what the servizio reports;
 * - [trattenuta] holds the WHOLE shared queue while [modelliPronti] answers `false` (AC-235/AC-S60 —
 *   a Riassunto source is never held this way, ADR 0023 §2);
 * - [recupera] is `RecuperaElaborazioniInterrotte` (its failure logged by `ModuloTrascrizione`), run by the queue
 *   before any claim (AC-233).
 */
internal fun fonteCodaElaborazione(
    servizio: EseguiProssimaElaborazioneServizio,
    elenco: ElaborazioniInAttesa,
    recupera: () -> Unit,
    modelliPronti: () -> Boolean,
): FonteCoda = FonteCoda(
    tipo = TipoElementoCoda.ELABORAZIONE,
    teste = { esclusi ->
        elenco.elenco().firstOrNull { it.elaborazioneId !in esclusi }
            ?.let { ElementoInCoda(it.elaborazioneId, it.registrazioneId.valore, it.creataAlle) }
    },
    prossima = { esclusi, limite ->
        val esito = servizio.esegui(EseguiProssimaElaborazione(esclusi.map(::ElaborazioneId).toSet(), nonDopo = limite))
        tentativoDi(esito, servizio.ultimaTentata?.valore)
    },
    ultimaTentata = { servizio.ultimaTentata?.valore },
    recupera = recupera,
    trattenuta = { !modelliPronti() },
)

/**
 * One [RisultatoAvanzamento] → one [RisultatoTentativo], variant by variant. `esegui` never returns
 * an `Esito.Errore` (a refusal is `AvvioRifiutato`, its own KDoc); should it ever, it is treated as a
 * refusal of the head it had picked ([ultimaTentata]) — so it counts toward that head's exclusion —
 * or as "nothing eligible" when no head was picked.
 */
internal fun tentativoDi(esito: Esito<RisultatoAvanzamento>, ultimaTentata: String?): RisultatoTentativo =
    when (esito) {
        is Esito.Ok -> when (val r = esito.valore) {
            RisultatoAvanzamento.NessunElemento -> RisultatoTentativo.Nessuno
            is RisultatoAvanzamento.Avviata -> RisultatoTentativo.Avviata(r.id.valore)
            is RisultatoAvanzamento.AvvioRifiutato -> RisultatoTentativo.Rifiutata(r.id.valore)
        }
        is Esito.Errore -> ultimaTentata?.let(RisultatoTentativo::Rifiutata) ?: RisultatoTentativo.Nessuno
    }
