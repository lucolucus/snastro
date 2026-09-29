package snastro.ui.impostazioni

/** One lambda per user action of the Impostazioni screen (app-wide part). */
data class AzioniImpostazioni(
    val seleziona: (SezioneImpostazioni) -> Unit,
    val cambiaTema: (TemaApp) -> Unit,
    val cambiaCartellaProgetti: (percorso: String) -> Unit,
    val ripristinaCartellaProgetti: () -> Unit,
    val chiudiErrore: () -> Unit,
)
