package snastro.sbobinatura.adattatori.eventi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import snastro.kernel.DispatcherEventiInMemoria
import snastro.kernel.Esito
import snastro.kernel.EventoPubblicato
import snastro.kernel.IncontroId
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.SegmentoRef
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.VoceId
import snastro.sbobinatura.applicazione.politiche.RigenerazioneSbobinaturaPolitica
import snastro.sbobinatura.applicazione.porte.LettoreNomiFinta
import snastro.sbobinatura.applicazione.porte.LettoreTrascritto
import snastro.sbobinatura.applicazione.porte.LettoreTrascrittoFinta
import snastro.sbobinatura.applicazione.porte.ScrittoreSbobinaturaFinta
import snastro.sbobinatura.applicazione.porte.SegmentoVista
import snastro.sbobinatura.applicazione.porte.TrascrittoTesto
import snastro.supporto.Segnalazione
import snastro.trascrizione.applicazione.eventi.ElaborazioneCompletata
import snastro.trascrizione.applicazione.eventi.SegmentoRiassegnato
import snastro.trascrizione.applicazione.eventi.TrascrittoEliminato
import snastro.trascrizione.applicazione.eventi.VociUnite
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** The Incontro-wide fan-out of [AbbonatoSbobinaturaEventi] (AC-I63, ADR 0038 / ADR 0035 §7). */
@OptIn(ExperimentalCoroutinesApi::class)
class AbbonatoSbobinaturaIncontroTest {

    // --- AC-I63 (TrascrittoEliminato, ADR 0038) ----------------------------------------------------

    @Test
    fun `AC-I63 eliminata la Parte 2 di 3 con una Voce rimossa le Parti 1 e 3 si rigenerano senza quel numero`() =
        runTest {
            val parte1 = RegistrazioneId("p1")
            val parte2 = RegistrazioneId("p2")
            val parte3 = RegistrazioneId("p3")
            fun parte(id: RegistrazioneId, titolo: String, vociParlanti: List<Int>) =
                unTrascritto(id, titolo, INCONTRO_I).copy(
                    segmenti = vociParlanti.mapIndexed { i, v ->
                        val inizio = i * 1_000L
                        SegmentoVista(SegmentoId(i + 1), VoceId(v), IntervalloMs(inizio, inizio + 500), "Testo $v.")
                    },
                )
            val trascritti = mutableMapOf(
                parte1 to parte(parte1, "Parte 1", listOf(1, 2)),
                parte2 to parte(parte2, "Parte 2", listOf(2)),
                parte3 to parte(parte3, "Parte 3", listOf(1, 2, 3)),
            )
            val ordine = listOf(parte1, parte2, parte3)
            val lettore = object : LettoreTrascritto {
                override fun trascritto(id: RegistrazioneId) = trascritti[id]
                override fun partiConTrascritto(incontroId: IncontroId) = ordine.filter { it in trascritti }
                override fun registrazioniConTrascritto() = trascritti.keys.toList()
            }
            val scrittore = ScrittoreSbobinaturaFinta()
            val dispatcher = DispatcherEventiInMemoria(UnitaDiLavoroFinta())
            abbonaSbobinatura(
                dispatcher,
                RigenerazioneSbobinaturaPolitica(lettore, LettoreNomiFinta(), scrittore),
                lettore::registrazioniConTrascritto,
                CoroutineScope(StandardTestDispatcher(testScheduler)),
                Segnalazione { _, _ -> },
                lettore::partiConTrascritto,
            )
            advanceUntilIdle()
            assertTrue(scrittore.sbobinature.getValue("2026-09-12 Parte 1.md").contains("Voce 2"))
            val prima = scrittore.operazioni.size

            // the deleting unit: Parte 2 and its Voce 2 are gone from Trascrizione, and Voce 2 from Parti 1 and 3
            trascritti.remove(parte2)
            trascritti[parte1] = parte(parte1, "Parte 1", listOf(1))
            trascritti[parte3] = parte(parte3, "Parte 3", listOf(1, 3))
            dispatcher.unitaDiLavoro.inTransazione {
                dispatcher.pubblica(TrascrittoEliminato(parte2, INCONTRO_I, setOf(VoceId(2))))
                Esito.Ok(Unit)
            }
            advanceUntilIdle()

            assertEquals(
                setOf("2026-09-12 Parte 1.md", "2026-09-12 Parte 3.md"),
                scrittore.operazioni.drop(prima).map { (it as ScrittoreSbobinaturaFinta.Operazione.Scritto).nomeFile }
                    .toSet(),
            )
            listOf("2026-09-12 Parte 1.md", "2026-09-12 Parte 3.md").forEach {
                assertFalse(scrittore.sbobinature.getValue(it).contains("Voce 2"), it)
            }
        }

    @Test
    fun `AC-I63 un guasto nel listare le Parti non risale al comando gia committato ed e ritentato`() = runTest {
        val lettore = LettoreTrascrittoFinta(
            mapOf(PARTE_A to unTrascritto(PARTE_A, "Parte A", INCONTRO_I)),
        )
        var guasti = 1
        val scrittore = ScrittoreSbobinaturaFinta()
        val dispatcher = DispatcherEventiInMemoria(UnitaDiLavoroFinta())
        abbonaSbobinatura(
            dispatcher,
            RigenerazioneSbobinaturaPolitica(lettore, LettoreNomiFinta(), scrittore),
            lettore::registrazioniConTrascritto,
            CoroutineScope(StandardTestDispatcher(testScheduler)),
            Segnalazione { _, _ -> },
        ) { incontro ->
            if (guasti-- > 0) error("lettura guasta")
            lettore.partiConTrascritto(incontro)
        }
        advanceUntilIdle()
        val prima = scrittore.operazioni.size

        dispatcher.unitaDiLavoro.inTransazione {
            dispatcher.pubblica(VociUnite(INCONTRO_I, sopravvissuta = VoceId(1), rimossa = VoceId(2)))
            Esito.Ok(Unit)
        }
        advanceUntilIdle()

        assertEquals(
            listOf(ScrittoreSbobinaturaFinta.Operazione.Scritto("2026-09-12 Parte A.md")),
            scrittore.operazioni.drop(prima),
        )
    }

    // --- AC-I35 SegmentoRiassegnato: the Incontro's Parti, and no Parte of another Incontro --------------------

    @Test
    fun `AC-I35 SegmentoRiassegnato rigenera ogni Parte dell Incontro e nessuna di un altro`() = runTest {
        val parteB = RegistrazioneId("parte-b")
        val altra = RegistrazioneId("altra-incontro")
        val lettore = LettoreTrascrittoFinta(
            mapOf(
                PARTE_A to unTrascritto(PARTE_A, "Parte A", INCONTRO_I),
                parteB to unTrascritto(parteB, "Parte B", INCONTRO_I),
                altra to unTrascritto(altra, "Altra", IncontroId("altro-incontro")),
            ),
        )
        val scrittore = ScrittoreSbobinaturaFinta()
        val dispatcher = DispatcherEventiInMemoria(UnitaDiLavoroFinta())
        abbonaSbobinatura(
            dispatcher,
            RigenerazioneSbobinaturaPolitica(lettore, LettoreNomiFinta(), scrittore),
            lettore::registrazioniConTrascritto,
            CoroutineScope(StandardTestDispatcher(testScheduler)),
            Segnalazione { _, _ -> },
            lettore::partiConTrascritto,
        )
        advanceUntilIdle()
        val prima = scrittore.operazioni.size

        dispatcher.unitaDiLavoro.inTransazione {
            dispatcher.pubblica(
                SegmentoRiassegnato(
                    INCONTRO_I,
                    SegmentoRef(parteB, SegmentoId(1)),
                    da = VoceId(1),
                    a = VoceId(2),
                    daRimossa = false,
                    aNuova = false,
                ),
            )
            Esito.Ok(Unit)
        }
        advanceUntilIdle()

        assertEquals(
            listOf("2026-09-12 Parte A.md", "2026-09-12 Parte B.md"),
            scrittore.operazioni.drop(prima).map { (it as ScrittoreSbobinaturaFinta.Operazione.Scritto).nomeFile }
                .sorted(),
        )
    }

    // --- AC-183: one write for ANY order of a burst mixing the Registrazione's own event and Incontro-wide ones ----

    @Test
    fun `AC-183 una sola scrittura per qualunque ordine della raffica, duplicati compresi`() = runTest {
        val proprio = ElaborazioneCompletata(PARTE_A, INCONTRO_I)
        val unite = VociUnite(INCONTRO_I, sopravvissuta = VoceId(1), rimossa = VoceId(2))
        val eliminato = TrascrittoEliminato(RegistrazioneId("altra"), INCONTRO_I, setOf(VoceId(2)))
        val ordini = listOf(
            listOf(proprio, unite, eliminato),
            listOf(unite, eliminato, proprio),
            listOf(unite, proprio, eliminato),
            listOf(unite, proprio, eliminato, proprio, unite),
        )
        ordini.forEach { raffica ->
            val lettore = LettoreTrascrittoFinta(mapOf(PARTE_A to unTrascritto(PARTE_A, "Parte A", INCONTRO_I)))
            val scrittore = ScrittoreSbobinaturaFinta()
            val dispatcher = DispatcherEventiInMemoria(UnitaDiLavoroFinta())
            abbonaSbobinatura(
                dispatcher,
                RigenerazioneSbobinaturaPolitica(lettore, LettoreNomiFinta(), scrittore),
                lettore::registrazioniConTrascritto,
                CoroutineScope(StandardTestDispatcher(testScheduler)),
                Segnalazione { _, _ -> },
                lettore::partiConTrascritto,
            )
            advanceUntilIdle()
            val prima = scrittore.operazioni.size

            raffica.forEach { evento ->
                dispatcher.unitaDiLavoro.inTransazione {
                    dispatcher.pubblica(evento)
                    Esito.Ok(Unit)
                }
            }
            advanceUntilIdle()

            assertEquals(1, scrittore.operazioni.size - prima, "ordine: ${raffica.map { it::class.simpleName }}")
        }
    }

    // --- AC-C46/AC-C47 for the Incontro fan-out: one failing listing never blocks another Registrazione ---------

    @Test
    fun `AC-C46 un passo indietro per un elenco Parti non azzera il fallimento di una Registrazione che fallisce`() =
        runTest {
            val poisoned = RegistrazioneId("reg-poisoned")
            val lettore = object : LettoreTrascritto {
                override fun trascritto(id: RegistrazioneId): TrascrittoTesto? = error("guasto permanente per $id")
                override fun partiConTrascritto(incontroId: IncontroId) = emptyList<RegistrazioneId>()
                override fun registrazioniConTrascritto() = emptyList<RegistrazioneId>()
            }
            val dispatcher = DispatcherEventiInMemoria(UnitaDiLavoroFinta())
            val messaggi = mutableListOf<String>()
            val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
            try {
                abbonaSbobinatura(
                    dispatcher,
                    RigenerazioneSbobinaturaPolitica(lettore, LettoreNomiFinta(), ScrittoreSbobinaturaFinta()),
                    lettore::registrazioniConTrascritto,
                    scope,
                    { messaggio, _ -> messaggi += messaggio },
                    lettore::partiConTrascritto,
                )
                runCurrent()
                fun commit(evento: EventoPubblicato) = dispatcher.unitaDiLavoro.inTransazione {
                    Esito.Ok(dispatcher.pubblica(evento))
                }
                commit(ElaborazioneCompletata(poisoned, INCONTRO_I))
                runCurrent() // first failure: a retry is scheduled

                // while it waits, its own event and an Incontro fan-out arrive in ONE batch, the key first
                commit(ElaborazioneCompletata(poisoned, INCONTRO_I))
                commit(VociUnite(INCONTRO_I, sopravvissuta = VoceId(1), rimossa = VoceId(2)))
                advanceTimeBy(60.seconds)
                runCurrent()

                assertTrue(messaggi.none { "riuscito" in it }, "mai un falso successo: $messaggi")
                assertTrue(messaggi.any { "tentativo 2" in it }, "il contatore dei fallimenti prosegue: $messaggi")
            } finally {
                scope.cancel() // poisoned retries forever
            }
        }

    @Test
    fun `AC-C46 un elenco Parti che fallisce sempre per X non blocca la scrittura di una Parte di Y`() = runTest {
        val incontroX = IncontroId("incontro-x")
        val incontroY = IncontroId("incontro-y")
        val b = RegistrazioneId("parte-b-di-y")
        // X is already failing before b's event arrives, or both arrive in the same burst (X first).
        listOf(true, false).forEach { xGiaInRitento ->
            val lettore = LettoreTrascrittoFinta(mapOf(b to unTrascritto(b, "Parte B", incontroY)))
            val scrittore = ScrittoreSbobinaturaFinta()
            val dispatcher = DispatcherEventiInMemoria(UnitaDiLavoroFinta())
            val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
            abbonaSbobinatura(
                dispatcher,
                RigenerazioneSbobinaturaPolitica(lettore, LettoreNomiFinta(), scrittore),
                { emptyList() },
                scope,
                Segnalazione { _, _ -> },
            ) { incontro ->
                check(incontro != incontroX) { "VociDellIncontro di X corrotto" }
                lettore.partiConTrascritto(incontro)
            }
            try {
                runCurrent()

                val raffica = listOf(
                    VociUnite(incontroX, sopravvissuta = VoceId(1), rimossa = VoceId(2)),
                    ElaborazioneCompletata(b, incontroY),
                )
                raffica.forEach { evento ->
                    dispatcher.unitaDiLavoro.inTransazione {
                        dispatcher.pubblica(evento)
                        Esito.Ok(Unit)
                    }
                    if (xGiaInRitento) runCurrent()
                }
                advanceTimeBy(120.seconds)
                runCurrent()

                assertEquals(
                    listOf(ScrittoreSbobinaturaFinta.Operazione.Scritto("2026-09-12 Parte B.md")),
                    scrittore.operazioni,
                    "X gia in ritento: $xGiaInRitento",
                )
            } finally {
                scope.cancel() // X retries forever: stop it, or runTest's final drain chases it endlessly
            }
        }
    }

    private companion object {
        val PARTE_A = RegistrazioneId("parte-a")
        val INCONTRO_I = IncontroId("incontro-i")

        fun unTrascritto(id: RegistrazioneId, titolo: String, incontro: IncontroId) = TrascrittoTesto(
            registrazioneId = id,
            incontroId = incontro,
            titolo = titolo,
            dataRegistrazione = LocalDate.of(2026, 9, 12),
            segmenti = listOf(SegmentoVista(SegmentoId(1), VoceId(1), IntervalloMs(0, 1_000), "Ciao.")),
        )
    }
}
