package snastro.sintesi.applicazione.comandi

/**
 * Command (actor: sistema, startup / escape recovery, ADR 0023 §2): every `in_corso` Riassunto left
 * over from a run that never returned (crash, forced quit, an escaped exception) becomes
 * `fallito('interrotto')` — no model run ever survives a restart. Leaves every `in_attesa`, `pronto`
 * and `fallito` Riassunto untouched; running it twice changes nothing more (AC-S89).
 */
public data object RecuperaRiassuntiInterrotti
