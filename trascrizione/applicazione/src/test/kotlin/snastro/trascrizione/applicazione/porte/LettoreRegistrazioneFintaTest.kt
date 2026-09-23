package snastro.trascrizione.applicazione.porte

import snastro.kernel.GeneratoreIdFinto
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import java.util.Locale

class LettoreRegistrazioneFintaTest : LettoreRegistrazioneContratto() {
    override fun ambiente(): AmbienteLettoreRegistrazione = AmbienteFinto()

    /** Mints ids like the supplier (GeneratoreId) and applies the pinned minting rule of riferimentoAudio. */
    private class AmbienteFinto : AmbienteLettoreRegistrazione {
        private val generatore = GeneratoreIdFinto()
        private val registrazioni = mutableMapOf<RegistrazioneId, RegistrazioneVista>()

        override val progettoId = ProgettoId(generatore.nuovo())

        override val lettore: LettoreRegistrazione
            get() = LettoreRegistrazioneFinta(registrazioni.toMap())

        override fun semina(seme: SemeRegistrazione): RegistrazioneId {
            val id = RegistrazioneId(generatore.nuovo())
            registrazioni[id] = RegistrazioneVista(
                registrazioneId = id,
                progettoId = progettoId,
                titolo = seme.titolo,
                riferimentoAudio = RiferimentoAudio("audio/${id.valore}.${seme.estensione.lowercase(Locale.ROOT)}"),
                dataRegistrazione = seme.dataRegistrazione,
                durataMs = seme.durataMs,
            )
            return id
        }
    }
}
