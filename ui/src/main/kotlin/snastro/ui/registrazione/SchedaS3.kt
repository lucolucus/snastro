package snastro.ui.registrazione

/**
 * S3 · Registrazione centre-column tabs (ux-proposal "Screen S3", AC-S120): [TRASCRIZIONE] (today's
 * body, default) and [RIASSUNTO] (the content slot [SorgenteRiassuntoS3] supplies, always wired by the
 * single composition, ADR 0030 §1). Neither tab renders in a fixture/test that builds
 * [RegistrazioneUiStato.Dati] directly without a `contenutoRiassunto` (AC-S119).
 */
enum class SchedaS3 { TRASCRIZIONE, RIASSUNTO }
