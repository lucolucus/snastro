package snastro.avvio.r2

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.io.TempDir
import snastro.avvio.r1.attendiFinche
import snastro.kernel.CampioniAudio
import snastro.kernel.Esito
import snastro.kernel.EventoPubblicato
import snastro.kernel.atteso
import snastro.persistenza.DatabaseProgetto
import snastro.progetto.applicazione.comandi.RinominaRegistrazione
import snastro.trascrizione.applicazione.comandi.AvviaElaborazione
import snastro.trascrizione.applicazione.letture.StatoElaborazioneVista
import snastro.trascrizione.applicazione.porte.Diarizzatore
import snastro.trascrizione.applicazione.porte.DiarizzatoreFinta
import snastro.trascrizione.applicazione.porte.Turno
import snastro.trascrizione.dominio.NumeroPersone
import snastro.ui.registrazione.ComandoVoce
import snastro.ui.registrazione.ContenutoCarta
import snastro.ui.registrazione.RegistrazioneUiStato
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

/**
 * ADR 0017 end to end on [AmbienteR2]: a print extraction waits for the native Mutex (a fair
 * `ReentrantLock(true)` shared by [EstrattoreConMutex] and, here, a pipeline step or the test itself)
 * without a transaction, off the UI thread, cancellably — AC-236, AC-418..AC-421.
 */
class AttesaMutexR2Test {
    @TempDir
    lateinit var radice: Path

    @Test
    fun `AC-236 un'estrazione durante un'Elaborazione attende il Mutex senza transazione e fuori dalla UI`() {
        val mutex = ReentrantLock(true)
        val diarizzatore = DiarizzatoreCheTieneIlMutex(mutex)
        val estrattore = EstrattoreConMutex(mutex)
        AmbienteR2(radice, diarizzatore, estrattore).use {
            val a = it.importa()
            it.trascrivi(a)
            val b = it.importa()
            val s2 = costruisciRegistrazioniPresenterR2(it.grafoR0, it.collaboratori, it.r2) {}
            diarizzatore.trattieni.set(true)
            it.r2.r1.avviaElaborazione(AvviaElaborazione(b)).atteso()
            diarizzatore.inCorso.await() // the pipeline holds the Mutex for its (long) diarization

            val conferma = CoroutineScope(Dispatchers.Default).async {
                it.r2.comandi.esegui(ComandoVoce.Nuovo(voce(a, 1), "Anna"))
            }
            attendiFinche(messaggio = "estrazione in attesa del Mutex") { mutex.hasQueuedThreads() }

            // No transaction is open while it waits: another write commits, and S2's state keeps flowing.
            it.collaboratori.rinominaRegistrazione(RinominaRegistrazione(a, "Durante l'attesa")).atteso()
            attendiFinche(messaggio = "S2 aggiornata durante l'attesa") {
                val righe = (s2.stato.value as? RegistrazioniUiStato.Dati)?.righe.orEmpty()
                righe.any { r -> r.titolo == "Durante l'attesa" }
            }
            assertTrue(!conferma.isCompleted)

            diarizzatore.rilascia.countDown() // the Mutex is released; the pipeline stays in its phase meanwhile
            assertEquals(Esito.Ok(Unit), runBlocking { conferma.await() }, "il comando completa dopo il rilascio")
            diarizzatore.prosegui.countDown()
            assertEquals(1, it.conteggi(a).attribuzioni)
            assertTrue(estrattore.thread.none { t -> t.name == AmbienteR2.THREAD_UI }, "mai sul thread della UI")
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
        AmbienteR2(radice, diarizzatore, EstrattoreConMutex(mutex)).use {
            val a = it.importa()
            it.trascrivi(a)
            val b = it.importa()
            diarizzatore.trattieni.set(true)
            it.r2.r1.avviaElaborazione(AvviaElaborazione(b)).atteso()
            diarizzatore.inCorso.await()

            val conferma = CoroutineScope(Dispatchers.Default).async {
                it.r2.comandi.esegui(ComandoVoce.Nuovo(voce(a, 1), "Anna"))
            }
            attendiFinche(messaggio = "estrazione in attesa del Mutex") { mutex.hasQueuedThreads() }

            diarizzatore.prosegui.countDown() // the pipeline is NOT held: its completion races the command
            diarizzatore.rilascia.countDown()
            assertEquals(Esito.Ok(Unit), runBlocking { conferma.await() }, "nessun SQLITE_BUSY_SNAPSHOT")
            attendiFinche(messaggio = "elaborazione di b completata") {
                it.r2.r1.statiElaborazione(listOf(b)).single().stato == StatoElaborazioneVista.COMPLETATA
            }
            assertEquals(1, it.conteggi(a).attribuzioni)
        }
    }

    @Test
    fun `AC-418 uno stato in corso per VoceRef, e uscire da S3 non annulla il comando`() {
        val estrattore = EstrattoreConMutex()
        AmbienteR2(radice, estrattore = estrattore).use {
            val id = it.importa()
            it.trascrivi(id)
            val schermata = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            estrattore.lock.lock()
            try {
                schermata.async { it.r2.comandi.esegui(ComandoVoce.Nuovo(voce(id, 1), "Anna")) }
                attendiFinche(messaggio = "comando in attesa") { estrattore.lock.hasQueuedThreads() }
                assertEquals(setOf(voce(id, 1)), it.r2.comandi.stato.value.keys)

                schermata.cancel() // the user leaves S3
            } finally {
                estrattore.lock.unlock()
            }
            attendiFinche(messaggio = "comando concluso nello scope del progetto") {
                it.r2.comandi.stato.value.isEmpty()
            }
            assertEquals(1, it.conteggi(id).attribuzioni)
        }
    }

    @Test
    fun `AC-419 annulla durante l'attesa del Mutex non scrive nulla e non e un errore`() {
        val estrattore = EstrattoreConMutex()
        AmbienteR2(radice, estrattore = estrattore).use {
            val id = it.importa()
            it.trascrivi(id)
            val eventi = CopyOnWriteArrayList<EventoPubblicato>()
            it.contesto.dispatcher.registraDopoCommit { e -> eventi += e }
            val prima = it.conteggi(id)
            estrattore.lock.lock()
            try {
                val esito = CoroutineScope(Dispatchers.Default).async {
                    it.r2.comandi.esegui(ComandoVoce.Nuovo(voce(id, 1), "Anna"))
                }
                attendiFinche(messaggio = "comando in attesa") { estrattore.lock.hasQueuedThreads() }

                it.r2.comandi.annulla(voce(id, 1))

                assertNull(runBlocking { esito.await() }, "annullato: null, non un Errore")
                attendiFinche(messaggio = "estrazione interrotta") { !estrattore.lock.hasQueuedThreads() }
            } finally {
                estrattore.lock.unlock()
            }
            assertEquals(prima, it.conteggi(id), "nessuna Attribuzione, impronta o Parlante")
            assertEquals(emptyList(), eventi.toList(), "nessun evento")
            assertTrue(it.r2.comandi.stato.value.isEmpty())
        }
    }

    @Test
    fun `AC-420 chiudere annulla comandi e Proposte in corso e li attende prima di chiudere il database`() {
        val estrattore = EstrattoreConMutex()
        val osservato = AtomicReference<CollaboratoriR2?>()
        val vivoAllaChiusura = AtomicReference<Boolean?>()
        val chiudiDatabase: (DatabaseProgetto) -> Unit = { db ->
            osservato.getAndSet(null)?.let { r2 ->
                vivoAllaChiusura.set(estrattore.lock.hasQueuedThreads() || !r2.lavoro.isCompleted)
            }
            db.chiudi()
        }
        AmbienteR2(radice, DiarizzatoreFinta(AmbienteR2.TRE_VOCI), estrattore, chiudiDatabase = chiudiDatabase).use {
            val id = it.importa()
            it.trascrivi(id)
            checkNotNull(runBlocking { it.r2.comandi.esegui(ComandoVoce.Nuovo(voce(id, 1), "Anna")) }).atteso()
            val prima = it.conteggi(id)
            val r2 = it.r2
            estrattore.lock.lock()
            try {
                val comando = CoroutineScope(Dispatchers.Default).async {
                    r2.comandi.esegui(ComandoVoce.Salta(voce(id, 2)))
                }
                val schermata = r2.scopeSchermata(it.collaboratori.scope)
                costruisciRegistrazionePresenterR2(it.grafo, it.collaboratori, r2, id, schermata)
                attendiFinche(messaggio = "comando e Proposta in attesa del Mutex") { estrattore.lock.queueLength == 2 }
                osservato.set(r2)

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
        AmbienteR2(radice, DiarizzatoreFinta(AmbienteR2.TRE_VOCI), estrattore).use {
            val id = it.importa()
            it.trascrivi(id)
            checkNotNull(runBlocking { it.r2.comandi.esegui(ComandoVoce.Nuovo(voce(id, 1), "Anna")) }).atteso()
            estrattore.lock.lock()
            val schermate = List(2) { _ -> it.r2.scopeSchermata(it.collaboratori.scope) }
            try {
                // Two S3 visits overlap (leave and come back while the first extraction still waits).
                schermate.forEach { s -> costruisciRegistrazionePresenterR2(it.grafo, it.collaboratori, it.r2, id, s) }
                attendiFinche(messaggio = "una Proposta in attesa del Mutex") { estrattore.lock.hasQueuedThreads() }
                Thread.sleep(ATTESA_OSSERVAZIONE_MS)
                assertEquals(1, estrattore.lock.queueLength, "mai N attese concorrenti sul Mutex")

                schermate.forEach(CoroutineScope::cancel) // leaving S3
                attendiFinche(messaggio = "nessuna Proposta piu in attesa") { !estrattore.lock.hasQueuedThreads() }
            } finally {
                estrattore.lock.unlock()
            }
            // Back on S3: every not-attributed Voce gets its Proposta (nothing stale was cached).
            val s3 = costruisciRegistrazionePresenterR2(
                it.grafo,
                it.collaboratori,
                it.r2,
                id,
                it.r2.scopeSchermata(it.collaboratori.scope),
            )
            attendiFinche(messaggio = "Proposte di Voce 2 e 3 pronte") {
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
        private val delegato = DiarizzatoreFinta(AmbienteR2.DUE_VOCI)
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
