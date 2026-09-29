package snastro.avvio.modelli

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import snastro.kernel.ErroreDominio
import snastro.kernel.Esito
import snastro.modelli.CatalogoModelli
import snastro.modelli.ErroreModelli
import snastro.modelli.ProvisioningModelli
import snastro.modelli.VoceCatalogo
import snastro.ui.modelli.ErroreServizioModelli
import snastro.ui.modelli.LicenzaVista
import snastro.ui.modelli.ServizioModelli
import snastro.ui.modelli.StatoModelli
import snastro.ui.modelli.StatoModelloFacoltativo
import snastro.ui.stile.LICENZE_CARATTERI
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.InvalidPathException
import java.util.logging.Level
import java.util.logging.Logger

/**
 * [ServizioModelli] (`:ui`'s S5 port, `tec-modelli-ui`) over `:modelli`'s [ProvisioningModelli]
 * (AC-329): every [ErroreModelli] is mapped 1:1 onto its namesake [ErroreServizioModelli]
 * ([mappaErrore]), so `:ui` never sees `snastro.modelli`.
 *
 * AC-352: an I/O fault escaping the provisioning ([IOException], [UncheckedIOException],
 * [InvalidPathException]) is caught here — [scarica] ends in [StatoModelli.Errore]
 * (`ScritturaFallita(motivo)`), never a crash and never a [StatoModelli] stuck in `InDownload`, and a
 * new [scarica] ('Riprova') simply runs again; `pronti()`/`mancanti()` throwing at startup gives
 * [StatoModelli.Mancanti] over the catalogue's REQUIRED (`obbligatoria`) entries only (AC-S32 — an
 * optional entry never counts here either), the app starts anyway.
 *
 * `tec-modelli-ui-facoltativo` (ADR 0025 §4, block `modello-facoltativo-avvio`): [scaricaFacoltativo]/
 * [statoFacoltativi] are the SAME pattern one level down, keyed by [VoceCatalogo.id], over
 * [ProvisioningModelli]'s by-id [installata]/`scarica(id, …)` — the SAME [ProvisioningModelli]
 * instance as [pronti]/[mancanti]/[scaricaModelli] (never a second one: [di]'s single caller,
 * `ModelliApp`, shares it). [statoFacoltativi] is seeded once per optional catalogue entry at
 * construction (a fresh [installata] read), so [snastro.avvio.sintesi.DisponibilitaModelloLinguisticoAvvio]
 * (the SAME state holder, ADR 0025 §4) never needs the map to have been touched by a download first.
 *
 * The provisioning is reached through five functions (not the final class itself) so a test can make
 * each of them throw; [di] binds the real [ProvisioningModelli].
 */
internal class ServizioModelliProvisioning(
    private val catalogo: CatalogoModelli,
    private val pronti: () -> Boolean,
    private val mancanti: () -> List<VoceCatalogo>,
    private val scaricaModelli: ((String, Long, Long) -> Unit) -> Esito<Unit>,
    private val installata: (String) -> Boolean,
    private val scaricaFacoltativoModello: (String, (Long, Long) -> Unit) -> Esito<Unit>,
) : ServizioModelli {
    private val _stato = MutableStateFlow(statoAttuale())
    override val stato: StateFlow<StatoModelli> = _stato.asStateFlow()

    /** Blocking (like [ProvisioningModelli.scarica]): the caller runs it on a background dispatcher. */
    override fun scarica() {
        _stato.value = try {
            val esito = scaricaModelli { id, scaricati, totali ->
                _stato.value = StatoModelli.InDownload(id, scaricati, totali)
            }
            when (esito) {
                is Esito.Ok -> statoAttuale()
                is Esito.Errore -> StatoModelli.Errore(mappaErrore(esito.errore))
            }
        } catch (e: IOException) {
            erroreDiScrittura(e)
        } catch (e: UncheckedIOException) {
            erroreDiScrittura(e)
        } catch (e: InvalidPathException) {
            erroreDiScrittura(e)
        }
    }

    /**
     * AC-556: the S5 licences list gains the three bundled OFL fonts, appended here (the composition
     * root) rather than in `:modelli`'s own catalogue — they are a `:ui` asset, not a downloaded model.
     * AC-S76: an OPTIONAL entry is listed only once [installata] — the required entries are always
     * listed (they already are, by the time this screen is reachable).
     */
    override fun licenze(): List<LicenzaVista> {
        fun installataSenzaEccezioni(id: String): Boolean {
            fun nonInstallataDiRiserva(e: Exception): Boolean {
                log.log(Level.WARNING, "verifica dell'installazione del modello facoltativo '$id' fallita", e)
                return false
            }
            return try {
                installata(id)
            } catch (e: IOException) {
                nonInstallataDiRiserva(e)
            } catch (e: UncheckedIOException) {
                nonInstallataDiRiserva(e)
            } catch (e: InvalidPathException) {
                nonInstallataDiRiserva(e)
            }
        }
        return catalogo.voci.filter { it.obbligatoria || installataSenzaEccezioni(it.id) }
            .map { LicenzaVista(it.id, it.ruolo, it.licenza, it.attribuzione) } + LICENZE_CARATTERI
    }

    private val _statoFacoltativi = MutableStateFlow(statoFacoltativiAttuale())
    override val statoFacoltativi: StateFlow<Map<String, StatoModelloFacoltativo>> = _statoFacoltativi.asStateFlow()

    /**
     * Blocking (like [ProvisioningModelli.scarica]): a caller runs it on a background dispatcher
     * (AC-S73). Mirrors [scarica] one entry at a time — an I/O fault escaping the provisioning ends in
     * [StatoModelloFacoltativo.Errore], never a crash and never stuck `InDownload` (AC-352's own guard,
     * repeated here since [scaricaFacoltativoModello] is `:modelli`'s SAME by-id `scarica`, AC-352
     * finding #53).
     */
    override fun scaricaFacoltativo(id: String) {
        val statoFinale = try {
            val esito = scaricaFacoltativoModello(id) { scaricati, totali ->
                val tick = StatoModelloFacoltativo.InDownload(scaricati, totali)
                _statoFacoltativi.value = _statoFacoltativi.value + (id to tick)
            }
            when (esito) {
                is Esito.Ok -> statoFacoltativoDaDisco(id)
                is Esito.Errore -> StatoModelloFacoltativo.Errore(mappaErrore(esito.errore))
            }
        } catch (e: IOException) {
            erroreScritturaFacoltativo(e)
        } catch (e: UncheckedIOException) {
            erroreScritturaFacoltativo(e)
        } catch (e: InvalidPathException) {
            erroreScritturaFacoltativo(e)
        }
        _statoFacoltativi.value = _statoFacoltativi.value + (id to statoFinale)
    }

    private fun statoFacoltativiAttuale(): Map<String, StatoModelloFacoltativo> =
        catalogo.voci.filterNot { it.obbligatoria }.associate { it.id to statoFacoltativoDaDisco(it.id) }

    /** AC-S17/AC-S76: [installata] re-read fresh — never a stale map value — with the catalogue's declared size. */
    private fun statoFacoltativoDaDisco(id: String): StatoModelloFacoltativo {
        fun nonInstallatoDiRiserva(e: Exception): StatoModelloFacoltativo {
            log.log(Level.WARNING, "verifica dell'installazione del modello facoltativo '$id' fallita", e)
            return StatoModelloFacoltativo.NonInstallato(dimensioneCatalogo(id))
        }
        return try {
            if (installata(id)) {
                StatoModelloFacoltativo.Installato
            } else {
                StatoModelloFacoltativo.NonInstallato(dimensioneCatalogo(id))
            }
        } catch (e: IOException) {
            nonInstallatoDiRiserva(e)
        } catch (e: UncheckedIOException) {
            nonInstallatoDiRiserva(e)
        } catch (e: InvalidPathException) {
            nonInstallatoDiRiserva(e)
        }
    }

    private fun dimensioneCatalogo(id: String): Long = catalogo.voci.find { it.id == id }?.dimensioneByte ?: 0L

    private fun erroreScritturaFacoltativo(e: Exception): StatoModelloFacoltativo {
        log.log(Level.WARNING, "download del modello facoltativo fallito", e)
        val motivo = e.message ?: e.javaClass.simpleName
        return StatoModelloFacoltativo.Errore(ErroreServizioModelli.ScritturaFallita(motivo))
    }

    private fun statoAttuale(): StatoModelli = try {
        if (pronti()) StatoModelli.Pronti else mancantiDi(mancanti())
    } catch (e: IOException) {
        tuttiMancanti(e)
    } catch (e: UncheckedIOException) {
        tuttiMancanti(e)
    } catch (e: InvalidPathException) {
        tuttiMancanti(e)
    }

    private fun tuttiMancanti(e: Exception): StatoModelli {
        log.log(Level.WARNING, "verifica dei modelli installati fallita: li considero mancanti", e)
        return mancantiDi(catalogo.voci.filter { it.obbligatoria })
    }

    private fun erroreDiScrittura(e: Exception): StatoModelli {
        log.log(Level.WARNING, "download dei modelli fallito", e)
        return StatoModelli.Errore(ErroreServizioModelli.ScritturaFallita(e.message ?: e.javaClass.simpleName))
    }

    companion object {
        fun di(catalogo: CatalogoModelli, provisioning: ProvisioningModelli): ServizioModelliProvisioning =
            ServizioModelliProvisioning(
                catalogo,
                provisioning::pronti,
                provisioning::mancanti,
                provisioning::scarica,
                provisioning::installata,
                provisioning::scarica,
            )

        private fun mancantiDi(voci: List<VoceCatalogo>) =
            StatoModelli.Mancanti(voci.size, voci.sumOf { it.dimensioneByte })

        private val log: Logger = Logger.getLogger(ServizioModelliProvisioning::class.java.name)
    }
}

/**
 * AC-329: `:modelli`'s [ErroreModelli] → `:ui`'s [ErroreServizioModelli], variant by variant, no
 * `else` (RC-4). Any other [ErroreDominio] is not one `ProvisioningModelli` ever returns: reported as a
 * failed download rather than crashing the screen.
 */
internal fun mappaErrore(errore: ErroreDominio): ErroreServizioModelli = when (errore) {
    is ErroreModelli -> when (errore) {
        is ErroreModelli.HashNonValido -> ErroreServizioModelli.HashNonValido(errore.modelloId)
        is ErroreModelli.ArchivioNonValido -> ErroreServizioModelli.ArchivioNonValido(errore.modelloId)
        ErroreModelli.ReteAssente -> ErroreServizioModelli.ReteAssente
        is ErroreModelli.ScritturaFallita -> ErroreServizioModelli.ScritturaFallita(errore.motivo)
        is ErroreModelli.DownloadFallito -> ErroreServizioModelli.DownloadFallito(errore.motivo)
        // ADR 0025 §3/tec-modelli-ui-facoltativo: replaces the ScritturaFallita stopgap now that
        // `:ui` has its own dedicated variant + message (AC-S34).
        is ErroreModelli.SpazioInsufficiente -> ErroreServizioModelli.SpazioInsufficiente(errore.richiestiByte)
    }
    else -> ErroreServizioModelli.DownloadFallito(errore.toString())
}
