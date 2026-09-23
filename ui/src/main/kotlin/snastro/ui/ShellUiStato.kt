package snastro.ui

/** State of the app shell (AC-177, AC-181, AC-341) — presenter-owned, rendered by [SchermataShell]. */
sealed interface ShellUiStato {
    /** No Progetto open: only S1 is shown, no left nav (AC-177). */
    data object SenzaProgetto : ShellUiStato

    /** A `crea`/`apri` is in flight (AC-181). */
    data object Caricamento : ShellUiStato

    /** The last `crea`/`apri` failed; still no Progetto open (AC-181). */
    data class ErroreApertura(val messaggio: String) : ShellUiStato

    /**
     * A Progetto is open: the left nav shows only [destinazioniDisponibili] (AC-177, AC-341 — R0/R1
     * omit [DestinazioneShell.PARLANTI]).
     */
    data class ConProgetto(
        val progetto: ProgettoAperto,
        val destinazioniDisponibili: Set<DestinazioneShell>,
        val destinazioneSelezionata: DestinazioneShell,
    ) : ShellUiStato
}
