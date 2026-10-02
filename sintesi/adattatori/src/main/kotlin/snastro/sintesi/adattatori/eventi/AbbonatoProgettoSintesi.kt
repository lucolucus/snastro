package snastro.sintesi.adattatori.eventi

import snastro.kernel.AbbonatoSincrono
import snastro.kernel.Esito
import snastro.kernel.EventoPubblicato
import snastro.progetto.applicazione.eventi.RegistrazioneEliminata
import snastro.sintesi.applicazione.politiche.ApplicaEliminazioneRegistrazioneSintesiPolitica

/**
 * The Sintesi synchronous subscriber of Progetto's [RegistrazioneEliminata] (ADR 0020 §2 step 4, amended by
 * ADR 0024 §1, AC-S117/AC-S118): translates it into
 * [ApplicaEliminazioneRegistrazioneSintesiPolitica.applica] INSIDE `EliminaRegistrazione`'s deleting transaction,
 * BEFORE the Trascrizione (`AbbonatoEliminazioneRegistrazione`) and Parlanti (`AbbonatoRevisioneParlanti`)
 * subscribers of that same step (ADR 0030 §2: Sintesi → Parlanti → Trascrizione). The policy never vetoes
 * (ADR 0024 §1: an `in_corso` Riassunto is left to finish into its own compare-and-set, which finds no row);
 * a repository [Esito.Errore] (an infrastructure fault) is the only way it dooms — returned unchanged, so the
 * whole `EliminaRegistrazione` rolls back (ADR 0012). Every other event is ignored (`Esito.Ok(Unit)`, no policy call).
 *
 * `:sintesi:applicazione` may not import Progetto's published events (`architecture.md` / ADR 0021 §2-3 edges),
 * so this translation lives here, mirroring Trascrizione's own `AbbonatoEliminazioneRegistrazione`.
 *
 * A plain [AbbonatoSincrono] VALUE (ADR 0030 §1, AC-C67): it never registers itself. `:avvio`'s `ModuloSintesi`
 * pairs it with [RegistrazioneEliminata] and the composition root registers it — before the first command,
 * alongside the other two synchronous subscribers of ADR 0020 §2 step 4, in the declared order (ADR 0024 §4).
 */
public class AbbonatoProgettoSintesi(
    private val politica: ApplicaEliminazioneRegistrazioneSintesiPolitica,
) : AbbonatoSincrono {
    override fun ricevi(evento: EventoPubblicato): Esito<Unit> = when (evento) {
        // ADR 0033 §4.1, ADR 0038 §1: the Riassunti are the Incontro's, and the event carries it — no lookup that
        // could miss and silently skip a ceased Incontro's purge.
        is RegistrazioneEliminata -> politica.applica(evento.incontroId, evento.incontroCessato)
        else -> Esito.Ok(Unit)
    }
}
