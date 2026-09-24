package snastro.parlanti.adattatori.persistenza

import org.junit.jupiter.api.io.TempDir
import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.atteso
import snastro.parlanti.dominio.Impronta
import snastro.parlanti.dominio.Nome
import snastro.parlanti.dominio.Parlante
import snastro.parlanti.dominio.TipoParlante
import snastro.persistenza.apriDatabaseProgetto
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * AC-116 / R23 (ADR 0009 amendment): after `EliminaParlante` (here simulated by the caller's
 * [snastro.kernel.UnitaDiLavoro] transaction shape, a `db.transaction { }` wrapping [ParlanteRepositorySql.salva]
 * of an `eliminato` Parlante) no `impronta_vocale` row of it remains, and the WAL has been merged into
 * `progetto.db` and truncated — `PRAGMA wal_checkpoint(TRUNCATE)` never runs while that transaction is
 * still open (it would be a no-op / partial checkpoint), only once it has committed.
 */
class ParlanteRepositorySqlCheckpointTest {
    @Test
    fun `AC-116 dopo EliminaParlante non resta nessuna riga impronta_vocale e il WAL e troncato dopo il commit`(
        @TempDir cartella: File,
    ) {
        val database = apriDatabaseProgetto(cartella)
        try {
            val db = database.database
            db.progettoQueries.inserisci("progetto-1", "Progetto di prova")
            db.registrazioneQueries.inserisci(
                id = "registrazione-1",
                progettoId = "progetto-1",
                titolo = "Registrazione di prova",
                riferimentoAudio = "audio/registrazione-1.wav",
                durataMs = 600_000L,
                dataRegistrazione = "2026-09-23",
                aggiuntaAlle = 0L,
            )
            db.trascrittoQueries.inserisci(
                registrazioneId = "registrazione-1",
                prossimaVoce = 1L,
                prossimoSegmento = 1L,
            )
            db.voceQueries.inserisci(registrazioneId = "registrazione-1", numero = 1L)

            val repo = ParlanteRepositorySql(db)
            val progettoId = ProgettoId("progetto-1")
            val voceRef = VoceRef(RegistrazioneId("registrazione-1"), VoceId(1))
            val p = Parlante.crea(
                ParlanteId("id-1"),
                progettoId,
                Nome.di("Marco").atteso(),
                TipoParlante.RICORRENTE,
            ).aggregato
            p.registraImpronta(voceRef, Impronta(floatArrayOf(1f, 2f, 3f)), "0-1000", "modello-1").atteso()
            repo.salva(p).atteso()
            assertEquals(1, repo.impronteDiRegistrazione(RegistrazioneId("registrazione-1")).size)

            p.elimina().atteso()
            db.transaction { repo.salva(p).atteso() } // mirrors UnitaDiLavoroSql wrapping EliminaParlanteServizio

            assertEquals(emptyList(), repo.impronteDiRegistrazione(RegistrazioneId("registrazione-1")))
            val wal = File(cartella, "progetto.db-wal")
            assertTrue(
                !wal.exists() || wal.length() == 0L,
                "il WAL deve essere stato troncato dopo il commit: ${wal.length()} byte",
            )
        } finally {
            database.chiudi()
        }
    }
}
