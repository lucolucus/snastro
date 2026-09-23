package snastro.modelli

import org.junit.jupiter.api.AfterEach
import snastro.kernel.Esito
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat
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
    fun `dopo un download riuscito il file e installato, pronti diventa vero e il progresso viene avvisato`() {
        val contenuto = "contenuto del modello di prova".toByteArray()
        val voce = unaVoce("a", contenuto)
        server.servi(server.url("/modelli/a").percorsoDaUrl(), contenuto)
        val provisioning = ProvisioningModelli(CatalogoModelli(listOf(voce)), cartella)
        val progressi = mutableListOf<Triple<String, Long, Long>>()

        val esito = provisioning.scarica { id, scaricati, totali -> progressi += Triple(id, scaricati, totali) }

        esito.atteso()
        assertTrue(provisioning.pronti())
        assertEquals(emptyList(), provisioning.mancanti())
        assertContentEquals(contenuto, Files.readAllBytes(provisioning.percorso("a")))
        assertTrue(progressi.isNotEmpty())
        assertEquals("a", progressi.last().first)
        assertEquals(contenuto.size.toLong(), progressi.last().second)
        assertEquals(contenuto.size.toLong(), progressi.last().third)
    }

    @Test
    fun `AC-129 hash errato restituisce HashNonValido e non installa nulla`() {
        val dichiarato = "contenuto atteso dal catalogo".toByteArray()
        val servitoDavvero = "contenuto diverso da quello atteso".toByteArray()
        val voce = unaVoce("a", dichiarato)
        server.servi(server.url("/modelli/a").percorsoDaUrl(), servitoDavvero)
        val provisioning = ProvisioningModelli(CatalogoModelli(listOf(voce)), cartella)

        val esito = provisioning.scarica { _, _, _ -> }

        esito.erroreAtteso<ErroreModelli.HashNonValido>()
        assertFalse(Files.exists(provisioning.percorso("a")))
        assertFalse(Files.exists(cartella.resolve("a.part")))
        assertFalse(provisioning.pronti())
    }

    @Test
    fun `AC-130 il file finale non esiste mai prima che l hash sia verificato`() {
        // Provato dagli altri due casi, con lo stesso meccanismo (file temporaneo + spostamento
        // atomico SOLO dopo la verifica, mai prima): hash errato (sopra) -> percorso(id) resta
        // assente; download interrotto (sotto) -> percorso(id) resta assente finche' non completa.
        val dichiarato = "x".repeat(100).toByteArray()
        val voce = unaVoce("a", dichiarato)
        server.servi(server.url("/modelli/a").percorsoDaUrl(), "y".repeat(100).toByteArray())
        val provisioning = ProvisioningModelli(CatalogoModelli(listOf(voce)), cartella)

        provisioning.scarica { _, _, _ -> }

        assertFalse(Files.exists(provisioning.percorso("a")), "nessun file finale prima della verifica dell'hash")
    }

    @Test
    fun `AC-131 un download interrotto riprende dal punto raggiunto`() {
        val contenuto = ByteArray(20_000) { (it % 256).toByte() }
        val voce = unaVoce("a", contenuto)
        server.serviConInterruzionePrimaRichiesta(server.url("/modelli/a").percorsoDaUrl(), contenuto)
        val provisioning = ProvisioningModelli(CatalogoModelli(listOf(voce)), cartella)

        val primoEsito = provisioning.scarica { _, _, _ -> }

        assertTrue(primoEsito is Esito.Errore, "il primo tentativo, interrotto, non completa")
        val parziale = cartella.resolve("a.part")
        assertTrue(Files.isRegularFile(parziale), "il file temporaneo resta per la ripresa")
        val giaScaricati = Files.size(parziale)
        assertTrue(giaScaricati in 1 until contenuto.size.toLong(), "il parziale non e' ne' vuoto ne' completo")
        assertFalse(Files.exists(provisioning.percorso("a")))

        val secondoEsito = provisioning.scarica { _, _, _ -> }

        secondoEsito.atteso()
        assertContentEquals(contenuto, Files.readAllBytes(provisioning.percorso("a")))
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

    private fun unaVoce(id: String, contenuto: ByteArray): VoceCatalogo = VoceCatalogo(
        id = id,
        ruolo = "asr",
        url = server.url("/modelli/$id"),
        sha256 = sha256Esadecimale(contenuto),
        dimensioneByte = contenuto.size.toLong(),
        licenza = "MIT",
        attribuzione = "k2-fsa",
    )

    private fun sha256Esadecimale(bytes: ByteArray): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))

    private fun String.percorsoDaUrl(): String = java.net.URI.create(this).path
}
