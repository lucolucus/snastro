package snastro.parlanti.adattatori.persistenza

import org.sqlite.SQLiteErrorCode
import org.sqlite.SQLiteException
import snastro.kernel.Esito
import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RicostituzioneDaPersistenza
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.parlanti.applicazione.porte.ParlanteRepository
import snastro.parlanti.applicazione.porte.RigaImpronta
import snastro.parlanti.dominio.ErroreParlanti.NomeGiaInUso
import snastro.parlanti.dominio.Impronta
import snastro.parlanti.dominio.ImprontaVocale
import snastro.parlanti.dominio.Nome
import snastro.parlanti.dominio.Parlante
import snastro.parlanti.dominio.StatoParlante
import snastro.parlanti.dominio.TipoParlante
import snastro.persistenza.MetadatiDelProgetto
import snastro.persistenza.MetadatiDiRegistrazione
import snastro.persistenza.SnastroDatabase
import java.nio.ByteBuffer
import java.nio.ByteOrder
import migrations.Impronta_vocale as ImprontaVocaleRiga
import migrations.Parlante as ParlanteRiga

/**
 * [ParlanteRepository] on the generated [SnastroDatabase] queries (dev-architecture-app.md#repository,
 * ADR 0006/0007/0009): [salva] upserts the root row, then **replaces** the owned `impronta_vocale`
 * rows of that `parlante_id` (delete then re-insert, mirroring `TrascrittoRepositorySql`) — always
 * inside the caller's transaction, never opening one (ADR 0012).
 *
 * `nome_normalizzato` is the [Nome] VO's own `normalizzato` (never computed in SQL, ADR 0007): INV-16
 * is backed by the partial unique index `parlante_nome_attivo_unico`, so a violation on the root
 * upsert is caught here and mapped to [NomeGiaInUso] — nothing (root nor prints) is written, because
 * the upsert runs BEFORE the print replacement below.
 *
 * `impronta_vocale.parlante_id` is an IMMEDIATE FK to `parlante(id)` (only its `(registrazione_id,
 * voce_id)` FK to `voce` is deferred, migrations/1.sqm), so the root upsert must precede the print
 * writes even without an enclosing transaction (as in this adapter's own contract test).
 *
 * R23 (ADR 0009 amendment, widened by ADR 0020 §3, AC-622): `PRAGMA wal_checkpoint(TRUNCATE)` cannot
 * run inside a transaction — every [salva] that removed at least one stored print (an `eliminato`
 * [Parlante], [INV-15] moves, [INV-21], [INV-25], ADR 0018 / 0020 purges) and every [rimuovi]
 * registers ONE checkpoint on a SQLDelight `afterCommit` hook. Nested inside the caller's [snastro.kernel.UnitaDiLavoro]
 * transaction, SQLDelight defers `afterCommit` hooks of a nested transaction to the OUTERMOST one
 * (`Transacter.kt`), so the checkpoint only ever runs once that transaction has actually committed.
 */
public class ParlanteRepositorySql(private val db: SnastroDatabase) : ParlanteRepository {
    override fun trova(id: ParlanteId): Parlante? {
        val riga = db.parlanteQueries.trovaPerId(id.valore).executeAsOneOrNull() ?: return null
        return riga.inDominio(impronteDi(db, id))
    }

    override fun delProgetto(id: ProgettoId): List<Parlante> =
        db.parlanteQueries.trovaDelProgetto(id.valore).executeAsList()
            .map { it.inDominio(impronteDi(db, ParlanteId(it.id))) }

    override fun nomeAttivoInUso(progettoId: ProgettoId, nome: Nome, escluso: ParlanteId?): Boolean =
        db.parlanteQueries.contaAttivoConNome(progettoId.valore, nome.normalizzato, escluso?.valore)
            .executeAsOne() > 0

    override fun salva(p: Parlante): Esito<Unit> {
        try {
            scriviRadice(db, p)
        } catch (ex: SQLiteException) {
            if (ex.resultCode != SQLiteErrorCode.SQLITE_CONSTRAINT_UNIQUE) throw ex
            return Esito.Errore(NomeGiaInUso(p.nome.valore))
        }
        if (sostituisciImpronte(db, p)) checkpointDopoCommit()
        return Esito.Ok(Unit)
    }

    override fun rimuovi(id: ParlanteId) {
        db.improntaVocaleQueries.eliminaDiParlante(id.valore)
        db.parlanteQueries.rimuovi(id.valore)
        checkpointDopoCommit()
    }

    /** One checkpoint per removal (user Q-1, 2026-09-25): no per-transaction dedup. Dropped on rollback. */
    private fun checkpointDopoCommit() {
        db.transaction { afterCommit { db.parlanteQueries.walCheckpointTruncate() } }
    }

    override fun impronteDiRegistrazione(id: RegistrazioneId): List<RigaImpronta> =
        db.improntaVocaleQueries.metadatiDiRegistrazione(id.valore).executeAsList().map { it.inRigaImpronta() }

    override fun impronteDelProgetto(id: ProgettoId): List<RigaImpronta> =
        db.improntaVocaleQueries.metadatiDelProgetto(id.valore).executeAsList().map { it.inRigaImpronta() }

    override fun aggiornaImpronta(
        attesa: RigaImpronta,
        impronta: Impronta,
        sorgente: String,
        modello: String,
    ): Boolean {
        val righe = db.improntaVocaleQueries.aggiornaCompareAndSet(
            impronta = impronta.aBlob(),
            sorgenteImpronta = sorgente,
            modelloImpronta = modello,
            parlanteId = attesa.parlanteId.valore,
            registrazioneId = attesa.voceRef.registrazioneId.valore,
            voceId = attesa.voceRef.voceId.numero.toLong(),
            sorgenteAttesa = attesa.sorgente,
            modelloAtteso = attesa.modello,
        ).value
        return righe == 1L
    }
}

private fun scriviRadice(db: SnastroDatabase, p: Parlante) {
    val tipo = p.tipo.name.lowercase()
    val stato = p.stato.name.lowercase()
    val esistente = db.parlanteQueries.trovaPerId(p.id.valore).executeAsOneOrNull()
    if (esistente == null) {
        db.parlanteQueries.inserisci(
            id = p.id.valore,
            progettoId = p.progettoId.valore,
            nome = p.nome.valore,
            nomeNormalizzato = p.nome.normalizzato,
            tipo = tipo,
            stato = stato,
        )
    } else {
        db.parlanteQueries.aggiorna(
            nome = p.nome.valore,
            nomeNormalizzato = p.nome.normalizzato,
            tipo = tipo,
            stato = stato,
            id = p.id.valore,
        )
    }
}

/** Replaces the prints of [p]; true iff a print that was stored is gone (removed, or moved to another Voce). */
private fun sostituisciImpronte(db: SnastroDatabase, p: Parlante): Boolean {
    val primaDi = db.improntaVocaleQueries.trovaDiParlante(p.id.valore).executeAsList()
        .map { VoceRef(RegistrazioneId(it.registrazione_id), VoceId(it.voce_id.toInt())) }
    db.improntaVocaleQueries.eliminaDiParlante(p.id.valore)
    p.impronte.forEach { imp ->
        db.improntaVocaleQueries.inserisci(
            parlanteId = p.id.valore,
            registrazioneId = imp.voceRef.registrazioneId.valore,
            voceId = imp.voceRef.voceId.numero.toLong(),
            impronta = imp.impronta.aBlob(),
            sorgenteImpronta = imp.sorgente,
            modelloImpronta = imp.modello,
        )
    }
    return !p.impronte.map { it.voceRef }.containsAll(primaDi)
}

private fun impronteDi(db: SnastroDatabase, id: ParlanteId): List<ImprontaVocale> =
    db.improntaVocaleQueries.trovaDiParlante(id.valore).executeAsList().map { it.inImprontaVocale() }

/** The database is trusted, nothing is re-validated (CR-15). */
@OptIn(RicostituzioneDaPersistenza::class)
private fun ParlanteRiga.inDominio(impronte: List<ImprontaVocale>): Parlante = Parlante.ricostituisci(
    id = ParlanteId(id),
    progettoId = ProgettoId(progetto_id),
    nome = nome,
    tipo = TipoParlante.valueOf(tipo.uppercase()),
    stato = StatoParlante.valueOf(stato.uppercase()),
    impronte = impronte,
)

private fun ImprontaVocaleRiga.inImprontaVocale(): ImprontaVocale = ImprontaVocale(
    voceRef = VoceRef(RegistrazioneId(registrazione_id), VoceId(voce_id.toInt())),
    impronta = impronta.daBlob(),
    sorgente = sorgente_impronta,
    modello = modello_impronta,
)

private fun MetadatiDiRegistrazione.inRigaImpronta(): RigaImpronta = RigaImpronta(
    parlanteId = ParlanteId(parlante_id),
    voceRef = VoceRef(RegistrazioneId(registrazione_id), VoceId(voce_id.toInt())),
    sorgente = sorgente_impronta,
    modello = modello_impronta,
)

private fun MetadatiDelProgetto.inRigaImpronta(): RigaImpronta = RigaImpronta(
    parlanteId = ParlanteId(parlante_id),
    voceRef = VoceRef(RegistrazioneId(registrazione_id), VoceId(voce_id.toInt())),
    sorgente = sorgente_impronta,
    modello = modello_impronta,
)

/** BLOB float32 little-endian (ADR 0009): the app writes no other encoding of a print's values. */
private fun Impronta.aBlob(): ByteArray {
    val valori = valori
    val buffer = ByteBuffer.allocate(valori.size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
    valori.forEach { buffer.putFloat(it) }
    return buffer.array()
}

private fun ByteArray.daBlob(): Impronta {
    val buffer = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
    val valori = FloatArray(size / Float.SIZE_BYTES) { buffer.getFloat() }
    return Impronta(valori)
}
