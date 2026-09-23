package snastro.modelli

import snastro.kernel.Esito
import snastro.kernel.poi
import java.net.http.HttpClient
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Model catalogue provisioning (ADR 0008 Amendment (c)): first-run download with progress,
 * verify-then-atomic install, resumable partial downloads, redirects, an inactivity timeout, and a
 * per-[cartella] cache. `:modelli` is the ONLY module allowed network I/O (CR-3) — every request
 * this class makes goes through [cliente].
 *
 * [scarica] BLOCKS the calling thread for the whole operation (no coroutine/async variant); a UI
 * caller runs it on a background dispatcher. Concurrent calls on the SAME instance are serialized
 * (never interleaved) by an in-process lock — they never race on the same `.part`/temp files.
 */
public class ProvisioningModelli(
    private val catalogo: CatalogoModelli,
    private val cartella: Path = CartellaCacheModelli.risolvi(),
    private val cliente: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(TIMEOUT_CONNESSIONE_SECONDI))
        .followRedirects(HttpClient.Redirect.NORMAL) // AC-336 — the JDK default is NEVER.
        .build(),
    private val timeoutInattivita: Duration = Duration.ofSeconds(TIMEOUT_INATTIVITA_SECONDI),
) {
    private val lock = ReentrantLock()

    /** True once every catalogue entry is installed in [cartella]. */
    public fun pronti(): Boolean = mancanti().isEmpty()

    /** Catalogue entries not yet installed in [cartella] (AC-333: a stale `.sha256` counts as missing). */
    public fun mancanti(): List<VoceCatalogo> = catalogo.voci.filterNot { installata(it) }

    /** Where [id]'s installed DIRECTORY lives, whether it is installed yet or not. */
    public fun percorso(id: String): Path = cartella.resolve(id)

    /**
     * Downloads and installs every [mancanti] entry, one at a time, stopping at the first failure.
     * Each entry is streamed into `<id>.part`, verified, then extracted/copied into a temporary
     * directory next to [percorso] and atomically renamed into place (AC-130) — the final path is
     * never visible before verification and extraction complete.
     */
    public fun scarica(progresso: (id: String, scaricati: Long, totali: Long) -> Unit): Esito<Unit> = lock.withLock {
        Files.createDirectories(cartella)
        pulisciTemporaneeResidue() // AC-333: a `*.tmp-*` left by a crash never lingers.
        for (voce in mancanti()) {
            val esito = ScaricamentoAsset(voce, cartella, cliente, timeoutInattivita, progresso).esegui()
                .poi { InstallatoreAsset(voce, cartella).installa() }
            if (esito is Esito.Errore) return@withLock esito
        }
        Esito.Ok(Unit)
    }

    /** Installed = the directory exists AND its `.sha256` marker matches the catalogue's (AC-333). */
    private fun installata(voce: VoceCatalogo): Boolean {
        val cartellaModello = percorso(voce.id)
        val marcatore = cartellaModello.resolve(ProtocolloInstallazione.MARCATORE)
        if (!Files.isDirectory(cartellaModello) || !Files.isRegularFile(marcatore)) return false
        return Files.readString(marcatore).trim().equals(voce.sha256, ignoreCase = true)
    }

    private fun pulisciTemporaneeResidue() {
        Files.newDirectoryStream(cartella).use { flusso ->
            flusso.filter { Files.isDirectory(it) && REGEX_TEMPORANEA.matches(it.fileName.toString()) }
                .forEach { ProtocolloInstallazione.eliminaRicorsivo(it) }
        }
    }

    private companion object {
        const val TIMEOUT_CONNESSIONE_SECONDI = 10L
        const val TIMEOUT_INATTIVITA_SECONDI = 20L
        val REGEX_TEMPORANEA = Regex(""".+\.tmp-\d+$""")
    }
}
