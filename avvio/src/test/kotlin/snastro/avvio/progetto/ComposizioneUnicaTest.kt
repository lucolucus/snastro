package snastro.avvio.progetto

import org.junit.jupiter.api.io.TempDir
import snastro.avvio.ModuloComposizione
import snastro.avvio.coda.Campanello
import snastro.avvio.coda.CodaCondivisa
import snastro.avvio.documento.CollaboratoriDocumento
import snastro.avvio.documento.ModuloDocumento
import snastro.avvio.parlanti.CollaboratoriParlanti
import snastro.avvio.parlanti.ModuloParlanti
import snastro.avvio.sintesi.CollaboratoriSintesi
import snastro.avvio.sintesi.ModuloSintesi
import snastro.avvio.trascrizione.CollaboratoriTrascrizione
import snastro.avvio.trascrizione.ModuloTrascrizione
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ADR 0030 §1 on the single composition (block c3): the modules (AC-C68), the queue's wake-up handle (AC-C70), the
 * typed collaborators (AC-C72), the retired release code (AC-C74), the packages by concern (AC-C76) and the per-project
 * scopes (AC-C73) — each on the REAL `apriProgetto` ([AmbienteProgetto] + [SondaCostruzioni]) or, where the fact is
 * textual, on `avvio/src`.
 */
class ComposizioneUnicaTest {
    @TempDir
    lateinit var radice: Path

    private val principale = File("src/main/kotlin")
    private val sorgentiMain: List<File> =
        principale.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    /** Every code line (comments excluded) of [dove] matching [regola], as `file:line: text`. */
    private fun righe(regola: Regex, dove: List<File> = sorgentiMain): List<String> = dove
        .flatMap { file -> file.readLines().mapIndexed { i, riga -> Triple(file, i + 1, riga) } }
        .filter { (_, _, riga) ->
            val codice = riga.trimStart()
            val eCommento = codice.startsWith("*") || codice.startsWith("//") || codice.startsWith("/*")
            !eCommento && regola.containsMatchIn(riga)
        }
        .map { (file, n, riga) -> "${file.name}:$n: ${riga.trim()}" }

    @Test
    @Suppress("MaxLineLength", "MaximumLineLength", "ArgumentListWrapping") // the test name alone crosses 120 columns
    fun `AC-C68 i quattro moduli sono costruiti dalla sola PorteProgetto ed espongono abbonati, fonti, avvia, ferma e collaboratori`() {
        AmbienteProgetto(radice).use {
            val costruzioni = it.costruzioniApertura
            val porte = costruzioni.istanze(PorteProgetto::class.java.name).single()
            for (modulo in MODULI) {
                val costruito = costruzioni.di { n -> n == modulo.java.name }.single()
                assertTrue(costruito.istanza is ModuloComposizione, "${modulo.simpleName} e un ModuloComposizione")
                assertTrue(costruito.argomenti.any { a -> a === porte }, "${modulo.simpleName} riceve la PorteProgetto")
                // It builds no repository: every SQL adapter is PorteProgetto's (AC-C60 counts them, one each).
                val repository = costruito.argomenti.filter { a ->
                    a?.javaClass?.simpleName.orEmpty().endsWith("RepositorySql")
                }
                assertTrue(repository.isEmpty(), "${modulo.simpleName} non riceve repository: $repository")
            }
            assertEquals(
                it.composto.ordineAvvio.filterIsInstance<ModuloComposizione>().map { m -> m::class }.toSet(),
                (MODULI + ModuloProgetto::class).toSet(),
            )
        }
        // The typed collaborators each module exposes (compile-time shape of the public API).
        val collaboratori = mapOf(
            ModuloTrascrizione::class to CollaboratoriTrascrizione::class,
            ModuloParlanti::class to CollaboratoriParlanti::class,
            ModuloSintesi::class to CollaboratoriSintesi::class,
            ModuloDocumento::class to CollaboratoriDocumento::class,
        )
        collaboratori.forEach { (modulo, tipo) ->
            val campo = modulo.java.getDeclaredField("collaboratori")
            assertEquals(tipo.java, campo.type, "${modulo.simpleName}.collaboratori")
        }
    }

    @Test
    fun `AC-C70 la coda e costruita dalle fonti di ogni modulo e da un Campanello creato prima dei moduli`() {
        AmbienteProgetto(radice).use {
            val costruzioni = it.costruzioniApertura
            val ordine = costruzioni.di { true }.map { c -> c.istanza.javaClass.name }
            val campanello = costruzioni.istanze(Campanello::class.java.name).single()
            assertTrue(
                ordine.indexOf(Campanello::class.java.name) < MODULI.minOf { m -> ordine.indexOf(m.java.name) },
                "il Campanello prima dei moduli: $ordine",
            )
            for (consumatore in listOf(CodaCondivisa::class, ModuloTrascrizione::class, ModuloSintesi::class)) {
                val ricevuti = costruzioni.argomenti(consumatore.java.name, Campanello::class.java.name)
                assertEquals(listOf(campanello), ricevuti, "${consumatore.simpleName} riceve LO STESSO Campanello")
            }
            val fonti = it.composto.ordineAvvio.filterIsInstance<ModuloComposizione>().flatMap { m -> m.fontiCoda() }
            assertEquals(2, fonti.size, "Elaborazione (Trascrizione) e Riassunto (Sintesi)")
            // A Riassunto enqueued by ModuloSintesi wakes the queue and runs (AC-S146 runs it end to end).
            val a = it.registrazioneTrascritta()
            it.riassumi(a)
            it.attendiPronto(a)
        }
        assertEquals(emptyList(), righe(Regex("""AtomicReference<CodaCondivisa""")))
    }

    @Test
    fun `AC-C72 CollaboratoriProgetto ha i quattro campi tipati non nulli, e src main non fa cast ne catene`() {
        val campi = mapOf(
            "trascrizione" to CollaboratoriTrascrizione::class.java,
            "parlanti" to CollaboratoriParlanti::class.java,
            "sintesi" to CollaboratoriSintesi::class.java,
            "documento" to CollaboratoriDocumento::class.java,
        )
        campi.forEach { (nome, tipo) ->
            assertEquals(tipo, CollaboratoriProgetto::class.java.getDeclaredField(nome).type, nome)
        }
        AmbienteProgetto(radice).use {
            val c = it.collaboratori
            assertTrue(listOf<Any>(c.trascrizione, c.parlanti, c.sintesi, c.documento).size == campi.size)
        }
        assertEquals(emptyList(), righe(Regex("""\bas\??\s+Collaboratori""")))
        assertEquals(emptyList(), righe(Regex("""\br[0-9]\.r[0-9]\b""")))
    }

    @Test
    fun `AC-C73 ogni scope per progetto e un figlioDi, e l arresto e UNO ArrestoProgetto`() {
        // The app's ONE presenter scope (Grafo) is the only hand-built one; every other is figlioDi(…) (ADR 0028).
        assertEquals(listOf("Grafo.kt"), righe(Regex("""\bSupervisorJob\(""")).map { r -> r.substringBefore(':') })
        val scopeAMano = righe(Regex("""\bCoroutineScope\(""")).filterNot { r -> "figlioDi(CoroutineScope(" in r }
        assertEquals(listOf("Grafo.kt"), scopeAMano.map { r -> r.substringBefore(':') }, "$scopeAMano")
        val arresti = righe(Regex("""\bArrestoProgetto\(""")).filterNot { r -> "class ArrestoProgetto(" in r }
        assertEquals(listOf("ApriProgetto.kt"), arresti.map { r -> r.substringBefore(':') }, "UN arresto")
    }

    @Test
    fun `AC-C74 R0-R2 ritirate, e un solo Grafo, ContenutoApp, SEZIONI_SHELL, dispatcher io e merge`() {
        val sorgenti = File("src").walkTopDown()
            .filter { f -> f.isFile && f.extension == "kt" && f.name != "ComposizioneUnicaTest.kt" }
            .toList()
        assertTrue(sorgenti.any { f -> f.path.contains("src/test/") })
        assertTrue(sorgenti.any { f -> f.path.contains("src/main/") })
        assertEquals(emptyList(), righe(Regex("""\b(${RITIRATI.joinToString("|")})\b"""), sorgenti))
        assertEquals(1, righe(Regex("""\bclass Grafo\(""")).size)
        assertEquals(1, righe(Regex("""\bfun ContenutoApp\(""")).size)
        assertEquals(1, righe(Regex("""\bval SEZIONI_SHELL\b""")).size)
        assertEquals(1, righe(Regex("""\bDispatchers\.IO\b""")).size, "un solo dispatcher io, quello del Grafo")
        assertEquals(1, righe(Regex("""\bmerge\(""")).size, "una sola unione di AggiornamentiVista")
        val chiamate = righe(Regex("""\bContenutoProgetto\(""")).filterNot { r -> "fun ContenutoProgetto(" in r }
        assertEquals(1, chiamate.size, "un solo punto che compone ContenutoProgetto: $chiamate")
    }

    @Test
    fun `AC-C76 pacchetti per concern, nessuna cartella di release, Main in snastro avvio`() {
        val avvio = File(principale, "snastro/avvio")
        val rilasci = avvio.listFiles { f -> f.isDirectory && f.name.matches(Regex("r[0-9]")) }.orEmpty()
        assertEquals(emptyList(), rilasci.map { f -> f.name })
        val attesi = mapOf(
            "progetto/ErroriApplicazioneAvvio.kt" to "snastro.avvio.progetto",
            "modelli/ServizioModelliProvisioning.kt" to "snastro.avvio.modelli",
            "trascrizione/SelezioneAdattatoriMl.kt" to "snastro.avvio.trascrizione",
            "progetto/SchermataR1.kt" to "snastro.avvio.progetto",
            "parlanti/LavoriPerChiave.kt" to "snastro.avvio.parlanti",
            "parlanti/ProposteSerializzate.kt" to "snastro.avvio.parlanti",
            "parlanti/AzioniSomiglianzaProgetto.kt" to "snastro.avvio.parlanti",
            "coda/CodaCondivisa.kt" to "snastro.avvio.coda",
            "smoke/Smoke.kt" to "snastro.avvio.smoke",
            "Main.kt" to "snastro.avvio",
        )
        attesi.forEach { (percorso, pacchetto) ->
            val file = File(avvio, percorso)
            assertTrue(file.isFile, "$percorso esiste")
            assertEquals("package $pacchetto", file.readLines().first(), percorso)
        }
        val concern = setOf("progetto", "trascrizione", "parlanti", "sintesi", "documento", "modelli", "coda", "smoke")
        val cartelle = avvio.listFiles { f -> f.isDirectory }.orEmpty().map { f -> f.name }.toSet()
        assertEquals(concern, cartelle, "solo pacchetti per concern")
    }

    private companion object {
        val MODULI =
            listOf(ModuloTrascrizione::class, ModuloParlanti::class, ModuloSintesi::class, ModuloDocumento::class)

        /** ADR 0030 §1 "Retired" (AC-C74): none of these names survives in `:avvio`'s sources. */
        val RITIRATI = listOf(
            "costruisciGrafoR[0-9]", "GrafoR[0-9]", "ContenutoAppR[0-9]", "EstensioneSessione", "EstensioneR[0-9]",
            "CollaboratoriR[0-9]", "LettoreNomiVuoto", "SEZIONI_SHELL_R[0-9]", "ContestoEstensione", "ProgettoEsteso",
            "CollaboratoriProgettoAperto", "costruisciGrafoR0", "AmbienteR[0-9]", "PorteProgettoParlanti",
            "PorteProgettoSintesi",
        )
    }
}
