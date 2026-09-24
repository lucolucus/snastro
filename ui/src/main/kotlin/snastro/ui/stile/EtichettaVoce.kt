package snastro.ui.stile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import snastro.kernel.VoceId

/**
 * AC-561: dot + name (`heading`, `ink`) when the Voce has a Nome, else "Voce n" (`label`, `inkMuted`).
 * The voice colour never touches the text — only [PallinoVoce].
 */
@Composable
public fun EtichettaVoce(voceId: VoceId, nome: String?, grande: Boolean = false) {
    val colori = LocalSnastroColori.current
    val tipografia = LocalSnastroTipografia.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(SnastroMisure.space1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PallinoVoce(voceId = voceId, conNome = nome != null, grande = grande)
        if (nome != null) {
            Text(text = nome, style = tipografia.heading, color = colori.ink)
        } else {
            Text(text = "Voce ${voceId.numero}", style = tipografia.label, color = colori.inkMuted)
        }
    }
}
