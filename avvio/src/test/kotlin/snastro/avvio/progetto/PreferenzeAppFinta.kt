package snastro.avvio.progetto

import snastro.ui.impostazioni.Preferenze
import snastro.ui.impostazioni.PreferenzeApp

/** In-memory [PreferenzeApp]: starts from [iniziali], keeps what is saved. */
class PreferenzeAppFinta(iniziali: Preferenze = Preferenze()) : PreferenzeApp {
    var salvate: Preferenze = iniziali
        private set

    override fun leggi(): Preferenze = salvate

    override fun salva(preferenze: Preferenze) {
        salvate = preferenze
    }
}
