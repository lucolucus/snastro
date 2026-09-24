package snastro.ui.registrazione

import snastro.kernel.ParlanteId
import snastro.parlanti.applicazione.eventi.TipoParlanteVista

/** What a [CartaVoce] shows (AC-405: [Caricamento] per card, never a provisional count nor an empty gallery). */
sealed interface ContenutoCarta {
    /** `identificazione-voci` / `parlanti-attivi` not read yet. */
    data object Caricamento : ContenutoCarta

    /** AC-405: a failed read of a source — the message sits in the card, the transcript stays usable. */
    data class Errore(val messaggio: String) : ContenutoCarta

    /** AC-219: an attributed Voce shows its Nome + TipoParlante and 'cambia', never 'salta'. */
    data class Attribuita(val parlanteId: ParlanteId, val nome: String, val tipo: TipoParlanteVista) : ContenutoCarta

    /** Not attributed yet: the [proposta]; [galleriaVuota] = no attivo Parlante at all (AC-212). */
    data class DaIdentificare(val proposta: StatoProposta, val galleriaVuota: Boolean) : ContenutoCarta
}
