package snastro.persistenza

import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * AC-9 / AC-13: the schema-level guards exist and actually reject a violating row — the ADR 0007
 * partial unique indexes (INV-4 x2, INV-16), the `attribuzione` primary key (AC-23, structural) and
 * the `impronta_vocale` unique constraint (INV-14). The repository adapters (wave 4) map the
 * resulting [SQLException] to the matching `ErroreDominio`; here we only prove the store itself
 * refuses the second row.
 */
class SchemaVincoliTest {
    @Test
    fun `AC-9 elaborazione_aperta_unica rifiuta una seconda Elaborazione aperta per la stessa Registrazione`() {
        val db = databaseInMemoria()
        val registrazioneId = db.seminaProgettoERegistrazione()
        db.elaborazioneQueries.inserisci("elab-1", registrazioneId, "in_attesa", 0L, null, null)

        assertFailsWith<SQLException> {
            db.elaborazioneQueries.inserisci("elab-2", registrazioneId, "in_corso", 1L, 1L, null)
        }
    }

    @Test
    fun `AC-9 elaborazione_completata_unica rifiuta una seconda Elaborazione completata per la stessa Registrazione`() {
        val db = databaseInMemoria()
        val registrazioneId = db.seminaProgettoERegistrazione()
        db.elaborazioneQueries.inserisci("elab-1", registrazioneId, "completata", 0L, 0L, null)

        assertFailsWith<SQLException> {
            db.elaborazioneQueries.inserisci("elab-2", registrazioneId, "completata", 1L, 1L, null)
        }
    }

    @Test
    fun `AC-9 elaborazione_aperta_unica non blocca due Registrazioni diverse`() {
        val db = databaseInMemoria()
        val progettoId = "progetto-1"
        db.progettoQueries.inserisci(progettoId, "Progetto di prova")
        val r1 = db.seminaRegistrazione(progettoId, "reg-1")
        val r2 = db.seminaRegistrazione(progettoId, "reg-2")

        db.elaborazioneQueries.inserisci("elab-1", r1, "in_attesa", 0L, null, null)
        db.elaborazioneQueries.inserisci("elab-2", r2, "in_attesa", 0L, null, null)
    }

    @Test
    fun `AC-9 parlante_nome_attivo_unico rifiuta un secondo Parlante attivo con lo stesso nome normalizzato`() {
        val db = databaseInMemoria()
        val progettoId = "progetto-1"
        db.progettoQueries.inserisci(progettoId, "Progetto di prova")
        db.parlanteQueries.inserisci("parlante-1", progettoId, "Marco", "marco", "ricorrente", "attivo")

        assertFailsWith<SQLException> {
            db.parlanteQueries.inserisci("parlante-2", progettoId, "  Marco  ", "marco", "occasionale", "attivo")
        }
    }

    @Test
    fun `AC-9 parlante_nome_attivo_unico non blocca lo stesso nome tra un attivo e un eliminato`() {
        val db = databaseInMemoria()
        val progettoId = "progetto-1"
        db.progettoQueries.inserisci(progettoId, "Progetto di prova")
        db.parlanteQueries.inserisci("parlante-1", progettoId, "Marco", "marco", "ricorrente", "eliminato")

        db.parlanteQueries.inserisci("parlante-2", progettoId, "Marco", "marco", "ricorrente", "attivo")
    }

    @Test
    fun `AC-13 attribuzione ha chiave registrazione_id voce_id e rifiuta una seconda riga`() {
        val db = databaseInMemoria()
        val progettoId = "progetto-1"
        db.progettoQueries.inserisci(progettoId, "Progetto di prova")
        val registrazioneId = db.seminaRegistrazione(progettoId, "reg-1")
        db.seminaTrascrittoConVoce(registrazioneId, numeroVoce = 1L)
        db.parlanteQueries.inserisci("parlante-1", progettoId, "Marco", "marco", "ricorrente", "attivo")
        db.parlanteQueries.inserisci("parlante-2", progettoId, "Luca", "luca", "ricorrente", "attivo")
        db.attribuzioneQueries.inserisci(registrazioneId, 1L, progettoId, "parlante-1")

        assertFailsWith<SQLException> {
            db.attribuzioneQueries.inserisci(registrazioneId, 1L, progettoId, "parlante-2")
        }
    }

    @Test
    fun `AC-13 impronta_vocale rifiuta una seconda riga con stessi parlante_id registrazione_id voce_id`() {
        val db = databaseInMemoria()
        val progettoId = "progetto-1"
        db.progettoQueries.inserisci(progettoId, "Progetto di prova")
        val registrazioneId = db.seminaRegistrazione(progettoId, "reg-1")
        db.seminaTrascrittoConVoce(registrazioneId, numeroVoce = 1L)
        db.parlanteQueries.inserisci("parlante-1", progettoId, "Marco", "marco", "ricorrente", "attivo")
        db.improntaVocaleQueries.inserisci("parlante-1", registrazioneId, 1L, byteArrayOf(1, 2, 3))

        assertFailsWith<SQLException> {
            db.improntaVocaleQueries.inserisci("parlante-1", registrazioneId, 1L, byteArrayOf(4, 5, 6))
        }
    }

    @Test
    fun `AC-13 sostituisci rimpiazza l unica impronta di quel VoceRef senza toccare le altre`() {
        val db = databaseInMemoria()
        val progettoId = "progetto-1"
        db.progettoQueries.inserisci(progettoId, "Progetto di prova")
        val registrazioneId = db.seminaRegistrazione(progettoId, "reg-1")
        db.seminaTrascrittoConVoce(registrazioneId, numeroVoce = 1L)
        db.seminaTrascrittoConVoce(registrazioneId, numeroVoce = 2L, creaTrascritto = false)
        db.parlanteQueries.inserisci("parlante-1", progettoId, "Marco", "marco", "ricorrente", "attivo")
        db.improntaVocaleQueries.inserisci("parlante-1", registrazioneId, 1L, byteArrayOf(1))
        db.improntaVocaleQueries.inserisci("parlante-1", registrazioneId, 2L, byteArrayOf(2))

        db.improntaVocaleQueries.sostituisci("parlante-1", registrazioneId, 1L, byteArrayOf(9))

        val impronte = db.improntaVocaleQueries.trovaDiParlante("parlante-1").executeAsList()
        assertEquals(
            setOf(1L to byteArrayOf(9).toList(), 2L to byteArrayOf(2).toList()),
            impronte.map { it.voce_id to it.valori.toList() }.toSet(),
        )
    }

    private fun SnastroDatabase.seminaProgettoERegistrazione(): String {
        val progettoId = "progetto-1"
        progettoQueries.inserisci(progettoId, "Progetto di prova")
        return seminaRegistrazione(progettoId, "reg-1")
    }

    private fun SnastroDatabase.seminaRegistrazione(progettoId: String, registrazioneId: String): String {
        registrazioneQueries.inserisci(
            registrazioneId,
            progettoId,
            "titolo",
            "audio/$registrazioneId.wav",
            60_000L,
            "2026-09-23",
            0L,
        )
        return registrazioneId
    }

    private fun SnastroDatabase.seminaTrascrittoConVoce(
        registrazioneId: String,
        numeroVoce: Long,
        creaTrascritto: Boolean = true,
    ) {
        if (creaTrascritto) trascrittoQueries.inserisci(registrazioneId, numeroVoce + 1, 1L)
        voceQueries.inserisci(registrazioneId, numeroVoce)
    }
}
