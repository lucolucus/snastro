package snastro.avvio.r2

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import snastro.avvio.r1.attendiFinche
import snastro.kernel.Esito
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.parlanti.applicazione.letture.PianoRiassegnazione
import snastro.parlanti.applicazione.letture.SpostamentoProposto
import snastro.parlanti.dominio.ErroreParlanti
import snastro.trascrizione.applicazione.comandi.RiassegnaSegmenti
import snastro.trascrizione.dominio.ErroreTrascrizione
import snastro.trascrizione.dominio.SpostamentoSegmento
import snastro.ui.registrazione.AzioniSomiglianzaContratto
import snastro.ui.registrazione.ErroreSomiglianzaUi
import snastro.ui.registrazione.GruppoSpostamenti
import snastro.ui.registrazione.StatoSomiglianza
import java.time.Clock
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.ContinuationInterceptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val REG = RegistrazioneId("id-1")

/** The plan whose grouping is [gruppi]: each group expanded into `frasi` moves of distinct Segmenti. */
private fun pianoDi(id: RegistrazioneId, gruppi: List<GruppoSpostamenti>, incerte: Int): PianoRiassegnazione {
    var n = 0
    val spostamenti = gruppi.flatMap { g ->
        List(g.frasi) {
            n++
            SpostamentoProposto(SegmentoId(n), g.da, g.a, IntervalloMs(n * 1_000L, n * 1_000L + 900))
        }
    }
    return PianoRiassegnazione(id, spostamenti.sortedBy { it.intervallo.inizioMs }, incerte)
}

/** D2: the per-project glue honours the consumer's [snastro.ui.registrazione.AzioniSomiglianza] contract. */
class AzioniSomiglianzaProgettoContrattoTest : AzioniSomiglianzaContratto() {
    override fun con(progetto: CoroutineScope, clock: Clock, scenario: ScenarioSomiglianza): SondaSomiglianza {
        val applicazioni = AtomicInteger()
        val bg = progetto.coroutineContext[ContinuationInterceptor] as CoroutineDispatcher
        val porta = AzioniSomiglianzaProgetto(
            progetto,
            bg,
            clock,
            { id, _ -> Esito.Ok(pianoDi(id, scenario.gruppi, scenario.incerte)) },
            {
                applicazioni.incrementAndGet()
                Esito.Ok(Unit)
            },
        )
        return SondaSomiglianza(porta) { applicazioni.get() }
    }
}

/**
 * AC-537 / AC-549 on real threads: the computation runs on the background dispatcher through
 * `runInterruptible` (an [annulla][AzioniSomiglianzaProgetto.annulla] interrupts it, nothing applied), the
 * HELD plan is what `applica` sends 1:1, the plan is dropped on annulla / Ritrascrivi / project close.
 */
class AzioniSomiglianzaProgettoTest {
    private val progetto = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val applicati = CopyOnWriteArrayList<RiassegnaSegmenti>()
    private var esitoApplica: Esito<Unit> = Esito.Ok(Unit)

    private fun porta(calcola: (RegistrazioneId, (Int, Int) -> Unit) -> Esito<PianoRiassegnazione>) =
        AzioniSomiglianzaProgetto(progetto, Dispatchers.IO, Clock.systemUTC(), calcola) {
            applicati += it
            esitoApplica
        }

    private val piano = pianoDi(
        REG,
        listOf(GruppoSpostamenti(VoceId(3), VoceId(1), 2), GruppoSpostamenti(VoceId(4), VoceId(2), 1)),
        incerte = 1,
    )

    private fun AzioniSomiglianzaProgetto.inAnteprima(): AzioniSomiglianzaProgetto {
        calcola(REG)
        attendiFinche(messaggio = "anteprima") { stato.value[REG] is StatoSomiglianza.Anteprima }
        return this
    }

    @Test
    fun `AC-537 il calcolo pubblica l avanzamento, finisce in Anteprima, un secondo calcola e ignorato`() {
        val chiamate = AtomicInteger()
        val via = CountDownLatch(1)
        val p = porta { id, progresso ->
            chiamate.incrementAndGet()
            progresso(1, 3)
            via.await(5, TimeUnit.SECONDS)
            progresso(3, 3)
            Esito.Ok(piano.copy(registrazioneId = id))
        }
        p.calcola(REG)
        attendiFinche(messaggio = "avanzamento") { (p.stato.value[REG] as? StatoSomiglianza.InCorso)?.fatti == 1 }
        p.calcola(REG)
        via.countDown()
        attendiFinche(messaggio = "anteprima") { p.stato.value[REG] is StatoSomiglianza.Anteprima }
        p.calcola(REG)
        assertEquals(1, chiamate.get())
        assertEquals(
            StatoSomiglianza.Anteprima(
                listOf(GruppoSpostamenti(VoceId(3), VoceId(1), 2), GruppoSpostamenti(VoceId(4), VoceId(2), 1)),
                1,
            ),
            p.stato.value[REG],
        )
        assertTrue(applicati.isEmpty())
    }

    @Test
    fun `AC-537 annulla interrompe il calcolo in attesa, nessuno stato resta e nulla e applicato`() {
        val interrotto = AtomicBoolean(false)
        val p = porta { _, _ ->
            try {
                Thread.sleep(10_000)
            } catch (e: InterruptedException) {
                interrotto.set(true)
                throw e
            }
            Esito.Ok(piano)
        }
        p.calcola(REG)
        attendiFinche(messaggio = "in corso") { p.stato.value[REG] is StatoSomiglianza.InCorso }
        Thread.sleep(50)
        p.annulla(REG)
        attendiFinche(messaggio = "interruzione") { interrotto.get() }
        assertNull(p.stato.value[REG])
        p.applica(REG)
        assertTrue(applicati.isEmpty())
    }

    @Test
    fun `AC-549 applica invia esattamente il piano tenuto 1 a 1, una volta sola, poi il piano non c e piu`() {
        val calcoli = AtomicInteger()
        val p = porta { _, _ ->
            calcoli.incrementAndGet()
            Esito.Ok(piano)
        }.inAnteprima()
        p.applica(REG)
        p.applica(REG)
        attendiFinche(messaggio = "esito") { p.stato.value[REG] is StatoSomiglianza.Esito }
        assertEquals(StatoSomiglianza.Esito(3, 1), p.stato.value[REG])
        assertEquals(1, calcoli.get())
        val atteso = piano.spostamenti.map { SpostamentoSegmento(it.segmentoId, it.da, it.a, it.intervallo) }
        assertEquals(listOf(RiassegnaSegmenti(REG, atteso)), applicati.toList())
        p.applica(REG)
        assertEquals(1, applicati.size)
    }

    @Test
    fun `AC-549 errori mappati, TrascrittoCambiato scarta il piano e applica poi non invia nulla`() {
        esitoApplica = Esito.Errore(ErroreTrascrizione.TrascrittoCambiato(REG))
        val p = porta { _, _ -> Esito.Ok(piano) }.inAnteprima()
        p.applica(REG)
        attendiFinche(messaggio = "errore") { p.stato.value[REG] is StatoSomiglianza.Errore }
        assertEquals(StatoSomiglianza.Errore(ErroreSomiglianzaUi.TrascrittoCambiato), p.stato.value[REG])
        p.applica(REG)
        assertEquals(1, applicati.size)

        val pochi = porta { id, _ -> Esito.Errore(ErroreParlanti.RiferimentiInsufficienti(id)) }
        pochi.calcola(REG)
        attendiFinche(messaggio = "riferimenti") { pochi.stato.value[REG] is StatoSomiglianza.Errore }
        assertEquals(StatoSomiglianza.Errore(ErroreSomiglianzaUi.RiferimentiInsufficienti), pochi.stato.value[REG])

        val rotto = porta { id, _ -> Esito.Errore(ErroreParlanti.TrascrittoNonTrovato(id)) }
        rotto.calcola(REG)
        attendiFinche(messaggio = "altro") { rotto.stato.value[REG] is StatoSomiglianza.Errore }
        assertIs<ErroreSomiglianzaUi.Altro>((rotto.stato.value[REG] as StatoSomiglianza.Errore).errore)
    }

    @Test
    fun `AC-549 con N zero applica non invia nulla, annulla e Ritrascrivi scartano l anteprima`() {
        val vuota = porta { id, _ -> Esito.Ok(PianoRiassegnazione(id, emptyList(), 2)) }.inAnteprima()
        vuota.applica(REG)
        assertIs<StatoSomiglianza.Anteprima>(vuota.stato.value[REG])

        val annullata = porta { _, _ -> Esito.Ok(piano) }.inAnteprima()
        annullata.annulla(REG)
        annullata.applica(REG)
        val scartata = porta { _, _ -> Esito.Ok(piano) }.inAnteprima()
        scartata.scarta(REG)
        scartata.applica(REG)
        assertNull(scartata.stato.value[REG])
        assertTrue(applicati.isEmpty())
    }

    @Test
    fun `AC-537 AC-549 chiudere il progetto annulla il calcolo, lo attende e scarta ogni piano`() {
        val interrotto = AtomicBoolean(false)
        val p = porta { id, _ ->
            if (id == REG) {
                Esito.Ok(piano)
            } else {
                try {
                    Thread.sleep(10_000)
                } catch (e: InterruptedException) {
                    interrotto.set(true)
                    throw e
                }
                Esito.Ok(piano)
            }
        }.inAnteprima()
        val altra = RegistrazioneId("id-2")
        p.calcola(altra)
        attendiFinche(messaggio = "in corso") { p.stato.value[altra] is StatoSomiglianza.InCorso }
        Thread.sleep(50)
        progetto.cancel()
        runBlocking { checkNotNull(progetto.coroutineContext[Job]).join() }
        assertTrue(interrotto.get())
        assertTrue(p.stato.value.isEmpty())
        p.applica(REG)
        assertTrue(applicati.isEmpty())
    }
}
