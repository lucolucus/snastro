package snastro.sintesi.adattatori.persistenza

import org.junit.jupiter.api.io.TempDir
import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.persistenza.UnitaDiLavoroSql
import snastro.persistenza.apriDatabaseProgetto
import snastro.persistenza.scriviDatabaseV7
import snastro.sintesi.dominio.StrutturaIncontro
import snastro.sintesi.dominio.StrutturaTrascritto
import java.nio.file.Path
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * INV-I3 (ADR 0034 §4): a `pronto` Riassunto written by the previous release (schema 7, `struttura` = the bare
 * StrutturaTrascritto key) is NOT `superato` after the real `apriDatabaseProgetto` migration. Proven with the
 * domain's own predicate on the migrated rows: the Riassunto is read through [RiassuntoRepositorySql] (the stored
 * key is taken verbatim, no prefix is stripped) and judged against the StrutturaIncontro built from the migrated
 * `segmento` rows by the domain, never a key rebuilt in SQL.
 */
class RiassuntoMigratoDaV7Test {
    @Test
    fun `INV-I3 il Riassunto pronto di un progetto v7 migrato non e superato con il predicato del dominio`(
        @TempDir cartella: Path,
    ) {
        scriviDatabaseV7(cartella.toFile(), RIGHE_V7)
        val progetto = apriDatabaseProgetto(cartella.toFile())
        try {
            val db = progetto.database
            val riassunti = RiassuntoRepositorySql(db, UnitaDiLavoroSql(db)).trova(IncontroId("reg-1"))
            val pronto = assertNotNull(riassunti.singleOrNull { it.pronto })

            val corrente = StrutturaIncontro(listOf(RegistrazioneId("reg-1") to strutturaMigrata(cartella)))

            val motivo = "pronto 1-Parte come oggi: ${pronto.struttura} vs ${corrente.chiave}"
            assertFalse(pronto.superato(corrente), motivo)
        } finally {
            progetto.chiudi()
        }
    }

    /** The Trascritto of reg-1 as it stands in the migrated file, built by the domain from the raw rows. */
    private fun strutturaMigrata(cartella: Path): StrutturaTrascritto {
        val coppie = mutableListOf<Pair<SegmentoId, VoceId>>()
        DriverManager.getConnection("jdbc:sqlite:${cartella.resolve("progetto.db")}").use { c ->
            val r = c.createStatement().executeQuery(
                "SELECT numero, voce_numero FROM segmento WHERE registrazione_id = 'reg-1' ORDER BY numero",
            )
            while (r.next()) coppie += SegmentoId(r.getInt(1)) to VoceId(r.getInt(2))
        }
        return StrutturaTrascritto.di(coppie)
    }

    private companion object {
        /** reg-1: a revised Trascritto (Voci 1..3, `prossima_voce` 7) and a `pronto` Riassunto keyed as v7 did. */
        val RIGHE_V7 = listOf(
            "INSERT INTO progetto(id, nome) VALUES ('progetto-1', 'Progetto')",
            "INSERT INTO registrazione(id, progetto_id, titolo, riferimento_audio, durata_ms, data_registrazione, " +
                "aggiunta_alle) VALUES ('reg-1', 'progetto-1', 't', 'audio/reg-1.wav', 1000, '2026-09-25', 0)",
            "INSERT INTO trascritto(registrazione_id, prossima_voce, prossimo_segmento) VALUES ('reg-1', 7, 5)",
            "INSERT INTO voce(registrazione_id, numero) VALUES ('reg-1', 1), ('reg-1', 2), ('reg-1', 3)",
            "INSERT INTO segmento(registrazione_id, numero, voce_numero, inizio_ms, fine_ms, testo, confermato) " +
                "VALUES ('reg-1', 1, 1, 0, 900, 'a', 1), ('reg-1', 2, 2, 900, 1800, 'b', 0), " +
                "('reg-1', 3, 1, 1800, 2700, 'c', 0), ('reg-1', 4, 3, 2700, 3600, 'd', 0)",
            "INSERT INTO riassunto(id, registrazione_id, stato, argomento, lunghezza_massima_parole, richiesto_alle, " +
                "avviato_alle, motivo_fallimento, sommario, omessi, struttura) VALUES " +
                "('r-pronto', 'reg-1', 'pronto', NULL, 2000, 0, 1, NULL, 'sommario', 0, '1:1,2:2,3:1,4:3')",
        )
    }
}
