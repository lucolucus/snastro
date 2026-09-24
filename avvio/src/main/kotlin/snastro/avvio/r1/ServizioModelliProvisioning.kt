package snastro.avvio.r1

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
 * [StatoModelli.Mancanti] over the whole catalogue (the app starts anyway).
 *
 * The provisioning is reached through three functions (not the final class itself) so a test can make
 * each of them throw; [di] binds the real [ProvisioningModelli].
 */
internal class ServizioModelliProvisioning(
    private val catalogo: CatalogoModelli,
    private val pronti: () -> Boolean,
    private val mancanti: () -> List<VoceCatalogo>,
    private val scaricaModelli: ((String, Long, Long) -> Unit) -> Esito<Unit>,
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

    // AC-556: the S5 licences list gains the three bundled OFL fonts, appended here (the composition
    // root) rather than in `:modelli`'s own catalogue — they are a `:ui` asset, not a downloaded model.
    override fun licenze(): List<LicenzaVista> =
        catalogo.voci.map { LicenzaVista(it.id, it.ruolo, it.licenza, it.attribuzione) } + LICENZE_CARATTERI

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
        return mancantiDi(catalogo.voci)
    }

    private fun erroreDiScrittura(e: Exception): StatoModelli {
        log.log(Level.WARNING, "download dei modelli fallito", e)
        return StatoModelli.Errore(ErroreServizioModelli.ScritturaFallita(e.message ?: e.javaClass.simpleName))
    }

    companion object {
        fun di(catalogo: CatalogoModelli, provisioning: ProvisioningModelli): ServizioModelliProvisioning =
            ServizioModelliProvisioning(catalogo, provisioning::pronti, provisioning::mancanti, provisioning::scarica)

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
    }
    else -> ErroreServizioModelli.DownloadFallito(errore.toString())
}
