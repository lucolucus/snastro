package snastro.modelli

import snastro.kernel.Esito
import snastro.kernel.poi
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.time.Duration
import java.util.HexFormat

/**
 * Model catalogue provisioning (ADR 0008): first-run download with progress, verify-then-atomic
 * move, resumable partial downloads, per-[cartella] cache. `:modelli` is the ONLY module allowed
 * network I/O (CR-3) — every request this class makes goes through [cliente].
 */
public class ProvisioningModelli(
    private val catalogo: CatalogoModelli,
    private val cartella: Path = CartellaCacheModelli.risolvi(),
    private val cliente: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(TIMEOUT_CONNESSIONE_SECONDI))
        .build(),
) {
    /** True once every catalogue entry is installed in [cartella]. */
    public fun pronti(): Boolean = mancanti().isEmpty()

    /** Catalogue entries not yet installed in [cartella]. */
    public fun mancanti(): List<VoceCatalogo> = catalogo.voci.filterNot { installata(it) }

    /** Where [id], installed or not, lives in the cache. */
    public fun percorso(id: String): Path = cartella.resolve(id)

    /**
     * Downloads every [mancanti] entry, one at a time, stopping at the first failure. Each entry
     * is streamed into a `<id>.part` temporary file and moved atomically into [percorso] only
     * after its SHA-256 verifies (AC-130); an interrupted transfer leaves the partial file in
     * place so a later call resumes it with a `Range` request (AC-131).
     */
    public fun scarica(progresso: (id: String, scaricati: Long, totali: Long) -> Unit): Esito<Unit> {
        Files.createDirectories(cartella)
        for (voce in mancanti()) {
            val esito = Scaricamento(voce, progresso).esegui()
            if (esito is Esito.Errore) return esito
        }
        return Esito.Ok(Unit)
    }

    private fun installata(voce: VoceCatalogo): Boolean = Files.isRegularFile(percorso(voce.id))

    /** One entry's download: request (with resume) -> stream to `.part` -> verify -> atomic move. */
    private inner class Scaricamento(
        private val voce: VoceCatalogo,
        private val progresso: (id: String, scaricati: Long, totali: Long) -> Unit,
    ) {
        private val parziale = cartella.resolve("${voce.id}.part")
        private val giaScaricati = if (Files.isRegularFile(parziale)) Files.size(parziale) else 0L

        fun esegui(): Esito<Unit> = richiedi().poi { scrivi(it) }.poi { verifica(it) }

        private fun richiedi(): Esito<HttpResponse<InputStream>> {
            val richiesta = HttpRequest.newBuilder(URI.create(voce.url))
                .apply { if (giaScaricati > 0) header("Range", "bytes=$giaScaricati-") }
                .GET()
                .build()
            val risposta = try {
                cliente.send(richiesta, HttpResponse.BodyHandlers.ofInputStream())
            } catch (ignore: IOException) {
                return Esito.Errore(ErroreModelli.ReteAssente)
            }
            return if (risposta.statusCode() == HTTP_OK || risposta.statusCode() == HTTP_CONTENUTO_PARZIALE) {
                Esito.Ok(risposta)
            } else {
                Esito.Errore(ErroreModelli.DownloadFallito("HTTP ${risposta.statusCode()} per '${voce.id}'"))
            }
        }

        private fun scrivi(risposta: HttpResponse<InputStream>): Esito<Long> {
            val riprende = giaScaricati > 0 && risposta.statusCode() == HTTP_CONTENUTO_PARZIALE
            val scritti = try {
                copia(risposta.body(), riprende)
            } catch (ignore: IOException) {
                return Esito.Errore(ErroreModelli.ReteAssente)
            }
            return Esito.Ok(scritti)
        }

        private fun copia(flusso: InputStream, riprende: Boolean): Long {
            var scritti = if (riprende) giaScaricati else 0L
            val uscita = if (riprende) {
                Files.newOutputStream(parziale, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
            } else {
                Files.newOutputStream(
                    parziale,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE,
                )
            }
            uscita.use { scritti = copiaFlusso(flusso, it, scritti) }
            return scritti
        }

        private fun copiaFlusso(flusso: InputStream, uscita: java.io.OutputStream, scrittiIniziali: Long): Long {
            var scritti = scrittiIniziali
            val buffer = ByteArray(DIMENSIONE_BUFFER)
            while (true) {
                val letti = flusso.read(buffer)
                if (letti == -1) break
                uscita.write(buffer, 0, letti)
                scritti += letti
                progresso(voce.id, scritti, voce.dimensioneByte)
            }
            return scritti
        }

        private fun verifica(scritti: Long): Esito<Unit> {
            if (scritti < voce.dimensioneByte) {
                // Fewer bytes than promised, no exception: the connection ended early without
                // signalling it. Keep the partial file — a later call resumes it (AC-131).
                return Esito.Errore(ErroreModelli.ReteAssente)
            }
            val hash = sha256Di(parziale)
            return if (hash.equals(voce.sha256, ignoreCase = true)) {
                Files.move(
                    parziale,
                    percorso(voce.id),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
                Esito.Ok(Unit)
            } else {
                Files.deleteIfExists(parziale)
                Esito.Errore(ErroreModelli.HashNonValido(voce.id))
            }
        }
    }

    private fun sha256Di(file: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(file).use { flusso ->
            val buffer = ByteArray(DIMENSIONE_BUFFER)
            while (true) {
                val letti = flusso.read(buffer)
                if (letti == -1) break
                digest.update(buffer, 0, letti)
            }
        }
        return HexFormat.of().formatHex(digest.digest())
    }

    private companion object {
        const val DIMENSIONE_BUFFER = 64 * 1024
        const val TIMEOUT_CONNESSIONE_SECONDI = 10L
        const val HTTP_OK = 200
        const val HTTP_CONTENUTO_PARZIALE = 206
    }
}
