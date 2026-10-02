package snastro.parlanti.adattatori.eventi

import snastro.kernel.AbbonatoSincrono
import snastro.kernel.Esito
import snastro.kernel.EventoPubblicato
import snastro.parlanti.applicazione.politiche.ApplicaRevisionePolitica
import snastro.parlanti.applicazione.politiche.ApplicaSostituzioneTrascrittoPolitica
import snastro.trascrizione.applicazione.eventi.SegmentoRiassegnato
import snastro.trascrizione.applicazione.eventi.TrascrittoEliminato
import snastro.trascrizione.applicazione.eventi.TrascrittoSostituito
import snastro.trascrizione.applicazione.eventi.VoceDivisa
import snastro.trascrizione.applicazione.eventi.VociUnite

/**
 * `AbbonatoSincrono` (ADR 0012) that applies the structural half of [INV-21]/[INV-25] to every
 * Trascrizione Revisione (block `abbonato-revisione-parlanti`, AC-142/AC-143), and (ADR 0018 §3 +
 * Amendment 2026-09-24 (b) §1, AC-446) the [INV-15]/[INV-25] purge on [TrascrittoSostituito]:
 * translates [VociUnite]/[VoceDivisa]/[SegmentoRiassegnato] 1:1 into an [ApplicaRevisionePolitica]
 * call, and [TrascrittoSostituito] / [TrascrittoEliminato] (ADR 0035 §6, [INV-I8b]) into an
 * [ApplicaSostituzioneTrascrittoPolitica] call — run INSIDE
 * the publishing command's transaction in both cases — an [Esito.Errore] from either policy dooms and
 * rolls back the whole transaction (the kernel dispatcher's rule). ADR 0020 §2 step 4 / ADR 0038 §2 (AC-I61):
 * the deleting unit's purge arrives as [TrascrittoEliminato] (nested, synchronous); Parlanti subscribes to NO
 * `RegistrazioneEliminata` (it never knew which Voci a non-last Parte's removal ends).
 * `:parlanti:applicazione` may not import Trascrizione's (`architecture.md` edges), so this translation
 * lives here, mirroring both policies' own KDoc.
 *
 * A plain [AbbonatoSincrono] VALUE (ADR 0030 §1, AC-C67): it never registers itself. The composition root
 * (`:avvio`'s `ModuloParlanti`) pairs it with each event type it handles and registers it, in the declared
 * order, before the first command.
 */
public class AbbonatoRevisioneParlanti(
    private val politica: ApplicaRevisionePolitica,
    private val politicaSostituzione: ApplicaSostituzioneTrascrittoPolitica,
) : AbbonatoSincrono {
    override fun ricevi(evento: EventoPubblicato): Esito<Unit> = when (evento) {
        // ADR 0035 §5: the Revisione events and TrascrittoSostituito carry the Incontro of their Voci.
        is VociUnite -> politica.applicaVociUnite(evento.incontroId, evento.sopravvissuta, evento.rimossa)
        is VoceDivisa -> politica.applicaVoceDivisa(evento.incontroId, evento.origine)
        is SegmentoRiassegnato ->
            politica.applicaSegmentoRiassegnato(evento.incontroId, evento.da, evento.a, evento.daRimossa, evento.aNuova)
        is TrascrittoSostituito ->
            politicaSostituzione.applica(evento.registrazioneId, evento.incontroId, evento.vociRimosse)
        is TrascrittoEliminato ->
            politicaSostituzione.applica(evento.registrazioneId, evento.incontroId, evento.vociRimosse)
        else -> Esito.Ok(Unit)
    }
}
