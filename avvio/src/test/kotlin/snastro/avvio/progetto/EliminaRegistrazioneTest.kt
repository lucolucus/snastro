package snastro.avvio.progetto

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.io.TempDir
import snastro.avvio.costruisciRegistrazioniPresenter
import snastro.avvio.orologioApp
import snastro.kernel.CampioniAudio
import snastro.kernel.ErroreDiProva
import snastro.kernel.Esito
import snastro.kernel.EventoPubblicato
import snastro.kernel.ParlanteId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.parlanti.adattatori.persistenza.AttribuzioneRepositorySql
import snastro.parlanti.adattatori.persistenza.ParlanteRepositorySql
import snastro.parlanti.applicazione.comandi.EliminaParlante
import snastro.persistenza.DatabaseProgettoContato
import snastro.persistenza.UnitaDiLavoroSql
import snastro.persistenza.apriDatabaseProgetto
import snastro.progetto.adattatori.persistenza.EliminazioniInSospesoSql
import snastro.progetto.adattatori.persistenza.RegistrazioneRepositorySql
import snastro.progetto.applicazione.comandi.EliminaRegistrazione
import snastro.progetto.applicazione.comandi.RinominaRegistrazione
import snastro.progetto.applicazione.eventi.RegistrazioneEliminata
import snastro.progetto.applicazione.porte.EliminazioneInSospeso
import snastro.sbobinatura.applicazione.letture.Sbobinatura
import snastro.supporto.test.attendiFinche
import snastro.supporto.test.restaVeroPer
import snastro.trascrizione.adattatori.persistenza.ElaborazioneRepositorySql
import snastro.trascrizione.adattatori.persistenza.TrascrittoRepositorySql
import snastro.trascrizione.applicazione.comandi.AnnullaElaborazione
import snastro.trascrizione.applicazione.comandi.AvviaElaborazione
import snastro.trascrizione.applicazione.letture.StatoElaborazioneVista
import snastro.trascrizione.applicazione.porte.Diarizzatore
import snastro.trascrizione.applicazione.porte.FaseElaborazione
import snastro.trascrizione.applicazione.porte.Turno
import snastro.trascrizione.dominio.ErroreTrascrizione.ElaborazioneGiaAperta
import snastro.trascrizione.dominio.NumeroPersone
import snastro.ui.Cambiamento
import snastro.ui.registrazione.ComandoVoce
import snastro.ui.registrazioni.RegistrazioniPresenter
import snastro.ui.registrazioni.RegistrazioniUiStato
import snastro.ui.registrazioni.RigaRegistrazione
import snastro.ui.registrazioni.StatoEliminazione
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * ADR 0020 end to end on the REAL single composition ([AmbienteProgetto]: a SQLite project FILE opened by the session
 * with the production driver, real queue, real Parlanti/Trascrizione subscribers, real Sbobinatura writer; Finte ML):
 * AC-630 (S2 supplied), AC-631, AC-633, AC-634 (INV-28), AC-635. Rows are read through the SQL adapters; the project
 * database is the production one with its driver counting `wal_checkpoint` ([DatabaseProgettoContato], AC-634).
 */
class EliminaRegistrazioneTest {
    @TempDir
    lateinit var radice: Path

    private val diarizzatore = DiarizzatoreTrattenuto()
    private val database = CopyOnWriteArrayList<DatabaseProgettoContato>()

    @Test
    fun `AC-634 INV-28 Elimina da S2 cancella righe e file di R, purga l Ospite, tiene Mario e la lapide`() {
        ambiente().use {
            val s = prepara(it)
            val cartella = Path.of(it.progetto.percorso)
            val sbobinaturaR = Path.of(checkNotNull(it.sbobinatura.percorsoSbobinatura(s.r)))
            val sbobinaturaQ = Path.of(checkNotNull(it.sbobinatura.percorsoSbobinatura(s.q)))
            val s2 = presenterS2(it)
            attendiFinche(timeout = 10.seconds, messaggio = "'Elimina…' disponibile su R") {
                riga(s2, s.r)?.eliminazione == StatoEliminazione.Disponibile
            }

            s2.elimina(s.r)
            attendiFinche(timeout = 10.seconds, messaggio = "conferma") { riga(s2, s.r)?.confermaElimina == true }
            val contato = database.last()
            contato.azzeraCheckpoint()
            s2.confermaElimina(s.r)

            attendiFinche(timeout = 10.seconds, messaggio = "S2 elenca solo Q") {
                righe(s2)?.map { r -> r.registrazioneId } == listOf(s.q)
            }
            assertEquals(NESSUNA_RIGA, righe(it, s.r), "righe di R per tabella: solo quella in sospeso")
            assertEquals(1, righe(it, s.q).getValue("registrazione"))
            val galleria = it.parlanti.letture.parlantiDelProgetto().associateBy { p -> p.nome }
            assertEquals(setOf("Mario", "Terzo"), galleria.keys, "l'Ospite non esiste piu'")
            assertEquals(1, galleria.getValue("Mario").numImpronte)
            assertEquals(1, galleria.getValue("Mario").numRegistrazioni)
            val parlanti = ParlanteRepositorySql(it.porte.database, it.porte.lettura)
            assertEquals(1, parlanti.impronteDiRegistrazione(s.q).size)
            val lapide = checkNotNull(parlanti.trova(s.terzo))
            assertTrue(lapide.eliminato, "la lapide resta eliminata")
            assertEquals("Terzo", lapide.nome.valore)
            assertFalse(Files.exists(cartella.resolve(audio(s.r))))
            assertFalse(Files.exists(cartella.resolve(wav(s.r))))
            attendiFinche(timeout = 10.seconds, messaggio = "Sbobinatura di R rimosso") { !Files.exists(sbobinaturaR) }
            assertTrue(Files.exists(cartella.resolve(audio(s.q))))
            assertTrue(Files.exists(cartella.resolve(wav(s.q))))
            assertTrue(Files.exists(sbobinaturaQ))
            assertEquals(
                listOf(false, false, false),
                contato.checkpoint,
                "Q-1: uno per rimozione (salva di Mario, salva e rimuovi dell'Ospite), tutti a transazione chiusa",
            )

            it.sessione.chiudi()
            it.sessione.apri(it.progetto.percorso).atteso()

            attendiFinche(timeout = 10.seconds, messaggio = "riga in sospeso conclusa alla riapertura") {
                righe(it, s.r).getValue("eliminazione_in_sospeso") == 0
            }
        }
    }

    @Test
    fun `AC-635 INV-28 in corso o in coda l eliminazione e rifiutata e nulla cambia, dopo Annulla riesce`() {
        ambiente().use {
            val s = prepara(it)
            val z = it.importa()
            it.collaboratori.rinominaRegistrazione(RinominaRegistrazione(z, "Terza riunione")).atteso()
            val sx = it.importa()
            it.collaboratori.rinominaRegistrazione(RinominaRegistrazione(sx, "Quarta riunione")).atteso()
            val eliminate = CopyOnWriteArrayList<EventoPubblicato>()
            it.porte.dispatcher.registraDopoCommit { e -> if (e is RegistrazioneEliminata) eliminate += e }
            val cartella = Path.of(it.progetto.percorso)

            // S in_corso: its pipeline held in diarization.
            val primo = diarizzatore.trattieni()
            it.collaboratori.avviaElaborazione(AvviaElaborazione(sx)).atteso()
            attendiFinche(timeout = 10.seconds, messaggio = "S in corso") {
                fase(it, sx) == FaseElaborazione.DIARIZZAZIONE
            }
            val prima = istantanea(it, sx)
            it.collaboratori.eliminaRegistrazione(EliminaRegistrazione(sx)).erroreAtteso<ElaborazioneGiaAperta>()
            assertEquals(prima, istantanea(it, sx))
            assertTrue(Files.exists(cartella.resolve(audio(sx))))
            primo.countDown()
            it.attendiCompletata(sx)

            // S re-queued in_attesa behind Z, held in diarization.
            val secondo = diarizzatore.trattieni()
            it.collaboratori.avviaElaborazione(AvviaElaborazione(z)).atteso()
            attendiFinche(timeout = 10.seconds, messaggio = "Z in corso") {
                fase(it, z) == FaseElaborazione.DIARIZZAZIONE
            }
            it.collaboratori.avviaElaborazione(AvviaElaborazione(sx)).atteso()
            attendiFinche(timeout = 10.seconds, messaggio = "S in coda") {
                it.stato(sx) == StatoElaborazioneVista.IN_ATTESA
            }
            val inCoda = istantanea(it, sx)
            it.collaboratori.eliminaRegistrazione(EliminaRegistrazione(sx)).erroreAtteso<ElaborazioneGiaAperta>()
            assertEquals(inCoda, istantanea(it, sx))
            assertTrue(Files.exists(cartella.resolve(audio(sx))))
            assertEquals(emptyList(), inSospeso(it), "nessuna riga in sospeso")
            assertEquals(emptyList(), eliminate, "nessun evento dopo il commit")

            val inAttesa = checkNotNull(it.vistaDi(sx).elaborazioneId)
            it.trascrizione.annullaElaborazione(AnnullaElaborazione(inAttesa)).atteso()
            it.collaboratori.eliminaRegistrazione(EliminaRegistrazione(sx)).atteso()

            assertEquals(0, righe(it, sx).getValue("registrazione"))
            assertEquals(1, eliminate.size)
            assertEquals(1, righe(it, s.r).getValue("registrazione"), "le altre restano")
            secondo.countDown()
            it.attendiCompletata(z)
        }
    }

    @Test
    fun `AC-631 RegistrazioneEliminata dopo il commit invalida le Proposte, un Cambiamento null, mai su rollback`() {
        val estrattore = EstrattoreConMutex()
        AmbienteProgetto(radice, estrattore = estrattore).use {
            val x = it.importa()
            val y = it.importa()
            it.collaboratori.rinominaRegistrazione(RinominaRegistrazione(y, "Altra riunione")).atteso()
            it.trascrivi(x)
            it.trascrivi(y)
            runBlocking { it.parlanti.comandi.esegui(ComandoVoce.Nuovo(voce(x, 1), "Anna")) }
            attendiFinche(timeout = 10.seconds, messaggio = "Nomi nella Sbobinatura") {
                it.sbobinatura.percorsoSbobinatura(x)?.let { p -> "**Anna**" in Path.of(p).toFile().readText() } == true
            }
            val cambiamenti = it.raccogliCambiamenti()
            it.parlanti.letture.proposta(voce(x, 2))
            val calcolate = estrattore.chiamate.get()
            val dispatcher = it.porte.dispatcher

            dispatcher.unitaDiLavoro.inTransazione {
                dispatcher.pubblica(eliminataDi(it, y))
                Esito.Errore(ErroreDiProva.Fallito("rollback"))
            }
            restaVeroPer(ATTESA_NESSUN_EFFETTO_MS.milliseconds, messaggio = "mai consegnato su rollback") {
                Cambiamento(null) !in cambiamenti
            }
            it.parlanti.letture.proposta(voce(x, 2))
            assertEquals(calcolate, estrattore.chiamate.get(), "la Proposta resta in cache dopo un rollback")

            it.collaboratori.eliminaRegistrazione(EliminaRegistrazione(y)).atteso()

            attendiFinche(timeout = 10.seconds, messaggio = "Cambiamento(null)") { Cambiamento(null) in cambiamenti }
            it.parlanti.letture.proposta(voce(x, 2))
            assertEquals(calcolate + 1, estrattore.chiamate.get(), "la Proposta e' ricalcolata dopo l'evento")
        }
    }

    @Test
    fun `AC-633 all apertura una riga in sospeso fa sparire audio, WAV e Sbobinatura, e la riga`() {
        val (percorso, file) = progettoConEliminazioneInterrotta()
        val appunti = Path.of(percorso).resolve("sbobinature/appunti.md")
        Files.write(appunti, byteArrayOf(1))

        AmbienteProgetto(radice.resolve("bis").also(Files::createDirectories)).use {
            it.sessione.chiudi()
            it.sessione.apri(percorso).atteso()

            attendiFinche(timeout = 10.seconds, messaggio = "eliminazione completata all'apertura") {
                inSospeso(it).isEmpty()
            }
            file.forEach { f -> assertFalse(Files.exists(f), "$f") }
            assertTrue(Files.exists(appunti), "un file dell'utente non si tocca")
        }
    }

    @Test
    fun `AC-633 se un derivato non si rimuove la riga resta per la prossima apertura`() {
        val (percorso, file) = progettoConEliminazioneInterrotta()
        val wav = file[1]
        Files.delete(wav)
        Files.createDirectories(wav.resolve("non-vuota")) // deleting it fails with an IOException

        AmbienteProgetto(radice.resolve("bis").also(Files::createDirectories)).use {
            it.sessione.chiudi()
            it.sessione.apri(percorso).atteso()

            attendiFinche(timeout = 10.seconds, messaggio = "audio scartato") { !Files.exists(file[0]) }
            restaVeroPer(
                ATTESA_NESSUN_EFFETTO_MS.milliseconds,
                messaggio = "la pulizia dei derivati e' fallita: la riga resta",
            ) { inSospeso(it).size == 1 }
            assertEquals(1, inSospeso(it).size, "la pulizia dei derivati e' fallita: la riga resta")
        }
    }

    /**
     * A project folder where a Registrazione was deleted and the app died after the COMMIT: its pending row, and its
     * `audio/`, `cache/audio/` and `sbobinature/` files, all still there. Returns the folder and those three files.
     */
    private fun progettoConEliminazioneInterrotta(): Pair<String, List<Path>> {
        val percorso = ambiente().use { it.progetto.percorso }
        val cartella = Path.of(percorso)
        val id = RegistrazioneId("eliminata-prima-del-crash")
        val data = LocalDate.parse("2026-09-12")
        val file = listOf(
            "audio/${id.valore}.m4a",
            wav(id),
            "sbobinature/${Sbobinatura.nomeFile(data, "Riunione persa")}",
        ).map(cartella::resolve)
        file.forEach { f ->
            Files.createDirectories(f.parent)
            Files.write(f, byteArrayOf(1))
        }
        val db = apriDatabaseProgetto(cartella.toFile())
        UnitaDiLavoroSql(db.database).inTransazione {
            EliminazioniInSospesoSql(db.database, orologio()).registra(
                EliminazioneInSospeso(id, "Riunione persa", data, RiferimentoAudio("audio/${id.valore}.m4a")),
            )
            Esito.Ok(Unit)
        }.atteso()
        db.chiudi()
        return percorso to file
    }

    /** AC-634/AC-635's R and Q; [terzo] is the eliminato tombstone attributed to R's Voce 3. */
    private class Scenario(val r: RegistrazioneId, val q: RegistrazioneId, val mario: ParlanteId, val terzo: ParlanteId)

    private fun ambiente() = AmbienteProgetto(radice, diarizzatore, apriDatabase = { cartella ->
        DatabaseProgettoContato.apri(cartella).also(database::add).database
    })

    private fun prepara(ambiente: AmbienteProgetto): Scenario {
        val r = ambiente.importa()
        val q = ambiente.importa()
        ambiente.collaboratori.rinominaRegistrazione(RinominaRegistrazione(q, "Altra riunione")).atteso()
        ambiente.trascrivi(r)
        ambiente.trascrivi(q)
        comando(ambiente, ComandoVoce.Nuovo(voce(r, 1), "Mario"))
        comando(ambiente, ComandoVoce.Salta(voce(r, 2)))
        comando(ambiente, ComandoVoce.Nuovo(voce(r, 3), "Terzo"))
        val galleria = ambiente.parlanti.letture.parlantiDelProgetto()
        val mario = galleria.single { p -> p.nome == "Mario" }.parlanteId
        val terzo = galleria.single { p -> p.nome == "Terzo" }.parlanteId
        comando(ambiente, ComandoVoce.Conferma(voce(q, 1), mario))
        ambiente.parlanti.comandiParlante.elimina(EliminaParlante(terzo)).atteso()
        assertTrue(galleria.any { p -> p.nome.startsWith("Ospite") })
        val cartella = Path.of(ambiente.progetto.percorso)
        listOf(r, q).forEach { id -> Files.write(cartella.resolve(wav(id)), byteArrayOf(1)) }
        attendiFinche(timeout = 10.seconds, messaggio = "Sbobinature di R e Q scritti") {
            ambiente.sbobinatura.percorsoSbobinatura(r) != null && ambiente.sbobinatura.percorsoSbobinatura(q) != null
        }
        return Scenario(r, q, mario, terzo)
    }

    private fun comando(ambiente: AmbienteProgetto, c: ComandoVoce) {
        assertEquals(Esito.Ok(Unit), runBlocking { ambiente.parlanti.comandi.esegui(c) })
    }

    private fun AmbienteProgetto.attendiCompletata(id: RegistrazioneId) = attendiFinche(
        timeout = 10.seconds,
        messaggio = "completata",
    ) {
        stato(id) == StatoElaborazioneVista.COMPLETATA
    }

    private fun fase(ambiente: AmbienteProgetto, id: RegistrazioneId): FaseElaborazione? =
        ambiente.vistaDi(id).fase

    /** Every row count keyed by [id] + its audio copy — what a refused deletion must leave unchanged. */
    private fun istantanea(ambiente: AmbienteProgetto, id: RegistrazioneId): Map<String, Any> =
        righe(ambiente, id) + ("audio" to Files.exists(Path.of(ambiente.progetto.percorso).resolve(audio(id))))

    /**
     * The rows keyed by [id], per table, through the SQL adapters. `voce`/`segmento` are counted on the stored
     * Trascritto: none can outlive it (the voce FK is immediate, the segmento one checked at COMMIT).
     */
    private fun righe(ambiente: AmbienteProgetto, id: RegistrazioneId): Map<String, Int> {
        val db = ambiente.porte.database
        val lettura = ambiente.porte.lettura
        val trascritto = TrascrittoRepositorySql(db, lettura).trova(id)
        return mapOf(
            "registrazione" to listOfNotNull(RegistrazioneRepositorySql(db).trova(id)).size,
            "elaborazione" to ElaborazioneRepositorySql(db).diRegistrazione(id).size,
            "trascritto" to listOfNotNull(trascritto).size,
            "voce" to (trascritto?.voci?.size ?: 0),
            "segmento" to (trascritto?.segmenti?.size ?: 0),
            "attribuzione" to AttribuzioneRepositorySql(db).diRegistrazione(id).size,
            "impronta_vocale" to ParlanteRepositorySql(db, lettura).impronteDiRegistrazione(id).size,
            "eliminazione_in_sospeso" to inSospeso(ambiente).count { e -> e.registrazioneId == id },
        )
    }

    private fun inSospeso(ambiente: AmbienteProgetto): List<EliminazioneInSospeso> =
        EliminazioniInSospesoSql(ambiente.porte.database, orologio()).elenco()

    private fun eliminataDi(ambiente: AmbienteProgetto, id: RegistrazioneId): RegistrazioneEliminata {
        val r = ambiente.collaboratori.registrazioni().single { v -> v.registrazioneId == id }
        val progetto = ambiente.progetto.progettoId
        return RegistrazioneEliminata(id, progetto, r.titolo, r.dataRegistrazione, RiferimentoAudio(audio(id)))
    }

    private fun presenterS2(ambiente: AmbienteProgetto): RegistrazioniPresenter =
        costruisciRegistrazioniPresenter(ambiente.grafo(), ambiente.collaboratori) {}

    private fun righe(s2: RegistrazioniPresenter): List<RigaRegistrazione>? =
        (s2.stato.value as? RegistrazioniUiStato.Dati)?.righe

    private fun riga(s2: RegistrazioniPresenter, id: RegistrazioneId): RigaRegistrazione? =
        righe(s2)?.find { it.registrazioneId == id }

    /** A [Diarizzatore] returning three Voci; [trattieni] holds the NEXT run in diarization until released. */
    private class DiarizzatoreTrattenuto : Diarizzatore {
        @Volatile private var barriera: CountDownLatch? = null

        fun trattieni(): CountDownLatch = CountDownLatch(1).also { barriera = it }

        override fun diarizza(c: CampioniAudio, numeroPersone: NumeroPersone?): List<Turno> {
            barriera?.let { b ->
                barriera = null
                b.await()
            }
            return AmbienteProgetto.TRE_VOCI
        }
    }

    private companion object {
        const val ATTESA_NESSUN_EFFETTO_MS = 500L

        /** INV-28 after the COMMIT: nothing keyed by the Registrazione but its pending row. */
        val NESSUNA_RIGA = mapOf(
            "registrazione" to 0,
            "elaborazione" to 0,
            "trascritto" to 0,
            "voce" to 0,
            "segmento" to 0,
            "attribuzione" to 0,
            "impronta_vocale" to 0,
            "eliminazione_in_sospeso" to 1,
        )

        fun audio(id: RegistrazioneId) = "audio/${id.valore}.wav" // minting rule of RiferimentoAudio (a .wav source)

        fun wav(id: RegistrazioneId) = "cache/audio/${id.valore}.wav"

        fun orologio() = orologioApp()
    }
}
