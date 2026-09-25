package snastro.parlanti.adattatori.eventi

import snastro.kernel.AbbonatoSincrono
import snastro.kernel.DispatcherEventiInMemoria
import snastro.kernel.Esito
import snastro.kernel.EventoPubblicato
import snastro.parlanti.applicazione.politiche.ApplicaRevisionePolitica
import snastro.parlanti.applicazione.politiche.ApplicaSostituzioneTrascrittoPolitica
import snastro.progetto.applicazione.eventi.RegistrazioneEliminata
import snastro.trascrizione.applicazione.eventi.SegmentoRiassegnato
import snastro.trascrizione.applicazione.eventi.TrascrittoSostituito
import snastro.trascrizione.applicazione.eventi.VoceDivisa
import snastro.trascrizione.applicazione.eventi.VociUnite

/**
 * `AbbonatoSincrono` (ADR 0012) that applies the structural half of [INV-21]/[INV-25] to every
 * Trascrizione Revisione (block `abbonato-revisione-parlanti`, AC-142/AC-143), and (ADR 0018 §3 +
 * Amendment 2026-09-24 (b) §1, AC-446) the [INV-15]/[INV-25] purge on [TrascrittoSostituito]:
 * translates [VociUnite]/[VoceDivisa]/[SegmentoRiassegnato] 1:1 into an [ApplicaRevisionePolitica]
 * call, and [TrascrittoSostituito] into an [ApplicaSostituzioneTrascrittoPolitica] call — run INSIDE
 * the publishing command's transaction in both cases — an [Esito.Errore] from either policy dooms and
 * rolls back the whole transaction (the [DispatcherEventiInMemoria] rule). ADR 0020 §2 step 4 (AC-621):
 * Progetto's [RegistrazioneEliminata] is translated into the SAME [ApplicaSostituzioneTrascrittoPolitica]
 * call, inside the deleting transaction. `:parlanti:applicazione` may not import Trascrizione's or
 * Progetto's published events (`architecture.md` edges), so this translation lives here, mirroring
 * both policies' own KDoc.
 *
 * Registers itself on [dispatcher] in `init`. This is a plain component: wiring it into the app's
 * composition (registering it at startup, before the first command) is `avvio-parlanti`'s job, not
 * this block's.
 */
public class AbbonatoRevisioneParlanti(
    dispatcher: DispatcherEventiInMemoria,
    private val politica: ApplicaRevisionePolitica,
    private val politicaSostituzione: ApplicaSostituzioneTrascrittoPolitica,
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
        is TrascrittoSostituito -> politicaSostituzione.applica(evento.registrazioneId)
        is RegistrazioneEliminata -> politicaSostituzione.applica(evento.registrazioneId)
        else -> Esito.Ok(Unit)
    }
}
