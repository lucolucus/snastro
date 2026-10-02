package snastro.progetto.adattatori.persistenza

import org.junit.jupiter.api.Test
import snastro.kernel.Esito
import snastro.kernel.IncontroId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.kernel.atteso
import snastro.persistenza.SnastroDatabase
import snastro.persistenza.UnitaDiLavoroSql
import snastro.persistenza.databaseInMemoria
import snastro.persistenza.seminaRegistrazioneDiProva
import snastro.progetto.applicazione.porte.RegistrazioneRepository
import snastro.progetto.applicazione.porte.RegistrazioneRepositoryContratto
import snastro.progetto.dominio.Incontro
import snastro.progetto.dominio.Registrazione
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** D2 (dev-architecture-app.md#porta-contratto): the contract passes real-on-real (AC-109). */
class RegistrazioneRepositorySqlTest : RegistrazioneRepositoryContratto() {
    override fun ambiente(): Ambiente {
        val db = databaseInMemoria()
        val progetto = ProgettoId("progetto-1")
        db.progettoQueries.inserisci(progetto.valore, "Progetto di prova")
        return object : Ambiente {
            override val registrazioni = ConIncontro(db, RegistrazioneRepositorySql(db))
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
     * AC-I55 (ADR 0033 §6): saving a Registrazione never writes `incontro`, [IncontroRepositorySql] is its only
     * writer. Without the Incontro row the immediate FK refuses the Parte (nothing is left); with it, a save leaves it.
     */
    @Test
    fun `AC-I55 salvare una Registrazione non crea l'Incontro`() {
        val db = databaseInMemoria()
        val progetto = ProgettoId("progetto-1")
        db.progettoQueries.inserisci(progetto.valore, "Progetto di prova")
        val repo = RegistrazioneRepositorySql(db)
        val uow = UnitaDiLavoroSql(db)
        val r = unaRegistrazione(progetto, RegistrazioneId("reg-1"))

        val rifiuto = assertFails { uow.inTransazione { Esito.Ok(repo.salva(r)) } }
        val messaggi = generateSequence(rifiuto) { it.cause }.mapNotNull { it.message }.toList()
        assertTrue(messaggi.any { "FOREIGN KEY constraint failed" in it }, "il rifiuto e' la FK: $messaggi")
        assertEquals(0, contaIncontri(db))
        assertNull(repo.trova(r.id))

        IncontroRepositorySql(db).salva(Incontro.nuovo(r.incontroId, progetto))
        uow.inTransazione { Esito.Ok(repo.salva(r)) }.atteso()
        uow.inTransazione { Esito.Ok(repo.salva(r)) }.atteso()
        assertEquals(1, contaIncontri(db))
        val incontroSalvato = db.registrazioneQueries.trovaPerId("reg-1").executeAsOne().incontro_id
        assertEquals(r.incontroId.valore, incontroSalvato)
    }

    @Test
    fun `AC-I55 rimuovere l'ultima Parte lascia l'Incontro a IncontroRepository`() {
        val db = databaseInMemoria()
        val progetto = ProgettoId("progetto-1")
        db.progettoQueries.inserisci(progetto.valore, "Progetto di prova")
        val repo = RegistrazioneRepositorySql(db)
        val r = unaRegistrazione(progetto, RegistrazioneId("reg-1"))
        IncontroRepositorySql(db).salva(Incontro.nuovo(r.incontroId, progetto))
        repo.salva(r)

        repo.rimuovi(r.id)

        assertEquals(1, contaIncontri(db), "solo IncontroRepository.rimuovi toglie l'Incontro")
        assertNull(repo.trova(r.id))
    }

    /** The database is trusted (CR-15): a corrupt stored time reads as the empty time, it does not break the list. */
    @Test
    fun `AC-I55 un'ora_di_inizio illeggibile si legge come vuota senza rompere la lista`() {
        val db = databaseInMemoria()
        val progetto = ProgettoId("progetto-1")
        db.progettoQueries.inserisci(progetto.valore, "Progetto di prova")
        db.incontroQueries.inserisci(id = "incontro-1", progettoId = progetto.valore)
        for ((id, ora) in listOf("reg-1" to "25:99:00", "reg-2" to null)) {
            db.registrazioneQueries.inserisci(
                id = id,
                progettoId = progetto.valore,
                incontroId = "incontro-1",
                titolo = id,
                riferimentoAudio = "audio/$id.wav",
                durataMs = 1L,
                dataRegistrazione = "2026-02-12",
                aggiuntaAlle = 0L,
                oraDiInizio = ora,
            )
        }

        assertEquals(listOf(null, null), RegistrazioneRepositorySql(db).delProgetto(progetto).map { it.oraDiInizio })
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

/** The import saves the Incontro before its Parti (the real writer does it through the port): same for the contract. */
private class ConIncontro(
    db: SnastroDatabase,
    private val delega: RegistrazioneRepository,
) : RegistrazioneRepository by delega {
    private val incontri = IncontroRepositorySql(db)

    override fun salva(r: Registrazione) {
        incontri.salva(Incontro.nuovo(r.incontroId, r.progettoId))
        delega.salva(r)
    }
}
