package snastro.progetto.dominio

import snastro.kernel.IncontroId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class OrdineDelleRegistrazioniTest {
    private val giorno = LocalDate.of(2026, 9, 12)

    private fun una(id: String, data: LocalDate = giorno, aggiuntaAlle: Long = 0) =
        Registrazione.aggiungi(
            id = RegistrazioneId(id),
            progettoId = ProgettoId("p"),
            incontroId = IncontroId("i-$id"),
            titolo = id,
            riferimentoAudio = RiferimentoAudio("audio/$id.m4a"),
            durataMs = 1L,
            dataRegistrazione = data,
            aggiuntaAlle = Instant.ofEpochMilli(aggiuntaAlle),
        ).aggregato

    private fun ordine(vararg registrazioni: Registrazione): List<String> =
        OrdineDelleRegistrazioni.ordina(registrazioni.toList()).map { it.id.valore }

    private data class Caso(val nome: String, val input: List<Registrazione>, val atteso: List<String>)

    @Test
    fun `AC-161 lista S2 data desc, poi aggiuntaAlle desc, poi id asc`() {
        val casi = listOf(
            Caso("vuota", emptyList(), emptyList()),
            Caso(
                "data piu recente prima, anche se aggiunta prima",
                listOf(una("vecchia", giorno, aggiuntaAlle = 9), una("nuova", giorno.plusDays(1), aggiuntaAlle = 1)),
                listOf("nuova", "vecchia"),
            ),
            Caso(
                "stessa data, l'ultima aggiunta prima",
                listOf(una("prima", aggiuntaAlle = 1), una("seconda", aggiuntaAlle = 2)),
                listOf("seconda", "prima"),
            ),
            Caso(
                "stessa data e stessa aggiunta, id crescente",
                listOf(una("b"), una("a"), una("c")),
                listOf("a", "b", "c"),
            ),
            Caso(
                "tutti i criteri insieme",
                listOf(
                    una("x", giorno, aggiuntaAlle = 5),
                    una("y", giorno.plusDays(2), aggiuntaAlle = 0),
                    una("z", giorno, aggiuntaAlle = 5),
                    una("w", giorno, aggiuntaAlle = 7),
                ),
                listOf("y", "w", "x", "z"),
            ),
        )

        casi.forEach { caso ->
            assertEquals(caso.atteso, ordine(*caso.input.toTypedArray()), caso.nome)
            assertEquals(caso.atteso, ordine(*caso.input.reversed().toTypedArray()), "${caso.nome} (invertito)")
        }
    }
}
