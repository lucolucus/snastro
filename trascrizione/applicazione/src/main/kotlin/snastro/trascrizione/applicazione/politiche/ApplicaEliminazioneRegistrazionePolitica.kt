package snastro.trascrizione.applicazione.politiche

import snastro.kernel.Esito
import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.trascrizione.applicazione.porte.ElaborazioneRepository
import snastro.trascrizione.applicazione.porte.LettoreRegistrazione
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
 * [user default]. Otherwise the Parte leaves the Voci dell'Incontro (`VociDellIncontro.rimuoviParte`, INV-I6) and
 * EVERY Elaborazione of it goes, history included. The root itself is removed only when the Incontro ceases with
 * this Parte, its last one (ADR 0035 §1: the counter survives every other removal, INV-I4) — read from
 * [registrazioni] before Progetto removes the row (ADR 0020 §2). Structural only: it never decodes nor extracts
 * (ADR 0012 Amendment (d)).
 */
public class ApplicaEliminazioneRegistrazionePolitica(
    private val elaborazioni: ElaborazioneRepository,
    private val trascritti: VociDellIncontroRepository,
    private val registrazioni: LettoreRegistrazione,
) {
    /** [incontroId] is the Incontro the deleted Parte belongs to, resolved by the caller (ADR 0033 §4.1). */
    public fun applica(registrazioneId: RegistrazioneId, incontroId: IncontroId): Esito<Unit> {
        val diRegistrazione = elaborazioni.diRegistrazione(registrazioneId)
        if (diRegistrazione.any { it.aperta }) {
            return Esito.Errore(ErroreTrascrizione.ElaborazioneGiaAperta(registrazioneId))
        }
        trascritti.trova(incontroId)?.let { radice ->
            val cessa = registrazioni.parti(incontroId).orEmpty().all { it.registrazioneId == registrazioneId }
            if (cessa) {
                trascritti.rimuovi(incontroId)
            } else if (radice.haParte(registrazioneId)) {
                check(radice.rimuoviParte(registrazioneId) is Esito.Ok) // haParte: never refused
                trascritti.salva(radice)
            }
        }
        // INV-5: a Trascritto exists only with a completata Elaborazione — none at all, nothing more to remove.
        if (diRegistrazione.isNotEmpty()) elaborazioni.rimuoviDiRegistrazione(registrazioneId)
        return Esito.Ok(Unit)
    }
}
