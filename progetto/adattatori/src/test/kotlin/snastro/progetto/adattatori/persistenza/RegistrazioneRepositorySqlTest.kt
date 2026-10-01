package snastro.progetto.adattatori.persistenza

import org.junit.jupiter.api.Test
import snastro.kernel.ErroreDiProva
import snastro.kernel.Esito
import snastro.kernel.IncontroId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.persistenza.SnastroDatabase
import snastro.persistenza.UnitaDiLavoroSql
import snastro.persistenza.databaseInMemoria
import snastro.persistenza.seminaRegistrazioneDiProva
import snastro.progetto.applicazione.porte.RegistrazioneRepositoryContratto
import snastro.progetto.dominio.Registrazione
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/** D2 (dev-architecture-app.md#porta-contratto): the contract passes real-on-real (AC-109). */
class RegistrazioneRepositorySqlTest : RegistrazioneRepositoryContratto() {
    override fun ambiente(): Ambiente {
        val db = databaseInMemoria()
        val progetto = ProgettoId("progetto-1")
        db.progettoQueries.inserisci(progetto.valore, "Progetto di prova")
        return object : Ambiente {
            override val registrazioni = RegistrazioneRepositorySql(db)
            override val unitaDiLavoro = UnitaDiLavoroSql(db)
            override val progettoId = progetto
        }
    }

    /**
     * AC-326: `titoliDelProgetto` reads the sole `titolo` column — a row with a `data_registrazione`
     * that cannot even parse as a [java.time.LocalDate] (seeded straight through
     * `registrazioneQueries`, bypassing the aggregate) proves it: were this path reconstituting a
     * `Registrazione` (dev-architecture-app.md#repository's `inDominio`), it would throw here.
     */
    @Test
    fun `AC-326 titoliDelProgetto legge solo la colonna titolo, senza ricostituire la Registrazione`() {
        val db = databaseInMemoria()
        val progetto = ProgettoId("progetto-1")
        db.progettoQueries.inserisci(progetto.valore, "Progetto di prova")
        db.seminaRegistrazioneDiProva(
            id = "reg-1",
            progettoId = progetto.valore,
            titolo = "Seduta di marzo",
            riferimentoAudio = "audio/reg-1.wav",
            durataMs = 60_000L,
            dataRegistrazione = "non-una-data",
            aggiuntaAlle = 0L,
        )

        assertEquals(listOf("Seduta di marzo"), RegistrazioneRepositorySql(db).titoliDelProgetto(progetto))
    }

    /**
     * AC-I6 (ADR 0033 §6 transition): a Registrazione saved by the repository is the one Parte of its OWN new
     * Incontro, written in the same transaction (a rolled-back save leaves no Incontro either) — and the repository
     * reads `incontro_id` from the column, never assuming it equals the Registrazione's id.
     */
    @Test
    fun `AC-I6 salvare una nuova Registrazione crea il suo Incontro con una sola Parte nella stessa transazione`() {
        val db = databaseInMemoria()
        val progetto = ProgettoId("progetto-1")
        db.progettoQueries.inserisci(progetto.valore, "Progetto di prova")
        val repo = RegistrazioneRepositorySql(db)
        val uow = UnitaDiLavoroSql(db)
        val r = unaRegistrazione(progetto, RegistrazioneId("reg-1"))

        uow.inTransazione<Unit> {
            repo.salva(r)
            Esito.Errore(ErroreDiProva.Fallito("annullato"))
        }.erroreAtteso<ErroreDiProva.Fallito>()
        assertEquals(0, contaIncontri(db), "un salva annullato non lascia un Incontro")

        uow.inTransazione { Esito.Ok(repo.salva(r)) }.atteso()
        val incontroId = checkNotNull(db.registrazioneQueries.trovaPerId("reg-1").executeAsOne().incontro_id)
        assertEquals(1, contaIncontri(db))
        assertEquals(progetto.valore, db.incontroQueries.trovaPerId(incontroId).executeAsOne().progetto_id)
        assertNotEquals(r.id.valore, incontroId, "l'Incontro ha un id proprio")

        uow.inTransazione { Esito.Ok(repo.salva(r)) }.atteso()
        assertEquals(1, contaIncontri(db), "un salva successivo non crea altri Incontri")
    }

    @Test
    fun `AC-I6 rimuovere l'ultima Parte toglie anche il suo Incontro`() {
        val db = databaseInMemoria()
        val progetto = ProgettoId("progetto-1")
        db.progettoQueries.inserisci(progetto.valore, "Progetto di prova")
        val repo = RegistrazioneRepositorySql(db)
        val r = unaRegistrazione(progetto, RegistrazioneId("reg-1"))
        repo.salva(r)

        repo.rimuovi(r.id)

        assertEquals(0, contaIncontri(db))
        assertNull(repo.trova(r.id))
    }

    private fun contaIncontri(db: SnastroDatabase): Int =
        db.incontroQueries.trovaDelProgetto("progetto-1").executeAsList().size

    private fun unaRegistrazione(progetto: ProgettoId, id: RegistrazioneId): Registrazione =
        Registrazione.aggiungi(
            id = id,
            progettoId = progetto,
            incontroId = IncontroId("incontro-di-${id.valore}"),
            titolo = "Seduta di marzo",
            riferimentoAudio = RiferimentoAudio("audio/${id.valore}.m4a"),
            durataMs = 3_600_000,
            dataRegistrazione = LocalDate.of(2026, 2, 12),
            aggiuntaAlle = Instant.parse("2026-09-23T10:15:30.123Z"),
        ).aggregato
}
