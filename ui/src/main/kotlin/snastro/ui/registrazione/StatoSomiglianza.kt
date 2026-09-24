package snastro.ui.registrazione

/** One entry of [AzioniSomiglianza.stato] (ADR 0019 Amendment (b).2). */
sealed interface StatoSomiglianza {
    /** Computing: [fatti] of [totale] Segmenti embedded; [ultimoAvanzamentoMs] = epoch ms of the last tick. */
    data class InCorso(val fatti: Int, val totale: Int, val ultimoAvanzamentoMs: Long) : StatoSomiglianza

    /** The preview of the HELD plan: [gruppi] ordered by (a, da), N = the sum of their `frasi`. */
    data class Anteprima(val gruppi: List<GruppoSpostamenti>, val incerte: Int) : StatoSomiglianza

    /** 'Applica' is running the final transaction (not cancellable). */
    data object Applicazione : StatoSomiglianza

    /** Applied: [spostate] Segmenti moved, [incerte] left where they were. */
    data class Esito(val spostate: Int, val incerte: Int) : StatoSomiglianza

    data class Errore(val errore: ErroreSomiglianzaUi) : StatoSomiglianza
}
