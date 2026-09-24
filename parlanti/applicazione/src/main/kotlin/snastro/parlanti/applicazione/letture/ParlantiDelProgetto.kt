package snastro.parlanti.applicazione.letture

import snastro.kernel.EstrattoRef
import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId
import snastro.parlanti.applicazione.eventi.TipoParlanteVista
import snastro.parlanti.applicazione.porte.AttribuzioneRepository
import snastro.parlanti.applicazione.porte.LettoreRegistrazione
import snastro.parlanti.applicazione.porte.ParlanteRepository
import snastro.parlanti.dominio.Parlante
import snastro.parlanti.dominio.TipoParlante
import java.time.LocalDate

/**
 * Read-model `ParlantiDelProgetto` (S4 Galleria, AC-175/AC-176): every Parlante of a Progetto,
 * `eliminato` tombstones included (S4 shows them too, ADR 0009). RC-1: no rule is decided here —
 * [Parlante] owns `attivo`/`eliminato`/its prints, [AttribuzioneRepository] owns which Registrazioni
 * a Parlante appears in (an `eliminato`'s past Attribuzioni are never removed, so `numRegistrazioni`
 * survives Eliminazione even though its prints — and so `numImpronte`/`estratto` — do not, AC-176).
 */
public class ParlantiDelProgetto(
    private val parlanti: ParlanteRepository,
    private val attribuzioni: AttribuzioneRepository,
    private val registrazioni: LettoreRegistrazione,
    private val estrattoAudio: EstrattoAudio,
) {
    /** AC-175: one row per Parlante of [progettoId], `eliminati` included. */
    public fun parlanti(progettoId: ProgettoId): List<ParlanteDelProgetto> =
        parlanti.delProgetto(progettoId).map { riga(it) }

    private fun riga(p: Parlante): ParlanteDelProgetto {
        // AC-175/AC-176: distinct registrazioneId (una Voce unita/proposta di unione puo dare piu
        // Attribuzioni nella STESSA Registrazione, INV-22) — mai toccate da EliminaParlante.
        val registrazioniIds = attribuzioni.diParlante(p.id).map { it.voceRef.registrazioneId }.distinct()
        val ultimaApparizione = registrazioniIds
            .mapNotNull { registrazioni.registrazione(it)?.dataRegistrazione }
            .maxOrNull()
        // AC-176: un eliminato non ha piu impronte (ADR 0009), quindi ne' numImpronte ne' un estratto.
        val estratto = p.impronte.firstOrNull()?.let { estrattoAudio.estratto(it.voceRef) }
        return ParlanteDelProgetto(
            parlanteId = p.id,
            nome = p.nome.valore,
            tipoParlante = p.tipo.vista(),
            statoParlante = p.statoVista(),
            numImpronte = p.impronte.size,
            numRegistrazioni = registrazioniIds.size,
            ultimaApparizione = ultimaApparizione,
            estratto = estratto,
        )
    }
}

/** `parlanti-del-progetto` view_shape: one Parlante of the S4 Galleria (AC-175). */
public data class ParlanteDelProgetto(
    val parlanteId: ParlanteId,
    val nome: String,
    val tipoParlante: TipoParlanteVista,
    val statoParlante: StatoParlanteVista,
    val numImpronte: Int,
    val numRegistrazioni: Int,
    val ultimaApparizione: LocalDate?,
    val estratto: EstrattoRef?,
)

/** View of `StatoParlante` for S4 — never the raw domain enum outside `dominio`/persistenza (S14 gate). */
public enum class StatoParlanteVista { ATTIVO, ELIMINATO }

private fun TipoParlante.vista(): TipoParlanteVista =
    when (this) {
        TipoParlante.RICORRENTE -> TipoParlanteVista.RICORRENTE
        TipoParlante.OCCASIONALE -> TipoParlanteVista.OCCASIONALE
    }

/** Uses the named predicate [Parlante.attivo], never the raw `StatoParlante` (S14 gate, ADR 0007). */
private fun Parlante.statoVista(): StatoParlanteVista =
    if (attivo) StatoParlanteVista.ATTIVO else StatoParlanteVista.ELIMINATO
