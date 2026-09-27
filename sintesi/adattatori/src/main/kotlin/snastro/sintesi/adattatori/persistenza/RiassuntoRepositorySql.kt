package snastro.sintesi.adattatori.persistenza

import org.sqlite.SQLiteErrorCode
import org.sqlite.SQLiteException
import snastro.kernel.Esito
import snastro.kernel.LetturaCoerente
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.persistenza.SnastroDatabase
import snastro.sintesi.applicazione.porte.RiassuntoRepository
import snastro.sintesi.dominio.ErroreSintesi
import snastro.sintesi.dominio.Riassunto
import snastro.sintesi.dominio.RiassuntoId
import snastro.sintesi.dominio.TestoConVoci

/**
 * [RiassuntoRepository] on the generated [SnastroDatabase] queries (dev-architecture-app.md#repository,
 * ADR 0022): only `riassunto*Queries` (never another context's — ADR 0021 §8, AC-S114). The write
 * helpers live as private top-level functions in this file, keeping the class itself just the port's
 * eight operations; the row -> domain mapping (`inDominio`, like `ParlanteRepositorySql`'s
 * `Parlante_row.inDominio`) lives in `RiassuntoMappingSql.kt` (one file's `TooManyFunctions` budget
 * cannot hold both halves).
 *
 * [salva] upserts the root: a brand-new id is a plain `inserisci`; an EXISTING one reuses the lifecycle
 * queries `Riassunto.sq` already declares for the transition the incoming [Riassunto] represents
 * (`avvia` for `in_attesa -> in_corso`, `concludi`'s SET clause for `-> pronto|fallito`) — never a
 * delete-then-reinsert of the root row, so a UNIQUE violation always leaves the STORED row exactly as
 * it was (an UPDATE that fails changes nothing) rather than risking data loss outside an enclosing
 * transaction (as this adapter's own contract test calls it). `salva` is only ever called immediately
 * after the aggregate's own (single-step) transition, so the stored precondition each reused query
 * expects always holds on the intended path; a repeat save of the identical, already-stored target
 * state is a harmless no-op on the reused query (content unchanged). Children
 * (`riassunto_elemento`/`riassunto_fonte`) are always replaced (delete then re-insert), like every
 * other repository's owned child rows.
 *
 * Both partial unique indexes (`riassunto_non_pronto_unico`, `riassunto_pronto_unico`, ADR 0022 §3)
 * map to the SAME [ErroreSintesi.RiassuntoGiaAperto] (D-0003); any other constraint failure is
 * rethrown raw (ADR 0003).
 *
 * [concludi] is the completion compare-and-set (ADR 0022 §4, INV-S8): re-read by id first: absent or
 * no longer `in_corso` -> `Ok(false)`, nothing written (a previous `pronto` of the Registrazione
 * included); only then, and only for a `pronto` [r], THIS call removes the previous `pronto` of the
 * SAME Registrazione (D-0003 — callers never do it first); the `concludi` query's own `stato =
 * 'in_corso'` condition is the defensive second half. Meaningful concurrency proof: the caller's
 * `UnitaDiLavoro` (`UnitaDiLavoroSql`) opens every transaction `BEGIN IMMEDIATE`
 * (`persistenza/AperturaDatabase.kt`), so the re-read here is authoritative — proven under a real race
 * in `RiassuntoRepositorySqlConcorrenzaTest` (AC-S113); `databaseInMemoria()` never contends.
 *
 * [trova]/[diRegistrazione]/[inAttesa]/[inCorso] read the root row and its children from ONE [lettura]
 * snapshot (ADR 0029 §5, AC-C30): never the root outside it, so a completion committing between the root
 * SELECT and the children's can never pair an OLD root with NEW children or the reverse (AC-C31).
 * `concludi` stays a write (`BEGIN IMMEDIATE`, via the caller's `UnitaDiLavoro`), never [lettura].
 */
public class RiassuntoRepositorySql(
    private val db: SnastroDatabase,
    private val lettura: LetturaCoerente,
) : RiassuntoRepository {
    /** ADR 0029 §5/ADR 0022 §4: the root row and its children (elementi/fonti, via [inDominio]) from ONE
     * snapshot — never the root outside it (AC-C30). */
    override fun trova(id: RiassuntoId): Riassunto? = lettura.inLettura {
        db.riassuntoQueries.trovaPerId(id.valore).executeAsOneOrNull()?.let { inDominio(db, it) }
    }

    override fun diRegistrazione(r: RegistrazioneId): List<Riassunto> = lettura.inLettura {
        db.riassuntoQueries.trovaDiRegistrazione(r.valore).executeAsList().map { inDominio(db, it) }
    }

    override fun inAttesa(): List<Riassunto> = lettura.inLettura {
        db.riassuntoQueries.trovaInAttesa().executeAsList().map { inDominio(db, it) }
    }

    override fun inCorso(): List<Riassunto> = lettura.inLettura {
        db.riassuntoQueries.trovaInCorso().executeAsList().map { inDominio(db, it) }
    }

    override fun salva(r: Riassunto): Esito<Unit> = try {
        if (db.riassuntoQueries.trovaPerId(r.id.valore).executeAsOneOrNull() == null) {
            scriviRadiceNuova(db, r)
        } else {
            aggiornaRadiceEsistente(db, r)
        }
        eliminaFigli(db, r.id)
        if (r.pronto) scriviFigli(db, r)
        Esito.Ok(Unit)
    } catch (ex: SQLiteException) {
        mappaErrore(r.registrazioneId, ex)
    }

    @Suppress("ReturnCount") // ADR 0022 §4's own three numbered steps, each a guard clause — clearer than nesting
    override fun concludi(r: Riassunto): Esito<Boolean> {
        require(r.pronto || r.fallito) { "concludi di un Riassunto non concluso: ${r.id}" }
        val esistente = db.riassuntoQueries.trovaPerId(r.id.valore).executeAsOneOrNull()
        if (esistente == null || esistente.stato != CODICE_IN_CORSO) return Esito.Ok(false)
        return try {
            if (r.pronto) rimuoviPrecedentePronto(db, r.registrazioneId)
            val righe = eseguiConcludi(db, r)
            if (righe == 0L) return Esito.Ok(false)
            if (r.pronto) scriviFigli(db, r)
            Esito.Ok(true)
        } catch (ex: SQLiteException) {
            mappaErrore(r.registrazioneId, ex)
        }
    }

    override fun rimuovi(id: RiassuntoId): Esito<Unit> {
        eliminaFigli(db, id)
        db.riassuntoQueries.elimina(id.valore)
        return Esito.Ok(Unit)
    }

    override fun rimuoviDiRegistrazione(r: RegistrazioneId): Esito<Int> {
        db.riassuntoFonteQueries.eliminaDiRegistrazione(r.valore)
        db.riassuntoElementoQueries.eliminaDiRegistrazione(r.valore)
        val righe = db.riassuntoQueries.eliminaDiRegistrazione(r.valore).value
        return Esito.Ok(righe.toInt())
    }
}

private fun scriviRadiceNuova(db: SnastroDatabase, r: Riassunto) {
    db.riassuntoQueries.inserisci(
        id = r.id.valore,
        registrazioneId = r.registrazioneId.valore,
        stato = r.stato.codice,
        argomento = r.argomento?.valore,
        lunghezzaMassimaParole = r.lunghezzaMassima.valore.toLong(),
        richiestoAlle = r.richiestoAlle.toEpochMilli(),
        avviatoAlle = r.avviatoAlle?.toEpochMilli(),
        motivoFallimento = r.motivoFallimento?.codice,
        sommario = r.sommario?.testo?.codifica(),
        omessi = r.omessi?.toLong(),
        struttura = r.struttura,
    )
}

/** No mutable column of an `in_attesa` row ever changes after [scriviRadiceNuova] (INV-S10): nothing to write. */
private fun aggiornaRadiceEsistente(db: SnastroDatabase, r: Riassunto) {
    if (r.inCorso) {
        db.riassuntoQueries.avvia(avviatoAlle = checkNotNull(r.avviatoAlle).toEpochMilli(), id = r.id.valore)
    } else if (r.pronto || r.fallito) {
        eseguiConcludi(db, r)
    }
}

private fun eseguiConcludi(db: SnastroDatabase, r: Riassunto): Long =
    db.riassuntoQueries.concludi(
        stato = r.stato.codice,
        motivoFallimento = r.motivoFallimento?.codice,
        sommario = r.sommario?.testo?.codifica(),
        omessi = r.omessi?.toLong(),
        struttura = r.struttura,
        id = r.id.valore,
    ).value

/** D-0003: only [RiassuntoRepositorySql.concludi] removes the previous `pronto` of [registrazioneId]. */
private fun rimuoviPrecedentePronto(db: SnastroDatabase, registrazioneId: RegistrazioneId) {
    val precedente = db.riassuntoQueries.trovaDiRegistrazione(registrazioneId.valore).executeAsList()
        .firstOrNull { it.stato == CODICE_PRONTO } ?: return
    eliminaFigli(db, RiassuntoId(precedente.id))
    db.riassuntoQueries.elimina(precedente.id)
}

// No cascade (dev-architecture #repository): fonte -> elemento, always before the riassunto row itself.
private fun eliminaFigli(db: SnastroDatabase, id: RiassuntoId) {
    db.riassuntoFonteQueries.eliminaDiRiassunto(id.valore)
    db.riassuntoElementoQueries.eliminaDiRiassunto(id.valore)
}

private fun scriviFigli(db: SnastroDatabase, r: Riassunto) {
    r.decisioni.forEachIndexed { i, e -> scriviElemento(db, r.id, TIPO_DECISIONE, i, e.testo, e.fonti, voce = null) }
    r.questioniAperte.forEachIndexed { i, e ->
        scriviElemento(db, r.id, TIPO_QUESTIONE_APERTA, i, e.testo, e.fonti, voce = null)
    }
    r.azioni.forEachIndexed { i, e -> scriviElemento(db, r.id, TIPO_AZIONE, i, e.testo, e.fonti, e.responsabile) }
    r.puntiChiave.forEachIndexed { i, e ->
        scriviElemento(db, r.id, TIPO_PUNTO_CHIAVE, i, e.testo, e.fonti, e.parlante)
    }
}

@Suppress("LongParameterList") // one parameter per stored column of the two child tables
private fun scriviElemento(
    db: SnastroDatabase,
    id: RiassuntoId,
    tipo: String,
    posizione: Int,
    testo: TestoConVoci,
    fonti: Set<SegmentoId>,
    voce: VoceId?,
) {
    db.riassuntoElementoQueries.inserisci(
        riassuntoId = id.valore,
        tipo = tipo,
        posizione = posizione.toLong(),
        testo = testo.codifica(),
        voceId = voce?.numero?.toLong(),
    )
    fonti.forEach { f ->
        db.riassuntoFonteQueries.inserisci(
            riassuntoId = id.valore,
            tipo = tipo,
            posizione = posizione.toLong(),
            segmentoId = f.numero.toLong(),
        )
    }
}

/** Maps a `riassunto_non_pronto_unico` / `riassunto_pronto_unico` violation to the ONE Sintesi error for a
 * per-Registrazione collision (D-0003); any other constraint failure is an infra fault (ADR 0003). */
private fun <T> mappaErrore(registrazioneId: RegistrazioneId, ex: SQLiteException): Esito<T> {
    if (ex.resultCode != SQLiteErrorCode.SQLITE_CONSTRAINT_UNIQUE) throw ex
    return Esito.Errore(ErroreSintesi.RiassuntoGiaAperto(registrazioneId))
}

private const val CODICE_IN_CORSO = "in_corso"
private const val CODICE_PRONTO = "pronto"
internal const val TIPO_DECISIONE = "decisione"
internal const val TIPO_QUESTIONE_APERTA = "questione_aperta"
internal const val TIPO_AZIONE = "azione"
internal const val TIPO_PUNTO_CHIAVE = "punto_chiave"
