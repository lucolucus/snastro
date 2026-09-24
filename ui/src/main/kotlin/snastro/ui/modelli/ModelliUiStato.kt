package snastro.ui.modelli

/** State of S5 · Modelli (AC-227..232) — presenter-owned, rendered by [SchermataModelli]. */
sealed interface ModelliUiStato {
    /** AC-227: [numero] catalogue entries are missing, [totaleByte] bytes to download in total. */
    data class Mancanti(val numero: Int, val totaleByte: Long) : ModelliUiStato

    /** AC-228: [modelloId] is downloading, [scaricatiByte] of [totaliByte]. */
    data class InDownload(val modelloId: String, val scaricatiByte: Long, val totaliByte: Long) : ModelliUiStato

    /** AC-229/230: the download failed — nothing was installed; [messaggio] pairs with 'Riprova'. */
    data class Errore(val messaggio: String) : ModelliUiStato

    /** AC-231/232: every model is installed — [licenze] lists each one; the screen never blocks the app. */
    data class Pronti(val licenze: List<LicenzaVista>) : ModelliUiStato
}
