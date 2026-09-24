package snastro.avvio.r2

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.io.TempDir
import snastro.avvio.r1.attendiFinche
import snastro.kernel.AbbonatoSincrono
import snastro.kernel.CampioniAudio
import snastro.kernel.ElaborazioneId
import snastro.kernel.ErroreDiProva
import snastro.kernel.Esito
import snastro.kernel.EventoPubblicato
import snastro.kernel.ParlanteId
import snastro.kernel.RegistrazioneId
import snastro.kernel.atteso
import snastro.parlanti.adattatori.persistenza.AttribuzioneRepositorySql
import snastro.parlanti.adattatori.persistenza.ParlanteRepositorySql
import snastro.persistenza.apriDatabaseProgetto
import snastro.progetto.applicazione.comandi.RinominaRegistrazione
import snastro.trascrizione.adattatori.persistenza.ElaborazioneRepositorySql
import snastro.trascrizione.applicazione.comandi.AvviaElaborazione
import snastro.trascrizione.applicazione.eventi.TrascrittoSostituito
import snastro.trascrizione.applicazione.letture.StatoElaborazioneVista
import snastro.trascrizione.applicazione.porte.Diarizzatore
import snastro.trascrizione.applicazione.porte.FaseElaborazione
import snastro.trascrizione.applicazione.porte.Turno
import snastro.trascrizione.dominio.NumeroPersone
import snastro.trascrizione.dominio.StatoElaborazione
import snastro.trascrizione.dominio.unaElaborazione
import snastro.ui.Cambiamento
import snastro.ui.registrazione.ComandoVoce
import snastro.ui.registrazione.RegistrazionePresenter
import snastro.ui.registrazione.RegistrazioneUiStato
import snastro.ui.registrazioni.IdentificazioneRiga
import snastro.ui.registrazioni.RegistrazioniPresenter
import snastro.ui.registrazioni.RegistrazioniUiStato
import snastro.ui.registrazioni.RigaRegistrazione
import snastro.ui.registrazioni.StatoElaborazioneRiga
import snastro.ui.testi.messaggioRitrascrizioneNonRiuscita
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * ADR 0018 + Amendment (b) end-to-end on the REAL R2 composition ([AmbienteR2]: SQLite project folder,
 * real queue/pipeline, real Parlanti SQL repositories and subscribers; Finte ML): AC-456, AC-457, AC-458,
 * AC-459, AC-479, and the purge running synchronously inside the completion transaction (ADR 0018 §2/§3).
 *
 * AC-458's setup ([prepara]): Registrazione X completata, Voce 1 → ricorrente 'Mario' (print), Mario also
 * attributed in Registrazione Y (print); Voce 2 → 'salta', an occasionale 'Ospite del …' (print) nowhere else.
 */
class RitrascriviR2Test {
    @TempDir
    lateinit var radice: Path

    private val diarizzatore = DiarizzatoreScriptato()

    @Test
    fun `AC-458 Ritrascrivi sostituisce il Trascritto, purga Attribuzioni e impronte e rigenera il Documento`() {
        AmbienteR2(radice, diarizzatore).use {
            val s = prepara(it)
            val s2 = presenterS2(it)
            val s3 = presenterS3(it, s.x)
            attendiFinche(messaggio = "S3 modificabile") { datiS3(s3)?.soloLettura == false }
            val documentoPrima = documento(it, s.x)
            val barriera = CountDownLatch(1)
            diarizzatore.turni = AmbienteR2.TRE_VOCI // ignores k: the re-run finds 3 Voci anyway
            diarizzatore.barriera = barriera

            ritrascrivi(s2, s.x, persone = "2")

            attendiFinche(messaggio = "S2 'Ritrascrizione in corso'") {
                val stato = riga(s2, s.x)?.elaborazione
                stato is StatoElaborazioneRiga.InCorso && stato.ritrascrizione
            }
            attendiFinche(messaggio = "S3 in sola lettura sul vecchio Trascritto") { datiS3(s3)?.soloLettura == true }
            assertEquals(2, datiS3(s3)?.segmenti?.size, "il vecchio Trascritto resta visibile")
            assertEquals(2, it.r2.r1.trascritto(s.x)?.voci?.size)
            assertEquals(2, AttribuzioneRepositorySql(it.contesto.database).diRegistrazione(s.x).size)

            barriera.countDown()

            attendiFinche(messaggio = "S2 'Completata' con il badge '3 voci · 3 da identificare'") {
                val r = riga(s2, s.x)
                r?.elaborazione == StatoElaborazioneRiga.Completata && r.identificazione == IdentificazioneRiga(3, 3)
            }
            assertEquals(NumeroPersone.di(2).atteso(), diarizzatore.numeroPersoneRicevuti.last())
            val parlanti = ParlanteRepositorySql(it.contesto.database)
            val attribuzioni = AttribuzioneRepositorySql(it.contesto.database)
            assertEquals(emptyList(), attribuzioni.diRegistrazione(s.x))
            assertEquals(emptyList(), parlanti.impronteDiRegistrazione(s.x))
            val galleria = it.r2.letture.parlantiDelProgetto()
            assertEquals(listOf("Mario"), galleria.map { p -> p.nome }, "l'Ospite non esiste piu', Mario resta")
            assertEquals(s.mario, attribuzioni.diRegistrazione(s.y).single().parlanteId)
            assertEquals(1, parlanti.impronteDiRegistrazione(s.y).size, "la sua impronta altrove resta")
            assertEquals(3, it.r2.r1.trascritto(s.x)?.voci?.size)
            attendiFinche(messaggio = "Documento riscritto con Voce 1..3") {
                documento(it, s.x)?.contains("**Voce 3**") == true
            }
            val nuovo = documento(it, s.x).orEmpty()
            assertFalse("Mario" in nuovo || "Ospite" in nuovo, "solo etichette 'Voce n': $nuovo")
            assertTrue((1..3).all { n -> "**Voce $n**" in nuovo })
            assertNotEquals(documentoPrima, nuovo)
            attendiFinche(messaggio = "S3 ricaricato sulla nuova generazione, modificabile") {
                datiS3(s3)?.let { d -> !d.soloLettura && d.segmenti.size == 3 } == true
            }
        }
    }

    @Test
    fun `AC-459 una Ritrascrizione fallita lascia intatti Attribuzioni, impronte, Trascritto, Documento e Galleria`() {
        AmbienteR2(radice, diarizzatore).use {
            val s = prepara(it)
            val s2 = presenterS2(it)
            val prima = Istantanea.di(it, s.x)
            diarizzatore.fallisci = true

            ritrascrivi(s2, s.x, persone = "2")

            attendiFinche(messaggio = "S2 'Ritrascrizione non riuscita'") {
                riga(s2, s.x)?.ritrascrizioneFallita != null
            }
            val r = checkNotNull(riga(s2, s.x))
            assertEquals(StatoElaborazioneRiga.Completata, r.elaborazione)
            assertEquals(
                "Ritrascrizione non riuscita: errore nella separazione delle voci",
                messaggioRitrascrizioneNonRiuscita(checkNotNull(r.ritrascrizioneFallita)),
            )
            Thread.sleep(ATTESA_NESSUN_EFFETTO_MS) // nothing after commit may touch the Documento either
            Istantanea.di(it, s.x).confronta(prima)
        }
    }

    @Test
    fun `AC-479 annullare una Ritrascrizione in coda lascia tutto intatto e S3 torna modificabile`() {
        AmbienteR2(radice, diarizzatore).use {
            val s = prepara(it)
            val z = it.importa() // holds the queue: X's re-run stays in_attesa behind it
            it.collaboratori.rinominaRegistrazione(RinominaRegistrazione(z, "Terza riunione")).atteso()
            val pubblicati = CopyOnWriteArrayList<EventoPubblicato>()
            it.contesto.dispatcher.registraDopoCommit { e -> pubblicati += e }
            val s2 = presenterS2(it)
            val prima = Istantanea.di(it, s.x)
            val barriera = CountDownLatch(1)
            diarizzatore.barriera = barriera
            it.r2.r1.avviaElaborazione(AvviaElaborazione(z)).atteso()
            attendiFinche(messaggio = "Z in corso") {
                it.r2.r1.statiElaborazione(listOf(z)).single().fase == FaseElaborazione.DIARIZZAZIONE
            }

            ritrascrivi(s2, s.x, persone = "")
            attendiFinche(messaggio = "S2 'Ritrascrizione in coda (1)' annullabile") {
                val r = riga(s2, s.x)
                r?.elaborazione == StatoElaborazioneRiga.InAttesa(1, ritrascrizione = true) && r.annullabile
            }
            // The row opens S3 (built on navigation, as in ContenutoAppR2): read-only on the old transcript.
            val s3 = presenterS3(it, s.x)
            attendiFinche(messaggio = "S3 in sola lettura") { datiS3(s3)?.soloLettura == true }
            assertEquals(2, datiS3(s3)?.segmenti?.size)

            s2.annullaElaborazione(s.x)

            attendiFinche(messaggio = "S2 'Completata' + 'Ritrascrivi', S3 modificabile") {
                val r = riga(s2, s.x)
                r?.elaborazione == StatoElaborazioneRiga.Completata && r.ritrascriviDisponibile &&
                    !r.operazioneInCorso && datiS3(s3)?.soloLettura == false
            }
            Istantanea.di(it, s.x).confronta(prima)
            barriera.countDown()
            attendiFinche(messaggio = "Z completata") {
                it.r2.r1.statiElaborazione(listOf(z)).single().stato == StatoElaborazioneVista.COMPLETATA
            }
            assertEquals(emptyList(), pubblicati.filterIsInstance<TrascrittoSostituito>())
            assertEquals(3, diarizzatore.numeroPersoneRicevuti.size, "la Ritrascrizione annullata non gira mai")
        }
    }

    @Test
    fun `ADR 0018 la purga gira in modo sincrono dentro la transazione di completamento e ne segue il rollback`() {
        AmbienteR2(radice, diarizzatore).use {
            val s = prepara(it)
            val attribuzioni = AttribuzioneRepositorySql(it.contesto.database)
            var vistaNellaTransazione: Int? = null
            // Registered AFTER the composition's purge policy: it runs in the same transaction, then dooms it.
            it.contesto.dispatcher.registraSincrono(
                AbbonatoSincrono { evento: EventoPubblicato ->
                    if (evento is TrascrittoSostituito) {
                        vistaNellaTransazione = attribuzioni.diRegistrazione(s.x).size
                        Esito.Errore(ErroreDiProva.Fallito("sonda"))
                    } else {
                        Esito.Ok(Unit)
                    }
                },
            )
            diarizzatore.turni = AmbienteR2.TRE_VOCI

            it.r2.r1.avviaElaborazione(AvviaElaborazione(s.x)).atteso()

            attendiFinche(messaggio = "completamento rifiutato e compensato") {
                it.r2.r1.statiElaborazione(listOf(s.x)).single().stato == StatoElaborazioneVista.FALLITA
            }
            assertEquals(0, vistaNellaTransazione, "la purga e' gia' avvenuta, dentro la transazione")
            assertEquals(
                "salvataggio del risultato non riuscito",
                it.r2.r1.statiElaborazione(listOf(s.x)).single().motivoFallimento,
            )
            assertEquals(2, attribuzioni.diRegistrazione(s.x).size, "il rollback annulla anche la purga")
            assertEquals(2, ParlanteRepositorySql(it.contesto.database).impronteDiRegistrazione(s.x).size)
            assertEquals(2, it.r2.r1.trascritto(s.x)?.voci?.size)
        }
    }

    @Test
    fun `AC-456 TrascrittoSostituito dopo il commit invalida le Proposte e da un Cambiamento null, mai su rollback`() {
        val estrattore = EstrattoreConMutex()
        AmbienteR2(radice, estrattore = estrattore).use {
            val x = it.importa()
            val y = it.importa()
            it.collaboratori.rinominaRegistrazione(RinominaRegistrazione(y, "Altra riunione")).atteso()
            it.trascrivi(x)
            it.trascrivi(y)
            runBlocking { it.r2.comandi.esegui(ComandoVoce.Nuovo(voce(y, 1), "Anna")) }
            attendiFinche(messaggio = "ParlanteCreato consegnato") { documento(it, y)?.contains("**Anna**") == true }
            val cambiamenti = raccogli(it)
            it.r2.letture.proposta(voce(x, 2))
            it.r2.letture.proposta(voce(x, 2))
            val calcolate = estrattore.chiamate.get()
            val dispatcher = it.contesto.dispatcher

            dispatcher.unitaDiLavoro.inTransazione {
                dispatcher.pubblica(TrascrittoSostituito(x))
                Esito.Errore(ErroreDiProva.Fallito("rollback"))
            }
            Thread.sleep(ATTESA_NESSUN_EFFETTO_MS)
            assertFalse(Cambiamento(null) in cambiamenti, "mai consegnato su rollback")
            it.r2.letture.proposta(voce(x, 2))
            assertEquals(calcolate, estrattore.chiamate.get(), "la Proposta resta in cache dopo un rollback")

            dispatcher.unitaDiLavoro.inTransazione { Esito.Ok(dispatcher.pubblica(TrascrittoSostituito(x))) }.atteso()

            attendiFinche(messaggio = "Cambiamento(null)") { Cambiamento(null) in cambiamenti }
            it.r2.letture.proposta(voce(x, 2))
            assertEquals(calcolate + 1, estrattore.chiamate.get(), "la Proposta e' ricalcolata dopo l'evento")
        }
    }

    @Test
    fun `AC-457 la purga e registrata prima che la coda esegua il primo comando`() {
        // A project closed with a re-run of X already queued: the queue runs it right at the next opening.
        val primo = AmbienteR2(radice)
        val x = primo.importa()
        primo.trascrivi(x)
        runBlocking { primo.r2.comandi.esegui(ComandoVoce.Nuovo(voce(x, 1), "Anna")) }
        primo.close()
        val percorso = primo.progetto.percorso
        val db = apriDatabaseProgetto(Path.of(percorso).toFile())
        val rerun = ElaborazioneId("e-ritrascrizione")
        ElaborazioneRepositorySql(db.database)
            .salva(unaElaborazione(StatoElaborazione.IN_ATTESA, id = rerun, registrazioneId = x))
            .atteso()
        db.chiudi()

        AmbienteR2(radice.resolve("bis").also(Files::createDirectories)).use {
            it.rendiLeggibile(x)
            it.sessione.chiudi()
            it.sessione.apri(percorso).atteso()

            attendiFinche(messaggio = "ritrascrizione in coda eseguita all'apertura") {
                ElaborazioneRepositorySql(it.contesto.database).trova(rerun)?.completata == true
            }
            // Same Voce numbers in the new generation: without the purge Anna would silently re-attach.
            assertEquals(emptyList(), AttribuzioneRepositorySql(it.contesto.database).diRegistrazione(x))
            assertEquals(emptyList(), ParlanteRepositorySql(it.contesto.database).impronteDiRegistrazione(x))
            assertEquals(listOf("Anna"), it.r2.letture.parlantiDelProgetto().map { p -> p.nome }, "ricorrente: resta")
        }
    }

    /** Ids of AC-458's setup. */
    private class Scenario(val x: RegistrazioneId, val y: RegistrazioneId, val mario: ParlanteId)

    private fun prepara(ambiente: AmbienteR2): Scenario {
        val x = ambiente.importa()
        val y = ambiente.importa()
        // Distinct titles: the Documento file name is date + titolo (ADR 0010).
        ambiente.collaboratori.rinominaRegistrazione(RinominaRegistrazione(y, "Altra riunione")).atteso()
        ambiente.trascrivi(x)
        ambiente.trascrivi(y)
        assertEquals(Esito.Ok(Unit), comando(ambiente, ComandoVoce.Nuovo(voce(x, 1), "Mario")))
        assertEquals(Esito.Ok(Unit), comando(ambiente, ComandoVoce.Salta(voce(x, 2))))
        val mario = ambiente.r2.letture.parlantiDelProgetto().single { p -> p.nome == "Mario" }.parlanteId
        assertEquals(Esito.Ok(Unit), comando(ambiente, ComandoVoce.Conferma(voce(y, 1), mario)))
        assertEquals(2, ParlanteRepositorySql(ambiente.contesto.database).impronteDiRegistrazione(x).size)
        assertEquals(2, ambiente.r2.letture.parlantiDelProgetto().size)
        attendiFinche(messaggio = "Documento di X con i Nomi") { documento(ambiente, x)?.contains("**Mario**") == true }
        return Scenario(x, y, mario)
    }

    private fun comando(ambiente: AmbienteR2, c: ComandoVoce): Esito<Unit>? =
        runBlocking { ambiente.r2.comandi.esegui(c) }

    /** S2 'Ritrascrivi': the field, the button, then the confirmation (AC-449). */
    private fun ritrascrivi(s2: RegistrazioniPresenter, id: RegistrazioneId, persone: String) {
        attendiFinche(messaggio = "'Ritrascrivi' offerto") {
            riga(s2, id)?.let { r -> r.ritrascriviDisponibile && !r.operazioneInCorso } == true
        }
        s2.modificaNumeroPersone(id, persone)
        s2.ritrascrivi(id)
        attendiFinche(messaggio = "conferma di 'Ritrascrivi'") { riga(s2, id)?.confermaRitrascrivi == true }
        s2.confermaRitrascrivi(id)
    }

    private fun presenterS2(ambiente: AmbienteR2): RegistrazioniPresenter =
        costruisciRegistrazioniPresenterR2(ambiente.grafoR0, ambiente.collaboratori, ambiente.r2) {}

    private fun presenterS3(ambiente: AmbienteR2, id: RegistrazioneId): RegistrazionePresenter =
        costruisciRegistrazionePresenterR2(
            ambiente.grafo,
            ambiente.collaboratori,
            ambiente.r2,
            id,
            ambiente.r2.scopeSchermata(ambiente.collaboratori.scope),
        )

    private fun riga(s2: RegistrazioniPresenter, id: RegistrazioneId): RigaRegistrazione? =
        (s2.stato.value as? RegistrazioniUiStato.Dati)?.righe?.find { it.registrazioneId == id }

    private fun datiS3(s3: RegistrazionePresenter): RegistrazioneUiStato.Dati? =
        s3.stato.value as? RegistrazioneUiStato.Dati

    /** Collects the project's Cambiamenti from now on (the flows' replayed past ones dropped). */
    private fun raccogli(ambiente: AmbienteR2): MutableList<Cambiamento> {
        val cambiamenti = CopyOnWriteArrayList<Cambiamento>()
        ambiente.scope.launch { ambiente.collaboratori.aggiornamentiVista.cambiamenti.collect(cambiamenti::add) }
        Thread.sleep(ATTESA_REPLAY_MS)
        cambiamenti.clear()
        return cambiamenti
    }

    /** What AC-459/AC-479 require unchanged: X's Parlanti rows, Trascritto and Documento bytes, and the Galleria. */
    private class Istantanea(
        val attribuzioni: List<Any>,
        val impronte: List<Any>,
        val trascritto: Any?,
        val documento: ByteArray?,
        val galleria: List<Any>,
    ) {
        fun confronta(prima: Istantanea) {
            assertEquals(prima.attribuzioni, attribuzioni)
            assertEquals(prima.impronte, impronte)
            assertEquals(prima.trascritto, trascritto)
            assertContentEquals(prima.documento, documento)
            assertEquals(prima.galleria, galleria)
        }

        companion object {
            fun di(ambiente: AmbienteR2, id: RegistrazioneId): Istantanea = Istantanea(
                attribuzioni = AttribuzioneRepositorySql(ambiente.contesto.database).diRegistrazione(id)
                    .map { a -> a.voceRef to a.parlanteId },
                impronte = ParlanteRepositorySql(ambiente.contesto.database).impronteDiRegistrazione(id),
                trascritto = ambiente.r2.r1.trascritto(id),
                documento = ambiente.r2.r1.percorsoDocumento(id)?.let { p -> Path.of(p).readBytes() },
                galleria = ambiente.r2.letture.parlantiDelProgetto(),
            )
        }
    }

    /**
     * A scripted [Diarizzatore] whose [turni] ignore the Numero di persone (it only records it): a re-run
     * can find more Voci than asked (AC-458). [barriera] holds a run in DIARIZZAZIONE; [fallisci] makes it
     * throw (→ "errore nella separazione delle voci").
     */
    private class DiarizzatoreScriptato : Diarizzatore {
        @Volatile var turni: List<Turno> = AmbienteR2.DUE_VOCI

        @Volatile var barriera: CountDownLatch? = null

        @Volatile var fallisci: Boolean = false

        val numeroPersoneRicevuti: MutableList<NumeroPersone?> = CopyOnWriteArrayList()

        override fun diarizza(c: CampioniAudio, numeroPersone: NumeroPersone?): List<Turno> {
            numeroPersoneRicevuti += numeroPersone
            barriera?.await()
            check(!fallisci) { "diarizzazione fallita (finta)" }
            return turni
        }
    }

    private companion object {
        const val ATTESA_NESSUN_EFFETTO_MS = 500L
        const val ATTESA_REPLAY_MS = 200L

        fun documento(ambiente: AmbienteR2, id: RegistrazioneId): String? =
            ambiente.r2.r1.percorsoDocumento(id)?.let { p -> Path.of(p).readText() }
    }
}
