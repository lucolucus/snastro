package snastro.ui.registrazione

/**
 * AC-S121: the Trascrizione/Riassunto choice is kept PER WINDOW while the user navigates between
 * recordings. A [RegistrazionePresenter] is torn down and rebuilt on every navigation (a new
 * `registrazioneId`), so the choice cannot live on the presenter itself — the composition builds ONE
 * instance per window (`Main.kt` opens exactly one) and passes the SAME instance to every
 * [RegistrazionePresenter] it constructs for it. A plain mutable holder, not a cross-context port:
 * `:ui`-internal wiring, the same shape the composition already uses for a shared `CoroutineScope`.
 */
class SelezioneSchedaS3 {
    var scheda: SchedaS3 = SchedaS3.TRASCRIZIONE
        private set

    fun seleziona(scheda: SchedaS3) {
        this.scheda = scheda
    }
}
