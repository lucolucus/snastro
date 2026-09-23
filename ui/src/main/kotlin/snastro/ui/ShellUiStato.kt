package snastro.ui

/** State of the app shell (AC-177, AC-181, AC-341) — presenter-owned, rendered by [SchermataShell]. */
sealed interface ShellUiStato {
    /**
     * No Progetto open: only S1 is shown, no left nav (AC-177). [erroreApertura], when set, is a
     * dismissible banner over S1 for the last failed `crea`/`apri` (H1: an error never replaces the
     * underlying state — [AzioniShell.chiudiErrore] clears it).
     */
    data class SenzaProgetto(val erroreApertura: String? = null) : ShellUiStato

    /** A `crea`/`apri` is in flight (AC-181). */
    data object Caricamento : ShellUiStato

    /**
     * A Progetto is open: the left nav shows only [destinazioniDisponibili] (AC-177, AC-341 — R0/R1
     * omit [DestinazioneShell.PARLANTI]). [erroreApertura], when set, is a dismissible banner over the
     * nav for the last failed `crea`/`apri` while this Progetto stayed open (H1).
     */
    data class ConProgetto(
        val progetto: ProgettoAperto,
        val destinazioniDisponibili: Set<DestinazioneShell>,
        val destinazioneSelezionata: DestinazioneShell,
        val erroreApertura: String? = null,
    ) : ShellUiStato
}
