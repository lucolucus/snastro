package snastro.avvio.parlanti

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.io.TempDir
import snastro.avvio.costruisciRegistrazionePresenter
import snastro.avvio.costruisciRegistrazioniPresenter
import snastro.avvio.progetto.AmbienteProgetto
import snastro.avvio.progetto.EstrattoreConMutex
import snastro.avvio.progetto.voce
import snastro.kernel.CampioniAudio
import snastro.kernel.Esito
import snastro.kernel.EventoPubblicato
import snastro.kernel.atteso
import snastro.persistenza.DatabaseProgetto
import snastro.progetto.applicazione.comandi.RinominaRegistrazione
import snastro.supporto.test.attendiFinche
import snastro.trascrizione.applicazione.comandi.AvviaElaborazione
import snastro.trascrizione.applicazione.letture.StatoElaborazioneVista
import snastro.trascrizione.applicazione.porte.Diarizzatore
import snastro.trascrizione.applicazione.porte.DiarizzatoreFinta
import snastro.trascrizione.applicazione.porte.Turno
import snastro.trascrizione.dominio.NumeroPersone
import snastro.ui.registrazione.ComandoVoce
import snastro.ui.registrazione.ContenutoCarta
import snastro.ui.registrazione.RegistrazioneUiStato
import snastro.ui.registrazione.SelezioneSchedaS3
import snastro.ui.registrazione.StatoProposta
import snastro.ui.registrazioni.RegistrazioniUiStato
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * ADR 0017 end to end on [AmbienteProgetto]: a print extraction waits for the native Mutex (a fair
 * `ReentrantLock(true)` shared by [EstrattoreConMutex] and, here, a pipeline step or the test itself)
 * without a transaction, off the UI thread, cancellably — AC-236, AC-418..AC-421.
 */
class AttesaMutexTest {
    @TempDir
    lateinit var radice: Path

    @Test
    fun `AC-236 un'estrazione durante un'Elaborazione attende il Mutex senza transazione e fuori dalla UI`() {
        val mutex = ReentrantLock(true)
        val diarizzatore = DiarizzatoreCheTieneIlMutex(mutex)
        val estrattore = EstrattoreConMutex(mutex)
        AmbienteProgetto(radice, diarizzatore, estrattore = estrattore).use {
            val a = it.importa()
            it.trascrivi(a)
            val b = it.importa()
            val s2 = costruisciRegistrazioniPresenter(it.grafo(), it.collaboratori) {}
            diarizzatore.trattieni.set(true)
            it.collaboratori.avviaElaborazione(AvviaElaborazione(b)).atteso()
            diarizzatore.inCorso.await() // the pipeline holds the Mutex for its (long) diarization

            val conferma = CoroutineScope(Dispatchers.Default).async {
                it.parlanti.comandi.esegui(ComandoVoce.Nuovo(voce(a, 1), "Anna"))
            }
            attendiFinche(timeout = 10.seconds, messaggio = "estrazione in attesa del Mutex") {
                mutex.hasQueuedThreads()
            }

            // No transaction is open while it waits: another write commits, and S2's state keeps flowing.
            it.collaboratori.rinominaRegistrazione(RinominaRegistrazione(a, "Durante l'attesa")).atteso()
            attendiFinche(timeout = 10.seconds, messaggio = "S2 aggiornata durante l'attesa") {
                val righe = (s2.stato.value as? RegistrazioniUiStato.Dati)?.righe.orEmpty()
                righe.any { r -> r.titolo == "Durante l'attesa" }
            }
            assertTrue(!conferma.isCompleted)

            diarizzatore.rilascia.countDown() // the Mutex is released; the pipeline stays in its phase meanwhile
            assertEquals(Esito.Ok(Unit), runBlocking { conferma.await() }, "il comando completa dopo il rilascio")
            diarizzatore.prosegui.countDown()
            assertEquals(1, it.conteggi(a).attribuzioni)
            assertTrue(estrattore.thread.none { t -> t.name == AmbienteProgetto.THREAD_UI }, "mai sul thread della UI")
        }
    }

    /**
     * AC-236 variant WITHOUT holding the pipeline after the Mutex (fix-batch-17): once released, the
     * diarization returns at once, so the pipeline's completion commit races the command's read-then-
     * write transaction. Before every transaction began `BEGIN IMMEDIATE` this could fail with
     * `SQLITE_BUSY_SNAPSHOT`; now one of the two waits (busy_timeout) and both land.
     */
    @Test
    fun `AC-236 il commit di completamento della pipeline non fa fallire il comando in attesa del Mutex`() {
        val mutex = ReentrantLock(true)
        val diarizzatore = DiarizzatoreCheTieneIlMutex(mutex)
        AmbienteProgetto(radice, diarizzatore, estrattore = EstrattoreConMutex(mutex)).use {
            val a = it.importa()
            it.trascrivi(a)
            val b = it.importa()
            diarizzatore.trattieni.set(true)
            it.collaboratori.avviaElaborazione(AvviaElaborazione(b)).atteso()
            diarizzatore.inCorso.await()

            val conferma = CoroutineScope(Dispatchers.Default).async {
                it.parlanti.comandi.esegui(ComandoVoce.Nuovo(voce(a, 1), "Anna"))
            }
            attendiFinche(timeout = 10.seconds, messaggio = "estrazione in attesa del Mutex") {
                mutex.hasQueuedThreads()
            }

            diarizzatore.prosegui.countDown() // the pipeline is NOT held: its completion races the command
            diarizzatore.rilascia.countDown()
            assertEquals(Esito.Ok(Unit), runBlocking { conferma.await() }, "nessun SQLITE_BUSY_SNAPSHOT")
            attendiFinche(timeout = 10.seconds, messaggio = "elaborazione di b completata") {
                it.stato(b) == StatoElaborazioneVista.COMPLETATA
            }
            assertEquals(1, it.conteggi(a).attribuzioni)
        }
    }

    @Test
    fun `AC-418 uno stato in corso per VoceRef, e uscire da S3 non annulla il comando`() {
        val estrattore = EstrattoreConMutex()
        AmbienteProgetto(radice, estrattore = estrattore).use {
            val id = it.importa()
            it.trascrivi(id)
            val schermata = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            estrattore.lock.lock()
            try {
                schermata.async { it.parlanti.comandi.esegui(ComandoVoce.Nuovo(voce(id, 1), "Anna")) }
                attendiFinche(timeout = 10.seconds, messaggio = "comando in attesa") {
                    estrattore.lock.hasQueuedThreads()
                }
                assertEquals(setOf(voce(id, 1)), it.parlanti.comandi.stato.value.keys)

                schermata.cancel() // the user leaves S3
            } finally {
                estrattore.lock.unlock()
            }
            attendiFinche(timeout = 10.seconds, messaggio = "comando concluso nello scope del progetto") {
                it.parlanti.comandi.stato.value.isEmpty()
            }
            assertEquals(1, it.conteggi(id).attribuzioni)
        }
    }

    @Test
    fun `AC-419 annulla durante l'attesa del Mutex non scrive nulla e non e un errore`() {
        val estrattore = EstrattoreConMutex()
        AmbienteProgetto(radice, estrattore = estrattore).use {
            val id = it.importa()
            it.trascrivi(id)
            val eventi = CopyOnWriteArrayList<EventoPubblicato>()
            it.porte.dispatcher.registraDopoCommit { e -> eventi += e }
            val prima = it.conteggi(id)
            estrattore.lock.lock()
            try {
                val esito = CoroutineScope(Dispatchers.Default).async {
                    it.parlanti.comandi.esegui(ComandoVoce.Nuovo(voce(id, 1), "Anna"))
                }
                attendiFinche(timeout = 10.seconds, messaggio = "comando in attesa") {
                    estrattore.lock.hasQueuedThreads()
                }

                it.parlanti.comandi.annulla(voce(id, 1))

                assertNull(runBlocking { esito.await() }, "annullato: null, non un Errore")
                attendiFinche(timeout = 10.seconds, messaggio = "estrazione interrotta") {
                    !estrattore.lock.hasQueuedThreads()
                }
            } finally {
                estrattore.lock.unlock()
            }
            assertEquals(prima, it.conteggi(id), "nessuna Attribuzione, impronta o Parlante")
            assertEquals(emptyList(), eventi.toList(), "nessun evento")
            assertTrue(it.parlanti.comandi.stato.value.isEmpty())
        }
    }

    @Test
    fun `AC-420 chiudere annulla comandi e Proposte in corso e li attende prima di chiudere il database`() {
        val estrattore = EstrattoreConMutex()
        // What is still alive of the Parlanti work when the database closes (null = not observed yet).
        val osservato = AtomicReference<(() -> Boolean)?>()
        val vivoAllaChiusura = AtomicReference<Boolean?>()
        val chiudiDatabase: (DatabaseProgetto) -> Unit = { db ->
            osservato.getAndSet(null)?.let { vivo -> vivoAllaChiusura.set(vivo()) }
            db.chiudi()
        }
        AmbienteProgetto(
            radice,
            DiarizzatoreFinta(AmbienteProgetto.TRE_VOCI),
            estrattore = estrattore,
            chiudiDatabase = chiudiDatabase,
        ).use {
            val id = it.importa()
            it.trascrivi(id)
            checkNotNull(runBlocking { it.parlanti.comandi.esegui(ComandoVoce.Nuovo(voce(id, 1), "Anna")) }).atteso()
            val prima = it.conteggi(id)
            val parlanti = it.parlanti
            estrattore.lock.lock()
            try {
                val comando = CoroutineScope(Dispatchers.Default).async {
                    parlanti.comandi.esegui(ComandoVoce.Salta(voce(id, 2)))
                }
                val schermata = parlanti.scopeSchermata(it.collaboratori.scope)
                costruisciRegistrazionePresenter(it.grafo(), it.collaboratori, id, schermata, SelezioneSchedaS3())
                attendiFinche(timeout = 10.seconds, messaggio = "comando e Proposta in attesa del Mutex") {
                    estrattore.lock.queueLength == 2
                }
                // the pending command (its per-key entry), the S3 screen's own Proposta job, and the Mutex queue
                osservato.set {
                    estrattore.lock.hasQueuedThreads() || parlanti.comandi.stato.value.isNotEmpty() ||
                        !schermata.coroutineContext.job.isCompleted
                }

                it.sessione.chiudi()

                assertEquals(false, vivoAllaChiusura.get(), "nessun lavoro dei Parlanti vivo quando il database chiude")
                assertNull(runBlocking { comando.await() }, "il comando in corso e annullato, non fallito")
            } finally {
                estrattore.lock.unlock()
            }
            it.sessione.apri(it.progetto.percorso).atteso()
            assertEquals(prima, it.conteggi(id), "nulla scritto dopo chiudi")
        }
    }

    @Test
    fun `AC-421 le Proposte di S3 sono calcolate una Voce alla volta e uscire da S3 le annulla`() {
        val estrattore = EstrattoreConMutex()
        AmbienteProgetto(radice, DiarizzatoreFinta(AmbienteProgetto.TRE_VOCI), estrattore = estrattore).use {
            val id = it.importa()
            it.trascrivi(id)
            checkNotNull(runBlocking { it.parlanti.comandi.esegui(ComandoVoce.Nuovo(voce(id, 1), "Anna")) }).atteso()
            estrattore.lock.lock()
            val schermate = List(2) { _ -> it.parlanti.scopeSchermata(it.collaboratori.scope) }
            try {
                // Two S3 visits overlap (leave and come back while the first extraction still waits).
                schermate.forEach { s ->
                    costruisciRegistrazionePresenter(it.grafo(), it.collaboratori, id, s, SelezioneSchedaS3())
                }
                attendiFinche(timeout = 10.seconds, messaggio = "una Proposta in attesa del Mutex") {
                    estrattore.lock.hasQueuedThreads()
                }
                // real time is the subject: confirms no SECOND thread also queues on the Mutex meanwhile.
                Thread.sleep(ATTESA_OSSERVAZIONE_MS)
                assertEquals(1, estrattore.lock.queueLength, "mai N attese concorrenti sul Mutex")

                schermate.forEach(CoroutineScope::cancel) // leaving S3
                attendiFinche(timeout = 10.seconds, messaggio = "nessuna Proposta piu in attesa") {
                    !estrattore.lock.hasQueuedThreads()
                }
            } finally {
                estrattore.lock.unlock()
            }
            // Back on S3: every not-attributed Voce gets its Proposta (nothing stale was cached).
            val s3 = costruisciRegistrazionePresenter(
                it.grafo(),
                it.collaboratori,
                id,
                it.parlanti.scopeSchermata(it.collaboratori.scope),
                SelezioneSchedaS3(),
            )
            attendiFinche(timeout = 10.seconds, messaggio = "Proposte di Voce 2 e 3 pronte") {
                val carte = (s3.stato.value as? RegistrazioneUiStato.Dati)?.pannello?.carte.orEmpty()
                carte.map { c -> (c.contenuto as? ContenutoCarta.DaIdentificare)?.proposta }
                    .count { p -> p is StatoProposta.Pronta } == 2
            }
        }
    }

    /**
     * A pipeline step holding the native Mutex for a whole diarization while [trattieni] is set — like
     * `DiarizzatoreSherpa.diarizza`'s single `conSessione` (ADR 0017 §1.1) — until [rilascia]; it then
     * returns only at [prosegui], so the pipeline's own completion commit never races the command's
     * transaction in the first AC-236 test (that concurrency is `:persistenza`'s, not the Mutex's; the
     * no-hold variant opens [prosegui] first to race it on purpose).
     */
    private class DiarizzatoreCheTieneIlMutex(private val mutex: ReentrantLock) : Diarizzatore {
        private val delegato = DiarizzatoreFinta(AmbienteProgetto.DUE_VOCI)
        val trattieni = AtomicBoolean(false)
        val inCorso = CountDownLatch(1)
        val rilascia = CountDownLatch(1)
        val prosegui = CountDownLatch(1)

        override fun diarizza(c: CampioniAudio, numeroPersone: NumeroPersone?): List<Turno> {
            if (!trattieni.get()) return delegato.diarizza(c, numeroPersone)
            mutex.lock()
            try {
                inCorso.countDown()
                rilascia.await()
            } finally {
                mutex.unlock()
            }
            prosegui.await()
            return delegato.diarizza(c, numeroPersone)
        }
    }

    private companion object {
        const val ATTESA_OSSERVAZIONE_MS = 500L
    }
}
