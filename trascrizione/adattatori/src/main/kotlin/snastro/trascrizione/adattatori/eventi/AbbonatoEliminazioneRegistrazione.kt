package snastro.trascrizione.adattatori.eventi

import snastro.kernel.AbbonatoSincrono
import snastro.kernel.DispatcherEventiInMemoria
import snastro.kernel.Esito
import snastro.kernel.EventoPubblicato
import snastro.progetto.applicazione.eventi.RegistrazioneEliminata
import snastro.trascrizione.applicazione.politiche.ApplicaEliminazioneRegistrazionePolitica

/**
 * The Trascrizione synchronous subscriber of [RegistrazioneEliminata] (ADR 0020 §2 step 4, AC-612): translates it into
 * [ApplicaEliminazioneRegistrazionePolitica.applica] INSIDE the deleting transaction, so the policy's veto
 * (`ElaborazioneGiaAperta`) dooms and rolls back the whole `EliminaRegistrazione`. Every other event is ignored.
 * `:trascrizione:applicazione` may not import Progetto's published events, hence this translation lives here.
 *
 * Registers itself on [dispatcher] in `init`; wiring it before the first command is `avvio-parlanti`'s job.
 */
public class AbbonatoEliminazioneRegistrazione(
    dispatcher: DispatcherEventiInMemoria,
    private val politica: ApplicaEliminazioneRegistrazionePolitica,
) {
    init {
        dispatcher.registraSincrono(AbbonatoSincrono(::ricevi))
    }

    private fun ricevi(evento: EventoPubblicato): Esito<Unit> = when (evento) {
        is RegistrazioneEliminata -> politica.applica(evento.registrazioneId)
        else -> Esito.Ok(Unit)
    }
}
