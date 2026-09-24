package snastro.ui.parlanti

import snastro.kernel.ParlanteId
import snastro.parlanti.applicazione.eventi.TipoParlanteVista
import java.time.LocalDate

/**
 * State of S4 · Parlanti del Progetto (AC-220..226) — presenter-owned, rendered by
 * [SchermataParlanti].
 */
sealed interface ParlantiUiStato {
    /** The read-model is still loading, on first mount (AC-221). */
    data object Caricamento : ParlantiUiStato

    /**
     * The known Parlanti, already grouped by the read-model's `tipoParlante`/`statoParlante`
     * (AC-222): [ricorrenti] and [occasionali] (each active Parlante appears in exactly one, RC-1 —
     * `TipoParlante` is read, never re-decided) and [eliminati] (tombstoned, collapsed — name only,
     * ADR 0009). Every group empty renders the AC-220 empty message, not a separate state. [errore],
     * when set, is a dismissible inline message for the last failed list refresh (H1).
     */
    data class Dati(
        val ricorrenti: List<RigaParlante> = emptyList(),
        val occasionali: List<RigaParlante> = emptyList(),
        val eliminati: List<RigaParlanteEliminato> = emptyList(),
        val errore: String? = null,
    ) : ParlantiUiStato {
        val vuoto: Boolean get() = ricorrenti.isEmpty() && occasionali.isEmpty() && eliminati.isEmpty()
    }

    /**
     * The INITIAL load failed — distinct from [Dati] with every group empty (AC-220, a real empty
     * catalogue): showing the AC-220 empty message here would falsely claim there are no Parlanti.
     * [messaggio] is paired with a retry action (`AzioniParlanti.riprova`). A refresh failing AFTER
     * rows are already known stays in [Dati] (H1): the known rows survive, only [Dati.errore] changes.
     */
    data class Errore(val messaggio: String) : ParlantiUiStato
}

/**
 * One active (`attivo`) Parlante row (AC-175/AC-222..226). [operazioneInCorso] guards a second
 * `rinomina`/`promuovi`/`elimina` on this row while one is in flight (M3); [erroreRiga], when set,
 * is a dismissible inline message for the last failed one (H1). [confermaEliminazione] shows the
 * inline privacy-effect confirmation in place of the row's own controls (AC-225) — never an AWT/OS
 * modal dialog (the render-check captures a single composable tree). [riproduzioneAbilitata] is
 * `false` when the read-model's `estratto` is absent (AC-226: '▶' disabled, no `ImprontaVocale` to
 * derive an excerpt from).
 */
data class RigaParlante(
    val parlanteId: ParlanteId,
    val nome: String,
    val tipoParlante: TipoParlanteVista,
    val numImpronte: Int,
    val numRegistrazioni: Int,
    val ultimaApparizione: LocalDate?,
    val riproduzioneAbilitata: Boolean,
    val operazioneInCorso: Boolean = false,
    val erroreRiga: String? = null,
    val confermaEliminazione: Boolean = false,
)

/** AC-222: an `eliminato` Parlante shown collapsed in the "Eliminati" section — name only. */
data class RigaParlanteEliminato(val parlanteId: ParlanteId, val nome: String)
