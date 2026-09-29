package snastro.sintesi.dominio

import snastro.kernel.VoceId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TestoConVociTest {
    @Test
    fun `INV-S5 decodifica e codifica sono un giro senza perdite`() {
        val casi = mapOf(
            "{V1} propone il budget" to listOf(ParteTesto.Voce(VoceId(1)), ParteTesto.Testo(" propone il budget")),
            "{V1}{V2}" to listOf(ParteTesto.Voce(VoceId(1)), ParteTesto.Voce(VoceId(2))),
            "graffe {{letterali}} e {V12}" to
                listOf(ParteTesto.Testo("graffe {letterali} e "), ParteTesto.Voce(VoceId(12))),
            "{{{V3}}}" to listOf(ParteTesto.Testo("{"), ParteTesto.Voce(VoceId(3)), ParteTesto.Testo("}")),
            "" to emptyList(),
        )

        casi.forEach { (codificato, parti) ->
            val t = assertNotNull(TestoConVoci.decodifica(codificato), codificato)
            assertEquals(TestoConVoci(parti), t, codificato)
            assertEquals(codificato, t.codifica(), codificato)
            assertEquals(t, TestoConVoci.decodifica(t.codifica()), codificato)
        }
    }

    @Test
    fun `INV-S5 un token malformato decodifica a null`() {
        listOf("{", "a }", "{V}", "{V0}", "{Vx}", "x {V01}", "{V1").forEach { s ->
            assertNull(TestoConVoci.decodifica(s), s)
        }
    }

    @Test
    fun `INV-S5 voci elenca le Voci referenziate`() {
        assertEquals(setOf(VoceId(1), VoceId(4)), testo("{V1} e {V4} con {V1}").voci)
    }

    @Test
    fun `A28 il costruttore pubblico rifiuta una Voce non canonica, chiudendo l asimmetria con decodifica`() {
        listOf(VoceId(0), VoceId(-1), VoceId(1_000_000_000)).forEach { nonCanonica ->
            assertFailsWith<IllegalArgumentException>("$nonCanonica") {
                TestoConVoci(listOf(ParteTesto.Voce(nonCanonica)))
            }
        }
        // Prima del fix, codifica() scriveva un token che decodifica() poi rifiutava (asimmetria A28).
        TestoConVoci(listOf(ParteTesto.Voce(VoceId(1)), ParteTesto.Voce(VoceId(999_999_999)))) // canonical: accepted
    }
}
