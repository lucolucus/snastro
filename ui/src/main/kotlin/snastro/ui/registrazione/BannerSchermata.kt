package snastro.ui.registrazione

/**
 * AC-S123 (ux-proposal "Banner precedence on S3", design-system rule "al massimo un banner per
 * schermata" — [snastro.ui.stile.BannerSn]'s own AC-566 KDoc): the ONE screen `Banner`
 * [RegistrazioneUiStato.Dati.bannerSchermata] shows, by precedence — [Ritrascrizione] wins over
 * [AudioMancante], which wins over [VociDaIdentificare]. `superato`, a failed Riassunto and the model
 * download are tab-scoped (`scheda-riassunto`'s own inline notices inside the Riassunto tab body) and
 * never reach this type at all.
 */
sealed interface BannerSchermata {
    /** ADR 0018 Amendment (b) §2 (AC-452/454): same wording/shape as before, only now routed through
     * here — [testo]/[pannello] are [RegistrazioneUiStato.Dati.bannerRitrascrizione] /
     * [RegistrazioneUiStato.Dati.bannerRitrascrizionePannello] unchanged. */
    data class Ritrascrizione(val testo: String, val pannello: String?) : BannerSchermata

    /** AC-S123: the audio source is missing (same signal as [RegistrazioneUiStato.Dati.audioDisponibile]). */
    data object AudioMancante : BannerSchermata

    /** AC-S123: at least one Voce of the R2 panel is still [ContenutoCarta.DaIdentificare]. */
    data class VociDaIdentificare(val numero: Int) : BannerSchermata
}
