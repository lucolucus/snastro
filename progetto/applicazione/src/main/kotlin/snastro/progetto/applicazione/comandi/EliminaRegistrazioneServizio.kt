package snastro.progetto.applicazione.comandi

import snastro.kernel.DispatcherEventi
import snastro.kernel.Esito
import snastro.kernel.IncontroId
import snastro.kernel.UnitaDiLavoro
import snastro.progetto.applicazione.eventi.RegistrazioneEliminata
import snastro.progetto.applicazione.porte.EliminazioneInSospeso
import snastro.progetto.applicazione.porte.EliminazioniInSospeso
import snastro.progetto.applicazione.porte.IncontroRepository
import snastro.progetto.applicazione.porte.RegistrazioneRepository
import snastro.progetto.dominio.ErroreProgetto

/**
 * Use-case `EliminaRegistrazione` (ADR 0020 §2, INV-28), ONE transaction:
 * 1. the Registrazione (absent → `RegistrazioneNonTrovata`, nothing written);
 * 2. `Registrazione.elimina()` — the event with its values at deletion;
 * 3. the pending-cleanup row, so it exists iff the deletion commits (ADR 0020 §4);
 * 4. `pubblica`: the SYNCHRONOUS subscribers run here (ADR 0038 §2) — Sintesi's Riassunto removal when the Incontro
 *    ceases, then the Trascrizione veto (`ElaborazioneGiaAperta`, returned unchanged: the dispatcher dooms the
 *    transaction) + purge, which reaches the Parlanti purge + INV-25 through `TrascrittoEliminato`;
 * 5. `rimuovi` of the registrazione row — AFTER 4: the elaborazione / trascritto FKs are immediate;
 * 6. if it was the last Parte (`incontroCessato`, read at 1 from `partiDi`, ADR 0038 §1): `incontri.rimuovi` — AFTER 5,
 *    the `registrazione → incontro` FK is immediate (the call is a no-op if the row is already gone).
 *
 * No file I/O here: the files go after commit, each with its owner's subscriber. No Trascrizione / Parlanti query.
 */
public class EliminaRegistrazioneServizio(
    private val uow: UnitaDiLavoro,
    private val registrazioni: RegistrazioneRepository,
    private val incontri: IncontroRepository,
    private val inSospeso: EliminazioniInSospeso,
    private val eventi: DispatcherEventi,
) {
    public fun esegui(c: EliminaRegistrazione): Esito<Unit> = uow.inTransazione {
        val registrazione = registrazioni.trova(c.registrazioneId)
            ?: return@inTransazione Esito.Errore(ErroreProgetto.RegistrazioneNonTrovata(c.registrazioneId))
        val evento = registrazione.elimina()
        val incontroCessato = incontri.partiDi(registrazione.incontroId).none { it != evento.id }
        inSospeso.registra(
            EliminazioneInSospeso(evento.id, evento.titolo, evento.dataRegistrazione, evento.riferimentoAudio),
        )
        eventi.pubblica(evento.pubblicato(registrazione.incontroId, incontroCessato))
        registrazioni.rimuovi(evento.id)
        if (incontroCessato) incontri.rimuovi(registrazione.incontroId)
        Esito.Ok(Unit)
    }
}

private fun snastro.progetto.dominio.RegistrazioneEliminata.pubblicato(
    incontroId: IncontroId,
    incontroCessato: Boolean,
): RegistrazioneEliminata =
    RegistrazioneEliminata(
        registrazioneId = id,
        progettoId = progettoId,
        titolo = titolo,
        dataRegistrazione = dataRegistrazione,
        riferimentoAudio = riferimentoAudio,
        incontroId = incontroId,
        incontroCessato = incontroCessato,
    )
