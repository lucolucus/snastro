package snastro.sbobinatura.adattatori.eventi

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import snastro.kernel.DispatcherEventiInMemoria
import snastro.kernel.ErroreDiProva
import snastro.kernel.Esito
import snastro.kernel.EventoPubblicato
import snastro.kernel.IncontroId
import snastro.kernel.IntervalloMs
import snastro.kernel.ParlanteId
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.SegmentoRef
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.unIncontroDi
import snastro.parlanti.applicazione.eventi.AttribuzioneConfermata
import snastro.parlanti.applicazione.eventi.ParlanteEliminato
import snastro.parlanti.applicazione.eventi.ParlantePromosso
import snastro.parlanti.applicazione.eventi.ParlanteRinominato
import snastro.progetto.applicazione.eventi.DataRegistrazioneModificata
import snastro.progetto.applicazione.eventi.OraDiInizioModificata
import snastro.progetto.applicazione.eventi.RegistrazioneRinominata
import snastro.sbobinatura.applicazione.politiche.RigenerazioneSbobinaturaPolitica
import snastro.sbobinatura.applicazione.porte.LettoreNomiFinta
import snastro.sbobinatura.applicazione.porte.LettoreTrascritto
import snastro.sbobinatura.applicazione.porte.LettoreTrascrittoFinta
import snastro.sbobinatura.applicazione.porte.ScrittoreSbobinatura
import snastro.sbobinatura.applicazione.porte.ScrittoreSbobinaturaFinta
import snastro.sbobinatura.applicazione.porte.SegmentoVista
import snastro.sbobinatura.applicazione.porte.TrascrittoTesto
import snastro.supporto.RitentaConBackoff
import snastro.supporto.Segnalazione
import snastro.supporto.test.attendiFinche
import snastro.supporto.test.conScopeDiProva
import snastro.trascrizione.applicazione.eventi.ElaborazioneCompletata
import snastro.trascrizione.applicazione.eventi.SegmentoRiassegnato
import snastro.trascrizione.applicazione.eventi.VoceDivisa
import snastro.trascrizione.applicazione.eventi.VociUnite
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Tests of [AbbonatoSbobinaturaEventi]: AC-182..186, AC-186bis (rename, manifest delta
 * `2026-09-24-rinomina-sbobinatura.md`) and AC-C45..C49/AC-C92 (its retry mechanics on [RitentaConBackoff],
 * `a2-ritenta-sbobinatura`). Virtual time only ([StandardTestDispatcher] + [TestCoroutineScheduler],
 * `advanceUntilIdle`) — no real sleeps — except AC-C92, a genuine concurrency probe on real threads
 * ([conScopeDiProva], per `lessons-by-block-type.md`: a concurrency AC needs real racers, not virtual time).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AbbonatoSbobinaturaEventiTest {

    /**
     * One test's wiring: a real [DispatcherEventiInMemoria], a real [RigenerazioneSbobinaturaPolitica]
     * over [scrittore], and the [AbbonatoSbobinaturaEventi] under test (registered as the composition
     * registers it, then started) — all sharing [scheduler]'s virtual clock.
     */
    private class Ambiente(
        scheduler: TestCoroutineScheduler,
        trascritti: Map<RegistrazioneId, TrascrittoTesto> = emptyMap(),
        nomi: LettoreNomiFinta = LettoreNomiFinta(),
        val scrittore: ScrittoreSbobinatura = ScrittoreSbobinaturaFinta(),
        val segnalazioni: SegnalazioniRegistrate = SegnalazioniRegistrate(),
    ) {
        val dispatcher = DispatcherEventiInMemoria(UnitaDiLavoroFinta())
        private val lettore = LettoreTrascrittoFinta(trascritti)
        private val politica = RigenerazioneSbobinaturaPolitica(lettore, nomi, scrittore)

        init {
            val scope = CoroutineScope(StandardTestDispatcher(scheduler))
            abbonaSbobinatura(
                dispatcher,
                politica,
                lettore::registrazioniConTrascritto,
                scope,
                segnalazioni,
                lettore::partiConTrascritto,
            )
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

        fun operazioni(): List<ScrittoreSbobinaturaFinta.Operazione> =
            (scrittore as ScrittoreSbobinaturaFinta).operazioni

        fun sbobinature(): Map<String, String> = (scrittore as ScrittoreSbobinaturaFinta).sbobinature
    }

    // --- AC-182 -------------------------------------------------------------------------------

    @Test
    fun `AC-182 un comando annullato non scrive alcuna Sbobinatura`() = runTest {
        val ambiente = Ambiente(testScheduler, mapOf(REG_1 to unTrascritto(REG_1)))
        advanceUntilIdle() // AC-185's own startup sweep settles first (nothing to do with this AC)
        val primaDelRollback = ambiente.operazioni().size

        ambiente.commitAnnullato(ElaborazioneCompletata(REG_1, unIncontroDi(REG_1)))
        advanceUntilIdle()

        assertEquals(primaDelRollback, ambiente.operazioni().size)
    }

    // --- AC-183 (coalescing) -------------------------------------------------------------------

    @Test
    fun `AC-183 N eventi della stessa Registrazione in rapida successione producono una sola scrittura`() = runTest {
        val ambiente = Ambiente(
            testScheduler,
            mapOf(REG_1 to unTrascritto(REG_1)),
            LettoreNomiFinta(mapOf(VoceRef(unIncontroDi(REG_1), VoceId(1)) to PARLANTE), mapOf(PARLANTE to "Marco")),
        )
        advanceUntilIdle() // startup sweep settles
        val primaDellaRaffica = ambiente.operazioni().size

        // Tre eventi della STESSA Registrazione, tutti pubblicati prima che il worker abbia la
        // possibilita' di girare (nessun advance* tra un commit e l'altro).
        ambiente.commit(ElaborazioneCompletata(REG_1, unIncontroDi(REG_1)))
        ambiente.commit(AttribuzioneConfermata(VoceRef(unIncontroDi(REG_1), VoceId(1)), PARLANTE, precedente = null))
        ambiente.commit(VociUnite(unIncontroDi(REG_1), sopravvissuta = VoceId(1), rimossa = VoceId(2)))
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
        ambiente.commit(DataRegistrazioneModificata(REG_1, vecchiaData, nuovaData, unIncontroDi(REG_1)))
        ambiente.commit(RegistrazioneRinominata(REG_1, precedente = "Titolo Vecchio", nuovo = "Titolo Nuovo"))
        advanceUntilIdle()

        val vecchioFile = ScrittoreSbobinaturaFinta.Operazione.Rimosso("2026-09-19 Titolo Vecchio.md")
        assertTrue(ambiente.operazioni().contains(vecchioFile))
        assertFalse(ambiente.sbobinature().containsKey("2026-09-19 Titolo Vecchio.md"))
        assertTrue(ambiente.sbobinature().containsKey("2026-09-20 Titolo Nuovo.md"))
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
        ambiente.commit(ElaborazioneCompletata(REG_1, unIncontroDi(REG_1)))
        val inizio = testScheduler.currentTime
        advanceUntilIdle()

        assertEquals(tentativiDopoAvvio + 3, scrittore.tentativi) // 2 fallimenti + 1 successo, in piu'
        assertTrue(testScheduler.currentTime > inizio) // il backoff e' passato per davvero: no busy loop
    }

    @Test
    fun `D-0008 un eccezione nella lettura del Trascritto viene ritentata e non esce dal ciclo`() = runTest {
        val lettore = LettoreCheLancia(LettoreTrascrittoFinta(mapOf(REG_1 to unTrascritto(REG_1))))
        val scrittore = ScrittoreSbobinaturaFinta()
        val dispatcher = DispatcherEventiInMemoria(UnitaDiLavoroFinta())
        val politica = RigenerazioneSbobinaturaPolitica(lettore, LettoreNomiFinta(), scrittore)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        abbonaSbobinatura(
            dispatcher,
            politica,
            lettore::registrazioniConTrascritto,
            scope,
            Segnalazione { _, _ -> },
        )
        advanceUntilIdle() // startup sweep settles (no fault armed yet)
        val prima = scrittore.operazioni.size

        lettore.lanciaProssimeLetture(2)
        dispatcher.unitaDiLavoro.inTransazione {
            Esito.Ok(dispatcher.pubblica(ElaborazioneCompletata(REG_1, unIncontroDi(REG_1))))
        }
        advanceUntilIdle()

        assertEquals(prima + 1, scrittore.operazioni.size, "riletto dopo 2 eccezioni, scritto una volta")
        dispatcher.unitaDiLavoro.inTransazione {
            Esito.Ok(dispatcher.pubblica(ElaborazioneCompletata(REG_1, unIncontroDi(REG_1))))
        }
        advanceUntilIdle()
        assertEquals(prima + 2, scrittore.operazioni.size, "il ciclo e ancora vivo")
    }

    // --- AC-185 (startup sweep) ------------------------------------------------------------------

    @Test
    fun `AC-185 all avvio ogni Sbobinatura con un Trascritto viene rigenerata`() = runTest {
        val a = RegistrazioneId("reg-a")
        val b = RegistrazioneId("reg-b")
        val trascritti = mapOf(a to unTrascritto(a, titolo = "Uno"), b to unTrascritto(b, titolo = "Due"))
        val ambiente = Ambiente(testScheduler, trascritti)
        advanceUntilIdle()

        assertEquals(setOf("2026-09-12 Uno.md", "2026-09-12 Due.md"), ambiente.sbobinature().keys)
    }

    // --- AC-186 (event -> policy mapping) ---------------------------------------------------------

    @Test
    fun `AC-186 ElaborazioneCompletata attiva la Rigenerazione`() = runTest {
        assertEventoRigenera(ElaborazioneCompletata(REG_1, unIncontroDi(REG_1)))
    }

    @Test
    fun `AC-186 VociUnite attiva la Rigenerazione`() = runTest {
        assertEventoRigenera(VociUnite(unIncontroDi(REG_1), sopravvissuta = VoceId(1), rimossa = VoceId(2)))
    }

    @Test
    fun `AC-186 VoceDivisa attiva la Rigenerazione`() = runTest {
        assertEventoRigenera(
            VoceDivisa(
                unIncontroDi(REG_1),
                origine = VoceId(1),
                nuova = VoceId(2),
                spostati = listOf(SegmentoRef(REG_1, SegmentoId(2))),
            ),
        )
    }

    @Test
    fun `AC-186 SegmentoRiassegnato attiva la Rigenerazione`() = runTest {
        assertEventoRigenera(
            SegmentoRiassegnato(
                unIncontroDi(REG_1),
                SegmentoRef(REG_1, SegmentoId(1)),
                da = VoceId(1),
                a = VoceId(2),
                daRimossa = false,
                aNuova = true,
            ),
        )
    }

    @Test
    fun `AC-186 AttribuzioneConfermata attiva la Rigenerazione`() = runTest {
        assertEventoRigenera(
            AttribuzioneConfermata(VoceRef(unIncontroDi(REG_1), VoceId(1)), PARLANTE, precedente = null),
        )
    }

    @Test
    fun `AC-186 DataRegistrazioneModificata attiva la Rigenerazione e rimuove il vecchio file`() = runTest {
        val vecchiaData = LocalDate.of(2026, 9, 19)
        val nuovaData = LocalDate.of(2026, 9, 20)
        val ambiente = Ambiente(testScheduler, mapOf(REG_1 to unTrascritto(REG_1, data = nuovaData)))
        advanceUntilIdle()

        ambiente.commit(DataRegistrazioneModificata(REG_1, vecchiaData, nuovaData, unIncontroDi(REG_1)))
        advanceUntilIdle()

        val vecchioFile = ScrittoreSbobinaturaFinta.Operazione.Rimosso("2026-09-19 Riunione.md")
        assertTrue(ambiente.operazioni().contains(vecchioFile))
        assertTrue(ambiente.sbobinature().containsKey("2026-09-20 Riunione.md"))
    }

    @Test
    fun `AC-186bis (rename) RegistrazioneRinominata rigenera come DataRegistrazioneModificata`() = runTest {
        val ambiente = Ambiente(testScheduler, mapOf(REG_1 to unTrascritto(REG_1, titolo = "Titolo Nuovo")))
        advanceUntilIdle()

        ambiente.commit(RegistrazioneRinominata(REG_1, precedente = "Titolo Vecchio", nuovo = "Titolo Nuovo"))
        advanceUntilIdle()

        val vecchioFile = ScrittoreSbobinaturaFinta.Operazione.Rimosso("2026-09-12 Titolo Vecchio.md")
        assertTrue(ambiente.operazioni().contains(vecchioFile))
        assertTrue(ambiente.sbobinature().containsKey("2026-09-12 Titolo Nuovo.md"))
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
            LettoreNomiFinta(
                mapOf(VoceRef(unIncontroDi(conAttribuzione), VoceId(1)) to PARLANTE),
                mapOf(PARLANTE to "Marco Rossi"),
            ),
        )
        advanceUntilIdle()
        val primaDelRename = ambiente.operazioni().size

        ambiente.commit(ParlanteRinominato(PARLANTE, "Marco Rossi"))
        advanceUntilIdle()

        assertEquals(
            listOf(ScrittoreSbobinaturaFinta.Operazione.Scritto("2026-09-12 Uno.md")),
            ambiente.operazioni().drop(primaDelRename),
        )
    }

    // --- AC-I35 (Incontro fan-out, ADR 0035 §7) ----------------------------------------------------

    @Test
    fun `AC-I35 VociUnite rigenera ogni Parte trascritta dell Incontro e nessuna di un altro`() = runTest {
        assertRigeneraSoloLeParti(VociUnite(INCONTRO_I, sopravvissuta = VoceId(1), rimossa = VoceId(2)))
    }

    @Test
    fun `AC-I35 VoceDivisa rigenera ogni Parte dell Incontro e nessuna di un altro`() = runTest {
        assertRigeneraSoloLeParti(
            VoceDivisa(
                INCONTRO_I,
                origine = VoceId(1),
                nuova = VoceId(5),
                spostati = listOf(SegmentoRef(PARTE_B, SegmentoId(2))),
            ),
        )
    }

    @Test
    fun `AC-I35 AttribuzioneConfermata rigenera ogni Parte dell Incontro e nessuna di un altro`() = runTest {
        assertRigeneraSoloLeParti(
            AttribuzioneConfermata(VoceRef(INCONTRO_I, VoceId(1)), PARLANTE, precedente = null),
        )
    }

    private suspend fun TestScope.assertRigeneraSoloLeParti(evento: EventoPubblicato) {
        val altra = RegistrazioneId("altra-incontro")
        val ambiente = ambienteDueParti(altre = mapOf(altra to unTrascritto(altra, titolo = "Altra")))
        advanceUntilIdle()
        val prima = ambiente.operazioni().size

        ambiente.commit(evento)
        advanceUntilIdle()

        // a list, not a set: a duplicate write of a Parte must fail (one write per Parte)
        assertEquals(
            listOf("2026-09-12 Parte A.md", "2026-09-12 Parte B.md"),
            ambiente.operazioni().drop(prima).map { (it as ScrittoreSbobinaturaFinta.Operazione.Scritto).nomeFile }
                .sorted(),
        )
    }

    @Test
    fun `AC-I35 una Parte dell Incontro senza Trascritto non viene scritta`() = runTest {
        val ambiente = ambienteDueParti(parteBTrascritta = false)
        advanceUntilIdle()
        val prima = ambiente.operazioni().size

        ambiente.commit(VociUnite(INCONTRO_I, sopravvissuta = VoceId(1), rimossa = VoceId(2)))
        advanceUntilIdle()

        assertEquals(
            listOf(ScrittoreSbobinaturaFinta.Operazione.Scritto("2026-09-12 Parte A.md")),
            ambiente.operazioni().drop(prima),
        )
    }

    @Test
    fun `AC-I35 ElaborazioneCompletata di B rigenera solo B`() = runTest {
        val ambiente = ambienteDueParti()
        advanceUntilIdle()
        val prima = ambiente.operazioni().size

        ambiente.commit(ElaborazioneCompletata(PARTE_B, INCONTRO_I))
        advanceUntilIdle()

        assertEquals(
            listOf(ScrittoreSbobinaturaFinta.Operazione.Scritto("2026-09-12 Parte B.md")),
            ambiente.operazioni().drop(prima),
        )
    }

    @Test
    fun `AC-I35 DataRegistrazioneModificata di B rigenera solo B`() = runTest {
        val ambiente = ambienteDueParti()
        advanceUntilIdle()
        val prima = ambiente.operazioni().size

        ambiente.commit(
            DataRegistrazioneModificata(PARTE_B, LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 12), INCONTRO_I),
        )
        advanceUntilIdle()

        assertEquals(
            listOf(ScrittoreSbobinaturaFinta.Operazione.Scritto("2026-09-12 Parte B.md")),
            ambiente.operazioni().drop(prima),
        )
    }

    @Test
    fun `AC-I35 OraDiInizioModificata non rigenera nulla`() = runTest {
        val ambiente = ambienteDueParti()
        advanceUntilIdle()
        val prima = ambiente.operazioni().size

        ambiente.commit(OraDiInizioModificata(PARTE_B, INCONTRO_I, precedente = null, nuova = LocalTime.of(9, 30)))
        advanceUntilIdle()

        assertEquals(emptyList(), ambiente.operazioni().drop(prima))
    }

    @Test
    fun `AC-I35 il rename di un Parlante rigenera ogni Parte di ogni Incontro in incontriCon`() = runTest {
        val altra = RegistrazioneId("altra")
        val ambiente = ambienteDueParti(
            altre = mapOf(altra to unTrascritto(altra, titolo = "Altra")),
        )
        advanceUntilIdle()
        val prima = ambiente.operazioni().size

        ambiente.commit(ParlanteRinominato(PARLANTE, "Marco Rossi"))
        advanceUntilIdle()

        // a list, not a set: a duplicate write of a Parte must fail (one write per Parte)
        assertEquals(
            listOf("2026-09-12 Parte A.md", "2026-09-12 Parte B.md"),
            ambiente.operazioni().drop(prima).map { (it as ScrittoreSbobinaturaFinta.Operazione.Scritto).nomeFile }
                .sorted(),
        )
    }

    @Test
    fun `INV-23 rigenerare una Parte non toccata scrive byte-identico`() = runTest {
        val ambiente = ambienteDueParti()
        advanceUntilIdle()
        val prima = ambiente.sbobinature()
        val scritturePrima = ambiente.operazioni().size

        ambiente.commit(VociUnite(INCONTRO_I, sopravvissuta = VoceId(1), rimossa = VoceId(2)))
        advanceUntilIdle()

        assertEquals(2, ambiente.operazioni().size - scritturePrima, "le due Parti sono state riscritte davvero")
        assertEquals(prima, ambiente.sbobinature())
    }

    @Test
    fun `INV-24 la Sbobinatura della Parte 2 rende il Nome dell Incontro e Voce n per le non attribuite`() = runTest {
        val ambiente = ambienteDueParti()
        advanceUntilIdle()

        val parteB = ambiente.sbobinature().getValue("2026-09-12 Parte B.md")

        assertTrue(parteB.contains("**Marco Rossi** (0:00): Ciao."))
        assertTrue(parteB.contains("**Voce 4** (0:02): Altro."))
    }

    private fun TestScope.ambienteDueParti(
        parteBTrascritta: Boolean = true,
        altre: Map<RegistrazioneId, TrascrittoTesto> = emptyMap(),
    ): Ambiente {
        val parteB = unTrascritto(PARTE_B, titolo = "Parte B", incontro = INCONTRO_I).copy(
            segmenti = listOf(
                SegmentoVista(SegmentoId(1), VoceId(1), IntervalloMs(0, 1_000), "Ciao."),
                SegmentoVista(SegmentoId(2), VoceId(4), IntervalloMs(2_000, 3_000), "Altro."),
            ),
        )
        val trascritti = buildMap {
            put(PARTE_A, unTrascritto(PARTE_A, titolo = "Parte A", incontro = INCONTRO_I))
            if (parteBTrascritta) put(PARTE_B, parteB)
            putAll(altre)
        }
        return Ambiente(
            testScheduler,
            trascritti,
            LettoreNomiFinta(
                mapOf(VoceRef(INCONTRO_I, VoceId(1)) to PARLANTE),
                mapOf(PARLANTE to "Marco Rossi"),
            ),
        )
    }

    @Test
    fun `AC-186 ParlantePromosso con nomeCambiato false non attiva alcuna Rigenerazione`() = runTest {
        val ambiente = Ambiente(
            testScheduler,
            mapOf(REG_1 to unTrascritto(REG_1)),
            LettoreNomiFinta(mapOf(VoceRef(unIncontroDi(REG_1), VoceId(1)) to PARLANTE), mapOf(PARLANTE to "Marco")),
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
            LettoreNomiFinta(mapOf(VoceRef(unIncontroDi(REG_1), VoceId(1)) to PARLANTE), mapOf(PARLANTE to "Marco")),
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

    // --- AC-C45 (structural): one RitentaConBackoff, one sealed key with exactly four cases -----------

    /**
     * By REFLECTION, never by reading the module's own source text: ADR 0010's `enforced_by` forbids any
     * file-reading call under `sbobinatura/`, including test sources (the check has no main-vs-test
     * exception), so a `File(...).readText()` structural assertion — legitimate in `:supporto`'s own
     * `RitentaConBackoffTest` — would trip it here. The absence of the old hand-rolled channel/backoff/
     * `runCatching` is instead proven by the diff itself and by every behavioural AC below staying green
     * (AC-C46..C49/AC-C92/C94) on the NEW single-worker design.
     */
    @Test
    fun `AC-C45 AbbonatoSbobinaturaEventi ha un solo RitentaConBackoff e una chiave sigillata a quattro casi`() {
        val chiave = AbbonatoSbobinaturaEventi::class.java.declaredClasses.single { it.simpleName == "Chiave" }
        val casi = chiave.declaredClasses.filter { it != chiave && chiave.isAssignableFrom(it) }

        val attesi = setOf("PerRegistrazione", "PerParlante", "PerIncontro", "Sweep")
        assertEquals(attesi, casi.map { it.simpleName }.toSet())
        val campiRitentaConBackoff = AbbonatoSbobinaturaEventi::class.java.declaredFields
            .count { it.type == RitentaConBackoff::class.java }
        assertEquals(1, campiRitentaConBackoff, "un solo campo RitentaConBackoff: le quattro specie lo condividono")
    }

    // --- AC-C46/AC-C47: a poisoned unit never blocks another's own progress ---------------------------

    @Test
    fun `AC-C46 una Registrazione che fallisce per sempre non blocca la rigenerazione di un altra`() = runTest {
        val poisoned = REG_1
        val altra = RegistrazioneId("reg-c46-altra")
        val trascritti = mapOf(
            poisoned to unTrascritto(poisoned, titolo = "X"),
            altra to unTrascritto(altra, titolo = "Y"),
        )
        val lettore = LettoreCheLanciaPer(poisoned, LettoreTrascrittoFinta(trascritti))
        val scrittore = ScrittoreSbobinaturaFinta()
        val dispatcher = DispatcherEventiInMemoria(UnitaDiLavoroFinta())
        val politica = RigenerazioneSbobinaturaPolitica(lettore, LettoreNomiFinta(), scrittore)
        val segnalazioni = SegnalazioniRegistrate()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        abbonaSbobinatura(dispatcher, politica, lettore::registrazioniConTrascritto, scope, segnalazioni)
        try {
            advanceTimeBy(1.seconds)
            runCurrent() // lo sweep di avvio: puo' fallire su poisoned, irrilevante qui

            dispatcher.unitaDiLavoro.inTransazione {
                Esito.Ok(dispatcher.pubblica(ElaborazioneCompletata(poisoned, unIncontroDi(poisoned))))
            }
            dispatcher.unitaDiLavoro.inTransazione {
                Esito.Ok(dispatcher.pubblica(ElaborazioneCompletata(altra, unIncontroDi(altra))))
            }
            advanceTimeBy(10.seconds)
            runCurrent()

            assertTrue(scrittore.sbobinature.containsKey("2026-09-12 Y.md"), "altra rigenerata nonostante X fallisca")
            assertFalse(scrittore.sbobinature.containsKey("2026-09-12 X.md"))
            val righeX = segnalazioni.tutte.filter { poisoned.valore in it.messaggio }
            assertTrue(righeX.size >= 2, "un report per ogni tentativo fallito di X, mai uno solo: $righeX")
            assertTrue(righeX.all { it.causa is IllegalStateException }, "$righeX")
        } finally {
            // poisoned ritenta per sempre: senza cancellare lo scope, il drain automatico di fine-runTest
            // continuerebbe ad avanzare il tempo virtuale all'infinito inseguendo un lavoro che non finisce mai.
            scope.cancel()
        }
    }

    @Test
    fun `AC-C47 lo sweep in fan-out, X avvelenata gia nota all avvio non impedisce ne riscrive Y`() = runTest {
        val poisoned = REG_1
        val altra = RegistrazioneId("reg-c47-altra")
        // Entrambe note GIA' all'avvio, nello STESSO elenco dello sweep, X PRIMA di Y: lo sweep deve accodare
        // ciascuna nella propria Chiave.PerRegistrazione (non ripiegare sul fold tutto-o-niente della policy,
        // che si fermerebbe alla prima e non arriverebbe mai a Y).
        val trascritti = mapOf(
            poisoned to unTrascritto(poisoned, titolo = "X"),
            altra to unTrascritto(altra, titolo = "Y"),
        )
        val lettore = LettoreCheLanciaPer(poisoned, LettoreTrascrittoFinta(trascritti))
        val scrittore = ScrittoreSbobinaturaFinta()
        val dispatcher = DispatcherEventiInMemoria(UnitaDiLavoroFinta())
        val politica = RigenerazioneSbobinaturaPolitica(lettore, LettoreNomiFinta(), scrittore)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        abbonaSbobinatura(
            dispatcher,
            politica,
            lettore::registrazioniConTrascritto,
            scope,
            Segnalazione { _, _ -> },
        )

        try {
            advanceTimeBy(120.seconds) // molti ritenti VIRTUALI di X: mai un busy loop reale, ne una advanceUntilIdle
            runCurrent() // (X ritenta per sempre: un advanceUntilIdle qui non terminerebbe mai)

            assertTrue(
                scrittore.sbobinature.containsKey("2026-09-12 Y.md"),
                "Y e' scritta nonostante lo sweep avveleni su X",
            )
            assertFalse(scrittore.sbobinature.containsKey("2026-09-12 X.md"), "X resta avvelenata, mai scritta")
            val scrittureY = scrittore.operazioni.count {
                it == ScrittoreSbobinaturaFinta.Operazione.Scritto("2026-09-12 Y.md")
            }
            assertEquals(
                1,
                scrittureY,
                "i ritenti di X (una Chiave separata, un backoff separato) non riscrivono mai Y",
            )
        } finally {
            // poisoned ritenta per sempre: senza cancellare lo scope qui (anche su un'asserzione fallita), il
            // drain automatico di fine-runTest continuerebbe ad avanzare il tempo virtuale all'infinito
            // inseguendo un lavoro che non finisce mai (lessons-by-block-type.md: fail, non hang, il gate).
            scope.cancel()
        }
    }

    @Test
    fun `AC-C47 lo sweep avvelenato non impedisce una Registrazione nota solo dopo, via evento`() = runTest {
        val poisoned = REG_1
        val altra = RegistrazioneId("reg-c47-altra-evento")
        val lettore = LettoreCheLanciaPer(poisoned, LettoreTrascrittoFinta(mapOf(poisoned to unTrascritto(poisoned))))
        val scrittore = ScrittoreSbobinaturaFinta()
        val dispatcher = DispatcherEventiInMemoria(UnitaDiLavoroFinta())
        val politica = RigenerazioneSbobinaturaPolitica(lettore, LettoreNomiFinta(), scrittore)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        abbonaSbobinatura(
            dispatcher,
            politica,
            lettore::registrazioniConTrascritto,
            scope,
            Segnalazione { _, _ -> },
        )
        advanceTimeBy(1.seconds)
        runCurrent() // lo sweep di avvio fallisce subito su poisoned e continua a ritentare in background

        // "Meanwhile": altra diventa nota solo ora (la sua Elaborazione completa dopo l'avvio) — lo sweep,
        // bloccato a ritentare poisoned, non deve impedire la SUA rigenerazione (guidata dall'evento).
        lettore.aggiungi(altra, unTrascritto(altra, titolo = "Y"))
        dispatcher.unitaDiLavoro.inTransazione {
            Esito.Ok(dispatcher.pubblica(ElaborazioneCompletata(altra, unIncontroDi(altra))))
        }
        advanceTimeBy(5.seconds)
        runCurrent()

        assertTrue(
            scrittore.sbobinature.containsKey("2026-09-12 Y.md"),
            "altra e' rigenerata nonostante lo sweep avveleni su X",
        )

        // poisoned ritenta per sempre: senza cancellare lo scope qui, il drain automatico di fine-runTest
        // continuerebbe ad avanzare il tempo virtuale all'infinito inseguendo un lavoro che non finisce mai.
        scope.cancel()
    }

    // --- AC-C48: scope cancellation stops the loop; an Error escapes instead of being retried --------

    @Test
    fun `AC-C48 il worker si ferma quando lo scope e cancellato, senza segnalare ne rigenerare oltre`() = runTest {
        val scrittore = ScrittoreSbobinaturaFinta()
        val dispatcher = DispatcherEventiInMemoria(UnitaDiLavoroFinta())
        val trascritti = LettoreTrascrittoFinta(mapOf(REG_1 to unTrascritto(REG_1)))
        val politica = RigenerazioneSbobinaturaPolitica(trascritti, LettoreNomiFinta(), scrittore)
        val segnalazioni = SegnalazioniRegistrate()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        abbonaSbobinatura(dispatcher, politica, trascritti::registrazioniConTrascritto, scope, segnalazioni)
        advanceUntilIdle() // sweep di avvio
        val primaDellaCancellazione = scrittore.operazioni.size

        scope.cancel()
        dispatcher.unitaDiLavoro.inTransazione {
            dispatcher.pubblica(ElaborazioneCompletata(REG_1, unIncontroDi(REG_1)))
            Esito.Ok(Unit)
        }
        advanceUntilIdle()

        assertEquals(primaDellaCancellazione, scrittore.operazioni.size)
        assertTrue(segnalazioni.tutte.isEmpty(), "nessuna segnalazione: ${segnalazioni.tutte}")
    }

    @Test
    fun `AC-C48 un Error nella rigenerazione esce verso il gestore dello scope invece di essere ritentato`() =
        runTest {
            val scrittore = ScrittoreCheLanciaUnErrore()
            val dispatcher = DispatcherEventiInMemoria(UnitaDiLavoroFinta())
            val trascritti = LettoreTrascrittoFinta(mapOf(REG_1 to unTrascritto(REG_1)))
            val politica = RigenerazioneSbobinaturaPolitica(trascritti, LettoreNomiFinta(), scrittore)
            val segnalazioni = SegnalazioniRegistrate()
            val sfuggiti = mutableListOf<Throwable>()
            val scope = CoroutineScope(
                StandardTestDispatcher(testScheduler) + CoroutineExceptionHandler { _, e -> sfuggiti += e },
            )
            abbonaSbobinatura(dispatcher, politica, trascritti::registrazioniConTrascritto, scope, segnalazioni)
            advanceUntilIdle()

            assertEquals(1, scrittore.tentativi, "un solo tentativo: l'Error non e' un ritento")
            assertTrue(segnalazioni.tutte.isEmpty(), "un Error non e' segnalato come fallimento: ${segnalazioni.tutte}")
            assertEquals(1, sfuggiti.size)
            assertIs<OutOfMemoryError>(sfuggiti.single())
            assertTrue(scope.coroutineContext[Job]?.isCancelled == true)
        }

    // --- AC-C92: the three unit kinds share ONE RitentaConBackoff, never run together ------------------

    @Test
    @Suppress("MaxLineLength", "MaximumLineLength") // the test name alone crosses 120 columns
    fun `AC-C92 le tre specie di lavoro non girano mai insieme, condividono un solo RitentaConBackoff`() =
        conScopeDiProva { scope ->
            val dentro = CountDownLatch(1)
            val procedi = CountDownLatch(1)
            val primaVolta = AtomicBoolean(true)
            val concorrenti = AtomicInteger(0)
            val massimoConcorrenti = AtomicInteger(0)
            val chiamate = AtomicInteger(0)
            val scrittore = object : ScrittoreSbobinatura {
                override fun scrivi(nomeFile: String, markdown: String) {
                    chiamate.incrementAndGet()
                    val n = concorrenti.incrementAndGet()
                    massimoConcorrenti.updateAndGet { max(it, n) }
                    if (primaVolta.compareAndSet(true, false)) {
                        dentro.countDown()
                        assertTrue(procedi.await(10, TimeUnit.SECONDS), "il test avrebbe dovuto sbloccare in tempo")
                    }
                    concorrenti.decrementAndGet()
                }

                override fun rimuovi(nomeFile: String) = Unit
            }
            val reg2 = RegistrazioneId("reg-c92-2")
            val trascritti = LettoreTrascrittoFinta(
                mapOf(REG_1 to unTrascritto(REG_1), reg2 to unTrascritto(reg2, titolo = "Due")),
            )
            val nomi = LettoreNomiFinta(
                mapOf(VoceRef(unIncontroDi(REG_1), VoceId(1)) to PARLANTE),
                mapOf(PARLANTE to "Marco"),
            )
            val dispatcher = DispatcherEventiInMemoria(UnitaDiLavoroFinta())
            val politica = RigenerazioneSbobinaturaPolitica(trascritti, nomi, scrittore)
            abbonaSbobinatura(
                dispatcher,
                politica,
                trascritti::registrazioniConTrascritto,
                scope,
                Segnalazione { _, _ -> },
            )

            // Lo sweep stesso non scrive (AC-C47: lista soltanto e fa il fan-out); e' la SUA prima unita' fanned-out
            // (PerRegistrazione(reg-1)) a bloccarsi qui.
            assertTrue(dentro.await(10, TimeUnit.SECONDS), "il fan-out dello sweep avrebbe dovuto partire")

            // Richieste "nel frattempo": reg2 e' GIA' pendente (accodata dal fan-out dello sweep, non ancora
            // girata) e si fonde nella stessa chiave; PerParlante e' tutta nuova. Entrambe restano in coda,
            // proprio perche' condividono l'UNICA istanza di RitentaConBackoff (sequenziale).
            dispatcher.unitaDiLavoro.inTransazione {
                Esito.Ok(dispatcher.pubblica(ElaborazioneCompletata(reg2, unIncontroDi(reg2))))
            }
            dispatcher.unitaDiLavoro.inTransazione {
                Esito.Ok(dispatcher.pubblica(ParlanteRinominato(PARLANTE, "Marco Rossi")))
            }
            // 1 = solo reg-1, gia' bloccato: ne' reg2 ne' PerParlante partono finche' non e' sbloccato.
            assertEquals(1, concorrenti.get(), "nessuna delle due gira finche' reg-1 e' bloccata")

            procedi.countDown()
            attendiFinche(messaggio = "le tre unita' avrebbero dovuto completarsi: ${chiamate.get()} chiamate") {
                // fan-out dello sweep: reg-1 (bloccata sopra) + reg2 (fusa con l'evento, UNA sola scrittura),
                // poi PerParlante(PARLANTE) su reg-1: 3 scritture totali, mai piu' di 4 (nessuna duplicata).
                chiamate.get() >= 3
            }

            assertEquals(1, massimoConcorrenti.get(), "mai piu' di un'unita' di lavoro in volo insieme (AC-C92)")
        }

    // --- AC-C94: a failure merged concurrently is retried ONCE with both precedenti fused ------------

    @Test
    fun `AC-C94 un guasto durante il tentativo si ritenta una sola volta con entrambi i precedenti fusi`() = runTest {
        val vecchiaData = LocalDate.of(2026, 9, 19)
        val nuovaData = LocalDate.of(2026, 9, 20)
        val dispatcher = DispatcherEventiInMemoria(UnitaDiLavoroFinta())
        val finta = ScrittoreSbobinaturaFinta()
        var primoTentativo = true
        val scrittore = object : ScrittoreSbobinatura by finta {
            override fun scrivi(nomeFile: String, markdown: String) {
                if (primoTentativo) {
                    primoTentativo = false
                    // Arriva ANCHE la rinomina, DURANTE questo tentativo (che sta per fallire): si fonde nel
                    // payload gia' fallito quando viene rimesso in coda (AC-C94, primaArrivata).
                    dispatcher.unitaDiLavoro.inTransazione {
                        val evento =
                            RegistrazioneRinominata(REG_1, precedente = "Titolo Vecchio", nuovo = "Titolo Nuovo")
                        Esito.Ok(dispatcher.pubblica(evento))
                    }
                    throw IOException("guasto simulato")
                }
                finta.scrivi(nomeFile, markdown)
            }
        }
        // Lo sweep di avvio non trova nulla (nessun test-inquinamento): la Rigenerazione qui e' guidata SOLO
        // dall'evento DataRegistrazioneModificata, come l'AC descrive.
        val trascritti = object : LettoreTrascritto {
            override fun trascritto(id: RegistrazioneId) =
                if (id == REG_1) unTrascritto(REG_1, titolo = "Titolo Nuovo", data = nuovaData) else null

            override fun partiConTrascritto(incontroId: IncontroId): List<RegistrazioneId> =
                listOf(REG_1).filter { unIncontroDi(it) == incontroId }

            override fun registrazioniConTrascritto(): List<RegistrazioneId> = emptyList()
        }
        val politica = RigenerazioneSbobinaturaPolitica(trascritti, LettoreNomiFinta(), scrittore)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        abbonaSbobinatura(
            dispatcher,
            politica,
            trascritti::registrazioniConTrascritto,
            scope,
            Segnalazione { _, _ -> },
        )
        advanceUntilIdle() // sweep di avvio: non trova nulla

        dispatcher.unitaDiLavoro.inTransazione {
            val evento = DataRegistrazioneModificata(REG_1, vecchiaData, nuovaData, unIncontroDi(REG_1))
            Esito.Ok(dispatcher.pubblica(evento))
        }
        advanceUntilIdle()

        val scritture = finta.operazioni.count { it is ScrittoreSbobinaturaFinta.Operazione.Scritto }
        assertEquals(1, scritture, "ritentato una sola volta")
        val vecchioFile = ScrittoreSbobinaturaFinta.Operazione.Rimosso("2026-09-19 Titolo Vecchio.md")
        assertTrue(finta.operazioni.contains(vecchioFile))
        assertTrue(finta.sbobinature.containsKey("2026-09-20 Titolo Nuovo.md"))
        assertFalse(finta.sbobinature.containsKey("2026-09-19 Titolo Vecchio.md"))
    }

    // --- helpers ----------------------------------------------------------------------------------

    /**
     * Constructs an [Ambiente] with a Trascritto already present for [REG_1] (so the AC-185 sweep
     * settles first), then commits [evento] and asserts it produced exactly one MORE successful
     * write — proof [AbbonatoSbobinaturaEventi] translated it into a [RigenerazioneSbobinaturaPolitica]
     * call (which event maps to which call is proven by `RigenerazioneSbobinaturaPoliticaTest`).
     */
    private suspend fun TestScope.assertEventoRigenera(evento: EventoPubblicato) {
        val ambiente = Ambiente(
            testScheduler,
            mapOf(REG_1 to unTrascritto(REG_1)),
            LettoreNomiFinta(mapOf(VoceRef(unIncontroDi(REG_1), VoceId(1)) to PARLANTE), mapOf(PARLANTE to "Marco")),
        )
        advanceUntilIdle()
        val prima = ambiente.operazioni().size

        ambiente.commit(evento)
        advanceUntilIdle()

        assertEquals(prima + 1, ambiente.operazioni().size)
    }

    /**
     * [LettoreTrascritto] whose next `n` [trascritto] calls throw like a half-written read (D-0008: the
     * `ricostituisci` `require` escaping `VociDellIncontroRepositorySql.trova`), then delegates.
     */
    private class LettoreCheLancia(private val delegato: LettoreTrascritto) : LettoreTrascritto by delegato {
        private var lanciRimanenti = 0

        fun lanciaProssimeLetture(n: Int) {
            lanciRimanenti = n
        }

        override fun trascritto(id: RegistrazioneId): TrascrittoTesto? {
            if (lanciRimanenti > 0) {
                lanciRimanenti--
                throw IllegalArgumentException("prossimaVoce 3 non oltre le Voci")
            }
            return delegato.trascritto(id)
        }
    }

    /**
     * [LettoreTrascritto] that always throws for [poison] (a PERMANENT guasto — unlike [LettoreCheLancia]'s
     * bounded one). [aggiungi] grows the rest on the fly: a test can simulate a Registrazione becoming known
     * only AFTER construction (AC-C47's "meanwhile").
     */
    private class LettoreCheLanciaPer(
        private val poison: RegistrazioneId,
        private val delegato: LettoreTrascritto,
    ) : LettoreTrascritto {
        private val extra = ConcurrentHashMap<RegistrazioneId, TrascrittoTesto>()

        fun aggiungi(id: RegistrazioneId, trascritto: TrascrittoTesto) {
            extra[id] = trascritto
        }

        override fun trascritto(id: RegistrazioneId): TrascrittoTesto? {
            if (id == poison) error("guasto permanente per $id")
            return extra[id] ?: delegato.trascritto(id)
        }

        override fun partiConTrascritto(incontroId: IncontroId): List<RegistrazioneId> =
            delegato.partiConTrascritto(incontroId) + extra.values.filter { it.incontroId == incontroId }
                .map { it.registrazioneId }

        override fun registrazioniConTrascritto(): List<RegistrazioneId> =
            delegato.registrazioniConTrascritto() + extra.keys
    }

    /**
     * [ScrittoreSbobinatura] that fails the next `n` calls to [scrivi] (armed by
     * [fallisciProssimeScritture]), then succeeds.
     */
    private class ScrittoreConGuasti : ScrittoreSbobinatura {
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

    /** [ScrittoreSbobinatura] whose first [scrivi] throws a real [Error] (AC-C48): never a retry candidate. */
    private class ScrittoreCheLanciaUnErrore : ScrittoreSbobinatura {
        var tentativi = 0
            private set

        override fun scrivi(nomeFile: String, markdown: String) {
            tentativi++
            throw OutOfMemoryError("finto")
        }

        override fun rimuovi(nomeFile: String) = Unit
    }

    /** A recording [Segnalazione] for the tests (thread-safe: AC-C92 requests it from real threads). */
    private class SegnalazioniRegistrate : Segnalazione {
        data class Riga(val messaggio: String, val causa: Throwable?)

        private val righe = mutableListOf<Riga>()

        val tutte: List<Riga> get() = synchronized(righe) { righe.toList() }

        override fun segnala(messaggio: String, causa: Throwable?) {
            synchronized(righe) { righe += Riga(messaggio, causa) }
        }
    }

    private companion object {
        val REG_1 = RegistrazioneId("reg-1")
        val PARTE_A = RegistrazioneId("parte-a")
        val PARTE_B = RegistrazioneId("parte-b")
        val INCONTRO_I = IncontroId("incontro-i")
        val PARLANTE = ParlanteId("parlante-1")

        fun unTrascritto(
            id: RegistrazioneId,
            titolo: String = "Riunione",
            data: LocalDate = LocalDate.of(2026, 9, 12),
            incontro: IncontroId = unIncontroDi(id),
        ) = TrascrittoTesto(
            registrazioneId = id,
            incontroId = incontro,
            titolo = titolo,
            dataRegistrazione = data,
            segmenti = listOf(SegmentoVista(SegmentoId(1), VoceId(1), IntervalloMs(0, 1_000), "Ciao.")),
        )
    }
}
