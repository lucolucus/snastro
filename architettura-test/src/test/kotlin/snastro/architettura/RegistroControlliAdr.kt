package snastro.architettura

import java.io.File

/** One `enforced_by` entry of ADR [adr]: the script at [percorso] (repo-relative), written by block [from]. */
internal data class ControlloAdr(val adr: String, val percorso: String, val from: String?) {
    val nome: String get() = percorso.substringAfterLast('/').removeSuffix(".sh")

    /** The `<check>` part of the `ADR-NNNN <check>: PASS|FAIL` line the script prints. */
    val esitoAtteso: String get() = "ADR-$adr ${nome.removePrefix("adr-$adr-")}"
}

/**
 * The ADRs' checks under [radice], read from the `enforced_by` of `.mismagent/decisions/NNNN-*.md`
 * (block list `- check: <path>` + `from: <block>`, flow items `- {check: <path>, from: <block>}`, or a flow list on
 * the `enforced_by:` line itself; quoted values are unquoted).
 *
 * A check is [differiti] (deferred, the rule of `MM lint --adrs`) when its path is a controlli-adr script (`.sh`),
 * nothing exists there yet, and its `from` is a block that exists ([bloccoNoto]) but is not [applicabile]: the block
 * that writes it is still to be built. Every other check is [daVerificare]: an existing path is always tested (a
 * directory fails the existence test), and a missing one fails once its `from` is integrated, when it has no
 * `from` or when `from` names no block (a typo or a deleted block is never deferred forever: fail-closed).
 */
internal class RegistroControlliAdr(private val radice: File) {
    val controlli: List<ControlloAdr> by lazy { leggiControlliDagliAdr() }
    val differiti: List<ControlloAdr> by lazy {
        controlli.filter {
            it.from != null && eScriptDiControllo(it.percorso) && !File(radice, it.percorso).exists() &&
                bloccoNoto(it.from) && !applicabile(it)
        }
    }
    val daVerificare: List<ControlloAdr> by lazy { controlli - differiti.toSet() }

    /**
     * Applicable to the project tree: no `from`, or its block is integrated
     * (`.mismagent/features/<f>/integrated/<from>.json` or `.mismagent/features/<f>/blocks/<side>/done/<from>.md`,
     * the tool's own rule), is one of [BLOCCHI_COSTRUITI_SENZA_MARCATORE], or names no known block at all
     * ([bloccoNoto]: never deferred).
     */
    fun applicabile(c: ControlloAdr): Boolean {
        val from = c.from ?: return true
        return from in BLOCCHI_COSTRUITI_SENZA_MARCATORE || !bloccoNoto(from) ||
            funzionalita().any { f ->
                val lati = File(f, "blocks").listFiles { d -> d.isDirectory }.orEmpty()
                File(f, "integrated/$from.json").isFile || lati.any { File(it, "done/$from.md").isFile }
            }
    }

    /** [from] is a block id of some `building-blocks.yaml` or has a block file `blocks/<side>/<state>/<from>.md`. */
    fun bloccoNoto(from: String): Boolean {
        val id = Regex("""^\s*-?\s*id:\s*["']?${Regex.escape(from)}["']?\s*(#.*)?$""")
        return from in BLOCCHI_COSTRUITI_SENZA_MARCATORE || funzionalita().any { f ->
            val manifesto = File(f, "building-blocks.yaml")
            (manifesto.isFile && manifesto.readLines().any { id.matches(it) }) ||
                File(f, "blocks").listFiles { d -> d.isDirectory }.orEmpty().any { lato ->
                    lato.listFiles { d -> d.isDirectory }.orEmpty().any { File(it, "$from.md").isFile }
                }
        }
    }

    private fun funzionalita(): List<File> =
        File(radice, ".mismagent/features").listFiles { f -> f.isDirectory }.orEmpty().toList()

    private fun eScriptDiControllo(percorso: String) =
        percorso.startsWith("architettura-test/controlli-adr/") && percorso.endsWith(".sh")

    private fun leggiControlliDagliAdr(): List<ControlloAdr> {
        val adr = File(radice, ".mismagent/decisions").listFiles { f -> f.name.matches(NOME_ADR) }.orEmpty()
        return adr.sortedBy { it.name }.flatMap { file ->
            val numero = file.name.take(CIFRE_ADR)
            vociEnforcedBy(frontmatter(file)).mapNotNull { voce ->
                CHIAVE_CHECK.find(voce)?.let { m ->
                    ControlloAdr(numero, m.groupValues[1], CHIAVE_FROM.find(voce)?.groupValues?.get(1))
                }
            }
        }
    }

    /** The list items of `enforced_by`, each with its continuation lines, comments stripped. */
    private fun vociEnforcedBy(righe: List<String>): List<String> {
        val inizio = righe.indexOfFirst { it.startsWith("enforced_by:") }
        if (inizio < 0) return emptyList()
        val voci = mutableListOf<StringBuilder>()
        // a flow list on the `enforced_by:` line itself: `enforced_by: [{check: a, from: b}, {check: c}]`
        val inline = senzaCommento(righe[inizio]).removePrefix("enforced_by:")
        ELEMENTO_FLOW.findAll(inline).forEach { voci += StringBuilder(it.value) }
        for (riga in righe.drop(inizio + 1).map(::senzaCommento)) {
            when {
                riga.isBlank() -> Unit
                riga.trimStart().startsWith("-") -> voci += StringBuilder(riga.trimStart().removePrefix("-"))
                riga.first().isWhitespace() -> voci.lastOrNull()?.append('\n')?.append(riga)
                else -> break
            }
        }
        return voci.map { it.toString() }
    }

    private fun senzaCommento(riga: String): String =
        if (riga.trimStart().startsWith("#")) "" else riga.substringBefore(" #")

    private fun frontmatter(file: File): List<String> {
        val righe = file.readLines()
        if (righe.firstOrNull()?.trim() != "---") return emptyList()
        val fine = righe.drop(1).indexOfFirst { it.trim() == "---" }
        return if (fine < 0) emptyList() else righe.subList(1, fine + 1)
    }

    private companion object {
        const val CIFRE_ADR = 4
        val NOME_ADR = Regex("""^\d{4}-.+\.md$""")
        val CHIAVE_CHECK = Regex("""\bcheck:\s*["']?([^\s,}"']+)""")
        val CHIAVE_FROM = Regex("""\bfrom:\s*["']?([^\s,}"']+)""")
        val ELEMENTO_FLOW = Regex("""\{[^}]*}""")

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
