package snastro.avvio.r1

import snastro.avvio.RisultatoTentativo
import snastro.kernel.ElaborazioneId
import snastro.kernel.ErroreDiProva
import snastro.kernel.Esito
import snastro.trascrizione.applicazione.comandi.RisultatoAvanzamento
import kotlin.test.Test
import kotlin.test.assertEquals

/** Carry-over 5: RisultatoAvanzamento (Trascrizione) → RisultatoTentativo (CodaElaborazioni), at the wiring site. */
class FonteAvanzamentoTrascrizioneTest {
    @Test
    fun `ogni RisultatoAvanzamento diventa il RisultatoTentativo omonimo con l id primitivo`() {
        val tabella = listOf(
            RisultatoAvanzamento.NessunElemento to RisultatoTentativo.Nessuno,
            RisultatoAvanzamento.Avviata(ElaborazioneId("e-1")) to RisultatoTentativo.Avviata("e-1"),
            RisultatoAvanzamento.AvvioRifiutato(ElaborazioneId("e-2"), ErroreDiProva.Fallito("no"))
                to RisultatoTentativo.Rifiutata("e-2"),
        )

        tabella.forEach { (avanzamento, atteso) ->
            assertEquals(atteso, tentativoDi(Esito.Ok(avanzamento), ultimaTentata = "ignorato"))
        }
    }

    @Test
    fun `un Esito Errore conta come rifiuto della testa tentata, o nessuno se non ne era stata scelta una`() {
        val errore = Esito.Errore(ErroreDiProva.Fallito("guasto"))

        assertEquals(RisultatoTentativo.Rifiutata("e-3"), tentativoDi(errore, ultimaTentata = "e-3"))
        assertEquals(RisultatoTentativo.Nessuno, tentativoDi(errore, ultimaTentata = null))
    }
}
