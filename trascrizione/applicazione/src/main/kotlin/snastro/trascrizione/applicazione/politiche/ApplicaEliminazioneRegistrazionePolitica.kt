package snastro.trascrizione.applicazione.politiche

import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.trascrizione.applicazione.porte.ElaborazioneRepository
import snastro.trascrizione.applicazione.porte.TrascrittoRepository
import snastro.trascrizione.dominio.ErroreTrascrizione

/**
 * Policy `ApplicaEliminazioneRegistrazione` — the Trascrizione half of [INV-28] (ADR 0020 §2 step 4), run
 * SYNCHRONOUSLY inside the deleting transaction of `EliminaRegistrazione`. It never imports `RegistrazioneEliminata`
 * (`:trascrizione:applicazione` may not depend on `:progetto:applicazione`): `abbonato-eliminazione-trascrizione`
 * (`:trascrizione:adattatori`) subscribes to it and calls [applica].
 *
 * It re-reads the Elaborazioni INSIDE the transaction: any `aperta` one (the aggregate's predicate) vetoes the
 * deletion with `ElaborazioneGiaAperta`, removing nothing — the deletion never cancels a queued Elaborazione
 * [user default]. Otherwise it removes the Trascritto (Voci, Segmenti) and EVERY Elaborazione, history included.
 * Structural only: it never decodes nor extracts (ADR 0012 Amendment (d)).
 */
public class ApplicaEliminazioneRegistrazionePolitica(
    private val elaborazioni: ElaborazioneRepository,
    private val trascritti: TrascrittoRepository,
) {
    public fun applica(registrazioneId: RegistrazioneId): Esito<Unit> {
        val diRegistrazione = elaborazioni.diRegistrazione(registrazioneId)
        if (diRegistrazione.any { it.aperta }) {
            return Esito.Errore(ErroreTrascrizione.ElaborazioneGiaAperta(registrazioneId))
        }
        // INV-5: a Trascritto exists only with a completata Elaborazione — none at all, nothing to remove.
        if (diRegistrazione.isEmpty()) return Esito.Ok(Unit)
        trascritti.rimuovi(registrazioneId)
        elaborazioni.rimuoviDiRegistrazione(registrazioneId)
        return Esito.Ok(Unit)
    }
}
