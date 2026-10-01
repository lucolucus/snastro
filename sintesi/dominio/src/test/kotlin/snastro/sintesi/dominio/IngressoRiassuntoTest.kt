package snastro.sintesi.dominio

import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IngressoRiassuntoTest {
    private val a = RegistrazioneId("parte-a")
    private val b = RegistrazioneId("parte-b")

    private fun segmento(r: RegistrazioneId, s: Int, v: Int, ms: Long, testo: String) =
        SegmentoIngresso(r, SegmentoId(s), VoceId(v), ms, testo)

    @Test
    fun `INV-I19 Parti A (5, 7) e B (2) danno s1 s2 s3 in ordine, nessun separatore, legenda Voce n e etichette`() {
        val ingresso = IngressoRiassunto.costruisci(
            listOf(
                listOf(segmento(a, 5, 1, 5_000, "ciao"), segmento(a, 7, 3, 65_000, "budget")),
                listOf(segmento(b, 2, 2, 1_000, "chiudiamo")),
            ),
        )

        assertEquals(
            listOf(
                "[s1 V1] ciao",
                "[s2 V3] budget",
                "[s3 V2] chiudiamo",
                "V1 = Voce 1",
                "V2 = Voce 2",
                "V3 = Voce 3", // ADR 0032: never a Nome — there is no way to pass one
            ).joinToString("\n"),
            ingresso.testo,
        )
        assertEquals(listOf(ref(a, 5), ref(a, 7), ref(b, 2)), ingresso.etichette)
    }

    @Test
    fun `INV-I19 una Parte sola numera le etichette da 1 nell ordine dato senza tempo`() {
        val ingresso = IngressoRiassunto.costruisci(
            listOf(listOf(segmento(a, 2, 3, 5_000, "ciao"), segmento(a, 1, 1, 3_725_000, "chiudiamo"))),
        )

        assertEquals("[s1 V3] ciao\n[s2 V1] chiudiamo\nV1 = Voce 1\nV3 = Voce 3", ingresso.testo)
        assertEquals(listOf(ref(a, 2), ref(a, 1)), ingresso.etichette)
    }

    @Test
    fun `INV-I19 ogni etichetta rimanda a un solo Segmento, un Segmento ripetuto e un errore del programmatore`() {
        assertFailsWith<IllegalArgumentException> {
            IngressoRiassunto.costruisci(listOf(listOf(segmento(a, 1, 1, 0, "x")), listOf(segmento(a, 1, 1, 0, "x"))))
        }
    }
}
