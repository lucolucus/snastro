package snastro.avvio.trascrizione

import org.junit.jupiter.api.io.TempDir
import snastro.avvio.ModuloComposizione
import snastro.avvio.SEZIONI_SHELL
import snastro.avvio.progetto.AmbienteProgetto
import snastro.kernel.LetturaCoerente
import snastro.kernel.UnitaDiLavoro
import snastro.progetto.applicazione.eventi.RegistrazioneAggiunta
import snastro.ui.DestinazioneShell
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * AC-355/AC-460/AC-341 on the single composition (ADR 0030 §3, retargeted): the declared subscriber lists, the
 * instances every command service received (through [snastro.avvio.progetto.SondaCostruzioni] while the REAL
 * `apriProgetto` ran) and, where the fact is textual, `avvio/src/main` itself.
 */
class CablaggioTrascrizioneTest {
    @TempDir
    lateinit var radice: Path

    private val sorgenti: List<File> =
        File("src/main/kotlin").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun righeCon(testo: String, dove: List<File> = sorgenti): List<String> = dove
        .flatMap { file -> file.readLines().mapIndexed { i, riga -> Triple(file, i + 1, riga) } }
        .filter { (_, _, riga) -> testo in riga && !commento(riga) }
        .map { (file, n, riga) -> "${file.name}:$n: ${riga.trim()}" }

    private fun commento(riga: String): Boolean = riga.trimStart().let { it.startsWith("*") || it.startsWith("//") }

    @Test
    fun `AC-355 nessun abbonato sincrono su RegistrazioneAggiunta, nelle liste dichiarate`() {
        AmbienteProgetto(radice).use {
            val sincroni = it.composto.ordineSincroni.flatMap { m -> m.abbonatiSincroni() }
            assertTrue(sincroni.isNotEmpty(), "la lista sincrona dichiarata non e vuota")
            assertEquals(emptyList(), sincroni.filter { a -> a.evento == RegistrazioneAggiunta::class })
            val fuoriDallaLista = it.composto.ordineAvvio.filterIsInstance<ModuloComposizione>()
                .filterNot { m -> m in it.composto.ordineSincroni }
            assertTrue(fuoriDallaLista.all { m -> m.abbonatiSincroni().isEmpty() }, "nessun sincrono fuori lista")
        }
        // Only apriProgetto registers, from the declared lists: no registration anywhere else in :avvio.
        val registrano = sorgenti.filter { f -> righeCon("registraSincrono", listOf(f)).isNotEmpty() }
        assertEquals(listOf("ApriProgetto.kt"), registrano.map { f -> f.name })
    }

    @Test
    fun `AC-355 ogni servizio comando riceve l unita di lavoro del dispatcher e LetturaCoerente e la stessa istanza`() {
        AmbienteProgetto(radice).use {
            val porte = it.porte
            val servizi = it.costruzioniApertura.di { c -> c.endsWith("Servizio") }
                .filter { c -> c.argomenti.any { a -> a is UnitaDiLavoro } }
            assertTrue(servizi.size >= MINIMO_SERVIZI, "i servizi comando della composizione: ${servizi.size}")
            // ADR 0029 §5: a service that only reads receives the project's LetturaCoerente, never a unit of work.
            val soloLettura = servizi.filter { c -> c.istanza.javaClass.simpleName in SERVIZI_DI_LETTURA }
            val comandi = servizi - soloLettura.toSet()
            val senzaDispatcher = comandi.filterNot { c -> c.argomenti.any { a -> a === porte.unitaDiLavoro } }
            assertEquals(emptyList(), senzaDispatcher.map { c -> c.istanza.javaClass.simpleName })
            assertTrue(soloLettura.all { c -> c.argomenti.any { a -> a === porte.lettura } })
            // ONE UnitaDiLavoroSql, the project's LetturaCoerente: no second one ever reaches a constructor.
            val tutte = it.costruzioniApertura.di { true }.flatMap { c -> c.argomenti }
            val estranee = tutte.filter { a -> (a is UnitaDiLavoro || a is LetturaCoerente) }
                .filterNot { a -> a === porte.unitaDiLavoro || a === porte.lettura }
            assertEquals(emptyList(), estranee)
        }
        val costruisconoLaGrezza = sorgenti.filter { f -> righeCon("UnitaDiLavoroSql(", listOf(f)).isNotEmpty() }
        assertEquals(listOf("PorteProgetto.kt"), costruisconoLaGrezza.map { f -> f.name })
    }

    @Test
    fun `AC-460 S2 riceve Ritrascrivi e Annulla da un solo costruttore, quello della composizione che purga`() {
        assertEquals(1, righeCon("ritrascrivi =").size, "un solo S2, con 'Ritrascrivi'")
        assertEquals(1, righeCon("annullaElaborazione = collaboratori.trascrizione.annullaElaborazione").size, "AC-478")
    }

    @Test
    fun `AC-341 AC-355 la shell e una sola, con Registrazioni e Parlanti`() {
        assertEquals(setOf(DestinazioneShell.REGISTRAZIONI, DestinazioneShell.PARLANTI), SEZIONI_SHELL)
        assertEquals(1, righeCon("val SEZIONI_SHELL").size, "nessun SEZIONI_SHELL per release")
    }

    private companion object {
        const val MINIMO_SERVIZI = 20
        val SERVIZI_DI_LETTURA = setOf("RiallineaTutteLeImpronteServizio")
    }
}
