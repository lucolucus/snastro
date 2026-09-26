package snastro.sintesi.adattatori.eventi

import snastro.kernel.AbbonatoSincrono
import snastro.kernel.DispatcherEventiInMemoria
import snastro.kernel.Esito
import snastro.kernel.EventoPubblicato
import snastro.progetto.applicazione.eventi.RegistrazioneEliminata
import snastro.sintesi.applicazione.politiche.ApplicaEliminazioneRegistrazioneSintesiPolitica

/**
 * The Sintesi synchronous subscriber of Progetto's [RegistrazioneEliminata] (ADR 0020 §2 step 4, amended by
 * ADR 0024 §1, AC-S117/AC-S118): translates it into
 * [ApplicaEliminazioneRegistrazioneSintesiPolitica.applica] INSIDE `EliminaRegistrazione`'s deleting transaction,
 * after the Trascrizione (`AbbonatoEliminazioneRegistrazione`) and Parlanti (`AbbonatoRevisioneParlanti`)
 * subscribers of that same step. The policy never vetoes (ADR 0024 §1: an `in_corso` Riassunto is left to finish
 * into its own compare-and-set, which finds no row); a repository [Esito.Errore] (an infrastructure fault) is the
 * only way it dooms — returned unchanged, so the whole `EliminaRegistrazione` rolls back (ADR 0012). Every other
 * event is ignored (`Esito.Ok(Unit)`, no policy call).
 *
 * `:sintesi:applicazione` may not import Progetto's published events (`architecture.md` / ADR 0021 §2-3 edges),
 * so this translation lives here, mirroring Trascrizione's own `AbbonatoEliminazioneRegistrazione` and Sintesi's
 * own `AbbonatoTrascrizioneSintesi`.
 *
 * Registers itself on [dispatcher] in `init`; wiring it into the R3 composition — before the first command,
 * alongside the other two synchronous subscribers of ADR 0020 §2 step 4 (ADR 0024 §4) — is `avvio-sintesi`'s
 * job, not this block's.
 */
public class AbbonatoProgettoSintesi(
    dispatcher: DispatcherEventiInMemoria,
    private val politica: ApplicaEliminazioneRegistrazioneSintesiPolitica,
) {
    init {
        dispatcher.registraSincrono(AbbonatoSincrono(::ricevi))
    }

    private fun ricevi(evento: EventoPubblicato): Esito<Unit> = when (evento) {
        is RegistrazioneEliminata -> politica.applica(evento.registrazioneId)
        else -> Esito.Ok(Unit)
    }
}
