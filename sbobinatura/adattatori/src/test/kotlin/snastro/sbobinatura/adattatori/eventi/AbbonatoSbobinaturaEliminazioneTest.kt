package snastro.sbobinatura.adattatori.eventi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import snastro.kernel.DispatcherEventiInMemoria
import snastro.kernel.ErroreDiProva
import snastro.kernel.Esito
import snastro.kernel.EventoPubblicato
import snastro.kernel.IntervalloMs
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.kernel.SegmentoId
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.VoceId
import snastro.kernel.unIncontroDi
import snastro.progetto.applicazione.eventi.DataRegistrazioneModificata
import snastro.progetto.applicazione.eventi.RegistrazioneEliminata
import snastro.progetto.applicazione.eventi.RegistrazioneRinominata
import snastro.sbobinatura.applicazione.politiche.RigenerazioneSbobinaturaPolitica
import snastro.sbobinatura.applicazione.porte.LettoreNomiFinta
import snastro.sbobinatura.applicazione.porte.LettoreTrascritto
import snastro.sbobinatura.applicazione.porte.ScrittoreSbobinatura
import snastro.sbobinatura.applicazione.porte.ScrittoreSbobinaturaFinta
import snastro.sbobinatura.applicazione.porte.ScrittoreSbobinaturaFinta.Operazione.Rimosso
import snastro.sbobinatura.applicazione.porte.ScrittoreSbobinaturaFinta.Operazione.Scritto
import snastro.sbobinatura.applicazione.porte.SegmentoVista
import snastro.sbobinatura.applicazione.porte.TrascrittoTesto
import snastro.supporto.Segnalazione
import snastro.trascrizione.applicazione.eventi.ElaborazioneCompletata
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * AC-624 (ADR 0020 §3): [AbbonatoSbobinaturaEventi] queues `RegistrazioneEliminata` on the SAME per-registrazioneId key
 * as the Rigenerazione. Real policy over fake ports; virtual time only.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AbbonatoSbobinaturaEliminazioneTest {
    /** [trascritti] is mutable: the deleting command's commit takes the Trascritto away, as the real one does. */
    private class Ambiente(
        scheduler: TestCoroutineScheduler,
        val finta: ScrittoreSbobinaturaFinta = ScrittoreSbobinaturaFinta(),
        scrittore: ScrittoreSbobinatura = finta,
    ) {
        val trascritti = ConcurrentHashMap(mapOf(REG to unTrascritto()))
        val dispatcher = DispatcherEventiInMemoria(UnitaDiLavoroFinta())

        init {
            val lettore = object : LettoreTrascritto {
                override fun trascritto(id: RegistrazioneId) = trascritti[id]

                override fun registrazioniConTrascritto() = trascritti.keys.toList()
            }
            val politica = RigenerazioneSbobinaturaPolitica(lettore, LettoreNomiFinta(), scrittore)
            abbonaSbobinatura(
                dispatcher,
                politica,
                lettore::registrazioniConTrascritto,
                CoroutineScope(StandardTestDispatcher(scheduler)),
                Segnalazione { _, _ -> },
            )
        }

        fun commit(evento: EventoPubblicato, esito: Esito<Unit> = Esito.Ok(Unit)) {
            dispatcher.unitaDiLavoro.inTransazione {
                dispatcher.pubblica(evento)
                if (evento is RegistrazioneEliminata && esito is Esito.Ok) trascritti.remove(evento.registrazioneId)
                esito
            }
        }

        fun operazioni() = finta.operazioni
    }

    @Test
    fun `AC-624 RegistrazioneEliminata dopo il commit rimuove la Sbobinatura col nome alla eliminazione`() = runTest {
        val ambiente = Ambiente(testScheduler)
        advanceUntilIdle() // the startup sweep writes the Sbobinatura
        val prima = ambiente.operazioni().size

        ambiente.commit(eliminata())
        advanceUntilIdle()

        assertEquals(listOf(Rimosso(NOME)), ambiente.operazioni().drop(prima))
    }

    @Test
    fun `AC-624 mai su rollback`() = runTest {
        val ambiente = Ambiente(testScheduler)
        advanceUntilIdle()
        val prima = ambiente.operazioni().size

        ambiente.commit(eliminata(), Esito.Errore(ErroreDiProva.Fallito("veto")))
        advanceUntilIdle()

        assertEquals(prima, ambiente.operazioni().size)
    }

    @Test
    fun `AC-624 sostituisce la Rigenerazione in attesa della stessa chiave`() = runTest {
        val ambiente = Ambiente(testScheduler)
        advanceUntilIdle()
        val prima = ambiente.operazioni().size

        // Both queued before the worker runs; the Finta still "has" the Trascritto until the deletion's commit, and
        // the pending Rigenerazione must not be run at all (not merely find nothing).
        ambiente.dispatcher.unitaDiLavoro.inTransazione {
            ambiente.dispatcher.pubblica(ElaborazioneCompletata(REG))
            Esito.Ok(Unit)
        }
        ambiente.dispatcher.unitaDiLavoro.inTransazione {
            ambiente.dispatcher.pubblica(eliminata())
            Esito.Ok(Unit)
        }
        advanceUntilIdle()

        assertEquals(listOf(Rimosso(NOME)), ambiente.operazioni().drop(prima))
    }

    @Test
    fun `AC-624 i precedenti in attesa di un rinomina e di un cambio data diventano nomiPrecedenti`() = runTest {
        val ambiente = Ambiente(testScheduler)
        advanceUntilIdle()
        val prima = ambiente.operazioni().size

        ambiente.commit(RegistrazioneRinominata(REG, precedente = "Vecchia", nuovo = TITOLO))
        ambiente.commit(DataRegistrazioneModificata(REG, DATA.minusDays(1), DATA, unIncontroDi(REG)))
        ambiente.commit(eliminata())
        advanceUntilIdle()

        val operazioni = ambiente.operazioni().drop(prima)
        assertTrue(operazioni.none { it is Scritto }, "nessuna scrittura: $operazioni")
        assertEquals(
            setOf(NOME, "2026-09-11 Vecchia.md", "2026-09-11 $TITOLO.md", "2026-09-12 Vecchia.md"),
            operazioni.map { (it as Rimosso).nomeFile }.toSet(),
        )
    }

    @Test
    fun `AC-624 una Rigenerazione in corso quando arriva l evento finisce PRIMA e la rimozione viene dopo`() = runTest {
        val finta = ScrittoreSbobinaturaFinta()
        val scrittore = InVolo(finta)
        val ambiente = Ambiente(testScheduler, finta, scrittore)
        advanceUntilIdle()
        val prima = ambiente.operazioni().size
        scrittore.durante = { ambiente.commit(eliminata()) } // the deletion commits while the write is in flight

        ambiente.commit(ElaborazioneCompletata(REG))
        advanceUntilIdle()

        assertEquals(listOf(Scritto(NOME), Rimosso(NOME)), ambiente.operazioni().drop(prima))
    }

    @Test
    fun `AC-624 una Rigenerazione in corso che fallisce non scavalca la rimozione ne la resuscita`() = runTest {
        val finta = ScrittoreSbobinaturaFinta()
        val scrittore = InVolo(finta)
        val ambiente = Ambiente(testScheduler, finta, scrittore)
        advanceUntilIdle()
        val prima = ambiente.operazioni().size
        scrittore.durante = {
            ambiente.commit(eliminata())
            throw IOException("guasto durante la scrittura")
        }

        ambiente.commit(ElaborazioneCompletata(REG))
        advanceUntilIdle()

        assertEquals(listOf(Rimosso(NOME)), ambiente.operazioni().drop(prima))
    }

    @Test
    fun `AC-C93 un aggiornamento fuso DOPO una rimozione pendente non la ritrasforma in scrittura`() = runTest {
        val ambiente = Ambiente(testScheduler)
        advanceUntilIdle() // lo sweep di avvio scrive REG una prima volta
        val prima = ambiente.operazioni().size

        // La rimozione e' GIA' pendente quando l'aggiornamento si fonde nella STESSA chiave, prima che il
        // worker giri: primaArrivata deve tenere l'eliminata della voce gia' pendente (prioritaria), mai quella
        // del nuovo evento (altra, che qui e' null) — altrimenti la rimozione tornerebbe una scrittura.
        ambiente.commit(eliminata())
        ambiente.commit(ElaborazioneCompletata(REG))
        advanceUntilIdle()

        assertEquals(listOf(Rimosso(NOME)), ambiente.operazioni().drop(prima))
    }

    @Test
    fun `AC-624 un guasto della rimozione e ritentato col backoff esistente fino al successo`() = runTest {
        val ambiente = Ambiente(testScheduler)
        advanceUntilIdle()
        val prima = ambiente.operazioni().size
        ambiente.finta.fallisciAllaProssimaRimozione()

        ambiente.commit(eliminata())
        val inizio = testScheduler.currentTime
        advanceUntilIdle()

        assertEquals(listOf(Rimosso(NOME)), ambiente.operazioni().drop(prima))
        assertTrue(testScheduler.currentTime > inizio, "il backoff e passato per davvero")
    }

    /** Runs [durante] once, inside the next [scrivi], before writing: a write "in flight" when it runs. */
    private class InVolo(private val finta: ScrittoreSbobinaturaFinta) : ScrittoreSbobinatura by finta {
        var durante: (() -> Unit)? = null

        override fun scrivi(nomeFile: String, markdown: String) {
            durante?.also { durante = null }?.invoke()
            finta.scrivi(nomeFile, markdown)
        }
    }

    private companion object {
        val REG = RegistrazioneId("reg-1")
        val DATA: LocalDate = LocalDate.of(2026, 9, 12)
        const val TITOLO = "Riunione"
        const val NOME = "2026-09-12 Riunione.md"

        fun eliminata() =
            RegistrazioneEliminata(
                REG,
                ProgettoId("p"),
                TITOLO,
                DATA,
                RiferimentoAudio("audio/reg-1.m4a"),
                unIncontroDi(REG),
                incontroCessato = true,
            )

        fun unTrascritto() = TrascrittoTesto(
            registrazioneId = REG,
            incontroId = unIncontroDi(REG),
            titolo = TITOLO,
            dataRegistrazione = DATA,
            segmenti = listOf(SegmentoVista(SegmentoId(1), VoceId(1), IntervalloMs(0, 1_000), "Ciao.")),
        )
    }
}
