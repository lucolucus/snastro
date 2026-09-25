package snastro.trascrizione.adattatori.persistenza

import org.sqlite.SQLiteErrorCode
import org.sqlite.SQLiteException
import snastro.kernel.ElaborazioneId
import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.kernel.RicostituzioneDaPersistenza
import snastro.persistenza.SnastroDatabase
import snastro.trascrizione.applicazione.porte.ElaborazioneRepository
import snastro.trascrizione.dominio.Elaborazione
import snastro.trascrizione.dominio.ErroreTrascrizione.ElaborazioneGiaAperta
import snastro.trascrizione.dominio.ErroreTrascrizione.ElaborazioneGiaAvviata
import snastro.trascrizione.dominio.ErroreTrascrizione.ElaborazioneNonTrovata
import snastro.trascrizione.dominio.NumeroPersone
import snastro.trascrizione.dominio.StatoElaborazione
import java.time.Instant
import migrations.Elaborazione as ElaborazioneRiga

/**
 * [ElaborazioneRepository] on the generated [SnastroDatabase] queries (dev-architecture-app.md#repository).
 * [salva] never opens its own transaction — the caller's [snastro.kernel.UnitaDiLavoro] does (ADR 0012).
 *
 * INV-4 (ADR 0007) is backed by the partial unique index `elaborazione_aperta_unica` on
 * `elaborazione(registrazione_id)` WHERE `stato IN ('in_attesa','in_corso')` — the only unique constraint an
 * `elaborazione` write can violate (`elaborazione_completata_unica` is dropped by `3.sqm`, ADR 0018). Only an
 * open write can violate it, so [Elaborazione.aperta] alone (no message parsing) maps it to `ElaborazioneGiaAperta`;
 * any other constraint fault is rethrown raw (AC-111).
 */
public class ElaborazioneRepositorySql(private val db: SnastroDatabase) : ElaborazioneRepository {
    override fun diRegistrazione(id: RegistrazioneId): List<Elaborazione> =
        db.elaborazioneQueries.trovaDiRegistrazione(id.valore).executeAsList().map { it.inDominio() }

    override fun inAttesa(): List<Elaborazione> =
        db.elaborazioneQueries.trovaInAttesa().executeAsList().map { it.inDominio() }

    override fun inCorso(): List<Elaborazione> =
        db.elaborazioneQueries.trovaInCorso().executeAsList().map { it.inDominio() }

    override fun trova(id: ElaborazioneId): Elaborazione? =
        db.elaborazioneQueries.trovaPerId(id.valore).executeAsOneOrNull()?.inDominio()

    /**
     * `eliminaInAttesa` carries the `in_attesa` condition in the DELETE itself (never a stale read); only when it
     * deleted nothing is the row re-read, just to tell a started row from an absent one (AC-472).
     */
    override fun rimuoviInAttesa(id: ElaborazioneId): Esito<Unit> = when {
        db.elaborazioneQueries.eliminaInAttesa(id.valore).value > 0 -> Esito.Ok(Unit)
        db.elaborazioneQueries.trovaPerId(id.valore).executeAsOneOrNull() != null ->
            Esito.Errore(ElaborazioneGiaAvviata(id))
        else -> Esito.Errore(ElaborazioneNonTrovata(id))
    }

    // ADR 0020: every row of the Registrazione, any state — the elimination policy vetoed open ones first.
    override fun rimuoviDiRegistrazione(id: RegistrazioneId) {
        db.elaborazioneQueries.eliminaDiRegistrazione(id.valore)
    }

    override fun salva(e: Elaborazione): Esito<Unit> = try {
        val esistente = db.elaborazioneQueries.trovaPerId(e.id.valore).executeAsOneOrNull()
        if (esistente == null) {
            db.elaborazioneQueries.inserisci(
                id = e.id.valore,
                registrazioneId = e.registrazioneId.valore,
                stato = e.stato.testo(),
                creataAlle = e.creataAlle.toEpochMilli(),
                avviataAlle = e.avviataAlle?.toEpochMilli(),
                motivoFallimento = e.motivoFallimento,
                numeroPersone = e.numeroPersone?.valore?.toLong(),
            )
        } else {
            db.elaborazioneQueries.aggiornaStato(
                stato = e.stato.testo(),
                avviataAlle = e.avviataAlle?.toEpochMilli(),
                motivoFallimento = e.motivoFallimento,
                id = e.id.valore,
            )
        }
        Esito.Ok(Unit)
    } catch (ex: SQLiteException) {
        errore(e, ex)
    }

    private fun errore(e: Elaborazione, ex: SQLiteException): Esito<Unit> {
        if (ex.resultCode != SQLiteErrorCode.SQLITE_CONSTRAINT_UNIQUE || !e.aperta) throw ex
        return Esito.Errore(ElaborazioneGiaAperta(e.registrazioneId))
    }
}

private fun StatoElaborazione.testo(): String = name.lowercase()

/** The database is trusted, nothing is re-validated (CR-15); [numero_persone] is checked by the
 * migration's CHECK constraint, so [NumeroPersone.di] is always [Esito.Ok] here. */
@OptIn(RicostituzioneDaPersistenza::class)
private fun ElaborazioneRiga.inDominio(): Elaborazione = Elaborazione.ricostituisci(
    id = ElaborazioneId(id),
    registrazioneId = RegistrazioneId(registrazione_id),
    creataAlle = Instant.ofEpochMilli(creata_alle),
    numeroPersone = numero_persone?.let { it.toInt().numeroPersone() },
    stato = StatoElaborazione.valueOf(stato.uppercase()),
    avviataAlle = avviata_alle?.let(Instant::ofEpochMilli),
    motivoFallimento = motivo_fallimento,
)

private fun Int.numeroPersone(): NumeroPersone = when (val esito = NumeroPersone.di(this)) {
    is Esito.Ok -> esito.valore
    is Esito.Errore -> error("numero_persone $this fuori da 1..10 nel database (il CHECK dello schema lo impedisce)")
}
