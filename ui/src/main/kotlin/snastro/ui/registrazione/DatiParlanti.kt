package snastro.ui.registrazione

import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.parlanti.applicazione.letture.ParlanteAttivo
import snastro.parlanti.applicazione.letture.PropostaDiUnione
import snastro.parlanti.applicazione.letture.VoceIdentificata
import snastro.trascrizione.applicazione.letture.TrascrittoView
import snastro.trascrizione.applicazione.letture.VoceIncontroRiga
import java.time.Duration
import java.time.Instant

// What [StatoVoci] and its holders share: the panel's read, the Voce labels and references, the pending wait.

/**
 * ADR 0033 §4.1: the Voci are the Incontro's, so their VoceRef carries the Incontro of the trascritto shown.
 * Read off the loaded vista alone, never through a `trascritto()` read: that is a repository read, only for `io`.
 */
internal fun voceRef(vista: TrascrittoView?, voceId: VoceId): VoceRef =
    VoceRef(checkNotNull(vista?.incontroId) { "Voce $voceId senza trascritto caricato" }, voceId)

/** What the Voci panel reads from Parlanti (AC-405), kept until a newer read succeeds. */
internal class DatiParlanti(
    val identificate: Map<VoceId, VoceIdentificata>,
    val attivi: List<ParlanteAttivo>,
    val unioni: List<PropostaDiUnione>,
    /** AC-I77: the Incontro's Voci (empty over a 1-Parte Incontro: not read). */
    val vociIncontro: List<VoceIncontroRiga>,
) {
    /** AC-405: the attributed Nome of [voceId], if any. */
    fun nomeDi(voceId: VoceId): String? = identificate[voceId]?.nome
}

/** AC-405: the Nome if attributed, else trascritto-view's own "Voce n". */
internal fun etichettaDiVoce(vista: TrascrittoView, voceId: VoceId, nome: String?): String =
    nome ?: vista.voci.find { it.voceId == voceId }?.etichetta ?: "Voce ${voceId.numero}"

/** Every Voce of [vista] as a menu option, under its label. */
internal fun opzioniDi(vista: TrascrittoView, nomeDi: (VoceId) -> String?): List<OpzioneVoce> =
    vista.voci.map { OpzioneVoce(it.voceId, etichettaDiVoce(vista, it.voceId, nomeDi(it.voceId))) }

/** AC-412/AC-529: a command pending since [inizio] is in progress, then 'In attesa…' past the threshold. */
internal fun attesaDa(inizio: Instant?, adesso: Instant): AttesaComando? {
    if (inizio == null) return null
    return if (trascorsiMs(inizio, adesso) >= RegistrazionePresenter.SOGLIA_ATTESA_VISIBILE_MS) {
        AttesaComando.IN_ATTESA
    } else {
        AttesaComando.IN_CORSO
    }
}

internal fun trascorsiMs(inizio: Instant, adesso: Instant): Long = Duration.between(inizio, adesso).toMillis()
