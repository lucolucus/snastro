package snastro.progetto.applicazione.comandi

import snastro.kernel.DispatcherEventi
import snastro.kernel.Esito
import snastro.kernel.UnitaDiLavoro
import snastro.kernel.poi
import snastro.progetto.applicazione.eventi.RegistrazioneRinominata
import snastro.progetto.applicazione.porte.RegistrazioneRepository
import snastro.progetto.dominio.ErroreProgetto
import snastro.progetto.dominio.Registrazione

/**
 * Use-case `RinominaRegistrazione` (AC-360..362): goes through [Registrazione.rinomina] FIRST (trim,
 * blank → `TitoloVuoto`, same titolo → no-op without saving or publishing anything). Only once the
 * root accepts the new titolo is the set rule of AC-322 pre-checked — its [TitoloRegistrazione.chiave]
 * must differ from the key of every OTHER Registrazione of the same Progetto, else `TitoloGiaUsato`
 * and the transaction rolls back (the renamed in-memory copy is discarded). One unit of work;
 * `RegistrazioneRinominata` reaches after-commit subscribers only once it commits (ADR 0012). The
 * audio file in `audio/` is never touched (AC-362): only the row's titolo changes.
 */
public class RinominaRegistrazioneServizio(
    private val uow: UnitaDiLavoro,
    private val registrazioni: RegistrazioneRepository,
    private val eventi: DispatcherEventi,
) {
    public fun esegui(c: RinominaRegistrazione): Esito<Unit> = uow.inTransazione {
        val registrazione = registrazioni.trova(c.registrazioneId)
            ?: return@inTransazione Esito.Errore(ErroreProgetto.RegistrazioneNonTrovata(c.registrazioneId))
        registrazione.rinomina(c.nuovoTitolo).poi { evento ->
            if (evento == null) Esito.Ok(Unit) else salvaSeTitoloLibero(registrazione, evento)
        }
    }

    /** AC-361/AC-322 pre-check, after the root already accepted the rename (itself excluded). */
    private fun salvaSeTitoloLibero(
        registrazione: Registrazione,
        evento: snastro.progetto.dominio.RegistrazioneRinominata,
    ): Esito<Unit> {
        val chiave = TitoloRegistrazione.chiave(registrazione.titolo)
        val occupato = registrazioni.delProgetto(registrazione.progettoId)
            .any { it.id != registrazione.id && TitoloRegistrazione.chiave(it.titolo) == chiave }
        if (occupato) return Esito.Errore(ErroreProgetto.TitoloGiaUsato(registrazione.titolo))
        registrazioni.salva(registrazione)
        eventi.pubblica(evento.pubblicato())
        return Esito.Ok(Unit)
    }
}

private fun snastro.progetto.dominio.RegistrazioneRinominata.pubblicato(): RegistrazioneRinominata =
    RegistrazioneRinominata(registrazioneId = id, precedente = precedente, nuovo = nuovo)
