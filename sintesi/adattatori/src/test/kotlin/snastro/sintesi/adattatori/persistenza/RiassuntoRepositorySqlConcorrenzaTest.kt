package snastro.sintesi.adattatori.persistenza

import org.junit.jupiter.api.io.TempDir
import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.kernel.atteso
import snastro.persistenza.SnastroDatabase
import snastro.persistenza.UnitaDiLavoroSql
import snastro.persistenza.apriDatabaseProgetto
import snastro.sintesi.applicazione.porte.conAvvio
import snastro.sintesi.applicazione.porte.conCompletamento
import snastro.sintesi.applicazione.porte.unRiassunto
import snastro.sintesi.applicazione.porte.unaStruttura
import snastro.sintesi.dominio.BozzaElemento
import snastro.sintesi.dominio.BozzaRiassunto
import java.io.File
import java.sql.DriverManager
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Carry-over (pre-release L63, ADR 0022 §4 "CAS race cases"): an N-thread + start-barrier CAS race,
 * meaningful only on this real SQL D2 — `databaseInMemoria()` never contends, only a real FILE database
 * makes `BEGIN IMMEDIATE` (`persistenza/AperturaDatabase.kt`) serialise concurrent writers. Two
 * [UnitaDiLavoroSql] threads race [RiassuntoRepositorySql.concludi] (an `in_corso` Riassunto completing
 * to `pronto`) against [RiassuntoRepositorySql.rimuoviDiRegistrazione] (as the eliminazione-registrazione
 * / sostituzione-trascritto policies call it) on the SAME Registrazione, repeated [RIPETIZIONI] times on
 * fresh rows.
 *
 * `riassunto_elemento`/`riassunto_fonte` carry a real, non-deferrable FK to `riassunto`/`riassunto_elemento`
 * (`migrations/6.sqm`): an ordering bug in either method would surface as a THROWN constraint violation,
 * never a silent orphan, and only one Riassunto row ever exists per round — so a round with no thrown
 * exception already rules out "two pronto" and "a pronto without its children" leaving an orphan; the
 * empty-elemento/fonte assertions below make that explicit, and the empty-diRegistrazione assertion is
 * the resurrection check.
 *
 * A84: each round now seeds a PREVIOUS `pronto` Riassunto of the SAME Registrazione BEFORE the race — with
 * no earlier `pronto`, `riassunto_pronto_unico` could never actually be at risk of holding two rows, so the
 * name's "never two pronto" claim could not fail either way (`concludi`'s own `rimuoviPrecedentePronto` step
 * had nothing to race). The `contaPronto` assertion below queries the raw row count after every round — the
 * one place that actually states "never two" — and the outcome-split assertion at the end confirms this
 * really is a two-way race ([RIPETIZIONI] is tuned so both orderings occur; ADR 0028 §3's `attendiFinche`
 * does not apply to a one-shot thread race like this one).
 */
class RiassuntoRepositorySqlConcorrenzaTest {
    @Test
    fun `AC-S113 concludi contro rimuoviDiRegistrazione su una riga in_corso mai una riga resuscitata ne due pronto`(
        @TempDir cartella: File,
    ) {
        val database = apriDatabaseProgetto(cartella)
        try {
            val db = database.database
            val uow = UnitaDiLavoroSql(db)
            val repo = RiassuntoRepositorySql(db, uow)
            val esiti = mutableMapOf<String, Int>()

            repeat(RIPETIZIONI) { giro -> eseguiGiro(cartella, db, uow, repo, giro, esiti) }

            assertEquals(RIPETIZIONI, esiti.values.sum(), "ogni giro finisce in uno dei due esiti: $esiti")
            assertTrue(
                esiti.keys.containsAll(ESITI_ATTESI),
                "entrambi gli ordini di completamento devono occorrere su $RIPETIZIONI giri: $esiti",
            )
        } finally {
            database.chiudi()
        }
    }

    /** One round: seeds a previous `pronto` + races `concludi` against `rimuoviDiRegistrazione` on a fresh
     * `riassunto-$giro`, then asserts the invariants (A84) and tallies [esiti]. */
    @Suppress("LongParameterList") // one parameter per collaborator the race needs + the shared esiti tally
    private fun eseguiGiro(
        cartella: File,
        db: SnastroDatabase,
        uow: UnitaDiLavoroSql,
        repo: RiassuntoRepositorySql,
        giro: Int,
        esiti: MutableMap<String, Int>,
    ) {
        val registrazioneId = RegistrazioneId("registrazione-$giro")
        seminaFile(cartella, "progetto-$giro", registrazioneId.valore)
        val precedente = unRiassunto("precedente-$giro", registrazioneId).conAvvio()
            .conCompletamento(BOZZA, unaStruttura(1 to 1))
        repo.salva(precedente).atteso()
        val r = unRiassunto("riassunto-$giro", registrazioneId).conAvvio()
        repo.salva(r).atteso()
        r.conCompletamento(BOZZA, unaStruttura(1 to 1))

        val via = CyclicBarrier(2)
        val concludiEsito = AtomicReference<Result<Esito<Boolean>>>()
        val rimozioneEsito = AtomicReference<Result<Esito<Int>>>()

        val a = thread(name = "concludi-$giro") {
            concludiEsito.set(
                runCatching {
                    via.await(ATTESA_S, TimeUnit.SECONDS)
                    uow.inTransazione { repo.concludi(r) }
                },
            )
        }
        val b = thread(name = "rimuovi-$giro") {
            rimozioneEsito.set(
                runCatching {
                    via.await(ATTESA_S, TimeUnit.SECONDS)
                    uow.inTransazione { repo.rimuoviDiRegistrazione(registrazioneId) }
                },
            )
        }
        a.join(ATTESA_S * MILLIS)
        b.join(ATTESA_S * MILLIS)

        val concludi = checkNotNull(concludiEsito.get()) { "giro $giro: nessun esito di concludi" }
            .getOrElse { fail("giro $giro: concludi ha lanciato", it) }
            .atteso()
        val rimozione = checkNotNull(rimozioneEsito.get()) { "giro $giro: nessun esito di rimozione" }
            .getOrElse { fail("giro $giro: rimozione ha lanciato", it) }
            .atteso()

        // concludi wins (Ok(true)): its OWN rimuoviPrecedentePronto step already removed `precedente`
        // (D-0003), so rimuoviDiRegistrazione — running second — finds only `r` left (1 row). rimuovi
        // wins (Ok(false), r absent when concludi re-reads): both `precedente` and `r` are still there
        // when rimuoviDiRegistrazione runs first (2 rows).
        assertEquals(if (concludi) 1 else 2, rimozione, "giro $giro: righe rimosse coerenti con l'esito")
        assertEquals(emptyList(), repo.diRegistrazione(registrazioneId), "giro $giro: riga resuscitata")
        assertTrue(elementiOrfani(db, r.id.valore).isEmpty(), "giro $giro: elemento orfano di riassunto-$giro")
        assertTrue(fontiOrfane(db, r.id.valore).isEmpty(), "giro $giro: fonte orfana di riassunto-$giro")
        assertTrue(
            elementiOrfani(db, precedente.id.valore).isEmpty(),
            "giro $giro: elemento orfano di precedente-$giro",
        )
        assertTrue(fontiOrfane(db, precedente.id.valore).isEmpty(), "giro $giro: fonte orfana di precedente-$giro")
        // A84: the actual "never two pronto" claim — checked directly against the raw row count, not
        // inferred from diRegistrazione being empty (which would hold even if the assertion above the
        // index enforces uniqueness had silently degenerated to something else).
        assertTrue(contaPronto(db, registrazioneId.valore) <= 1, "giro $giro: due pronto contemporanei")
        esiti.merge(if (concludi) "completato-poi-rimosso" else "rimosso-prima-del-completamento", 1, Int::plus)
    }

    private fun elementiOrfani(db: SnastroDatabase, riassuntoId: String) =
        db.riassuntoElementoQueries.trovaDiRiassunto(riassuntoId).executeAsList()

    private fun fontiOrfane(db: SnastroDatabase, riassuntoId: String) =
        db.riassuntoFonteQueries.trovaDiRiassunto(riassuntoId).executeAsList()

    /** A84: the raw count of `pronto` rows of [registrazioneId] — through the already-named `trovaDiRegistrazione`
     * (ADR 0006 confined SQL: no new query added for this), never `riassuntoQueries.trovaPerId` alone. */
    private fun contaPronto(db: SnastroDatabase, registrazioneId: String) =
        db.riassuntoQueries.trovaDiRegistrazione(registrazioneId).executeAsList().count { it.stato == "pronto" }

    /** Never `progettoQueries`/`registrazioneQueries` (ADR 0021 clause 2): a plain JDBC connection to the
     * SAME file, opened/closed BEFORE the race starts. */
    private fun seminaFile(cartella: File, progettoId: String, registrazioneId: String) {
        DriverManager.getConnection("jdbc:sqlite:${File(cartella, "progetto.db").absolutePath}").use { conn ->
            conn.createStatement().use { st ->
                st.execute("INSERT INTO progetto(id, nome) VALUES ('$progettoId', 'Progetto di prova')")
                st.execute(
                    "INSERT INTO registrazione(id, progetto_id, titolo, riferimento_audio, durata_ms, " +
                        "data_registrazione, aggiunta_alle) VALUES ('$registrazioneId', '$progettoId', 't', " +
                        "'audio/$registrazioneId.wav', 1000, '2026-09-26', 0)",
                )
            }
        }
    }

    private companion object {
        const val RIPETIZIONI = 50
        const val ATTESA_S = 15L
        const val MILLIS = 1_000L
        val ESITI_ATTESI = setOf("completato-poi-rimosso", "rimosso-prima-del-completamento")
        val BOZZA = BozzaRiassunto(
            sommario = null,
            decisioni = listOf(BozzaElemento("una decisione.", listOf(1), null)),
            questioniAperte = emptyList(),
            azioni = emptyList(),
            puntiChiave = emptyList(),
        )
    }
}
