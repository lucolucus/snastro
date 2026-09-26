package snastro.sintesi.dominio

import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import kotlin.test.Test
import kotlin.test.assertEquals

class IngressoRiassuntoTest {
    private fun segmento(s: Int, v: Int, ms: Long, testo: String) =
        SegmentoIngresso(SegmentoId(s), VoceId(v), ms, testo)

    @Test
    fun `AC-S3 una riga per Segmento nell ordine dato senza tempo poi la legenda delle Voci per n crescente`() {
        val ingresso = IngressoRiassunto.costruisci(
            listOf(
                segmento(2, 3, 5_000, "ciao"),
                segmento(1, 1, 65_000, "budget"),
                segmento(7, 3, 3_725_000, "chiudiamo"),
            ),
            mapOf(VoceId(3) to "Marco", VoceId(9) to "Assente"),
        )

        assertEquals(
            listOf(
                "[s2 V3] ciao",
                "[s1 V1] budget",
                "[s7 V3] chiudiamo",
                "V1 = Voce 1",
                "V3 = Marco",
            ).joinToString("\n"),
            ingresso,
        )
    }
}
