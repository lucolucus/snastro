package snastro.sintesi.dominio

import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import kotlin.test.Test
import kotlin.test.assertEquals

class IngressoRiassuntoTest {
    private fun segmento(s: Int, v: Int, ms: Long, testo: String) =
        SegmentoIngresso(SegmentoId(s), VoceId(v), ms, testo)

    @Test
    fun `AC-S3 ADR 0032 una riga per Segmento senza tempo poi la legenda Voce n per n crescente`() {
        val ingresso = IngressoRiassunto.costruisci(
            listOf(
                segmento(2, 3, 5_000, "ciao"),
                segmento(1, 1, 65_000, "budget"),
                segmento(7, 3, 3_725_000, "chiudiamo"),
            ),
        )

        assertEquals(
            listOf(
                "[s2 V3] ciao",
                "[s1 V1] budget",
                "[s7 V3] chiudiamo",
                "V1 = Voce 1",
                "V3 = Voce 3", // ADR 0032: never a Nome — there is no way to pass one
            ).joinToString("\n"),
            ingresso,
        )
    }
}
