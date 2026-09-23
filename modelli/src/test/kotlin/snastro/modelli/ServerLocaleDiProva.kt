package snastro.modelli

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Loopback-only HTTP server for the gate's tests (AC-134: no real network reaches these tests).
 * Serves fixed content for a path and honours `Range` requests, so a resumed download (AC-131)
 * gets served correctly. Every request runs on its own (daemon) thread — needed so a test can hold
 * one request open (a redirect loop, an inactivity stall) while making others.
 */
internal class ServerLocaleDiProva : AutoCloseable {
    private val contenuti = mutableMapOf<String, ByteArray>()
    private val interrompiPrimaRichiesta = mutableSetOf<String>()
    private val redirezioni = mutableMapOf<String, String>()
    private val rifiuta416 = mutableSetOf<String>()
    private val ignoraRange = mutableSetOf<String>()
    private val sospesi = mutableMapOf<String, Int>()

    /** Bytes actually written by the server on the last request it answered (assertions on resume). */
    var byteServitiUltimaRichiesta: Int = 0
        private set

    private val executor = Executors.newCachedThreadPool { azione ->
        Thread(azione, "server-di-prova").apply { isDaemon = true }
    }
    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { scambio -> gestisci(scambio) }
        executor = this@ServerLocaleDiProva.executor
        start()
    }

    fun url(percorso: String): String = "http://127.0.0.1:${server.address.port}$percorso"

    fun servi(percorso: String, contenuto: ByteArray) {
        contenuti[percorso] = contenuto
    }

    fun serviConInterruzionePrimaRichiesta(percorso: String, contenuto: ByteArray) {
        contenuti[percorso] = contenuto
        interrompiPrimaRichiesta += percorso
    }

    /** `percorsoRedirect` answers `302` pointing at `versoPercorso` (a k2-fsa/GitHub-release-style redirect). */
    fun redirect(percorsoRedirect: String, versoPercorso: String) {
        redirezioni[percorsoRedirect] = versoPercorso
    }

    /** `percorso` answers `416` to any request carrying a `Range` header (AC-337). */
    fun rifiuta416PerRange(percorso: String) {
        rifiuta416 += percorso
    }

    /** `percorso` ignores any `Range` header and always answers `200` with the full content. */
    fun ignoraRichiesteDiRange(percorso: String) {
        ignoraRange += percorso
    }

    /** `percorso` writes only `byteIniziali` bytes then stalls forever without closing (AC-338). */
    fun serviConSospensione(percorso: String, contenuto: ByteArray, byteIniziali: Int) {
        contenuti[percorso] = contenuto
        sospesi[percorso] = byteIniziali
    }

    override fun close() {
        server.stop(0)
        executor.shutdownNow()
        executor.awaitTermination(1, TimeUnit.SECONDS)
    }

    private fun gestisci(scambio: HttpExchange) {
        val percorso = scambio.requestURI.path
        val gestito = rispondiRedirectSeConfigurato(scambio, percorso) ||
            rispondiNonTrovatoSeAssente(scambio, percorso) ||
            rispondi416SeConfigurato(scambio, percorso) ||
            rispondiTroncatoSeConfigurato(scambio, percorso) ||
            rispondiSospesoSeConfigurato(scambio, percorso)
        if (!gestito) rispondiContenuto(scambio, percorso)
    }

    private fun rispondiRedirectSeConfigurato(scambio: HttpExchange, percorso: String): Boolean {
        val verso = redirezioni[percorso] ?: return false
        scambio.responseHeaders.add("Location", verso)
        scambio.sendResponseHeaders(HTTP_REDIRECT, -1)
        scambio.close()
        return true
    }

    private fun rispondiNonTrovatoSeAssente(scambio: HttpExchange, percorso: String): Boolean {
        if (percorso in contenuti) return false
        scambio.sendResponseHeaders(HTTP_NON_TROVATO, -1)
        scambio.close()
        return true
    }

    private fun rangeRichiesto(scambio: HttpExchange, percorso: String): String? =
        if (percorso in ignoraRange) null else scambio.requestHeaders.getFirst("Range")

    private fun rispondi416SeConfigurato(scambio: HttpExchange, percorso: String): Boolean {
        if (rangeRichiesto(scambio, percorso) == null || percorso !in rifiuta416) return false
        scambio.sendResponseHeaders(HTTP_RANGE_NON_SODDISFACIBILE, -1)
        scambio.close()
        return true
    }

    private fun rispondiTroncatoSeConfigurato(scambio: HttpExchange, percorso: String): Boolean {
        if (rangeRichiesto(scambio, percorso) != null || percorso !in interrompiPrimaRichiesta) return false
        rispondiTroncato(scambio, contenuti.getValue(percorso))
        return true
    }

    private fun rispondiSospesoSeConfigurato(scambio: HttpExchange, percorso: String): Boolean {
        val byteIniziali = sospesi[percorso] ?: return false
        rispondiESospendi(scambio, contenuti.getValue(percorso), byteIniziali)
        return true
    }

    private fun rispondiContenuto(scambio: HttpExchange, percorso: String) {
        val bytes = contenuti.getValue(percorso)
        val range = rangeRichiesto(scambio, percorso)
        val inizio = range?.removePrefix("bytes=")?.removeSuffix("-")?.toIntOrNull() ?: 0
        val corpo = bytes.copyOfRange(inizio, bytes.size)
        byteServitiUltimaRichiesta = corpo.size
        val codice = if (range != null) HTTP_CONTENUTO_PARZIALE else HTTP_OK
        if (range != null) {
            scambio.responseHeaders.add("Content-Range", "bytes $inizio-${bytes.size - 1}/${bytes.size}")
        }
        scambio.sendResponseHeaders(codice, corpo.size.toLong())
        scambio.responseBody.use { it.write(corpo) }
    }

    private fun rispondiTroncato(scambio: HttpExchange, bytesCompleti: ByteArray) {
        val tagliati = bytesCompleti.copyOfRange(0, bytesCompleti.size / QUARTO)
        byteServitiUltimaRichiesta = tagliati.size
        // Declares the FULL length but writes only `tagliati`, then closes: the client sees a
        // connection that ended before every promised byte arrived (a dropped download).
        scambio.sendResponseHeaders(HTTP_OK, bytesCompleti.size.toLong())
        scambio.responseBody.write(tagliati)
        scambio.close()
    }

    private fun rispondiESospendi(scambio: HttpExchange, bytesCompleti: ByteArray, byteIniziali: Int) {
        byteServitiUltimaRichiesta = byteIniziali
        scambio.sendResponseHeaders(HTTP_OK, bytesCompleti.size.toLong())
        scambio.responseBody.write(bytesCompleti, 0, byteIniziali)
        scambio.responseBody.flush()
        // Never writes the rest and never closes: a true stall (not a clean EOF). The handler
        // thread (daemon, its own thread per request) just parks here until the server is closed.
        try {
            CountDownLatch(1).await()
        } catch (ignore: InterruptedException) {
            // `close()` shuts the executor down; the interrupt is how this thread is released.
            Thread.currentThread().interrupt()
        }
    }

    private companion object {
        const val QUARTO = 4
        const val HTTP_OK = 200
        const val HTTP_REDIRECT = 302
        const val HTTP_NON_TROVATO = 404
        const val HTTP_RANGE_NON_SODDISFACIBILE = 416
        const val HTTP_CONTENUTO_PARZIALE = 206
    }
}
