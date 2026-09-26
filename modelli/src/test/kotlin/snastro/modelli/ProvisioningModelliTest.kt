package snastro.modelli

import org.junit.jupiter.api.AfterEach
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration
import java.util.HexFormat
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [ProvisioningModelli] real-on-real: every source is [ServerLocaleDiProva], a loopback-only HTTP
 * server (AC-134 — no real network reaches this test, so it stays in the default gate).
 */
class ProvisioningModelliTest {
    private val server = ServerLocaleDiProva()
    private val cartella: Path = Files.createTempDirectory("modelli-provisioning-test")

    @AfterEach
    fun chiudiServer() {
        server.close()
    }

    private companion object {
        // AC-S31: 64 MiB, la stessa soglia di ProvisioningModelli.MARGINE_SPAZIO_BYTE (privata: il
        // test la ripete per costruire soglie esatte, mai per ispezionare l'implementazione).
        const val MARGINE_SPAZIO_BYTE_TEST = 64L * 1024 * 1024
    }

    @Test
    fun `un catalogo vuoto e sempre pronto`() {
        val provisioning = ProvisioningModelli(CatalogoModelli(emptyList()), cartella)

        assertTrue(provisioning.pronti())
        assertTrue(provisioning.mancanti().isEmpty())
    }

    @Test
    fun `AC-132 un catalogo con una voce non installata non e pronto e la elenca come mancante`() {
        val voce = unaVoce("a", "contenuto".toByteArray())
        val provisioning = ProvisioningModelli(CatalogoModelli(listOf(voce)), cartella)

        assertFalse(provisioning.pronti())
        assertEquals(listOf(voce), provisioning.mancanti())
    }

    @Test
    fun `AC-331 dopo un download riuscito il formato FILE e installato nella directory, pronti diventa vero`() {
        val contenuto = "contenuto del modello di prova".toByteArray()
        val voce = unaVoceServita("a", contenuto)
        val provisioning = ProvisioningModelli(CatalogoModelli(listOf(voce)), cartella)
        val progressi = mutableListOf<Triple<String, Long, Long>>()

        val esito = provisioning.scarica { id, scaricati, totali -> progressi += Triple(id, scaricati, totali) }

        esito.atteso()
        assertTrue(provisioning.pronti())
        assertEquals(emptyList(), provisioning.mancanti())
        assertTrue(Files.isDirectory(provisioning.percorso("a")), "percorso(id) e' una DIRECTORY, mai un file")
        assertContentEquals(contenuto, Files.readAllBytes(provisioning.percorso("a").resolve("peso.bin")))
        assertEquals(voce.sha256, Files.readString(provisioning.percorso("a").resolve(".sha256")).trim())
        assertFalse(Files.exists(cartella.resolve("a.part")), "il .part non esiste piu' dopo l'installazione")
        assertTrue(progressi.isNotEmpty())
        assertEquals("a", progressi.last().first)
        assertEquals(contenuto.size.toLong(), progressi.last().second)
        assertEquals(contenuto.size.toLong(), progressi.last().third)
    }

    @Test
    fun `AC-330 un archivio tar bz2 con una cartella di primo livello e estratto e la cartella e rimossa`() {
        val encoder = "dati encoder".toByteArray()
        val tokens = "dati tokens".toByteArray()
        val archivio = ArchivioDiProva.tarBz2("top/encoder.onnx" to encoder, "top/tokens.txt" to tokens)
        val voce = unaVoceServita("modello-diarizzazione", archivio, formato = FormatoVoce.TAR_BZ2)
        val provisioning = ProvisioningModelli(CatalogoModelli(listOf(voce)), cartella)

        val esito = provisioning.scarica { _, _, _ -> }

        esito.atteso()
        val installata = provisioning.percorso("modello-diarizzazione")
        assertContentEquals(encoder, Files.readAllBytes(installata.resolve("encoder.onnx")))
        assertContentEquals(tokens, Files.readAllBytes(installata.resolve("tokens.txt")))
        assertFalse(Files.exists(cartella.resolve("modello-diarizzazione.part")))
        assertTrue(provisioning.pronti())
    }

    @Test
    fun `AC-129 hash errato restituisce HashNonValido e non installa nulla`() {
        val dichiarato = "contenuto atteso dal catalogo".toByteArray()
        val servitoDavvero = "contenuto diverso da quello atteso".toByteArray()
        val voce = unaVoce("a", dichiarato)
        server.servi(voce.url.percorsoDaUrl(), servitoDavvero)
        val provisioning = ProvisioningModelli(CatalogoModelli(listOf(voce)), cartella)

        val esito = provisioning.scarica { _, _, _ -> }

        val errore = esito.erroreAtteso<ErroreModelli.HashNonValido>()
        assertEquals("a", errore.modelloId)
        assertFalse(Files.exists(provisioning.percorso("a")))
        assertFalse(Files.exists(cartella.resolve("a.part")))
        assertFalse(provisioning.pronti())
    }

    @Test
    fun `AC-130 il percorso finale non esiste mai prima che l hash sia verificato ed estratto`() {
        // Provato dai casi vicini, con lo stesso meccanismo (file temporaneo + verifica + directory
        // temporanea + rinomina atomica SOLO alla fine, mai prima): hash errato (sopra) -> nessuna
        // directory finale; download interrotto (sotto) -> nessuna directory finale finche' non
        // completa; e qui esplicitamente nessuna directory `.tmp-*` sopravvive a un fallimento.
        val dichiarato = "x".repeat(100).toByteArray()
        val voce = unaVoce("a", dichiarato)
        server.servi(voce.url.percorsoDaUrl(), "y".repeat(100).toByteArray())
        val provisioning = ProvisioningModelli(CatalogoModelli(listOf(voce)), cartella)

        provisioning.scarica { _, _, _ -> }

        assertFalse(Files.exists(provisioning.percorso("a")), "nessun percorso finale prima della verifica")
        Files.newDirectoryStream(cartella).use { flusso ->
            assertFalse(flusso.any { it.fileName.toString().contains(".tmp-") }, "nessuna directory temporanea residua")
        }
    }

    @Test
    fun `AC-131 AC-337 connessione chiusa in anticipo da DownloadFallito, il part resta, la ripresa completa`() {
        val contenuto = ByteArray(20_000) { (it % 256).toByte() }
        val voce = unaVoce("a", contenuto)
        server.serviConInterruzionePrimaRichiesta(voce.url.percorsoDaUrl(), contenuto)
        val provisioning = ProvisioningModelli(CatalogoModelli(listOf(voce)), cartella)

        val primoEsito = provisioning.scarica { _, _, _ -> }

        primoEsito.erroreAtteso<ErroreModelli.DownloadFallito>()
        val parziale = cartella.resolve("a.part")
        assertTrue(Files.isRegularFile(parziale), "il file temporaneo resta per la ripresa")
        val giaScaricati = Files.size(parziale)
        assertTrue(giaScaricati in 1 until contenuto.size.toLong(), "il parziale non e' ne' vuoto ne' completo")
        assertFalse(Files.exists(provisioning.percorso("a")))

        val secondoEsito = provisioning.scarica { _, _, _ -> }

        secondoEsito.atteso()
        assertContentEquals(contenuto, Files.readAllBytes(provisioning.percorso("a").resolve("peso.bin")))
        assertTrue(
            server.byteServitiUltimaRichiesta < contenuto.size,
            "la seconda richiesta ha ricevuto solo il resto, non l'intero file: e' una ripresa, non un nuovo download",
        )
    }

    @Test
    fun `AC-132 rete assente restituisce ReteAssente`() {
        val voce = unaVoce("a", "contenuto".toByteArray()).copy(url = "http://127.0.0.1:1/nessuno-in-ascolto")
        val provisioning = ProvisioningModelli(CatalogoModelli(listOf(voce)), cartella)

        val esito = provisioning.scarica { _, _, _ -> }

        esito.erroreAtteso<ErroreModelli.ReteAssente>()
    }

    @Test
    fun `AC-333 un marcatore diverso dal catalogo forza la reinstallazione sostituendo la vecchia directory`() {
        // Esercita direttamente il meccanismo di confronto del marcatore: nella catena reale uno
        // sha256 diverso conia sempre un nuovo id (ADR 0008 Amendment (c)), ma il meccanismo deve
        // comunque sapersi riprendere da un marcatore non corrispondente o assente allo stesso modo
        // (un marcatore corrotto, un crash a meta' scrittura).
        val id = "a"
        val contenutoV1 = "versione 1".toByteArray()
        val voceV1 = unaVoceServita(id, contenutoV1)
        val provisioning1 = ProvisioningModelli(CatalogoModelli(listOf(voceV1)), cartella)
        provisioning1.scarica { _, _, _ -> }.atteso()
        assertTrue(provisioning1.pronti())

        val contenutoV2 = "versione 2, decisamente piu lunga della precedente".toByteArray()
        val voceV2 = unaVoceServita(id, contenutoV2)
        val provisioning2 = ProvisioningModelli(CatalogoModelli(listOf(voceV2)), cartella)

        assertEquals(listOf(voceV2), provisioning2.mancanti())
        val esito = provisioning2.scarica { _, _, _ -> }

        esito.atteso()
        assertContentEquals(contenutoV2, Files.readAllBytes(provisioning2.percorso(id).resolve("peso.bin")))
        assertTrue(provisioning2.pronti())
    }

    @Test
    fun `uno stato HTTP diverso da 200 206 e 416 restituisce DownloadFallito`() {
        // Nessun contenuto registrato su questo percorso: il server finto risponde 404.
        val voce = unaVoce("a", "contenuto".toByteArray())
        val provisioning = ProvisioningModelli(CatalogoModelli(listOf(voce)), cartella)

        val esito = provisioning.scarica { _, _, _ -> }

        val errore = esito.erroreAtteso<ErroreModelli.DownloadFallito>()
        assertTrue(errore.motivo.contains("404"), "il motivo riporta lo stato HTTP ricevuto")
    }

    @Test
    fun `AC-333 una directory tmp residua da un crash e eliminata all avvio di scarica`() {
        Files.createDirectories(cartella)
        val residua = cartella.resolve("qualcosa.tmp-0")
        Files.createDirectories(residua)
        Files.writeString(residua.resolve("frammento"), "avanzo")
        val provisioning = ProvisioningModelli(CatalogoModelli(emptyList()), cartella)

        provisioning.scarica { _, _, _ -> }.atteso()

        assertFalse(Files.exists(residua))
    }

    @Test
    fun `AC-336 i redirect vengono seguiti e il download si completa`() {
        val contenuto = "contenuto ridiretto come una release GitHub".toByteArray()
        val voce = unaVoce("a", contenuto)
        val percorsoReale = "/modelli/a/reale.bin"
        server.servi(percorsoReale, contenuto)
        server.redirect(voce.url.percorsoDaUrl(), server.url(percorsoReale))
        val provisioning = ProvisioningModelli(CatalogoModelli(listOf(voce)), cartella)

        val esito = provisioning.scarica { _, _, _ -> }

        esito.atteso()
        assertContentEquals(contenuto, Files.readAllBytes(provisioning.percorso("a").resolve("peso.bin")))
    }

    @Test
    fun `AC-336 un ciclo di redirect da DownloadFallito, mai un blocco`() {
        val voce = unaVoce("a", "contenuto".toByteArray())
        val percorsoA = voce.url.percorsoDaUrl()
        val percorsoB = "/modelli/a/altro.bin"
        server.redirect(percorsoA, server.url(percorsoB))
        server.redirect(percorsoB, server.url(percorsoA))
        val provisioning = ProvisioningModelli(CatalogoModelli(listOf(voce)), cartella)

        val esito = provisioning.scarica { _, _, _ -> }

        esito.erroreAtteso<ErroreModelli.DownloadFallito>()
    }

    @Test
    fun `AC-337 un part piu grande del dichiarato si scarta e la ripartenza da zero riesce`() {
        val contenuto = "x".repeat(50).toByteArray()
        val voce = unaVoce("a", contenuto)
        server.servi(voce.url.percorsoDaUrl(), contenuto)
        Files.createDirectories(cartella)
        Files.write(cartella.resolve("a.part"), "y".repeat(200).toByteArray())
        val provisioning = ProvisioningModelli(CatalogoModelli(listOf(voce)), cartella)

        val esito = provisioning.scarica { _, _, _ -> }

        esito.atteso()
        assertContentEquals(contenuto, Files.readAllBytes(provisioning.percorso("a").resolve("peso.bin")))
    }

    @Test
    fun `AC-337 un 416 sulla richiesta Range scarta il part e la ripartenza da zero riesce`() {
        val contenuto = ByteArray(20_000) { (it % 256).toByte() }
        val voce = unaVoce("a", contenuto)
        server.servi(voce.url.percorsoDaUrl(), contenuto)
        server.rifiuta416PerRange(voce.url.percorsoDaUrl())
        Files.createDirectories(cartella)
        Files.write(cartella.resolve("a.part"), contenuto.copyOfRange(0, 5_000))
        val provisioning = ProvisioningModelli(CatalogoModelli(listOf(voce)), cartella)

        val esito = provisioning.scarica { _, _, _ -> }

        esito.atteso()
        assertContentEquals(contenuto, Files.readAllBytes(provisioning.percorso("a").resolve("peso.bin")))
    }

    @Test
    fun `AC-337 il server che ignora Range risponde 200 e il client riparte da zero senza corrompersi`() {
        val contenuto = ByteArray(20_000) { (it % 256).toByte() }
        val voce = unaVoce("a", contenuto)
        server.servi(voce.url.percorsoDaUrl(), contenuto)
        server.ignoraRichiesteDiRange(voce.url.percorsoDaUrl())
        Files.createDirectories(cartella)
        Files.write(cartella.resolve("a.part"), contenuto.copyOfRange(0, 5_000))
        val provisioning = ProvisioningModelli(CatalogoModelli(listOf(voce)), cartella)

        val esito = provisioning.scarica { _, _, _ -> }

        esito.atteso()
        assertContentEquals(contenuto, Files.readAllBytes(provisioning.percorso("a").resolve("peso.bin")))
    }

    @Test
    fun `AC-338 un server che smette di inviare byte fallisce entro il timeout di inattivita`() {
        val contenuto = ByteArray(50_000) { (it % 256).toByte() }
        val voce = unaVoce("a", contenuto)
        server.serviConSospensione(voce.url.percorsoDaUrl(), contenuto, byteIniziali = 1_000)
        val provisioning = ProvisioningModelli(
            CatalogoModelli(listOf(voce)),
            cartella,
            timeoutInattivita = Duration.ofMillis(200),
        )

        val inizio = System.nanoTime()
        val esito = provisioning.scarica { _, _, _ -> }
        val trascorsiMs = (System.nanoTime() - inizio) / 1_000_000

        esito.erroreAtteso<ErroreModelli.DownloadFallito>()
        assertTrue(trascorsiMs < 5_000, "non deve bloccare indefinitamente (trascorsi ${trascorsiMs}ms)")
        assertTrue(Files.isRegularFile(cartella.resolve("a.part")), "il part resta per la ripresa")
    }

    @Test
    fun `AC-339 una cartella non scrivibile restituisce ScritturaFallita, mai ReteAssente`() {
        val contenuto = "contenuto".toByteArray()
        val soloLettura = Files.createTempDirectory("modelli-provisioning-test-ro")
        val voce = VoceCatalogo(
            id = "a",
            ruolo = "asr",
            url = server.url("/modelli/a/peso.bin"),
            sha256 = sha256Esadecimale(contenuto),
            dimensioneByte = contenuto.size.toLong(),
            formato = FormatoVoce.FILE,
            licenza = "MIT",
            attribuzione = "k2-fsa",
        )
        server.servi(voce.url.percorsoDaUrl(), contenuto)
        val revocato = soloLettura.toFile().setWritable(false)
        assertTrue(revocato, "il test presuppone di poter revocare il permesso di scrittura")
        try {
            val provisioning = ProvisioningModelli(CatalogoModelli(listOf(voce)), soloLettura)

            val esito = provisioning.scarica { _, _, _ -> }

            esito.erroreAtteso<ErroreModelli.ScritturaFallita>()
        } finally {
            soloLettura.toFile().setWritable(true)
        }
    }

    @Test
    fun `chiamate concorrenti a scarica sono serializzate, mai una gara sugli stessi file`() {
        val contenuto = ByteArray(50_000) { (it % 256).toByte() }
        val voce = unaVoceServita("a", contenuto)
        val provisioning = ProvisioningModelli(CatalogoModelli(listOf(voce)), cartella)
        val esecutore = Executors.newFixedThreadPool(2)
        try {
            val futures = (1..2).map { esecutore.submit(Callable { provisioning.scarica { _, _, _ -> } }) }
            val risultati = futures.map { it.get(10, TimeUnit.SECONDS) }

            risultati.forEach { it.atteso() }
        } finally {
            esecutore.shutdown()
        }
        assertTrue(provisioning.pronti())
        assertContentEquals(contenuto, Files.readAllBytes(provisioning.percorso("a").resolve("peso.bin")))
    }

    @Test
    fun `AC-S26 pronti e mancanti ignorano una voce facoltativa non installata`() {
        val obbligatoria = unaVoceServita("richiesta", "contenuto".toByteArray())
        val facoltativa = unaVoce("facoltativa", "peso llm".toByteArray()).copy(obbligatoria = false)
        val provisioning = ProvisioningModelli(CatalogoModelli(listOf(obbligatoria, facoltativa)), cartella)

        val esito = provisioning.scarica { _, _, _ -> }

        esito.atteso()
        assertTrue(provisioning.pronti())
        assertEquals(emptyList(), provisioning.mancanti())
        assertFalse(provisioning.installata(facoltativa.id))
    }

    @Test
    fun `AC-S27 lo scarica di massa non fa mai una richiesta per una voce facoltativa`() {
        val obbligatoria = unaVoceServita("richiesta", "contenuto".toByteArray())
        val facoltativa = unaVoce("facoltativa", "peso llm".toByteArray()).copy(obbligatoria = false)
        // Nota: server.servi() non e' MAI chiamato per facoltativa.url: se scarica() la richiedesse
        // comunque, il server risponderebbe 404 e l'esito complessivo sarebbe un Errore, non Ok.
        val provisioning = ProvisioningModelli(CatalogoModelli(listOf(obbligatoria, facoltativa)), cartella)

        val esito = provisioning.scarica { _, _, _ -> }

        esito.atteso()
        assertEquals(0, server.richiesteA(facoltativa.url.percorsoDaUrl()))
    }

    @Test
    fun `AC-S28 installata(id) e vera solo se la directory esiste e il marcatore combacia col catalogo`() {
        val facoltativa = unaVoceServita("facoltativa", "peso llm".toByteArray()).copy(obbligatoria = false)
        val provisioning = ProvisioningModelli(CatalogoModelli(listOf(facoltativa)), cartella)

        assertFalse(provisioning.installata(facoltativa.id), "nessuna directory ancora")
        assertFalse(provisioning.installata("id-sconosciuto-nel-catalogo"))

        provisioning.scarica(facoltativa.id) { _, _ -> }.atteso()
        assertTrue(provisioning.installata(facoltativa.id))

        Files.writeString(provisioning.percorso(facoltativa.id).resolve(".sha256"), "hash-non-valido")
        assertFalse(provisioning.installata(facoltativa.id), "un marcatore diverso dal catalogo non e' installato")
    }

    @Test
    fun `AC-S29 scarica(id) installa solo quella voce, lascia intatte le obbligatorie, progresso monotono`() {
        val obbligatoria = unaVoceServita("richiesta", "contenuto richiesto".toByteArray())
        val facoltativa = unaVoceServita("facoltativa", ByteArray(30_000) { (it % 256).toByte() })
            .copy(obbligatoria = false)
        val provisioning = ProvisioningModelli(CatalogoModelli(listOf(obbligatoria, facoltativa)), cartella)
        provisioning.scarica { _, _, _ -> }.atteso() // installa solo la voce obbligatoria
        val hashRichiestaPrima = Files.readString(provisioning.percorso("richiesta").resolve(".sha256"))
        val progressi = mutableListOf<Pair<Long, Long>>()

        val esito = provisioning.scarica(facoltativa.id) { scaricati, totali -> progressi += scaricati to totali }

        esito.atteso()
        assertTrue(provisioning.installata(facoltativa.id))
        assertContentEquals(
            "contenuto richiesto".toByteArray(),
            Files.readAllBytes(provisioning.percorso("richiesta").resolve("peso.bin")),
        )
        assertEquals(
            hashRichiestaPrima,
            Files.readString(provisioning.percorso("richiesta").resolve(".sha256")),
            "la voce obbligatoria resta intatta byte per byte",
        )
        assertTrue(progressi.isNotEmpty())
        for (i in 1 until progressi.size) {
            assertTrue(progressi[i].first >= progressi[i - 1].first, "il progresso non decresce mai")
        }
        assertEquals(facoltativa.dimensioneByte, progressi.last().first)
        assertEquals(facoltativa.dimensioneByte, progressi.last().second)
    }

    @Test
    fun `AC-S29 due chiamate concorrenti a scarica(id) sono serializzate sullo stesso lucchetto`() {
        val facoltativa = unaVoceServita("facoltativa", ByteArray(50_000) { (it % 256).toByte() })
            .copy(obbligatoria = false)
        val provisioning = ProvisioningModelli(CatalogoModelli(listOf(facoltativa)), cartella)
        val esecutore = Executors.newFixedThreadPool(2)
        try {
            val futures = (1..2).map { esecutore.submit(Callable { provisioning.scarica(facoltativa.id) { _, _ -> } }) }
            val risultati = futures.map { it.get(10, TimeUnit.SECONDS) }
            risultati.forEach { it.atteso() }
        } finally {
            esecutore.shutdown()
        }
        assertTrue(provisioning.installata(facoltativa.id))
        assertContentEquals(
            ByteArray(50_000) { (it % 256).toByte() },
            Files.readAllBytes(provisioning.percorso(facoltativa.id).resolve("peso.bin")),
        )
    }

    @Test
    fun `AC-S31 spazio insufficiente restituisce SpazioInsufficiente senza toccare la rete`() {
        val facoltativa = unaVoce("facoltativa", "x".repeat(1000).toByteArray()).copy(obbligatoria = false)
        // Nessun contenuto servito per facoltativa: se la rete fosse toccata l'esito sarebbe
        // DownloadFallito (404), mai SpazioInsufficiente.
        val necessari = facoltativa.dimensioneByte + MARGINE_SPAZIO_BYTE_TEST
        val provisioning = ProvisioningModelli(
            CatalogoModelli(listOf(facoltativa)),
            cartella,
            spazioDisponibileByte = { necessari - 1 },
        )

        val esito = provisioning.scarica(facoltativa.id) { _, _ -> }

        val errore = esito.erroreAtteso<ErroreModelli.SpazioInsufficiente>()
        assertEquals(facoltativa.dimensioneByte, errore.richiestiByte)
        assertFalse(Files.exists(cartella.resolve("facoltativa.part")))
        assertEquals(0, server.richiesteA(facoltativa.url.percorsoDaUrl()))
    }

    @Test
    fun `AC-S31 spazio esattamente sufficiente fa procedere il download`() {
        val contenuto = "contenuto ottimale per lo spazio".toByteArray()
        val facoltativa = unaVoceServita("facoltativa", contenuto).copy(obbligatoria = false)
        val necessari = contenuto.size.toLong() + MARGINE_SPAZIO_BYTE_TEST
        val provisioning = ProvisioningModelli(
            CatalogoModelli(listOf(facoltativa)),
            cartella,
            spazioDisponibileByte = { necessari },
        )

        val esito = provisioning.scarica(facoltativa.id) { _, _ -> }

        esito.atteso()
        assertTrue(provisioning.installata(facoltativa.id))
    }

    @Test
    fun `AC-S31 un part esistente riduce lo spazio richiesto e la ripresa parte dal punto raggiunto`() {
        val contenuto = ByteArray(20_000) { (it % 256).toByte() }
        val facoltativa = unaVoceServita("facoltativa", contenuto).copy(obbligatoria = false)
        Files.createDirectories(cartella)
        Files.write(cartella.resolve("facoltativa.part"), contenuto.copyOfRange(0, 5_000))
        val necessari = (contenuto.size - 5_000).toLong() + MARGINE_SPAZIO_BYTE_TEST
        val provisioning = ProvisioningModelli(
            CatalogoModelli(listOf(facoltativa)),
            cartella,
            spazioDisponibileByte = { necessari }, // esattamente cio' che serve DOPO aver contato il .part
        )

        val esito = provisioning.scarica(facoltativa.id) { _, _ -> }

        esito.atteso()
        assertContentEquals(contenuto, Files.readAllBytes(provisioning.percorso("facoltativa").resolve("peso.bin")))
        assertTrue(
            server.byteServitiUltimaRichiesta < contenuto.size,
            "la richiesta ha ricevuto solo il resto: e' una ripresa",
        )
    }

    private fun unaVoce(
        id: String,
        contenuto: ByteArray,
        formato: FormatoVoce = FormatoVoce.FILE,
    ): VoceCatalogo = VoceCatalogo(
        id = id,
        ruolo = "asr",
        url = server.url("/modelli/$id/peso.bin"),
        sha256 = sha256Esadecimale(contenuto),
        dimensioneByte = contenuto.size.toLong(),
        formato = formato,
        licenza = "MIT",
        attribuzione = "k2-fsa",
    )

    private fun unaVoceServita(
        id: String,
        contenuto: ByteArray,
        formato: FormatoVoce = FormatoVoce.FILE,
    ): VoceCatalogo {
        val voce = unaVoce(id, contenuto, formato)
        server.servi(voce.url.percorsoDaUrl(), contenuto)
        return voce
    }

    private fun sha256Esadecimale(bytes: ByteArray): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))

    private fun String.percorsoDaUrl(): String = URI.create(this).path
}
