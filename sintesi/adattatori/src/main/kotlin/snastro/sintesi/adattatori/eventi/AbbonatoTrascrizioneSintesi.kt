package snastro.sintesi.adattatori.eventi

import snastro.kernel.AbbonatoSincrono
import snastro.kernel.DispatcherEventiInMemoria
import snastro.kernel.Esito
import snastro.kernel.EventoPubblicato
import snastro.sintesi.applicazione.politiche.ApplicaSostituzioneTrascrittoSintesiPolitica
import snastro.trascrizione.applicazione.eventi.TrascrittoSostituito

/**
 * The Sintesi synchronous subscriber of Trascrizione's `TrascrittoSostituito` (ADR 0018 §5 amended by
 * ADR 0021 §3/§6, AC-S115/AC-S116): translates it into
 * [ApplicaSostituzioneTrascrittoSintesiPolitica.applica] INSIDE the completion transaction that
 * replaced the Trascritto — the same transaction ADR 0018 §2 already used to `salva` the new
 * Trascritto BEFORE publishing this event, which is what the policy relies on to read the NEW
 * generation, never the one being replaced. An [Esito.Errore] from the policy dooms and rolls the
 * whole completion back (`DispatcherEventiInMemoria`'s rule; ADR 0018 §6 then compensates it to
 * `fallita`). Every other event is ignored (`Esito.Ok(Unit)`, no policy call).
 *
 * `:sintesi:applicazione` may not import Trascrizione's published events (`architecture.md` /
 * ADR 0021 §2 edges), so this translation lives here, mirroring Parlanti's own
 * `AbbonatoRevisioneParlanti` / Trascrizione's own `AbbonatoEliminazioneRegistrazione`.
 *
 * Registers itself on [dispatcher] in `init`; wiring it into the app's composition — before the first
 * command — is `avvio-sintesi`'s job (ADR 0021 §10), not this block's.
 */
public class AbbonatoTrascrizioneSintesi(
    dispatcher: DispatcherEventiInMemoria,
    private val politica: ApplicaSostituzioneTrascrittoSintesiPolitica,
) {
    init {
        dispatcher.registraSincrono(AbbonatoSincrono(::ricevi))
    }

    private fun ricevi(evento: EventoPubblicato): Esito<Unit> = when (evento) {
        is TrascrittoSostituito -> politica.applica(evento.registrazioneId)
        else -> Esito.Ok(Unit)
    }
}
