package snastro.avvio.r3

import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.io.TempDir
import snastro.avvio.TipoElementoCoda
import snastro.avvio.r1.attendiFinche
import snastro.avvio.r2.AmbienteR2
import snastro.avvio.r2.EstrattoreConMutex
import snastro.avvio.r2.costruisciRegistrazionePresenterR2
import snastro.avvio.r2.voce
import snastro.kernel.ElaborazioneId
import snastro.kernel.Esito
import snastro.kernel.EventoPubblicato
import snastro.kernel.RegistrazioneId
import snastro.kernel.atteso
import snastro.parlanti.adattatori.persistenza.ParlanteRepositorySql
import snastro.parlanti.applicazione.comandi.RinominaParlante
import snastro.persistenza.SnastroDatabase
import snastro.persistenza.UnitaDiLavoroSql
import snastro.persistenza.apriDatabaseProgetto
import snastro.progetto.applicazione.comandi.EliminaRegistrazione
import snastro.sintesi.adattatori.persistenza.RiassuntoRepositorySql
import snastro.sintesi.applicazione.eventi.RiassuntoAvviato
import snastro.sintesi.applicazione.eventi.RiassuntoEliminato
import snastro.sintesi.applicazione.eventi.RiassuntoFallito
import snastro.sintesi.applicazione.eventi.RiassuntoPronto
import snastro.sintesi.applicazione.letture.ParteTestoVista
import snastro.sintesi.applicazione.porte.ErroreApplicazioneSintesi
import snastro.sintesi.applicazione.porte.conAvvio
import snastro.sintesi.applicazione.porte.conCompletamento
import snastro.sintesi.applicazione.porte.unRiassunto
import snastro.sintesi.applicazione.porte.unaStruttura
import snastro.sintesi.dominio.BozzaElemento
import snastro.sintesi.dominio.BozzaRiassunto
import snastro.sintesi.dominio.MotivoFallimento
import snastro.sintesi.dominio.Riassunto
import snastro.sintesi.dominio.RiassuntoId
import snastro.trascrizione.adattatori.persistenza.ElaborazioneRepositorySql
import snastro.trascrizione.applicazione.eventi.ElaborazioneAvviata
import snastro.trascrizione.applicazione.eventi.TrascrittoSostituito
import snastro.trascrizione.applicazione.letture.StatoElaborazioneVista
import snastro.trascrizione.dominio.StatoElaborazione
import snastro.trascrizione.dominio.unaElaborazione
import snastro.ui.registrazione.ComandoVoce
import snastro.ui.registrazione.RegistrazioneUiStato
import java.io.File
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.locks.ReentrantLock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The R3 composition end to end ([AmbienteR3]: a SQLite project FILE opened by the session with the production
 * driver, the real shared queue on its own worker thread, the real Sintesi/Trascrizione/Parlanti repositories and
 * subscribers; fake ML + [ModelloLinguisticoDiProva]): AC-S143, S145..S150, S161, S162.
 */
class ComposizioneR3Test {
    @TempDir
    lateinit var radice: Path

    @Test
    fun `AC-S143 i cinque abbonati sincroni sono registrati prima della coda e di qualunque comando`() {
        AmbienteR3(radice).use {
            // Sintesi's two (TrascrittoSostituito, RegistrazioneEliminata) before R2 runs — R2 then registers its own
            // Trascrizione (RegistrazioneEliminata) and Parlanti (both events) purges before R1 builds the queue.
            assertEquals(2, it.sincroniPrimaDiR2)
            assertEquals(4, it.sincroniAllApertura, "4 abbonati = 5 coppie (evento, abbonato)")
        }
        val estensione = File("src/main/kotlin/snastro/avvio/r3/EstensioneR3.kt").readLines()
        val r2 = estensione.indexOfFirst { "r2.apri(" in it }
        assertTrue(estensione.indexOfFirst { "AbbonatoTrascrizioneSintesi(" in it } in 0 until r2)
        assertTrue(estensione.indexOfFirst { "AbbonatoProgettoSintesi(" in it } in 0 until r2)
    }

    @Test
    fun `AC-S143 la composizione R2 non mostra la scheda Riassunto e non crea righe riassunto`() {
        AmbienteR2(radice).use {
            val a = it.importa()
            it.trascrivi(a)
            val s3 = costruisciRegistrazionePresenterR2(it.grafo, it.collaboratori, it.r2, a, it.scope)
            attendiFinche(messaggio = "S3 caricata") { s3.stato.value is RegistrazioneUiStato.Dati }
            assertNull((s3.stato.value as RegistrazioneUiStato.Dati).contenutoRiassunto, "nessuna scheda in R2")
            val riassunti = RiassuntoRepositorySql(it.contesto.database, it.contesto.lettura)
            assertEquals(emptyList(), riassunti.diRegistrazione(a))
        }
        val r1r2 = listOf("r1", "r2").flatMap { d ->
            File("src/main/kotlin/snastro/avvio/$d").walkTopDown()
                .filter { f -> f.isFile && f.extension == "kt" }
                .toList()
        }
        assertEquals(emptyList(), r1r2.filter { f -> "snastro.sintesi" in f.readText() }.map { f -> f.name })
    }

    @Test
    fun `AC-S145 all'avvio i due recuperi girano prima del primo reclamo`() {
        AmbienteR3(radice).use {
            val a = it.registrazioneTrascritta()
            val b = it.registrazioneTrascritta()
            val cartella = it.progetto.percorso
            it.sessione.chiudi()
            val interrotto = unRiassunto("interrotto", a).conAvvio()
            val daFare = unRiassunto("da-fare", b)
            conDatabase(cartella) { db ->
                val uow = UnitaDiLavoroSql(db)
                val repo = RiassuntoRepositorySql(db, uow)
                uow.inTransazione { repo.salva(interrotto).also { repo.salva(daFare) } }.atteso()
                ElaborazioneRepositorySql(db).salva(
                    unaElaborazione(
                        StatoElaborazione.IN_CORSO,
                        ElaborazioneId("e-interrotta"),
                        a,
                        creataAlle = Instant.now().plusSeconds(60), // the latest one of a
                        avviataAlle = Instant.now().plusSeconds(61),
                    ),
                ).atteso()
            }
            val alPrimoReclamo = CopyOnWriteArrayList<Any?>()
            val ambiente = it
            ambiente.modello.primaDellaRisposta = { _ ->
                if (alPrimoReclamo.isEmpty()) {
                    alPrimoReclamo += ambiente.riassunti.trova(RiassuntoId("interrotto"))?.motivoFallimento
                    alPrimoReclamo += ambiente.stato(a)
                }
            }

            it.sessione.apri(cartella).atteso()

            it.attendiPronto(b)
            assertEquals(listOf(MotivoFallimento.INTERROTTO, StatoElaborazioneVista.FALLITA), alPrimoReclamo.toList())
            assertEquals(1, it.modello.chiamate.get(), "l'interrotto non viene rieseguito")
        }
    }

    @Test
    fun `AC-S146 Riassumi fino al pronto mostrato, e due Riassunti e un'Elaborazione in ordine di richiesta`() {
        AmbienteR3(radice).use {
            val a = it.registrazioneTrascritta()
            val b = it.registrazioneTrascritta()
            val c = it.registrazioneTrascritta()
            val d = it.registrazioneTrascritta()
            val avvii = registraAvvii(it)

            it.riassumi(a)
            assertTrue(it.diRegistrazione(a).single().let { r -> r.inAttesa || r.inCorso || r.pronto })
            it.attendiPronto(a)
            assertNotNull(it.r3.vista(a)?.mostrato, "riassunto-vista mostra il pronto")
            assertEquals(listOf(a), avvii.toList())

            it.modello.blocca()
            it.riassumi(c) // occupies the worker so the next three queue up
            attendiFinche(messaggio = "c in corso") { it.modello.chiamate.get() == 2 }
            it.riassumi(b)
            Thread.sleep(PAUSA_MS)
            it.avviaElaborazione(d)
            Thread.sleep(PAUSA_MS)
            it.riassumi(a)
            it.modello.sblocca()

            attendiFinche(messaggio = "tutti conclusi") {
                it.diRegistrazione(a).singleOrNull()?.pronto == true && it.stato(d) == StatoElaborazioneVista.COMPLETATA
            }
            assertEquals(listOf(a, c, b, d, a), avvii.toList(), "R(b), E(d), R(a) nell'ordine di richiesta")
        }
    }

    @Test
    fun `AC-S147 Elimina cancella pronto e in_attesa con elementi e Fonti`() {
        AmbienteR3(radice).use {
            val a = it.registrazioneTrascritta()
            val b = it.registrazioneTrascritta()
            it.riassumi(a)
            it.attendiPronto(a)
            val pronto = it.diRegistrazione(a).single().id
            assertTrue(conteggiFigli(it.contesto.database, pronto) > 0)
            it.modello.blocca()
            it.riassumi(b)
            attendiFinche(messaggio = "b in corso") { it.modello.chiamate.get() == 2 }
            it.riassumi(a)
            val inAttesa = it.diRegistrazione(a).single { r -> r.inAttesa }.id

            it.r3.r2.eliminaRegistrazione(EliminaRegistrazione(a)).atteso()

            assertEquals(emptyList(), it.diRegistrazione(a))
            assertEquals(0, conteggiFigli(it.contesto.database, pronto))
            assertEquals(0, conteggiFigli(it.contesto.database, inAttesa))
            it.modello.sblocca()
            it.attendiPronto(b)
        }
    }

    @Test
    fun `AC-S147 senza AbbonatoProgettoSintesi (composizione R2) l'eliminazione fallisce sulla FK e nulla cambia`() {
        AmbienteR2(radice).use {
            val a = it.importa()
            it.trascrivi(a)
            val db = it.contesto.database
            val repo = RiassuntoRepositorySql(db, it.contesto.lettura)
            val r = unRiassunto("pronto", a).conAvvio()
            UnitaDiLavoroSql(db).inTransazione { repo.salva(r) }.atteso()
            r.conCompletamento(BOZZA, unaStruttura(1 to 1, 2 to 2))
            UnitaDiLavoroSql(db).inTransazione { repo.concludi(r).poiUnit() }.atteso()
            val figli = conteggiFigli(db, r.id)

            val esito = runCatching { it.r2.eliminaRegistrazione(EliminaRegistrazione(a)) }

            assertFalse(esito.getOrNull() is Esito.Ok, "l'eliminazione non riesce: $esito")
            assertTrue(it.collaboratori.registrazioni().any { x -> x.registrazioneId == a })
            assertTrue(repo.diRegistrazione(a).single().pronto)
            assertEquals(figli, conteggiFigli(db, r.id))
            assertNotNull(it.r2.r1.trascritto(a))
        }
    }

    @Test
    fun `AC-S148 Ritrascrivi completata sostituisce il pronto con UN in_attesa con lo stesso Argomento`() {
        AmbienteR3(radice).use {
            val a = it.registrazioneTrascritta()
            it.riassumi(a, "budget")
            it.attendiPronto(a)
            val vecchio = it.diRegistrazione(a).single().id
            val alCommit = CopyOnWriteArrayList<List<Riassunto>>()
            it.contesto.dispatcher.registraDopoCommit { e ->
                if (e is TrascrittoSostituito && e.registrazioneId == a) alCommit += it.diRegistrazione(a)
            }
            val barriera = CountDownLatch(1)
            it.diarizzatore.barriera = barriera
            it.diarizzatore.turni = AmbienteR2.TRE_VOCI
            it.modello.blocca()

            it.avviaElaborazione(a)
            attendiFinche(messaggio = "ritrascrizione in corso") { it.stato(a) == StatoElaborazioneVista.IN_CORSO }
            assertEquals(listOf(vecchio), it.diRegistrazione(a).map { r -> r.id }, "prima del commit: il vecchio")
            assertTrue(it.diRegistrazione(a).single().pronto)
            barriera.countDown()

            attendiFinche(messaggio = "commit della sostituzione") { alCommit.isNotEmpty() }
            val nuovo = alCommit.single().single()
            assertTrue(nuovo.inAttesa)
            assertEquals("budget", nuovo.argomento?.valore)
            assertNotEquals(vecchio, nuovo.id)
            it.modello.sblocca()
            it.attendiPronto(a)
        }
    }

    @Test
    fun `AC-S148 una Ritrascrizione fallita non cambia nulla`() {
        AmbienteR3(radice).use {
            val a = it.registrazioneTrascritta()
            it.riassumi(a, "budget")
            it.attendiPronto(a)
            val prima = it.diRegistrazione(a).single()
            it.diarizzatore.fallisci = true

            it.avviaElaborazione(a)
            attendiFinche(messaggio = "ritrascrizione fallita") {
                it.r3.r2.r1.statiElaborazione(listOf(a)).single().let { s ->
                    s.stato == StatoElaborazioneVista.FALLITA || s.motivoFallimento != null
                }
            }
            val dopo = it.diRegistrazione(a).single()
            assertEquals(prima.id, dopo.id)
            assertTrue(dopo.pronto)
            assertEquals(prima.decisioni, dopo.decisioni)
        }
    }

    @Test
    fun `AC-S149 eliminare durante un run - nessuna riga, ne Pronto ne Fallito, la coda prosegue`() {
        AmbienteR3(radice).use {
            val a = it.registrazioneTrascritta()
            val b = it.registrazioneTrascritta()
            val eventi = registraEventi(it)
            it.modello.blocca()
            it.riassumi(a)
            attendiFinche(messaggio = "a in corso") { it.modello.chiamate.get() == 1 }

            it.r3.r2.eliminaRegistrazione(EliminaRegistrazione(a)).atteso()
            attendiFinche(messaggio = "il run di a annullato") { it.modello.esiti.isNotEmpty() }
            it.modello.sblocca()

            assertEquals(Esito.Errore(ErroreApplicazioneSintesi.Annullato), it.modello.esiti.single())
            assertEquals(emptyList(), it.diRegistrazione(a))
            it.riassumi(b)
            it.attendiPronto(b)
            val diA = eventi.filter { e -> registrazioneDi(e) == a }
            assertTrue(diA.none { e -> e is RiassuntoPronto || e is RiassuntoFallito }, "eventi di a: $diA")
        }
    }

    @Test
    fun `AC-S150 il run del LLM non prende il Mutex sherpa - una Conferma completa mentre il Riassunto e bloccato`() {
        val mutex = ReentrantLock(true)
        AmbienteR3(radice, estrattore = EstrattoreConMutex(mutex)).use {
            val a = it.registrazioneTrascritta()
            it.modello.blocca()
            it.riassumi(a)
            attendiFinche(messaggio = "Riassunto in corso") { it.modello.chiamate.get() == 1 }

            val esito = runBlocking { it.r3.r2.comandi.esegui(ComandoVoce.Nuovo(voce(a, 1), "Anna")) }

            assertEquals(Esito.Ok(Unit), esito)
            assertFalse(mutex.isLocked)
            assertTrue(it.modello.esiti.isEmpty(), "il Riassunto e ancora bloccato")
            it.modello.sblocca()
            it.attendiPronto(a)
        }
    }

    @Test
    fun `AC-S161 un RiassuntoEliminato tardivo o duplicato non annulla il Riassunto riaccodato dalla sostituzione`() {
        AmbienteR3(radice).use {
            val a = it.registrazioneTrascritta()
            it.riassumi(a, "budget")
            it.attendiPronto(a)
            it.diarizzatore.turni = AmbienteR2.TRE_VOCI
            it.modello.blocca()
            it.avviaElaborazione(a) // sostituzione: commits RiassuntoEliminato(a) + RiassuntoRichiesto(a), re-queues X
            attendiFinche(messaggio = "X reclamato e in corso") { it.modello.chiamate.get() == 2 }
            val x = it.diRegistrazione(a).single { r -> r.inCorso }.id

            repeat(2) { _ -> consegna(it, RiassuntoEliminato(a)) } // late + duplicate delivery
            Thread.sleep(PAUSA_MS)
            it.modello.sblocca()

            it.attendiPronto(a)
            assertEquals(x, it.diRegistrazione(a).single().id, "X completa normalmente, non resta in_corso")
            assertIs<Esito.Ok<*>>(it.modello.esiti.last())
            it.riassumi(a) // not refused (a stuck in_corso X would be RiassuntoGiaAperto)
            attendiFinche(messaggio = "il nuovo Riassunto completa") { it.modello.chiamate.get() == 3 }
        }
    }

    @Test
    fun `AC-S162 lo STOP annulla il Riassunto in corso anche con la riga presente e interruzioni ignorate`() {
        AmbienteR3(radice).use {
            val a = it.registrazioneTrascritta()
            it.modello.blocca()
            it.riassumi(a)
            attendiFinche(messaggio = "Riassunto in corso") { it.modello.chiamate.get() == 1 }
            val coda = it.r3.r2.r1.coda

            it.contesto.scope.cancel() // fermaEAttendi's contract: the caller cancels the scope first
            val inizio = System.nanoTime()
            val fermata = coda.fermaEAttendi(TIMEOUT_STOP_MS)

            assertTrue(fermata, "la coda si ferma entro il timeout")
            assertTrue((System.nanoTime() - inizio) / NANO_PER_MS < TIMEOUT_STOP_MS)
            assertEquals(Esito.Errore(ErroreApplicazioneSintesi.Annullato), it.modello.esiti.single())
            assertTrue(it.diRegistrazione(a).single().inCorso, "nulla scritto: il recupero lo marchera' interrotto")
            coda.annullaInCorso(TipoElementoCoda.RIASSUNTO, a.valore) // nothing running any more: no effect
        }
    }

    @Test
    fun `AC-C35 contesto lettura e la stessa istanza a cui il dispatcher delega`() {
        AmbienteR3(radice).use {
            val visto = it.contesto.dispatcher.unitaDiLavoro.inTransazione {
                Esito.Ok(it.contesto.lettura.inLettura { 1 })
            }.atteso()

            assertEquals(1, visto, "la inLettura annidata deve UNIRSI alla transazione, non aprirne una propria")
        }
    }

    @Test
    fun `LettoreNomi su SQL reale - il run etichetta la Voce col Nome attuale e la vista segue una rinomina`() {
        AmbienteR3(radice).use {
            val a = it.registrazioneTrascritta()
            assertEquals(Esito.Ok(Unit), runBlocking { it.r3.r2.comandi.esegui(ComandoVoce.Nuovo(voce(a, 1), "Anna")) })

            it.riassumi(a)
            it.attendiPronto(a)

            assertTrue("Anna" in it.modello.richieste.single().ingresso, "la legenda nomina Voce 1 'Anna'")
            assertEquals(listOf("Anna"), nomiNelSommario(it, a))
            val parlanti = ParlanteRepositorySql(it.contesto.database, it.contesto.lettura)
            val anna = parlanti.delProgetto(it.progetto.progettoId).single().id
            it.r3.r2.comandiParlante.rinomina(RinominaParlante(anna, "Annamaria")).atteso()
            assertEquals(listOf("Annamaria"), nomiNelSommario(it, a), "INV-S5: i nomi si leggono, mai salvati")
        }
    }

    private fun nomiNelSommario(ambiente: AmbienteR3, id: RegistrazioneId): List<String?> =
        checkNotNull(ambiente.r3.vista(id)?.mostrato?.sommario)
            .filterIsInstance<ParteTestoVista.Voce>()
            .filter { p -> p.voce.voceId == 1 }
            .map { p -> p.voce.nome }

    private fun registraEventi(ambiente: AmbienteR3): MutableList<EventoPubblicato> =
        CopyOnWriteArrayList<EventoPubblicato>().also { l ->
            ambiente.contesto.dispatcher.registraDopoCommit { e -> l += e }
        }

    /** The start order of every queued item, by Registrazione (Riassunto and Elaborazione alike). */
    private fun registraAvvii(ambiente: AmbienteR3): MutableList<RegistrazioneId> =
        CopyOnWriteArrayList<RegistrazioneId>().also { l ->
            ambiente.contesto.dispatcher.registraDopoCommit { e ->
                when (e) {
                    is RiassuntoAvviato -> l += e.registrazioneId
                    is ElaborazioneAvviata -> l += e.registrazioneId
                    else -> Unit
                }
            }
        }

    /** A (late / duplicate) after-commit delivery of [evento] through the project's own dispatcher. */
    private fun consegna(ambiente: AmbienteR3, evento: EventoPubblicato) {
        val d = ambiente.contesto.dispatcher
        d.unitaDiLavoro.inTransazione {
            d.pubblica(evento)
            Esito.Ok(Unit)
        }.atteso()
    }

    private fun registrazioneDi(e: EventoPubblicato): RegistrazioneId? = when (e) {
        is RiassuntoPronto -> e.registrazioneId
        is RiassuntoFallito -> e.registrazioneId
        is RiassuntoAvviato -> e.registrazioneId
        is RiassuntoEliminato -> e.registrazioneId
        else -> null
    }

    private fun conDatabase(cartella: String, blocco: (SnastroDatabase) -> Unit) {
        val db = apriDatabaseProgetto(File(cartella))
        try {
            blocco(db.database)
        } finally {
            db.chiudi()
        }
    }

    private companion object {
        const val PAUSA_MS = 20L
        const val TIMEOUT_STOP_MS = 5_000L
        const val NANO_PER_MS = 1_000_000L

        val BOZZA = BozzaRiassunto(
            sommario = "{V1} apre la riunione.",
            decisioni = listOf(BozzaElemento("Si parte dal primo punto.", listOf(1), null)),
            questioniAperte = emptyList(),
            azioni = emptyList(),
            puntiChiave = emptyList(),
        )

        /** Rows of `riassunto_elemento` + `riassunto_fonte` of [id] (ADR 0024: the children go with the root). */
        fun conteggiFigli(db: SnastroDatabase, id: RiassuntoId): Int =
            db.riassuntoElementoQueries.trovaDiRiassunto(id.valore).executeAsList().size +
                db.riassuntoFonteQueries.trovaDiRiassunto(id.valore).executeAsList().size

        fun Esito<Boolean>.poiUnit(): Esito<Unit> = when (this) {
            is Esito.Ok -> Esito.Ok(Unit)
            is Esito.Errore -> this
        }
    }
}
