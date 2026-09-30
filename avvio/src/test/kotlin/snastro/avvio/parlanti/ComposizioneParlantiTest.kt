package snastro.avvio.parlanti

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.io.TempDir
import snastro.avvio.costruisciRegistrazioniPresenter
import snastro.avvio.progetto.AmbienteProgetto
import snastro.avvio.progetto.EstrattoreConMutex
import snastro.avvio.progetto.voce
import snastro.kernel.AbbonatoSincrono
import snastro.kernel.ElaborazioneId
import snastro.kernel.ErroreDiProva
import snastro.kernel.Esito
import snastro.kernel.EventoPubblicato
import snastro.kernel.ParlanteId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.kernel.atteso
import snastro.modelli.CatalogoDiarizzazione
import snastro.parlanti.adattatori.persistenza.AttribuzioneRepositorySql
import snastro.parlanti.adattatori.persistenza.ParlanteRepositorySql
import snastro.parlanti.applicazione.comandi.EliminaParlante
import snastro.parlanti.applicazione.comandi.RinominaParlante
import snastro.parlanti.applicazione.eventi.ImpronteRiallineate
import snastro.parlanti.applicazione.porte.EstrattoreImpronta
import snastro.parlanti.applicazione.porte.EstrattoreImprontaFinta
import snastro.persistenza.DatabaseProgetto
import snastro.persistenza.SnastroDatabase
import snastro.persistenza.apriDatabaseProgetto
import snastro.supporto.test.attendiFinche
import snastro.supporto.test.pausaInTempoReale
import snastro.supporto.test.restaVeroPer
import snastro.trascrizione.adattatori.persistenza.ElaborazioneRepositorySql
import snastro.trascrizione.applicazione.comandi.DividiVoce
import snastro.trascrizione.applicazione.comandi.UnisciVoci
import snastro.trascrizione.applicazione.eventi.VociUnite
import snastro.trascrizione.applicazione.porte.DiarizzatoreFinta
import snastro.trascrizione.dominio.StatoElaborazione
import snastro.trascrizione.dominio.unaElaborazione
import snastro.ui.Cambiamento
import snastro.ui.registrazione.ComandoVoce
import snastro.ui.registrazioni.RegistrazioniUiStato
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import kotlin.concurrent.thread
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * End-to-end ACs of the Parlanti module of the single composition on [AmbienteProgetto] (real SessioneProgettoImpl +
 * apriProgetto over a
 * real project folder; only FFmpeg and the ML models are Finte): AC-359, AC-315, AC-316, AC-317, AC-358.
 */
class ComposizioneParlantiTest {
    @TempDir
    lateinit var radice: Path

    @Test
    fun `AC-359 il Documento mostra i Nomi attribuiti e ne segue la rinomina`() {
        AmbienteProgetto(radice).use {
            val id = it.importa()
            it.trascrivi(id)

            val nomina = runBlocking { it.parlanti.comandi.esegui(ComandoVoce.Nuovo(voce(id, 1), "Anna")) }

            assertEquals(Esito.Ok(Unit), nomina)
            attendiFinche(timeout = 10.seconds, messaggio = "Documento con il Nome") {
                documento(it)?.contains("**Anna**") == true
            }
            assertTrue(documento(it).orEmpty().contains("**Voce 2**"), "una Voce senza Parlante resta 'Voce n'")

            // S4's command, over eventi.unitaDiLavoro: its ParlanteRinominato reaches the Documento after commit.
            val anna = it.parlanti.letture.parlantiDelProgetto().single().parlanteId
            it.parlanti.comandiParlante.rinomina(RinominaParlante(anna, "Bea")).atteso()
            attendiFinche(timeout = 10.seconds, messaggio = "Documento rinominato") {
                documento(it)?.contains("**Bea**") == true
            }
        }
    }

    @Test
    fun `AC-359 la revisione-policy e un abbonato sincrono, dentro la transazione della Revisione`() {
        AmbienteProgetto(radice).use {
            val id = it.importa()
            it.trascrivi(id)
            runBlocking { it.parlanti.comandi.esegui(ComandoVoce.Nuovo(voce(id, 2), "Anna")) }
            val attribuzioni = AttribuzioneRepositorySql(it.porte.database)
            var vistaNellaTransazione: ParlanteId? = null
            // Registered AFTER the policy: it runs inside the same transaction, then dooms it.
            it.porte.dispatcher.registraSincrono(
                AbbonatoSincrono { evento: EventoPubblicato ->
                    if (evento is VociUnite) {
                        vistaNellaTransazione = attribuzioni.trova(voce(id, 1))?.parlanteId
                        Esito.Errore(ErroreDiProva.Fallito("sonda"))
                    } else {
                        Esito.Ok(Unit)
                    }
                },
            )

            val esito = it.trascrizione.revisione.unisciVoci
                .esegui(UnisciVoci(id, sopravvive = VoceId(1), rimossa = VoceId(2)))

            assertTrue(esito is Esito.Errore)
            val anna = attribuzioni.trova(voce(id, 2))?.parlanteId
            assertEquals(anna, vistaNellaTransazione, "INV-21 Voce 1 eredita Anna DENTRO la transazione")
            assertNull(attribuzioni.trova(voce(id, 1)), "la Revisione annullata annulla anche la policy")
        }
    }

    @Test
    fun `AC-315 una Revisione committata porta a un RiallineaImpronte della sua Registrazione`() {
        AmbienteProgetto(radice, DiarizzatoreFinta(AmbienteProgetto.VOCE_1_IN_DUE_SEGMENTI)).use {
            val id = it.importa()
            it.trascrivi(id)
            runBlocking { it.parlanti.comandi.esegui(ComandoVoce.Nuovo(voce(id, 1), "Anna")) }
            val parlanti = ParlanteRepositorySql(it.porte.database, it.porte.lettura)
            assertEquals("0-1000,2000-3000", parlanti.impronteDiRegistrazione(id).single().sorgente)

            it.trascrizione.revisione.dividiVoce.esegui(DividiVoce(id, VoceId(1), setOf(SegmentoId(3)))).atteso()

            attendiFinche(timeout = 10.seconds, messaggio = "impronta riallineata alla nuova sorgente della Voce") {
                parlanti.impronteDiRegistrazione(id).single().sorgente == "0-1000"
            }
        }
    }

    @Test
    fun `AC-317 ImpronteRiallineate produce un Cambiamento e invalida la Proposta, senza rigenerare il Documento`() {
        val estrattore = EstrattoreConMutex()
        AmbienteProgetto(radice, estrattore = estrattore).use {
            val id = it.importa()
            it.trascrivi(id)
            runBlocking { it.parlanti.comandi.esegui(ComandoVoce.Nuovo(voce(id, 1), "Anna")) }
            attendiFinche(timeout = 10.seconds, messaggio = "Documento con il Nome") {
                documento(it)?.contains("**Anna**") == true
            }
            val cambiamenti = it.raccogliCambiamenti()
            it.parlanti.letture.proposta(voce(id, 2))
            it.parlanti.letture.proposta(voce(id, 2))
            val primaDellEvento = estrattore.chiamate.get()
            val file = checkNotNull(fileDocumento(it))
            val scritto = file.getLastModifiedTime()

            val dispatcher = it.porte.dispatcher
            dispatcher.unitaDiLavoro.inTransazione { Esito.Ok(dispatcher.pubblica(ImpronteRiallineate(id))) }.atteso()

            attendiFinche(timeout = 10.seconds, messaggio = "Cambiamento della Registrazione") {
                Cambiamento(id) in cambiamenti
            }
            it.parlanti.letture.proposta(voce(id, 2))
            assertEquals(primaDellEvento + 1, estrattore.chiamate.get(), "la Proposta e ricalcolata dopo l'evento")
            restaVeroPer(
                ATTESA_NESSUNA_RIGENERAZIONE_MS.milliseconds,
                messaggio = "le impronte non cambiano il Documento",
            ) {
                file.getLastModifiedTime() == scritto
            }
        }
    }

    @Test
    fun `AC-316 RiallineaTutteLeImpronte gira in background dopo il recupero, senza bloccare la UI`() {
        val (percorso, id) = progettoConImpronta()
        // A crashed in_corso run of the same Registrazione: RecuperaElaborazioniInterrotte (apriProgetto step 5) fails
        // it; the realignment (step 6) must find it already failed.
        conDatabase(percorso) { db ->
            ElaborazioneRepositorySql(db).salva(
                unaElaborazione(
                    StatoElaborazione.IN_CORSO,
                    ElaborazioneId("e-crash"),
                    id,
                    creataAlle = Instant.now().plusSeconds(60),
                    avviataAlle = Instant.now().plusSeconds(61),
                ),
            ).atteso()
        }
        val estrattore = EstrattoreConMutex(modello = "altro-modello") // every stored row is stale for it
        val aperti = CopyOnWriteArrayList<DatabaseProgetto>()
        val statiAlRiallineamento = CopyOnWriteArrayList<List<StatoElaborazione>>()
        AmbienteProgetto(
            radice.resolve("bis").also(Files::createDirectories),
            estrattore = estrattore,
            apriDatabase = { cartella -> apriDatabaseProgetto(cartella).also(aperti::add) },
        ).use {
            it.sessione.chiudi()
            estrattore.lock.lock() // the native Mutex is busy: RiallineaTutteLeImpronte will wait on it
            try {
                estrattore.primaDellaChiamata = {
                    if (statiAlRiallineamento.isEmpty()) {
                        statiAlRiallineamento += ElaborazioneRepositorySql(aperti.last().database)
                            .diRegistrazione(id).map { e -> e.stato }
                    }
                }

                it.apri(percorso) // returns while the realignment waits

                attendiFinche(timeout = 10.seconds, messaggio = "riallineamento in attesa del Mutex") {
                    estrattore.lock.hasQueuedThreads()
                }
                val presenter = costruisciRegistrazioniPresenter(it.grafo(), it.collaboratori) {}
                attendiFinche(timeout = 10.seconds, messaggio = "S2 usabile durante il riallineamento") {
                    presenter.stato.value is RegistrazioniUiStato.Dati
                }
            } finally {
                estrattore.lock.unlock()
            }
            attendiFinche(timeout = 10.seconds, messaggio = "impronta riallineata al modello corrente") {
                it.porte.parlanti.impronteDiRegistrazione(id).single().modello == "altro-modello"
            }
            val statiVisti = statiAlRiallineamento.first()
            assertTrue(StatoElaborazione.FALLITA in statiVisti, "dopo RecuperaElaborazioniInterrotte: $statiVisti")
            assertTrue(StatoElaborazione.IN_CORSO !in statiVisti, "nessun in_corso rimasto: $statiVisti")
            assertTrue(estrattore.thread.none { t -> t.name == AmbienteProgetto.THREAD_UI }, "mai sul thread della UI")
        }
    }

    @Test
    fun `AC-358 un'eccezione del riallineamento e registrata nel log e non ferma lo scope ne gli altri abbonati`() {
        val (percorso, id) = progettoConImpronta(DiarizzatoreFinta(AmbienteProgetto.VOCE_1_IN_DUE_SEGMENTI))
        val estrattore = EstrattoreConMutex(modello = "altro-modello").apply { fallisci = true }
        val registro = RegistroLog()
        AmbienteProgetto(
            radice.resolve("bis").also(Files::createDirectories),
            DiarizzatoreFinta(AmbienteProgetto.VOCE_1_IN_DUE_SEGMENTI),
            estrattore = estrattore,
        ).use {
            registro.use { _ ->
                it.sessione.chiudi()
                it.sessione.apri(percorso).atteso()

                // At open: RiallineaTutteLeImpronte throws → logged; the Parlanti scope is alive.
                attendiFinche(timeout = 10.seconds, messaggio = "fallimento all'apertura nel log") {
                    registro.da(ModuloParlanti::class.java)
                }
                estrattore.fallisci = false
                val esito = runBlocking { it.parlanti.comandi.esegui(ComandoVoce.Nuovo(voce(id, 2), "Bea")) }
                assertEquals(Esito.Ok(Unit), esito, "lo scope dei Parlanti e ancora vivo")

                // After commit: RiallineaImpronte throws → logged; the other subscribers still run.
                estrattore.fallisci = true
                val cambiamenti = it.raccogliCambiamenti()
                registro.svuota()
                it.trascrizione.revisione.dividiVoce.esegui(DividiVoce(id, VoceId(1), setOf(SegmentoId(3)))).atteso()
                attendiFinche(timeout = 10.seconds, messaggio = "fallimento dopo commit nel log") {
                    // a4-supporto-avvio: the retry's own Segnalazione now reports the failure
                    // (RitentaConBackoff) through the app's ONE JUL-backed Segnalazione (AC-C54), no longer
                    // ModuloParlanti's own per-class logger — RegistroLog.da follows either identity.
                    registro.da(ModuloParlanti::class.java)
                }
                attendiFinche(timeout = 10.seconds, messaggio = "S2/S3 informati della Revisione") {
                    Cambiamento(id) in cambiamenti
                }
                attendiFinche(timeout = 10.seconds, messaggio = "Documento rigenerato con la nuova Voce") {
                    documentoIn(Path.of(percorso))?.contains("**Voce 3**") == true
                }
            }
        }
    }

    @Test
    fun `con proposte false la Proposta e una Galleria vuota, senza estrazione, e la nomina manuale funziona`() {
        val estrattore = EstrattoreConMutex()
        AmbienteProgetto(radice, estrattore = estrattore, proposte = false).use {
            val id = it.importa()
            it.trascrivi(id)
            val nomina = runBlocking { it.parlanti.comandi.esegui(ComandoVoce.Nuovo(voce(id, 1), "Anna")) }
            assertEquals(Esito.Ok(Unit), nomina)
            val chiamate = estrattore.chiamate.get()

            assertEquals(emptyList(), it.parlanti.letture.proposta(voce(id, 2))?.candidati)
            assertEquals(chiamate, estrattore.chiamate.get())
        }
    }

    @Test
    fun `AC-541 le impronte scritte dal sostituto nessun-estrattore sono riderivate con TitaNet-small all apertura`() {
        val (percorso, id) = progettoConImpronta(estrattore = EstrattoreImprontaFinta(modello = "nessun-estrattore"))
        val titanet = EstrattoreImprontaFinta(modello = CatalogoDiarizzazione.embeddingTitanetSmall.id)
        AmbienteProgetto(radice.resolve("ter").also(Files::createDirectories), estrattore = titanet).use {
            it.sessione.chiudi()
            it.sessione.apri(percorso).atteso()
            attendiFinche(timeout = 10.seconds, messaggio = "impronta riderivata al modello reale") {
                val parlanti = ParlanteRepositorySql(it.porte.database, it.porte.lettura)
                parlanti.impronteDiRegistrazione(id).single().modello == CatalogoDiarizzazione.embeddingTitanetSmall.id
            }
        }
    }

    @Test
    fun `AC-C35 contesto lettura e la stessa istanza a cui il dispatcher delega`() {
        AmbienteProgetto(radice).use {
            val visto = it.porte.dispatcher.unitaDiLavoro.inTransazione {
                Esito.Ok(it.porte.lettura.inLettura { 1 })
            }.atteso()

            assertEquals(1, visto, "la inLettura annidata deve UNIRSI alla transazione, non aprirne una propria")
        }
    }

    /**
     * B17/D-0014 composition-level proof (not just the adapter level): a checkpoint left incomplete by a concurrent
     * DEFERRED reader is logged once at commit time (`ParlanteRepositorySql.alCheckpointIncompleto`); [ModuloParlanti]
     * itself only starts the retry worker (`porte.parlanti.avviaRitentaCheckpoint`) at [ModuloParlanti.avvia].
     *
     * The genuinely discriminating proof is `RitentaConBackoff`'s OWN "fallito"/"riuscito" report reaching the
     * log — checking the WAL file alone would NOT discriminate: releasing the parked reader alone lets SQLite's own
     * busy_timeout-bounded wait inside a wal_checkpoint(TRUNCATE) call succeed on its own, wired or not (verified:
     * an unwired run still empties the WAL). [ATTESA_PRIMO_RITENTO_MS] holds the reader past that busy_timeout, so
     * the FIRST wal_checkpoint(TRUNCATE) — both the commit-time one and the retry worker's own first attempt —
     * genuinely gives up (`busy`) instead of just outwaiting the reader; only a SUBSEQUENT attempt, after
     * `RitentaConBackoff`'s own backoff, converges. A throwaway probe removing `ModuloParlanti.avvia`'s
     * `avviaRitentaCheckpoint` call (never committed) leaves `registro` with ONLY the commit-time "incompleto"
     * line — verified: the final `attendiFinche` below times out (`richiedi(Unit)` queues a request nobody
     * consumes), the WAL still becomes empty on its own once the reader releases, but no `RitentaConBackoff:`
     * report is ever logged.
     */
    @Test
    fun `B17 un checkpoint incompleto per un lettore DEFERRED e ritentato dal worker di ModuloParlanti, loggato`() {
        val registro = RegistroLog()
        AmbienteProgetto(radice).use { ambiente ->
            registro.use { _ ->
                val id = ambiente.importa()
                ambiente.trascrivi(id)
                runBlocking { ambiente.parlanti.comandi.esegui(ComandoVoce.Nuovo(voce(id, 1), "Anna")) }
                val anna = ambiente.parlanti.letture.parlantiDelProgetto().single().parlanteId

                val parcheggiato = CountDownLatch(1)
                val via = CountDownLatch(1)
                val lettore = thread(name = "lettore-deferred-b17") {
                    ambiente.porte.lettura.inLettura {
                        // A real SELECT (never just the query_only pragma) is what actually pins a WAL snapshot.
                        ambiente.porte.parlanti.trova(anna)
                        parcheggiato.countDown()
                        via.await(ATTESA_LETTORE_S, TimeUnit.SECONDS)
                    }
                }
                attendiFinche(
                    timeout = 10.seconds,
                    messaggio = "il lettore deve parcheggiarsi su una lettura DEFERRED",
                ) {
                    parcheggiato.count == 0L
                }

                // EliminaParlante removes Anna's print: ParlanteRepositorySql.salva registers ONE checkpoint after
                // commit — incomplete here, since the DEFERRED reader above is still parked on an older snapshot.
                ambiente.parlanti.comandiParlante.elimina(EliminaParlante(anna)).atteso()

                assertTrue(
                    registro.contieneMessaggio("checkpoint WAL incompleto"),
                    "il checkpoint a fine commit deve risultare incompleto e loggato col lettore ancora parcheggiato",
                )
                pausaInTempoReale(
                    ATTESA_PRIMO_RITENTO_MS.milliseconds,
                    motivo = "il primo tentativo del worker deve vedere anch'esso il lettore parcheggiato e fallire",
                )
                via.countDown()
                lettore.join(10_000)

                attendiFinche(timeout = 15.seconds, messaggio = "RitentaConBackoff deve riportare il proprio ritento") {
                    registro.contieneMessaggio("RitentaConBackoff")
                }
            }
        }
    }

    @Test
    fun `AC-492 chiudere il progetto rilascia una volta il modello delle impronte, a lavori fermati`() {
        val rilasci = AtomicInteger()
        AmbienteProgetto(radice, rilasciaMl = { rilasci.incrementAndGet() }).use {
            assertEquals(0, rilasci.get(), "aperto: il modello resta in cache")

            it.sessione.chiudi()

            attendiFinche(timeout = 10.seconds, messaggio = "rilascio alla chiusura") { rilasci.get() == 1 }
        }
        assertEquals(1, rilasci.get())
    }

    /** A project with one Registrazione, Voce 1 attributed (print of the Finta's model); closed. */
    private fun progettoConImpronta(
        diarizzatore: DiarizzatoreFinta = DiarizzatoreFinta(AmbienteProgetto.DUE_VOCI),
        estrattore: EstrattoreImpronta = EstrattoreImprontaFinta(),
    ): Pair<String, snastro.kernel.RegistrazioneId> {
        val ambiente = AmbienteProgetto(radice, diarizzatore, estrattore = estrattore)
        val id = ambiente.importa()
        ambiente.trascrivi(id)
        runBlocking { ambiente.parlanti.comandi.esegui(ComandoVoce.Nuovo(voce(id, 1), "Anna")) }
        ambiente.close()
        return ambiente.progetto.percorso to id
    }

    private fun conDatabase(cartella: String, blocco: (SnastroDatabase) -> Unit) {
        val db = apriDatabaseProgetto(File(cartella))
        try {
            blocco(db.database)
        } finally {
            db.chiudi()
        }
    }

    private fun fileDocumento(ambiente: AmbienteProgetto): Path? = fileDocumentoIn(Path.of(ambiente.progetto.percorso))

    private fun fileDocumentoIn(progetto: Path): Path? =
        progetto.resolve("documenti").takeIf(Files::isDirectory)?.listDirectoryEntries("*.md")?.singleOrNull()

    private fun documentoIn(progetto: Path): String? = fileDocumentoIn(progetto)?.readText()

    private fun documento(ambiente: AmbienteProgetto): String? = fileDocumento(ambiente)?.readText()

    /** Captures WARNING records of the `snastro.avvio` loggers while in use. */
    private class RegistroLog : Handler(), AutoCloseable {
        // "snastro" (not "snastro.avvio"): a4-supporto-avvio's ONE Segnalazione (AC-C54) logs directly on
        // "snastro" — a record logged there never propagates DOWN to "snastro.avvio"'s own handlers, only UP
        // from a descendant logger. Attaching at the top catches both a per-class logger (propagates up) and
        // the shared Segnalazione (logs there directly).
        private val radice = Logger.getLogger("snastro")
        private val record = CopyOnWriteArrayList<LogRecord>()

        init {
            radice.addHandler(this)
        }

        fun svuota() = record.clear()

        /**
         * [classe]'s own per-class logger, OR the app's ONE shared Segnalazione (AC-C54, loggerName "snastro") — but
         * then only a report CAUSED by the print extractor's failure (pre-release L57: never another worker's WARNING).
         */
        fun da(classe: Class<*>): Boolean = record.any {
            it.level == Level.WARNING &&
                (it.loggerName == classe.name || it.loggerName == "snastro" && causataDallEstrattore(it.thrown))
        }

        private fun causataDallEstrattore(e: Throwable?): Boolean =
            generateSequence(e) { c -> c.cause }.any { c -> c.message == "estrazione fallita (finta)" }

        /** B17: any record (any level, any logger under "snastro") whose message contains [sottostringa]. */
        fun contieneMessaggio(sottostringa: String): Boolean = record.any { sottostringa in it.message.orEmpty() }

        override fun publish(r: LogRecord) {
            record += r
        }

        override fun flush() = Unit

        override fun close() {
            radice.removeHandler(this)
        }
    }

    private companion object {
        const val ATTESA_NESSUNA_RIGENERAZIONE_MS = 500L

        // Only a hang guard: B17 releases the reader itself. At 15 s a slower host (the GitHub macOS runner) let the
        // reader release on its own before the commit-time checkpoint ran, so that checkpoint completed.
        const val ATTESA_LETTORE_S = 60L

        // Comfortably longer than AperturaDatabase's busy_timeout (5s): the FIRST wal_checkpoint(TRUNCATE) — both
        // the commit-time one and the retry worker's own first attempt — internally WAITS on that timeout for the
        // parked reader before giving up, so holding it any shorter would let that internal wait alone (not the
        // retry's OWN backoff/second attempt) explain a success — never actually exercising the backoff+retry path.
        const val ATTESA_PRIMO_RITENTO_MS = 7_000L
    }
}
