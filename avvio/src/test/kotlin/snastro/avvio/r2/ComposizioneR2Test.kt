package snastro.avvio.r2

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.io.TempDir
import snastro.avvio.r1.attendiFinche
import snastro.kernel.AbbonatoSincrono
import snastro.kernel.ErroreDiProva
import snastro.kernel.Esito
import snastro.kernel.EventoPubblicato
import snastro.kernel.ParlanteId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.kernel.atteso
import snastro.parlanti.adattatori.persistenza.AttribuzioneRepositorySql
import snastro.parlanti.adattatori.persistenza.ParlanteRepositorySql
import snastro.parlanti.applicazione.comandi.RinominaParlante
import snastro.parlanti.applicazione.eventi.ImpronteRiallineate
import snastro.trascrizione.applicazione.comandi.DividiVoce
import snastro.trascrizione.applicazione.comandi.UnisciVoci
import snastro.trascrizione.applicazione.eventi.VociUnite
import snastro.trascrizione.applicazione.porte.DiarizzatoreFinta
import snastro.ui.Cambiamento
import snastro.ui.registrazione.ComandoVoce
import snastro.ui.registrazioni.RegistrazioniUiStato
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * End-to-end ACs of the R2 composition on [AmbienteR2] (real SessioneProgettoImpl + EstensioneR2 over a
 * real project folder; only FFmpeg and the ML models are Finte): AC-359, AC-315, AC-316, AC-317, AC-358.
 */
class ComposizioneR2Test {
    @TempDir
    lateinit var radice: Path

    @Test
    fun `AC-359 il Documento mostra i Nomi attribuiti e ne segue la rinomina`() {
        AmbienteR2(radice).use {
            val id = it.importa()
            it.trascrivi(id)

            assertEquals(Esito.Ok(Unit), runBlocking { it.r2.comandi.esegui(ComandoVoce.Nuovo(voce(id, 1), "Anna")) })
            attendiFinche(messaggio = "Documento con il Nome") { documento(it)?.contains("**Anna**") == true }
            assertTrue(documento(it).orEmpty().contains("**Voce 2**"), "una Voce senza Parlante resta 'Voce n'")

            // S4's command, over eventi.unitaDiLavoro: its ParlanteRinominato reaches the Documento after commit.
            val anna = it.r2.letture.parlantiDelProgetto().single().parlanteId
            it.r2.comandiParlante.rinomina(RinominaParlante(anna, "Bea")).atteso()
            attendiFinche(messaggio = "Documento rinominato") { documento(it)?.contains("**Bea**") == true }
        }
    }

    @Test
    fun `AC-359 la revisione-policy e un abbonato sincrono, dentro la transazione della Revisione`() {
        AmbienteR2(radice).use {
            val id = it.importa()
            it.trascrivi(id)
            runBlocking { it.r2.comandi.esegui(ComandoVoce.Nuovo(voce(id, 2), "Anna")) }
            val attribuzioni = AttribuzioneRepositorySql(it.contesto.database)
            var vistaNellaTransazione: ParlanteId? = null
            // Registered AFTER the policy: it runs inside the same transaction, then dooms it.
            it.contesto.dispatcher.registraSincrono(
                AbbonatoSincrono { evento: EventoPubblicato ->
                    if (evento is VociUnite) {
                        vistaNellaTransazione = attribuzioni.trova(voce(id, 1))?.parlanteId
                        Esito.Errore(ErroreDiProva.Fallito("sonda"))
                    } else {
                        Esito.Ok(Unit)
                    }
                },
            )

            val esito = it.r2.r1.revisione.unisciVoci
                .esegui(UnisciVoci(id, sopravvive = VoceId(1), rimossa = VoceId(2)))

            assertTrue(esito is Esito.Errore)
            val anna = attribuzioni.trova(voce(id, 2))?.parlanteId
            assertEquals(anna, vistaNellaTransazione, "INV-21 Voce 1 eredita Anna DENTRO la transazione")
            assertNull(attribuzioni.trova(voce(id, 1)), "la Revisione annullata annulla anche la policy")
        }
    }

    @Test
    fun `AC-315 una Revisione committata porta a un RiallineaImpronte della sua Registrazione`() {
        AmbienteR2(radice, DiarizzatoreFinta(AmbienteR2.VOCE_1_IN_DUE_SEGMENTI)).use {
            val id = it.importa()
            it.trascrivi(id)
            runBlocking { it.r2.comandi.esegui(ComandoVoce.Nuovo(voce(id, 1), "Anna")) }
            val parlanti = ParlanteRepositorySql(it.contesto.database)
            assertEquals("0-1000,2000-3000", parlanti.impronteDiRegistrazione(id).single().sorgente)

            it.r2.r1.revisione.dividiVoce.esegui(DividiVoce(id, VoceId(1), setOf(SegmentoId(3)))).atteso()

            attendiFinche(messaggio = "impronta riallineata alla nuova sorgente della Voce") {
                parlanti.impronteDiRegistrazione(id).single().sorgente == "0-1000"
            }
        }
    }

    @Test
    fun `AC-317 ImpronteRiallineate produce un Cambiamento e invalida la Proposta, senza rigenerare il Documento`() {
        val estrattore = EstrattoreConMutex()
        AmbienteR2(radice, estrattore = estrattore).use {
            val id = it.importa()
            it.trascrivi(id)
            runBlocking { it.r2.comandi.esegui(ComandoVoce.Nuovo(voce(id, 1), "Anna")) }
            attendiFinche(messaggio = "Documento con il Nome") { documento(it)?.contains("**Anna**") == true }
            val cambiamenti = raccogli(it)
            it.r2.letture.proposta(voce(id, 2))
            it.r2.letture.proposta(voce(id, 2))
            val primaDellEvento = estrattore.chiamate.get()
            val file = checkNotNull(fileDocumento(it))
            val scritto = file.getLastModifiedTime()

            val dispatcher = it.contesto.dispatcher
            dispatcher.unitaDiLavoro.inTransazione { Esito.Ok(dispatcher.pubblica(ImpronteRiallineate(id))) }.atteso()

            attendiFinche(messaggio = "Cambiamento della Registrazione") { Cambiamento(id) in cambiamenti }
            it.r2.letture.proposta(voce(id, 2))
            assertEquals(primaDellEvento + 1, estrattore.chiamate.get(), "la Proposta e ricalcolata dopo l'evento")
            Thread.sleep(ATTESA_NESSUNA_RIGENERAZIONE_MS)
            assertEquals(scritto, file.getLastModifiedTime(), "le impronte non cambiano il Documento")
        }
    }

    @Test
    fun `AC-316 RiallineaTutteLeImpronte gira in background dopo il recupero, senza bloccare la UI`() {
        val (percorso, id) = progettoConImpronta()
        val estrattore = EstrattoreConMutex(modello = "altro-modello") // every stored row is stale for it
        var recuperoGiaConcluso: Boolean? = null
        AmbienteR2(radice.resolve("bis").also(Files::createDirectories), estrattore = estrattore).use {
            it.sessione.chiudi()
            estrattore.lock.lock() // the native Mutex is busy: RiallineaTutteLeImpronte will wait on it
            try {
                estrattore.primaDellaChiamata = {
                    recuperoGiaConcluso = it.progettoEsteso(indice = 1).r1.recuperoConcluso.isCompleted
                }

                it.sessione.apri(percorso).atteso() // returns while the realignment waits

                attendiFinche(messaggio = "riallineamento in attesa del Mutex") { estrattore.lock.hasQueuedThreads() }
                val presenter = costruisciRegistrazioniPresenterR2(it.grafoR0, it.collaboratori, it.r2) {}
                attendiFinche(messaggio = "S2 usabile durante il riallineamento") {
                    presenter.stato.value is RegistrazioniUiStato.Dati
                }
            } finally {
                estrattore.lock.unlock()
            }
            attendiFinche(messaggio = "impronta riallineata al modello corrente") {
                val riga = ParlanteRepositorySql(it.contesto.database).impronteDiRegistrazione(id).single()
                riga.modello == "altro-modello"
            }
            assertEquals(true, recuperoGiaConcluso, "dopo RecuperaElaborazioniInterrotte")
            assertTrue(estrattore.thread.none { t -> t.name == AmbienteR2.THREAD_UI }, "mai sul thread della UI")
        }
    }

    @Test
    fun `AC-358 un'eccezione del riallineamento e registrata nel log e non ferma lo scope ne gli altri abbonati`() {
        val (percorso, id) = progettoConImpronta(DiarizzatoreFinta(AmbienteR2.VOCE_1_IN_DUE_SEGMENTI))
        val estrattore = EstrattoreConMutex(modello = "altro-modello").apply { fallisci = true }
        val registro = RegistroLog()
        AmbienteR2(
            radice.resolve("bis").also(Files::createDirectories),
            DiarizzatoreFinta(AmbienteR2.VOCE_1_IN_DUE_SEGMENTI),
            estrattore,
        ).use {
            registro.use { _ ->
                it.sessione.chiudi()
                it.sessione.apri(percorso).atteso()

                // At open: RiallineaTutteLeImpronte throws → logged; the R2 scope is alive.
                attendiFinche(messaggio = "fallimento all'apertura nel log") { registro.da(EstensioneR2::class.java) }
                estrattore.fallisci = false
                val esito = runBlocking { it.r2.comandi.esegui(ComandoVoce.Nuovo(voce(id, 2), "Bea")) }
                assertEquals(Esito.Ok(Unit), esito, "lo scope dei Parlanti e ancora vivo")

                // After commit: RiallineaImpronte throws → logged; the other subscribers still run.
                estrattore.fallisci = true
                val cambiamenti = raccogli(it)
                registro.svuota()
                it.r2.r1.revisione.dividiVoce.esegui(DividiVoce(id, VoceId(1), setOf(SegmentoId(3)))).atteso()
                attendiFinche(messaggio = "fallimento dopo commit nel log") {
                    registro.da(EstrattoreImprontaConLog::class.java)
                }
                attendiFinche(messaggio = "S2/S3 informati della Revisione") { Cambiamento(id) in cambiamenti }
                attendiFinche(messaggio = "Documento rigenerato con la nuova Voce") {
                    documentoIn(Path.of(percorso))?.contains("**Voce 3**") == true
                }
            }
        }
    }

    @Test
    fun `REALI senza estrattore la Proposta e una Galleria vuota, senza estrazione, e la nomina manuale funziona`() {
        val estrattore = EstrattoreConMutex()
        AmbienteR2(radice, estrattore = estrattore, proposte = false).use {
            val id = it.importa()
            it.trascrivi(id)
            assertEquals(Esito.Ok(Unit), runBlocking { it.r2.comandi.esegui(ComandoVoce.Nuovo(voce(id, 1), "Anna")) })
            val chiamate = estrattore.chiamate.get()

            assertEquals(emptyList(), it.r2.letture.proposta(voce(id, 2))?.candidati)
            assertEquals(chiamate, estrattore.chiamate.get())
        }
    }

    /** A project with one Registrazione, Voce 1 attributed (print of the Finta's model); closed. */
    private fun progettoConImpronta(
        diarizzatore: DiarizzatoreFinta = DiarizzatoreFinta(AmbienteR2.DUE_VOCI),
    ): Pair<String, snastro.kernel.RegistrazioneId> {
        val ambiente = AmbienteR2(radice, diarizzatore)
        val id = ambiente.importa()
        ambiente.trascrivi(id)
        runBlocking { ambiente.r2.comandi.esegui(ComandoVoce.Nuovo(voce(id, 1), "Anna")) }
        ambiente.close()
        return ambiente.progetto.percorso to id
    }

    /** Collects the project's Cambiamenti from now on (the flows' replayed past ones dropped). */
    private fun raccogli(ambiente: AmbienteR2): MutableList<Cambiamento> {
        val cambiamenti = CopyOnWriteArrayList<Cambiamento>()
        ambiente.scope.launch { ambiente.collaboratori.aggiornamentiVista.cambiamenti.collect(cambiamenti::add) }
        Thread.sleep(ATTESA_REPLAY_MS)
        cambiamenti.clear()
        return cambiamenti
    }

    private fun fileDocumento(ambiente: AmbienteR2): Path? = fileDocumentoIn(Path.of(ambiente.progetto.percorso))

    private fun fileDocumentoIn(progetto: Path): Path? =
        progetto.resolve("documenti").takeIf(Files::isDirectory)?.listDirectoryEntries("*.md")?.singleOrNull()

    private fun documentoIn(progetto: Path): String? = fileDocumentoIn(progetto)?.readText()

    private fun documento(ambiente: AmbienteR2): String? = fileDocumento(ambiente)?.readText()

    /** Captures WARNING records of the `snastro.avvio` loggers while in use. */
    private class RegistroLog : Handler(), AutoCloseable {
        private val radice = Logger.getLogger("snastro.avvio")
        private val record = CopyOnWriteArrayList<LogRecord>()

        init {
            radice.addHandler(this)
        }

        fun svuota() = record.clear()

        fun da(classe: Class<*>): Boolean = record.any { it.loggerName == classe.name && it.level == Level.WARNING }

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
        const val ATTESA_REPLAY_MS = 200L
    }
}
