package snastro.avvio.r2

import snastro.avvio.r1.SceltaMl
import snastro.avvio.r1.SelezioneAdattatoriMl
import snastro.parlanti.applicazione.porte.DecodificatoreAudioFinta
import snastro.parlanti.applicazione.porte.EstrattoreImprontaFinta
import snastro.ui.DestinazioneShell
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The static half of the R2 composition's ACs, checked on `avvio/src/main` and on the selection point. */
class CablaggioR2Test {
    private val r2: List<File> = File("src/main/kotlin/snastro/avvio/r2")
        .walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun righeCon(testo: String): List<String> = r2
        .flatMap { file -> file.readLines().mapIndexed { i, riga -> Triple(file, i + 1, riga) } }
        .filter { (_, _, riga) -> testo in riga && !riga.trimStart().let { it.startsWith("*") || it.startsWith("//") } }
        .map { (file, n, riga) -> "${file.name}:$n: ${riga.trim()}" }

    @Test
    fun `AC-359 nessun servizio della composizione R2 e costruito con la UnitaDiLavoroSql grezza`() {
        assertTrue(r2.isNotEmpty())
        assertEquals(emptyList(), righeCon("UnitaDiLavoroSql"))
        assertTrue(righeCon("dispatcher.unitaDiLavoro").isNotEmpty(), "eventi.unitaDiLavoro per tutti")
    }

    @Test
    fun `AC-341 AC-177 la shell R2 include la sezione Parlanti`() {
        assertEquals(setOf(DestinazioneShell.REGISTRAZIONI, DestinazioneShell.PARLANTI), SEZIONI_SHELL_R2)
    }

    @Test
    fun `selezione ML FINTE l'estrattore e il decodificatore finti, e la Proposta confronta davvero`() {
        val adattatori = SelezioneAdattatoriMl.adattatoriParlanti(SceltaMl.FINTE)
        assertIs<EstrattoreImprontaFinta>(adattatori.estrattore)
        assertIs<DecodificatoreAudioFinta>(adattatori.decodificatore(Path.of("progetto")))
        assertTrue(adattatori.proposte)
    }

    @Test
    fun `selezione ML REALI finche estrattore-impronta-sherpa non esiste nessuna estrazione nativa, Galleria vuota`() {
        val adattatori = SelezioneAdattatoriMl.adattatoriParlanti(SceltaMl.REALI)
        assertSame(EstrattoreImprontaAssente, adattatori.estrattore)
        assertSame(DecodificatoreAudioAssente, adattatori.decodificatore(Path.of("progetto")))
        assertFalse(adattatori.proposte)
        assertEquals("nessun-estrattore", adattatori.estrattore.modello)
    }
}
