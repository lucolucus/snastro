package snastro.ui.lettore

/** State of the shared audio bar (AC-187, AC-189, AC-191) — presenter-owned, rendered by [BarraLettore]. */
sealed interface LettoreUiStato {
    /** Nothing has been requested yet: the play control is shown, idle. */
    data object Inattivo : LettoreUiStato

    /** [messaggio]: the source is not available (AC-189) — the controls are disabled. */
    data class NonDisponibile(val messaggio: String) : LettoreUiStato

    /**
     * [messaggio]: a TRANSIENT port fault (an exception from `disponibile`/`riproduci*`/`pausa`, L471d) —
     * unlike [NonDisponibile] this says nothing about the source itself, so retrying may well succeed.
     * The play control therefore stays ENABLED here (only [NonDisponibile]/[Caricamento] disable it).
     */
    data class Errore(val messaggio: String) : LettoreUiStato

    /**
     * A `riproduci`/`riproduciEstratto` was requested and [LettoreAudio.stato] has not caught up to it
     * yet — "WAV in preparazione" (AC-191). Never left stuck: it always resolves to [Pronto] once the
     * port reports the requested source, or to [NonDisponibile] if a later request finds it unavailable.
     */
    data object Caricamento : LettoreUiStato

    /** The player is ready: [posizioneMs] is the current position, [inRiproduzione] play vs. pause (AC-187). */
    data class Pronto(val posizioneMs: Long, val inRiproduzione: Boolean) : LettoreUiStato
}
