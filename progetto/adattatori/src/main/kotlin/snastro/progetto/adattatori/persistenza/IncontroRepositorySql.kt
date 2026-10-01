package snastro.progetto.adattatori.persistenza

import snastro.kernel.IncontroId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.persistenza.SnastroDatabase
import snastro.progetto.applicazione.porte.IncontroRepository
import snastro.progetto.dominio.Incontro

/**
 * [IncontroRepository] on the `incontro` table (ADR 0034), the ONLY writer of `incontro`;
 * every write joins the caller's transaction (ADR 0012).
 * [rimuovi] is refused by the immediate FK `registrazione.incontro_id → incontro` while a Parte is left (INV-I1).
 */
public class IncontroRepositorySql(private val db: SnastroDatabase) : IncontroRepository {
    override fun trova(id: IncontroId): Incontro? =
        db.incontroQueries.trovaPerId(id.valore).executeAsOneOrNull()
            ?.let { Incontro.nuovo(IncontroId(it.id), ProgettoId(it.progetto_id)) }

    // Immutable root (AC-I15): saving it again writes nothing (INSERT OR IGNORE).
    override fun salva(i: Incontro) {
        db.incontroQueries.inserisci(id = i.id.valore, progettoId = i.progettoId.valore)
    }

    override fun rimuovi(id: IncontroId) {
        db.incontroQueries.elimina(id.valore)
    }

    override fun partiDi(id: IncontroId): List<RegistrazioneId> =
        db.registrazioneQueries.trovaDiIncontro(id.valore).executeAsList().map { RegistrazioneId(it.id) }
}
