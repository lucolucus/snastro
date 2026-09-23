package snastro.modelli

import snastro.kernel.Esito
import snastro.kernel.poi
import java.io.IOException
import java.io.InputStream
import java.net.ConnectException
import java.net.URI
import java.net.UnknownHostException
import java.net.http.HttpClient
import java.net.http.HttpConnectTimeoutException
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.time.Duration

/**
 * Downloads one [VoceCatalogo]'s asset into `<cartella>/<id>.part` and verifies it (ADR 0008
 * Amendment (c)): resumable (`Range`), redirects are followed by [cliente]
 * (`HttpClient.Redirect.NORMAL`, wired by [ProvisioningModelli]), recovers a stuck `.part` (a
 * pre-existing partial at or beyond the declared size, or a `416` on resume — discarded and
 * retried from 0 exactly once, AC-337), and gives up on a stalled connection (an inactivity
 * watchdog closes the response stream after [timeoutInattivita] without a byte, [CopiaConTimeout],
 * AC-338). Leaves the `.part` verified and ready for [InstallatoreAsset] on success; on a genuine
 * network-absence it leaves nothing, on every other failure the `.part` (if any) is preserved so a
 * later call resumes it (AC-131/AC-337). Blocks the calling thread.
 */
internal class ScaricamentoAsset(
    private val voce: VoceCatalogo,
    cartella: Path,
    private val cliente: HttpClient,
    private val timeoutInattivita: Duration,
    private val progresso: (id: String, scaricati: Long, totali: Long) -> Unit,
) {
    private val parziale = FileParziale(cartella.resolve("${voce.id}.part"))

    fun esegui(): Esito<Unit> = tentativo(permettiRipartenza = true)

    private fun tentativo(permettiRipartenza: Boolean): Esito<Unit> {
        val giaScaricati = parziale.dimensione()
        return if (giaScaricati >= voce.dimensioneByte && giaScaricati > 0) {
            verificaSenzaRichiesta(permettiRipartenza)
        } else {
            richiedi(giaScaricati).poi { risposta -> gestisci(risposta, giaScaricati, permettiRipartenza) }
        }
    }

    /** AC-337: a `.part` already at or beyond the declared size is verified before any request. */
    private fun verificaSenzaRichiesta(permettiRipartenza: Boolean): Esito<Unit> {
        val corrisponde = parziale.dimensione() == voce.dimensioneByte &&
            parziale.sha256().equals(voce.sha256, ignoreCase = true)
        return when {
            corrisponde -> Esito.Ok(Unit)
            permettiRipartenza -> {
                parziale.elimina()
                tentativo(permettiRipartenza = false)
            }
            else -> {
                parziale.elimina()
                Esito.Errore(ErroreModelli.HashNonValido(voce.id))
            }
        }
    }

    private fun richiedi(giaScaricati: Long): Esito<HttpResponse<InputStream>> {
        val richiesta = HttpRequest.newBuilder(URI.create(voce.url))
            .timeout(TIMEOUT_RICHIESTA)
            .apply { if (giaScaricati > 0) header("Range", "bytes=$giaScaricati-") }
            .GET()
            .build()
        return try {
            Esito.Ok(cliente.send(richiesta, HttpResponse.BodyHandlers.ofInputStream()))
        } catch (ignore: ConnectException) {
            Esito.Errore(ErroreModelli.ReteAssente)
        } catch (ignore: UnknownHostException) {
            Esito.Errore(ErroreModelli.ReteAssente)
        } catch (ignore: HttpConnectTimeoutException) {
            Esito.Errore(ErroreModelli.ReteAssente)
        } catch (e: IOException) {
            Esito.Errore(ErroreModelli.DownloadFallito("'${voce.id}': ${e.message ?: e.javaClass.simpleName}"))
        }
    }

    private fun gestisci(
        risposta: HttpResponse<InputStream>,
        giaScaricati: Long,
        permettiRipartenza: Boolean,
    ): Esito<Unit> = when (val codice = risposta.statusCode()) {
        // AC-337: the server refuses to resume at our offset — discard `.part`, retry from 0 once
        // (within THIS `scarica()` attempt, so it succeeds, not just on the caller's next call).
        HTTP_RANGE_NON_SODDISFACIBILE -> {
            risposta.body().close()
            if (permettiRipartenza) {
                parziale.elimina()
                tentativo(permettiRipartenza = false)
            } else {
                Esito.Errore(ErroreModelli.DownloadFallito("HTTP $codice per '${voce.id}'"))
            }
        }
        HTTP_CONTENUTO_PARZIALE ->
            if (giaScaricati > 0 && rangeIniziaA(risposta, giaScaricati)) {
                scriviEVerifica(risposta, riprende = true)
            } else {
                risposta.body().close()
                Esito.Errore(ErroreModelli.DownloadFallito("intervallo 206 inatteso per '${voce.id}'"))
            }
        HTTP_OK -> scriviEVerifica(risposta, riprende = false)
        else -> {
            risposta.body().close()
            Esito.Errore(ErroreModelli.DownloadFallito("HTTP $codice per '${voce.id}'"))
        }
    }

    private fun rangeIniziaA(risposta: HttpResponse<InputStream>, atteso: Long): Boolean {
        val valore = risposta.headers().firstValue("Content-Range").orElse(null) ?: return false
        val inizio = valore.removePrefix("bytes ").substringBefore('-').trim().toLongOrNull()
        return inizio == atteso
    }

    private fun scriviEVerifica(risposta: HttpResponse<InputStream>, riprende: Boolean): Esito<Unit> {
        val copiatore = CopiaConTimeout(voce.id, voce.dimensioneByte, timeoutInattivita, progresso)
        val esitoScrittura = risposta.body().use { flusso -> copiaVerso(flusso, riprende, copiatore) }
        return esitoScrittura.poi { scritti -> verifica(scritti) }
    }

    private fun copiaVerso(flusso: InputStream, riprende: Boolean, copiatore: CopiaConTimeout): Esito<Long> {
        val uscita = try {
            parziale.apriScrittura(riprende)
        } catch (e: IOException) {
            return Esito.Errore(ErroreModelli.ScritturaFallita(e.message ?: e.javaClass.simpleName))
        }
        val iniziali = if (riprende) parziale.dimensione() else 0L
        return uscita.use { copiatore.copia(flusso, it, iniziali) }
    }

    private fun verifica(scritti: Long): Esito<Unit> {
        if (scritti < voce.dimensioneByte) {
            // Clean end-of-stream before every promised byte arrived: the `.part` is KEPT, a later
            // call resumes it (AC-131) — but THIS attempt is reported as failed (AC-337).
            val motivo = "connessione interrotta a $scritti/${voce.dimensioneByte} byte per '${voce.id}'"
            return Esito.Errore(ErroreModelli.DownloadFallito(motivo))
        }
        return if (parziale.sha256().equals(voce.sha256, ignoreCase = true)) {
            Esito.Ok(Unit)
        } else {
            parziale.elimina()
            Esito.Errore(ErroreModelli.HashNonValido(voce.id))
        }
    }

    private companion object {
        const val HTTP_OK = 200
        const val HTTP_CONTENUTO_PARZIALE = 206
        const val HTTP_RANGE_NON_SODDISFACIBILE = 416
        val TIMEOUT_RICHIESTA: Duration = Duration.ofMinutes(2)
    }
}
