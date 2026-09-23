package snastro.progetto.adattatori.porte

import org.junit.jupiter.api.io.TempDir
import snastro.kernel.ProgettoId
import snastro.progetto.applicazione.porte.VoceRegistro
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * AC-120 (atomic rewrite), AC-121 (a corrupt/unreadable file never crashes the caller), AC-328
 * (cross-process + in-process locking) and the round-trip guarantee for `percorso` (spaces,
 * parentheses, Unicode, Windows `\` separators — R3, AC-263/264).
 */
class RegistroProgettiFileRobustezzaTest {
    @TempDir
    lateinit var cartella: Path

    @Test
    fun `AC-120 un guasto a meta scrittura del file temporaneo lascia intatto il contenuto precedente`() {
        val file = cartella.resolve("progetti-recenti")
        val registro = RegistroProgettiFile(file)
        registro.registra(unaVoce())
        val contenutoPrimaDelGuasto = Files.readString(file)

        // F1: sostituisce il passo di scrittura del temporaneo con uno che ne scrive SOLO una parte
        // e poi fallisce — un crash a meta scrittura, non prima di scrivere nulla (il permesso negato
        // sulla cartella non e portabile e non prova il caso "meta file").
        val registroGuasto = RegistroProgettiFile(file) { temporaneo, righe ->
            Files.write(temporaneo, righe.take(1))
            throw IOException("crash simulato a meta scrittura del file temporaneo")
        }
        assertFailsWith<IOException> { registroGuasto.registra(unaVoce(percorso = "/altro/Assemblea.snastro")) }

        assertEquals(contenutoPrimaDelGuasto, Files.readString(file))
        assertEquals(listOf(unaVoce()), registro.elenco())
        val presentiInCartella = Files.list(cartella).use { it.toList() }
        // il file dati e il file di lock del locking incrociato (AC-328) — nessun *.tmp abbandonato.
        assertEquals(setOf(file, cartella.resolve("progetti-recenti.lock")), presentiInCartella.toSet())
    }

    @Test
    fun `AC-121 un file illeggibile produce un elenco vuoto e il prossimo registra lo riscrive valido`() {
        val file = cartella.resolve("progetti-recenti")
        Files.writeString(file, "questo non e un registro valido ne un formato riconoscibile")
        val registro = RegistroProgettiFile(file)

        assertEquals(emptyList(), registro.elenco())

        registro.registra(unaVoce())
        assertEquals(listOf(unaVoce()), registro.elenco())
    }

    @Test
    fun `AC-121 un file mancante produce un elenco vuoto senza crash`() {
        val registro = RegistroProgettiFile(cartella.resolve("non-esiste-ancora"))
        assertEquals(emptyList(), registro.elenco())
    }

    @Test
    fun `AC-121 un percorso che e una cartella produce elenco vuoto senza crash e registra lancia`() {
        val percorso = Files.createDirectory(cartella.resolve("e-una-cartella-non-un-file"))
        val registro = RegistroProgettiFile(percorso)

        assertEquals(emptyList(), registro.elenco()) // tollerante: nessun crash (AC-121)

        assertFailsWith<IOException> { registro.registra(unaVoce()) } // rigoroso: non maschera il guasto da "vuoto"
    }

    @Test
    fun `un guasto IO non di corruzione si propaga da registra aggiorna rimuovi ma elenco resta vuoto`() {
        val file = cartella.resolve("progetti-recenti")
        val scrittore = RegistroProgettiFile(file)
        scrittore.registra(unaVoce())
        val contenutoPrimaDelGuasto = Files.readString(file)

        // Seam di lettura (come quello di scrittura usato da F1): un guasto DETERMINISTICO, non
        // NoSuchFileException (file mancante) ne un problema di decodifica — es. un errore disco
        // transitorio o un permesso negato momentaneamente.
        val registroGuasto = RegistroProgettiFile(file, leggiBytes = { throw IOException("guasto IO simulato") })

        assertEquals(emptyList(), registroGuasto.elenco()) // tollerante

        assertFailsWith<IOException> { registroGuasto.registra(unaVoce(percorso = "/altro/Assemblea.snastro")) }
        assertFailsWith<IOException> {
            registroGuasto.aggiorna(unaVoce().percorso, numRegistrazioni = 9, ultimaAttivita = Instant.now())
        }
        assertFailsWith<IOException> { registroGuasto.rimuovi(unaVoce().percorso) }

        assertEquals(contenutoPrimaDelGuasto, Files.readString(file)) // mai toccato dai guasti sopra
    }

    @Test
    fun `F2 una riga malformata tra righe valide non cancella le altre e il prossimo registra le conserva`() {
        val file = cartella.resolve("progetti-recenti")
        val buona1 = "id-1\tConsiglio comunale\t/progetti/Consiglio comunale.snastro\t1\t2026-09-23T10:15:30Z"
        val campiSbagliati = "solo\tdue-campi"
        val numeroNonValido = "id-2\tAssemblea\t/progetti/Assemblea.snastro\tNON-UN-NUMERO\t2026-09-23T10:15:30Z"
        val dataNonValida = "id-3\tComitato\t/progetti/Comitato.snastro\t1\tnon-una-data"
        val buona2 = "id-4\tConsulta\t/progetti/Consulta.snastro\t2\t2026-09-23T09:00:00Z"
        val righe = listOf(buona1, campiSbagliati, numeroNonValido, dataNonValida, buona2)
        Files.writeString(file, righe.joinToString("\n"))
        val registro = RegistroProgettiFile(file)

        assertEquals(
            setOf("/progetti/Consiglio comunale.snastro", "/progetti/Consulta.snastro"),
            registro.elenco().map { it.percorso }.toSet(),
        )

        registro.registra(unaVoce(progettoId = ProgettoId("id-5"), percorso = "/progetti/Nuovo.snastro"))

        val dopo = RegistroProgettiFile(file)
        assertEquals(
            setOf("/progetti/Consiglio comunale.snastro", "/progetti/Consulta.snastro", "/progetti/Nuovo.snastro"),
            dopo.elenco().map { it.percorso }.toSet(),
        )
    }

    @Test
    fun `F2 un file con BOM UTF-8 iniziale e leggibile`() {
        val file = cartella.resolve("progetti-recenti")
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val riga = "id-1\tConsiglio comunale\t/progetti/Consiglio comunale.snastro\t1\t2026-09-23T10:15:30Z\n"
        Files.write(file, bom + riga.toByteArray(Charsets.UTF_8))
        val registro = RegistroProgettiFile(file)

        assertEquals(listOf(unaVoce()), registro.elenco())
    }

    @Test
    fun `F2 un file interamente non decodificabile come UTF-8 produce un elenco vuoto`() {
        val file = cartella.resolve("progetti-recenti")
        Files.write(file, byteArrayOf(0x80.toByte(), 0x81.toByte(), 0x82.toByte()))
        val registro = RegistroProgettiFile(file)

        assertEquals(emptyList(), registro.elenco())
    }

    @Test
    fun `F2 una riga non decodificabile come UTF-8 tra righe valide non cancella le altre e sopravvive a registra`() {
        val file = cartella.resolve("progetti-recenti")
        val buona1 = "id-1\tConsiglio comunale\t/progetti/Consiglio comunale.snastro\t1\t2026-09-23T10:15:30Z\n"
            .toByteArray(Charsets.UTF_8)
        val corrotta = byteArrayOf(0x80.toByte(), 0x81.toByte(), 0x82.toByte(), '\n'.code.toByte())
        val buona2 = "id-4\tConsulta\t/progetti/Consulta.snastro\t2\t2026-09-23T09:00:00Z\n".toByteArray(Charsets.UTF_8)
        Files.write(file, buona1 + corrotta + buona2)
        val registro = RegistroProgettiFile(file)

        assertEquals(
            setOf("/progetti/Consiglio comunale.snastro", "/progetti/Consulta.snastro"),
            registro.elenco().map { it.percorso }.toSet(),
        )

        registro.registra(unaVoce(progettoId = ProgettoId("id-5"), percorso = "/progetti/Nuovo.snastro"))

        val dopo = RegistroProgettiFile(file)
        assertEquals(
            setOf("/progetti/Consiglio comunale.snastro", "/progetti/Consulta.snastro", "/progetti/Nuovo.snastro"),
            dopo.elenco().map { it.percorso }.toSet(),
        )
    }

    @Test
    fun `F8 una scape sconosciuta nel file mantiene entrambi i caratteri`() {
        val file = cartella.resolve("progetti-recenti")
        // riga scritta a mano (mai prodotta da `blocca`): `\x` non e una scape riconosciuta.
        Files.writeString(file, "id-1\tNome\t/percorso/con\\xb.snastro\t1\t2026-09-23T10:15:30Z\n")
        val registro = RegistroProgettiFile(file)

        assertEquals(listOf("/percorso/con\\xb.snastro"), registro.elenco().map { it.percorso })
    }

    @Test
    fun `F4 due thread che registrano in concorrenza non perdono aggiornamenti`() {
        val file = cartella.resolve("progetti-recenti")
        val registro = RegistroProgettiFile(file)
        val perThread = 30
        val esecutore = Executors.newFixedThreadPool(2)
        try {
            val compiti = (0 until perThread).map { i ->
                esecutore.submit {
                    registro.registra(unaVoce(progettoId = ProgettoId("t1-$i"), percorso = "/t1/progetto-$i.snastro"))
                }
            } + (0 until perThread).map { i ->
                esecutore.submit {
                    registro.registra(unaVoce(progettoId = ProgettoId("t2-$i"), percorso = "/t2/progetto-$i.snastro"))
                }
            }
            compiti.forEach { it.get() }
        } finally {
            esecutore.shutdown()
        }

        assertEquals(2 * perThread, registro.elenco().size)
    }

    @Test
    fun `AC-328 due istanze sullo stesso file in una JVM non perdono aggiornamenti in scrittura concorrente`() {
        val file = cartella.resolve("progetti-recenti")
        val registroUno = RegistroProgettiFile(file)
        val registroDue = RegistroProgettiFile(file)
        val perIstanza = 25
        val esecutore = Executors.newFixedThreadPool(2)
        try {
            val compiti = (0 until perIstanza).map { i ->
                val voce = unaVoce(progettoId = ProgettoId("u1-$i"), percorso = "/u1/progetto-$i.snastro")
                esecutore.submit { registroUno.registra(voce) }
            } + (0 until perIstanza).map { i ->
                val voce = unaVoce(progettoId = ProgettoId("u2-$i"), percorso = "/u2/progetto-$i.snastro")
                esecutore.submit { registroDue.registra(voce) }
            }
            compiti.forEach { it.get() }
        } finally {
            esecutore.shutdown()
        }

        assertEquals(2 * perIstanza, registroUno.elenco().size)
        assertEquals(2 * perIstanza, registroDue.elenco().size)
    }

    @Test
    fun `F7 un tmp abbandonato da una scrittura precedente e spazzato via alla scrittura successiva`() {
        val file = cartella.resolve("progetti-recenti")
        val abbandonato = cartella.resolve("progetti-recenti1234567890.tmp")
        Files.writeString(abbandonato, "resti di un crash precedente")
        val registro = RegistroProgettiFile(file)

        registro.registra(unaVoce())

        assertEquals(false, Files.exists(abbandonato))
        assertEquals(listOf(unaVoce()), registro.elenco())
    }

    @Test
    fun `F7 un tmp non correlato nella stessa cartella sopravvive alla pulizia`() {
        val file = cartella.resolve("progetti-recenti")
        val nonCorrelato = cartella.resolve("altro.tmp")
        Files.writeString(nonCorrelato, "tmp di un altro registro, non deve sparire")
        val registro = RegistroProgettiFile(file)

        registro.registra(unaVoce())

        assertTrue(Files.exists(nonCorrelato))
    }

    @Test
    fun `F7 la pulizia di un registro non cancella il tmp in scrittura di un registro diverso nella stessa cartella`() {
        val fileA = cartella.resolve("registro-a")
        val fileB = cartella.resolve("registro-b")
        val bTempCreato = CountDownLatch(1)
        val bPuoContinuare = CountDownLatch(1)
        val bTemporaneo = AtomicReference<Path>()
        val registroB = RegistroProgettiFile(fileB) { temporaneo, righe ->
            Files.write(temporaneo, righe)
            bTemporaneo.set(temporaneo)
            bTempCreato.countDown()
            assertTrue(bPuoContinuare.await(5, TimeUnit.SECONDS))
        }
        val registroA = RegistroProgettiFile(fileA)
        val esecutore = Executors.newSingleThreadExecutor()
        try {
            val futuro = esecutore.submit { registroB.registra(unaVoce(percorso = "/b/progetto.snastro")) }
            assertTrue(bTempCreato.await(5, TimeUnit.SECONDS))

            // A e B hanno percorsi diversi: lock separati, corrono davvero in parallelo. La pulizia
            // interna a registroA.registra deve ignorare il tmp di B (prefisso "registro-b", non
            // "registro-a").
            registroA.registra(unaVoce(percorso = "/a/progetto.snastro"))

            assertTrue(Files.exists(bTemporaneo.get()))

            bPuoContinuare.countDown()
            futuro.get(5, TimeUnit.SECONDS)
        } finally {
            esecutore.shutdown()
        }

        assertEquals(listOf(unaVoce(percorso = "/a/progetto.snastro")), registroA.elenco())
        assertEquals(listOf(unaVoce(percorso = "/b/progetto.snastro")), registroB.elenco())
    }

    @Test
    fun `il percorso attraversa verbatim scrittura e rilettura su file - spazi parentesi unicode separatori Windows`() {
        val file = cartella.resolve("progetti-recenti")
        val percorsi = listOf(
            "/Users/utente/Documents/snastro/Riunione (2).snastro",
            "C:\\Utenti\\Città\\Documenti\\snastro\\Città metropolitana (3).snastro",
            "/percorsi/Attività è più «unicode» ünïcödé/Progetto.snastro",
        )
        val voci = percorsi.mapIndexed { i, p ->
            unaVoce(progettoId = ProgettoId("id-${i + 1}"), nome = "Progetto $i", percorso = p)
        }

        val scrittore = RegistroProgettiFile(file)
        voci.forEach(scrittore::registra)

        // Rilettura da un'istanza nuova: prova che il round-trip passa per il file, non per la memoria.
        val lettore = RegistroProgettiFile(file)
        assertEquals(percorsi.toSet(), lettore.elenco().map { it.percorso }.toSet())
    }

    private fun unaVoce(
        progettoId: ProgettoId = ProgettoId("id-1"),
        nome: String = "Consiglio comunale",
        percorso: String = "/progetti/Consiglio comunale.snastro",
        ultimaAttivita: Instant = Instant.parse("2026-09-23T10:15:30Z"),
    ): VoceRegistro = VoceRegistro(progettoId, nome, percorso, numRegistrazioni = 1, ultimaAttivita = ultimaAttivita)
}
