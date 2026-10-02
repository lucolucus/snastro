package snastro.avvio.trascrizione

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import org.junit.jupiter.api.io.TempDir
import snastro.avvio.coda.ElementoInCoda
import snastro.avvio.coda.FonteCoda
import snastro.avvio.coda.RisultatoTentativo
import snastro.avvio.coda.TipoElementoCoda
import snastro.avvio.costruisciRegistrazionePresenter
import snastro.avvio.costruisciRegistrazioniPresenter
import snastro.avvio.progetto.AmbienteProgetto
import snastro.avvio.progetto.PorteProgetto
import snastro.kernel.CampioniAudio
import snastro.kernel.ElaborazioneId
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceId
import snastro.kernel.atteso
import snastro.persistenza.apriDatabaseProgetto
import snastro.progetto.applicazione.porte.RegistrazioneRepository
import snastro.progetto.dominio.Registrazione
import snastro.supporto.test.attendiFinche
import snastro.supporto.test.restaVeroPer
import snastro.trascrizione.adattatori.persistenza.ElaborazioneRepositorySql
import snastro.trascrizione.applicazione.comandi.AvviaElaborazione
import snastro.trascrizione.applicazione.comandi.UnisciVoci
import snastro.trascrizione.applicazione.eventi.TrascrittoSostituito
import snastro.trascrizione.applicazione.letture.StatoElaborazioneVista
import snastro.trascrizione.applicazione.porte.Diarizzatore
import snastro.trascrizione.applicazione.porte.DiarizzatoreFinta
import snastro.trascrizione.applicazione.porte.FaseElaborazione
import snastro.trascrizione.applicazione.porte.Riconoscimento
import snastro.trascrizione.applicazione.porte.RiconoscitoreParlato
import snastro.trascrizione.applicazione.porte.RiconoscitoreParlatoFinta
import snastro.trascrizione.applicazione.porte.Turno
import snastro.trascrizione.dominio.NumeroPersone
import snastro.trascrizione.dominio.StatoElaborazione
import snastro.trascrizione.dominio.unaElaborazione
import snastro.ui.registrazione.RegistrazioneUiStato
import snastro.ui.registrazione.SelezioneSchedaS3
import snastro.ui.registrazioni.RegistrazioniUiStato
import snastro.ui.registrazioni.RigaRegistrazione
import snastro.ui.registrazioni.StatoElaborazioneRiga
import snastro.ui.testi.etichetta
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * End-to-end ACs of the Trascrizione module on the single composition, on [AmbienteProgetto] (real
 * SessioneProgettoImpl + apriProgetto over a real project folder; only FFmpeg and the ML models are Finte).
 */
class ComposizioneTrascrizioneTest {
    @TempDir
    lateinit var radice: Path

    private val dueVoci = DiarizzatoreFinta(
        turni = listOf(
            Turno(IntervalloMs(0, 1_000), voceIndice = 0),
            Turno(IntervalloMs(2_000, 3_000), voceIndice = 1),
        ),
    )

    @Test
    fun `AC-371 dopo un import nessuna Elaborazione esiste, la vista da NON_AVVIATA e S2 mostra Trascrivi`() {
        // B79 pre-release triage, 2026-09-29: `.use { }` (the file's own idiom elsewhere) instead of a bare
        // `ambiente.close()` mid-body — a failing assertion above used to skip close() and leak the scope/executor.
        val (percorsoProgetto, id) = AmbienteProgetto(radice).use { ambiente ->
            val id = ambiente.importa()
            val presenter = costruisciRegistrazioniPresenter(ambiente.grafo(), ambiente.collaboratori) {}

            assertEquals(StatoElaborazioneVista.NON_AVVIATA, ambiente.stato(id))
            attendiFinche(timeout = 30.seconds, messaggio = "riga NON_AVVIATA in S2") {
                rigaDi(presenter.stato.value, id) == StatoElaborazioneRiga.NonAvviata
            }
            // la coda gira ogni secondo: nulla deve comparire nel frattempo
            restaVeroPer(ATTESA_NESSUN_AVVIO_MS.milliseconds, messaggio = "un'Elaborazione e partita da sola") {
                ambiente.stato(id) == StatoElaborazioneVista.NON_AVVIATA
            }
            ambiente.progetto.percorso to id
        }

        val db = apriDatabaseProgetto(Path.of(percorsoProgetto).toFile())
        val righe = ElaborazioneRepositorySql(db.database).diRegistrazione(id)
        db.chiudi()
        assertTrue(righe.isEmpty(), "l'import non deve creare alcuna Elaborazione (ADR 0014): $righe")
    }

    @Test
    fun `AC-353 AC-354 una fase della pipeline e visibile in S2 senza polling, poi la riga e completata`() {
        val barriera = CountDownLatch(1)
        val ambiente = AmbienteProgetto(radice, DiarizzatoreConBarriera(barriera, DiarizzatoreFinta()))
        ambiente.use {
            val id = it.importa()
            val presenter = costruisciRegistrazioniPresenter(it.grafo(), it.collaboratori) {}

            it.collaboratori.avviaElaborazione(AvviaElaborazione(id)).atteso() // 'Trascrivi'

            attendiFinche(timeout = 30.seconds, messaggio = "fase di diarizzazione in S2") {
                val riga = rigaDi(presenter.stato.value, id)
                riga is StatoElaborazioneRiga.InCorso && riga.faseEtichetta == etichetta(FaseElaborazione.DIARIZZAZIONE)
            }
            assertEquals(FaseElaborazione.DIARIZZAZIONE, it.vistaDi(id).fase)

            barriera.countDown()
            attendiFinche(timeout = 30.seconds, messaggio = "riga completata in S2") {
                rigaDi(presenter.stato.value, id) == StatoElaborazioneRiga.Completata
            }
        }
    }

    @Test
    fun `AC-369 Trascrivi con Numero di persone lo passa al Diarizzatore della pipeline`() {
        val diarizzatore = DiarizzatoreFinta()
        AmbienteProgetto(radice, diarizzatore).use {
            val id = it.importa()

            it.collaboratori.avviaElaborazione(AvviaElaborazione(id, numeroPersone = 3)).atteso()

            attendiFinche(timeout = 30.seconds, messaggio = "elaborazione completata") {
                it.stato(id) == StatoElaborazioneVista.COMPLETATA
            }
            assertEquals(listOf(NumeroPersone.di(3).atteso()), diarizzatore.numeroPersoneRicevuti)
        }
    }

    @Test
    fun `ADR 0004 la memoria dei modelli e rilasciata una volta per ogni Elaborazione terminata`() {
        val riconoscitore = RiconoscitoreChiudibile()
        AmbienteProgetto(radice, riconoscitore = riconoscitore, rilasciaDopoElaborazione = riconoscitore::close).use {
            val id = it.importa()
            it.collaboratori.avviaElaborazione(AvviaElaborazione(id)).atteso()
            attendiFinche(timeout = 30.seconds, messaggio = "elaborazione completata") {
                it.stato(id) == StatoElaborazioneVista.COMPLETATA
            }

            attendiFinche(timeout = 30.seconds, messaggio = "un rilascio per l'Elaborazione terminata") {
                riconoscitore.chiusure == 1
            }
            assertTrue(riconoscitore.chiamate > 0, "il riconoscitore deve essere stato usato prima del rilascio")
        }
    }

    @Test
    fun `AC-356 una Voce senza Parlante nominato e resa Voce n e una Revisione committata rigenera la Sbobinatura`() {
        AmbienteProgetto(radice, dueVoci).use {
            val id = it.importa()
            it.collaboratori.avviaElaborazione(AvviaElaborazione(id)).atteso()
            attendiFinche(timeout = 30.seconds, messaggio = "Sbobinatura con due Voci") {
                sbobinatura(it)?.contains("**Voce 2**") == true
            }
            assertTrue(sbobinatura(it).orEmpty().contains("**Voce 1**"))

            val unione = UnisciVoci(id, sopravvive = VoceId(1), rimossa = VoceId(2))
            it.trascrizione.revisione.unisciVoci.esegui(unione).atteso()

            attendiFinche(timeout = 30.seconds, messaggio = "Sbobinatura rigenerata dopo VociUnite") {
                sbobinatura(it)?.contains("**Voce 2**") == false
            }
            assertEquals(2, Regex("""\*\*Voce 1\*\*""").findAll(sbobinatura(it).orEmpty()).count())
        }
    }

    @Test
    fun `AC-351 S3 in sola lettura mostra il Trascritto completato, le etichette Voce n e dove sta la Sbobinatura`() {
        AmbienteProgetto(radice, dueVoci).use {
            val id = it.importa()
            it.collaboratori.avviaElaborazione(AvviaElaborazione(id)).atteso()
            attendiFinche(timeout = 30.seconds, messaggio = "Sbobinatura scritta") {
                it.sbobinatura.percorsoSbobinatura(id) != null
            }
            val scopeS3 = CoroutineScope(SupervisorJob() + it.dispatcherUi)

            val presenter =
                costruisciRegistrazionePresenter(it.grafo(), it.collaboratori, id, scopeS3, SelezioneSchedaS3(), {})

            attendiFinche(timeout = 30.seconds, messaggio = "S3 caricato") {
                presenter.stato.value is RegistrazioneUiStato.Dati
            }
            val dati = presenter.stato.value as RegistrazioneUiStato.Dati
            assertEquals(listOf("Voce 1", "Voce 2"), dati.segmenti.map { s -> s.etichettaVoce })
            assertNotNull(dati.sbobinaturaPercorso)
            scopeS3.cancel()
        }
    }

    @Test
    fun `AC-478 annullare B in coda lo riporta a Trascrivi, C sale a In coda 1 e la coda non esegue mai B`() {
        val barriera = CountDownLatch(1) // A resta in_corso finche' non la si apre
        AmbienteProgetto(radice, DiarizzatoreConBarriera(barriera, DiarizzatoreFinta())).use {
            val a = it.importa()
            val b = it.importa()
            val c = it.importa()
            val presenter = costruisciRegistrazioniPresenter(it.grafo(), it.collaboratori) {}
            it.collaboratori.avviaElaborazione(AvviaElaborazione(a)).atteso()
            attendiFinche(timeout = 30.seconds, messaggio = "A in corso") {
                it.vistaDi(a).fase == FaseElaborazione.DIARIZZAZIONE
            }
            attendiFinche(timeout = 30.seconds, messaggio = "S2 con A in corso") {
                rigaDi(presenter.stato.value, a) is StatoElaborazioneRiga.InCorso
            }
            presenter.avviaElaborazione(b) // S2 'Trascrivi' (reloads the list: queuing publishes no event)
            attendiFinche(timeout = 30.seconds, messaggio = "B in coda") {
                rigaDi(presenter.stato.value, b) is StatoElaborazioneRiga.InAttesa
            }
            presenter.avviaElaborazione(c)
            // `operazioneInCorso` too: the row turns InAttesa BEFORE the 'Trascrivi' operation's own flag is cleared,
            // and an 'Annulla' sent on a row with an operation in flight is (by design, M3) silently ignored.
            attendiFinche(timeout = 30.seconds, messaggio = "B 'In coda (1)' annullabile, C 'In coda (2)'") {
                val rigaB = rigaCompleta(presenter.stato.value, b)
                rigaB?.elaborazione == StatoElaborazioneRiga.InAttesa(1) && rigaB.annullabile &&
                    !rigaB.operazioneInCorso &&
                    rigaDi(presenter.stato.value, c) == StatoElaborazioneRiga.InAttesa(2)
            }

            presenter.annullaElaborazione(b) // S2 'Annulla'

            attendiFinche(timeout = 30.seconds, messaggio = "B 'Trascrivi' e C 'In coda (1)' in S2") {
                val rigaB = rigaCompleta(presenter.stato.value, b)
                rigaB?.elaborazione == StatoElaborazioneRiga.NonAvviata && !rigaB.operazioneInCorso &&
                    rigaDi(presenter.stato.value, c) == StatoElaborazioneRiga.InAttesa(1)
            }
            assertNull(rigaCompleta(presenter.stato.value, b)?.erroreRiga)
            val vistaB = it.vistaDi(b)
            assertEquals(StatoElaborazioneVista.NON_AVVIATA, vistaB.stato)
            assertNull(vistaB.elaborazioneId, "nessuna riga elaborazione resta per B")

            barriera.countDown()
            attendiFinche(timeout = 30.seconds, messaggio = "la coda esegue C dopo A") {
                it.stato(c) == StatoElaborazioneVista.COMPLETATA
            }
            assertEquals(StatoElaborazioneVista.NON_AVVIATA, it.stato(b))
            assertEquals(listOf(a, c), it.decodificate.toList(), "la pipeline non e mai invocata per B")
        }
    }

    @Test
    fun `AC-460 Ritrascrivi e offerto su una riga completata solo perche la composizione registra la purga sincrona`() {
        AmbienteProgetto(radice).use {
            // ADR 0030 §3 (retargeted): 'Ritrascrivi' is offered only by a composition that registers the synchronous
            // Parlanti purge of TrascrittoSostituito (ADR 0018 §3) — the single composition always does.
            val purghe = it.composto.ordineSincroni.flatMap { m -> m.abbonatiSincroni() }
                .filter { a -> a.evento == TrascrittoSostituito::class }
                .map { a -> a.abbonato::class.simpleName }
            assertTrue("AbbonatoRevisioneParlanti" in purghe, "la purga sincrona dei Parlanti e dichiarata: $purghe")
            val id = it.importa()
            it.collaboratori.avviaElaborazione(AvviaElaborazione(id)).atteso()
            attendiFinche(timeout = 30.seconds, messaggio = "elaborazione completata") {
                it.stato(id) == StatoElaborazioneVista.COMPLETATA
            }
            val presenter = costruisciRegistrazioniPresenter(it.grafo(), it.collaboratori) {}

            attendiFinche(timeout = 30.seconds, messaggio = "riga completata in S2") {
                rigaDi(presenter.stato.value, id) == StatoElaborazioneRiga.Completata
            }
            val riga = checkNotNull(rigaCompleta(presenter.stato.value, id))
            assertTrue(riga.ritrascriviDisponibile, "'Ritrascrivi' offerto: la purga e registrata")
            assertFalse(riga.annullabile)
        }
    }

    @Test
    fun `AC-478 S3 riceve stati e AggiornamentiVista e segue lo stato della sua Registrazione senza polling`() {
        val diarizzatore = DiarizzatoreTrattenibile(dueVoci)
        AmbienteProgetto(radice, diarizzatore).use {
            val id = it.importa()
            it.collaboratori.avviaElaborazione(AvviaElaborazione(id)).atteso()
            attendiFinche(timeout = 30.seconds, messaggio = "elaborazione completata") {
                it.stato(id) == StatoElaborazioneVista.COMPLETATA
            }
            val scopeS3 = CoroutineScope(SupervisorJob() + it.dispatcherUi)
            val presenter =
                costruisciRegistrazionePresenter(it.grafo(), it.collaboratori, id, scopeS3, SelezioneSchedaS3(), {})
            attendiFinche(timeout = 30.seconds, messaggio = "S3 modificabile") {
                (presenter.stato.value as? RegistrazioneUiStato.Dati)?.soloLettura == false
            }

            // A second run over the existing Trascritto ('Ritrascrivi', AC-457): S3 hears of it.
            val barriera = CountDownLatch(1).also { b -> diarizzatore.barriera = b }
            it.collaboratori.avviaElaborazione(AvviaElaborazione(id)).atteso()
            attendiFinche(timeout = 30.seconds, messaggio = "S3 in sola lettura durante la nuova elaborazione") {
                (presenter.stato.value as? RegistrazioneUiStato.Dati)?.soloLettura == true
            }
            barriera.countDown()
            attendiFinche(timeout = 30.seconds, messaggio = "S3 di nuovo modificabile a fine elaborazione") {
                (presenter.stato.value as? RegistrazioneUiStato.Dati)?.soloLettura == false
            }
            scopeS3.cancel()
        }
    }

    @Test
    fun `carry-over 3 il recupero gira prima della coda, un in_corso lasciato da un crash diventa fallita`() {
        val ambiente = AmbienteProgetto(radice)
        val id = ambiente.importa()
        val percorso = ambiente.progetto.percorso
        ambiente.close()
        val db = apriDatabaseProgetto(Path.of(percorso).toFile())
        ElaborazioneRepositorySql(db.database)
            .salva(unaElaborazione(StatoElaborazione.IN_CORSO, id = ElaborazioneId("e-crash"), registrazioneId = id))
            .atteso()
        db.chiudi()

        val riaperto = AmbienteProgetto(radice.resolve("bis").also(Files::createDirectories))
        riaperto.use {
            it.sessione.chiudi()
            it.sessione.apri(percorso).atteso()
            attendiFinche(timeout = 30.seconds, messaggio = "recupero dell'in_corso") {
                it.stato(id) == StatoElaborazioneVista.FALLITA
            }
            assertEquals("interrotta", it.vistaDi(id).motivoFallimento)
        }
    }

    @Test
    fun `carry-over 1 chiudi con la pipeline in corso ferma la coda prima di chiudere il database`() {
        val barriera = CountDownLatch(1) // mai rilasciata: la pipeline resta bloccata finche' chiudi la interrompe
        val ambiente = AmbienteProgetto(radice, DiarizzatoreConBarriera(barriera, DiarizzatoreFinta()))
        val id = ambiente.importa()
        val trascrizione = ambiente.trascrizione
        val coda = ambiente.coda
        val percorso = ambiente.progetto.percorso
        ambiente.collaboratori.avviaElaborazione(AvviaElaborazione(id)).atteso()
        attendiFinche(timeout = 30.seconds, messaggio = "Z in DIARIZZAZIONE") {
            trascrizione.statiElaborazione(listOf(id)).single().fase == FaseElaborazione.DIARIZZAZIONE
        }

        ambiente.close()

        assertTrue(coda.lavoro.isCompleted, "la coda deve essere ferma quando chiudi ritorna")
        assertNull(ambiente.sessione.corrente.value)

        // L624c: chiudi() ferma la coda ma non riscrive la riga (resta `in_corso`, interrotta a meta') —
        // e' la riapertura successiva (RecuperaElaborazioniInterrotte, come carry-over 3) a marcarla FALLITA.
        val riaperto = AmbienteProgetto(radice.resolve("dopo-chiudi").also(Files::createDirectories))
        riaperto.use {
            it.sessione.chiudi()
            it.sessione.apri(percorso).atteso()
            attendiFinche(timeout = 30.seconds, messaggio = "recupero dell'in_corso lasciato da chiudi") {
                it.stato(id) == StatoElaborazioneVista.FALLITA
            }
            assertEquals("interrotta", it.vistaDi(id).motivoFallimento)
        }
    }

    // --- rework cycle 1 (AC-C54, AC-C55, AC-C58): the REAL composition's wiring (apriProgetto), no fake module ----

    @Test
    @Suppress("MaxLineLength", "MaximumLineLength", "ArgumentListWrapping") // the test name alone crosses 120 columns
    fun `AC-C55 un Error che sfugge al lavoro della Sbobinatura dopo commit e segnalato una volta, lo scope del progetto sopravvive`() {
        SpiaSnastro().use { spia ->
            // The fault is injected through the session's only repository seam, and ONLY on the Sbobinatura worker's
            // own
            // stack: the pipeline and every command read the same repository untouched.
            val ambiente = AmbienteProgetto(
                radice,
                costruisciRegistrazioni = { db -> RegistrazioniGuasteNellaSbobinatura(PorteProgetto.registrazioniSql(db)) },
            )
            val id = ambiente.importa()

            // AC-356: dopo un'Elaborazione completata, ElaborazioneCompletata fa girare (dopo commit) il
            // worker della Sbobinatura, che legge la Registrazione — qui un OutOfMemoryError, un Error che
            // RitentaConBackoff non cattura mai (rethrow): sfugge alla coroutine del worker della Sbobinatura.
            ambiente.collaboratori.avviaElaborazione(AvviaElaborazione(id)).atteso()

            attendiFinche(timeout = 30.seconds, messaggio = "l'Error del worker Sbobinatura e' stato segnalato") {
                spia.catturati.any { it.thrown is OutOfMemoryError }
            }
            assertEquals(
                1,
                spia.catturati.count { it.thrown is OutOfMemoryError },
                "AC-C55: segnalato esattamente una volta, mai per ogni retry",
            )
            assertTrue(
                ambiente.collaboratori.scope.isActive,
                "AC-C55: lo scope del progetto sopravvive all'Error del worker Sbobinatura (SupervisorJob)",
            )

            ambiente.close()
        }
    }

    @Test
    fun `AC-C54 un elemento escluso dalla coda condivisa e segnalato con WARNING attraverso la ONE Segnalazione`() {
        SpiaSnastro().use { spia ->
            // Una fonte SEMPRE rifiutata (mai la vera Elaborazione, che resta inerte: nessuna importata qui):
            // dopo MAX_TENTATIVI_PER_ID tentativi il suo id entra nell'esclusione e segnalaBloccato fa il suo
            // (unico) report — attraverso la wiring REALE di apriProgetto, non una CodaCondivisa costruita a mano.
            val fonteGuasta = FonteCoda(
                tipo = TipoElementoCoda.ELABORAZIONE,
                teste = { esclusi ->
                    if ("guasta" !in esclusi) ElementoInCoda("guasta", "reg-guasta", Instant.EPOCH) else null
                },
                prossima = { _, _ -> RisultatoTentativo.Rifiutata("guasta") },
                ultimaTentata = { "guasta" },
                recupera = {},
                trattenuta = { false },
            )
            AmbienteProgetto(radice, fontiCoda = listOf(fonteGuasta)).use {
                val messaggio = "l'elemento guasto e' escluso e segnalato via WARNING"
                attendiFinche(timeout = 30.seconds, messaggio = messaggio) {
                    spia.catturati.any { it.level == Level.WARNING && it.thrown != null }
                }
                val record = spia.catturati.first { it.level == Level.WARNING && it.thrown != null }
                assertEquals(
                    "snastro",
                    record.loggerName,
                    "AC-C54: attraverso la ONE Segnalazione (segnalazioneApp), mai un log.warning locale",
                )
            }
        }
    }

    @Test
    @Suppress("MaxLineLength", "MaximumLineLength", "ArgumentListWrapping") // the test name alone crosses 120 columns
    fun `AC-C58 un interrompi guasto durante lo spegnimento non impedisce di chiudere il database e rilasciare il lock`() {
        val bloccato = CountDownLatch(1)
        val interrotta = CountDownLatch(1)
        val fonteGuasta = FonteCoda(
            // fermaEAttendi interrompe la FONTE in corso (non la prima del suo tipo): questa fonte aggiunta, accanto
            // alle vere Elaborazione e Riassunto dei moduli, e' davvero quella il cui interrompi guasto viene chiamato.
            tipo = TipoElementoCoda.RIASSUNTO,
            teste = { esclusi ->
                if ("guasta" !in esclusi) ElementoInCoda("guasta", "reg-guasta", Instant.EPOCH) else null
            },
            prossima = { _, _ ->
                bloccato.countDown()
                // Like an LLM's native call (ADR 0023 §5, AC-S162), this run IGNORES the interrupt: it ends only once
                // interrompi flips its flag, so it is still "in corso" whenever fermaEAttendi reads it, however late
                // the shutdown thread is scheduled. An interruptible fake ends on the scope's cancel instead (flaky).
                attendiIgnorandoInterruzioni(interrotta)
                RisultatoTentativo.Nessuno
            },
            ultimaTentata = { "guasta" },
            recupera = {},
            trattenuta = { false },
            interrompi = {
                interrotta.countDown() // il flag e' girato, POI il guasto
                error("interrompi guasto") // IllegalStateException
            },
        )
        val spia = SpiaSnastro()
        val ambiente = AmbienteProgetto(radice, fontiCoda = listOf(fonteGuasta))
        val percorso = ambiente.progetto.percorso
        val progettoId = ambiente.progetto.progettoId
        assertTrue(bloccato.await(10, TimeUnit.SECONDS), "prossima deve essere partita e bloccata")

        // sessione.chiudi() direttamente: lo spegnimento di produzione (cancel, poi ArrestoProgetto -> fermaEAttendi).
        spia.use { ambiente.sessione.chiudi() } // non deve lanciare, nonostante l'interrompi guasto (AC-C58)

        assertNull(ambiente.sessione.corrente.value)
        // Discriminating (pre-release L66): the fault is caught and reported by fermaEAttendi ITSELF — never left to
        // the outer shutdown guard, which would report the queue's whole stop as failed.
        assertTrue(
            spia.catturati.any { r ->
                r.message == "elemento della coda condivisa sfuggito" && r.thrown is IllegalStateException
            },
            "l'interrompi guasto e segnalato come sfuggito",
        )
        assertTrue(spia.catturati.none { r -> r.message.orEmpty().startsWith("arresto") }, "l'arresto non fallisce")
        val riaperta = AmbienteProgetto(radice.resolve("bis").also(Files::createDirectories))
        riaperta.use {
            // il database e' chiuso e il lock rilasciato: una riapertura riesce, mai ProgettoGiaAperto.
            val riaperto = it.sessione.apri(percorso).atteso()
            assertEquals(progettoId, riaperto.progettoId)
        }
        ambiente.close() // pulizia dell'esecutore/scope residui di AmbienteProgetto (chiudi() e' idempotente)
    }

    /** AC-C55: the production Registrazione repository; its `trova` throws an [Error] on the Sbobinatura worker only.
     * */
    private class RegistrazioniGuasteNellaSbobinatura(private val delegato: RegistrazioneRepository) :
        RegistrazioneRepository by delegato {
        override fun trova(id: RegistrazioneId): Registrazione? {
            val dallaSbobinatura =
                Thread.currentThread().stackTrace.any { f -> f.className.startsWith(ABBONATO_SBOBINATURA) }
            if (dallaSbobinatura) throw OutOfMemoryError("guasto iniettato")
            return delegato.trova(id)
        }
    }

    /** Captures every record logged on `"snastro"` (segnalazioneApp) while in use — same shape as [RegistroLog]. */
    private class SpiaSnastro : Handler(), AutoCloseable {
        private val radice = Logger.getLogger("snastro")
        val catturati: MutableList<LogRecord> = mutableListOf()

        init {
            radice.addHandler(this)
        }

        override fun publish(record: LogRecord) {
            catturati += record
        }

        override fun flush() = Unit

        override fun close() {
            radice.removeHandler(this)
        }
    }

    private fun rigaDi(stato: RegistrazioniUiStato, id: RegistrazioneId): StatoElaborazioneRiga? =
        rigaCompleta(stato, id)?.elaborazione

    private fun rigaCompleta(stato: RegistrazioniUiStato, id: RegistrazioneId): RigaRegistrazione? =
        (stato as? RegistrazioniUiStato.Dati)?.righe?.find { it.registrazioneId == id }

    private fun sbobinatura(ambiente: AmbienteProgetto): String? =
        ambiente.cartellaSbobinature().takeIf(Files::isDirectory)
            ?.listDirectoryEntries("*.md")?.singleOrNull()?.readText()

    /** A RiconoscitoreParlato that keeps a (pretend) model loaded across calls, counting its releases. */
    private class RiconoscitoreChiudibile : RiconoscitoreParlato, AutoCloseable {
        private val delegato = RiconoscitoreParlatoFinta()

        @Volatile var chiamate = 0

        @Volatile var chiusure = 0

        override fun riconosci(c: CampioniAudio): Riconoscimento = delegato.riconosci(c).also { chiamate++ }

        override fun close() {
            chiusure++
        }
    }

    /** A Diarizzatore that holds the pipeline in the DIARIZZAZIONE phase until [barriera] opens (interruptible). */
    private class DiarizzatoreConBarriera(
        private val barriera: CountDownLatch,
        private val delegato: Diarizzatore,
    ) : Diarizzatore {
        override fun diarizza(c: CampioniAudio, numeroPersone: NumeroPersone?): List<Turno> {
            barriera.await()
            return delegato.diarizza(c, numeroPersone)
        }
    }

    /** A Diarizzatore that holds a run in DIARIZZAZIONE only while a [barriera] is set and still closed. */
    private class DiarizzatoreTrattenibile(private val delegato: Diarizzatore) : Diarizzatore {
        @Volatile var barriera: CountDownLatch? = null

        override fun diarizza(c: CampioniAudio, numeroPersone: NumeroPersone?): List<Turno> {
            barriera?.await()
            return delegato.diarizza(c, numeroPersone)
        }
    }

    /**
     * AC-C58: waits for [segnale] like a native call would: deaf to the interrupt (restored on return, never lost),
     * and bounded, so a regression that never calls `interrompi` fails the assertions instead of hanging the JVM.
     */
    private fun attendiIgnorandoInterruzioni(segnale: CountDownLatch) {
        val scadenza = System.nanoTime() + TimeUnit.SECONDS.toNanos(ATTESA_INTERROMPI_S)
        var interrotto = false
        while (segnale.count > 0 && System.nanoTime() < scadenza) {
            try {
                segnale.await(scadenza - System.nanoTime(), TimeUnit.NANOSECONDS)
            } catch (ignored: InterruptedException) {
                interrotto = true
            }
        }
        if (interrotto) Thread.currentThread().interrupt()
    }

    private companion object {
        const val ATTESA_INTERROMPI_S = 20L
        const val ATTESA_NESSUN_AVVIO_MS = 1_500L
        const val ABBONATO_SBOBINATURA = "snastro.sbobinatura.adattatori.eventi.AbbonatoSbobinaturaEventi"
    }
}
