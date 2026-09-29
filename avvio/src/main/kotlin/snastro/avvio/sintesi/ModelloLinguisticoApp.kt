package snastro.avvio.sintesi

import snastro.avvio.trascrizione.SceltaMl
import snastro.avvio.trascrizione.SelezioneAdattatoriMl
import snastro.modelli.ProvisioningModelli
import snastro.modelli.VOCE_CATALOGO_MODELLO_LINGUISTICO
import snastro.sintesi.adattatori.ml.ModelloLinguisticoLlama
import snastro.sintesi.applicazione.porte.ModelloLinguistico
import java.nio.file.Path
import java.util.logging.Logger

private val log: Logger = Logger.getLogger("snastro.avvio.sintesi.ModelloLinguisticoApp")

/**
 * The app's [ModelloLinguistico] for [scelta] (ADR 0027 §7, carry-over D-0011): [SceltaMl.REALI] is the real
 * adapter over :llama-jni, its locations handed as suppliers read at each run — never at composition, so building
 * the graph loads no native and touches no model; [SceltaMl.FINTE] (`--smoke`, the gate) keeps the placeholder.
 */
internal fun modelloLinguistico(scelta: SceltaMl, provisioning: ProvisioningModelli): ModelloLinguistico =
    when (scelta) {
        SceltaMl.FINTE -> ModelloLinguisticoNonDisponibile
        SceltaMl.REALI -> ModelloLinguisticoLlama(
            cartellaNativi = { cartellaNativiLlama() },
            fileModello = { fileModelloLinguistico(provisioning) },
            misure = { log.info("riassunto: $it") },
        )
    }

/**
 * The llama.cpp native directory (ADR 0026 §2 / ADR 0027 §7): `snastro.llm.native.path` if set (`modelliTest`,
 * `benchmarkRiassunto`), else `compose.application.resources.dir` (`:avvio:run` and the packaged app, where
 * `copiaNativiLlama` put them, ADR 0016 §3); `null` when neither is set — the adapter's `ErroreRuntime`.
 */
internal fun cartellaNativiLlama(proprieta: (String) -> String? = System::getProperty): Path? =
    (proprieta("snastro.llm.native.path") ?: proprieta("compose.application.resources.dir"))?.let(Path::of)

/**
 * Where `:modelli` installs the GGUF (`FILE`: `<percorso(id)>/<file name of the url>`, ADR 0008 (c)). The file
 * appears there only once the verified download is moved in (ADR 0025 §3), so its absence is "not installed".
 */
internal fun fileModelloLinguistico(provisioning: ProvisioningModelli): Path =
    SelezioneAdattatoriMl.fileInstallato(provisioning, VOCE_CATALOGO_MODELLO_LINGUISTICO)
