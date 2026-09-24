package snastro.parlanti.adattatori.eventi

import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import snastro.kernel.CampioniAudio
import snastro.kernel.DispatcherEventiInMemoria
import snastro.kernel.ErroreDiProva
import snastro.kernel.Esito
import snastro.kernel.EventoPubblicato
import snastro.kernel.IntervalloMs
import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.atteso
import snastro.parlanti.applicazione.comandi.RiallineaImpronte
import snastro.parlanti.applicazione.comandi.RiallineaImpronteServizio
import snastro.parlanti.applicazione.eventi.ImpronteRiallineate
import snastro.parlanti.applicazione.porte.DecodificatoreAudioFinta
import snastro.parlanti.applicazione.porte.EstrattoreImpronta
import snastro.parlanti.applicazione.porte.EstrattoreImprontaFinta
import snastro.parlanti.applicazione.porte.LettoreVociFinta
import snastro.parlanti.applicazione.porte.ParlanteRepositoryFinta
import snastro.parlanti.applicazione.porte.VoceVista
import snastro.parlanti.dominio.Impronta
import snastro.parlanti.dominio.ImprontaVocale
import snastro.parlanti.dominio.Nome
import snastro.parlanti.dominio.Parlante
import snastro.parlanti.dominio.SorgenteImpronta
import snastro.parlanti.dominio.TipoParlante
import snastro.trascrizione.applicazione.eventi.SegmentoRiassegnato
import snastro.trascrizione.applicazione.eventi.VoceDivisa
import snastro.trascrizione.applicazione.eventi.VociUnite
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests of [AbbonatoRiallineamentoImpronte]: AC-304..AC-307. Virtual time only
 * ([StandardTestDispatcher] + [TestCoroutineScheduler], `advanceUntilIdle`) — no real sleeps.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AbbonatoRiallineamentoImpronteTest {

    /**
     * One test's wiring: a real [DispatcherEventiInMemoria] over [ParlanteRepositoryFinta] (the
     * `Ripristinabile` "in-memory database", `UnitaDiLavoroFinta`), a real [RiallineaImpronteServizio]
     * over [voci]/[estrattore], and the [AbbonatoRiallineamentoImpronte] under test (self-registering,
     * discarded) — all sharing [scheduler]'s virtual clock.
     */
    private class Ambiente(
        scheduler: TestCoroutineScheduler,
        voci: Map<RegistrazioneId, List<VoceVista>> = emptyMap(),
        estrattore: EstrattoreImpronta? = null,
        decoratore: (RiallineaImpronteServizio) -> RiallineaImpronteServizio = { it },
    ) {
        val parlanti = ParlanteRepositoryFinta()
        private val transazioni = UnitaDiLavoroFinta(parlanti)
        val dispatcher = DispatcherEventiInMemoria(transazioni)
        val riallinea = decoratore(
            RiallineaImpronteServizio(
                dispatcher.unitaDiLavoro,
                LettoreVociFinta(voci),
                parlanti,
                DecodificatoreAudioFinta(unitaDiLavoro = transazioni),
                estrattore ?: EstrattoreImprontaFinta(unitaDiLavoro = transazioni),
                dispatcher,
            ),
        )

        init {
            AbbonatoRiallineamentoImpronte(dispatcher, riallinea, CoroutineScope(StandardTestDispatcher(scheduler)))
        }

        fun commit(evento: EventoPubblicato): Esito<Unit> = dispatcher.unitaDiLavoro.inTransazione {
            dispatcher.pubblica(evento)
            Esito.Ok(Unit)
        }

        fun commitAnnullato(evento: EventoPubblicato): Esito<Unit> = dispatcher.unitaDiLavoro.inTransazione {
            dispatcher.pubblica(evento)
            Esito.Errore(ErroreDiProva.Fallito("boom"))
        }

        /** Seeds a Parlante with one STALE print row for `VoceRef(REG, voce)`. */
        fun seminaStale(id: String, voce: Int): ParlanteId {
            val p = Parlante.crea(ParlanteId(id), PROGETTO, Nome.di(id).atteso(), TipoParlante.RICORRENTE).aggregato
            p.registraImpronta(VoceRef(REG, VoceId(voce)), VECCHIA, "0-1000", MODELLO).atteso()
            parlanti.salva(p).atteso()
            return p.id
        }

        fun impronta(id: ParlanteId, voce: Int = 1): ImprontaVocale =
            assertNotNull(parlanti.trova(id)).impronte.single { it.voceRef == VoceRef(REG, VoceId(voce)) }
    }

    // --- AC-304 (after-commit timing + translation) --------------------------------------------

    @Test
    fun `AC-304 VociUnite invoca RiallineaImpronte solo dopo il commit della Revisione`() = runTest {
        val ambiente = Ambiente(testScheduler, voci = mapOf(REG to listOf(unaVoce(1, NUOVI))))
        val id = ambiente.seminaStale("Marco", 1)
        val ricevuti = mutableListOf<EventoPubblicato>()
        ambiente.dispatcher.registraDopoCommit { ricevuti += it }

        ambiente.commit(VociUnite(REG, sopravvissuta = VoceId(1), rimossa = VoceId(2))).atteso()
        assertEquals(VECCHIA, ambiente.impronta(id).impronta, "non ancora eseguito: il worker non ha ancora girato")

        advanceUntilIdle()

        assertEquals(chiave(NUOVI), ambiente.impronta(id).sorgente, "eseguito dopo il commit")
        assertTrue(ricevuti.any { it is ImpronteRiallineate }, "ImpronteRiallineate pubblicato")
    }

    @Test
    fun `AC-304 VoceDivisa e SegmentoRiassegnato innescano anch essi RiallineaImpronte dopo il commit`() = runTest {
        val ambiente = Ambiente(testScheduler, voci = mapOf(REG to listOf(unaVoce(1, NUOVI))))
        val id = ambiente.seminaStale("Marco", 1)

        ambiente.commit(VoceDivisa(REG, origine = VoceId(1), nuova = VoceId(2), segmentiSpostati = emptyList()))
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

        ambiente.commitAnnullato(VociUnite(REG, sopravvissuta = VoceId(1), rimossa = VoceId(2)))
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
        ambiente.commit(VociUnite(REG, sopravvissuta = VoceId(1), rimossa = VoceId(2))).atteso()
        val divisa = VoceDivisa(REG, origine = VoceId(1), nuova = VoceId(3), segmentiSpostati = emptyList())
        ambiente.commit(divisa).atteso()
        ambiente.commit(
            SegmentoRiassegnato(REG, SegmentoId(1), da = VoceId(1), a = VoceId(3), daRimossa = false, aNuova = false),
        ).atteso()
        advanceUntilIdle()

        verify(exactly = 1) { spia.esegui(RiallineaImpronte(REG)) }
    }

    // --- AC-307 (retry, backoff, no busy loop, propagated exceptions included) -------------------

    @Test
    fun `AC-307 un eccezione di RiallineaImpronte e ritentata con backoff limitato fino al successo`() = runTest {
        val guasto = EstrattoreConGuasti()
        guasto.fallisciProssimeEstrazioni(2)
        val ambiente = Ambiente(testScheduler, voci = mapOf(REG to listOf(unaVoce(1, NUOVI))), estrattore = guasto)
        val id = ambiente.seminaStale("Marco", 1)

        val inizio = testScheduler.currentTime
        ambiente.commit(VociUnite(REG, sopravvissuta = VoceId(1), rimossa = VoceId(2))).atteso()
        advanceUntilIdle()

        assertEquals(3, guasto.tentativi, "2 fallimenti + 1 successo")
        assertTrue(testScheduler.currentTime > inizio, "il backoff e' passato per davvero: no busy loop")
        assertEquals(chiave(NUOVI), ambiente.impronta(id).sorgente, "converge al successo")
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

    private fun unaVoce(voce: Int, intervalli: List<IntervalloMs>): VoceVista =
        VoceVista(VoceRef(REG, VoceId(voce)), intervalli)

    private companion object {
        val PROGETTO = ProgettoId("progetto-1")
        val REG = RegistrazioneId("registrazione-1")
        const val MODELLO = EstrattoreImprontaFinta.MODELLO
        val VECCHIA = Impronta(floatArrayOf(9f, 9f, 9f, 9f, 9f, 9f, 9f, 9f))
        val NUOVI = listOf(IntervalloMs(10_000, 13_000))

        fun chiave(intervalli: List<IntervalloMs>): String = SorgenteImpronta.di(intervalli).chiave
    }
}
