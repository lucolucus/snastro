package snastro.trascrizione.applicazione.politiche

import snastro.kernel.DispatcherEventi
import snastro.kernel.Esito
import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.kernel.poi
import snastro.trascrizione.applicazione.eventi.TrascrittoEliminato
import snastro.trascrizione.applicazione.porte.ElaborazioneRepository
import snastro.trascrizione.applicazione.porte.VociDellIncontroRepository
import snastro.trascrizione.dominio.ErroreTrascrizione

/**
 * Policy `ApplicaEliminazioneRegistrazione` — the Trascrizione half of [INV-28] (ADR 0020 §2 step 4), run
 * SYNCHRONOUSLY inside the deleting transaction of `EliminaRegistrazione`. It never imports `RegistrazioneEliminata`
 * (`:trascrizione:applicazione` may not depend on `:progetto:applicazione`): `abbonato-eliminazione-trascrizione`
 * (`:trascrizione:adattatori`) subscribes to it and calls [applica].
 *
 * It re-reads the Elaborazioni INSIDE the transaction: any `aperta` one (the aggregate's predicate) vetoes the
 * deletion with `ElaborazioneGiaAperta`, removing nothing — the deletion never cancels a queued Elaborazione
 * [user default]. Otherwise EVERY Elaborazione of the Parte goes first, history included, then the Parte leaves the
 * Voci dell'Incontro (`VociDellIncontro.rimuoviParte`, INV-I6), in ADR 0038 §2's order. The root itself is removed
 * only when the Incontro ceases with this Parte, its last one (ADR 0035 §1: the counter survives every other removal,
 * INV-I4) — the caller passes `RegistrazioneEliminata.incontroCessato`, minted by `elimina-parte`. When the Parte had
 * a Trascritto, [TrascrittoEliminato] is published inside the unit (ADR 0038 §2, INV-I6), after the root is written
 * (or removed) and before Progetto removes the rows (ADR 0020 §2). Structural only: it never decodes nor extracts
 * (ADR 0012 Amendment (d)).
 */
public class ApplicaEliminazioneRegistrazionePolitica(
    private val elaborazioni: ElaborazioneRepository,
    private val trascritti: VociDellIncontroRepository,
    private val eventi: DispatcherEventi,
) {
    /** [incontroId] / [incontroCessato] are those of `RegistrazioneEliminata` (ADR 0038). */
    public fun applica(
        registrazioneId: RegistrazioneId,
        incontroId: IncontroId,
        incontroCessato: Boolean,
    ): Esito<Unit> {
        val diRegistrazione = elaborazioni.diRegistrazione(registrazioneId)
        if (diRegistrazione.any { it.aperta }) {
            return Esito.Errore(ErroreTrascrizione.ElaborazioneGiaAperta(registrazioneId))
        }
        // INV-5: a Trascritto exists only with a completata Elaborazione — none at all, nothing more to remove.
        if (diRegistrazione.isNotEmpty()) elaborazioni.rimuoviDiRegistrazione(registrazioneId)
        val radice = trascritti.trova(incontroId)
        // haParte first: the refusal of rimuoviParte is not expected, but it travels as its named error, never a cast.
        val rimozione = radice?.takeIf { it.haParte(registrazioneId) }?.rimuoviParte(registrazioneId) ?: Esito.Ok(null)
        return rimozione.poi { vociRimosse ->
            if (radice != null && incontroCessato) {
                trascritti.rimuovi(incontroId)
            } else if (radice != null && vociRimosse != null) {
                trascritti.salva(radice)
            }
            if (vociRimosse != null) eventi.pubblica(TrascrittoEliminato(registrazioneId, incontroId, vociRimosse))
            Esito.Ok(Unit)
        }
    }
}
