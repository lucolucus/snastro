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
 * Model catalogue provisioning (ADR 0008 Amendment (c), ADR 0025): first-run download with
 * progress, verify-then-atomic install, resumable partial downloads, redirects, an inactivity
 * timeout, a free-space pre-check, and a per-[cartella] cache. `:modelli` is the ONLY module
 * allowed network I/O (CR-3) — every request this class makes goes through [cliente].
 *
 * Catalogue entries split in two (ADR 0025 §1): [VoceCatalogo.obbligatoria] ones are onboarding's
 * concern — [pronti]/[mancanti] range over them only, and the bulk [scarica] installs only them.
 * An OPTIONAL entry (`obbligatoria = false`, e.g. Sintesi's on-demand LLM) is never touched by the
 * bulk [scarica]; it is reached only by its own id, through [installata] and the single-entry
 * [scarica].
 *
 * Both [scarica] overloads BLOCK the calling thread for the whole operation (no coroutine/async
 * variant); a UI caller runs them on a background dispatcher. Concurrent calls on the SAME instance
 * are serialized (never interleaved) by an in-process lock — they never race on the same
 * `.part`/temp files, whichever entry each call names.
 */
public class ProvisioningModelli(
    private val catalogo: CatalogoModelli,
    private val cartella: Path = CartellaCacheModelli.risolvi(),
    private val cliente: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(TIMEOUT_CONNESSIONE_SECONDI))
        .followRedirects(HttpClient.Redirect.NORMAL) // AC-336 — the JDK default is NEVER.
        .build(),
    private val timeoutInattivita: Duration = Duration.ofSeconds(TIMEOUT_INATTIVITA_SECONDI),
    private val operazioniFile: OperazioniFile = OperazioniFileReali,
    // AC-S31: a plain lambda seam (frugality ladder rung 5) — a real java.nio.file.FileStore
    // can't be forced to a tiny usable-space value from a test, so the query itself is injected.
    private val spazioDisponibileByte: (Path) -> Long = { Files.getFileStore(it).usableSpace },
) {
    private val lock = ReentrantLock()

    /** True once every REQUIRED (`obbligatoria`) catalogue entry is installed in [cartella]. */
    public fun pronti(): Boolean = mancanti().isEmpty()

    /**
     * REQUIRED catalogue entries not yet installed in [cartella] (AC-333: a stale `.sha256` counts
     * as missing). An optional entry (ADR 0025 §1) never appears here.
     */
    public fun mancanti(): List<VoceCatalogo> = catalogo.voci.filter { it.obbligatoria }.filterNot { installata(it) }

    /** Where [id]'s installed DIRECTORY lives, whether it is installed yet or not. */
    public fun percorso(id: String): Path = cartella.resolve(id)

    /**
     * True iff [id] names a catalogue entry AND [percorso] holds it installed (AC-S28: the same
     * marker rule as [mancanti] — directory exists AND its `.sha256` matches the catalogue's). An
     * [id] absent from the catalogue is never installed.
     */
    public fun installata(id: String): Boolean = catalogo.voci.find { it.id == id }?.let { installata(it) } ?: false

    /**
     * Downloads and installs every [mancanti] REQUIRED entry, one at a time, stopping at the first
     * failure. Each entry is streamed into `<id>.part`, verified, then extracted/copied into a
     * temporary directory next to [percorso] and atomically renamed into place (AC-130) — the
     * final path is never visible before verification and extraction complete. An optional entry
     * (ADR 0025 §1) is NEVER downloaded here — zero requests reach its URL.
     */
    public fun scarica(progresso: (id: String, scaricati: Long, totali: Long) -> Unit): Esito<Unit> = lock.withLock {
        Files.createDirectories(cartella)
        pulisciTemporaneeResidue() // AC-333: a `*.tmp-*` left by a crash never lingers.
        for (voce in mancanti()) {
            val esito = scaricaEInstalla(voce, progresso)
            if (esito is Esito.Errore) return@withLock esito
        }
        Esito.Ok(Unit)
    }

    /**
     * Downloads and installs the ONE catalogue entry named [id] — required or optional (ADR 0025
     * §1/§4, AC-S29): the same resumable, verify-then-atomic protocol and lock as the bulk
     * [scarica], but touches no other entry's directory or `.part`. A no-op (`Ok`) when [id] is
     * already [installata]; [ErroreModelli.DownloadFallito] when [id] names no catalogue entry.
     */
    public fun scarica(id: String, progresso: (scaricati: Long, totali: Long) -> Unit): Esito<Unit> = lock.withLock {
        val voce = catalogo.voci.find { it.id == id }
            ?: return@withLock Esito.Errore(ErroreModelli.DownloadFallito("id sconosciuto nel catalogo: '$id'"))
        if (installata(voce)) return@withLock Esito.Ok(Unit)
        Files.createDirectories(cartella)
        pulisciTemporaneeResidue()
        scaricaEInstalla(voce) { _, scaricati, totali -> progresso(scaricati, totali) }
    }

    private fun scaricaEInstalla(
        voce: VoceCatalogo,
        progresso: (id: String, scaricati: Long, totali: Long) -> Unit,
    ): Esito<Unit> = verificaSpazioLibero(voce).poi {
        ScaricamentoAsset(voce, cartella, cliente, timeoutInattivita, progresso).esegui()
            .poi { InstallatoreAsset(voce, cartella, operazioniFile).installa() }
    }

    /** AC-S31: checked BEFORE any network request; the existing `.part` (if any) counts as already secured. */
    private fun verificaSpazioLibero(voce: VoceCatalogo): Esito<Unit> {
        val giaScaricati = FileParziale(cartella.resolve("${voce.id}.part")).dimensione()
        val necessari = voce.dimensioneByte - giaScaricati + MARGINE_SPAZIO_BYTE
        return if (spazioDisponibileByte(cartella) < necessari) {
            Esito.Errore(ErroreModelli.SpazioInsufficiente(voce.dimensioneByte))
        } else {
            Esito.Ok(Unit)
        }
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
        const val MARGINE_SPAZIO_BYTE = 64L * 1024 * 1024 // 64 MiB (ADR 0025 §3)
        val REGEX_TEMPORANEA = Regex(""".+\.tmp-\d+$""")
    }
}
