package snastro.trascrizione.adattatori.persistenza

import snastro.kernel.IntervalloMs
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
 * never opening its own transaction — the caller's [snastro.kernel.UnitaDiLavoro] does (ADR 0012).
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
public class TrascrittoRepositorySql(private val db: SnastroDatabase) : TrascrittoRepository {
    @OptIn(RicostituzioneDaPersistenza::class)
    override fun trova(id: RegistrazioneId): Trascritto? {
        val riga = db.trascrittoQueries.trovaPerRegistrazione(id.valore).executeAsOneOrNull() ?: return null
        val segmenti = db.segmentoQueries.trovaDiTrascritto(id.valore).executeAsList().map { it.inDominio() }
        return Trascritto.ricostituisci(id, segmenti, riga.prossima_voce.toInt(), riga.prossimo_segmento.toInt())
    }

    override fun conTrascritto(): List<RegistrazioneId> =
        db.trascrittoQueries.trovaRegistrazioniConTrascritto().executeAsList().map(::RegistrazioneId)

    // ADR 0020: children first (voce -> trascritto is immediate); attribuzione / impronta_vocale rows pointing at
    // these voci are the Parlanti purge's, checked by the deferred FKs at COMMIT.
    override fun rimuovi(id: RegistrazioneId) {
        db.segmentoQueries.eliminaDiRegistrazione(id.valore)
        db.voceQueries.eliminaDiRegistrazione(id.valore)
        db.trascrittoQueries.elimina(id.valore)
    }

    override fun salva(t: Trascritto) {
        val registrazioneId = t.registrazioneId.valore
        if (db.trascrittoQueries.trovaPerRegistrazione(registrazioneId).executeAsOneOrNull() == null) {
            db.trascrittoQueries.inserisci(
                registrazioneId = registrazioneId,
                prossimaVoce = t.prossimaVoce.toLong(),
                prossimoSegmento = t.prossimoSegmento.toLong(),
            )
        } else {
            db.trascrittoQueries.aggiornaContatori(
                prossimaVoce = t.prossimaVoce.toLong(),
                prossimoSegmento = t.prossimoSegmento.toLong(),
                registrazioneId = registrazioneId,
            )
        }

        db.segmentoQueries.eliminaDiRegistrazione(registrazioneId)
        db.voceQueries.eliminaDiRegistrazione(registrazioneId)

        t.voci.forEach { voce ->
            db.voceQueries.inserisci(registrazioneId = registrazioneId, numero = voce.id.numero.toLong())
        }
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
