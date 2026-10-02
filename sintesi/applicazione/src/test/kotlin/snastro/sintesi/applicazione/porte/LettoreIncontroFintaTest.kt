package snastro.sintesi.applicazione.porte

import snastro.kernel.GeneratoreIdFinto
import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.kernel.unIncontroDi
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** D1: [LettoreIncontroFinta] passes [LettoreIncontroContratto]; ids minted like the supplier (GeneratoreId). */
class LettoreIncontroFintaTest : LettoreIncontroContratto() {
    override fun ambiente(): AmbienteLettoreIncontro = AmbienteFinto()

    @Test
    fun `ogniIncontroConUnaParte risponde solo agli Incontri della convenzione, null altrimenti come il fornitore`() {
        val lettore = ogniIncontroConUnaParte()
        val r = RegistrazioneId("reg-1")

        assertEquals(listOf(ParteSintesi(r, 1)), lettore.parti(unIncontroDi(r)))
        assertNull(lettore.parti(IncontroId("incontro-ignoto")))
    }

    /**
     * Plays the supplier: keeps each Incontro's Parti in INV-I2 order (date, OraDiInizio with an empty one last,
     * import order) after every change, since the Finta only follows the order it is given.
     */
    private class AmbienteFinto : AmbienteLettoreIncontro {
        private val generatore = GeneratoreIdFinto()
        private val seme = linkedMapOf<RegistrazioneId, Seme>()
        private val parti = mutableMapOf<IncontroId, List<RegistrazioneId>>()
        private var importate = 0

        override val lettore: LettoreIncontro = LettoreIncontroFinta(parti)

        override val piuPartiPerIncontro: Boolean = true

        override fun importa(data: LocalDate, ora: LocalTime?): RegistrazioneId =
            aggiungi(IncontroId(generatore.nuovo()), data, ora)

        override fun aggiungiParte(incontroId: IncontroId, data: LocalDate, ora: LocalTime?): RegistrazioneId {
            require(incontroId in parti) { "Incontro sconosciuto o cessato: $incontroId" }
            return aggiungi(incontroId, data, ora)
        }

        override fun incontroDi(registrazioneId: RegistrazioneId): IncontroId = seme.getValue(registrazioneId).incontro

        override fun modificaData(registrazioneId: RegistrazioneId, data: LocalDate) =
            cambia(registrazioneId) { it.copy(data = data) }

        override fun modificaOraDiInizio(registrazioneId: RegistrazioneId, ora: LocalTime?) =
            cambia(registrazioneId) { it.copy(ora = ora) }

        override fun elimina(registrazioneId: RegistrazioneId) {
            val incontro = seme.getValue(registrazioneId).incontro
            seme.remove(registrazioneId)
            riordina(incontro)
        }

        private fun aggiungi(incontro: IncontroId, data: LocalDate, ora: LocalTime?): RegistrazioneId {
            val r = RegistrazioneId(generatore.nuovo())
            seme[r] = Seme(incontro, data, ora, importate++)
            riordina(incontro)
            return r
        }

        private fun cambia(r: RegistrazioneId, modifica: (Seme) -> Seme) {
            seme[r] = modifica(seme.getValue(r))
            riordina(seme.getValue(r).incontro)
        }

        private fun riordina(incontro: IncontroId) {
            val sue = seme.filterValues { it.incontro == incontro }.entries
                .sortedWith(compareBy({ it.value.data }, { it.value.ora ?: FINE_GIORNATA }, { it.value.importata }))
                .map { it.key }
            if (sue.isEmpty()) parti.remove(incontro) else parti[incontro] = sue
        }
    }

    private data class Seme(val incontro: IncontroId, val data: LocalDate, val ora: LocalTime?, val importata: Int)

    private companion object {
        /** Sorts an empty OraDiInizio after every time of day (OraDiInizio < 24:00). */
        val FINE_GIORNATA: LocalTime = LocalTime.MAX
    }
}
