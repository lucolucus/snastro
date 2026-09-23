package snastro.parlanti.applicazione.porte

import snastro.kernel.Esito
import snastro.kernel.ParlanteId
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.erroreAtteso
import snastro.parlanti.dominio.Attribuzione
import snastro.parlanti.dominio.ErroreParlanti
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AttribuzioneRepositoryFintaTest : AttribuzioneRepositoryContratto() {
    override fun repository(): AttribuzioneRepository = AttribuzioneRepositoryFinta()

    @Test
    fun `AC-38 la Finta segue il rollback di UnitaDiLavoroFinta`() {
        val repo = AttribuzioneRepositoryFinta()
        val uow = UnitaDiLavoroFinta(repo)
        val voce1 = VoceRef(REGISTRAZIONE_1, VoceId(1))
        val voce2 = VoceRef(REGISTRAZIONE_1, VoceId(2))
        uow.inTransazione {
            repo.salva(Attribuzione.conferma(voce1, PROGETTO, ParlanteId("marco")).aggregato)
            Esito.Ok(Unit)
        }

        uow.inTransazione<Unit> {
            repo.rimuovi(voce1)
            repo.salva(Attribuzione.conferma(voce2, PROGETTO, ParlanteId("anna")).aggregato)
            Esito.Errore(ErroreParlanti.NomeVuoto)
        }.erroreAtteso<ErroreParlanti.NomeVuoto>()

        assertEquals(listOf(voce1), repo.diRegistrazione(REGISTRAZIONE_1).map { it.voceRef })
        assertNull(repo.trova(voce2))
    }
}
