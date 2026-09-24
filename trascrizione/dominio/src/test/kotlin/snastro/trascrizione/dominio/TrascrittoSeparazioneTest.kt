package snastro.trascrizione.dominio

import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** ADR 0019: the `confermato` flag (INV-26), `riassegnaInBlocco` (AC-511/512/514) and `confermaSegmento` (AC-513). */
class TrascrittoSeparazioneTest {
    private val registrazioneId = RegistrazioneId("id-1")

    // --- INV-26 -----------------------------------------------------------------------------------

    @Test
    fun `INV-26 crea parte con ogni flag confermato a false`() {
        val t = unTrascritto(voci = 3, segmentiPerVoce = 3)
        assertTrue(t.segmenti.none { it.confermato })
    }

    @Test
    fun `INV-26 una riassegna manuale conferma il Segmento spostato e solo quello`() {
        val t = unTrascritto(voci = 2, segmentiPerVoce = 3) // V1:S1,S3,S5 V2:S2,S4,S6

        t.riassegna(SegmentoId(3), VoceId(2)).atteso()
        t.riassegna(SegmentoId(5), null).atteso()

        assertEquals(setOf(SegmentoId(3), SegmentoId(5)), t.confermati())
    }

    @Test
    fun `INV-26 dividi conferma ogni Segmento di S e quelli rimasti su A tengono il loro flag`() {
        val t = unTrascritto(voci = 2, segmentiPerVoce = 3) // V1:S1,S3,S5 V2:S2,S4,S6
        t.confermaSegmento(SegmentoId(1), true).atteso()

        t.dividi(VoceId(1), setOf(SegmentoId(3), SegmentoId(5))).atteso()

        assertEquals(setOf(SegmentoId(1), SegmentoId(3), SegmentoId(5)), t.confermati())
        t.confermaSegmento(SegmentoId(1), false).atteso()
        t.dividi(VoceId(2), setOf(SegmentoId(2))).atteso()
        assertEquals(setOf(SegmentoId(2), SegmentoId(3), SegmentoId(5)), t.confermati())
    }

    @Test
    fun `INV-26 unisci mantiene ogni flag`() {
        val t = unTrascritto(voci = 2, segmentiPerVoce = 2) // V1:S1,S3 V2:S2,S4
        t.confermaSegmento(SegmentoId(2), true).atteso()
        t.confermaSegmento(SegmentoId(3), true).atteso()

        t.unisci(sopravvive = VoceId(1), rimossa = VoceId(2)).atteso()

        assertEquals(setOf(SegmentoId(2), SegmentoId(3)), t.confermati())
    }

    @Test
    fun `INV-26 riassegnaInBlocco non cambia mai un flag e rifiuta un Segmento confermato`() {
        val t = unTrascritto(voci = 3, segmentiPerVoce = 2) // V1:S1,S4 V2:S2,S5 V3:S3,S6
        t.confermaSegmento(SegmentoId(4), true).atteso()

        t.riassegnaInBlocco(listOf(t.sposta(2, verso = 1), t.sposta(3, verso = 1))).atteso()
        assertEquals(setOf(SegmentoId(4)), t.confermati())

        val prima = t.stato()
        t.riassegnaInBlocco(listOf(t.sposta(4, verso = 2))).erroreAtteso<ErroreTrascrizione.TrascrittoCambiato>()
        assertEquals(prima, t.stato())
    }

    @Test
    fun `INV-26 confermaSegmento false revoca il flag`() {
        val t = unTrascritto(voci = 2, segmentiPerVoce = 2)
        t.riassegna(SegmentoId(1), VoceId(2)).atteso()

        val evento = t.confermaSegmento(SegmentoId(1), false).atteso()

        assertEquals(SegmentoConfermato(registrazioneId, SegmentoId(1), confermato = false), evento)
        assertTrue(t.confermati().isEmpty())
    }

    // --- AC-511 / AC-512 / AC-514 riassegnaInBlocco -------------------------------------------------

    @Test
    fun `AC-511 riassegnaInBlocco valido sposta tutto in ordine e rimuove le Voci vuote alla fine`() {
        val t = unTrascritto(voci = 4, segmentiPerVoce = 2) // V1:S1,S5 V2:S2,S6 V3:S3,S7 V4:S4,S8
        val prossimaVoce = t.prossimaVoce
        val piano = listOf(
            t.sposta(3, verso = 1),
            t.sposta(2, verso = 1),
            t.sposta(7, verso = 2),
            t.sposta(6, verso = 1),
        )

        val eventi = t.riassegnaInBlocco(piano).atteso()

        assertEquals(
            listOf(
                riassegnato(segmento = 3, da = 3, a = 1, daRimossa = false),
                riassegnato(segmento = 2, da = 2, a = 1, daRimossa = false),
                riassegnato(segmento = 7, da = 3, a = 2, daRimossa = true),
                riassegnato(segmento = 6, da = 2, a = 1, daRimossa = false),
            ),
            eventi,
        )
        // V3 empty at the end → removed; V2 emptied (S2, S6 left) but refilled by S7 → kept
        assertEquals(listOf(1, 2, 4), t.voci.map { it.id.numero })
        assertEquals(listOf(1, 2, 3, 5, 6), t.voce(1))
        assertEquals(listOf(7), t.voce(2))
        assertEquals(prossimaVoce, t.prossimaVoce) // no Voce created
        // INV-12: the removed number is never reused
        assertEquals(VoceId(prossimaVoce), t.riassegna(SegmentoId(1), null).atteso().a)
    }

    @Test
    fun `AC-511 una Voce svuotata e poi riempita nello stesso blocco resta e non ha daRimossa`() {
        val t = unTrascritto(voci = 4, segmentiPerVoce = 1) // V1:S1 V2:S2 V3:S3 V4:S4
        val s1 = t.sposta(2, verso = 3) // s1 = V2's only Segmento
        val s2 = t.sposta(4, verso = 2)

        val eventi = t.riassegnaInBlocco(listOf(s1, s2)).atteso()

        assertTrue(eventi.none { it.daRimossa && it.da == VoceId(2) })
        assertEquals(listOf(4), t.voce(2))
        assertEquals(listOf(2, 3), t.voce(3))
        assertEquals(listOf(false, true), eventi.map { it.daRimossa }) // V4 is empty at the end
        assertFalse(t.voci.any { it.id == VoceId(4) })
    }

    @Test
    fun `AC-512 riassegnaInBlocco rifiuta ogni piano non valido lasciando lo stato invariato`() {
        val t = unTrascritto(voci = 3, segmentiPerVoce = 4) // V1:S1,S4,S7,S10 V2:S2,S5,S8,S11 V3:S3,S6,S9,S12
        t.confermaSegmento(SegmentoId(9), true).atteso()
        val valido = t.sposta(1, verso = 2)
        val s2 = t.segmenti.single { it.id == SegmentoId(2) }
        val cambiato = ErroreTrascrizione.TrascrittoCambiato(registrazioneId)
        val casi = listOf(
            "Segmento mancante" to
                listOf(valido, SpostamentoSegmento(SegmentoId(99), VoceId(1), VoceId(2), s2.intervallo)),
            "non su da" to listOf(SpostamentoSegmento(s2.id, VoceId(1), VoceId(3), s2.intervallo)),
            "intervallo diverso" to listOf(SpostamentoSegmento(s2.id, VoceId(2), VoceId(1), IntervalloMs(1, 2))),
            "a mancante" to listOf(SpostamentoSegmento(s2.id, VoceId(2), VoceId(7), s2.intervallo)),
            "Segmento confermato" to listOf(t.sposta(9, verso = 1)),
        )
        val prima = t.stato()
        casi.forEach { (caso, piano) ->
            val errore = t.riassegnaInBlocco(piano).erroreAtteso<ErroreTrascrizione.TrascrittoCambiato>()
            assertEquals(cambiato, errore, caso)
            assertEquals(prima, t.stato(), caso)
        }
        assertEquals(
            ErroreTrascrizione.RiassegnazioneNonAmmessa(SegmentoId(2), VoceId(2)),
            t.riassegnaInBlocco(listOf(valido, SpostamentoSegmento(s2.id, VoceId(2), VoceId(2), s2.intervallo)))
                .erroreAtteso<ErroreTrascrizione.RiassegnazioneNonAmmessa>(),
        )
        assertEquals(prima, t.stato())
        assertEquals(
            ErroreTrascrizione.RiassegnazioneNonAmmessa(SegmentoId(1), VoceId(3)),
            t.riassegnaInBlocco(listOf(valido, t.sposta(1, verso = 3)))
                .erroreAtteso<ErroreTrascrizione.RiassegnazioneNonAmmessa>(),
        )
        assertEquals(prima, t.stato())
    }

    @Test
    fun `AC-512 una voce stantia alla fine di un piano di 10 non applica nulla`() {
        val t = unTrascritto(voci = 3, segmentiPerVoce = 4)
        val piano = listOf(1, 3, 4, 6, 7, 9, 10, 12, 2).map { t.sposta(it, verso = if (it % 3 == 2) 1 else 2) } +
            SpostamentoSegmento(SegmentoId(5), VoceId(2), VoceId(1), IntervalloMs(0, 1))
        assertEquals(10, piano.size)
        val prima = t.stato()

        t.riassegnaInBlocco(piano).erroreAtteso<ErroreTrascrizione.TrascrittoCambiato>()

        assertEquals(prima, t.stato())
    }

    @Test
    fun `AC-512 una voce il cui a e svuotato da una voce precedente dello stesso piano non e stantia`() {
        val t = unTrascritto(voci = 3, segmentiPerVoce = 1) // V1:S1 V2:S2 V3:S3
        val piano = listOf(t.sposta(2, verso = 1), t.sposta(3, verso = 2))

        val eventi = t.riassegnaInBlocco(piano).atteso()

        assertEquals(listOf(false, true), eventi.map { it.daRimossa })
        assertEquals(listOf(1, 2), t.voce(1))
        assertEquals(listOf(3), t.voce(2))
        assertEquals(listOf(1, 2), t.voci.map { it.id.numero })
    }

    @Test
    fun `AC-514 riassegnaInBlocco di una lista vuota restituisce Ok vuoto e non cambia nulla`() {
        val t = unTrascritto(voci = 2, segmentiPerVoce = 2)
        val prima = t.stato()

        assertEquals(emptyList(), t.riassegnaInBlocco(emptyList()).atteso())
        assertEquals(prima, t.stato())
    }

    // --- AC-513 confermaSegmento --------------------------------------------------------------------

    @Test
    fun `AC-513 confermaSegmento imposta il flag e restituisce l evento lo stesso valore e un no-op`() {
        val t = unTrascritto(voci = 2, segmentiPerVoce = 2)

        assertEquals(
            SegmentoConfermato(registrazioneId, SegmentoId(2), confermato = true),
            t.confermaSegmento(SegmentoId(2), true).atteso(),
        )
        assertEquals(setOf(SegmentoId(2)), t.confermati())

        val prima = t.stato()
        assertNull(t.confermaSegmento(SegmentoId(2), true).atteso())
        assertNull(t.confermaSegmento(SegmentoId(1), false).atteso())
        assertEquals(prima, t.stato())

        assertEquals(
            ErroreTrascrizione.SegmentoNonTrovato(SegmentoId(99)),
            t.confermaSegmento(SegmentoId(99), true).erroreAtteso<ErroreTrascrizione.SegmentoNonTrovato>(),
        )
        assertEquals(prima, t.stato())
    }

    private fun Trascritto.sposta(segmento: Int, verso: Int): SpostamentoSegmento {
        val s = segmenti.single { it.id == SegmentoId(segmento) }
        return SpostamentoSegmento(s.id, s.voceId, VoceId(verso), s.intervallo)
    }

    private fun riassegnato(segmento: Int, da: Int, a: Int, daRimossa: Boolean): SegmentoRiassegnato =
        SegmentoRiassegnato(registrazioneId, SegmentoId(segmento), VoceId(da), VoceId(a), daRimossa, aNuova = false)

    private fun Trascritto.confermati(): Set<SegmentoId> =
        segmenti.filter { it.confermato }.mapTo(mutableSetOf()) { it.id }

    private fun Trascritto.stato(): Triple<List<Segmento>, List<Voce>, Int> = Triple(segmenti, voci, prossimaVoce)

    private fun Trascritto.voce(numero: Int): List<Int> =
        voci.single { it.id == VoceId(numero) }.segmenti.map { it.id.numero }
}
