package snastro.trascrizione.adattatori.eventi

import snastro.kernel.AbbonatoSincrono
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
 * A plain [AbbonatoSincrono] VALUE (ADR 0030 §1, AC-C67): it never registers itself. `:avvio`'s `ModuloTrascrizione`
 * pairs it with [RegistrazioneEliminata] and the composition root registers it before the first command.
 */
public class AbbonatoEliminazioneRegistrazione(
    private val politica: ApplicaEliminazioneRegistrazionePolitica,
) : AbbonatoSincrono {
    override fun ricevi(evento: EventoPubblicato): Esito<Unit> = when (evento) {
        is RegistrazioneEliminata -> politica.applica(evento.registrazioneId)
        else -> Esito.Ok(Unit)
    }
}
