package snastro.trascrizione.adattatori.persistenza

import snastro.kernel.IncontroId
import snastro.kernel.IntervalloMs
import snastro.kernel.LetturaCoerente
import snastro.kernel.RegistrazioneId
import snastro.kernel.RicostituzioneDaPersistenza
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.persistenza.SnastroDatabase
import snastro.trascrizione.applicazione.porte.TrascrittoRepository
import snastro.trascrizione.dominio.Segmento
import snastro.trascrizione.dominio.Trascritto
import migrations.Segmento as SegmentoRiga

/**
 * [TrascrittoRepository] on the generated [SnastroDatabase] queries (dev-architecture-app.md#repository,
 * INV-12): [salva] replaces the owned `voce`/`segmento` rows of its Registrazione (delete then re-insert),
 * never opening its own transaction — the caller's [snastro.kernel.UnitaDiLavoro] does (ADR 0012); only [trova] wraps
 * its two SELECTs in one, so they see a single snapshot (D-0008).
 *
 * Delete order is CHILD (`segmento`) then PARENT (`voce`); insert order is PARENT then CHILD: the three
 * FKs to `voce` (from `segmento`, and from Parlanti's `attribuzione`/`impronta_vocale`) are DEFERRABLE
 * INITIALLY DEFERRED so they surface only at the enclosing COMMIT (ADR migrations/1.sqm) — this order
 * additionally keeps every single statement self-consistent even with no enclosing transaction (as in
 * this adapter's own contract test, which calls [salva] directly).
 *
 * `voce` rows carry no data beyond the id: a Voce's Segmenti are the observable state (INV-6 — a Voce
 * with none does not exist), so [trova] never reads `voce`, only `trascritto` + `segmento`.
 */
public class TrascrittoRepositorySql(
    private val db: SnastroDatabase,
    private val lettura: LetturaCoerente,
) : TrascrittoRepository {
    /**
     * The counters (`trascritto`) and the Segmenti are read from ONE snapshot (ADR 0029 §5, AC-C28/C29):
     * called outside any unit of work, [lettura] opens the outermost `BEGIN DEFERRED` read (no queueing
     * behind a writer, AC-C28); called inside a command's [snastro.kernel.UnitaDiLavoro.inTransazione], it
     * joins that transaction and sees its uncommitted writes (rule 2) — a throw here dooms the whole unit
     * (AC-C29). Never a raw `db.transactionWithResult` (CR-3b): a Revisione committing between the two
     * SELECTs used to pair the OLD `prossimaVoce` with the NEW Segmenti and `ricostituisci` refused it
     * (D-0008); the snapshot rules that out.
     */
    @OptIn(RicostituzioneDaPersistenza::class)
    override fun trova(id: RegistrazioneId, incontroId: IncontroId): Trascritto? = lettura.inLettura {
        val riga = db.trascrittoQueries.trovaPerRegistrazione(id.valore).executeAsOneOrNull()
            ?: return@inLettura null
        val voci = db.vociIncontroQueries.trovaPerIncontro(incontroId.valore).executeAsOneOrNull()
            ?: return@inLettura null
        val segmenti = db.segmentoQueries.trovaDiTrascritto(id.valore).executeAsList().map { it.inDominio() }
        Trascritto.ricostituisci(id, incontroId, segmenti, voci.prossima_voce.toInt(), riga.prossimo_segmento.toInt())
    }

    override fun conTrascritto(): List<RegistrazioneId> =
        db.trascrittoQueries.trovaRegistrazioniConTrascritto().executeAsList().map(::RegistrazioneId)

    // ADR 0020: children first (voce -> trascritto is immediate); attribuzione / impronta_vocale rows pointing at
    // these voci are the Parlanti purge's, checked by the deferred FKs at COMMIT.
    override fun rimuovi(id: RegistrazioneId, incontroId: IncontroId) {
        db.segmentoQueries.eliminaDiRegistrazione(id.valore)
        db.voceQueries.eliminaDiRegistrazione(id.valore)
        db.voceIncontroQueries.eliminaSenzaPresenza(incontroId.valore, id.valore)
        db.vociIncontroQueries.eliminaSeSenzaVoci(incontroId.valore)
        db.trascrittoQueries.elimina(id.valore)
    }

    override fun salva(t: Trascritto) {
        val registrazioneId = t.registrazioneId.valore
        if (db.trascrittoQueries.esistePerRegistrazione(registrazioneId).executeAsOneOrNull() == null) {
            db.trascrittoQueries.inserisci(registrazioneId, t.prossimoSegmento.toLong())
        } else {
            db.trascrittoQueries.aggiornaContatori(t.prossimoSegmento.toLong(), registrazioneId)
        }
        // The Voce counter lives in the root "Voci dell'Incontro" (ADR 0034 §2), keyed by the Trascritto's Incontro.
        val incontroId = t.incontroId.valore
        if (db.vociIncontroQueries.trovaPerIncontro(incontroId).executeAsOneOrNull() == null) {
            db.vociIncontroQueries.inserisci(incontroId, t.prossimaVoce.toLong())
        } else {
            db.vociIncontroQueries.aggiorna(t.prossimaVoce.toLong(), incontroId)
        }

        db.segmentoQueries.eliminaDiRegistrazione(registrazioneId)
        db.voceQueries.eliminaDiRegistrazione(registrazioneId)

        t.voci.forEach { voce ->
            db.voceIncontroQueries.inserisciSeAssente(incontroId, voce.id.numero.toLong())
            db.voceQueries.inserisci(registrazioneId = registrazioneId, numero = voce.id.numero.toLong())
        }
        db.voceIncontroQueries.eliminaSenzaPresenza(incontroId, registrazioneId)
        t.segmenti.forEach { s ->
            db.segmentoQueries.inserisci(
                registrazioneId = registrazioneId,
                numero = s.id.numero.toLong(),
                voceNumero = s.voceId.numero.toLong(),
                inizioMs = s.intervallo.inizioMs,
                fineMs = s.intervallo.fineMs,
                testo = s.testo,
                confermato = if (s.confermato) 1L else 0L,
            )
        }
    }
}

/** [Segmento] is a plain read-copy VO (CR-15 gates only the aggregate's own `ricostituisci`). */
private fun SegmentoRiga.inDominio(): Segmento =
    Segmento(
        SegmentoId(numero.toInt()),
        VoceId(voce_numero.toInt()),
        IntervalloMs(inizio_ms, fine_ms),
        testo,
        confermato = confermato != 0L,
    )
