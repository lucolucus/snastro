package snastro.progetto.applicazione.porte

import snastro.kernel.ErroreDiProva
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class PuliziaDerivatiRegistrazioneFintaTest {
    private val e = EliminazioneInSospeso(
        RegistrazioneId("reg-1"),
        "Seduta",
        LocalDate.of(2026, 9, 25),
        RiferimentoAudio("audio/reg-1.m4a"),
    )

    @Test
    fun `AC-615 la finta registra le chiamate e fallisce una sola volta quando richiesto`() {
        val finta = PuliziaDerivatiRegistrazioneFinta().falliscaUnaVoltaPer(RegistrazioneId("reg-1"))

        finta.pulisci(e).erroreAtteso<ErroreDiProva.Fallito>()
        finta.pulisci(e).atteso()

        assertEquals(listOf(e, e), finta.chiamate)
    }
}
