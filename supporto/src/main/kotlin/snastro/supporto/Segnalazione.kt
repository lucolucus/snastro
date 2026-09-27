package snastro.supporto

/**
 * The only logging channel of `:supporto` (ADR 0028 §2): it never touches JUL. `:avvio` injects the
 * implementation that writes to the configured log.
 */
public fun interface Segnalazione {
    /** Reports [messaggio]; [causa] is null when there is no throwable (e.g. a job that returned false). */
    public fun segnala(messaggio: String, causa: Throwable?)
}
