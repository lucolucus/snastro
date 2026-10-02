package snastro.avvio.trascrizione

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.io.TempDir
import snastro.avvio.costruisciRegistrazionePresenter
import snastro.avvio.costruisciRegistrazioniPresenter
import snastro.avvio.progetto.AmbienteProgetto
import snastro.avvio.progetto.EstrattoreConMutex
import snastro.avvio.progetto.voce
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
import snastro.supporto.test.attendiFinche
import snastro.supporto.test.restaVeroPer
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
import snastro.ui.registrazione.SelezioneSchedaS3
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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * ADR 0018 + Amendment (b) end-to-end on the REAL single composition ([AmbienteProgetto]: SQLite project folder,
 * real queue/pipeline, real Parlanti SQL repositories and subscribers; Finte ML): AC-456, AC-457, AC-458,
 * AC-459, AC-479, and the purge running synchronously inside the completion transaction (ADR 0018 §2/§3).
 *
 * AC-458's setup ([prepara]): Registrazione X completata, Voce 1 → ricorrente 'Mario' (print), Mario also
 * attributed in Registrazione Y (print); Voce 2 → 'salta', an occasionale 'Ospite del …' (print) nowhere else.
 */
class RitrascriviTest {
    @TempDir
    lateinit var radice: Path

    private val diarizzatore = DiarizzatoreScriptato()

    @Test
    fun `AC-458 Ritrascrivi sostituisce il Trascritto, purga Attribuzioni e impronte e rigenera la Sbobinatura`() {
        AmbienteProgetto(radice, diarizzatore).use {
            val s = prepara(it)
            val s2 = presenterS2(it)
            val s3 = presenterS3(it, s.x)
            attendiFinche(timeout = 10.seconds, messaggio = "S3 modificabile") { datiS3(s3)?.soloLettura == false }
            val sbobinaturaPrima = sbobinatura(it, s.x)
            val barriera = CountDownLatch(1)
            diarizzatore.turni = AmbienteProgetto.TRE_VOCI // ignores k: the re-run finds 3 Voci anyway
            diarizzatore.barriera = barriera

            ritrascrivi(it, s2, s.x, persone = "2")

            attendiFinche(timeout = 10.seconds, messaggio = "S2 'Ritrascrizione in corso'") {
                val stato = riga(s2, s.x)?.elaborazione
                stato is StatoElaborazioneRiga.InCorso && stato.ritrascrizione
            }
            attendiFinche(timeout = 10.seconds, messaggio = "S3 in sola lettura sul vecchio Trascritto") {
                datiS3(s3)?.soloLettura == true
            }
            assertEquals(2, datiS3(s3)?.segmenti?.size, "il vecchio Trascritto resta visibile")
            assertEquals(2, it.trascrizione.trascritto(s.x)?.voci?.size)
            assertEquals(2, AttribuzioneRepositorySql(it.porte.database).diIncontro(it.incontroDi(s.x)).size)

            barriera.countDown()

            attendiFinche(
                timeout = 10.seconds,
                messaggio = "S2 'Completata' con il badge '3 voci · 3 da identificare'",
            ) {
                val r = riga(s2, s.x)
                r?.elaborazione == StatoElaborazioneRiga.Completata && r.identificazione == IdentificazioneRiga(3, 3)
            }
            assertEquals(NumeroPersone.di(2).atteso(), diarizzatore.numeroPersoneRicevuti.last())
            val parlanti = ParlanteRepositorySql(it.porte.database, it.porte.lettura)
            val attribuzioni = AttribuzioneRepositorySql(it.porte.database)
            assertEquals(emptyList(), attribuzioni.diIncontro(it.incontroDi(s.x)))
            assertEquals(emptyList(), parlanti.impronteDiRegistrazione(s.x))
            val galleria = it.parlanti.letture.parlantiDelProgetto()
            assertEquals(listOf("Mario"), galleria.map { p -> p.nome }, "l'Ospite non esiste piu', Mario resta")
            assertEquals(s.mario, attribuzioni.diIncontro(it.incontroDi(s.y)).single().parlanteId)
            assertEquals(1, parlanti.impronteDiRegistrazione(s.y).size, "la sua impronta altrove resta")
            assertEquals(3, it.trascrizione.trascritto(s.x)?.voci?.size)
            // INV-I4 (ADR 0035 §2, D-0007): the new Voci take the Incontro counter, after Voce 1-2, never reused.
            attendiFinche(timeout = 10.seconds, messaggio = "Sbobinatura riscritta con Voce 3..5") {
                sbobinatura(it, s.x)?.contains("**Voce 5**") == true
            }
            val nuovo = sbobinatura(it, s.x).orEmpty()
            assertFalse("Mario" in nuovo || "Ospite" in nuovo, "solo etichette 'Voce n': $nuovo")
            assertTrue((3..5).all { n -> "**Voce $n**" in nuovo })
            assertFalse((1..2).any { n -> "**Voce $n**" in nuovo }, "nessun numero riusato: $nuovo")
            assertNotEquals(sbobinaturaPrima, nuovo)
            attendiFinche(timeout = 10.seconds, messaggio = "S3 ricaricato sulla nuova generazione, modificabile") {
                datiS3(s3)?.let { d -> !d.soloLettura && d.segmenti.size == 3 } == true
            }
        }
    }

    @Test
    fun `AC-459 una Ritrascrizione fallita non tocca Attribuzioni, impronte, Trascritto, Sbobinatura e Galleria`() {
        AmbienteProgetto(radice, diarizzatore).use {
            val s = prepara(it)
            val s2 = presenterS2(it)
            val prima = Istantanea.di(it, s.x)
            diarizzatore.fallisci = true

            ritrascrivi(it, s2, s.x, persone = "2")

            attendiFinche(timeout = 10.seconds, messaggio = "S2 'Ritrascrizione non riuscita'") {
                riga(s2, s.x)?.ritrascrizioneFallita != null
            }
            val r = checkNotNull(riga(s2, s.x))
            assertEquals(StatoElaborazioneRiga.Completata, r.elaborazione)
            assertEquals(
                "Ritrascrizione non riuscita: errore nella separazione delle voci",
                messaggioRitrascrizioneNonRiuscita(checkNotNull(r.ritrascrizioneFallita)),
            )
            assicuraInvariata(it, s.x, prima) // nothing after commit may touch the Sbobinatura either
        }
    }

    @Test
    fun `AC-479 annullare una Ritrascrizione in coda lascia tutto intatto e S3 torna modificabile`() {
        AmbienteProgetto(radice, diarizzatore).use {
            val s = prepara(it)
            val z = it.importa() // holds the queue: X's re-run stays in_attesa behind it
            it.collaboratori.rinominaRegistrazione(RinominaRegistrazione(z, "Terza riunione")).atteso()
            val pubblicati = CopyOnWriteArrayList<EventoPubblicato>()
            it.porte.dispatcher.registraDopoCommit { e -> pubblicati += e }
            val s2 = presenterS2(it)
            val prima = Istantanea.di(it, s.x)
            val barriera = CountDownLatch(1)
            diarizzatore.barriera = barriera
            it.collaboratori.avviaElaborazione(AvviaElaborazione(z)).atteso()
            attendiFinche(timeout = 10.seconds, messaggio = "Z in corso") {
                it.vistaDi(z).fase == FaseElaborazione.DIARIZZAZIONE
            }

            ritrascrivi(it, s2, s.x, persone = "")
            // `!operazioneInCorso` too: the row can already show the queued re-run (a Cambiamento-driven reload)
            // while the confirmation's own command is still completing — 'Annulla' is then a no-op by design (M3).
            attendiFinche(timeout = 10.seconds, messaggio = "S2 'Ritrascrizione in coda (1)' annullabile") {
                val r = riga(s2, s.x)
                r?.elaborazione == StatoElaborazioneRiga.InAttesa(1, ritrascrizione = true) && r.annullabile &&
                    !r.operazioneInCorso
            }
            // The row opens S3 (built on navigation, as in ContenutoApp): read-only on the old transcript.
            val s3 = presenterS3(it, s.x)
            attendiFinche(timeout = 10.seconds, messaggio = "S3 in sola lettura") { datiS3(s3)?.soloLettura == true }
            assertEquals(2, datiS3(s3)?.segmenti?.size)

            sulThreadUi(it) { s2.annullaElaborazione(s.x) }

            attendiFinche(timeout = 10.seconds, messaggio = "S2 'Completata' + 'Ritrascrivi', S3 modificabile") {
                val r = riga(s2, s.x)
                r?.elaborazione == StatoElaborazioneRiga.Completata && r.ritrascriviDisponibile &&
                    !r.operazioneInCorso && datiS3(s3)?.soloLettura == false
            }
            Istantanea.di(it, s.x).confronta(prima)
            barriera.countDown()
            attendiFinche(timeout = 10.seconds, messaggio = "Z completata") {
                it.stato(z) == StatoElaborazioneVista.COMPLETATA
            }
            assertEquals(emptyList(), pubblicati.filterIsInstance<TrascrittoSostituito>())
            assertEquals(3, diarizzatore.numeroPersoneRicevuti.size, "la Ritrascrizione annullata non gira mai")
        }
    }

    @Test
    fun `ADR 0018 la purga gira in modo sincrono dentro la transazione di completamento e ne segue il rollback`() {
        AmbienteProgetto(radice, diarizzatore).use {
            val s = prepara(it)
            val attribuzioni = AttribuzioneRepositorySql(it.porte.database)
            var vistaNellaTransazione: Int? = null
            // Registered AFTER the composition's purge policy: it runs in the same transaction, then dooms it.
            it.porte.dispatcher.registraSincrono(
                AbbonatoSincrono { evento: EventoPubblicato ->
                    if (evento is TrascrittoSostituito) {
                        vistaNellaTransazione = attribuzioni.diIncontro(it.incontroDi(s.x)).size
                        Esito.Errore(ErroreDiProva.Fallito("sonda"))
                    } else {
                        Esito.Ok(Unit)
                    }
                },
            )
            diarizzatore.turni = AmbienteProgetto.TRE_VOCI

            it.collaboratori.avviaElaborazione(AvviaElaborazione(s.x)).atteso()

            attendiFinche(timeout = 10.seconds, messaggio = "completamento rifiutato e compensato") {
                it.trascrizione.statiElaborazione(listOf(s.x)).single().stato == StatoElaborazioneVista.FALLITA
            }
            assertEquals(0, vistaNellaTransazione, "la purga e' gia' avvenuta, dentro la transazione")
            assertEquals(
                "salvataggio del risultato non riuscito",
                it.trascrizione.statiElaborazione(listOf(s.x)).single().motivoFallimento,
            )
            assertEquals(2, attribuzioni.diIncontro(it.incontroDi(s.x)).size, "il rollback annulla anche la purga")
            val parlanti = ParlanteRepositorySql(it.porte.database, it.porte.lettura)
            assertEquals(2, parlanti.impronteDiRegistrazione(s.x).size)
            assertEquals(2, it.trascrizione.trascritto(s.x)?.voci?.size)
        }
    }

    @Test
    fun `AC-456 TrascrittoSostituito dopo il commit invalida le Proposte e da un Cambiamento null, mai su rollback`() {
        val estrattore = EstrattoreConMutex()
        AmbienteProgetto(radice, estrattore = estrattore).use {
            val x = it.importa()
            val y = it.importa()
            it.collaboratori.rinominaRegistrazione(RinominaRegistrazione(y, "Altra riunione")).atteso()
            it.trascrivi(x)
            it.trascrivi(y)
            runBlocking { it.parlanti.comandi.esegui(ComandoVoce.Nuovo(voce(y, 1), "Anna")) }
            attendiFinche(timeout = 10.seconds, messaggio = "ParlanteCreato consegnato") {
                sbobinatura(it, y)?.contains("**Anna**") == true
            }
            val cambiamenti = it.raccogliCambiamenti()
            it.parlanti.letture.proposta(voce(x, 2))
            it.parlanti.letture.proposta(voce(x, 2))
            val calcolate = estrattore.chiamate.get()
            val dispatcher = it.porte.dispatcher

            dispatcher.unitaDiLavoro.inTransazione {
                dispatcher.pubblica(TrascrittoSostituito(x, it.incontroDi(x), emptySet()))
                Esito.Errore(ErroreDiProva.Fallito("rollback"))
            }
            restaVeroPer(ATTESA_NESSUN_EFFETTO_MS.milliseconds, messaggio = "mai consegnato su rollback") {
                Cambiamento(null) !in cambiamenti
            }
            it.parlanti.letture.proposta(voce(x, 2))
            assertEquals(calcolate, estrattore.chiamate.get(), "la Proposta resta in cache dopo un rollback")

            dispatcher.unitaDiLavoro.inTransazione {
                Esito.Ok(dispatcher.pubblica(TrascrittoSostituito(x, it.incontroDi(x), emptySet())))
            }.atteso()

            attendiFinche(timeout = 10.seconds, messaggio = "Cambiamento(null)") { Cambiamento(null) in cambiamenti }
            it.parlanti.letture.proposta(voce(x, 2))
            assertEquals(calcolate + 1, estrattore.chiamate.get(), "la Proposta e' ricalcolata dopo l'evento")
        }
    }

    @Test
    fun `AC-457 la purga e registrata prima che la coda esegua il primo comando`() {
        // A project closed with a re-run of X already queued: the queue runs it right at the next opening.
        val primo = AmbienteProgetto(radice)
        val x = primo.importa()
        primo.trascrivi(x)
        runBlocking { primo.parlanti.comandi.esegui(ComandoVoce.Nuovo(voce(x, 1), "Anna")) }
        primo.close()
        val percorso = primo.progetto.percorso
        val db = apriDatabaseProgetto(Path.of(percorso).toFile())
        val rerun = ElaborazioneId("e-ritrascrizione")
        ElaborazioneRepositorySql(db.database)
            .salva(unaElaborazione(StatoElaborazione.IN_ATTESA, id = rerun, registrazioneId = x))
            .atteso()
        db.chiudi()

        AmbienteProgetto(radice.resolve("bis").also(Files::createDirectories)).use {
            it.rendiLeggibile(x)
            it.sessione.chiudi()
            it.sessione.apri(percorso).atteso()

            attendiFinche(timeout = 10.seconds, messaggio = "ritrascrizione in coda eseguita all'apertura") {
                ElaborazioneRepositorySql(it.porte.database).trova(rerun)?.completata == true
            }
            // Same Voce numbers in the new generation: without the purge Anna would silently re-attach.
            assertEquals(emptyList(), AttribuzioneRepositorySql(it.porte.database).diIncontro(it.incontroDi(x)))
            val parlanti = ParlanteRepositorySql(it.porte.database, it.porte.lettura)
            assertEquals(emptyList(), parlanti.impronteDiRegistrazione(x))
            val nomi = it.parlanti.letture.parlantiDelProgetto().map { p -> p.nome }
            assertEquals(listOf("Anna"), nomi, "ricorrente: resta")
        }
    }

    /** Ids of AC-458's setup. */
    private class Scenario(val x: RegistrazioneId, val y: RegistrazioneId, val mario: ParlanteId)

    private fun prepara(ambiente: AmbienteProgetto): Scenario {
        val x = ambiente.importa()
        val y = ambiente.importa()
        // Distinct titles: the Sbobinatura file name is date + titolo (ADR 0010).
        ambiente.collaboratori.rinominaRegistrazione(RinominaRegistrazione(y, "Altra riunione")).atteso()
        ambiente.trascrivi(x)
        ambiente.trascrivi(y)
        assertEquals(Esito.Ok(Unit), comando(ambiente, ComandoVoce.Nuovo(voce(x, 1), "Mario")))
        assertEquals(Esito.Ok(Unit), comando(ambiente, ComandoVoce.Salta(voce(x, 2))))
        val mario = ambiente.parlanti.letture.parlantiDelProgetto().single { p -> p.nome == "Mario" }.parlanteId
        assertEquals(Esito.Ok(Unit), comando(ambiente, ComandoVoce.Conferma(voce(y, 1), mario)))
        val parlanti = ParlanteRepositorySql(ambiente.porte.database, ambiente.porte.lettura)
        assertEquals(2, parlanti.impronteDiRegistrazione(x).size)
        assertEquals(2, ambiente.parlanti.letture.parlantiDelProgetto().size)
        // Both Sbobinatura rewrites of X must have landed (Nuovo -> 'Mario', Salta -> 'Ospite del ...'): the
        // after-commit writer is asynchronous, and AC-459/AC-479 take their byte-level baseline right after this.
        attendiFinche(timeout = 10.seconds, messaggio = "Sbobinatura di X con Mario e l'Ospite") {
            sbobinatura(ambiente, x)?.let { d -> "**Mario**" in d && "**Ospite del " in d } == true
        }
        return Scenario(x, y, mario)
    }

    private fun comando(ambiente: AmbienteProgetto, c: ComandoVoce): Esito<Unit>? =
        runBlocking { ambiente.parlanti.comandi.esegui(c) }

    /**
     * S2 'Ritrascrivi': the field, the button, then the confirmation (AC-449) — each user action on the UI
     * thread, as the app does: the presenter's state is confined to it, and an action fired from the test
     * thread could be overwritten by a reload merging on the UI thread at the same moment (the typed Numero
     * di persone lost, the re-run sent with none).
     */
    private fun ritrascrivi(
        ambiente: AmbienteProgetto,
        s2: RegistrazioniPresenter,
        id: RegistrazioneId,
        persone: String,
    ) {
        attendiFinche(timeout = 10.seconds, messaggio = "'Ritrascrivi' offerto") {
            riga(s2, id)?.let { r -> r.ritrascriviDisponibile && !r.operazioneInCorso } == true
        }
        sulThreadUi(ambiente) {
            s2.modificaNumeroPersone(id, persone)
            s2.ritrascrivi(id)
        }
        attendiFinche(timeout = 10.seconds, messaggio = "conferma di 'Ritrascrivi' con '$persone'") {
            riga(s2, id)?.let { r -> r.confermaRitrascrivi && r.numeroPersone == persone } == true
        }
        sulThreadUi(ambiente) { s2.confermaRitrascrivi(id) }
    }

    /** Runs a user action on the presenters' UI thread (the app's `Dispatchers.Swing`), waiting for it to return. */
    private fun sulThreadUi(ambiente: AmbienteProgetto, azione: () -> Unit) =
        runBlocking(ambiente.dispatcherUi) { azione() }

    private fun presenterS2(ambiente: AmbienteProgetto): RegistrazioniPresenter =
        costruisciRegistrazioniPresenter(ambiente.grafo(), ambiente.collaboratori) {}

    private fun presenterS3(ambiente: AmbienteProgetto, id: RegistrazioneId): RegistrazionePresenter =
        costruisciRegistrazionePresenter(
            ambiente.grafo(),
            ambiente.collaboratori,
            id,
            ambiente.parlanti.scopeSchermata(ambiente.collaboratori.scope),
            SelezioneSchedaS3(),
            vaiAllaParte = {},
        )

    private fun riga(s2: RegistrazioniPresenter, id: RegistrazioneId): RigaRegistrazione? =
        (s2.stato.value as? RegistrazioniUiStato.Dati)?.righe?.find { it.registrazioneId == id }

    private fun datiS3(s3: RegistrazionePresenter): RegistrazioneUiStato.Dati? =
        s3.stato.value as? RegistrazioneUiStato.Dati

    /**
     * AC-459 (L713b): a fixed `Thread.sleep` then ONE comparison flaked once under a loaded full
     * run (a straggler after-commit effect can still be settling past the fixed window). Polls the
     * post-commit snapshot repeatedly for the whole [entro] deadline instead, failing as soon as one
     * sample diverges from [prima] — same total wait budget, no weaker assertion, just samples it
     * throughout the window rather than trusting one read at the very end.
     */
    private fun assicuraInvariata(
        ambiente: AmbienteProgetto,
        id: RegistrazioneId,
        prima: Istantanea,
        entro: Long = ATTESA_NESSUN_EFFETTO_MS,
    ) {
        restaVeroPer(entro.milliseconds, messaggio = "lo stato di $id e cambiato") {
            Istantanea.di(ambiente, id).confronta(prima)
            true
        }
    }

    /** What AC-459/AC-479 require unchanged: X's Parlanti rows, Trascritto and Sbobinatura bytes, and the Galleria. */
    private class Istantanea(
        val attribuzioni: List<Any>,
        val impronte: List<Any>,
        val trascritto: Any?,
        val sbobinatura: ByteArray?,
        val galleria: List<Any>,
    ) {
        fun confronta(prima: Istantanea) {
            assertEquals(prima.attribuzioni, attribuzioni)
            assertEquals(prima.impronte, impronte)
            assertEquals(prima.trascritto, trascritto)
            assertContentEquals(prima.sbobinatura, sbobinatura)
            assertEquals(prima.galleria, galleria)
        }

        companion object {
            fun di(ambiente: AmbienteProgetto, id: RegistrazioneId): Istantanea = Istantanea(
                attribuzioni = AttribuzioneRepositorySql(ambiente.porte.database).diIncontro(ambiente.incontroDi(id))
                    .map { a -> a.voceRef to a.parlanteId },
                impronte = ParlanteRepositorySql(ambiente.porte.database, ambiente.porte.lettura)
                    .impronteDiRegistrazione(id),
                trascritto = ambiente.trascrizione.trascritto(id),
                sbobinatura = ambiente.sbobinatura.percorsoSbobinatura(id)?.let { p -> Path.of(p).readBytes() },
                galleria = ambiente.parlanti.letture.parlantiDelProgetto(),
            )
        }
    }

    /**
     * A scripted [Diarizzatore] whose [turni] ignore the Numero di persone (it only records it): a re-run
     * can find more Voci than asked (AC-458). [barriera] holds a run in DIARIZZAZIONE; [fallisci] makes it
     * throw (→ "errore nella separazione delle voci").
     */
    private class DiarizzatoreScriptato : Diarizzatore {
        @Volatile var turni: List<Turno> = AmbienteProgetto.DUE_VOCI

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

        fun sbobinatura(ambiente: AmbienteProgetto, id: RegistrazioneId): String? =
            ambiente.sbobinatura.percorsoSbobinatura(id)?.let { p -> Path.of(p).readText() }
    }
}
