package snastro.trascrizione.adattatori.persistenza

import snastro.kernel.IncontroId
import snastro.kernel.IntervalloMs
import snastro.kernel.LetturaCoerente
import snastro.kernel.RegistrazioneId
import snastro.kernel.RicostituzioneDaPersistenza
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.persistenza.SnastroDatabase
import snastro.trascrizione.applicazione.porte.LettoreRegistrazione
import snastro.trascrizione.applicazione.porte.VociDellIncontroRepository
import snastro.trascrizione.dominio.Segmento
import snastro.trascrizione.dominio.Trascritto
import snastro.trascrizione.dominio.VociDellIncontro
import migrations.Segmento as SegmentoRiga

/**
 * [VociDellIncontroRepository] on the generated [SnastroDatabase] queries (dev-architecture-app.md#repository, ADR
 * 0034, ADR 0035 §1): the only writer of `voci_incontro`, `voce_incontro`, `trascritto`, `voce`, `segmento`. Writes
 * never open a transaction — the caller's [snastro.kernel.UnitaDiLavoro] does (ADR 0012); reads run in ONE
 * [LetturaCoerente] snapshot (ADR 0029).
 *
 * The Parti of an Incontro are read through [registrazioni] (ADR 0033 §4.1: no Trascrizione query reads the
 * `registrazione` table), in the Incontro's order: a stored Parte is one that has a `trascritto` row. [salva] replaces
 * the owned rows of every Parte the root holds whose stored rows differ (delete then re-insert), deletes those of a
 * Parte it no longer holds, and keeps `voce_incontro` equal to the root's Voci.
 *
 * Delete order is CHILD (`segmento`) then PARENT (`voce`, then `trascritto`); insert order is PARENT then CHILD: the
 * FKs to `voce`/`voce_incontro` from `segmento` and from Parlanti's `attribuzione`/`impronta_vocale` are DEFERRABLE
 * INITIALLY DEFERRED, so a Voce that ceased without its Parlanti rows purged fails the enclosing COMMIT (fail closed).
 */
public class VociDellIncontroRepositorySql(
    private val db: SnastroDatabase,
    private val lettura: LetturaCoerente,
    private val registrazioni: LettoreRegistrazione,
) : VociDellIncontroRepository {
    /**
     * The counter (`voci_incontro`), each Parte's counter (`trascritto`) and its Segmenti are read from ONE snapshot
     * (ADR 0029 §5, AC-C28/C29): outside any unit of work [lettura] opens the outermost `BEGIN DEFERRED` read; inside a
     * command's transaction it joins it and sees its uncommitted writes. Never a raw `db.transactionWithResult`
     * (CR-3b).
     */
    @OptIn(RicostituzioneDaPersistenza::class)
    override fun trova(id: IncontroId): VociDellIncontro? = lettura.inLettura {
        val radice = db.vociIncontroQueries.trovaPerIncontro(id.valore).executeAsOneOrNull() ?: return@inLettura null
        val prossimaVoce = radice.prossima_voce.toInt()
        val trascritti = partiDi(id).mapNotNull { leggi(it, id, prossimaVoce) }
        VociDellIncontro.ricostituisci(id, trascritti, prossimaVoce)
    }

    override fun trascritto(r: RegistrazioneId): Trascritto? = lettura.inLettura {
        val incontroId = registrazioni.registrazione(r)?.incontroId ?: return@inLettura null
        val radice = db.vociIncontroQueries.trovaPerIncontro(incontroId.valore).executeAsOneOrNull()
        radice?.let { leggi(r, incontroId, it.prossima_voce.toInt()) }
    }

    override fun conTrascritto(): List<RegistrazioneId> =
        db.trascrittoQueries.trovaRegistrazioniConTrascritto().executeAsList().map(::RegistrazioneId)

    override fun salva(root: VociDellIncontro) {
        val incontroId = root.incontroId.valore
        if (db.vociIncontroQueries.trovaPerIncontro(incontroId).executeAsOneOrNull() == null) {
            db.vociIncontroQueries.inserisci(incontroId, root.prossimaVoce.toLong())
        } else {
            db.vociIncontroQueries.aggiorna(root.prossimaVoce.toLong(), incontroId)
        }
        val trascritti = root.trascritti
        val tenute = trascritti.mapTo(HashSet()) { it.registrazioneId }
        partiDi(root.incontroId).filterNot { it in tenute }.forEach(::eliminaParte)
        val voci = root.voci.mapTo(HashSet()) { it.numero.toLong() }
        voci.forEach { db.voceIncontroQueries.inserisciSeAssente(incontroId, it) }
        trascritti.forEach { t ->
            // Only a Parte whose rows differ from the root's is rewritten (ADR 0035 §1): its stored copy is compared.
            val salvato = leggi(t.registrazioneId, root.incontroId, root.prossimaVoce)
            if (salvato?.stessoContenutoDi(t) != true) scriviParte(t)
        }
        db.voceIncontroQueries.numeriDiIncontro(incontroId).executeAsList()
            .filterNot { it in voci }
            .forEach { db.voceIncontroQueries.elimina(incontroId, it) }
    }

    // ADR 0020, ADR 0038: the Incontro ceased; its Parti go first (children first), then its Voci, then the root.
    override fun rimuovi(id: IncontroId) {
        partiDi(id).forEach(::eliminaParte)
        db.voceIncontroQueries.eliminaDiIncontro(id.valore)
        db.vociIncontroQueries.elimina(id.valore)
    }

    private fun partiDi(id: IncontroId): List<RegistrazioneId> =
        registrazioni.parti(id).orEmpty().map { it.registrazioneId }

    @OptIn(RicostituzioneDaPersistenza::class)
    private fun leggi(r: RegistrazioneId, incontroId: IncontroId, prossimaVoce: Int): Trascritto? {
        val riga = db.trascrittoQueries.trovaPerRegistrazione(r.valore).executeAsOneOrNull() ?: return null
        val segmenti = db.segmentoQueries.trovaDiTrascritto(r.valore).executeAsList().map { it.inDominio() }
        return Trascritto.ricostituisci(r, incontroId, segmenti, prossimaVoce, riga.prossimo_segmento.toInt())
    }

    private fun scriviParte(t: Trascritto) {
        val registrazioneId = t.registrazioneId.valore
        if (db.trascrittoQueries.esistePerRegistrazione(registrazioneId).executeAsOneOrNull() == null) {
            db.trascrittoQueries.inserisci(registrazioneId, t.prossimoSegmento.toLong())
        } else {
            db.trascrittoQueries.aggiornaContatori(t.prossimoSegmento.toLong(), registrazioneId)
        }
        db.segmentoQueries.eliminaDiRegistrazione(registrazioneId)
        db.voceQueries.eliminaDiRegistrazione(registrazioneId)
        t.voci.forEach { db.voceQueries.inserisci(registrazioneId = registrazioneId, numero = it.id.numero.toLong()) }
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

    // Children first: voce -> trascritto is immediate; Parlanti rows pointing at these voci are checked at COMMIT.
    private fun eliminaParte(r: RegistrazioneId) {
        db.segmentoQueries.eliminaDiRegistrazione(r.valore)
        db.voceQueries.eliminaDiRegistrazione(r.valore)
        db.trascrittoQueries.elimina(r.valore)
    }
}

private fun Trascritto.stessoContenutoDi(altro: Trascritto): Boolean =
    prossimoSegmento == altro.prossimoSegmento && segmenti == altro.segmenti

/** [Segmento] is a plain read-copy VO (CR-15 gates only the aggregate's own `ricostituisci`). */
private fun SegmentoRiga.inDominio(): Segmento =
    Segmento(
        SegmentoId(numero.toInt()),
        VoceId(voce_numero.toInt()),
        IntervalloMs(inizio_ms, fine_ms),
        testo,
        confermato = confermato != 0L,
    )
