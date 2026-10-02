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
        private val ordine = mutableMapOf<IncontroId, List<RegistrazioneId>>()

        override val progettoId = ProgettoId(generatore.nuovo())

        override val lettore: LettoreRegistrazione = LettoreRegistrazioneFinta(registrazioni, ordine)

        override fun semina(seme: SemeRegistrazione): RegistrazioneId = semina(seme, IncontroId(generatore.nuovo()))

        /** The supplier's order is not the seeding order: each new Parte goes first (a reader must not re-sort). */
        override fun seminaIncontro(semi: List<SemeRegistrazione>): IncontroId {
            val incontro = IncontroId(generatore.nuovo())
            semi.forEach { ordine[incontro] = listOf(semina(it, incontro)) + ordine[incontro].orEmpty() }
            return incontro
        }

        override fun ordineDelleParti(incontroId: IncontroId): List<RegistrazioneId> = ordine.getValue(incontroId)

        private fun semina(seme: SemeRegistrazione, incontro: IncontroId): RegistrazioneId {
            val id = RegistrazioneId(generatore.nuovo())
            registrazioni[id] = RegistrazioneVista(
                registrazioneId = id,
                progettoId = progettoId,
                incontroId = incontro,
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
