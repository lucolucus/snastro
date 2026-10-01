package snastro.trascrizione.applicazione.porte

import snastro.kernel.GeneratoreIdFinto
import snastro.kernel.IncontroId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import java.time.LocalDate
import java.util.Locale

class LettoreRegistrazioneFintaTest : LettoreRegistrazioneContratto() {
    override fun ambiente(): AmbienteLettoreRegistrazione = AmbienteFinto()

    /** Mints ids like the supplier (GeneratoreId) and applies the pinned minting rule of riferimentoAudio. */
    private class AmbienteFinto : AmbienteLettoreRegistrazione {
        private val generatore = GeneratoreIdFinto()
        private val registrazioni = mutableMapOf<RegistrazioneId, RegistrazioneVista>()

        override val progettoId = ProgettoId(generatore.nuovo())

        override val lettore: LettoreRegistrazione = LettoreRegistrazioneFinta(registrazioni)

        override fun semina(seme: SemeRegistrazione): RegistrazioneId {
            val id = RegistrazioneId(generatore.nuovo())
            registrazioni[id] = RegistrazioneVista(
                registrazioneId = id,
                progettoId = progettoId,
                incontroId = IncontroId(generatore.nuovo()),
                titolo = seme.titolo,
                riferimentoAudio = RiferimentoAudio("audio/${id.valore}.${seme.estensione.lowercase(Locale.ROOT)}"),
                dataRegistrazione = seme.dataRegistrazione,
                durataMs = seme.durataMs,
            )
            return id
        }

        override fun incontroDi(id: RegistrazioneId): IncontroId = registrazioni.getValue(id).incontroId

        override fun modificaData(id: RegistrazioneId, data: LocalDate) {
            registrazioni.computeIfPresent(id) { _, vista -> vista.copy(dataRegistrazione = data) }
        }
    }
}
