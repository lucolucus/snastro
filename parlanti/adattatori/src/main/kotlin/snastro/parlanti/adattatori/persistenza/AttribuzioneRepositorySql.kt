package snastro.parlanti.adattatori.persistenza

import snastro.kernel.IncontroId
import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId
import snastro.kernel.RicostituzioneDaPersistenza
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.parlanti.applicazione.porte.AttribuzioneRepository
import snastro.parlanti.dominio.Attribuzione
import snastro.persistenza.SnastroDatabase

/**
 * [AttribuzioneRepository] on the generated [SnastroDatabase] queries (dev-architecture-app.md#repository,
 * ADR 0007): keyed by [VoceRef] — `attribuzione`'s PRIMARY KEY `(incontro_id, voce_id)`, written and read directly
 * (ADR 0033 §4.1) — makes "at most one row per Voce" structural. [salva] is an upsert (`inserisci` or
 * `aggiornaParlante`, which never names `incontro_id`), never opening a transaction (ADR 0012).
 */
public class AttribuzioneRepositorySql(private val db: SnastroDatabase) : AttribuzioneRepository {
    override fun trova(v: VoceRef): Attribuzione? =
        db.attribuzioneQueries.trova(v.incontroId.valore, v.voceId.numero.toLong())
            .executeAsOneOrNull()?.let { inDominio(it.incontro_id, it.voce_id, it.progetto_id, it.parlante_id) }

    override fun diIncontro(id: IncontroId): List<Attribuzione> =
        db.attribuzioneQueries.trovaDiIncontro(id.valore).executeAsList()
            .map { inDominio(it.incontro_id, it.voce_id, it.progetto_id, it.parlante_id) }

    override fun diParlante(id: ParlanteId): List<Attribuzione> =
        db.attribuzioneQueries.trovaDiParlante(id.valore).executeAsList()
            .map { inDominio(it.incontro_id, it.voce_id, it.progetto_id, it.parlante_id) }

    override fun salva(a: Attribuzione) {
        val incontroId = a.voceRef.incontroId.valore
        val voceId = a.voceRef.voceId.numero.toLong()
        if (db.attribuzioneQueries.trova(incontroId, voceId).executeAsOneOrNull() == null) {
            db.attribuzioneQueries.inserisci(
                incontroId = incontroId,
                voceId = voceId,
                progettoId = a.progettoId.valore,
                parlanteId = a.parlanteId.valore,
            )
        } else {
            db.attribuzioneQueries.aggiornaParlante(
                parlanteId = a.parlanteId.valore,
                incontroId = incontroId,
                voceId = voceId,
            )
        }
    }

    override fun rimuovi(v: VoceRef) {
        db.attribuzioneQueries.rimuovi(v.incontroId.valore, v.voceId.numero.toLong())
    }
}

/** The database is trusted, nothing is re-validated (CR-15). */
@OptIn(RicostituzioneDaPersistenza::class)
private fun inDominio(incontroId: String, voceId: Long, progettoId: String, parlanteId: String): Attribuzione =
    Attribuzione.ricostituisci(
        voceRef = VoceRef(IncontroId(incontroId), VoceId(voceId.toInt())),
        progettoId = ProgettoId(progettoId),
        parlanteId = ParlanteId(parlanteId),
    )
