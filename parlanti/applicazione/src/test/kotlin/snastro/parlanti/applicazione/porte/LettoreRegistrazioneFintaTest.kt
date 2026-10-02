package snastro.parlanti.applicazione.porte

import snastro.kernel.GeneratoreIdFinto
import snastro.kernel.IncontroId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import java.time.LocalDate
import java.util.Locale

class LettoreRegistrazioneFintaTest : LettoreRegistrazioneContratto() {
    override fun ambiente(): AmbienteLettoreRegistrazione = AmbienteFinto()

    /**
     * Plays the supplier: mints ids like GeneratoreId, applies the pinned minting rule of riferimentoAudio, and keeps
     * the Finta's map in the supplier's order of the Parti ([INV-I2] on seeds without an OraDiInizio: date, then import
     * order) after every change, since the Finta only follows it.
     */
    private class AmbienteFinto : AmbienteLettoreRegistrazione {
        private val generatore = GeneratoreIdFinto()
        private val registrazioni = linkedMapOf<RegistrazioneId, RegistrazioneVista>()
        private val importate = mutableListOf<RegistrazioneId>()

        override val progettoId = ProgettoId(generatore.nuovo())

        override val lettore: LettoreRegistrazione = LettoreRegistrazioneFinta(registrazioni)

        override fun semina(seme: SemeRegistrazione): RegistrazioneId = importa(IncontroId(generatore.nuovo()), seme)

        override fun aggiungiParte(incontroId: IncontroId, seme: SemeRegistrazione): RegistrazioneId {
            require(registrazioni.values.any { it.incontroId == incontroId })
            return importa(incontroId, seme)
        }

        override fun incontroDi(id: RegistrazioneId): IncontroId = registrazioni.getValue(id).incontroId

        override fun elimina(id: RegistrazioneId) {
            registrazioni.remove(id)
        }

        override fun modificaData(id: RegistrazioneId, data: LocalDate) {
            registrazioni.computeIfPresent(id) { _, vista -> vista.copy(dataRegistrazione = data) }
            riordina()
        }

        private fun importa(incontroId: IncontroId, seme: SemeRegistrazione): RegistrazioneId {
            val id = RegistrazioneId(generatore.nuovo())
            registrazioni[id] = RegistrazioneVista(
                registrazioneId = id,
                incontroId = incontroId,
                progettoId = progettoId,
                titolo = seme.titolo,
                riferimentoAudio = RiferimentoAudio("audio/${id.valore}.${seme.estensione.lowercase(Locale.ROOT)}"),
                dataRegistrazione = seme.dataRegistrazione,
                durataMs = seme.durataMs,
            )
            importate += id
            riordina()
            return id
        }

        private fun riordina() {
            val ordinate = registrazioni.values.sortedWith(
                compareBy<RegistrazioneVista> { it.dataRegistrazione }.thenBy { importate.indexOf(it.registrazioneId) },
            )
            registrazioni.clear()
            ordinate.forEach { registrazioni[it.registrazioneId] = it }
        }
    }
}
