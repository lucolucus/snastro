package snastro.avvio.r1

import snastro.avvio.SEZIONI_SHELL_R0
import snastro.ui.DestinazioneShell
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The static half of AC-355/AC-356 over `avvio/src/main` (the behavioral half is `ComposizioneR1Test`):
 * what the R1 composition must never contain, checked on the source itself. R2 (`snastro.avvio.r2`,
 * avvio-parlanti) is excluded from the Parlanti guard only — it is exactly where they are wired.
 */
class CablaggioR1Test {
    private val sorgenti: List<File> =
        File("src/main/kotlin").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun righeCon(testo: String, dove: List<File> = sorgenti): List<String> = dove
        .flatMap { file -> file.readLines().mapIndexed { i, riga -> Triple(file, i + 1, riga) } }
        .filter { (_, _, riga) -> testo in riga && !commento(riga) }
        .map { (file, n, riga) -> "${file.name}:$n: ${riga.trim()}" }

    private fun commento(riga: String): Boolean = riga.trimStart().let { it.startsWith("*") || it.startsWith("//") }

    @Test
    fun `AC-355 nessun abbonato sincrono e registrato, in particolare su RegistrazioneAggiunta`() {
        assertEquals(emptyList(), righeCon("registraSincrono"))
    }

    @Test
    fun `AC-355 nessun servizio della composizione R1 e costruito con la UnitaDiLavoroSql grezza`() {
        val r1 = sorgenti.filter { it.path.contains("snastro/avvio/r1/") }
        assertTrue(r1.isNotEmpty())
        assertEquals(emptyList(), righeCon("UnitaDiLavoroSql", r1))
    }

    @Test
    fun `AC-356 nessuna classe di parlanti e referenziata dalla composizione R1`() {
        // R2 (avvio-parlanti) lives in its own package, `snastro.avvio.r2`: everything else stays free of it.
        val fuoriDaR2 = sorgenti.filterNot { it.path.contains("snastro/avvio/r2/") }
        assertTrue(fuoriDaR2.any { it.path.contains("snastro/avvio/r1/") })
        assertEquals(emptyList(), righeCon("snastro.parlanti", fuoriDaR2))
    }

    @Test
    fun `AC-341 AC-355 la shell R1 non include la sezione Parlanti`() {
        assertEquals(setOf(DestinazioneShell.REGISTRAZIONI), SEZIONI_SHELL_R1)
        assertEquals(SEZIONI_SHELL_R0, SEZIONI_SHELL_R1)
    }
}
