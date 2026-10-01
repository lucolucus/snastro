package snastro.persistenza

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ADR 0033 §4.1/§6 (incontro-chiavi): the repository queries are keyed by `incontro_id` directly. Scans the shipped
 * `.sq` query files (the migrations are the schema, never scanned): only Progetto's own `registrazione`/`incontro`
 * queries may read the `registrazione` table, and no UPDATE names `incontro_id` (D-0028, the immutability trigger).
 */
class QueryChiaviIncontroTest {
    private val cartella = File("src/main/sqldelight/snastro/persistenza")

    /** Every named statement of every `.sq`, comments stripped: file name to statement text. */
    private fun istruzioni(): List<Pair<String, String>> {
        val file = cartella.listFiles { f -> f.extension == "sq" }.orEmpty().sortedBy { it.name }
        assertTrue(file.size > 10, "nessun .sq trovato in ${cartella.absolutePath}")
        return file.flatMap { f ->
            val senzaCommenti = f.readLines().filterNot { it.trimStart().startsWith("--") }.joinToString("\n")
            senzaCommenti.split(';').map { it.trim() }.filter { it.isNotEmpty() }.map { f.name to it }
        }
    }

    @Test
    fun `AC-I13 AC-I208 solo le query di Progetto leggono la tabella registrazione`() {
        val tabellaRegistrazione = Regex("""\bregistrazione\b(?!_)""")
        val violazioni = istruzioni()
            .filter { (file, _) -> file !in setOf("Registrazione.sq", "Incontro.sq") }
            .filter { (_, sql) -> tabellaRegistrazione.containsMatchIn(sql.substringAfter(':')) }
        assertEquals(emptyList(), violazioni)
    }

    @Test
    fun `AC-I209 nessuna UPDATE nomina incontro_id nel suo SET`() {
        val violazioni = istruzioni().filter { (_, sql) ->
            val update = Regex("""\bUPDATE\b(.*?)\bSET\b(.*?)(\bWHERE\b|$)""", RegexOption.DOT_MATCHES_ALL).find(sql)
            update != null && "incontro_id" in update.groupValues[2]
        }
        assertEquals(emptyList(), violazioni)
    }
}
