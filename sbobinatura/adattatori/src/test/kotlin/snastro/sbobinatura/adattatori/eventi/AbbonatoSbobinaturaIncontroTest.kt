package snastro.sbobinatura.adattatori.eventi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import snastro.kernel.DispatcherEventiInMemoria
import snastro.kernel.Esito
import snastro.kernel.IncontroId
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
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
import snastro.trascrizione.applicazione.eventi.TrascrittoEliminato
import snastro.trascrizione.applicazione.eventi.VociUnite
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
