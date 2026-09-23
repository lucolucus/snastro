package snastro.parlanti.dominio

import snastro.kernel.IntervalloMs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SorgenteImprontaTest {
    private fun i(inizioMs: Long, fineMs: Long) = IntervalloMs(inizioMs, fineMs)

    private val List<IntervalloMs>.totaleMs: Long get() = sumOf { it.fineMs - it.inizioMs }

    private fun List<IntervalloMs>.disgiuntiInOrdine(): Boolean = zipWithNext().all { (a, b) -> a.fineMs <= b.inizioMs }

    @Test
    fun `AC-274 Selezione filtro 1000 ms esclude i segmenti piu corti quando almeno uno lo raggiunge`() {
        val sorgente = SorgenteImpronta.di(listOf(i(0, 999), i(2_000, 3_000), i(5_000, 5_500)))

        assertEquals(listOf(i(2_000, 3_000)), sorgente.intervalli)
    }

    @Test
    fun `AC-274 Selezione se nessun segmento raggiunge 1000 ms usa il solo piu lungo, a parita quello con inizio minore`() {
        assertEquals(listOf(i(1_000, 1_900)), SorgenteImpronta.di(listOf(i(0, 500), i(1_000, 1_900), i(3_000, 3_200))).intervalli)
        assertEquals(listOf(i(1_000, 1_900)), SorgenteImpronta.di(listOf(i(1_000, 1_900), i(3_000, 3_900))).intervalli)
    }

    @Test
    fun `AC-275 Selezione sceglie il piu lungo per primo e a parita di durata quello con inizioMs minore`() {
        // two 20 s intervals, budget 30 s: only one fits whole, it must be the earlier one
        val sorgente = SorgenteImpronta.di(listOf(i(0, 20_000), i(40_000, 60_000)))

        assertEquals(listOf(i(0, 20_000), i(40_000, 50_000)), sorgente.intervalli)
    }

    @Test
    fun `AC-275 Selezione il piu lungo vince anche se viene dopo nel tempo`() {
        val sorgente = SorgenteImpronta.di(listOf(i(0, 20_000), i(30_000, 55_000)))

        assertEquals(listOf(i(0, 5_000), i(30_000, 55_000)), sorgente.intervalli)
    }

    @Test
    fun `AC-276 Selezione budget una Voce di oltre 30 s da esattamente 30000 ms e l intervallo eccedente e tagliato dal suo inizio`() {
        val sorgente = SorgenteImpronta.di(listOf(i(0, 12_000), i(20_000, 45_000), i(50_000, 60_000)))

        assertEquals(BUDGET_IMPRONTA_MS, sorgente.intervalli.totaleMs)
        assertEquals(listOf(i(0, 5_000), i(20_000, 45_000)), sorgente.intervalli)
    }

    @Test
    fun `AC-276 Selezione un unico segmento oltre il budget diventa inizio piu 30000`() {
        assertEquals(listOf(i(7_000, 37_000)), SorgenteImpronta.di(listOf(i(7_000, 100_000))).intervalli)
    }

    @Test
    fun `AC-276 Selezione una Voce entro il budget e presa per intero`() {
        val voce = listOf(i(0, 5_000), i(6_000, 20_000))

        assertEquals(voce, SorgenteImpronta.di(voce).intervalli)
    }

    @Test
    fun `AC-277 Selezione il risultato e in ordine temporale indipendentemente dall ordine di scelta`() {
        val sorgente = SorgenteImpronta.di(listOf(i(50_000, 60_000), i(0, 2_000), i(10_000, 18_000)))

        assertEquals(listOf(i(0, 2_000), i(10_000, 18_000), i(50_000, 60_000)), sorgente.intervalli)
    }

    @Test
    fun `AC-278 Chiave e inizio-fine uniti da virgola in ordine temporale`() {
        val sorgente = SorgenteImpronta.di(listOf(i(8_000, 15_000), i(1_200, 5_400)))

        assertEquals("1200-5400,8000-15000", sorgente.chiave)
    }

    @Test
    fun `AC-278 Chiave due sorgenti hanno la stessa chiave se e solo se hanno gli stessi intervalli`() {
        val a = SorgenteImpronta(listOf(i(1_200, 5_400), i(8_000, 15_000)))
        val b = SorgenteImpronta(listOf(i(1_200, 5_400), i(8_000, 15_000)))
        val c = SorgenteImpronta(listOf(i(1_200, 5_400), i(8_000, 15_001)))
        val d = SorgenteImpronta(listOf(i(12, 5_400)))
        val e = SorgenteImpronta(listOf(i(1, 25_400)))

        assertEquals(a.chiave, b.chiave)
        assertEquals(a, b)
        assertNotEquals(a.chiave, c.chiave)
        assertNotEquals(d.chiave, e.chiave)
    }

    @Test
    fun `AC-279 Selezione intervalli sovrapposti sono prima fusi nella loro unione`() {
        val sorgente = SorgenteImpronta.di(listOf(i(0, 4_000), i(3_000, 6_000)))

        assertEquals(listOf(i(0, 6_000)), sorgente.intervalli)
        assertEquals("0-6000", sorgente.chiave)
    }

    @Test
    fun `AC-279 Selezione la fusione precede filtro, scelta e taglio e nessun millisecondo e contato due volte`() {
        // 600 + 700 ms overlapping -> 0-1100 passes the 1000 ms filter only once merged;
        // 20-40 s and 25-45 s -> 20-45 s (25 s), then the budget trims the next one.
        val sorgente = SorgenteImpronta.di(
            listOf(i(0, 600), i(400, 1_100), i(20_000, 40_000), i(25_000, 45_000), i(50_000, 60_000), i(52_000, 55_000)),
        )

        assertEquals(listOf(i(20_000, 45_000), i(50_000, 55_000)), sorgente.intervalli)
        assertEquals(BUDGET_IMPRONTA_MS, sorgente.intervalli.totaleMs)
        assertEquals("20000-45000,50000-55000", sorgente.chiave)
        assertEquals(listOf(i(0, 1_100)), selezionaIntervalli(listOf(i(0, 600), i(400, 1_100), i(2_000, 2_900)), BUDGET_IMPRONTA_MS, null))
    }

    @Test
    fun `AC-279 Selezione un intervallo contenuto in un altro non allunga l unione e intervalli adiacenti restano distinti`() {
        assertEquals(listOf(i(0, 6_000)), SorgenteImpronta.di(listOf(i(0, 6_000), i(1_000, 2_000))).intervalli)
        assertEquals(listOf(i(0, 2_000), i(2_000, 4_000)), SorgenteImpronta.di(listOf(i(0, 2_000), i(2_000, 4_000))).intervalli)
    }

    @Test
    fun `AC-280 Selezione parametri EstrattoAudio al piu 3 intervalli disgiunti e totale entro 10000 ms`() {
        val voce = listOf(i(0, 1_500), i(2_000, 3_600), i(4_000, 5_700), i(6_000, 7_800), i(8_000, 8_500))

        val estratto = selezionaIntervalli(voce, BUDGET_ESTRATTO_MS, MAX_INTERVALLI_ESTRATTO)

        assertEquals(listOf(i(2_000, 3_600), i(4_000, 5_700), i(6_000, 7_800)), estratto)
        assertTrue(estratto.size <= MAX_INTERVALLI_ESTRATTO && estratto.totaleMs <= BUDGET_ESTRATTO_MS)
    }

    @Test
    fun `AC-280 Selezione EstrattoAudio stessa regola fusione, taglio dall inizio, ripiego e ordine temporale`() {
        val estratto = selezionaIntervalli(
            listOf(i(30_000, 34_000), i(0, 3_000), i(2_000, 4_500), i(10_000, 13_000)),
            BUDGET_ESTRATTO_MS,
            MAX_INTERVALLI_ESTRATTO,
        )

        // union 0-4500 (4.5 s) first, then 30-34 s, then 10-13 s trimmed from its start to the 1.5 s left
        assertEquals(listOf(i(0, 4_500), i(10_000, 11_500), i(30_000, 34_000)), estratto)
        assertEquals(BUDGET_ESTRATTO_MS, estratto.totaleMs)
        assertTrue(estratto.disgiuntiInOrdine())
        assertEquals(listOf(i(100, 900)), selezionaIntervalli(listOf(i(100, 900), i(1_000, 1_500)), BUDGET_ESTRATTO_MS, MAX_INTERVALLI_ESTRATTO))
    }

    @Test
    fun `AC-281 SorgenteImpronta di su una lista vuota e IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> { SorgenteImpronta.di(emptyList()) }
    }

    @Test
    fun `SorgenteImpronta rifiuta intervalli vuoti, sovrapposti o fuori ordine`() {
        assertFailsWith<IllegalArgumentException> { SorgenteImpronta(emptyList()) }
        assertFailsWith<IllegalArgumentException> { SorgenteImpronta(listOf(i(0, 2_000), i(1_000, 3_000))) }
        assertFailsWith<IllegalArgumentException> { SorgenteImpronta(listOf(i(5_000, 6_000), i(0, 1_000))) }
    }

    @Test
    fun `le costanti pinnate hanno i valori dell ADR`() {
        assertEquals(30_000L, BUDGET_IMPRONTA_MS)
        assertEquals(1_000L, DURATA_MINIMA_SEGMENTO_MS)
        assertEquals(10_000L, BUDGET_ESTRATTO_MS)
        assertEquals(3, MAX_INTERVALLI_ESTRATTO)
    }
}
