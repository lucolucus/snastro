package snastro.avvio.r1

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import org.junit.jupiter.api.io.TempDir
import snastro.avvio.ElementoInCoda
import snastro.avvio.FonteCoda
import snastro.avvio.GrafoR0
import snastro.avvio.RisultatoTentativo
import snastro.avvio.TipoElementoCoda
import snastro.avvio.orologioApp
import snastro.documento.applicazione.porte.LettoreNomi
import snastro.kernel.CampioniAudio
import snastro.kernel.ElaborazioneId
import snastro.kernel.IntervalloMs
import snastro.kernel.ParlanteId
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.atteso
import snastro.persistenza.apriDatabaseProgetto
import snastro.progetto.applicazione.letture.ElencoProgetti
import snastro.progetto.applicazione.porte.RegistroProgettiFinta
import snastro.supporto.test.attendiFinche
import snastro.trascrizione.adattatori.persistenza.ElaborazioneRepositorySql
import snastro.trascrizione.applicazione.comandi.AvviaElaborazione
import snastro.trascrizione.applicazione.comandi.UnisciVoci
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
import snastro.ui.ApriEsternoFinta
import snastro.ui.modelli.ServizioModelliFinta
import snastro.ui.modelli.StatoModelli
import snastro.ui.registrazione.RegistrazioneUiStato
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
import kotlin.time.Duration.Companion.seconds

/**
 * End-to-end ACs of the R1 composition, on [AmbienteR1] (real SessioneProgettoImpl + EstensioneR1 over
 * a real project folder; only FFmpeg and the ML models are Finte).
 */
class ComposizioneR1Test {
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
        val ambiente = AmbienteR1(radice)
        val id = ambiente.importa()
        val presenter = costruisciRegistrazioniPresenterR1(grafoR0Di(ambiente), ambiente.collaboratori, ambiente.r1) {}

        assertEquals(StatoElaborazioneVista.NON_AVVIATA, ambiente.r1.statiElaborazione(listOf(id)).single().stato)
        attendiFinche(timeout = 10.seconds, messaggio = "riga NON_AVVIATA in S2") {
            rigaDi(presenter.stato.value, id) == StatoElaborazioneRiga.NonAvviata
        }
        Thread.sleep(ATTESA_NESSUN_AVVIO_MS) // la coda gira ogni secondo: nulla deve comparire nel frattempo
        assertEquals(StatoElaborazioneVista.NON_AVVIATA, ambiente.r1.statiElaborazione(listOf(id)).single().stato)
        ambiente.close()

        val db = apriDatabaseProgetto(Path.of(ambiente.progetto.percorso).toFile())
        val righe = ElaborazioneRepositorySql(db.database).diRegistrazione(id)
        db.chiudi()
        assertTrue(righe.isEmpty(), "l'import non deve creare alcuna Elaborazione (ADR 0014): $righe")
    }

    @Test
    fun `AC-353 AC-354 una fase della pipeline e visibile in S2 senza polling, poi la riga e completata`() {
        val barriera = CountDownLatch(1)
        val ambiente = AmbienteR1(radice, DiarizzatoreConBarriera(barriera, DiarizzatoreFinta()))
        ambiente.use {
            val id = it.importa()
            val presenter = costruisciRegistrazioniPresenterR1(grafoR0Di(it), it.collaboratori, it.r1) {}

            it.r1.avviaElaborazione(AvviaElaborazione(id)).atteso() // 'Trascrivi'

            attendiFinche(timeout = 10.seconds, messaggio = "fase di diarizzazione in S2") {
                val riga = rigaDi(presenter.stato.value, id)
                riga is StatoElaborazioneRiga.InCorso && riga.faseEtichetta == etichetta(FaseElaborazione.DIARIZZAZIONE)
            }
            assertEquals(FaseElaborazione.DIARIZZAZIONE, it.r1.statiElaborazione(listOf(id)).single().fase)

            barriera.countDown()
            attendiFinche(timeout = 10.seconds, messaggio = "riga completata in S2") {
                rigaDi(presenter.stato.value, id) == StatoElaborazioneRiga.Completata
            }
        }
    }

    @Test
    fun `AC-369 Trascrivi con Numero di persone lo passa al Diarizzatore della pipeline`() {
        val diarizzatore = DiarizzatoreFinta()
        AmbienteR1(radice, diarizzatore).use {
            val id = it.importa()

            it.r1.avviaElaborazione(AvviaElaborazione(id, numeroPersone = 3)).atteso()

            attendiFinche(timeout = 10.seconds, messaggio = "elaborazione completata") {
                it.r1.statiElaborazione(listOf(id)).single().stato == StatoElaborazioneVista.COMPLETATA
            }
            assertEquals(listOf(NumeroPersone.di(3).atteso()), diarizzatore.numeroPersoneRicevuti)
        }
    }

    @Test
    fun `ADR 0004 la memoria dei modelli e rilasciata una volta per ogni Elaborazione terminata`() {
        val riconoscitore = RiconoscitoreChiudibile()
        AmbienteR1(radice, riconoscitore = riconoscitore, rilasciaDopoElaborazione = riconoscitore::close).use {
            val id = it.importa()
            it.r1.avviaElaborazione(AvviaElaborazione(id)).atteso()
            attendiFinche(timeout = 10.seconds, messaggio = "elaborazione completata") {
                it.r1.statiElaborazione(listOf(id)).single().stato == StatoElaborazioneVista.COMPLETATA
            }

            attendiFinche(timeout = 10.seconds, messaggio = "un rilascio per l'Elaborazione terminata") {
                riconoscitore.chiusure == 1
            }
            assertTrue(riconoscitore.chiamate > 0, "il riconoscitore deve essere stato usato prima del rilascio")
        }
    }

    @Test
    fun `AC-356 senza Parlanti il Documento rende ogni Voce come Voce n e una Revisione committata lo rigenera`() {
        AmbienteR1(radice, dueVoci).use {
            val id = it.importa()
            it.r1.avviaElaborazione(AvviaElaborazione(id)).atteso()
            attendiFinche(timeout = 10.seconds, messaggio = "Documento con due Voci") {
                documento(it)?.contains("**Voce 2**") == true
            }
            assertTrue(documento(it).orEmpty().contains("**Voce 1**"))

            it.r1.revisione.unisciVoci.esegui(UnisciVoci(id, sopravvive = VoceId(1), rimossa = VoceId(2))).atteso()

            attendiFinche(timeout = 10.seconds, messaggio = "Documento rigenerato dopo VociUnite") {
                documento(it)?.contains("**Voce 2**") == false
            }
            assertEquals(2, Regex("""\*\*Voce 1\*\*""").findAll(documento(it).orEmpty()).count())
        }
    }

    @Test
    fun `AC-351 S3 in sola lettura mostra il Trascritto completato con etichette Voce n e il percorso del Documento`() {
        AmbienteR1(radice, dueVoci).use {
            val id = it.importa()
            it.r1.avviaElaborazione(AvviaElaborazione(id)).atteso()
            attendiFinche(timeout = 10.seconds, messaggio = "Documento scritto") { it.r1.percorsoDocumento(id) != null }
            val grafo = GrafoR1(grafoR0Di(it), ServizioModelliFinta(StatoModelli.Pronti), ApriEsternoFinta())
            val scopeS3 = CoroutineScope(SupervisorJob() + it.dispatcherUi)

            val presenter = costruisciRegistrazionePresenterR1(grafo, it.collaboratori, it.r1, id, scopeS3)

            attendiFinche(timeout = 10.seconds, messaggio = "S3 caricato") {
                presenter.stato.value is RegistrazioneUiStato.Dati
            }
            val dati = presenter.stato.value as RegistrazioneUiStato.Dati
            assertEquals(listOf("Voce 1", "Voce 2"), dati.segmenti.map { s -> s.etichettaVoce })
            assertNotNull(dati.documentoPercorso)
            scopeS3.cancel()
        }
    }

    @Test
    fun `AC-478 annullare B in coda lo riporta a Trascrivi, C sale a In coda 1 e la coda non esegue mai B`() {
        val barriera = CountDownLatch(1) // A resta in_corso finche' non la si apre
        AmbienteR1(radice, DiarizzatoreConBarriera(barriera, DiarizzatoreFinta())).use {
            val a = it.importa()
            val b = it.importa()
            val c = it.importa()
            val presenter = costruisciRegistrazioniPresenterR1(grafoR0Di(it), it.collaboratori, it.r1) {}
            it.r1.avviaElaborazione(AvviaElaborazione(a)).atteso()
            attendiFinche(timeout = 10.seconds, messaggio = "A in corso") {
                it.r1.statiElaborazione(listOf(a)).single().fase == FaseElaborazione.DIARIZZAZIONE
            }
            attendiFinche(timeout = 10.seconds, messaggio = "S2 con A in corso") {
                rigaDi(presenter.stato.value, a) is StatoElaborazioneRiga.InCorso
            }
            presenter.avviaElaborazione(b) // S2 'Trascrivi' (reloads the list: queuing publishes no event)
            attendiFinche(timeout = 10.seconds, messaggio = "B in coda") {
                rigaDi(presenter.stato.value, b) is StatoElaborazioneRiga.InAttesa
            }
            presenter.avviaElaborazione(c)
            attendiFinche(timeout = 10.seconds, messaggio = "B 'In coda (1)' annullabile, C 'In coda (2)'") {
                val rigaB = rigaCompleta(presenter.stato.value, b)
                rigaB?.elaborazione == StatoElaborazioneRiga.InAttesa(1) && rigaB.annullabile &&
                    rigaDi(presenter.stato.value, c) == StatoElaborazioneRiga.InAttesa(2)
            }

            presenter.annullaElaborazione(b) // S2 'Annulla'

            attendiFinche(timeout = 10.seconds, messaggio = "B 'Trascrivi' e C 'In coda (1)' in S2") {
                val rigaB = rigaCompleta(presenter.stato.value, b)
                rigaB?.elaborazione == StatoElaborazioneRiga.NonAvviata && !rigaB.operazioneInCorso &&
                    rigaDi(presenter.stato.value, c) == StatoElaborazioneRiga.InAttesa(1)
            }
            assertNull(rigaCompleta(presenter.stato.value, b)?.erroreRiga)
            val vistaB = it.r1.statiElaborazione(listOf(b)).single()
            assertEquals(StatoElaborazioneVista.NON_AVVIATA, vistaB.stato)
            assertNull(vistaB.elaborazioneId, "nessuna riga elaborazione resta per B")

            barriera.countDown()
            attendiFinche(timeout = 10.seconds, messaggio = "la coda esegue C dopo A") {
                it.r1.statiElaborazione(listOf(c)).single().stato == StatoElaborazioneVista.COMPLETATA
            }
            assertEquals(StatoElaborazioneVista.NON_AVVIATA, it.r1.statiElaborazione(listOf(b)).single().stato)
            assertEquals(listOf(a, c), it.decodificate.toList(), "la pipeline non e mai invocata per B")
        }
    }

    @Test
    fun `AC-460 la composizione R1 non offre Ritrascrivi su una riga completata`() {
        AmbienteR1(radice).use {
            val id = it.importa()
            it.r1.avviaElaborazione(AvviaElaborazione(id)).atteso()
            attendiFinche(timeout = 10.seconds, messaggio = "elaborazione completata") {
                it.r1.statiElaborazione(listOf(id)).single().stato == StatoElaborazioneVista.COMPLETATA
            }
            val presenter = costruisciRegistrazioniPresenterR1(grafoR0Di(it), it.collaboratori, it.r1) {}

            attendiFinche(timeout = 10.seconds, messaggio = "riga completata in S2") {
                rigaDi(presenter.stato.value, id) == StatoElaborazioneRiga.Completata
            }
            val riga = checkNotNull(rigaCompleta(presenter.stato.value, id))
            assertFalse(riga.ritrascriviDisponibile, "nessun campo ne' bottone 'Ritrascrivi' in R1")
            assertFalse(riga.annullabile)
        }
    }

    @Test
    fun `AC-478 S3 riceve stati e AggiornamentiVista e segue lo stato della sua Registrazione senza polling`() {
        val diarizzatore = DiarizzatoreTrattenibile(dueVoci)
        AmbienteR1(radice, diarizzatore).use {
            val id = it.importa()
            it.r1.avviaElaborazione(AvviaElaborazione(id)).atteso()
            attendiFinche(timeout = 10.seconds, messaggio = "elaborazione completata") {
                it.r1.statiElaborazione(listOf(id)).single().stato == StatoElaborazioneVista.COMPLETATA
            }
            val grafo = GrafoR1(grafoR0Di(it), ServizioModelliFinta(StatoModelli.Pronti), ApriEsternoFinta())
            val scopeS3 = CoroutineScope(SupervisorJob() + it.dispatcherUi)
            val presenter = costruisciRegistrazionePresenterR1(grafo, it.collaboratori, it.r1, id, scopeS3)
            attendiFinche(timeout = 10.seconds, messaggio = "S3 modificabile") {
                (presenter.stato.value as? RegistrazioneUiStato.Dati)?.soloLettura == false
            }

            // A second run over the existing Trascritto (not offered by R1's S2, AC-460): S3 hears of it.
            val barriera = CountDownLatch(1).also { b -> diarizzatore.barriera = b }
            it.r1.avviaElaborazione(AvviaElaborazione(id)).atteso()
            attendiFinche(timeout = 10.seconds, messaggio = "S3 in sola lettura durante la nuova elaborazione") {
                (presenter.stato.value as? RegistrazioneUiStato.Dati)?.soloLettura == true
            }
            barriera.countDown()
            attendiFinche(timeout = 10.seconds, messaggio = "S3 di nuovo modificabile a fine elaborazione") {
                (presenter.stato.value as? RegistrazioneUiStato.Dati)?.soloLettura == false
            }
            scopeS3.cancel()
        }
    }

    @Test
    fun `carry-over 3 il recupero gira prima della coda, un in_corso lasciato da un crash diventa fallita`() {
        val ambiente = AmbienteR1(radice)
        val id = ambiente.importa()
        val percorso = ambiente.progetto.percorso
        ambiente.close()
        val db = apriDatabaseProgetto(Path.of(percorso).toFile())
        ElaborazioneRepositorySql(db.database)
            .salva(unaElaborazione(StatoElaborazione.IN_CORSO, id = ElaborazioneId("e-crash"), registrazioneId = id))
            .atteso()
        db.chiudi()

        val riaperto = AmbienteR1(radice.resolve("bis").also(Files::createDirectories))
        riaperto.use {
            it.sessione.chiudi()
            it.sessione.apri(percorso).atteso()
            attendiFinche(timeout = 10.seconds, messaggio = "recupero dell'in_corso") {
                it.r1.statiElaborazione(listOf(id)).single().stato == StatoElaborazioneVista.FALLITA
            }
            assertEquals("interrotta", it.r1.statiElaborazione(listOf(id)).single().motivoFallimento)
        }
    }

    @Test
    fun `carry-over 1 chiudi con la pipeline in corso ferma la coda prima di chiudere il database`() {
        val barriera = CountDownLatch(1) // mai rilasciata: la pipeline resta bloccata finche' chiudi la interrompe
        val ambiente = AmbienteR1(radice, DiarizzatoreConBarriera(barriera, DiarizzatoreFinta()))
        val id = ambiente.importa()
        val r1 = ambiente.r1
        val percorso = ambiente.progetto.percorso
        r1.avviaElaborazione(AvviaElaborazione(id)).atteso()
        attendiFinche(timeout = 10.seconds, messaggio = "Z in DIARIZZAZIONE") {
            r1.statiElaborazione(listOf(id)).single().fase == FaseElaborazione.DIARIZZAZIONE
        }

        ambiente.close()

        assertTrue(r1.coda.lavoro.isCompleted, "la coda deve essere ferma quando chiudi ritorna")
        assertNull(ambiente.sessione.corrente.value)

        // L624c: chiudi() ferma la coda ma non riscrive la riga (resta `in_corso`, interrotta a meta') —
        // e' la riapertura successiva (RecuperaElaborazioniInterrotte, come carry-over 3) a marcarla FALLITA.
        val riaperto = AmbienteR1(radice.resolve("dopo-chiudi").also(Files::createDirectories))
        riaperto.use {
            it.sessione.chiudi()
            it.sessione.apri(percorso).atteso()
            attendiFinche(timeout = 10.seconds, messaggio = "recupero dell'in_corso lasciato da chiudi") {
                it.r1.statiElaborazione(listOf(id)).single().stato == StatoElaborazioneVista.FALLITA
            }
            assertEquals("interrotta", it.r1.statiElaborazione(listOf(id)).single().motivoFallimento)
        }
    }

    // --- rework cycle 1 (AC-C54, AC-C55, AC-C58): the REAL EstensioneR1 wiring, not a fake `estensione` -----

    @Test
    @Suppress("MaxLineLength", "MaximumLineLength", "ArgumentListWrapping") // the test name alone crosses 120 columns
    fun `AC-C55 un Error che sfugge al lavoro del Documento dopo commit e segnalato una volta, lo scope del progetto sopravvive`() {
        SpiaSnastro().use { spia ->
            val nomiGuasti = object : LettoreNomi {
                override fun nomi(id: RegistrazioneId): Map<VoceRef, String> =
                    throw OutOfMemoryError("guasto iniettato")

                override fun registrazioniCon(p: ParlanteId): List<RegistrazioneId> = emptyList()
            }
            val ambiente = AmbienteR1(radice, lettoreNomi = { nomiGuasti })
            val id = ambiente.importa()

            // AC-356: dopo un'Elaborazione completata, ElaborazioneCompletata fa girare (dopo commit) il
            // worker del Documento, che chiama nomi.nomi(...) — qui sempre un OutOfMemoryError, un Error che
            // RitentaConBackoff non cattura mai (rethrow): sfugge alla coroutine del worker del Documento.
            ambiente.r1.avviaElaborazione(AvviaElaborazione(id)).atteso()

            attendiFinche(timeout = 10.seconds, messaggio = "l'Error del worker Documento e' stato segnalato") {
                spia.catturati.any { it.thrown is OutOfMemoryError }
            }
            assertEquals(
                1,
                spia.catturati.count { it.thrown is OutOfMemoryError },
                "AC-C55: segnalato esattamente una volta, mai per ogni retry",
            )
            assertTrue(
                ambiente.collaboratori.scope.isActive,
                "AC-C55: lo scope del progetto sopravvive all'Error del worker Documento (SupervisorJob)",
            )

            ambiente.close()
        }
    }

    @Test
    fun `AC-C54 un elemento escluso dalla coda condivisa e segnalato con WARNING attraverso la ONE Segnalazione`() {
        SpiaSnastro().use { spia ->
            // Una fonte SEMPRE rifiutata (mai la vera Elaborazione, che resta inerte: nessuna importata qui):
            // dopo MAX_TENTATIVI_PER_ID tentativi il suo id entra nell'esclusione e segnalaBloccato fa il suo
            // (unico) report — attraverso la wiring REALE di EstensioneR1, non una fonte finta di CodaCondivisa.
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
            AmbienteR1(radice, fontiCoda = listOf(fonteGuasta)).use {
                val messaggio = "l'elemento guasto e' escluso e segnalato via WARNING"
                attendiFinche(timeout = 10.seconds, messaggio = messaggio) {
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
        val fonteGuasta = FonteCoda(
            // RIASSUNTO, mai ELABORAZIONE: EstensioneR1 collega SEMPRE la sua VERA fonte Elaborazione nella
            // stessa lista (fonteCodaElaborazione(...) + contesto.fontiCoda, ADR 0023 §1) — un secondo tipo
            // ELABORAZIONE qui collide con `fermaEAttendi`'s `fonti.firstOrNull { it.tipo == attivo.tipo }`,
            // che sceglierebbe SEMPRE quella vera (prima nella lista, `interrompi` no-op di default) invece
            // di questa fonte finta: l'interrompi guasto non verrebbe mai chiamato, e il test passerebbe a
            // vuoto anche senza il fix (rework cycle 1, item 5: scoperto probando la rimozione del fix).
            tipo = TipoElementoCoda.RIASSUNTO,
            teste = { esclusi ->
                if ("guasta" !in esclusi) ElementoInCoda("guasta", "reg-guasta", Instant.EPOCH) else null
            },
            prossima = { _, _ ->
                bloccato.countDown()
                Thread.sleep(Long.MAX_VALUE) // mai raggiunto: runInterruptible interrompe il thread allo spegnimento
                RisultatoTentativo.Nessuno
            },
            ultimaTentata = { "guasta" },
            recupera = {},
            trattenuta = { false },
            interrompi = { throw IllegalStateException("interrompi guasto") },
        )
        val ambiente = AmbienteR1(radice, fontiCoda = listOf(fonteGuasta))
        val percorso = ambiente.progetto.percorso
        val progettoId = ambiente.progetto.progettoId
        assertTrue(bloccato.await(10, TimeUnit.SECONDS), "prossima deve essere partita e bloccata")

        // sessione.chiudi() direttamente (non ambiente.close(), che cancella PRIMA lo scope genitore e attende
        // fino a 5s lo spegnimento del suo esecutore: darebbe al worker tutto il tempo di sbloccarsi da solo,
        // svuotando `corrente` prima ancora che fermaEAttendi lo legga) — la stessa successione stretta
        // cancella-poi-fermaEAttendi di CodaCondivisaSegnalazioneTest, cosi' l'elemento e' ancora "in corso"
        // quando fermaEAttendi chiama interrompi().
        ambiente.sessione.chiudi() // non deve lanciare, nonostante l'interrompi guasto della fonte (AC-C58)

        assertNull(ambiente.sessione.corrente.value)
        val riaperta = AmbienteR1(radice.resolve("bis").also(Files::createDirectories))
        riaperta.use {
            // il database e' chiuso e il lock rilasciato: una riapertura riesce, mai ProgettoGiaAperto.
            val riaperto = it.sessione.apri(percorso).atteso()
            assertEquals(progettoId, riaperto.progettoId)
        }
        ambiente.close() // pulizia dell'esecutore/scope residui di AmbienteR1 (chiudi() e' idempotente)
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

    private fun documento(ambiente: AmbienteR1): String? =
        ambiente.cartellaDocumenti().takeIf(Files::isDirectory)
            ?.listDirectoryEntries("*.md")?.singleOrNull()?.readText()

    private fun grafoR0Di(ambiente: AmbienteR1) = GrafoR0(
        scope = ambiente.scope,
        io = ambiente.dispatcherUi,
        clock = orologioApp(),
        sessione = ambiente.sessione,
        elencoProgetti = ElencoProgetti(RegistroProgettiFinta()),
        cartellaProgettiPredefinita = radice.toString(),
    )

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

    private companion object {
        const val ATTESA_NESSUN_AVVIO_MS = 1_500L
    }
}
