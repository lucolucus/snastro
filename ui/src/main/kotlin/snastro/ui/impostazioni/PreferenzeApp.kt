package snastro.ui.impostazioni

/**
 * The app-wide preferences of Impostazioni › Generali (per user, never inside a project folder). [cartellaProgetti]
 * `null` means "the built-in default" (ADR 0010: `~/Documents/snastro`), which `:avvio` owns.
 */
data class Preferenze(
    val tema: TemaApp = TemaApp.SISTEMA,
    val cartellaProgetti: String? = null,
)

/**
 * Consumer-owned port (as [snastro.ui.progetti.SceltaCartella]): where [Preferenze] are kept. `:avvio` implements it
 * over a small file in the per-user app-data folder. [leggi] never fails — a missing or unreadable store is the
 * defaults; [salva] may throw on an IO fault, which the presenter turns into a plain message.
 */
interface PreferenzeApp {
    fun leggi(): Preferenze

    fun salva(preferenze: Preferenze)
}
