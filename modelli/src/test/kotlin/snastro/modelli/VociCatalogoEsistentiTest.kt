package snastro.modelli

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * AC-S26: every `:modelli` catalogue entry that existed before ADR 0025's rework defaults to
 * [VoceCatalogo.obbligatoria] = true — onboarding, [ProvisioningModelli.pronti] and
 * [ProvisioningModelli.mancanti] keep ranging over exactly the same entries as before.
 */
class VociCatalogoEsistentiTest {
    @Test
    fun `AC-S26 ogni voce di catalogo esistente ha obbligatoria vero`() {
        val voci = CatalogoDiarizzazione.voci +
            listOf(VOCE_CATALOGO_ASR_PARAKEET_TDT_0_6B_V3_INT8, VOCE_CATALOGO_VAD_SILERO)

        voci.forEach { voce -> assertTrue(voce.obbligatoria, "atteso obbligatoria=true per '${voce.id}'") }
    }
}
