package snastro.trascrizione.dominio

import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.SegmentoRef
import snastro.kernel.VoceId
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VociDellIncontroTest {
    private val incontroId = IncontroId("incontro-1")
    private val a = RegistrazioneId("parte-a")
    private val b = RegistrazioneId("parte-b")

    // --- INV-I5 completaParte -------------------------------------------------------------------------------------

    @Test
    fun `INV-I5 la parte B aggiunge Voci nuove numerate per prima apparizione in B e non tocca la parte A`() {
        val v = VociDellIncontro.crea(incontroId)

        val primaA = v.completaParte(a, turni(0, 1), DURATA_TRASCRITTO_MS).atteso()
        val segmentiA = v.trascritto(a)?.segmenti
        // in B the diarizer's cluster 2 speaks first, then 0 (the same label as in A: never joined), then 1
        val primaB = v.completaParte(b, turni(2, 0, 1), DURATA_TRASCRITTO_MS).atteso()

        assertEquals(ConclusioneParte.PrimaTrascrizione(voci(1, 2)), primaA)
        assertEquals(ConclusioneParte.PrimaTrascrizione(voci(3, 4, 5)), primaB)
        assertEquals(segmentiA, v.trascritto(a)?.segmenti)
        assertEquals(listOf(3, 4, 5), v.trascritto(b)?.voci?.map { it.id.numero })
        assertEquals(VoceId(3), v.trascritto(b)?.segmenti?.minBy { it.intervallo.inizioMs }?.voceId)
        assertEquals(voci(1, 2, 3, 4, 5).toList(), v.voci)
        (1..5).forEach { n -> assertEquals(1, v.partiDi(VoceId(n)).size, "Voce $n in one Parte only") }
    }

    @Test
    fun `INV-I5 sostituire B rimuove le Voci solo di B, tiene la Voce unita con A, numera le nuove dal contatore`() {
        val v = dueParti() // A: Voce 1, 2 · B: Voce 3, 4, 5
        v.unisci(VoceId(1), VoceId(3)).atteso() // Voce 1 now speaks in A and B
        val segmentiA = v.trascritto(a)?.segmenti

        val conclusione = v.completaParte(b, turni(0, 1), DURATA_TRASCRITTO_MS).atteso()

        assertEquals(ConclusioneParte.Sostituzione(vociRimosse = voci(4, 5), vociNuove = voci(6, 7)), conclusione)
        assertEquals(listOf(a), v.partiDi(VoceId(1)))
        assertEquals(segmentiA, v.trascritto(a)?.segmenti)
        assertEquals(voci(1, 2, 6, 7).toList(), v.voci)
        assertEquals(setOf(VoceId(6), VoceId(7)), v.trascritto(b)?.segmenti?.map { it.voceId }?.toSet())
    }

    @Test
    fun `INV-I5 una parte senza parlato o oltre la durata e' rifiutata e non cambia nulla`() {
        val v = dueParti()
        val prima = v.istantanea()

        v.completaParte(b, emptyList(), DURATA_TRASCRITTO_MS).erroreAtteso<ErroreTrascrizione.NessunParlatoRilevato>()
        v.completaParte(b, turni(0), durataMs = 500).erroreAtteso<ErroreTrascrizione.SegmentoOltreLaDurata>()

        assertEquals(prima, v.istantanea())
    }

    // --- INV-I4 numbers never reused ------------------------------------------------------------------------------

    @Test
    fun `INV-I4 dopo unire, ogni sostituzione e la rimozione di ogni parte la Voce nuova prende il contatore`() {
        val v = dueParti() // 1, 2 | 3, 4, 5
        val contatori = mutableListOf(v.prossimaVoce)
        v.unisci(VoceId(1), VoceId(3)).atteso()
        contatori += v.prossimaVoce
        v.completaParte(a, turni(0, 1), DURATA_TRASCRITTO_MS).atteso() // 6, 7
        contatori += v.prossimaVoce
        v.completaParte(b, turni(0, 1), DURATA_TRASCRITTO_MS).atteso() // 8, 9
        contatori += v.prossimaVoce
        v.rimuoviParte(a).atteso()
        v.rimuoviParte(b).atteso()
        contatori += v.prossimaVoce
        assertTrue(v.voci.isEmpty())

        val c = RegistrazioneId("parte-c")
        val nuova = v.completaParte(c, turni(0), DURATA_TRASCRITTO_MS).atteso()

        assertEquals(ConclusioneParte.PrimaTrascrizione(voci(10)), nuova)
        assertEquals(contatori.sorted(), contatori, "the counter is never decremented")
    }

    // --- INV-I16 segmentoId never reused --------------------------------------------------------------------------

    @Test
    fun `INV-I16 sostituire la parte A con Segmenti 1-40 numera i nuovi da 41 e la Fonte A-12 non risolve piu'`() {
        val v = VociDellIncontro.crea(incontroId)
        v.completaParte(a, turni(0, 1, perVoce = 20), DURATA_TRASCRITTO_MS).atteso()
        assertEquals((1..40).toList(), v.trascritto(a)?.segmenti?.map { it.id.numero })

        v.completaParte(a, turni(0, perVoce = 3), DURATA_TRASCRITTO_MS).atteso()

        assertEquals(listOf(41, 42, 43), v.trascritto(a)?.segmenti?.map { it.id.numero })
        assertEquals(44, v.trascritto(a)?.prossimoSegmento)
        v.confermaSegmento(SegmentoRef(a, SegmentoId(12)), true).erroreAtteso<ErroreTrascrizione.SegmentoNonTrovato>()
        v.riassegna(SegmentoRef(a, SegmentoId(12)), null).erroreAtteso<ErroreTrascrizione.SegmentoNonTrovato>()
    }

    // --- INV-6 ----------------------------------------------------------------------------------------------------

    @Test
    fun `INV-6 unire, riassegnare, sostituire e rimuovere una parte rimuovono la Voce svuotata`() {
        val v = dueParti() // 1, 2 | 3, 4, 5
        v.unisci(VoceId(1), VoceId(3)).atteso()
        assertFalse(VoceId(3) in v.voci)

        val unico = v.completaParte(b, turni(0, 1, 2, perVoce = 1), DURATA_TRASCRITTO_MS).atteso() // 6, 7, 8
        assertEquals(voci(4, 5), (unico as ConclusioneParte.Sostituzione).vociRimosse)
        val ev = v.riassegna(SegmentoRef(b, segmentoDi(v, b, VoceId(8))), VoceId(6)).atteso()
        assertTrue(ev.daRimossa)
        assertFalse(VoceId(8) in v.voci)

        assertEquals(voci(6, 7), v.rimuoviParte(b).atteso())
        assertEquals(voci(1, 2).toList(), v.voci)
    }

    @Test
    fun `INV-6 nessuna sequenza di comandi lascia una Voce vuota o un Segmento su una Voce inesistente`() {
        val parti = listOf(a, b, RegistrazioneId("parte-c"))
        repeat(SEQUENZE) { seme ->
            val caso = Random(seme)
            val v = VociDellIncontro.crea(incontroId)
            val sparite = mutableSetOf<VoceId>()
            repeat(PASSI) {
                val prima = v.voci.toSet()
                val contatore = v.prossimaVoce
                comandoCasuale(v, caso, parti)
                val assegnate = v.trascritti.flatMap { t -> t.segmenti.map { it.voceId } }.toSet()
                assertEquals(assegnate.sortedBy { it.numero }, v.voci, "seed $seme: voci = Voci with a Segmento")
                v.voci.forEach { voce -> assertTrue(v.partiDi(voce).isNotEmpty(), "seed $seme: $voce empty") }
                assertTrue(v.prossimaVoce >= contatore, "seed $seme: counter decremented")
                assertTrue(v.voci.all { it.numero < v.prossimaVoce }, "seed $seme: a Voce beyond the counter")
                sparite += prima - v.voci.toSet()
                assertTrue(v.voci.none { it in sparite }, "seed $seme: a number given again")
            }
        }
    }

    // --- INV-8 ----------------------------------------------------------------------------------------------------

    @Test
    fun `INV-8 la Revisione tra due parti conserva Segmenti, intervalli, testi e parte - cambiano Voce e confermato`() {
        val v = dueParti() // 1, 2 | 3, 4, 5
        val prima = v.contenuto()

        v.unisci(VoceId(1), VoceId(3)).atteso()
        val diUno = v.refsDi(VoceId(1))
        v.dividi(VoceId(1), diUno.filter { it.registrazioneId == b }.toSet()).atteso()
        val mosso = SegmentoRef(a, segmentoDi(v, a, VoceId(2)))
        v.riassegna(mosso, VoceId(4)).atteso()
        assertTrue(v.trascritto(a)!!.segmenti.single { it.id == mosso.segmentoId }.confermato, "INV-26 manual move")
        val daSpostare = v.trascritto(b)!!.segmenti.first { it.voceId == VoceId(5) }
        v.riassegnaInBlocco(
            listOf(SpostamentoNellIncontro(SegmentoRef(b, daSpostare.id), VoceId(5), VoceId(2), daSpostare.intervallo)),
        ).atteso()
        v.confermaSegmento(SegmentoRef(a, SegmentoId(1)), true).atteso()

        assertEquals(prima, v.contenuto())
    }

    // --- INV-I6 ---------------------------------------------------------------------------------------------------

    @Test
    fun `INV-I6 rimuoviParte di B tiene la Voce 2 con i soli Segmenti di A e rimuove la Voce 4 solo di B`() {
        val v = VociDellIncontro.crea(incontroId)
        v.completaParte(a, turni(0, 1), DURATA_TRASCRITTO_MS).atteso() // 1, 2
        v.completaParte(b, turni(0, 1), DURATA_TRASCRITTO_MS).atteso() // 3, 4
        v.unisci(VoceId(2), VoceId(3)).atteso() // Voce 2 in A and B
        val segmentiA = v.trascritto(a)?.segmenti

        val vociRimosse = v.rimuoviParte(b).atteso()

        assertEquals(voci(4), vociRimosse)
        assertEquals(listOf(a), v.partiDi(VoceId(2)))
        assertEquals(segmentiA, v.trascritto(a)?.segmenti)
        assertFalse(v.haParte(b))
        assertEquals(5, v.prossimaVoce)
        v.rimuoviParte(b).erroreAtteso<ErroreTrascrizione.TrascrittoNonTrovato>()
    }

    // --- INV-I7 ---------------------------------------------------------------------------------------------------

    @Test
    fun `INV-I7 una Voce o un Segmento di un altro Incontro non si trova e nulla cambia`() {
        val v = VociDellIncontro.crea(incontroId)
        v.completaParte(a, turni(0, 1), DURATA_TRASCRITTO_MS).atteso() // 1, 2
        val altro = VociDellIncontro.crea(IncontroId("incontro-2"))
        val estranea = RegistrazioneId("parte-x")
        altro.completaParte(estranea, turni(0, 1, 2, 3), DURATA_TRASCRITTO_MS).atteso() // 1..4
        val prima = v.istantanea()

        v.unisci(VoceId(1), VoceId(4)).erroreAtteso<ErroreTrascrizione.VoceNonTrovata>()
        val fuori = SegmentoRef(estranea, SegmentoId(1))
        v.riassegna(fuori, VoceId(1)).erroreAtteso<ErroreTrascrizione.SegmentoNonTrovato>()
        v.confermaSegmento(fuori, true).erroreAtteso<ErroreTrascrizione.SegmentoNonTrovato>()
        v.dividi(VoceId(1), setOf(fuori)).erroreAtteso<ErroreTrascrizione.DivisioneNonAmmessa>()

        assertEquals(prima, v.istantanea())
    }

    // --- AC-I16 dividi over the Incontro --------------------------------------------------------------------------

    @Test
    fun `AC-I16 dividere la Voce 2 coi suoi Segmenti di B crea una Voce dal contatore - INV-10 sull'intero Incontro`() {
        val v = VociDellIncontro.crea(incontroId)
        v.completaParte(a, turni(0, 1), DURATA_TRASCRITTO_MS).atteso() // 1, 2
        v.completaParte(b, turni(0, 1), DURATA_TRASCRITTO_MS).atteso() // 3, 4
        v.unisci(VoceId(2), VoceId(4)).atteso()
        val diDue = v.refsDi(VoceId(2))
        val diB = diDue.filter { it.registrazioneId == b }.toSet()

        v.dividi(VoceId(2), diDue.toSet()).erroreAtteso<ErroreTrascrizione.DivisioneNonAmmessa>()
        val ev = v.dividi(VoceId(2), diB).atteso()

        assertEquals(EventoRevisione.VoceDivisa(incontroId, VoceId(2), VoceId(5), diB.toList()), ev)
        assertEquals(listOf(b), v.partiDi(VoceId(5)))
        assertTrue(v.trascritto(b)!!.segmenti.filter { it.voceId == VoceId(5) }.all { it.confermato })
        assertEquals(listOf(a), v.partiDi(VoceId(2)))
        assertEquals(6, v.prossimaVoce)
    }

    // --- read copies ----------------------------------------------------------------------------------------------

    @Test
    fun `INV-6 le copie lette non cambiano la radice`() {
        val v = dueParti()
        val prima = v.istantanea()

        v.trascritto(a)!!.unisci(VoceId(1), VoceId(2)).atteso()
        v.trascritti.first().unisci(VoceId(1), VoceId(2)).atteso()

        assertEquals(prima, v.istantanea())
    }

    // --- fixtures -------------------------------------------------------------------------------------------------

    /** A root whose Parte A has Voci 1, 2 and Parte B Voci 3, 4, 5, two Segmenti each. */
    private fun dueParti(): VociDellIncontro {
        val v = VociDellIncontro.crea(incontroId)
        v.completaParte(a, turni(0, 1), DURATA_TRASCRITTO_MS).atteso()
        v.completaParte(b, turni(0, 1, 2), DURATA_TRASCRITTO_MS).atteso()
        return v
    }

    /** [perVoce] rounds in which the diarizer clusters speak in the order [ordine], one 1 s turn each. */
    private fun turni(vararg ordine: Int, perVoce: Int = 2): List<SegmentoIniziale> =
        (0 until perVoce).flatMap { giro ->
            ordine.mapIndexed { i, cluster -> unSegmentoIniziale(cluster, ((giro * ordine.size + i) * 1_000).toLong()) }
        }

    private fun voci(vararg numeri: Int): Set<VoceId> = numeri.mapTo(LinkedHashSet()) { VoceId(it) }

    private fun segmentoDi(v: VociDellIncontro, r: RegistrazioneId, voce: VoceId): SegmentoId =
        v.trascritto(r)!!.segmenti.first { it.voceId == voce }.id

    private fun VociDellIncontro.refsDi(voce: VoceId): List<SegmentoRef> =
        trascritti.flatMap { t ->
            t.segmenti.filter { it.voceId == voce }.map { SegmentoRef(t.registrazioneId, it.id) }
        }

    private fun VociDellIncontro.istantanea(): Any =
        Triple(prossimaVoce, voci, trascritti.map { it.registrazioneId to it.segmenti })

    /** The multiset of (Parte, segmentoId, intervallo, testo): what INV-8 conserves. */
    private fun VociDellIncontro.contenuto(): List<Any> =
        trascritti.flatMap { t -> t.segmenti.map { listOf(t.registrazioneId, it.id, it.intervallo, it.testo) } }
            .sortedBy { it.toString() }

    private fun comandoCasuale(v: VociDellIncontro, caso: Random, parti: List<RegistrazioneId>) {
        val voci = v.voci
        val refs = v.trascritti.flatMap { t -> t.segmenti.map { SegmentoRef(t.registrazioneId, it.id) } }
        fun voce(): VoceId = if (voci.isEmpty()) VoceId(1) else voci[caso.nextInt(voci.size)]
        when (caso.nextInt(6)) {
            0 -> v.completaParte(
                parti[caso.nextInt(parti.size)],
                turni(*IntArray(caso.nextInt(1, 4)) { it }, perVoce = caso.nextInt(1, 3)),
                DURATA_TRASCRITTO_MS,
            )
            1 -> v.rimuoviParte(parti[caso.nextInt(parti.size)])
            2 -> v.unisci(voce(), voce())
            3 -> if (refs.isNotEmpty()) {
                v.riassegna(refs[caso.nextInt(refs.size)], if (caso.nextBoolean()) null else voce())
            }
            4 -> v.dividi(voce(), refs.shuffled(caso).take(caso.nextInt(0, 3)).toSet())
            else -> if (refs.isNotEmpty()) {
                val ref = refs[caso.nextInt(refs.size)]
                val s = v.trascritto(ref.registrazioneId)!!.segmenti.first { it.id == ref.segmentoId }
                v.riassegnaInBlocco(listOf(SpostamentoNellIncontro(ref, s.voceId, voce(), s.intervallo)))
            }
        }
    }

    private companion object {
        const val SEQUENZE = 200
        const val PASSI = 30
    }
}
