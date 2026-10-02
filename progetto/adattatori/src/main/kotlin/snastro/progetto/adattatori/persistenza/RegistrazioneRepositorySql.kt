package snastro.progetto.adattatori.persistenza

import snastro.kernel.Esito
import snastro.kernel.IncontroId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RicostituzioneDaPersistenza
import snastro.kernel.RiferimentoAudio
import snastro.persistenza.SnastroDatabase
import snastro.progetto.applicazione.porte.RegistrazioneRepository
import snastro.progetto.dominio.OraDiInizio
import snastro.progetto.dominio.Registrazione
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import migrations.Registrazione as RegistrazioneRiga

/**
 * [RegistrazioneRepository] on the generated [SnastroDatabase] queries (dev-architecture-app.md#repository).
 * [salva] never opens its own transaction — the caller's [snastro.kernel.UnitaDiLavoro] does
 * (ADR 0012). Only `titolo` (AC-360), `dataRegistrazione` (INV-2) and `oraDiInizio` (INV-I14, 'HH:MM:SS' or NULL)
 * change after creation (INV-1); `incontro_id` is never updated (D-0028).
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

    // The Incontro row is IncontroRepository's: the caller saved it first, the immediate FK refuses a Parte without it.
    override fun salva(r: Registrazione) {
        val ora = r.oraDiInizio?.valore?.format(ORA)
        if (db.registrazioneQueries.trovaPerId(r.id.valore).executeAsOneOrNull() == null) {
            db.registrazioneQueries.inserisci(
                id = r.id.valore,
                progettoId = r.progettoId.valore,
                incontroId = r.incontroId.valore,
                titolo = r.titolo,
                riferimentoAudio = r.riferimentoAudio.percorsoRelativo,
                durataMs = r.durataMs,
                dataRegistrazione = r.dataRegistrazione.toString(),
                aggiuntaAlle = r.aggiuntaAlle.toEpochMilli(),
                oraDiInizio = ora,
            )
        } else {
            db.registrazioneQueries.aggiorna(
                titolo = r.titolo,
                dataRegistrazione = r.dataRegistrazione.toString(),
                oraDiInizio = ora,
                id = r.id.valore,
            )
        }
    }

    // ADR 0020: the elaborazione / trascritto FKs are immediate — their rows must already be gone.
    // The Incontro is not touched: its ceasing is IncontroRepository.rimuovi, called by the deletion command.
    override fun rimuovi(id: RegistrazioneId) {
        db.registrazioneQueries.elimina(id.valore)
    }
}

/**
 * The database is trusted (dev-architecture-app.md#aggregato: `ricostituisci` re-validates nothing), and one bad row
 * never breaks a list.
 */
@OptIn(RicostituzioneDaPersistenza::class)
private fun RegistrazioneRiga.inDominio(): Registrazione = Registrazione.ricostituisci(
    id = RegistrazioneId(id),
    progettoId = ProgettoId(progetto_id),
    incontroId = IncontroId(checkNotNull(incontro_id) { "registrazione $id senza incontro_id (INV-I1)" }),
    titolo = titolo,
    riferimentoAudio = RiferimentoAudio(riferimento_audio),
    durataMs = durata_ms,
    dataRegistrazione = LocalDate.parse(data_registrazione),
    aggiuntaAlle = Instant.ofEpochMilli(aggiunta_alle),
    // OraDiInizio has no unchecked factory, so the stored text goes through OraDiInizio.di. This repository only ever
    // writes 'HH:MM:SS' or NULL: an unreadable text (written by something else) reads as the empty time, and the next
    // save of this Registrazione stores that empty time.
    oraDiInizio = (OraDiInizio.di(ora_di_inizio) as? Esito.Ok)?.valore,
)

/** 'HH:MM:SS', the stored form of an OraDiInizio (7.sqm). */
private val ORA: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
