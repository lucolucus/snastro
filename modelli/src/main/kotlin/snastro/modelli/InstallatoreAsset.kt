package snastro.modelli

import snastro.kernel.Esito
import snastro.kernel.poi
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.Locale

/**
 * Turns a verified `<cartella>/<id>.part` (ScaricamentoAsset already checked its SHA-256) into the
 * installed `<cartella>/<id>/` (ADR 0008 Amendment (c)): extract ([FormatoVoce.TAR_BZ2]) or move
 * ([FormatoVoce.FILE]) into a temporary directory NEXT TO the target, write the `.sha256` marker,
 * then an ATOMIC directory rename — nothing under `<id>/` is ever visible half-written. A stale
 * `<id>/` (a re-install, AC-333) is renamed aside first (POSIX `rename` cannot atomically replace a
 * non-empty directory) and deleted only after the swap succeeds.
 */
internal class InstallatoreAsset(private val voce: VoceCatalogo, private val cartella: Path) {
    private val parziale = cartella.resolve("${voce.id}.part")
    private val destinazione = cartella.resolve(voce.id)
    private val temp = cartella.resolve("${voce.id}.tmp-0")

    fun installa(): Esito<Unit> {
        val esito = preparaTemp()
            .poi { popola() }
            .poi { scriviMarcatore() }
            .poi { scambiaConDestinazione() }
        return if (esito is Esito.Errore) {
            ripulisciDopoErrore(esito)
        } else {
            eliminaParzialeDopoInstallazione()
        }
    }

    private fun preparaTemp(): Esito<Unit> = try {
        ProtocolloInstallazione.eliminaRicorsivo(temp)
        Files.createDirectories(temp)
        Esito.Ok(Unit)
    } catch (e: IOException) {
        Esito.Errore(ErroreModelli.ScritturaFallita(motivoLocale(e)))
    }

    private fun popola(): Esito<Unit> = when (voce.formato) {
        FormatoVoce.TAR_BZ2 -> EstrazioneTarBz2(voce, parziale, temp).estrai()
        FormatoVoce.FILE -> installaFile()
    }

    private fun installaFile(): Esito<Unit> {
        val nomeFile = nomeFileDaUrl(voce.url)
        return try {
            Files.move(parziale, temp.resolve(nomeFile))
            Esito.Ok(Unit)
        } catch (e: IOException) {
            Esito.Errore(ErroreModelli.ScritturaFallita(motivoLocale(e)))
        }
    }

    private fun scriviMarcatore(): Esito<Unit> = try {
        Files.newOutputStream(temp.resolve(ProtocolloInstallazione.MARCATORE), StandardOpenOption.CREATE_NEW).use {
            it.write(voce.sha256.lowercase(Locale.ROOT).toByteArray())
        }
        Esito.Ok(Unit)
    } catch (e: IOException) {
        Esito.Errore(ErroreModelli.ScritturaFallita(motivoLocale(e)))
    }

    private fun scambiaConDestinazione(): Esito<Unit> {
        val vecchiaDaEliminare = if (Files.exists(destinazione)) {
            val daParte = cartella.resolve("${voce.id}.old-${System.nanoTime()}")
            try {
                Files.move(destinazione, daParte)
            } catch (e: IOException) {
                return Esito.Errore(ErroreModelli.ScritturaFallita(motivoLocale(e)))
            }
            daParte
        } else {
            null
        }
        return try {
            Files.move(temp, destinazione, StandardCopyOption.ATOMIC_MOVE)
            if (vecchiaDaEliminare != null) ProtocolloInstallazione.eliminaRicorsivo(vecchiaDaEliminare)
            Esito.Ok(Unit)
        } catch (e: IOException) {
            Esito.Errore(ErroreModelli.ScritturaFallita(motivoLocale(e)))
        }
    }

    private fun eliminaParzialeDopoInstallazione(): Esito<Unit> = try {
        Files.deleteIfExists(parziale)
        Esito.Ok(Unit)
    } catch (e: IOException) {
        Esito.Errore(ErroreModelli.ScritturaFallita(motivoLocale(e)))
    }

    /**
     * Nothing is renamed into place on failure (AC-332): the temporary directory is best-effort
     * cleaned up here, and swept again at the next `scarica()` if this cleanup itself fails
     * (AC-333) — the original [errore] is always what is returned, never masked by a cleanup
     * failure.
     */
    private fun ripulisciDopoErrore(errore: Esito.Errore): Esito<Unit> {
        try {
            ProtocolloInstallazione.eliminaRicorsivo(temp)
        } catch (ignore: IOException) {
            return errore
        }
        return errore
    }

    private fun motivoLocale(e: IOException): String = e.message ?: e.javaClass.simpleName

    private companion object {
        fun nomeFileDaUrl(url: String): String {
            val ultimo = URI.create(url).path.substringAfterLast('/')
            return ultimo.ifBlank { "asset" }
        }
    }
}
