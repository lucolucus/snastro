package snastro.progetto.adattatori.persistenza

import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RicostituzioneDaPersistenza
import snastro.kernel.RiferimentoAudio
import snastro.persistenza.SnastroDatabase
import snastro.progetto.applicazione.porte.RegistrazioneRepository
import snastro.progetto.dominio.Registrazione
import java.time.Instant
import java.time.LocalDate
import migrations.Registrazione as RegistrazioneRiga

/**
 * [RegistrazioneRepository] on the generated [SnastroDatabase] queries (dev-architecture-app.md#repository).
 * [salva] never opens its own transaction — the caller's [snastro.kernel.UnitaDiLavoro] does
 * (ADR 0012). Only `titolo` (AC-360) and `dataRegistrazione` (INV-2) change after creation (INV-1),
 * so an update touches just those two columns ([SnastroDatabase.registrazioneQueries]'s `aggiorna`).
 */
public class RegistrazioneRepositorySql(private val db: SnastroDatabase) : RegistrazioneRepository {
    override fun trova(id: RegistrazioneId): Registrazione? =
        db.registrazioneQueries.trovaPerId(id.valore).executeAsOneOrNull()?.inDominio()

    override fun delProgetto(id: ProgettoId): List<Registrazione> =
        db.registrazioneQueries.trovaDelProgetto(id.valore).executeAsList().map { it.inDominio() }

    // AC-326: reads the sole `titolo` column — no row-object holds the other columns, so this
    // path can never reconstitute a Registrazione (no rule to duplicate, RC-1).
    override fun titoliDelProgetto(id: ProgettoId): List<String> =
        db.registrazioneQueries.titoliDelProgetto(id.valore).executeAsList()

    override fun salva(r: Registrazione) {
        val esistente = db.registrazioneQueries.trovaPerId(r.id.valore).executeAsOneOrNull()
        if (esistente == null) {
            db.registrazioneQueries.inserisci(
                id = r.id.valore,
                progettoId = r.progettoId.valore,
                titolo = r.titolo,
                riferimentoAudio = r.riferimentoAudio.percorsoRelativo,
                durataMs = r.durataMs,
                dataRegistrazione = r.dataRegistrazione.toString(),
                aggiuntaAlle = r.aggiuntaAlle.toEpochMilli(),
            )
        } else {
            db.registrazioneQueries.aggiorna(
                titolo = r.titolo,
                dataRegistrazione = r.dataRegistrazione.toString(),
                id = r.id.valore,
            )
        }
    }
}

/** The database is trusted, nothing is re-validated (CR-15). */
@OptIn(RicostituzioneDaPersistenza::class)
private fun RegistrazioneRiga.inDominio(): Registrazione = Registrazione.ricostituisci(
    id = RegistrazioneId(id),
    progettoId = ProgettoId(progetto_id),
    titolo = titolo,
    riferimentoAudio = RiferimentoAudio(riferimento_audio),
    durataMs = durata_ms,
    dataRegistrazione = LocalDate.parse(data_registrazione),
    aggiuntaAlle = Instant.ofEpochMilli(aggiunta_alle),
)
