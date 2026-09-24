package snastro.parlanti.adattatori.eventi

import snastro.kernel.AbbonatoSincrono
import snastro.kernel.DispatcherEventiInMemoria
import snastro.kernel.Esito
import snastro.kernel.EventoPubblicato
import snastro.parlanti.applicazione.politiche.ApplicaRevisionePolitica
import snastro.trascrizione.applicazione.eventi.SegmentoRiassegnato
import snastro.trascrizione.applicazione.eventi.VoceDivisa
import snastro.trascrizione.applicazione.eventi.VociUnite

/**
 * `AbbonatoSincrono` (ADR 0012) that applies the structural half of [INV-21]/[INV-25] to every
 * Trascrizione Revisione (block `abbonato-revisione-parlanti`, AC-142/AC-143): translates
 * [VociUnite]/[VoceDivisa]/[SegmentoRiassegnato] 1:1 into an [ApplicaRevisionePolitica] call, run
 * INSIDE the publishing command's transaction — an [Esito.Errore] from the policy dooms and rolls
 * back the whole Revisione (the [DispatcherEventiInMemoria] rule). `:parlanti:applicazione` may not
 * import Trascrizione's published events (`architecture.md` edges), so this translation lives here,
 * mirroring `ApplicaRevisionePolitica`'s own KDoc.
 *
 * Registers itself on [dispatcher] in `init`. This is a plain component: wiring it into the app's
 * composition (registering it at startup, before the first command) is `avvio-parlanti`'s job, not
 * this block's.
 */
public class AbbonatoRevisioneParlanti(
    dispatcher: DispatcherEventiInMemoria,
    private val politica: ApplicaRevisionePolitica,
) {
    init {
        dispatcher.registraSincrono(AbbonatoSincrono(::ricevi))
    }

    private fun ricevi(evento: EventoPubblicato): Esito<Unit> = when (evento) {
        is VociUnite -> politica.applicaVociUnite(evento.registrazioneId, evento.sopravvissuta, evento.rimossa)
        is VoceDivisa -> politica.applicaVoceDivisa(evento.registrazioneId, evento.origine)
        is SegmentoRiassegnato -> politica.applicaSegmentoRiassegnato(
            evento.registrazioneId,
            evento.da,
            evento.a,
            evento.daRimossa,
            evento.aNuova,
        )
        else -> Esito.Ok(Unit)
    }
}
