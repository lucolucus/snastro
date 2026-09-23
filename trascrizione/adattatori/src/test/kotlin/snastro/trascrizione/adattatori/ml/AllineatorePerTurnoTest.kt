package snastro.trascrizione.adattatori.ml

import snastro.kernel.CampioniAudio
import snastro.kernel.IntervalloMs
import snastro.trascrizione.applicazione.porte.CAMPIONI_PER_MS
import snastro.trascrizione.applicazione.porte.Riconoscimento
import snastro.trascrizione.applicazione.porte.RiconoscitoreParlato
import snastro.trascrizione.applicazione.porte.SegmentoGrezzo
import snastro.trascrizione.applicazione.porte.Token
import snastro.trascrizione.applicazione.porte.Turno
import snastro.trascrizione.applicazione.porte.Vad
import snastro.trascrizione.applicazione.porte.tonoDiProva
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ADR 0015 regole 1-10 (AC-247, AC-379..385), su [AllineatorePerTurno] con dei doppi di test locali
 * (mai i Finte generici, per poter osservare/controllare esattamente le chiamate del riconoscitore e
 * del vad — [AllineatoreContratto][snastro.trascrizione.applicazione.porte.AllineatoreContratto]
 * resta in `AllineatorePerTurnoContrattoTest`, AC-246).
 */
class AllineatorePerTurnoTest {

    // --- AC-247 / regola 9 — sovrapposizioni tra voci diverse -----------------------------------

    @Test
    fun `AC-247 turni sovrapposti di voci diverse restano entrambi, mai uniti ne tagliati`() {
        val riconoscitore = RiconoscitoreCheRegistra()
        val turni = listOf(
            Turno(IntervalloMs(0, 3_000), voceIndice = 0),
            Turno(IntervalloMs(2_000, 5_000), voceIndice = 1), // si sovrappone al turno della voce 0
        )

        val segmenti = AllineatorePerTurno(riconoscitore, vadCheNonDeveEssereChiamato)
            .allinea(tonoDiProva(5_000), turni)

        assertEquals(
            listOf(
                SegmentoGrezzo(0, IntervalloMs(0, 3_000), "parola1"),
                SegmentoGrezzo(1, IntervalloMs(2_000, 5_000), "parola2"),
            ),
            segmenti,
        )
        assertEquals(2, riconoscitore.chiamate.size, "ogni turno sovrapposto e' riconosciuto per conto proprio")
    }

    // --- AC-379 / regola 1 — unione dei turni ----------------------------------------------------

    private data class CasoUnione(val distanzaMs: Long, val unito: Boolean)

    private val tabellaUnione = listOf(
        CasoUnione(distanzaMs = -100, unito = true), // sovrapposto: unito
        CasoUnione(distanzaMs = 0, unito = true), // consecutivo: unito
        CasoUnione(distanzaMs = 999, unito = true),
        CasoUnione(distanzaMs = 1_000, unito = false),
    )

    @Test
    fun `AC-379 unione dei turni della stessa voce per distanza dal precedente (tabella)`() {
        tabellaUnione.forEach { caso ->
            val riconoscitore = RiconoscitoreCheRegistra()
            val secondoInizio = 1_000 + caso.distanzaMs
            val secondoFine = secondoInizio + 600
            val turni = listOf(
                Turno(IntervalloMs(0, 1_000), voceIndice = 0),
                Turno(IntervalloMs(secondoInizio, secondoFine), voceIndice = 0),
            )

            val segmenti = AllineatorePerTurno(riconoscitore, vadCheNonDeveEssereChiamato)
                .allinea(tonoDiProva(secondoFine), turni)

            val messaggio = "distanza ${caso.distanzaMs} ms"
            if (caso.unito) {
                assertEquals(1, riconoscitore.chiamate.size, messaggio)
                assertEquals(listOf(IntervalloMs(0, secondoFine)), segmenti.map { it.intervallo }, messaggio)
            } else {
                assertEquals(2, riconoscitore.chiamate.size, messaggio)
                assertEquals(
                    listOf(IntervalloMs(0, 1_000), IntervalloMs(secondoInizio, secondoFine)),
                    segmenti.map { it.intervallo },
                    messaggio,
                )
            }
        }
    }

    @Test
    fun `AC-379 una voce diversa in mezzo impedisce l unione anche se vicina in tempo`() {
        val riconoscitore = RiconoscitoreCheRegistra()
        val turni = listOf(
            Turno(IntervalloMs(0, 1_000), voceIndice = 0),
            Turno(IntervalloMs(1_050, 1_650), voceIndice = 1), // in mezzo, un'altra voce (>= 500 ms: non e' scartato)
            // gap dal primo turno (voce 0): 1_700 - 1_000 = 700 ms < 1000 ms, si unirebbe se non fosse per il mezzo
            Turno(IntervalloMs(1_700, 2_700), voceIndice = 0),
        )

        val segmenti = AllineatorePerTurno(riconoscitore, vadCheNonDeveEssereChiamato)
            .allinea(tonoDiProva(2_700), turni)

        assertEquals(
            listOf(
                IntervalloMs(0, 1_000) to 0,
                IntervalloMs(1_050, 1_650) to 1,
                IntervalloMs(1_700, 2_700) to 0,
            ),
            segmenti.map { it.intervallo to it.voceIndice },
            "i due turni della voce 0 restano separati: la voce 1 in mezzo impedisce l'unione",
        )
    }

    // --- AC-380 / regola 2 — turno minimo --------------------------------------------------------

    private data class CasoTurnoMinimo(val durataMs: Long, val riconosciuto: Boolean)

    private val tabellaTurnoMinimo = listOf(
        CasoTurnoMinimo(durataMs = 499, riconosciuto = false),
        CasoTurnoMinimo(durataMs = 500, riconosciuto = true),
    )

    @Test
    fun `AC-380 turno unito piu corto di 500 ms non produce chiamata ASR ne Segmento (tabella)`() {
        tabellaTurnoMinimo.forEach { caso ->
            val riconoscitore = RiconoscitoreCheRegistra()
            val turni = listOf(Turno(IntervalloMs(0, caso.durataMs), voceIndice = 0))

            val segmenti = AllineatorePerTurno(riconoscitore, vadCheNonDeveEssereChiamato)
                .allinea(tonoDiProva(caso.durataMs), turni)

            val messaggio = "${caso.durataMs} ms"
            assertEquals(if (caso.riconosciuto) 1 else 0, riconoscitore.chiamate.size, messaggio)
            assertEquals(if (caso.riconosciuto) 1 else 0, segmenti.size, messaggio)
        }
    }

    @Test
    fun `AC-380 un turno troppo corto non e' unito a un vicino solo perche' e' corto`() {
        val riconoscitore = RiconoscitoreCheRegistra()
        val turni = listOf(
            Turno(IntervalloMs(0, 499), voceIndice = 0), // troppo corto: nessuna chiamata
            Turno(IntervalloMs(1_499, 2_499), voceIndice = 0), // gap 1000 ms dal PRIMO turno: comunque separato
        )

        val segmenti = AllineatorePerTurno(riconoscitore, vadCheNonDeveEssereChiamato)
            .allinea(tonoDiProva(2_499), turni)

        assertEquals(listOf(IntervalloMs(1_499, 2_499)), segmenti.map { it.intervallo })
        assertEquals(1, riconoscitore.chiamate.size)
    }

    // --- AC-248 / regola 3 — chiamata massima, spezzatura via Vad -------------------------------

    @Test
    fun `AC-248 un turno unito di 25000 ms e' riconosciuto intero, senza Vad`() {
        val riconoscitore = RiconoscitoreCheRegistra()
        val turni = listOf(Turno(IntervalloMs(0, 25_000), voceIndice = 0))

        val segmenti = AllineatorePerTurno(riconoscitore, vadCheNonDeveEssereChiamato)
            .allinea(tonoDiProva(25_000), turni)

        assertEquals(1, riconoscitore.chiamate.size)
        assertEquals(25_000 * CAMPIONI_PER_MS, riconoscitore.chiamate.single().campioni.size)
        assertEquals(listOf(IntervalloMs(0, 25_000)), segmenti.map { it.intervallo })
    }

    @Test
    fun `AC-248 un turno di 25001 ms passa dal Vad e nessuna chiamata supera 25000 ms`() {
        val riconoscitore = RiconoscitoreCheRegistra()
        // Il Vad restituisce l'intero turno come UN SOLO intervallo di parlato: sta all'adattatore spezzarlo.
        val vad = VadFissa(listOf(IntervalloMs(0, 25_001)))
        val turni = listOf(Turno(IntervalloMs(0, 25_001), voceIndice = 0))

        val segmenti = AllineatorePerTurno(riconoscitore, vad).allinea(tonoDiProva(25_001), turni)

        assertEquals(25_001 * CAMPIONI_PER_MS, vad.chiamate.single().campioni.size, "il vad riceve il turno intero")
        assertEquals(2, riconoscitore.chiamate.size, "25001 ms -> 2 pezzi")
        riconoscitore.chiamate.forEach {
            val ms = it.campioni.size / CAMPIONI_PER_MS
            assertTrue(it.campioni.size <= 25_000 * CAMPIONI_PER_MS, "pezzo di $ms ms")
        }
        // regola 8: il Segmento resta UNO SOLO, con l'intervallo del TURNO UNITO, non dei pezzi
        assertEquals(listOf(IntervalloMs(0, 25_001)), segmenti.map { it.intervallo })
    }

    @Test
    fun `AC-248 un intervallo Vad di 50000 ms e' tagliato in due pezzi consecutivi uguali da 25000 ms`() {
        val riconoscitore = RiconoscitoreCheRegistra()
        val vad = VadFissa(listOf(IntervalloMs(0, 50_000)))
        val turni = listOf(Turno(IntervalloMs(0, 60_000), voceIndice = 0))

        AllineatorePerTurno(riconoscitore, vad).allinea(tonoDiProva(60_000), turni)

        assertEquals(2, riconoscitore.chiamate.size)
        assertEquals(
            listOf(25_000 * CAMPIONI_PER_MS, 25_000 * CAMPIONI_PER_MS),
            riconoscitore.chiamate.map { it.campioni.size },
        )
    }

    // --- AC-381 / regola 4 — pezzo minimo --------------------------------------------------------

    private val tabellaPezzoMinimo = listOf(
        CasoTurnoMinimo(durataMs = 199, riconosciuto = false),
        CasoTurnoMinimo(durataMs = 200, riconosciuto = true),
    )

    @Test
    fun `AC-381 intervallo Vad piu corto di 200 ms non produce chiamata ASR (tabella)`() {
        tabellaPezzoMinimo.forEach { caso ->
            val riconoscitore = RiconoscitoreCheRegistra()
            val vad = VadFissa(listOf(IntervalloMs(0, caso.durataMs)))
            val turni = listOf(Turno(IntervalloMs(0, 30_000), voceIndice = 0)) // > 25000 ms: passa dal Vad

            val segmenti = AllineatorePerTurno(riconoscitore, vad).allinea(tonoDiProva(30_000), turni)

            val messaggio = "${caso.durataMs} ms"
            assertEquals(if (caso.riconosciuto) 1 else 0, riconoscitore.chiamate.size, messaggio)
            assertEquals(if (caso.riconosciuto) 1 else 0, segmenti.size, messaggio)
        }
    }

    // --- AC-382 / regola 7 — testo vuoto ----------------------------------------------------------

    @Test
    fun `AC-382 testo unito vuoto o di soli spazi non produce alcun SegmentoGrezzo`() {
        val turni = listOf(Turno(IntervalloMs(0, 1_000), voceIndice = 0))

        val segmenti = AllineatorePerTurno(RiconoscitoreVuoto(), vadCheNonDeveEssereChiamato)
            .allinea(tonoDiProva(1_000), turni)

        assertEquals(emptyList(), segmenti)
    }

    @Test
    fun `AC-382 se tutti i turni sono vuoti o corti il risultato e' una lista vuota, senza eccezioni`() {
        val turni = listOf(
            Turno(IntervalloMs(0, 300), voceIndice = 0), // troppo corto (regola 2)
            Turno(IntervalloMs(2_000, 3_000), voceIndice = 1), // testo vuoto (regola 7)
        )

        val segmenti = AllineatorePerTurno(RiconoscitoreVuoto(), vadCheNonDeveEssereChiamato)
            .allinea(tonoDiProva(3_000), turni)

        assertEquals(emptyList(), segmenti)
    }

    // --- AC-383 / regole 5, 6, 8 — testo, campioni, intervallo -----------------------------------

    @Test
    fun `AC-383 il testo unisce i pezzi non vuoti con uno spazio in ordine di tempo, i token sono ignorati`() {
        val riconoscitore = RiconoscitoreCheRegistra()
        // due pezzi di parlato ordinati, entrambi <= 25000 ms: nessuna ulteriore spezzatura.
        val vad = VadFissa(listOf(IntervalloMs(0, 10_000), IntervalloMs(15_000, 26_000)))
        val turni = listOf(Turno(IntervalloMs(0, 30_000), voceIndice = 0))

        val segmenti = AllineatorePerTurno(riconoscitore, vad).allinea(tonoDiProva(30_000), turni)

        assertEquals(
            listOf(SegmentoGrezzo(0, IntervalloMs(0, 30_000), "parola1 parola2")),
            segmenti,
            "regola 8: l'intervallo resta quello del turno unito, non dei pezzi del Vad",
        )
    }

    @Test
    fun `AC-383 i campioni passati al riconoscitore sono tagliati esattamente ai limiti del turno, senza padding`() {
        val riconoscitore = RiconoscitoreCheRegistra()
        val turni = listOf(Turno(IntervalloMs(1_000, 2_500), voceIndice = 0)) // 1500 ms, <= 25000: chiamata intera

        AllineatorePerTurno(riconoscitore, vadCheNonDeveEssereChiamato).allinea(tonoDiProva(3_000), turni)

        assertEquals(1, riconoscitore.chiamate.size)
        assertEquals(1_500 * CAMPIONI_PER_MS, riconoscitore.chiamate.single().campioni.size)
    }

    // --- AC-384 / regola 10 — ordine e determinismo -----------------------------------------------

    @Test
    fun `AC-384 l'uscita e' ordinata per inizio, voceIndice, fine ed e' deterministica`() {
        val turni = listOf(
            Turno(IntervalloMs(5_000, 6_000), voceIndice = 1),
            Turno(IntervalloMs(0, 1_000), voceIndice = 2),
            Turno(IntervalloMs(0, 1_000), voceIndice = 1),
            Turno(IntervalloMs(2_000, 3_000), voceIndice = 0),
        )
        val campioni = tonoDiProva(6_000)

        val primo = AllineatorePerTurno(RiconoscitoreCheRegistra(), vadCheNonDeveEssereChiamato)
            .allinea(campioni, turni)
        val secondo = AllineatorePerTurno(RiconoscitoreCheRegistra(), vadCheNonDeveEssereChiamato)
            .allinea(campioni, turni)

        assertEquals(
            listOf(
                IntervalloMs(0, 1_000) to 1,
                IntervalloMs(0, 1_000) to 2,
                IntervalloMs(2_000, 3_000) to 0,
                IntervalloMs(5_000, 6_000) to 1,
            ),
            primo.map { it.intervallo to it.voceIndice },
        )
        assertEquals(primo, secondo, "stesso input -> stessa lista")
    }

    private companion object {
        /** Un turno <= 25000 ms non deve mai chiamare il Vad (regola 3): lo fa esplodere se capita. */
        val vadCheNonDeveEssereChiamato = VadCheNonDeveEssereChiamato()
    }
}

/** Registra ogni chiamata (i campioni ricevuti) e restituisce un testo non vuoto e distinto per ognuna. */
private class RiconoscitoreCheRegistra : RiconoscitoreParlato {
    val chiamate = mutableListOf<CampioniAudio>()

    override fun riconosci(c: CampioniAudio): Riconoscimento {
        chiamate += c
        return Riconoscimento("parola${chiamate.size}", listOf(Token("ignorato", IntervalloMs(0, 1))))
    }
}

/** Testo di soli spazi: modella un pezzo che l'ASR sente ma non trascrive (regola 7). */
private class RiconoscitoreVuoto : RiconoscitoreParlato {
    override fun riconosci(c: CampioniAudio): Riconoscimento = Riconoscimento("   ", null)
}

/** Restituisce SEMPRE gli [intervalli] dati (relativi ai campioni ricevuti), qualunque essi siano; li registra. */
private class VadFissa(private val intervalli: List<IntervalloMs>) : Vad {
    val chiamate = mutableListOf<CampioniAudio>()

    override fun parlato(c: CampioniAudio): List<IntervalloMs> {
        chiamate += c
        return intervalli
    }
}

/** Chiamarlo e' un errore del produttore: usato dove il turno e' <= 25000 ms e il Vad non deve intervenire. */
private class VadCheNonDeveEssereChiamato : Vad {
    override fun parlato(c: CampioniAudio): List<IntervalloMs> = error("il Vad non doveva essere chiamato")
}
