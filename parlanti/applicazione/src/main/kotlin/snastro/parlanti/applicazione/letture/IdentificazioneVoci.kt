package snastro.parlanti.applicazione.letture

import snastro.kernel.ParlanteId
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.parlanti.applicazione.eventi.TipoParlanteVista
import snastro.parlanti.applicazione.porte.AttribuzioneRepository
import snastro.parlanti.applicazione.porte.LettoreVoci
import snastro.parlanti.applicazione.porte.ParlanteRepository
import snastro.parlanti.dominio.TipoParlante

/**
 * Parlanti slice of S3 (`identificazione-voci`, ux-proposal S3 + R1): Voce → Parlante identity of a
 * Trascritto. Read-only: no rule lives here (RC-1) — [LettoreVoci] gives the Voci in play,
 * [AttribuzioneRepository] and [ParlanteRepository] the current identity of each.
 */
public class IdentificazioneVoci(
    private val voci: LettoreVoci,
    private val attribuzioni: AttribuzioneRepository,
    private val parlanti: ParlanteRepository,
) {
    /** AC-169: one entry per Voce of [id]'s Trascritto, in [LettoreVoci] order; empty without one. */
    public fun voci(id: RegistrazioneId): List<VoceIdentificata> =
        (voci.voci(id) ?: emptyList()).map { identifica(it.voceRef) }

    private fun identifica(voceRef: VoceRef): VoceIdentificata {
        val parlante = attribuzioni.trova(voceRef)?.let { parlanti.trova(it.parlanteId) }
        return VoceIdentificata(voceRef.voceId, parlante?.id, parlante?.nome?.valore, parlante?.tipo?.vista())
    }
}

/** One row of [IdentificazioneVoci.voci]: AC-169. Unattributed → only [voceId]. */
public data class VoceIdentificata(
    val voceId: VoceId,
    val parlanteId: ParlanteId? = null,
    val nome: String? = null,
    val tipoParlante: TipoParlanteVista? = null,
)

private fun TipoParlante.vista(): TipoParlanteVista =
    when (this) {
        TipoParlante.RICORRENTE -> TipoParlanteVista.RICORRENTE
        TipoParlante.OCCASIONALE -> TipoParlanteVista.OCCASIONALE
    }
