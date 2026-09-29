package snastro.ui.impostazioni

/**
 * State of the Impostazioni screen (app-wide part). [cartellaProgetti] is the folder new projects go to — the saved
 * one, or the built-in default when none is saved ([cartellaPersonalizzata] `false`). [errore], when set, is a
 * dismissible message for the last failed save; the shown values are then the last ones actually saved.
 */
data class ImpostazioniUiStato(
    val sezione: SezioneImpostazioni = SezioneImpostazioni.GENERALI,
    val tema: TemaApp = TemaApp.SISTEMA,
    val cartellaProgetti: String,
    val cartellaPersonalizzata: Boolean = false,
    val errore: String? = null,
)
