package snastro.sintesi.dominio

import snastro.kernel.VoceId
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import kotlin.test.Test
import kotlin.test.assertEquals

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
            val t = TestoConVoci.decodifica(codificato).atteso()
            assertEquals(TestoConVoci(parti), t, codificato)
            assertEquals(codificato, t.codifica(), codificato)
            assertEquals(t, TestoConVoci.decodifica(t.codifica()).atteso(), codificato)
        }
    }

    @Test
    fun `INV-S5 un token malformato e un Errore`() {
        val casi = listOf("{" to 0, "a }" to 2, "{V}" to 0, "{V0}" to 0, "{Vx}" to 0, "x {V01}" to 2, "{V1" to 0)
        casi.forEach { (s, pos) ->
            val errore = TestoConVoci.decodifica(s).erroreAtteso<ErroreSintesi.TokenVoceMalformato>()
            assertEquals(pos, errore.posizione, s)
        }
    }

    @Test
    fun `INV-S5 voci elenca le Voci referenziate`() {
        assertEquals(setOf(VoceId(1), VoceId(4)), testo("{V1} e {V4} con {V1}").voci)
    }
}
