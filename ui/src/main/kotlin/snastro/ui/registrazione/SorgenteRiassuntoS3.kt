package snastro.ui.registrazione

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow
import snastro.kernel.RegistrazioneId
import snastro.ui.stile.SegnoScheda

/**
 * Boundary `ui-schede-registrazione` (owner: this block; consumer: `avvio-sintesi`, ADR 0021 §3/§10):
 * the OPTIONAL Riassunto tab content, supplied by the composition. `null` in R0/R1/R2 — AC-S119: no
 * Riassunto tab exists at all then. [contenuto] renders the tab body for the given [RegistrazioneId];
 * [segno] feeds the small status mark after the 'Riassunto' label (AC-S122: [SegnoScheda.InAttesa]
 * while the open request is `in_attesa`, [SegnoScheda.InCorso] while `in_corso`, `null` otherwise).
 */
data class SorgenteRiassuntoS3(
    val contenuto: @Composable (RegistrazioneId) -> Unit,
    val segno: (RegistrazioneId) -> Flow<SegnoScheda?>,
)
