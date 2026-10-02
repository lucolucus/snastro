package snastro.avvio.sintesi

import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.io.TempDir
import snastro.avvio.Abbonamento
import snastro.avvio.coda.CodaCondivisa
import snastro.avvio.parlanti.ModuloParlanti
import snastro.avvio.progetto.AmbienteProgetto
import snastro.avvio.progetto.EstrattoreConMutex
import snastro.avvio.progetto.SondaCostruzioni
import snastro.avvio.progetto.parteDi
import snastro.avvio.progetto.voce
import snastro.avvio.trascrizione.ModuloTrascrizione
import snastro.kernel.DispatcherEventiInMemoria
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
import snastro.progetto.applicazione.comandi.EliminaRegistrazioneServizio
import snastro.sintesi.adattatori.persistenza.RiassuntoRepositorySql
import snastro.sintesi.applicazione.eventi.RiassuntoAvviato
import snastro.sintesi.applicazione.eventi.RiassuntoEliminato
import snastro.sintesi.applicazione.eventi.RiassuntoFallito
import snastro.sintesi.applicazione.eventi.RiassuntoPronto
import snastro.sintesi.applicazione.letture.ParteTestoVista
import snastro.sintesi.applicazione.letture.RiassuntiInAttesa
import snastro.sintesi.applicazione.porte.ErroreApplicazioneSintesi
import snastro.sintesi.applicazione.porte.conAvvio
import snastro.sintesi.applicazione.porte.conCompletamento
import snastro.sintesi.applicazione.porte.unRiassunto
import snastro.sintesi.applicazione.porte.unaStruttura
import snastro.sintesi.dominio.BozzaElemento
import snastro.sintesi.dominio.BozzaRiassunto
import snastro.sintesi.dominio.MotivoFallimento
import snastro.sintesi.dominio.RiassuntoId
import snastro.supporto.test.OrologioFinto
import snastro.supporto.test.attendiFinche
import snastro.supporto.test.pausaInTempoReale
import snastro.trascrizione.adattatori.persistenza.ElaborazioneRepositorySql
import snastro.trascrizione.applicazione.eventi.ElaborazioneAvviata
import snastro.trascrizione.applicazione.eventi.ElaborazioneFallita
import snastro.trascrizione.applicazione.eventi.TrascrittoSostituito
import snastro.trascrizione.applicazione.letture.StatoElaborazioneVista
import snastro.trascrizione.dominio.StatoElaborazione
import snastro.trascrizione.dominio.unaElaborazione
import snastro.ui.registrazione.ComandoVoce
import java.io.File
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The Sintesi module of the single composition end to end ([AmbienteProgetto]: a SQLite project FILE opened by the
 * session with the production
 * driver, the real shared queue on its own worker thread, the real Sintesi/Trascrizione/Parlanti repositories and
 * subscribers; fake ML + [ModelloLinguisticoDiProva]): AC-S143, S145..S150, S161, S162.
 */
class ComposizioneSintesiTest {
    @TempDir
    lateinit var radice: Path

    @Test
    fun `AC-S143 la lista sincrona dichiarata e Sintesi, Parlanti, Trascrizione e precede la coda e ogni comando`() {
        AmbienteProgetto(radice).use {
            val a = it.importa()
            val percorso = it.progetto.percorso
            it.sessione.chiudi()
            // A crashed in_corso run: at the next open, the recovery (the first COMMAND, apriProgetto step 5) fails it.
            conDatabase(percorso) { db ->
                ElaborazioneRepositorySql(db)
                    .salva(unaElaborazione(StatoElaborazione.IN_CORSO, ElaborazioneId("e-crash"), a))
                    .atteso()
            }

            val costruzioni = SondaCostruzioni.durante { it.apri(percorso) }.second

            // ADR 0030 §2: the declared list itself, read from the composed project (no reflection, no source scan).
            assertEquals(
                listOf(ModuloSintesi::class, ModuloParlanti::class, ModuloTrascrizione::class),
                it.composto.ordineSincroni.map { m -> m::class },
            )
            // …and the dispatcher got exactly its pairs, in that order: Sintesi's two, Parlanti's, Trascrizione's.
            val ordine = costruzioni.di { true }
            val registrati = ordine.filter { c -> c.istanza.javaClass.simpleName == "IscrizioneSincrona" }
                .map { c -> (c.argomenti.single() as Abbonamento<*>).abbonato.javaClass.simpleName }
            assertEquals(SINCRONI_DICHIARATI, registrati)
            // Registration precedes the queue's construction and the first command (the recovery's own event).
            val nomi = ordine.map { c -> c.istanza.javaClass.name }
            val ultimaRegistrazione = nomi.indexOfLast { n -> n.endsWith("IscrizioneSincrona") }
            val coda = nomi.indexOf(CodaCondivisa::class.java.name)
            val primoComando = nomi.indexOf(ElaborazioneFallita::class.java.name)
            assertTrue(ultimaRegistrazione in 0 until coda, "registrati prima della coda")
            assertTrue(coda < primoComando, "la coda prima del recupero, il primo comando: $primoComando")
            assertEquals(StatoElaborazioneVista.FALLITA, it.stato(a))
        }
    }

    @Test
    fun `AC-S145 all'avvio i due recuperi girano prima del primo reclamo`() {
        AmbienteProgetto(radice).use {
            val a = it.registrazioneTrascritta()
            val b = it.registrazioneTrascritta()
            val cartella = it.progetto.percorso
            it.sessione.chiudi()
            val interrotto = unRiassunto("interrotto", it.incontroDi(a)).conAvvio()
            val daFare = unRiassunto("da-fare", it.incontroDi(b))
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
            // The worker claims as soon as `avvia` runs, INSIDE `apri` — before the session publishes the project
            // that `ambiente.riassunti`/`stato` read through. So the probe waits for that publish (rework c3-2): the
            // queue is single-threaded and parked in this very claim, so what it reads is still the first claim's.
            val pubblicato = CountDownLatch(1)
            ambiente.modello.primaDellaRisposta = { _ ->
                if (alPrimoReclamo.isEmpty()) {
                    check(pubblicato.await(ATTESA_PUBBLICAZIONE_S, TimeUnit.SECONDS)) { "apri non ha pubblicato" }
                    alPrimoReclamo += ambiente.riassunti.trova(RiassuntoId("interrotto"))?.motivoFallimento
                    alPrimoReclamo += ambiente.stato(a)
                }
            }

            it.sessione.apri(cartella).atteso()
            pubblicato.countDown()

            it.attendiPronto(b)
            assertEquals(listOf(MotivoFallimento.INTERROTTO, StatoElaborazioneVista.FALLITA), alPrimoReclamo.toList())
            assertEquals(1, it.modello.chiamate.get(), "l'interrotto non viene rieseguito")
        }
    }

    @Test
    fun `AC-S146 Riassumi fino al pronto mostrato, e due Riassunti e un'Elaborazione in ordine di richiesta`() {
        // OrologioFinto (never a real sleep): richiestoAlle/avviatoAlle are persisted at ms precision, so an
        // exact avanza() between the three requests below guarantees the FIFO order deterministically.
        val orologio = OrologioFinto(Instant.now())
        AmbienteProgetto(radice, clock = orologio).use {
            val a = it.registrazioneTrascritta()
            val b = it.registrazioneTrascritta()
            val c = it.registrazioneTrascritta()
            val d = it.registrazioneTrascritta()
            val avvii = registraAvvii(it)

            it.riassumi(a)
            assertTrue(it.diRegistrazione(a).single().let { r -> r.inAttesa || r.inCorso || r.pronto })
            it.attendiPronto(a)
            assertNotNull(it.sintesi.vista(a)?.mostrato, "riassunto-vista mostra il pronto")
            assertEquals(listOf(a), avvii.toList())

            it.modello.blocca()
            it.riassumi(c) // occupies the worker so the next three queue up
            attendiFinche(timeout = 10.seconds, messaggio = "c in corso") { it.modello.chiamate.get() == 2 }
            it.riassumi(b)
            orologio.avanza(PASSO_OROLOGIO)
            it.avviaElaborazione(d)
            orologio.avanza(PASSO_OROLOGIO)
            it.riassumi(a)
            it.modello.sblocca()

            attendiFinche(timeout = 10.seconds, messaggio = "tutti conclusi") {
                it.diRegistrazione(a).singleOrNull()?.pronto == true && it.stato(d) == StatoElaborazioneVista.COMPLETATA
            }
            assertEquals(listOf(a, c, b, d, a), avvii.toList(), "R(b), E(d), R(a) nell'ordine di richiesta")
        }
    }

    @Test
    fun `AC-S147 Elimina cancella pronto e in_attesa con elementi e Fonti`() {
        AmbienteProgetto(radice).use {
            val a = it.registrazioneTrascritta()
            val b = it.registrazioneTrascritta()
            it.riassumi(a)
            it.attendiPronto(a)
            val pronto = it.diRegistrazione(a).single().id
            assertTrue(conteggiFigli(it.porte.database, pronto) > 0)
            it.modello.blocca()
            it.riassumi(b)
            attendiFinche(timeout = 10.seconds, messaggio = "b in corso") { it.modello.chiamate.get() == 2 }
            it.riassumi(a)
            val inAttesa = it.diRegistrazione(a).single { r -> r.inAttesa }.id

            it.collaboratori.eliminaRegistrazione(EliminaRegistrazione(a)).atteso()

            assertEquals(emptyList(), it.diRegistrazione(a))
            assertEquals(0, conteggiFigli(it.porte.database, pronto))
            assertEquals(0, conteggiFigli(it.porte.database, inAttesa))
            it.modello.sblocca()
            it.attendiPronto(b)
        }
    }

    @Test
    fun `AC-S147 senza AbbonatoProgettoSintesi l eliminazione fallisce sulla FK e nulla cambia`() {
        AmbienteProgetto(radice).use {
            val a = it.importa()
            it.trascrivi(a)
            val db = it.porte.database
            val repo = it.porte.riassunti
            val r = unRiassunto("pronto", it.incontroDi(a)).conAvvio()
            it.porte.unitaDiLavoro.inTransazione { repo.salva(r) }.atteso()
            r.conCompletamento(BOZZA, unaStruttura(1 to 1, 2 to 2), parte = a)
            it.porte.unitaDiLavoro.inTransazione { repo.concludi(r).poiUnit() }.atteso()
            val figli = conteggiFigli(db, r.id)
            // ADR 0024 §1 "fails closed": the declared list MINUS Sintesi (the same subscriber values the modules
            // expose), on a dispatcher of its own over the same database — the `riassunto` IMMEDIATE FK refuses.
            val senzaSintesi = DispatcherEventiInMemoria(UnitaDiLavoroSql(db))
            it.composto.ordineSincroni.filterNot { m -> m is ModuloSintesi }.flatMap { m -> m.abbonatiSincroni() }
                .forEach { ab ->
                    senzaSintesi.registraSincrono { e ->
                        if (ab.evento.isInstance(e)) ab.abbonato.ricevi(e) else Esito.Ok(Unit)
                    }
                }
            val elimina = EliminaRegistrazioneServizio(
                senzaSintesi.unitaDiLavoro,
                it.porte.registrazioni,
                it.porte.incontri,
                it.porte.eliminazioniInSospeso,
                senzaSintesi,
            )

            val esito = runCatching { elimina.esegui(EliminaRegistrazione(a)) }

            assertFalse(esito.getOrNull() is Esito.Ok, "l'eliminazione non riesce: $esito")
            assertTrue(it.collaboratori.registrazioni().any { x -> x.registrazioneId == a })
            assertTrue(repo.trova(it.incontroDi(a)).single().pronto)
            assertEquals(figli, conteggiFigli(db, r.id))
            assertNotNull(it.trascrizione.trascritto(a))
        }
    }

    @Test
    fun `INV-I12b una Ritrascrivi completata lascia il pronto com'e, senza nuovo Riassunto ne voci in coda`() {
        AmbienteProgetto(radice).use {
            val a = it.registrazioneTrascritta()
            it.riassumi(a, "budget")
            it.attendiPronto(a)
            val prima = it.diRegistrazione(a).single()
            val chiamate = it.modello.chiamate.get()
            it.diarizzatoreScriptato.turni = AmbienteProgetto.TRE_VOCI

            it.avviaElaborazione(a)
            attendiFinche(timeout = 10.seconds, messaggio = "ritrascrizione completata") {
                it.stato(a) == StatoElaborazioneVista.COMPLETATA
            }
            pausaInTempoReale(
                PAUSA_MS.milliseconds,
                motivo = "da' a un eventuale Riassunto automatico la possibilita' di essere accodato",
            )

            val dopo = it.diRegistrazione(a).single()
            assertEquals(prima.id, dopo.id)
            assertTrue(dopo.pronto)
            assertEquals(prima.decisioni, dopo.decisioni)
            assertEquals(prima.argomento, dopo.argomento)
            assertEquals(emptyList(), RiassuntiInAttesa(it.porte.riassunti).elenco())
            assertEquals(chiamate, it.modello.chiamate.get(), "nessun nuovo run del modello")
        }
    }

    @Test
    fun `INV-I12b un TrascrittoSostituito sul dispatcher non raggiunge alcun abbonato di Sintesi`() {
        AmbienteProgetto(radice).use {
            val a = it.registrazioneTrascritta()
            val modulo = it.composto.ordineSincroni.filterIsInstance<ModuloSintesi>().single()
            val eventi = modulo.abbonatiSincroni().map { ab -> ab.evento } +
                modulo.abbonatiDopoCommit().map { ab -> ab.evento }

            assertTrue(eventi.none { e -> e.isInstance(TrascrittoSostituito(a)) }, "abbonati di Sintesi: $eventi")
            consegna(it, TrascrittoSostituito(a))
            assertEquals(emptyList(), it.diRegistrazione(a))
        }
    }

    @Test
    fun `AC-S148 una Ritrascrizione fallita non cambia nulla`() {
        AmbienteProgetto(radice).use {
            val a = it.registrazioneTrascritta()
            it.riassumi(a, "budget")
            it.attendiPronto(a)
            val prima = it.diRegistrazione(a).single()
            it.diarizzatoreScriptato.fallisci = true

            it.avviaElaborazione(a)
            attendiFinche(timeout = 10.seconds, messaggio = "ritrascrizione fallita") {
                it.vistaDi(a).let { s ->
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
        AmbienteProgetto(radice).use {
            val a = it.registrazioneTrascritta()
            val b = it.registrazioneTrascritta()
            val eventi = registraEventi(it)
            it.modello.blocca()
            it.riassumi(a)
            attendiFinche(timeout = 10.seconds, messaggio = "a in corso") { it.modello.chiamate.get() == 1 }

            it.collaboratori.eliminaRegistrazione(EliminaRegistrazione(a)).atteso()
            attendiFinche(timeout = 10.seconds, messaggio = "il run di a annullato") { it.modello.esiti.isNotEmpty() }
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
        AmbienteProgetto(radice, estrattore = EstrattoreConMutex(mutex)).use {
            val a = it.registrazioneTrascritta()
            it.modello.blocca()
            it.riassumi(a)
            attendiFinche(timeout = 10.seconds, messaggio = "Riassunto in corso") { it.modello.chiamate.get() == 1 }

            val esito = runBlocking { it.parlanti.comandi.esegui(ComandoVoce.Nuovo(voce(a, 1), "Anna")) }

            assertEquals(Esito.Ok(Unit), esito)
            assertFalse(mutex.isLocked)
            assertTrue(it.modello.esiti.isEmpty(), "il Riassunto e ancora bloccato")
            it.modello.sblocca()
            it.attendiPronto(a)
        }
    }

    @Test
    fun `AC-S162 lo STOP annulla il Riassunto in corso anche con la riga presente e interruzioni ignorate`() {
        AmbienteProgetto(radice).use {
            val a = it.registrazioneTrascritta()
            it.modello.blocca()
            it.riassumi(a)
            attendiFinche(timeout = 10.seconds, messaggio = "Riassunto in corso") { it.modello.chiamate.get() == 1 }
            val coda = it.coda

            it.collaboratori.scope.cancel() // fermaEAttendi's contract: the caller cancels the scope first
            val inizio = System.nanoTime()
            val fermata = coda.fermaEAttendi(TIMEOUT_STOP_MS)

            assertTrue(fermata, "la coda si ferma entro il timeout")
            assertTrue((System.nanoTime() - inizio) / NANO_PER_MS < TIMEOUT_STOP_MS)
            assertEquals(Esito.Errore(ErroreApplicazioneSintesi.Annullato), it.modello.esiti.single())
            assertTrue(it.diRegistrazione(a).single().inCorso, "nulla scritto: il recupero lo marchera' interrotto")
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

    @Test
    fun `ADR 0032 su SQL reale il modello vede Voce n anche con un Nome, e la vista mostra il Nome e le rinomine`() {
        AmbienteProgetto(radice).use {
            val a = it.registrazioneTrascritta()
            val nomina = runBlocking { it.parlanti.comandi.esegui(ComandoVoce.Nuovo(voce(a, 1), "Anna")) }
            assertEquals(Esito.Ok(Unit), nomina)

            it.riassumi(a)
            it.attendiPronto(a)

            val ingresso = it.modello.richieste.single().ingresso
            assertFalse("Anna" in ingresso, "ADR 0032: nessun Nome nell'ingresso del modello")
            assertTrue("V1 = Voce 1" in ingresso, "la legenda resta 'Voce 1'")
            assertEquals(listOf("Anna"), nomiNelSommario(it, a))
            val parlanti = ParlanteRepositorySql(it.porte.database, it.porte.lettura)
            val anna = parlanti.delProgetto(it.progetto.progettoId).single().id
            it.parlanti.comandiParlante.rinomina(RinominaParlante(anna, "Annamaria")).atteso()
            assertEquals(listOf("Annamaria"), nomiNelSommario(it, a), "INV-S5: i nomi si leggono, mai salvati")
        }
    }

    private fun nomiNelSommario(ambiente: AmbienteProgetto, id: RegistrazioneId): List<String?> =
        checkNotNull(ambiente.sintesi.vista(id)?.mostrato?.sommario)
            .filterIsInstance<ParteTestoVista.Voce>()
            .filter { p -> p.voce.voceId == 1 }
            .map { p -> p.voce.nome }

    private fun registraEventi(ambiente: AmbienteProgetto): MutableList<EventoPubblicato> =
        CopyOnWriteArrayList<EventoPubblicato>().also { l ->
            ambiente.porte.dispatcher.registraDopoCommit { e -> l += e }
        }

    /** The start order of every queued item, by Registrazione (Riassunto and Elaborazione alike). */
    private fun registraAvvii(ambiente: AmbienteProgetto): MutableList<RegistrazioneId> =
        CopyOnWriteArrayList<RegistrazioneId>().also { l ->
            ambiente.porte.dispatcher.registraDopoCommit { e ->
                when (e) {
                    is RiassuntoAvviato -> l += checkNotNull(parteDi(e.incontroId))
                    is ElaborazioneAvviata -> l += e.registrazioneId
                    else -> Unit
                }
            }
        }

    /** A (late / duplicate) after-commit delivery of [evento] through the project's own dispatcher. */
    private fun consegna(ambiente: AmbienteProgetto, evento: EventoPubblicato) {
        val d = ambiente.porte.dispatcher
        d.unitaDiLavoro.inTransazione {
            d.pubblica(evento)
            Esito.Ok(Unit)
        }.atteso()
    }

    private fun registrazioneDi(e: EventoPubblicato): RegistrazioneId? = when (e) {
        is RiassuntoPronto -> parteDi(e.incontroId)
        is RiassuntoFallito -> parteDi(e.incontroId)
        is RiassuntoAvviato -> parteDi(e.incontroId)
        is RiassuntoEliminato -> parteDi(e.incontroId)
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
        const val ATTESA_PUBBLICAZIONE_S = 10L

        /** ADR 0030 §2: Sintesi → Parlanti → Trascrizione, each module's pairs in its own declared order. */
        val SINCRONI_DICHIARATI = listOf("AbbonatoProgettoSintesi") +
            List(5) { "AbbonatoRevisioneParlanti" } + "AbbonatoEliminazioneRegistrazione"
        const val TIMEOUT_STOP_MS = 5_000L
        const val NANO_PER_MS = 1_000_000L

        // richiestoAlle/avviatoAlle round-trip the SQL repositories at ms precision (AC-S146): one ms is
        // already enough to separate them deterministically via OrologioFinto.avanza, never a real sleep.
        val PASSO_OROLOGIO = 1.milliseconds

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
