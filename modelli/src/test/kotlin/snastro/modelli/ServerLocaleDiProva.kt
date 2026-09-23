package snastro.modelli

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress

/**
 * Loopback-only HTTP server for the gate's tests (AC-134: no real network reaches these tests).
 * Serves fixed content for a path and honours `Range` requests, so a resumed download (AC-131)
 * gets served correctly; [serviConInterruzionePrimaRichiesta] additionally cuts the FIRST
 * (non-resumed) response short, simulating a connection dropped mid-transfer.
 */
internal class ServerLocaleDiProva : AutoCloseable {
    private val contenuti = mutableMapOf<String, ByteArray>()
    private val interrompiPrimaRichiesta = mutableSetOf<String>()

    /** Bytes actually written by the server on the last request it answered (assertions on resume). */
    var byteServitiUltimaRichiesta: Int = 0
        private set

    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { scambio -> gestisci(scambio) }
        executor = null
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

    override fun close() {
        server.stop(0)
    }

    private fun gestisci(scambio: HttpExchange) {
        val percorso = scambio.requestURI.path
        val bytes = contenuti[percorso]
        if (bytes == null) {
            scambio.sendResponseHeaders(404, -1)
            scambio.close()
            return
        }
        val range = scambio.requestHeaders.getFirst("Range")
        if (range == null && percorso in interrompiPrimaRichiesta) {
            rispondiTroncato(scambio, bytes)
            return
        }
        val inizio = range?.removePrefix("bytes=")?.removeSuffix("-")?.toIntOrNull() ?: 0
        val corpo = bytes.copyOfRange(inizio, bytes.size)
        byteServitiUltimaRichiesta = corpo.size
        val codice = if (range != null) 206 else 200
        scambio.sendResponseHeaders(codice, corpo.size.toLong())
        scambio.responseBody.use { it.write(corpo) }
    }

    private fun rispondiTroncato(scambio: HttpExchange, bytesCompleti: ByteArray) {
        val tagliati = bytesCompleti.copyOfRange(0, bytesCompleti.size / QUARTO)
        byteServitiUltimaRichiesta = tagliati.size
        // Declares the FULL length but writes only `tagliati`, then closes: the client sees a
        // connection that ended before every promised byte arrived (a dropped download).
        scambio.sendResponseHeaders(200, bytesCompleti.size.toLong())
        scambio.responseBody.write(tagliati)
        scambio.close()
    }

    private companion object {
        const val QUARTO = 4
    }
}
