package snastro.avvio.r3

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import snastro.avvio.ApriEsternoDesktop
import snastro.avvio.cartellaDatiRegistroProgettiReale
import snastro.avvio.costruisciGrafoR0
import snastro.avvio.orologioApp
import snastro.avvio.r1.SceltaMl
import snastro.avvio.r1.SelezioneAdattatoriMl
import snastro.avvio.r1.ServizioModelliProvisioning
import snastro.avvio.r2.GrafoR2
import snastro.avvio.r2.componentiR2
import snastro.kernel.GeneratoreIdUuid
import snastro.kernel.RegistrazioneId
import snastro.modelli.CartellaCacheModelli
import snastro.modelli.ProvisioningModelli
import snastro.sintesi.applicazione.porte.DisponibilitaModelloLinguistico
import snastro.sintesi.applicazione.porte.ModelloLinguistico
import snastro.trascrizione.applicazione.letture.StatoElaborazioneVista
import snastro.ui.modelli.ServizioModelli
import java.nio.file.Path

/** ADR 0026 §8: the catalogue id of the ONE optional model v1 has (the Sintesi LLM). */
internal const val ID_MODELLO_LINGUISTICO: String = "llm-qwen3.5-9b-q4_k_m"

/** ADR 0026 §8: its `dimensioneByte` — what `NonInstallato` shows until the catalogue entry lands. */
internal const val DIMENSIONE_MODELLO_LINGUISTICO_BYTE: Long = 6_169_341_984L

/**
 * The app's model state holder (models are per user, not per project): S5's and the Riassunto tab's
 * [servizio] and the `installata` of the SAME `ProvisioningModelli` behind it (ADR 0025 §4, carry-over 4) —
 * so [disponibilita] never reads a second holder, neither in the app nor under `--smoke`.
 */
internal class ModelliApp(val servizio: ServizioModelli, private val installata: (String) -> Boolean) {
    val disponibilita: DisponibilitaModelloLinguistico = DisponibilitaModelloLinguisticoAvvio(
        ID_MODELLO_LINGUISTICO,
        DIMENSIONE_MODELLO_LINGUISTICO_BYTE,
        installata,
        servizio.statoFacoltativi,
    )
}

/**
 * The REAL model catalogue ([SceltaMl.REALI]) on [cartellaModelli], as ONE holder — the `--smoke` S5 and Riassunto
 * tab over an empty throwaway cache: building it loads and downloads nothing.
 */
internal fun modelliReali(cartellaModelli: Path): ModelliApp {
    val catalogo = SelezioneAdattatoriMl.catalogo(SceltaMl.REALI)
    val provisioning = ProvisioningModelli(catalogo, cartellaModelli)
    return ModelliApp(ServizioModelliProvisioning.di(catalogo, provisioning), provisioning::installata)
}

/**
 * The R3 (Sintesi) graph — the app's: R0's own ([costruisciGrafoR0]) extended with [EstensioneR3] over R2's
 * components ([componentiR2]). The graph's shape is R2's ([GrafoR2]); only the per-project extension grows.
 * [modelli] is `null` in the app (the models of [scelta], R1's own holder); `--smoke` passes [modelliReali] so S5,
 * the tab and [DisponibilitaModelloLinguistico] all read that ONE swapped holder. [modello] is the
 * [ModelloLinguisticoNonDisponibile] placeholder until `modello-linguistico-llama` binds the real adapter.
 */
internal fun costruisciGrafoR3(
    cartellaRegistro: Path = cartellaDatiRegistroProgettiReale(),
    scelta: SceltaMl = SceltaMl.daSistema(),
    cartellaModelli: Path = CartellaCacheModelli.risolvi(),
    modelli: ModelliApp? = null,
    modello: ModelloLinguistico = ModelloLinguisticoNonDisponibile,
): GrafoR2 {
    val io: CoroutineDispatcher = Dispatchers.IO
    val clock = orologioApp()
    val r2 = componentiR2(scelta, cartellaModelli, io, clock)
    val modelliApp = modelli ?: ModelliApp(r2.servizioModelli, r2.provisioning::installata)
    val estensione = EstensioneR3(r2.estensione, clock, GeneratoreIdUuid(), modello, modelliApp.disponibilita)
    return GrafoR2(
        r0 = costruisciGrafoR0(cartellaRegistro, io, clock, estensione),
        servizioModelli = modelliApp.servizio,
        apriEsterno = ApriEsternoDesktop(),
    )
}

/** The open R3 project's first Registrazione whose Elaborazione is COMPLETATA, or `null` (the `--smoke` S3 target). */
internal fun GrafoR2.primaRegistrazioneCompletataR3(): RegistrazioneId? {
    val collaboratori = r0.sessione.collaboratoriCorrenti()
    val r3 = collaboratori?.estensione as? CollaboratoriR3 ?: return null
    val ids = collaboratori.registrazioni().map { it.registrazioneId }
    return r3.r2.r1.statiElaborazione(ids)
        .firstOrNull { it.stato == StatoElaborazioneVista.COMPLETATA }
        ?.registrazioneId
}
