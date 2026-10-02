package snastro.parlanti.adattatori.eventi

import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import snastro.kernel.CampioniAudio
import snastro.kernel.DispatcherEventiInMemoria
import snastro.kernel.ErroreDiProva
import snastro.kernel.Esito
import snastro.kernel.EventoPubblicato
import snastro.kernel.IncontroId
import snastro.kernel.IntervalloMs
import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.SegmentoRef
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.atteso
import snastro.kernel.unIncontroDi
import snastro.kernel.unicaParteDi
import snastro.parlanti.applicazione.comandi.RiallineaImpronte
import snastro.parlanti.applicazione.comandi.RiallineaImpronteServizio
import snastro.parlanti.applicazione.eventi.ImpronteRiallineate
import snastro.parlanti.applicazione.porte.DecodificatoreAudio
import snastro.parlanti.applicazione.porte.DecodificatoreAudioFinta
import snastro.parlanti.applicazione.porte.EstrattoreImpronta
import snastro.parlanti.applicazione.porte.EstrattoreImprontaFinta
import snastro.parlanti.applicazione.porte.LettoreVociFinta
import snastro.parlanti.applicazione.porte.ParlanteRepositoryFinta
import snastro.parlanti.applicazione.porte.VoceVista
import snastro.parlanti.applicazione.porte.lettoreVociDiUnicheParti
import snastro.parlanti.applicazione.porte.ogniRegistrazioneNota
import snastro.parlanti.dominio.Impronta
import snastro.parlanti.dominio.ImprontaVocale
import snastro.parlanti.dominio.Nome
import snastro.parlanti.dominio.Parlante
import snastro.parlanti.dominio.SorgenteImpronta
import snastro.parlanti.dominio.TipoParlante
import snastro.supporto.RitentaConBackoff
import snastro.supporto.Segnalazione
import snastro.trascrizione.applicazione.eventi.SegmentoRiassegnato
import snastro.trascrizione.applicazione.eventi.VoceDivisa
import snastro.trascrizione.applicazione.eventi.VociUnite
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Tests of [AbbonatoRiallineamentoImpronte]: AC-304..AC-307 (behaviour, unchanged) and AC-C50..AC-C53
 * (its retry mechanics on [RitentaConBackoff], `a3-ritenta-parlanti`). Virtual time only
 * ([StandardTestDispatcher] + [TestCoroutineScheduler], `advanceUntilIdle`) — no real sleeps: every
 * fault injected below is bounded (a finite number of failures), so no test relies on cancelling a
 * never-converging key to escape a hanging `advanceUntilIdle`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AbbonatoRiallineamentoImpronteTest {

    /**
     * One test's wiring: a real [DispatcherEventiInMemoria] over [ParlanteRepositoryFinta] (the
     * `Ripristinabile` "in-memory database", `UnitaDiLavoroFinta`), a real [RiallineaImpronteServizio]
     * over [voci]/[estrattore]/[decodificatore], and the [AbbonatoRiallineamentoImpronte] under test
     * (registered as the composition registers it, then started) — all sharing [scheduler]'s virtual
     * clock and reporting through [segnalazioni].
     */
    @Suppress("LongParameterList") // one parameter per collaborator these tests vary (AC-304..AC-307/AC-C50..C53)
    private class Ambiente(
        scheduler: TestCoroutineScheduler,
        voci: Map<RegistrazioneId, List<VoceVista>> = emptyMap(),
        estrattore: EstrattoreImpronta? = null,
        decodificatore: DecodificatoreAudio? = null,
        val segnalazioni: SegnalazioniRegistrate = SegnalazioniRegistrate(),
        gestore: CoroutineExceptionHandler? = null,
        decoratore: (RiallineaImpronteServizio) -> RiallineaImpronteServizio = { it },
    ) {
        val parlanti = ParlanteRepositoryFinta()
        private val transazioni = UnitaDiLavoroFinta(parlanti)
        val dispatcher = DispatcherEventiInMemoria(transazioni)
        val riallinea = decoratore(
            RiallineaImpronteServizio(
                dispatcher.unitaDiLavoro,
                lettoreVociDiUnicheParti(voci),
                ogniRegistrazioneNota(),
                parlanti,
                decodificatore ?: DecodificatoreAudioFinta(unitaDiLavoro = transazioni),
                estrattore ?: EstrattoreImprontaFinta(unitaDiLavoro = transazioni),
                dispatcher,
            ),
        )
        val scope = CoroutineScope(StandardTestDispatcher(scheduler) + (gestore ?: EmptyCoroutineContext))

        init {
            // ADR 0030 §1 (AC-C67): a value the composition registers, whose worker starts only at avvia(scope).
            val abbonato = AbbonatoRiallineamentoImpronte(riallinea, segnalazioni)
            dispatcher.registraDopoCommit(abbonato)
            abbonato.avvia(scope)
        }

        fun commit(evento: EventoPubblicato): Esito<Unit> = dispatcher.unitaDiLavoro.inTransazione {
            dispatcher.pubblica(evento)
            Esito.Ok(Unit)
        }

        fun commitAnnullato(evento: EventoPubblicato): Esito<Unit> = dispatcher.unitaDiLavoro.inTransazione {
            dispatcher.pubblica(evento)
            Esito.Errore(ErroreDiProva.Fallito("boom"))
        }

        /** Seeds a Parlante with one STALE print row for `VoceRef(unIncontroDi(registrazioneId), voce)`. */
        fun seminaStale(id: String, voce: Int, registrazioneId: RegistrazioneId = REG): ParlanteId {
            val p = Parlante.crea(ParlanteId(id), PROGETTO, Nome.di(id).atteso(), TipoParlante.RICORRENTE).aggregato
            p.aggiungiImpronta(
                VoceRef(unIncontroDi(registrazioneId), VoceId(voce)),
                unicaParteDi(VoceRef(unIncontroDi(registrazioneId), VoceId(voce))),
                VECCHIA,
                "0-1000",
                MODELLO,
            ).atteso()
            parlanti.salva(p).atteso()
            return p.id
        }

        fun impronta(id: ParlanteId, voce: Int = 1, registrazioneId: RegistrazioneId = REG): ImprontaVocale =
            assertNotNull(
                parlanti.trova(id),
            ).impronte.single { it.voceRef == VoceRef(unIncontroDi(registrazioneId), VoceId(voce)) }
    }

    // --- AC-304 (after-commit timing + translation) --------------------------------------------

    @Test
    fun `AC-C67 costruito non lancia alcun lavoro, una richiesta gira solo dopo avvia`() = runTest {
        val parlanti = ParlanteRepositoryFinta()
        val transazioni = UnitaDiLavoroFinta(parlanti)
        val dispatcher = DispatcherEventiInMemoria(transazioni)
        val riallinea = spyk(
            RiallineaImpronteServizio(
                dispatcher.unitaDiLavoro,
                LettoreVociFinta(emptyMap()),
                ogniRegistrazioneNota(),
                parlanti,
                DecodificatoreAudioFinta(unitaDiLavoro = transazioni),
                EstrattoreImprontaFinta(unitaDiLavoro = transazioni),
                dispatcher,
            ),
        )
        val abbonato = AbbonatoRiallineamentoImpronte(riallinea, Segnalazione { _, _ -> })

        abbonato.ricevi(VociUnite(unIncontroDi(REG), sopravvissuta = VoceId(1), rimossa = VoceId(2)))
        runCurrent() // backgroundScope's own tasks: advanceUntilIdle ignores them
        verify(exactly = 0) { riallinea.esegui(any()) }
        assertTrue(backgroundScope.coroutineContext.job.children.none(), "nessun lavoro in corso prima di avvia")

        abbonato.avvia(backgroundScope)
        runCurrent() // backgroundScope's own tasks: advanceUntilIdle ignores them

        verify(exactly = 1) { riallinea.esegui(RiallineaImpronte(unIncontroDi(REG))) }
    }

    @Test
    fun `INV-I8 una Revisione chiede il riallineamento dell Incontro che nomina`() = runTest {
        val parlanti = ParlanteRepositoryFinta()
        val transazioni = UnitaDiLavoroFinta(parlanti)
        val dispatcher = DispatcherEventiInMemoria(transazioni)
        val riallinea = spyk(
            RiallineaImpronteServizio(
                dispatcher.unitaDiLavoro,
                LettoreVociFinta(emptyMap()),
                ogniRegistrazioneNota(),
                parlanti,
                DecodificatoreAudioFinta(unitaDiLavoro = transazioni),
                EstrattoreImprontaFinta(unitaDiLavoro = transazioni),
                dispatcher,
            ),
        )
        val abbonato = AbbonatoRiallineamentoImpronte(riallinea, Segnalazione { _, _ -> })
        abbonato.avvia(backgroundScope)

        abbonato.ricevi(VociUnite(IncontroId("uno"), sopravvissuta = VoceId(1), rimossa = VoceId(2)))
        abbonato.ricevi(VoceDivisa(IncontroId("due"), VoceId(1), VoceId(2), emptyList()))
        runCurrent()

        verify(exactly = 1) { riallinea.esegui(RiallineaImpronte(IncontroId("uno"))) }
        verify(exactly = 1) { riallinea.esegui(RiallineaImpronte(IncontroId("due"))) }
        verify(exactly = 2) { riallinea.esegui(any()) }
    }

    @Test
    fun `AC-304 VociUnite invoca RiallineaImpronte solo dopo il commit della Revisione`() = runTest {
        val ambiente = Ambiente(testScheduler, voci = mapOf(REG to listOf(unaVoce(1, NUOVI))))
        val id = ambiente.seminaStale("Marco", 1)
        val ricevuti = mutableListOf<EventoPubblicato>()
        ambiente.dispatcher.registraDopoCommit { ricevuti += it }

        ambiente.commit(VociUnite(unIncontroDi(REG), sopravvissuta = VoceId(1), rimossa = VoceId(2))).atteso()
        assertEquals(VECCHIA, ambiente.impronta(id).impronta, "non ancora eseguito: il worker non ha ancora girato")

        advanceUntilIdle()

        assertEquals(chiave(NUOVI), ambiente.impronta(id).sorgente, "eseguito dopo il commit")
        assertTrue(ricevuti.any { it is ImpronteRiallineate }, "ImpronteRiallineate pubblicato")
    }

    @Test
    fun `AC-304 VoceDivisa e SegmentoRiassegnato innescano anch essi RiallineaImpronte dopo il commit`() = runTest {
        val ambiente = Ambiente(testScheduler, voci = mapOf(REG to listOf(unaVoce(1, NUOVI))))
        val id = ambiente.seminaStale("Marco", 1)

        ambiente.commit(VoceDivisa(unIncontroDi(REG), origine = VoceId(1), nuova = VoceId(2), spostati = emptyList()))
            .atteso()
        advanceUntilIdle()

        assertEquals(chiave(NUOVI), ambiente.impronta(id).sorgente)
    }

    // --- AC-305 (rollback triggers nothing) -----------------------------------------------------

    @Test
    fun `AC-305 una Revisione annullata non innesca alcun RiallineaImpronte`() = runTest {
        val ambiente = Ambiente(testScheduler, voci = mapOf(REG to listOf(unaVoce(1, NUOVI))))
        val id = ambiente.seminaStale("Marco", 1)
        val ricevuti = mutableListOf<EventoPubblicato>()
        ambiente.dispatcher.registraDopoCommit { ricevuti += it }

        ambiente.commitAnnullato(VociUnite(unIncontroDi(REG), sopravvissuta = VoceId(1), rimossa = VoceId(2)))
        advanceUntilIdle()

        assertEquals(VECCHIA, ambiente.impronta(id).impronta, "nessun riallineamento dopo un rollback")
        assertEquals(emptyList(), ricevuti)
    }

    // --- AC-306 (coalescing) ---------------------------------------------------------------------

    @Test
    fun `AC-306 eventi ravvicinati della stessa Registrazione producono una sola esecuzione`() = runTest {
        lateinit var spia: RiallineaImpronteServizio
        val ambiente = Ambiente(testScheduler, voci = mapOf(REG to listOf(unaVoce(1, NUOVI)))) { reale ->
            spyk(reale).also { spia = it }
        }
        ambiente.seminaStale("Marco", 1)

        // Tre eventi della STESSA Registrazione, tutti pubblicati prima che il worker abbia la
        // possibilita' di girare (nessun advance* tra un commit e l'altro).
        ambiente.commit(VociUnite(unIncontroDi(REG), sopravvissuta = VoceId(1), rimossa = VoceId(2))).atteso()
        val divisa = VoceDivisa(unIncontroDi(REG), origine = VoceId(1), nuova = VoceId(3), spostati = emptyList())
        ambiente.commit(divisa).atteso()
        ambiente.commit(
            SegmentoRiassegnato(
                unIncontroDi(REG),
                SegmentoRef(REG, SegmentoId(1)),
                da = VoceId(1),
                a = VoceId(3),
                daRimossa = false,
                aNuova = false,
            ),
        ).atteso()
        advanceUntilIdle()

        verify(exactly = 1) { spia.esegui(RiallineaImpronte(unIncontroDi(REG))) }
    }

    // --- AC-307 (retry, backoff, no busy loop, propagated exceptions included) -------------------

    @Test
    fun `AC-307 un eccezione di RiallineaImpronte e ritentata con backoff limitato fino al successo`() = runTest {
        val guasto = EstrattoreConGuasti()
        guasto.fallisciProssimeEstrazioni(2)
        val ambiente = Ambiente(testScheduler, voci = mapOf(REG to listOf(unaVoce(1, NUOVI))), estrattore = guasto)
        val id = ambiente.seminaStale("Marco", 1)

        val inizio = testScheduler.currentTime
        ambiente.commit(VociUnite(unIncontroDi(REG), sopravvissuta = VoceId(1), rimossa = VoceId(2))).atteso()
        advanceUntilIdle()

        assertEquals(3, guasto.tentativi, "2 fallimenti + 1 successo")
        assertTrue(testScheduler.currentTime > inizio, "il backoff e' passato per davvero: no busy loop")
        assertEquals(chiave(NUOVI), ambiente.impronta(id).sorgente, "converge al successo")
    }

    // --- AC-C50 (structural): one RitentaConBackoff, no private channel/loop/backoff ------------------

    /**
     * By REFLECTION, never by reading the module's own source text (`lessons-by-block-type.md`: a
     * source-text check would trip an ADR that bans file reads in the module's own tree). The absence
     * of the old hand-rolled channel/backoff/`runCatching` is proven here structurally, and by every
     * behavioural AC below staying green (AC-C51..C53) on the single-`RitentaConBackoff` design.
     */
    @Test
    fun `AC-C50 AbbonatoRiallineamentoImpronte ha un solo RitentaConBackoff e nessun canale privato`() {
        val campi = AbbonatoRiallineamentoImpronte::class.java.declaredFields
        val ritentaConBackoff = campi.count { it.type == RitentaConBackoff::class.java }
        assertEquals(1, ritentaConBackoff, "un solo campo RitentaConBackoff: nessun loop/backoff proprio")
        assertTrue(
            campi.none { Channel::class.java.isAssignableFrom(it.type) },
            "nessun canale privato: il coalescing/segnale e' interamente dentro RitentaConBackoff",
        )
    }

    // --- AC-C51 (a failing key is reported and retried; its recovery is reported once; another key
    // is unaffected) --------------------------------------------------------------------------------

    @Test
    fun `AC-C51 un fallimento di K e segnalato e ritentato, la ripresa e segnalata, K2 non ne risente`() = runTest {
        val k1 = REG
        val k2 = RegistrazioneId("registrazione-c51-2")
        val guasto = DecodificatoreConGuastoPer(k1)
        guasto.fallisciProssimeVolte(2)
        val ambiente = Ambiente(
            testScheduler,
            voci = mapOf(
                k1 to listOf(unaVoce(1, NUOVI, registrazioneId = k1)),
                k2 to listOf(unaVoce(1, NUOVI, registrazioneId = k2)),
            ),
            decodificatore = guasto,
        )
        val id1 = ambiente.seminaStale("Marco", 1, registrazioneId = k1)
        val id2 = ambiente.seminaStale("Luca", 1, registrazioneId = k2)

        // K1 richiesto per primo, poi K2: stesso lotto, nessun advance tra i due commit.
        ambiente.commit(VociUnite(unIncontroDi(k1), sopravvissuta = VoceId(1), rimossa = VoceId(2))).atteso()
        ambiente.commit(VociUnite(unIncontroDi(k2), sopravvissuta = VoceId(1), rimossa = VoceId(2))).atteso()
        advanceUntilIdle()

        assertEquals(
            chiave(NUOVI),
            ambiente.impronta(id2, registrazioneId = k2).sorgente,
            "K2 e' processato nello stesso lotto, nonostante K1 fallisca prima di lui",
        )
        assertEquals(chiave(NUOVI), ambiente.impronta(id1, registrazioneId = k1).sorgente, "K1 converge dopo i retry")

        val righeK1 = ambiente.segnalazioni.tutte.filter { k1.valore in it.messaggio }
        assertEquals(3, righeK1.size, "2 fallimenti + 1 ripresa, uno per tentativo: $righeK1")
        assertTrue(righeK1.dropLast(1).all { it.causa is IllegalStateException }, "$righeK1")
        assertNull(righeK1.last().causa, "l'ultimo report e' la ripresa, senza causa: ${righeK1.last()}")
        assertTrue("riuscito" in righeK1.last().messaggio, "l'ultimo report e' una ripresa: ${righeK1.last()}")
        assertTrue(
            ambiente.segnalazioni.tutte.none { k2.valore in it.messaggio },
            "K2 non fallisce mai: nessun report per lui",
        )
    }

    // --- AC-C52 (the retired PorteImprontaConLog's failure is now reported by the Segnalazione) -----

    @Test
    fun `AC-C52 il fallimento dell EstrattoreImpronta e riportato dalla Segnalazione`() = runTest {
        val guasto = EstrattoreConGuasti()
        guasto.fallisciProssimeEstrazioni(1)
        val ambiente = Ambiente(testScheduler, voci = mapOf(REG to listOf(unaVoce(1, NUOVI))), estrattore = guasto)
        val id = ambiente.seminaStale("Marco", 1)

        ambiente.commit(VociUnite(unIncontroDi(REG), sopravvissuta = VoceId(1), rimossa = VoceId(2))).atteso()
        advanceUntilIdle()

        val righe = ambiente.segnalazioni.tutte.filter { REG.valore in it.messaggio }
        assertTrue(righe.isNotEmpty(), "il fallimento dell'estrazione e' riportato dalla Segnalazione: $righe")
        assertIs<IllegalStateException>(righe.first().causa)
        assertEquals(chiave(NUOVI), ambiente.impronta(id).sorgente, "e converge comunque al successo")
    }

    // --- AC-C53 (worker cancellation: no report; an Error escapes instead of being retried) ---------

    @Test
    fun `AC-C53 il worker si ferma quando lo scope e cancellato, senza segnalare ne rigenerare oltre`() = runTest {
        val ambiente = Ambiente(testScheduler, voci = mapOf(REG to listOf(unaVoce(1, NUOVI))))
        val id = ambiente.seminaStale("Marco", 1)
        advanceUntilIdle()

        ambiente.scope.cancel()
        ambiente.commit(VociUnite(unIncontroDi(REG), sopravvissuta = VoceId(1), rimossa = VoceId(2))).atteso()
        advanceUntilIdle()

        assertEquals(VECCHIA, ambiente.impronta(id).impronta, "nessun riallineamento dopo la cancellazione")
        assertTrue(ambiente.segnalazioni.tutte.isEmpty(), "nessuna segnalazione dopo la cancellazione")
    }

    @Test
    fun `AC-C53 un Error nel riallineamento esce verso il gestore dello scope invece di essere ritentato`() =
        runTest {
            val estrattore = EstrattoreCheLanciaUnErrore()
            val sfuggiti = mutableListOf<Throwable>()
            val ambiente = Ambiente(
                testScheduler,
                voci = mapOf(REG to listOf(unaVoce(1, NUOVI))),
                estrattore = estrattore,
                gestore = CoroutineExceptionHandler { _, e -> sfuggiti += e },
            )
            ambiente.seminaStale("Marco", 1)

            try {
                ambiente.commit(VociUnite(unIncontroDi(REG), sopravvissuta = VoceId(1), rimossa = VoceId(2))).atteso()
                // Tempo LIMITATO, mai una advanceUntilIdle: se l'Error fosse per errore ritentato (regressione
                // verso un runCatching), l'estrattore lo rilancerebbe all'infinito e non convergerebbe mai.
                advanceTimeBy(60.seconds)
                runCurrent()

                assertEquals(1, estrattore.tentativi, "un solo tentativo: l'Error non e' un ritento")
                assertTrue(ambiente.segnalazioni.tutte.isEmpty(), "un Error non e' segnalato come fallimento")
                assertEquals(1, sfuggiti.size)
                assertIs<OutOfMemoryError>(sfuggiti.single())
                assertTrue(ambiente.scope.coroutineContext[Job]?.isCancelled == true)
            } finally {
                // Se la regressione sopra si fosse verificata, il worker ritenterebbe all'infinito: senza
                // questo cancel il drain automatico di fine-runTest inseguirebbe un lavoro che non finisce mai.
                ambiente.scope.cancel()
            }
        }

    // --- helpers ----------------------------------------------------------------------------------

    /** [EstrattoreImpronta] that fails the next `n` calls to [estrai] with an exception, then succeeds. */
    private class EstrattoreConGuasti(
        private val reale: EstrattoreImpronta = EstrattoreImprontaFinta(),
    ) : EstrattoreImpronta by reale {
        private var guastiRimanenti = 0
        var tentativi = 0
            private set

        fun fallisciProssimeEstrazioni(n: Int) {
            guastiRimanenti = n
        }

        override fun estrai(c: CampioniAudio): Impronta {
            tentativi++
            if (guastiRimanenti > 0) {
                guastiRimanenti--
                error("guasto simulato")
            }
            return reale.estrai(c)
        }
    }

    /** [EstrattoreImpronta] whose every [estrai] throws a real [Error] (AC-C53): never a retry candidate. */
    private class EstrattoreCheLanciaUnErrore(
        private val reale: EstrattoreImpronta = EstrattoreImprontaFinta(),
    ) : EstrattoreImpronta by reale {
        var tentativi = 0
            private set

        override fun estrai(c: CampioniAudio): Impronta {
            tentativi++
            throw OutOfMemoryError("finto")
        }
    }

    /** [DecodificatoreAudio] that fails the next `n` calls to [campioni] for [bersaglio], then delegates. */
    private class DecodificatoreConGuastoPer(
        private val bersaglio: RegistrazioneId,
        private val reale: DecodificatoreAudio = DecodificatoreAudioFinta(),
    ) : DecodificatoreAudio {
        private var guastiRimanenti = 0

        fun fallisciProssimeVolte(n: Int) {
            guastiRimanenti = n
        }

        override fun campioni(id: RegistrazioneId, intervalli: List<IntervalloMs>): CampioniAudio {
            if (id == bersaglio && guastiRimanenti > 0) {
                guastiRimanenti--
                error("guasto simulato per ${id.valore}")
            }
            return reale.campioni(id, intervalli)
        }
    }

    /** A recording [Segnalazione] for the tests. */
    private class SegnalazioniRegistrate : Segnalazione {
        data class Riga(val messaggio: String, val causa: Throwable?)

        private val righe = mutableListOf<Riga>()

        val tutte: List<Riga> get() = righe.toList()

        override fun segnala(messaggio: String, causa: Throwable?) {
            righe += Riga(messaggio, causa)
        }
    }

    private fun unaVoce(voce: Int, intervalli: List<IntervalloMs>, registrazioneId: RegistrazioneId = REG): VoceVista =
        VoceVista(VoceRef(unIncontroDi(registrazioneId), VoceId(voce)), mapOf(registrazioneId to intervalli))

    private companion object {
        val PROGETTO = ProgettoId("progetto-1")
        val REG = RegistrazioneId("registrazione-1")
        const val MODELLO = EstrattoreImprontaFinta.MODELLO
        val VECCHIA = Impronta(floatArrayOf(9f, 9f, 9f, 9f, 9f, 9f, 9f, 9f))
        val NUOVI = listOf(IntervalloMs(10_000, 13_000))

        fun chiave(intervalli: List<IntervalloMs>): String = SorgenteImpronta.di(intervalli).chiave
    }
}
