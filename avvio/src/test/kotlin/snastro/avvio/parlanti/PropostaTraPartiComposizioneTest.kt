package snastro.avvio.parlanti

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.io.TempDir
import snastro.avvio.progetto.AmbienteProgetto
import snastro.avvio.progetto.DiarizzatoreScriptato
import snastro.avvio.progetto.EstrattoreConMutex
import snastro.kernel.ErroreDiProva
import snastro.kernel.Esito
import snastro.kernel.EventoPubblicato
import snastro.kernel.IncontroId
import snastro.kernel.IntervalloMs
import snastro.kernel.ParlanteId
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.SegmentoRef
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.atteso
import snastro.parlanti.applicazione.eventi.AttribuzioneConfermata
import snastro.parlanti.applicazione.eventi.ImpronteRiallineate
import snastro.progetto.applicazione.comandi.Destinazione
import snastro.progetto.applicazione.comandi.RinominaRegistrazione
import snastro.supporto.test.attendiFinche
import snastro.trascrizione.applicazione.eventi.ElaborazioneCompletata
import snastro.trascrizione.applicazione.eventi.SegmentoRiassegnato
import snastro.trascrizione.applicazione.eventi.TrascrittoEliminato
import snastro.trascrizione.applicazione.eventi.TrascrittoSostituito
import snastro.trascrizione.applicazione.eventi.VoceDivisa
import snastro.trascrizione.applicazione.eventi.VociUnite
import snastro.trascrizione.applicazione.porte.Turno
import snastro.ui.registrazione.ComandoVoce
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * `avvio-proposta-tra-parti` on the production composition ([AmbienteProgetto]): `PropostaTraParti` wired into the
 * open project — its subscribers (AC-I49), the shared sherpa Mutex, outside any transaction, one wait at a time
 * (AC-I92, ADR 0017). Each Parte has ONE Voce, so the two Voci of the Incontro are each other's only FORTE.
 */
class PropostaTraPartiComposizioneTest {
    @TempDir
    lateinit var radice: Path

    @Test
    fun `AC-I49 la composizione reale propone la coppia tra le due Parti e la tiene in cache`() {
        val estrattore = EstrattoreConMutex()
        ambiente(estrattore).use {
            val incontro = it.dueParti().first
            val coppia = it.parlanti.letture.traParti(incontro).single()
            assertEquals(1, coppia.parteA)
            assertEquals(2, coppia.parteB)
            val calcolate = estrattore.chiamate.get()
            assertEquals(2, calcolate, "una estrazione per fetta")

            it.parlanti.letture.traParti(incontro)
            assertEquals(calcolate, estrattore.chiamate.get(), "la seconda lettura e in cache")
        }
    }

    @Test
    fun `AC-I49 ognuno degli otto eventi invalida solo l'Incontro dell'evento, un rollback non invalida`() {
        val estrattore = EstrattoreConMutex()
        ambiente(estrattore).use {
            val (incontro, a, _) = it.dueParti()
            val altro = IncontroId("altro-incontro")
            val segmento = SegmentoRef(a, SegmentoId(1))
            val voce = VoceRef(incontro, VoceId(1))
            val dispatcher = it.porte.dispatcher
            fun pubblica(evento: EventoPubblicato) = dispatcher.unitaDiLavoro.inTransazione {
                Esito.Ok(dispatcher.pubblica(evento))
            }.atteso()

            val eventi = listOf<(IncontroId) -> EventoPubblicato>(
                { i -> VociUnite(i, VoceId(1), VoceId(2)) },
                { i -> VoceDivisa(i, VoceId(1), VoceId(3), listOf(segmento)) },
                { i -> SegmentoRiassegnato(i, segmento, VoceId(1), VoceId(2), false, false) },
                { i -> ElaborazioneCompletata(a, i) },
                { i -> TrascrittoSostituito(a, i, emptySet()) },
                { i -> TrascrittoEliminato(a, i, emptySet()) },
                { _ -> AttribuzioneConfermata(voce.copy(incontroId = incontro), ParlanteId("p"), null) },
                { i -> ImpronteRiallineate(i) },
            )
            it.parlanti.letture.traParti(incontro)
            eventi.forEach { crea ->
                val prima = estrattore.chiamate.get()
                it.parlanti.letture.traParti(incontro)
                assertEquals(prima, estrattore.chiamate.get(), "in cache prima di ${crea(incontro)}")

                dispatcher.unitaDiLavoro.inTransazione {
                    dispatcher.pubblica(crea(incontro))
                    Esito.Errore(ErroreDiProva.Fallito("rollback"))
                }
                it.parlanti.letture.traParti(incontro)
                assertEquals(prima, estrattore.chiamate.get(), "un rollback non invalida: ${crea(incontro)}")

                if (crea(incontro) !is AttribuzioneConfermata) {
                    pubblica(crea(altro))
                    it.parlanti.letture.traParti(incontro)
                    assertEquals(prima, estrattore.chiamate.get(), "un altro Incontro non invalida: ${crea(altro)}")
                }

                pubblica(crea(incontro))
                attendiFinche(timeout = 10.seconds, messaggio = "ricalcolo dopo ${crea(incontro)}") {
                    it.parlanti.letture.traParti(incontro)
                    estrattore.chiamate.get() > prima
                }
            }
        }
    }

    @Test
    fun `AC-I49 una Attribuzione confermata toglie la coppia dalla proposta`() {
        val estrattore = EstrattoreConMutex()
        ambiente(estrattore).use {
            val (incontro, _, _) = it.dueParti()
            assertEquals(1, it.parlanti.letture.traParti(incontro).size)

            checkNotNull(
                runBlocking { it.parlanti.comandi.esegui(ComandoVoce.Nuovo(VoceRef(incontro, VoceId(1)), "Anna")) },
            ).atteso()

            attendiFinche(timeout = 10.seconds, messaggio = "coppia tolta") {
                it.parlanti.letture.traParti(incontro).isEmpty()
            }
        }
    }

    @Test
    fun `AC-I92 il calcolo attende il Mutex condiviso fuori da ogni transazione e da un solo thread`() {
        val estrattore = EstrattoreConMutex()
        ambiente(estrattore).use {
            val (incontro, a, _) = it.dueParti()
            val mutex = estrattore.lock
            mutex.lock() // a transcription holds the sherpa Mutex
            val letture = try {
                // Two overlapping reloads of the banner.
                val l = List(2) { _ ->
                    CoroutineScope(Dispatchers.Default).async { it.parlanti.letture.traParti(incontro) }
                }
                attendiFinche(timeout = 10.seconds, messaggio = "il calcolo in attesa del Mutex") {
                    mutex.hasQueuedThreads()
                }
                // No transaction is open while it waits: another write commits.
                it.collaboratori.rinominaRegistrazione(RinominaRegistrazione(a, "Durante l'attesa")).atteso()
                assertTrue(l.none { d -> d.isCompleted }, "il calcolo attende il Mutex")
                assertEquals(1, mutex.queueLength, "mai N attese concorrenti sul Mutex")
                l
            } finally {
                mutex.unlock()
            }
            runBlocking { letture.forEach { d -> assertEquals(1, d.await().size) } }
            assertTrue(estrattore.thread.none { t -> t.name == AmbienteProgetto.THREAD_UI }, "mai sul thread della UI")
        }
    }

    @Test
    fun `AC-I92 una lettura in cache non attende dietro il calcolo di un altro Incontro`() {
        val estrattore = EstrattoreConMutex()
        ambiente(estrattore).use {
            val primo = it.dueParti().first
            assertEquals(1, it.parlanti.letture.traParti(primo).size) // cached
            val secondo = it.dueParti().first
            val mutex = estrattore.lock
            mutex.lock()
            val calcolo = CoroutineScope(Dispatchers.Default).async { it.parlanti.letture.traParti(secondo) }
            try {
                attendiFinche(timeout = 10.seconds, messaggio = "il calcolo in attesa del Mutex") {
                    mutex.hasQueuedThreads()
                }
                assertEquals(1, it.parlanti.letture.traParti(primo).size, "la cache si legge senza attendere")
                assertTrue(!calcolo.isCompleted)
            } finally {
                mutex.unlock()
            }
            runBlocking { assertEquals(1, calcolo.await().size) }
        }
    }

    private fun ambiente(estrattore: EstrattoreConMutex): AmbienteProgetto {
        val unaVoce = DiarizzatoreScriptato(listOf(Turno(IntervalloMs(0, 1_000), 0)))
        return AmbienteProgetto(radice, unaVoce, estrattore = estrattore)
    }

    /** One Incontro of two transcribed Parti, each with its one Voce: (incontro, parte 1, parte 2). */
    private fun AmbienteProgetto.dueParti(): Triple<IncontroId, RegistrazioneId, RegistrazioneId> {
        val a = importa()
        val incontro = incontroDi(a)
        trascrivi(a)
        val prima = collaboratori.registrazioni().map { r -> r.registrazioneId }.toSet()
        importaIn(Destinazione.Incontro(incontro)).atteso()
        val b = collaboratori.registrazioni().map { r -> r.registrazioneId }.single { r -> r !in prima }
        rendiLeggibile(b)
        trascrivi(b)
        return Triple(incontro, a, b)
    }
}
