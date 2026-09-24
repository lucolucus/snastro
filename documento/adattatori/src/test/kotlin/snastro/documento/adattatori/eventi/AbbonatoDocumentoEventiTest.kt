package snastro.documento.adattatori.eventi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import snastro.documento.applicazione.politiche.RigenerazioneDocumentoPolitica
import snastro.documento.applicazione.porte.LettoreNomiFinta
import snastro.documento.applicazione.porte.LettoreTrascrittoFinta
import snastro.documento.applicazione.porte.ScrittoreDocumento
import snastro.documento.applicazione.porte.ScrittoreDocumentoFinta
import snastro.documento.applicazione.porte.SegmentoVista
import snastro.documento.applicazione.porte.TrascrittoTesto
import snastro.kernel.DispatcherEventiInMemoria
import snastro.kernel.ErroreDiProva
import snastro.kernel.Esito
import snastro.kernel.EventoPubblicato
import snastro.kernel.IntervalloMs
import snastro.kernel.ParlanteId
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.parlanti.applicazione.eventi.AttribuzioneConfermata
import snastro.parlanti.applicazione.eventi.ParlanteEliminato
import snastro.parlanti.applicazione.eventi.ParlantePromosso
import snastro.parlanti.applicazione.eventi.ParlanteRinominato
import snastro.progetto.applicazione.eventi.DataRegistrazioneModificata
import snastro.progetto.applicazione.eventi.RegistrazioneRinominata
import snastro.trascrizione.applicazione.eventi.ElaborazioneCompletata
import snastro.trascrizione.applicazione.eventi.SegmentoRiassegnato
import snastro.trascrizione.applicazione.eventi.VoceDivisa
import snastro.trascrizione.applicazione.eventi.VociUnite
import java.io.IOException
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests of [AbbonatoDocumentoEventi]: AC-182..186 and AC-186bis (rename, manifest delta
 * `2026-09-24-rinomina-documento.md`). Virtual time only ([StandardTestDispatcher] +
 * [TestCoroutineScheduler], `advanceUntilIdle`) — no real sleeps.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AbbonatoDocumentoEventiTest {

    /**
     * One test's wiring: a real [DispatcherEventiInMemoria], a real [RigenerazioneDocumentoPolitica]
     * over [scrittore], and the [AbbonatoDocumentoEventi] under test (self-registering, discarded) —
     * all sharing [scheduler]'s virtual clock.
     */
    private class Ambiente(
        scheduler: TestCoroutineScheduler,
        trascritti: Map<RegistrazioneId, TrascrittoTesto> = emptyMap(),
        nomi: LettoreNomiFinta = LettoreNomiFinta(),
        val scrittore: ScrittoreDocumento = ScrittoreDocumentoFinta(),
    ) {
        val dispatcher = DispatcherEventiInMemoria(UnitaDiLavoroFinta())
        private val politica = RigenerazioneDocumentoPolitica(LettoreTrascrittoFinta(trascritti), nomi, scrittore)

        init {
            AbbonatoDocumentoEventi(dispatcher, politica, CoroutineScope(StandardTestDispatcher(scheduler)))
        }

        fun commit(evento: EventoPubblicato) {
            dispatcher.unitaDiLavoro.inTransazione {
                dispatcher.pubblica(evento)
                Esito.Ok(Unit)
            }
        }

        fun commitAnnullato(evento: EventoPubblicato) {
            dispatcher.unitaDiLavoro.inTransazione {
                dispatcher.pubblica(evento)
                Esito.Errore(ErroreDiProva.Fallito("boom"))
            }
        }

        fun operazioni(): List<ScrittoreDocumentoFinta.Operazione> = (scrittore as ScrittoreDocumentoFinta).operazioni

        fun documenti(): Map<String, String> = (scrittore as ScrittoreDocumentoFinta).documenti
    }

    // --- AC-182 -------------------------------------------------------------------------------

    @Test
    fun `AC-182 un comando annullato non scrive alcun Documento`() = runTest {
        val ambiente = Ambiente(testScheduler, mapOf(REG_1 to unTrascritto(REG_1)))
        advanceUntilIdle() // AC-185's own startup sweep settles first (nothing to do with this AC)
        val primaDelRollback = ambiente.operazioni().size

        ambiente.commitAnnullato(ElaborazioneCompletata(REG_1))
        advanceUntilIdle()

        assertEquals(primaDelRollback, ambiente.operazioni().size)
    }

    // --- AC-183 (coalescing) -------------------------------------------------------------------

    @Test
    fun `AC-183 N eventi della stessa Registrazione in rapida successione producono una sola scrittura`() = runTest {
        val ambiente = Ambiente(
            testScheduler,
            mapOf(REG_1 to unTrascritto(REG_1)),
            LettoreNomiFinta(mapOf(VoceRef(REG_1, VoceId(1)) to PARLANTE), mapOf(PARLANTE to "Marco")),
        )
        advanceUntilIdle() // startup sweep settles
        val primaDellaRaffica = ambiente.operazioni().size

        // Tre eventi della STESSA Registrazione, tutti pubblicati prima che il worker abbia la
        // possibilita' di girare (nessun advance* tra un commit e l'altro).
        ambiente.commit(ElaborazioneCompletata(REG_1))
        ambiente.commit(AttribuzioneConfermata(VoceRef(REG_1, VoceId(1)), PARLANTE, precedente = null))
        ambiente.commit(VociUnite(REG_1, sopravvissuta = VoceId(1), rimossa = VoceId(2)))
        advanceUntilIdle()

        assertEquals(1, ambiente.operazioni().size - primaDellaRaffica)
    }

    @Test
    fun `AC-183 eventi di tipo diverso si combinano nel file precedente corretto`() = runTest {
        val vecchiaData = LocalDate.of(2026, 9, 19)
        val nuovaData = LocalDate.of(2026, 9, 20)
        val trascritto = unTrascritto(REG_1, titolo = "Titolo Nuovo", data = nuovaData)
        val ambiente = Ambiente(testScheduler, mapOf(REG_1 to trascritto))
        advanceUntilIdle() // lo sweep di avvio scrive gia' "2026-09-20 Titolo Nuovo.md"

        // Prima di questa raffica il file davvero sul disco era "2026-09-19 Titolo Vecchio.md" (la
        // vecchia data CON il vecchio titolo): ne' un DataRegistrazioneModificata ne' una
        // RegistrazioneRinominata da soli lo sanno, solo la combinazione dei due precedenti.
        ambiente.commit(DataRegistrazioneModificata(REG_1, precedente = vecchiaData, nuova = nuovaData))
        ambiente.commit(RegistrazioneRinominata(REG_1, precedente = "Titolo Vecchio", nuovo = "Titolo Nuovo"))
        advanceUntilIdle()

        val vecchioFile = ScrittoreDocumentoFinta.Operazione.Rimosso("2026-09-19 Titolo Vecchio.md")
        assertTrue(ambiente.operazioni().contains(vecchioFile))
        assertFalse(ambiente.documenti().containsKey("2026-09-19 Titolo Vecchio.md"))
        assertTrue(ambiente.documenti().containsKey("2026-09-20 Titolo Nuovo.md"))
    }

    // --- AC-184 (retry, backoff, no busy loop) --------------------------------------------------

    @Test
    fun `AC-184 un errore nello sweep di avvio viene ritentato fino al successo senza busy loop`() = runTest {
        val scrittore = ScrittoreConGuasti()
        scrittore.fallisciProssimeScritture(2)
        Ambiente(testScheduler, mapOf(REG_1 to unTrascritto(REG_1)), scrittore = scrittore)

        val inizio = testScheduler.currentTime
        advanceUntilIdle()

        assertTrue(scrittore.scritti.containsKey("2026-09-12 Riunione.md"))
        assertEquals(3, scrittore.tentativi) // 2 fallimenti + 1 successo
        assertTrue(testScheduler.currentTime > inizio) // il backoff e' passato per davvero: no busy loop
    }

    @Test
    fun `AC-184 un errore di scrittura innescato da un evento viene ritentato fino al successo`() = runTest {
        val scrittore = ScrittoreConGuasti()
        val ambiente = Ambiente(testScheduler, mapOf(REG_1 to unTrascritto(REG_1)), scrittore = scrittore)
        advanceUntilIdle() // sweep di avvio, nessun guasto armato ancora: scrive una volta e basta
        val tentativiDopoAvvio = scrittore.tentativi

        scrittore.fallisciProssimeScritture(2)
        ambiente.commit(ElaborazioneCompletata(REG_1))
        val inizio = testScheduler.currentTime
        advanceUntilIdle()

        assertEquals(tentativiDopoAvvio + 3, scrittore.tentativi) // 2 fallimenti + 1 successo, in piu'
        assertTrue(testScheduler.currentTime > inizio) // il backoff e' passato per davvero: no busy loop
    }

    // --- AC-185 (startup sweep) ------------------------------------------------------------------

    @Test
    fun `AC-185 all avvio ogni Documento con un Trascritto viene rigenerato`() = runTest {
        val a = RegistrazioneId("reg-a")
        val b = RegistrazioneId("reg-b")
        val trascritti = mapOf(a to unTrascritto(a, titolo = "Uno"), b to unTrascritto(b, titolo = "Due"))
        val ambiente = Ambiente(testScheduler, trascritti)
        advanceUntilIdle()

        assertEquals(setOf("2026-09-12 Uno.md", "2026-09-12 Due.md"), ambiente.documenti().keys)
    }

    // --- AC-186 (event -> policy mapping) ---------------------------------------------------------

    @Test
    fun `AC-186 ElaborazioneCompletata attiva la Rigenerazione`() = runTest {
        assertEventoRigenera(ElaborazioneCompletata(REG_1))
    }

    @Test
    fun `AC-186 VociUnite attiva la Rigenerazione`() = runTest {
        assertEventoRigenera(VociUnite(REG_1, sopravvissuta = VoceId(1), rimossa = VoceId(2)))
    }

    @Test
    fun `AC-186 VoceDivisa attiva la Rigenerazione`() = runTest {
        assertEventoRigenera(
            VoceDivisa(REG_1, origine = VoceId(1), nuova = VoceId(2), segmentiSpostati = listOf(SegmentoId(2))),
        )
    }

    @Test
    fun `AC-186 SegmentoRiassegnato attiva la Rigenerazione`() = runTest {
        assertEventoRigenera(
            SegmentoRiassegnato(
                REG_1,
                segmentoId = SegmentoId(1),
                da = VoceId(1),
                a = VoceId(2),
                daRimossa = false,
                aNuova = true,
            ),
        )
    }

    @Test
    fun `AC-186 AttribuzioneConfermata attiva la Rigenerazione`() = runTest {
        assertEventoRigenera(AttribuzioneConfermata(VoceRef(REG_1, VoceId(1)), PARLANTE, precedente = null))
    }

    @Test
    fun `AC-186 DataRegistrazioneModificata attiva la Rigenerazione e rimuove il vecchio file`() = runTest {
        val vecchiaData = LocalDate.of(2026, 9, 19)
        val nuovaData = LocalDate.of(2026, 9, 20)
        val ambiente = Ambiente(testScheduler, mapOf(REG_1 to unTrascritto(REG_1, data = nuovaData)))
        advanceUntilIdle()

        ambiente.commit(DataRegistrazioneModificata(REG_1, precedente = vecchiaData, nuova = nuovaData))
        advanceUntilIdle()

        val vecchioFile = ScrittoreDocumentoFinta.Operazione.Rimosso("2026-09-19 Riunione.md")
        assertTrue(ambiente.operazioni().contains(vecchioFile))
        assertTrue(ambiente.documenti().containsKey("2026-09-20 Riunione.md"))
    }

    @Test
    fun `AC-186bis (rename) RegistrazioneRinominata rigenera come DataRegistrazioneModificata`() = runTest {
        val ambiente = Ambiente(testScheduler, mapOf(REG_1 to unTrascritto(REG_1, titolo = "Titolo Nuovo")))
        advanceUntilIdle()

        ambiente.commit(RegistrazioneRinominata(REG_1, precedente = "Titolo Vecchio", nuovo = "Titolo Nuovo"))
        advanceUntilIdle()

        val vecchioFile = ScrittoreDocumentoFinta.Operazione.Rimosso("2026-09-12 Titolo Vecchio.md")
        assertTrue(ambiente.operazioni().contains(vecchioFile))
        assertTrue(ambiente.documenti().containsKey("2026-09-12 Titolo Nuovo.md"))
    }

    @Test
    fun `AC-186 ParlanteRinominato rigenera ogni Registrazione attribuita al Parlante`() = runTest {
        val conAttribuzione = RegistrazioneId("con-attribuzione")
        val senzaAttribuzione = RegistrazioneId("senza-attribuzione")
        val ambiente = Ambiente(
            testScheduler,
            mapOf(
                conAttribuzione to unTrascritto(conAttribuzione, titolo = "Uno"),
                senzaAttribuzione to unTrascritto(senzaAttribuzione, titolo = "Due"),
            ),
            LettoreNomiFinta(mapOf(VoceRef(conAttribuzione, VoceId(1)) to PARLANTE), mapOf(PARLANTE to "Marco Rossi")),
        )
        advanceUntilIdle()
        val primaDelRename = ambiente.operazioni().size

        ambiente.commit(ParlanteRinominato(PARLANTE, "Marco Rossi"))
        advanceUntilIdle()

        assertEquals(
            listOf(ScrittoreDocumentoFinta.Operazione.Scritto("2026-09-12 Uno.md")),
            ambiente.operazioni().drop(primaDelRename),
        )
    }

    @Test
    fun `AC-186 ParlantePromosso con nomeCambiato false non attiva alcuna Rigenerazione`() = runTest {
        val ambiente = Ambiente(
            testScheduler,
            mapOf(REG_1 to unTrascritto(REG_1)),
            LettoreNomiFinta(mapOf(VoceRef(REG_1, VoceId(1)) to PARLANTE), mapOf(PARLANTE to "Marco")),
        )
        advanceUntilIdle()
        val prima = ambiente.operazioni().size

        ambiente.commit(ParlantePromosso(PARLANTE, "Marco", nomeCambiato = false))
        advanceUntilIdle()

        assertEquals(prima, ambiente.operazioni().size)
    }

    @Test
    fun `AC-186 ParlantePromosso con nomeCambiato true attiva la Rigenerazione`() = runTest {
        val ambiente = Ambiente(
            testScheduler,
            mapOf(REG_1 to unTrascritto(REG_1)),
            LettoreNomiFinta(mapOf(VoceRef(REG_1, VoceId(1)) to PARLANTE), mapOf(PARLANTE to "Marco")),
        )
        advanceUntilIdle()
        val prima = ambiente.operazioni().size

        ambiente.commit(ParlantePromosso(PARLANTE, "Marco", nomeCambiato = true))
        advanceUntilIdle()

        assertEquals(prima + 1, ambiente.operazioni().size)
    }

    @Test
    fun `AC-186 ParlanteEliminato non attiva alcuna Rigenerazione`() = runTest {
        val ambiente = Ambiente(testScheduler, mapOf(REG_1 to unTrascritto(REG_1)))
        advanceUntilIdle()
        val prima = ambiente.operazioni().size

        ambiente.commit(ParlanteEliminato(PARLANTE))
        advanceUntilIdle()

        assertEquals(prima, ambiente.operazioni().size)
    }

    // --- stop on scope cancellation --------------------------------------------------------------

    @Test
    fun `il worker si ferma quando lo scope e cancellato`() = runTest {
        val scrittore = ScrittoreDocumentoFinta()
        val dispatcher = DispatcherEventiInMemoria(UnitaDiLavoroFinta())
        val trascritti = LettoreTrascrittoFinta(mapOf(REG_1 to unTrascritto(REG_1)))
        val politica = RigenerazioneDocumentoPolitica(trascritti, LettoreNomiFinta(), scrittore)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        AbbonatoDocumentoEventi(dispatcher, politica, scope)
        advanceUntilIdle() // sweep di avvio
        val primaDellaCancellazione = scrittore.operazioni.size

        scope.cancel()
        dispatcher.unitaDiLavoro.inTransazione {
            dispatcher.pubblica(ElaborazioneCompletata(REG_1))
            Esito.Ok(Unit)
        }
        advanceUntilIdle()

        assertEquals(primaDellaCancellazione, scrittore.operazioni.size)
    }

    // --- helpers ----------------------------------------------------------------------------------

    /**
     * Constructs an [Ambiente] with a Trascritto already present for [REG_1] (so the AC-185 sweep
     * settles first), then commits [evento] and asserts it produced exactly one MORE successful
     * write — proof [AbbonatoDocumentoEventi] translated it into a [RigenerazioneDocumentoPolitica]
     * call (which event maps to which call is proven by `RigenerazioneDocumentoPoliticaTest`).
     */
    private suspend fun TestScope.assertEventoRigenera(evento: EventoPubblicato) {
        val ambiente = Ambiente(
            testScheduler,
            mapOf(REG_1 to unTrascritto(REG_1)),
            LettoreNomiFinta(mapOf(VoceRef(REG_1, VoceId(1)) to PARLANTE), mapOf(PARLANTE to "Marco")),
        )
        advanceUntilIdle()
        val prima = ambiente.operazioni().size

        ambiente.commit(evento)
        advanceUntilIdle()

        assertEquals(prima + 1, ambiente.operazioni().size)
    }

    /**
     * [ScrittoreDocumento] that fails the next `n` calls to [scrivi] (armed by
     * [fallisciProssimeScritture]), then succeeds.
     */
    private class ScrittoreConGuasti : ScrittoreDocumento {
        private var guastiRimanenti = 0
        var tentativi = 0
            private set
        val scritti = mutableMapOf<String, String>()

        fun fallisciProssimeScritture(n: Int) {
            guastiRimanenti = n
        }

        override fun scrivi(nomeFile: String, markdown: String) {
            tentativi++
            if (guastiRimanenti > 0) {
                guastiRimanenti--
                throw IOException("guasto simulato")
            }
            scritti[nomeFile] = markdown
        }

        override fun rimuovi(nomeFile: String) {
            scritti.remove(nomeFile)
        }
    }

    private companion object {
        val REG_1 = RegistrazioneId("reg-1")
        val PARLANTE = ParlanteId("parlante-1")

        fun unTrascritto(
            id: RegistrazioneId,
            titolo: String = "Riunione",
            data: LocalDate = LocalDate.of(2026, 9, 12),
        ) = TrascrittoTesto(
            registrazioneId = id,
            titolo = titolo,
            dataRegistrazione = data,
            segmenti = listOf(SegmentoVista(SegmentoId(1), VoceId(1), IntervalloMs(0, 1_000), "Ciao.")),
        )
    }
}
