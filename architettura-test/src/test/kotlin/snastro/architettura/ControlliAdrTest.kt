package snastro.architettura

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Runs the ADRs' mechanical checks inside the gate (`./gradlew check` -> `:architettura-test:test`).
 *
 * The registry is the ADRs themselves: every `enforced_by` entry (`- check: <repo path>`, optional
 * `from: <block-id>`) of `.mismagent/decisions/NNNN-*.md`. Each check is a POSIX `sh` script under
 * `architettura-test/controlli-adr/`, always invoked as `sh <script> <root>`, printing
 * `ADR-NNNN <check>: PASS|FAIL` (exit 0 = PASS).
 *
 * 1. Red-green proof: every `controlli-adr/fixture/<check>/conforme*` tree must PASS, every
 *    `violante*` tree must FAIL. Fixture files carry a `.fixture` suffix on every file (and on the
 *    directories git or the scans would skip: `build`, `.gradle`, `.mismagent`), stripped when the
 *    tree is materialized under `build/tmp/controlli-adr/`; so no fixture is ever seen by a real scan,
 *    by Konsist or by git. A case holding a `.repository-git` marker is materialized as a git repo.
 * 2. The project tree: every applicable check must PASS. A check with `from:` is applicable once that
 *    block is integrated (`.mismagent/features/<f>/integrated/<from>.json` or
 *    `.mismagent/features/<f>/blocks/<side>/done/<from>.md`, the tool's own rule) or is one of
 *    [BLOCCHI_COSTRUITI_SENZA_MARCATORE]. Before that its result on the tree is reported, not enforced:
 *    the `from` block's own review runs it directly (`sh <script> .`).
 */
class ControlliAdrTest {
    private data class ControlloAdr(val adr: String, val percorso: String, val from: String?) {
        val nome: String get() = percorso.substringAfterLast('/').removeSuffix(".sh")

        /** The `<check>` part of the `ADR-NNNN <check>: PASS|FAIL` line the script prints. */
        val esitoAtteso: String get() = "ADR-$adr ${nome.removePrefix("adr-$adr-")}"
    }

    private val radice: File =
        File(System.getProperty(PROPRIETA_RADICE) ?: "..").canonicalFile

    private val cartellaControlli = File(radice, "architettura-test/controlli-adr")
    private val cartellaFixture = File(cartellaControlli, "fixture")
    private val cartellaLavoro = File(radice, "architettura-test/build/tmp/controlli-adr")

    private val controlli: List<ControlloAdr> by lazy { leggiControlliDagliAdr() }

    @Test
    fun `ogni check citato da un ADR esiste ed e uno script di controlli-adr`() {
        assertTrue(controlli.isNotEmpty(), "no enforced_by check found in .mismagent/decisions")
        controlli.forEach { c ->
            assertTrue(File(radice, c.percorso).isFile, "ADR ${c.adr}: check ${c.percorso} does not exist")
            assertTrue(
                c.percorso.startsWith("architettura-test/controlli-adr/") && c.percorso.endsWith(".sh"),
                "ADR ${c.adr}: check ${c.percorso} is not a controlli-adr/*.sh script run by this harness",
            )
        }
    }

    @Test
    fun `ogni script di controlli-adr e citato da un ADR`() {
        val citati = controlli.map { it.nome }.toSet()
        val script = cartellaControlli.listFiles { f -> f.isFile && f.name.endsWith(".sh") }.orEmpty()
        val orfani = script.map { it.name.removeSuffix(".sh") }.filter { it !in citati }
        assertEquals(emptyList(), orfani, "check scripts no ADR's enforced_by cites")
    }

    @Test
    fun `ogni controllo ha almeno una fixture conforme e una violante`() {
        controlli.forEach { c ->
            val casi = casiDi(c).map { it.name }
            assertTrue(casi.any { it.startsWith(CONFORME) }, "${c.nome}: no conforme* fixture")
            assertTrue(casi.any { it.startsWith(VIOLANTE) }, "${c.nome}: no violante* fixture")
        }
    }

    @TestFactory
    fun `ogni controllo discrimina sulle sue fixture`(): List<DynamicTest> =
        controlli.flatMap { c ->
            casiDi(c).map { caso ->
                DynamicTest.dynamicTest("ADR-${c.adr} ${c.nome} / ${caso.name}") {
                    val albero = materializza(c, caso)
                    val esito = esegui(c, albero)
                    val atteso = caso.name.startsWith(CONFORME)
                    val etichetta = if (atteso) "PASS" else "FAIL"
                    assertEquals(
                        atteso,
                        esito.passa,
                        "${c.nome} on fixture ${caso.name}: expected $etichetta\n${esito.uscita}",
                    )
                    assertTrue(
                        esito.uscita.contains("${c.esitoAtteso}: $etichetta"),
                        "${c.nome}: output does not name the result\n${esito.uscita}",
                    )
                }
            }
        }

    @TestFactory
    fun `ogni controllo applicabile passa sull albero del progetto`(): List<DynamicTest> =
        controlli.map { c ->
            DynamicTest.dynamicTest("ADR-${c.adr} ${c.nome} on the project tree") {
                val esito = esegui(c, radice)
                println(esito.uscita.trimEnd())
                if (applicabile(c)) {
                    assertTrue(esito.passa, "ADR-${c.adr} ${c.nome} fails on the project tree:\n${esito.uscita}")
                } else {
                    println(
                        "ADR-${c.adr} ${c.nome}: not yet applicable (from: ${c.from}, not integrated) - " +
                            "reported, not enforced",
                    )
                }
            }
        }

    private fun applicabile(c: ControlloAdr): Boolean {
        val from = c.from ?: return true
        val funzionalita = File(radice, ".mismagent/features").listFiles { f -> f.isDirectory }.orEmpty()
        return from in BLOCCHI_COSTRUITI_SENZA_MARCATORE ||
            funzionalita.any { f ->
                val lati = File(f, "blocks").listFiles { d -> d.isDirectory }.orEmpty()
                File(f, "integrated/$from.json").isFile || lati.any { File(it, "done/$from.md").isFile }
            }
    }

    private fun leggiControlliDagliAdr(): List<ControlloAdr> {
        val adr = File(radice, ".mismagent/decisions").listFiles { f -> f.name.matches(NOME_ADR) }.orEmpty()
        return adr.sortedBy { it.name }.flatMap { file ->
            val numero = file.name.take(CIFRE_ADR)
            val righe = frontmatter(file)
            righe.mapIndexedNotNull { i, riga ->
                RIGA_CHECK.find(riga)?.let { m ->
                    val from = righe.getOrNull(i + 1)?.let { RIGA_FROM.find(it)?.groupValues?.get(1) }
                    ControlloAdr(numero, m.groupValues[1], from)
                }
            }
        }
    }

    private fun frontmatter(file: File): List<String> {
        val righe = file.readLines()
        if (righe.firstOrNull()?.trim() != "---") return emptyList()
        val fine = righe.drop(1).indexOfFirst { it.trim() == "---" }
        return if (fine < 0) emptyList() else righe.subList(1, fine + 1)
    }

    private fun casiDi(c: ControlloAdr): List<File> =
        File(cartellaFixture, c.nome).listFiles { f -> f.isDirectory }.orEmpty().sortedBy { it.name }

    private fun materializza(c: ControlloAdr, caso: File): File {
        val destinazione = File(cartellaLavoro, "${c.nome}/${caso.name}")
        destinazione.deleteRecursively()
        destinazione.mkdirs()
        caso.walkTopDown().filter { it.isFile && it.name != MARCATORE_GIT }.forEach { sorgente ->
            val relativo = sorgente.relativeTo(caso).invariantSeparatorsPath
            if (!relativo.endsWith(SUFFISSO)) {
                fail("${c.nome}/${caso.name}: fixture file $relativo lacks the $SUFFISSO suffix")
            }
            val reale = relativo.split('/').joinToString("/") { it.removeSuffix(SUFFISSO) }
            sorgente.copyTo(File(destinazione, reale), overwrite = true)
        }
        if (File(caso, MARCATORE_GIT).isFile) {
            comando(destinazione, "git", "init", "-q")
            comando(destinazione, "git", "add", "-f", "-A")
        }
        return destinazione
    }

    private data class Esito(val passa: Boolean, val uscita: String)

    private fun esegui(c: ControlloAdr, albero: File): Esito {
        val processo = ProcessBuilder("sh", File(radice, c.percorso).path, albero.path)
            .directory(radice)
            .redirectErrorStream(true)
            .start()
        val uscita = processo.inputStream.bufferedReader().readText()
        if (!processo.waitFor(TIMEOUT_SECONDI, TimeUnit.SECONDS)) {
            processo.destroyForcibly()
            fail("${c.nome}: timed out after ${TIMEOUT_SECONDI}s")
        }
        return Esito(processo.exitValue() == 0, uscita)
    }

    private fun comando(cartella: File, vararg argomenti: String) {
        val processo = ProcessBuilder(*argomenti).directory(cartella).redirectErrorStream(true).start()
        val uscita = processo.inputStream.bufferedReader().readText()
        val finito = processo.waitFor(TIMEOUT_SECONDI, TimeUnit.SECONDS)
        assertTrue(finito && processo.exitValue() == 0, "${argomenti.joinToString(" ")} failed in $cartella\n$uscita")
    }

    private companion object {
        const val PROPRIETA_RADICE = "snastro.radiceProgetto"
        const val CONFORME = "conforme"
        const val VIOLANTE = "violante"
        const val SUFFISSO = ".fixture"
        const val MARCATORE_GIT = ".repository-git"
        const val CIFRE_ADR = 4
        const val TIMEOUT_SECONDI = 60L
        val NOME_ADR = Regex("""^\d{4}-.+\.md$""")
        val RIGA_CHECK = Regex("""^\s*-\s*check:\s*(\S+)""")
        val RIGA_FROM = Regex("""^\s+from:\s*(\S+)""")

        /**
         * `from` blocks of `trascrizione-con-parlanti` (legacy manifest): built and merged, but their
         * block files never moved to `done/` and no `integrated/` marker exists.
         */
        val BLOCCHI_COSTRUITI_SENZA_MARCATORE = setOf(
            "persistenza-schema",
            "persistenza-ritrascrivi",
            "diarizzatore-sherpa",
            "persistenza-elimina-registrazione",
        )
    }
}
