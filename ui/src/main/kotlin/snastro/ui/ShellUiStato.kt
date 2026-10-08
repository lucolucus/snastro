package snastro.ui

import snastro.ui.progetti.ConfermaEliminazione

/** State of the app shell (AC-177, AC-181, AC-341) — presenter-owned, rendered by [SchermataShell]. */
sealed interface ShellUiStato {
    /**
     * No Progetto open: only S1 is shown, no left nav (AC-177). [avviso], when set, is a dismissible banner over S1:
     * the last failed `crea`/`apri`, or what the open project's delete left on disk or why it did not happen (R0,
     * EX-4, EX-10) — H1: it never replaces the underlying state, [AzioniShell.chiudiErrore] clears it.
     */
    data class SenzaProgetto(val avviso: String? = null) : ShellUiStato

    /** A `crea`/`apri` is in flight (AC-181). */
    data object Caricamento : ShellUiStato

    /**
     * A Progetto is open: the left nav shows only [destinazioniDisponibili] (AC-177, AC-341 — the single
     * composition always includes [DestinazioneShell.PARLANTI]). [avviso], when set, is a
     * dismissible banner over the nav for the last failed `crea`/`apri` while this Progetto stayed open (H1).
     * [conferma] (R0, EX-4/EX-5), when set, is the open delete confirmation of this Progetto, asked from the header's
     * menu; [eliminazioneInCorso] is true while its delete runs.
     */
    data class ConProgetto(
        val progetto: ProgettoAperto,
        val destinazioniDisponibili: Set<DestinazioneShell>,
        val destinazioneSelezionata: DestinazioneShell,
        val avviso: String? = null,
        val conferma: ConfermaEliminazione? = null,
        val eliminazioneInCorso: Boolean = false,
    ) : ShellUiStato
}
