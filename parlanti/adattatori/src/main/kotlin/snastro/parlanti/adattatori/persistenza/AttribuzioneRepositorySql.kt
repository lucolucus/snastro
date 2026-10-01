package snastro.parlanti.adattatori.persistenza

import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RicostituzioneDaPersistenza
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.parlanti.applicazione.porte.AttribuzioneRepository
import snastro.parlanti.dominio.Attribuzione
import snastro.persistenza.SnastroDatabase

/**
 * [AttribuzioneRepository] on the generated [SnastroDatabase] queries (dev-architecture-app.md#repository,
 * ADR 0007): keyed by [VoceRef] — `attribuzione`'s PRIMARY KEY `(incontro_id, voce_id)` (the Incontro is resolved from
 * the Parte by SQL join, ADR 0033 §6 transition) makes "at
 * most one row per Voce" structural. [salva] is an upsert (`inserisci` or `aggiornaParlante`), never
 * opening a transaction (ADR 0012).
 */
public class AttribuzioneRepositorySql(private val db: SnastroDatabase) : AttribuzioneRepository {
    override fun trova(v: VoceRef): Attribuzione? =
        db.attribuzioneQueries.trova(v.registrazioneId.valore, v.voceId.numero.toLong())
            .executeAsOneOrNull()?.let { inDominio(it.registrazione_id, it.voce_id, it.progetto_id, it.parlante_id) }

    override fun diRegistrazione(id: RegistrazioneId): List<Attribuzione> =
        db.attribuzioneQueries.trovaDiRegistrazione(id.valore).executeAsList()
            .map { inDominio(it.registrazione_id, it.voce_id, it.progetto_id, it.parlante_id) }

    override fun diParlante(id: ParlanteId): List<Attribuzione> =
        db.attribuzioneQueries.trovaDiParlante(id.valore).executeAsList()
            .map { inDominio(it.registrazione_id, it.voce_id, it.progetto_id, it.parlante_id) }

    override fun salva(a: Attribuzione) {
        val registrazioneId = a.voceRef.registrazioneId.valore
        val voceId = a.voceRef.voceId.numero.toLong()
        if (db.attribuzioneQueries.trova(registrazioneId, voceId).executeAsOneOrNull() == null) {
            db.attribuzioneQueries.inserisci(
                registrazioneId = registrazioneId,
                voceId = voceId,
                progettoId = a.progettoId.valore,
                parlanteId = a.parlanteId.valore,
            )
        } else {
            db.attribuzioneQueries.aggiornaParlante(
                parlanteId = a.parlanteId.valore,
                registrazioneId = registrazioneId,
                voceId = voceId,
            )
        }
    }

    override fun rimuovi(v: VoceRef) {
        db.attribuzioneQueries.rimuovi(v.registrazioneId.valore, v.voceId.numero.toLong())
    }
}

/** The database is trusted, nothing is re-validated (CR-15). */
@OptIn(RicostituzioneDaPersistenza::class)
private fun inDominio(registrazioneId: String, voceId: Long, progettoId: String, parlanteId: String): Attribuzione =
    Attribuzione.ricostituisci(
        voceRef = VoceRef(RegistrazioneId(registrazioneId), VoceId(voceId.toInt())),
        progettoId = ProgettoId(progettoId),
        parlanteId = ParlanteId(parlanteId),
    )
