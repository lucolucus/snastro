package snastro.progetto.applicazione.letture

import snastro.kernel.IncontroId
import java.time.LocalDate

/**
 * One row of the S2 list (boundary `vista-incontri`, ADR 0033 §5): [titolo] and [data] are the first Parte's (the
 * "· N parti" suffix is the presenter's), [durataMs] the sum of the Parti, [parti] ordered and numbered.
 * A 1-part Incontro carries exactly the data of today's `RegistrazioneDelProgettoVista` (INV-I3).
 */
public data class IncontroDelProgettoVista(
    val incontroId: IncontroId,
    val titolo: String,
    val data: LocalDate,
    val durataMs: Long,
    val numParti: Int,
    val parti: List<ParteVista>,
)
