package snastro.trascrizione.adattatori.ml

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Steps 2, 4 and 5 of ADR 0019 §1.2 on synthetic embeddings (gate, no natives): AC-480..AC-485, AC-487.
 * A "blob" is a run of 2 s pieces whose embeddings sit around one direction (deterministic jitter).
 */
class RaggruppamentoVociTest {
    private class Scena {
        val pezzi = mutableListOf<Pezzo>()
        val embedding = mutableListOf<DoubleArray>()
        private var t = 0.0

        /** [secondi] of speech in pieces of [durataPezzo] s around [direzione]; returns their indices. */
        fun blob(direzione: DoubleArray, secondi: Int, durataPezzo: Double = 2.0, jitter: Double = 0.02): IntRange {
            val primo = pezzi.size
            repeat((secondi / durataPezzo).toInt()) {
                pezzi += Pezzo(t, t + durataPezzo)
                t += durataPezzo
                val v = DoubleArray(direzione.size) { d -> direzione[d] + jitter * sin(pezzi.size * (d + FASE_JITTER)) }
                embedding += normalizzato(v)
            }
            return primo until pezzi.size
        }

        fun voci(k: Int?): IntArray = RaggruppamentoVoci.voci(pezzi, embedding, k)
    }

    @Test
    fun `AC-480 quattro blob da 100 80 70 e 20 s con k 3 danno 3 voci e il blob piccolo va al centroide piu vicino`() {
        val s = Scena()
        val a = s.blob(asse(0), 100)
        val b = s.blob(asse(1), 80)
        val c = s.blob(asse(2), 70)
        val d = s.blob(normalizzato(asse(0).plus(asse(3), 0.6)), 20)

        val voci = s.voci(3)

        assertEquals(3, voci.toSet().size)
        assertEquals(setOf(voci[a.first]), d.map { voci[it] }.toSet(), "il blob da 20 s segue A, il piu vicino")
        assertEquals(3, setOf(voci[a.first], voci[b.first], voci[c.first]).size)
    }

    @Test
    fun `AC-481 due blob compatti da 100 s con k 4 danno esattamente 2 voci senza eccezioni`() {
        val s = Scena()
        s.blob(asse(0), 100, jitter = 0.0)
        s.blob(asse(1), 100, jitter = 0.0)

        val voci = s.voci(4)

        assertEquals(2, voci.toSet().size)
    }

    @Test
    fun `AC-482 il minimo per qualificarsi e min di 60 s e del 10 per cento del parlato`() {
        assertEquals(60.0, RaggruppamentoVoci.durataMinimaGruppoS(30 * 60.0))
        assertEquals(30.0, RaggruppamentoVoci.durataMinimaGruppoS(5 * 60.0), 1e-9)
        assertEquals(0, RaggruppamentoVoci.voci(emptyList(), emptyList(), 3).size)
        assertEquals(0, RaggruppamentoVoci.voci(emptyList(), emptyList(), null).size)
    }

    @Test
    fun `AC-483 senza k taglio a SOGLIA_AHC_AUTO, i gruppi qualificati tengono tutti i pezzi`() {
        val s = Scena()
        val a = s.blob(asse(0), 100)
        val b = s.blob(asse(1), 100)

        val voci = s.voci(null)

        assertEquals(2, voci.toSet().size)
        assertEquals(1, a.map { voci[it] }.toSet().size)
        assertEquals(1, b.map { voci[it] }.toSet().size)
    }

    @Test
    fun `AC-483 senza k un parlato in cui nulla si qualifica da esattamente una voce`() {
        val s = Scena()
        repeat(DIMENSIONE_AMPIA) { s.blob(asse(it, DIMENSIONE_AMPIA), 2, jitter = 0.0) }

        val voci = s.voci(null)

        assertEquals(setOf(0), voci.toSet())
    }

    @Test
    fun `AC-484 lo stesso ingresso da lo stesso risultato`() {
        val s = Scena()
        s.blob(asse(0), 100)
        s.blob(asse(1), 80)
        s.blob(asse(2), 70)

        assertContentEquals(s.voci(3), s.voci(3))
        assertContentEquals(s.voci(null), s.voci(null))
    }

    @Test
    fun `AC-484 a parita di distanza vince la coppia i j piu bassa e il centroide piu basso`() {
        val unioni = MatriceDistanze.di(4) { _, _ -> 1.0 }.agglomera()

        assertEquals(listOf(Unione(0, 1, 1.0), Unione(4, 2, 1.0), Unione(5, 3, 1.0)), unioni)
        val c = doubleArrayOf(1.0, 0.0)
        assertEquals(0, piuVicino(doubleArrayOf(0.6, 0.8), listOf(c, c.copyOf())))
    }

    @Test
    fun `AC-485 un segmento di 7500 ms da 3 pezzi da 2500 ms, uno di 3000 ms da 1 pezzo, senza sovrapposizioni`() {
        val pezzi = RaggruppamentoVoci.pezzi(listOf(0.0 to 7.5, 10.0 to 13.0))

        assertEquals(listOf(Pezzo(0.0, 2.5), Pezzo(2.5, 5.0), Pezzo(5.0, 7.5), Pezzo(10.0, 13.0)), pezzi)
    }

    @Test
    fun `AC-485 nessun pezzo attraversa il confine di un segmento e i segmenti sovrapposti tengono i loro pezzi`() {
        val pezzi = RaggruppamentoVoci.pezzi(listOf(0.0 to 4.0, 2.0 to 9.5))

        assertEquals(listOf(Pezzo(0.0, 2.0), Pezzo(2.0, 4.0)), pezzi.take(2))
        assertEquals(listOf(2.0, 4.5, 7.0), pezzi.drop(2).map { it.inizioS })
        assertEquals(9.5, pezzi.last().fineS)
        assertTrue(pezzi.all { it.durataS <= 3.0 })
    }

    @Test
    fun `AC-485 normalizzazione v diviso norma piu 1e-9`() {
        val n = RaggruppamentoVoci.normalizza(floatArrayOf(3f, 4f))

        assertContentEquals(doubleArrayOf(3 / (5 + 1e-9), 4 / (5 + 1e-9)), n)
        assertContentEquals(doubleArrayOf(0.0, 0.0), RaggruppamentoVoci.normalizza(floatArrayOf(0f, 0f)))
    }

    @Test
    fun `AC-485 i pezzi sotto 1500 ms restano fuori dall AHC ma ricevono una voce dal centroide piu vicino`() {
        val s = Scena()
        val a = s.blob(asse(0), 100)
        s.blob(asse(1), 100)
        // 100 s of 1 s pieces in a THIRD direction, leaning to A: inside the AHC they would qualify alone.
        val corti = s.blob(normalizzato(asse(2).plus(asse(0), 0.9)), 100, durataPezzo = 1.0)

        val voci = s.voci(null)

        assertEquals(2, voci.toSet().size)
        assertEquals(setOf(voci[a.first]), corti.map { voci[it] }.toSet())
    }

    @Test
    fun `AC-485 l average linkage e pesato per NUMERO di pezzi, non per durata`() {
        // d01 = 0.1, d02 = 0.2, d12 = 0.8, d34 = 0.45, every other pair 0.9. After (0,1): by count
        // {0,1}-2 = (0.2 + 0.8) / 2 = 0.5 > 0.45, so (3,4) merges next; weighted by durations 3 s and
        // 1.5 s it would be 0.4 and merge first. Durations are not even an input of the linkage.
        val d = mapOf((0 to 1) to 0.1, (0 to 2) to 0.2, (1 to 2) to 0.8, (3 to 4) to 0.45)

        val unioni = MatriceDistanze.di(5) { i, j -> d[i to j] ?: 0.9 }.agglomera()

        assertEquals(Unione(0, 1, 0.1), unioni[0])
        assertEquals(Unione(3, 4, 0.45), unioni[1])
        assertEquals(Unione(5, 2, 0.5), unioni[2])
    }

    @Test
    fun `AC-485 la regola k alza il taglio finche k gruppi si qualificano`() {
        val s = Scena()
        val a = s.blob(asse(0), 100)
        val b = s.blob(asse(1), 100)
        // One outlier piece opposite to both: the last merge, so at m = 2 it is a cluster alone (not qualifying).
        s.blob(normalizzato(asse(0).plus(asse(1), 1.0).map { -it }.toDoubleArray()), 2)

        val voci = s.voci(2)

        assertEquals(2, voci.toSet().size)
        assertTrue(voci[a.first] != voci[b.first], "m = 3 separa A da B")
    }

    @Test
    fun `AC-485 le k voci sono ordinate per parlato qualificante dei soli pezzi da almeno 1500 ms`() {
        val s = Scena()
        val a = s.blob(asse(0), 70)
        s.blob(asse(0), 60, durataPezzo = 1.0) // A's short pieces: speech, but not qualifying speech
        val b = s.blob(asse(1), 100)

        val voci = s.voci(2)

        assertEquals(0, voci[b.first], "B: 100 s qualificanti contro i 70 di A")
        assertEquals(1, voci[a.first])
    }

    @Test
    fun `AC-485 i centroidi sono pesati per durata`() {
        // A = {(1,0) for 3 s, (0,1) for 1.5 s}: weighted it points at 26.6 deg, unweighted at 45 deg. B at
        // 60 deg. A piece at 50 deg is nearer to B only with the duration weighting.
        val x = listOf(doubleArrayOf(1.0, 0.0), doubleArrayOf(0.0, 1.0), unitario(GRADI_B))
        val centroidi = centroidi(x, doubleArrayOf(3.0, 1.5, 2.0), intArrayOf(0, 0, 1), listOf(0, 1))

        assertEquals(1, piuVicino(unitario(GRADI_PEZZO), centroidi))
    }

    @Test
    fun `AC-487 oltre PEZZI_MASSIMI_AHC l AHC gira su un pezzo ogni ceil di n su 6000 e ogni pezzo ha una voce`() {
        val s = Scena()
        repeat(4) { direzione -> s.blob(asse(direzione, DIMENSIONE_GRANDE), PEZZI_GRANDI / 4 * 2, jitter = 0.2) }
        val adatti = s.pezzi.indices.toList()

        val campione = RaggruppamentoVoci.campioneAhc(adatti, passo = 3)
        val voci = s.voci(4)

        assertEquals(PEZZI_GRANDI, s.pezzi.size)
        assertTrue(campione.size <= RaggruppamentoVoci.PEZZI_MASSIMI_AHC, "campione ${campione.size}")
        assertEquals((0 until PEZZI_GRANDI step 3).toList(), campione)
        assertEquals(PEZZI_GRANDI, voci.size)
        assertEquals(4, voci.toSet().size)
    }

    private fun asse(i: Int, dimensione: Int = DIMENSIONE): DoubleArray =
        DoubleArray(dimensione) { if (it == i) 1.0 else 0.0 }

    private fun DoubleArray.plus(altro: DoubleArray, peso: Double): DoubleArray =
        DoubleArray(size) { this[it] + peso * altro[it] }

    private fun unitario(gradi: Double): DoubleArray = Math.toRadians(gradi).let { doubleArrayOf(cos(it), sin(it)) }

    private companion object {
        const val DIMENSIONE = 8
        const val DIMENSIONE_AMPIA = 20
        const val DIMENSIONE_GRANDE = 16
        const val PEZZI_GRANDI = 13_000
        const val GRADI_B = 60.0
        const val GRADI_PEZZO = 50.0
        const val FASE_JITTER = 1.7

        fun normalizzato(v: DoubleArray): DoubleArray {
            val n = sqrt(v.sumOf { it * it })
            return DoubleArray(v.size) { v[it] / n }
        }
    }
}
