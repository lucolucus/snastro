package snastro.architettura

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * AC-C67, static half (ADR 0030 §1, analysis X4): no adapter registers itself. Under every `<ctx>/adattatori/src/main`
 * no code line names the concrete `DispatcherEventiInMemoria` nor calls `registraSincrono`/`registraDopoCommit`: each
 * subscriber is an `AbbonatoSincrono`/`AbbonatoDopoCommit` VALUE that `:avvio`'s composition registers, in its declared
 * order. The dynamic half (nothing launches before `avvia(scope)`) is each worker subscriber's own test.
 */
class AbbonatiComeValoriTest {
    private val radice: File = File(System.getProperty("snastro.radiceProgetto") ?: "..").canonicalFile

    private val sorgentiAdattatori: List<File> by lazy {
        listOf("progetto", "trascrizione", "parlanti", "sbobinatura", "sintesi")
            .map { File(radice, "$it/adattatori/src/main") }
            .filter(File::isDirectory)
            .flatMap { it.walkTopDown().filter { f -> f.isFile && f.extension == "kt" }.toList() }
    }

    private fun righeCon(regola: Regex): List<String> = sorgentiAdattatori
        .flatMap { f -> f.readLines().mapIndexed { i, riga -> Triple(f, i + 1, riga) } }
        .filter { (_, _, riga) ->
            val codice = riga.trimStart()
            val commento = codice.startsWith("*") || codice.startsWith("//") || codice.startsWith("/*")
            !commento && regola.containsMatchIn(riga)
        }
        .map { (f, n, riga) -> "${f.relativeTo(radice)}:$n: ${riga.trim()}" }

    @Test
    fun `AC-C67 nessun file di adattatori src main importa DispatcherEventiInMemoria ne registra un abbonato`() {
        assertTrue(sorgentiAdattatori.size > 10, "la guardia deve vedere gli adattatori dei contesti")
        assertEquals(emptyList(), righeCon(Regex("""\bDispatcherEventiInMemoria\b""")))
        assertEquals(emptyList(), righeCon(Regex("""\bregistra(Sincrono|DopoCommit)\b""")))
    }
}
