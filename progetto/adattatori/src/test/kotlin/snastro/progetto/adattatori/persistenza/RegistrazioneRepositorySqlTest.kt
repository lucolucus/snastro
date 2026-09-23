package snastro.progetto.adattatori.persistenza

import org.junit.jupiter.api.Test
import snastro.kernel.ProgettoId
import snastro.persistenza.UnitaDiLavoroSql
import snastro.persistenza.databaseInMemoria
import snastro.progetto.applicazione.porte.RegistrazioneRepositoryContratto
import kotlin.test.assertEquals

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
        db.registrazioneQueries.inserisci(
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
}
