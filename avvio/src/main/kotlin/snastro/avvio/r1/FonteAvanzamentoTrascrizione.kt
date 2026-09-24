package snastro.avvio.r1

import snastro.avvio.FonteAvanzamento
import snastro.avvio.RisultatoTentativo
import snastro.kernel.ElaborazioneId
import snastro.kernel.Esito
import snastro.trascrizione.applicazione.comandi.EseguiProssimaElaborazione
import snastro.trascrizione.applicazione.comandi.EseguiProssimaElaborazioneServizio
import snastro.trascrizione.applicazione.comandi.RisultatoAvanzamento

/**
 * The wiring-site translation between Trascrizione's [EseguiProssimaElaborazioneServizio] and the
 * Published-Language shape [snastro.avvio.CodaElaborazioni] speaks (primitive ids, no
 * `:trascrizione` type): `esclusi` → [ElaborazioneId]s in, [RisultatoAvanzamento] →
 * [RisultatoTentativo] out ([tentativoDi]); `ultimaTentata` read off the service after an escape.
 */
internal fun fonteAvanzamento(servizio: EseguiProssimaElaborazioneServizio): FonteAvanzamento = FonteAvanzamento(
    prossima = { esclusi ->
        val esito = servizio.esegui(EseguiProssimaElaborazione(esclusi.map(::ElaborazioneId).toSet()))
        tentativoDi(esito, servizio.ultimaTentata?.valore)
    },
    ultimaTentata = { servizio.ultimaTentata?.valore },
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
