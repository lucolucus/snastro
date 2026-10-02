package snastro.ui.riassunto

import snastro.sintesi.applicazione.letture.VoceVista
import kotlin.test.Test
import kotlin.test.assertEquals

class TestoRiassuntoDaCopiareTest {
    @Test
    fun `sommario e sezioni non vuote, con responsabili e parlanti per nome`() {
        val contenuto = ContenutoUi(
            superato = false,
            sommario = "  La riunione ha fissato il budget.  ",
            decisioni = listOf(ElementoUi("Budget a 10k", emptyList())),
            azioni = listOf(
                AzioneUi("Mandare il verbale", emptyList(), VoceVista(1, "Voce 1", "Anna", presente = true)),
                AzioneUi("Prenotare la sala", emptyList(), null),
            ),
            questioniAperte = emptyList(),
            puntiChiave = listOf(
                PuntoChiaveUi("Serve più tempo", emptyList(), VoceVista(2, "Voce 2", null, presente = true)),
            ),
            omessiTesto = "2 punti omessi",
            metadatiTesto = "Lunghezza massima: 2000 parole",
        )

        assertEquals(
            """
            La riunione ha fissato il budget.

            Decisioni
            - Budget a 10k

            Azioni
            - Mandare il verbale → Anna
            - Prenotare la sala

            Punti chiave
            - Voce 2: Serve più tempo
            """.trimIndent(),
            testoRiassuntoDaCopiare(contenuto),
        )
    }

    @Test
    fun `senza sommario parte dalla prima sezione`() {
        val contenuto = ContenutoUi(
            superato = true,
            sommario = null,
            decisioni = emptyList(),
            azioni = emptyList(),
            questioniAperte = listOf(ElementoUi("Chi paga?", emptyList())),
            puntiChiave = emptyList(),
            omessiTesto = null,
            metadatiTesto = "",
        )

        assertEquals("Questioni aperte\n- Chi paga?", testoRiassuntoDaCopiare(contenuto))
    }
}
