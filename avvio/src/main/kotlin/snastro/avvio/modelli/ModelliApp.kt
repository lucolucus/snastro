package snastro.avvio.modelli

import snastro.avvio.sintesi.DisponibilitaModelloLinguisticoAvvio
import snastro.modelli.CatalogoModelli
import snastro.modelli.ProvisioningModelli
import snastro.modelli.VOCE_CATALOGO_MODELLO_LINGUISTICO
import snastro.sintesi.applicazione.porte.DisponibilitaModelloLinguistico
import snastro.ui.modelli.StatoModelli
import java.nio.file.Path

/** ADR 0026 §8: the catalogue id of the ONE optional model v1 has (the Sintesi LLM) — from the catalogue itself. */
internal val ID_MODELLO_LINGUISTICO: String = VOCE_CATALOGO_MODELLO_LINGUISTICO.id

/** ADR 0026 §8: its `dimensioneByte` — what `NonInstallato` shows. */
internal val DIMENSIONE_MODELLO_LINGUISTICO_BYTE: Long = VOCE_CATALOGO_MODELLO_LINGUISTICO.dimensioneByte

/**
 * The app's models (per user, not per project), over ONE [provisioning] of [catalogo] on [cartellaModelli]
 * (ADR 0008 (c), ADR 0025 §4, AC-C75): S5's and the Riassunto tab's [servizio], Sintesi's [disponibilita] and the ML
 * adapters all read that SAME instance — in the app and under `--smoke` (which only swaps [catalogo] for the real
 * one on an empty throwaway cache: building it loads and downloads nothing).
 */
internal class ModelliApp(catalogo: CatalogoModelli, cartellaModelli: Path) {
    val provisioning: ProvisioningModelli = ProvisioningModelli(catalogo, cartellaModelli)

    val servizio: ServizioModelliProvisioning = ServizioModelliProvisioning.di(catalogo, provisioning)

    val disponibilita: DisponibilitaModelloLinguistico = DisponibilitaModelloLinguisticoAvvio(
        ID_MODELLO_LINGUISTICO,
        DIMENSIONE_MODELLO_LINGUISTICO_BYTE,
        provisioning::installata,
        servizio.statoFacoltativi,
    )

    /** AC-235: the Elaborazione queue holds while S5 is not [StatoModelli.Pronti] (at once for an empty catalogue). */
    fun pronti(): Boolean = servizio.stato.value == StatoModelli.Pronti
}
