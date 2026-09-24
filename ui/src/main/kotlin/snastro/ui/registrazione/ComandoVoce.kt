package snastro.ui.registrazione

import snastro.kernel.ParlanteId
import snastro.kernel.VoceRef
import snastro.parlanti.applicazione.eventi.TipoParlanteVista

/**
 * A card command of S3's Voci panel, in Published Language only (CR-1: `:ui` never sees the Parlanti
 * domain `TipoParlante`) — `avvio-parlanti` maps it onto `ConfermaAttribuzione` / `SaltaVoce`.
 */
sealed interface ComandoVoce {
    val voceRef: VoceRef

    /** 'Conferma' / 'altri ▾' / 'cambia': ConfermaAttribuzione onto an existing Parlante. */
    data class Conferma(override val voceRef: VoceRef, val parlanteId: ParlanteId) : ComandoVoce

    /** 'nuovo…': ConfermaAttribuzione onto a new Parlante named [nome] ([tipo] ricorrente by default, Q-7). */
    data class Nuovo(
        override val voceRef: VoceRef,
        val nome: String,
        val tipo: TipoParlanteVista = TipoParlanteVista.RICORRENTE,
    ) : ComandoVoce

    /** 'salta': SaltaVoce ("Ospite del …", INV-19). */
    data class Salta(override val voceRef: VoceRef) : ComandoVoce
}
