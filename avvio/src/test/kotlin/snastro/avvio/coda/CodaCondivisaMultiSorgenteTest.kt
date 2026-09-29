package snastro.avvio.coda

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import snastro.kernel.RegistrazioneId
import snastro.supporto.test.conScopeDiProva
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [CodaCondivisa] over N sources (ADR 0023, AC-S56..S63, carry-over 2): the two-kind (Elaborazione,
 * Riassunto) mechanics — total order, the claim's bound, the holding rule, per-source recovery and
 * targeted cancellation — over fake [FonteCoda]s, split from [CodaCondivisaTest] (the ported,
 * single-source AC-233..314 scenarios, unchanged behaviour, AC-S55).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CodaCondivisaMultiSorgenteTest {
    private fun sorgenteFinta(tipo: TipoElementoCoda, ordine: MutableList<String>): SorgenteFinta =
        SorgenteFinta(tipo, ordine)

    @Test
    fun `AC-S56 FIFO stretta tra E e R, E1 R2 E3 R4 girano in questo ordine`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val ordine = mutableListOf<String>()
        val e = sorgenteFinta(TipoElementoCoda.ELABORAZIONE, ordine)
        val r = sorgenteFinta(TipoElementoCoda.RIASSUNTO, ordine)
        e.aggiungi("e1", "reg-e1", t(1))
        r.aggiungi("r2", "reg-r2", t(2))
        e.aggiungi("e3", "reg-e3", t(3))
        r.aggiungi("r4", "reg-r4", t(4))

        codaAvviata(
            scope = backgroundScope,
            fonti = listOf(e.fonte(), r.fonte()),
            dispatcherSingoloThread = dispatcher,
        )
        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(listOf("e1", "r2", "e3", "r4"), ordine)
    }

    @Test
    fun `AC-S56 un elemento aggiunto mentre un altro gira prende il suo posto per istante`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val ordine = mutableListOf<String>()
        val e = sorgenteFinta(TipoElementoCoda.ELABORAZIONE, ordine)
        val r = sorgenteFinta(TipoElementoCoda.RIASSUNTO, ordine)
        e.aggiungi("e1", "reg-e1", t(1))
        r.aggiungi("r4", "reg-r4", t(4))

        codaAvviata(
            scope = backgroundScope,
            fonti = listOf(e.fonte(), r.fonte()),
            dispatcherSingoloThread = dispatcher,
        )
        runCurrent() // il primo tentativo e' immediato (nessun advanceTimeBy): gira solo e1, r4 resta in attesa
        assertEquals(listOf("e1"), ordine)

        e.aggiungi("e2", "reg-e2", t(2)) // prima di r4: deve passare davanti
        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(listOf("e1", "e2", "r4"), ordine, "e2 prende il suo posto per istante, non va in coda dopo r4")
    }

    @Test
    fun `AC-S57 a parita di millisecondo l Elaborazione gira prima del Riassunto`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val ordine = mutableListOf<String>()
        val e = sorgenteFinta(TipoElementoCoda.ELABORAZIONE, ordine)
        val r = sorgenteFinta(TipoElementoCoda.RIASSUNTO, ordine)
        val stessoIstante = t(1)
        r.aggiungi("r1", "reg-r1", stessoIstante)
        e.aggiungi("e1", "reg-e1", stessoIstante)

        codaAvviata(
            scope = backgroundScope,
            fonti = listOf(e.fonte(), r.fonte()),
            dispatcherSingoloThread = dispatcher,
        )
        advanceTimeBy(5_000)
        runCurrent()

        assertEquals(listOf("e1", "r1"), ordine, "a parita' di istante l'Elaborazione (ordinal 0) precede il Riassunto")
    }

    @Test
    @Suppress("MaxLineLength", "MaximumLineLength", "ArgumentListWrapping") // the test name alone crosses 120 columns
    fun `carry-over 2 - a parita di istante la posizione conta prima l Elaborazione poi il Riassunto`() = conScopeDiProva { scope ->
        val stessoIstante = t(1)
        val eFonte = FonteCoda(
            tipo = TipoElementoCoda.ELABORAZIONE,
            teste = { esclusi -> if ("e1" in esclusi) null else ElementoInCoda("e1", "reg-e1", stessoIstante) },
            prossima = { _, _ -> RisultatoTentativo.Nessuno },
            ultimaTentata = { null },
            recupera = {},
            trattenuta = { false },
        )
        val rFonte = FonteCoda(
            tipo = TipoElementoCoda.RIASSUNTO,
            teste = { esclusi -> if ("r1" in esclusi) null else ElementoInCoda("r1", "reg-r1", stessoIstante) },
            prossima = { _, _ -> RisultatoTentativo.Nessuno },
            ultimaTentata = { null },
            recupera = {},
            trattenuta = { false },
        )
        val coda = codaAvviata(scope = scope, fonti = listOf(eFonte, rFonte))
        scope.cancel() // solo istantanea() e' esercitato qui: il worker non serve
        coda.fermaEAttendi(1_000)

        val istantanea = coda.istantanea()

        val messaggio = "a parita' di istante l'Elaborazione e' 1a, il Riassunto 2o, mai la stessa posizione"
        assertEquals(mapOf(RegistrazioneId("reg-e1") to 1), istantanea.elaborazioni, messaggio)
        assertEquals(mapOf(RegistrazioneId("reg-r1") to 2), istantanea.riassunti, messaggio)
    }

    @Test
    fun `AC-S58 il limite passato alla claim e l istante della testa dell altra fonte, null se vuota`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val limitiE = mutableListOf<Instant?>()
        val limitiR = mutableListOf<Instant?>()
        val avviate = mutableListOf<String>()
        var e1Presente = true
        var r2Presente = true
        val eFonte = FonteCoda(
            tipo = TipoElementoCoda.ELABORAZIONE,
            teste = { if (e1Presente) ElementoInCoda("e1", "reg-e1", t(1)) else null },
            prossima = { _, limite ->
                limitiE += limite
                if (e1Presente) {
                    e1Presente = false
                    avviate += "e1"
                    RisultatoTentativo.Avviata("e1")
                } else {
                    RisultatoTentativo.Nessuno
                }
            },
            ultimaTentata = { null },
            recupera = {},
            trattenuta = { false },
        )
        val rFonte = FonteCoda(
            tipo = TipoElementoCoda.RIASSUNTO,
            teste = { if (r2Presente) ElementoInCoda("r2", "reg-r2", t(2)) else null },
            prossima = { _, limite ->
                limitiR += limite
                if (r2Presente) {
                    r2Presente = false
                    avviate += "r2"
                    RisultatoTentativo.Avviata("r2")
                } else {
                    RisultatoTentativo.Nessuno
                }
            },
            ultimaTentata = { null },
            recupera = {},
            trattenuta = { false },
        )

        codaAvviata(scope = backgroundScope, fonti = listOf(eFonte, rFonte), dispatcherSingoloThread = dispatcher)
        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(listOf("e1", "r2"), avviate)
        assertEquals(listOf<Instant?>(t(2)), limitiE, "la claim di E riceve nonDopo = la testa di R")
        assertEquals(listOf<Instant?>(null), limitiR, "la claim di R riceve primaDi = null: E e' ormai vuota")
    }

    @Test
    fun `rework FAIL 5 - AC-S57 due elementi della stessa specie a parita di istante mantengono ordine`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val ordine = mutableListOf<String>()
        val stessoIstante = t(1)
        var unoPresente = true
        var duePresente = true
        // due fonti Elaborazione DISTINTE, apposta: solo cosi' possono avere DUE teste simultanee della
        // stessa specie (una fonte vera ne espone una sola alla volta) allo stesso istante — il caso che
        // esercita davvero il terzo livello del confronto totale, l'id.
        val fonteUno = FonteCoda(
            tipo = TipoElementoCoda.ELABORAZIONE,
            teste = { if (unoPresente) ElementoInCoda("id-b", "reg-b", stessoIstante) else null },
            prossima = { _, _ ->
                unoPresente = false
                ordine += "id-b"
                RisultatoTentativo.Avviata("id-b")
            },
            ultimaTentata = { null },
            recupera = {},
            trattenuta = { false },
        )
        val fonteDue = FonteCoda(
            tipo = TipoElementoCoda.ELABORAZIONE,
            teste = { if (duePresente) ElementoInCoda("id-a", "reg-a", stessoIstante) else null },
            prossima = { _, _ ->
                duePresente = false
                ordine += "id-a"
                RisultatoTentativo.Avviata("id-a")
            },
            ultimaTentata = { null },
            recupera = {},
            trattenuta = { false },
        )

        codaAvviata(scope = backgroundScope, fonti = listOf(fonteUno, fonteDue), dispatcherSingoloThread = dispatcher)
        advanceTimeBy(5_000)
        runCurrent()

        assertEquals(
            listOf("id-a", "id-b"),
            ordine,
            "a parita' di istante e di specie, l'id decide: \"id-a\" prima di \"id-b\"",
        )
    }

    @Test
    fun `rework FAIL 2 - AC-S58 R prima di E non vuota riceve comunque il limite dalla testa di E`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val limitiR = mutableListOf<Instant?>()
        var r1Presente = true
        val eFonte = FonteCoda(
            tipo = TipoElementoCoda.ELABORAZIONE,
            // resta sempre presente: E non e' mai claimata in questo test, solo il suo limite conta
            teste = { ElementoInCoda("e2", "reg-e2", t(2)) },
            prossima = { _, _ -> RisultatoTentativo.Nessuno },
            ultimaTentata = { null },
            recupera = {},
            trattenuta = { false },
        )
        val rFonte = FonteCoda(
            tipo = TipoElementoCoda.RIASSUNTO,
            teste = { if (r1Presente) ElementoInCoda("r1", "reg-r1", t(1)) else null },
            prossima = { _, limite ->
                limitiR += limite
                r1Presente = false
                RisultatoTentativo.Avviata("r1")
            },
            ultimaTentata = { null },
            recupera = {},
            trattenuta = { false },
        )

        codaAvviata(scope = backgroundScope, fonti = listOf(eFonte, rFonte), dispatcherSingoloThread = dispatcher)
        runCurrent()

        assertEquals(
            listOf<Instant?>(t(2)),
            limitiR,
            "R parte prima (t1<t2, gira per prima) ma riceve comunque primaDi = la testa di E, mai null",
        )
    }

    @Test
    fun `rework FAIL 3 - AC-S59 con due fonti, la testa di E sparisce e R in attesa gira al suo turno`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val ordine = mutableListOf<String>()
        val vistaE = AtomicInteger(0)
        val eFonte = FonteCoda(
            tipo = TipoElementoCoda.ELABORAZIONE,
            // il picco la vede una sola volta (e1-sparito, t1 < t2 di R): poi e' davvero sparita per sempre
            teste = { if (vistaE.getAndIncrement() == 0) ElementoInCoda("e1-sparito", "reg-e1", t(1)) else null },
            prossima = { _, _ -> RisultatoTentativo.Nessuno }, // la claim non trova piu' nulla: era gia' sparita
            ultimaTentata = { null },
            recupera = {},
            trattenuta = { false },
        )
        val rFonte = FonteCoda(
            tipo = TipoElementoCoda.RIASSUNTO,
            teste = { esclusi -> if ("r2" in esclusi) null else ElementoInCoda("r2", "reg-r2", t(2)) },
            prossima = { _, _ ->
                ordine += "r2"
                RisultatoTentativo.Avviata("r2")
            },
            ultimaTentata = { null },
            recupera = {},
            trattenuta = { false },
        )

        codaAvviata(scope = backgroundScope, fonti = listOf(eFonte, rFonte), dispatcherSingoloThread = dispatcher)
        runCurrent() // nessun advanceTimeBy: la rivalutazione dopo Nessuno e' immediata (AC-S59)

        assertEquals(listOf("r2"), ordine, "r2 gira SUBITO al suo turno dopo la rivalutazione, mai scavalcato")
    }

    @Test
    fun `rework FAIL 4 - AC-S61 un id bloccato su R viene escluso solo li, l id omonimo su E continua`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val tentativiR = AtomicInteger(0)
        val bloccate = mutableListOf<String>()
        val eAvviata = AtomicBoolean(false)
        val recenteAvviata = AtomicBoolean(false)

        // "dup" appare su ENTRAMBE le fonti, apposta: l'esclusione di R su "dup" non deve toccare l'id
        // OMONIMO di E (le esclusioni sono per fonte, mai per id globale).
        val eFonte = fonteEConIdOmonimo(eAvviata)
        val rFonte = fonteRConDupBloccatoERecente(tentativiR, recenteAvviata)

        codaAvviata(
            scope = backgroundScope,
            fonti = listOf(eFonte, rFonte),
            segnalaBloccato = { bloccate += it },
            dispatcherSingoloThread = dispatcher,
        )
        advanceTimeBy(15_000)
        runCurrent()

        assertEquals(3, tentativiR.get(), "tre rifiuti su R prima dell'esclusione")
        assertEquals(listOf("dup"), bloccate, "segnalato una volta sola, per la fonte R")
        assertTrue(eAvviata.get(), "l'id \"dup\" su E non e' toccato dall'esclusione di \"dup\" su R")
        assertTrue(recenteAvviata.get(), "gli altri elementi di R continuano a scorrere dopo l'esclusione")
    }

    @Test
    fun `AC-S59 testa sparita tra il picco e la richiesta, la coda rivaluta subito senza attendere`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dettoTeste = AtomicInteger(0)
        val dettoProssima = AtomicInteger(0)
        val ordine = mutableListOf<String>()

        val eFonte = FonteCoda(
            tipo = TipoElementoCoda.ELABORAZIONE,
            teste = { esclusi ->
                if (dettoTeste.getAndIncrement() == 0) {
                    ElementoInCoda("e1-sparito", "reg-1", t(1)) // il picco vede una testa...
                } else if ("e2" in esclusi) {
                    null
                } else {
                    ElementoInCoda("e2", "reg-2", t(2)) // ... che alla claim non c'e' piu': e2 e' la vera testa
                }
            },
            prossima = { _, _ ->
                if (dettoProssima.getAndIncrement() == 0) {
                    RisultatoTentativo.Nessuno // e1-sparito: la claim non trova nulla di idoneo
                } else {
                    ordine += "e2"
                    RisultatoTentativo.Avviata("e2")
                }
            },
            ultimaTentata = { null },
            recupera = {},
            trattenuta = { false },
        )

        codaAvviata(scope = backgroundScope, fonti = listOf(eFonte), dispatcherSingoloThread = dispatcher)
        runCurrent() // NESSUN advanceTimeBy: la rivalutazione e' immediata, non un poll a 1s

        assertEquals(listOf("e2"), ordine, "il prossimo elemento gira SUBITO, senza aspettare il poll")
    }

    @Test
    fun `AC-S60 in attesa dei modelli tutta la coda resta ferma, anche un Riassunto dietro`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val ordine = mutableListOf<String>()
        val e = sorgenteFinta(TipoElementoCoda.ELABORAZIONE, ordine)
        val r = sorgenteFinta(TipoElementoCoda.RIASSUNTO, ordine)
        e.aggiungi("e1", "reg-e1", t(1))
        r.aggiungi("r2", "reg-r2", t(2))
        e.pronta = false

        codaAvviata(
            scope = backgroundScope,
            fonti = listOf(e.fonte(), r.fonte()),
            dispatcherSingoloThread = dispatcher,
        )
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(emptyList(), ordine, "niente gira, nemmeno il Riassunto dietro la testa trattenuta")

        e.pronta = true
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(listOf("e1", "r2"), ordine, "la coda riparte da sola, in ordine, quando i modelli sono pronti")
    }

    // AC-S60's second half ("a Riassunto head is never held for the LLM model") is a WIRING choice, not
    // a CodaCondivisa mechanism: the composition that binds the Riassunto source (ModuloSintesi)
    // wires its own `trattenuta` to a constant `false`, so this dispatcher's single, tipo-agnostic
    // `if (fonteScelta.trattenuta()) hold` never treats it specially — nothing to prove here beyond the
    // holding test above, which is deliberately tipo-agnostic on purpose.

    @Test
    fun `AC-S61 il recupero gira per ogni fonte, all avvio e di nuovo dopo una fuga di una sola`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val recuperi = mutableListOf<String>()
        val primaVolta = AtomicBoolean(true)
        val eFonte = FonteCoda(
            tipo = TipoElementoCoda.ELABORAZIONE,
            teste = { esclusi -> if ("e1" in esclusi) null else ElementoInCoda("e1", "reg-1", t(1)) },
            prossima = { _, _ ->
                if (primaVolta.compareAndSet(true, false)) {
                    throw OutOfMemoryError("di prova")
                } else {
                    RisultatoTentativo.Avviata("e1")
                }
            },
            ultimaTentata = { "e1" },
            recupera = { recuperi += "E" },
            trattenuta = { false },
        )
        val rFonte = FonteCoda(
            tipo = TipoElementoCoda.RIASSUNTO,
            teste = { null },
            prossima = { _, _ -> RisultatoTentativo.Nessuno },
            ultimaTentata = { null },
            recupera = { recuperi += "R" },
            trattenuta = { false },
        )

        codaAvviata(scope = backgroundScope, fonti = listOf(eFonte, rFonte), dispatcherSingoloThread = dispatcher)
        advanceTimeBy(5_000)
        runCurrent()

        assertEquals(listOf("E", "R", "E", "R"), recuperi, "all'avvio, poi ENTRAMBE dopo la fuga di E, non solo E")
    }

    /** FAIL 4: una sola testa "dup" (t2), mai esclusa (E non fallisce mai la sua claim). */
    private fun fonteEConIdOmonimo(avviata: AtomicBoolean): FonteCoda = FonteCoda(
        tipo = TipoElementoCoda.ELABORAZIONE,
        teste = { esclusi ->
            if ("dup" in esclusi || avviata.get()) null else ElementoInCoda("dup", "reg-e-dup", t(2))
        },
        prossima = { esclusi, _ ->
            if ("dup" in esclusi) {
                RisultatoTentativo.Nessuno
            } else {
                avviata.set(true)
                RisultatoTentativo.Avviata("dup")
            }
        },
        ultimaTentata = { null },
        recupera = {},
        trattenuta = { false },
    )

    /** FAIL 4: "dup" (t1) sempre rifiutata finche' non esclusa, poi "recente" (t3) parte. */
    private fun fonteRConDupBloccatoERecente(tentativi: AtomicInteger, recenteAvviata: AtomicBoolean): FonteCoda =
        FonteCoda(
            tipo = TipoElementoCoda.RIASSUNTO,
            teste = { esclusi ->
                when {
                    "dup" !in esclusi -> ElementoInCoda("dup", "reg-r-dup", t(1))
                    !recenteAvviata.get() -> ElementoInCoda("recente", "reg-r-recente", t(3))
                    else -> null
                }
            },
            prossima = { esclusi, _ ->
                when {
                    "dup" !in esclusi -> {
                        tentativi.incrementAndGet()
                        RisultatoTentativo.Rifiutata("dup")
                    }
                    !recenteAvviata.get() -> {
                        recenteAvviata.set(true)
                        RisultatoTentativo.Avviata("recente")
                    }
                    else -> RisultatoTentativo.Nessuno
                }
            },
            ultimaTentata = { null },
            recupera = {},
            trattenuta = { false },
        )

    private fun t(secondi: Long): Instant = Instant.parse("2026-09-23T10:00:00Z").plusSeconds(secondi)
}

/**
 * A tiny in-memory single-source FIFO backing a [FonteCoda] for the multi-source tests: [aggiungi]
 * queues an item, claiming it removes it and appends its id to the SHARED [ordine] (so tests can
 * assert the GLOBAL run order across two [SorgenteFinta]s). [pronta] backs [FonteCoda.trattenuta].
 */
private class SorgenteFinta(private val tipo: TipoElementoCoda, private val ordine: MutableList<String>) {
    private data class Voce(val id: String, val registrazioneId: String, val istante: Instant)

    private val inAttesa = mutableListOf<Voce>()
    var pronta: Boolean = true

    fun aggiungi(id: String, registrazioneId: String, istante: Instant) {
        inAttesa += Voce(id, registrazioneId, istante)
    }

    private fun testaIdonea(esclusi: Set<String>): Voce? =
        inAttesa.filter { it.id !in esclusi }.minByOrNull { it.istante }

    fun fonte(): FonteCoda = FonteCoda(
        tipo = tipo,
        teste = { esclusi -> testaIdonea(esclusi)?.let { ElementoInCoda(it.id, it.registrazioneId, it.istante) } },
        prossima = { esclusi, limite ->
            val testa = testaIdonea(esclusi)
            when {
                testa == null -> RisultatoTentativo.Nessuno
                limite != null && testa.istante > limite -> RisultatoTentativo.Nessuno
                else -> {
                    inAttesa.remove(testa)
                    ordine += testa.id
                    RisultatoTentativo.Avviata(testa.id)
                }
            }
        },
        ultimaTentata = { null },
        recupera = {},
        trattenuta = { !pronta },
    )
}
