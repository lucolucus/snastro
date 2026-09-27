package snastro.avvio.r3

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import snastro.avvio.CollaboratoriProgettoAperto
import snastro.avvio.ContestoEstensione
import snastro.avvio.EstensioneSessione
import snastro.avvio.GrafoR0
import snastro.avvio.SessioneProgettoImpl
import snastro.avvio.SessioneProgettoSeams
import snastro.avvio.orologioApp
import snastro.avvio.r1.AdattatoriMl
import snastro.avvio.r1.EstensioneR1
import snastro.avvio.r1.attendiFinche
import snastro.avvio.r2.AdattatoriParlanti
import snastro.avvio.r2.AmbienteR2
import snastro.avvio.r2.EstensioneR2
import snastro.avvio.r2.GrafoR2
import snastro.avvio.r2.lettoreNomiDaParlanti
import snastro.kernel.CampioniAudio
import snastro.kernel.Esito
import snastro.kernel.GeneratoreIdFinto
import snastro.kernel.GeneratoreIdUuid
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.kernel.atteso
import snastro.parlanti.applicazione.porte.EstrattoreImpronta
import snastro.parlanti.applicazione.porte.EstrattoreImprontaFinta
import snastro.persistenza.UnitaDiLavoroSql
import snastro.progetto.applicazione.comandi.AggiungiRegistrazione
import snastro.progetto.applicazione.letture.ElencoProgetti
import snastro.progetto.applicazione.porte.InfoAudio
import snastro.progetto.applicazione.porte.RegistroProgettiFinta
import snastro.progetto.applicazione.porte.SondaAudioFinta
import snastro.sintesi.adattatori.persistenza.RiassuntoRepositorySql
import snastro.sintesi.applicazione.porte.AzioneRisposta
import snastro.sintesi.applicazione.porte.DisponibilitaModelloLinguisticoFinta
import snastro.sintesi.applicazione.porte.ElementoRisposta
import snastro.sintesi.applicazione.porte.ErroreApplicazioneSintesi
import snastro.sintesi.applicazione.porte.ModelloLinguistico
import snastro.sintesi.applicazione.porte.PuntoChiaveRisposta
import snastro.sintesi.applicazione.porte.RichiestaRiassunto
import snastro.sintesi.applicazione.porte.RispostaModello
import snastro.sintesi.applicazione.porte.StatoModelloLinguistico
import snastro.sintesi.dominio.Riassunto
import snastro.trascrizione.applicazione.comandi.AvviaElaborazione
import snastro.trascrizione.applicazione.letture.StatoElaborazioneVista
import snastro.trascrizione.applicazione.porte.DecodificatoreAudioFinta
import snastro.trascrizione.applicazione.porte.Diarizzatore
import snastro.trascrizione.applicazione.porte.RiconoscitoreParlatoFinta
import snastro.trascrizione.applicazione.porte.Turno
import snastro.trascrizione.applicazione.porte.VadFinta
import snastro.trascrizione.dominio.NumeroPersone
import snastro.ui.ApriEsternoFinta
import snastro.ui.ProgettoAperto
import snastro.ui.modelli.ServizioModelliFinta
import snastro.ui.modelli.StatoModelli
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import snastro.parlanti.applicazione.porte.DecodificatoreAudioFinta as DecodificatoreParlantiFinta

/**
 * The REAL R3 composition ([SessioneProgettoImpl] + [EstensioneR3] over [EstensioneR2] over [EstensioneR1], SQLite
 * project FILE on disk opened by the session with the production driver, real shared queue and its worker thread,
 * real Sintesi/Parlanti/Trascrizione SQL repositories and subscribers) with only the non-headless edges faked: the
 * FFmpeg probe/decoders, the sherpa ML Finte, the print [estrattore], the LLM ([modello]) and the model's
 * availability ([disponibilita], `Installato` by default).
 */
internal class AmbienteR3(
    radice: Path,
    val modello: ModelloLinguisticoDiProva = ModelloLinguisticoDiProva(),
    val disponibilita: DisponibilitaModelloLinguisticoFinta =
        DisponibilitaModelloLinguisticoFinta(StatoModelloLinguistico.Installato),
    val diarizzatore: DiarizzatoreScriptato = DiarizzatoreScriptato(),
    estrattore: EstrattoreImpronta = EstrattoreImprontaFinta(),
) : AutoCloseable {
    private val sorgenti = mutableMapOf<RiferimentoAudio, Long>()
    private val sorgente: Path = radice.resolve("riunione.wav").also { Files.write(it, ByteArray(DIMENSIONE_SORGENTE)) }
    private val esecutoreUi = Executors.newSingleThreadExecutor { r -> Thread(r, "ui-di-prova") }
    private val esecutoreIo = Executors.newFixedThreadPool(THREAD_IO) { r -> Thread(r, "io-di-prova") }
    private val contesti = CopyOnWriteArrayList<ContestoEstensione>()

    val dispatcherUi = esecutoreUi.asCoroutineDispatcher()
    private val dispatcherIo = esecutoreIo.asCoroutineDispatcher()
    val scope = CoroutineScope(SupervisorJob() + dispatcherUi)

    /** How many synchronous subscribers the dispatcher had when R3 handed the context to R2 / when `apri` returned. */
    @Volatile var sincroniPrimaDiR2: Int = -1
        private set

    @Volatile var sincroniAllApertura: Int = -1
        private set

    private val estensioneR2 = EstensioneR2(
        r1 = EstensioneR1(
            io = Dispatchers.IO,
            clock = orologioApp(),
            generatoreId = GeneratoreIdFinto(),
            adattatoriMl = { AdattatoriMl(diarizzatore, RiconoscitoreParlatoFinta(), VadFinta()) },
            modelliPronti = { true },
            decodificatore = { DecodificatoreAudioFinta(sorgenti) },
            lettoreNomi = ::lettoreNomiDaParlanti,
        ),
        io = Dispatchers.IO,
        clock = orologioApp(),
        generatoreId = GeneratoreIdUuid(),
        adattatori = { AdattatoriParlanti(estrattore, { DecodificatoreParlantiFinta() }, proposte = true) },
    )

    private val estensione = EstensioneR3(
        r2 = EstensioneSessione { contesto ->
            sincroniPrimaDiR2 = contaSincroni(contesto)
            estensioneR2.apri(contesto)
        },
        clock = orologioApp(),
        generatoreId = GeneratoreIdUuid(),
        modello = modello,
        disponibilita = disponibilita,
    )

    val sessione = SessioneProgettoImpl(
        registro = RegistroProgettiFinta(),
        generatoreId = GeneratoreIdFinto(),
        clock = orologioApp(),
        scopeGenitore = scope,
        seams = SessioneProgettoSeams(
            sondaAudio = {
                SondaAudioFinta(mapOf(sorgente.toString() to InfoAudio(DURATA_MS, LocalDate.parse("2026-01-01"))))
            },
        ),
        estensione = EstensioneSessione { contesto ->
            contesti += contesto
            estensione.apri(contesto).also { sincroniAllApertura = contaSincroni(contesto) }
        },
    )

    val progetto: ProgettoAperto = sessione.crea(radice.resolve("progetti").toString(), "Prova").atteso()

    val collaboratori: CollaboratoriProgettoAperto get() = checkNotNull(sessione.collaboratoriCorrenti())
    val r3: CollaboratoriR3 get() = collaboratori.estensione as CollaboratoriR3
    val contesto: ContestoEstensione get() = contesti.last()
    val riassunti: RiassuntoRepositorySql get() = RiassuntoRepositorySql(contesto.database)

    val grafo: GrafoR2
        get() = GrafoR2(
            GrafoR0(
                scope = scope,
                io = dispatcherIo,
                clock = orologioApp(),
                sessione = sessione,
                elencoProgetti = ElencoProgetti(RegistroProgettiFinta()),
                cartellaProgettiPredefinita = progetto.percorso,
            ),
            ServizioModelliFinta(StatoModelli.Pronti),
            ApriEsternoFinta(),
        )

    /** Imports the test source through the REAL AggiungiRegistrazione and transcribes it; returns the NEW one. */
    fun registrazioneTrascritta(): RegistrazioneId {
        val prima = collaboratori.registrazioni().map { it.registrazioneId }.toSet()
        collaboratori.aggiungiRegistrazione(AggiungiRegistrazione(sorgente.toString())).atteso()
        val id = collaboratori.registrazioni().map { it.registrazioneId }.single { it !in prima }
        sorgenti[RiferimentoAudio("audio/${id.valore}.wav")] = DURATA_MS
        avviaElaborazione(id)
        attendiFinche(messaggio = "elaborazione completata") { stato(id) == StatoElaborazioneVista.COMPLETATA }
        return id
    }

    fun avviaElaborazione(id: RegistrazioneId) {
        r3.r2.avviaElaborazione(AvviaElaborazione(id)).atteso()
    }

    fun stato(id: RegistrazioneId): StatoElaborazioneVista = r3.r2.r1.statiElaborazione(listOf(id)).single().stato

    fun riassumi(id: RegistrazioneId, argomento: String? = null) {
        r3.riassumi(id, argomento).atteso()
    }

    /** In ONE read transaction (a consistent snapshot while the worker may be completing a Riassunto). */
    fun diRegistrazione(id: RegistrazioneId): List<Riassunto> =
        UnitaDiLavoroSql(contesto.database).inTransazione { Esito.Ok(riassunti.diRegistrazione(id)) }.atteso()

    fun attendiPronto(id: RegistrazioneId) =
        attendiFinche(messaggio = "Riassunto pronto") { diRegistrazione(id).singleOrNull()?.pronto == true }

    override fun close() {
        modello.sblocca()
        scope.cancel()
        listOf(esecutoreUi, esecutoreIo).forEach { e ->
            e.shutdown()
            e.awaitTermination(ATTESA_CHIUSURA_S, TimeUnit.SECONDS)
        }
        sessione.chiudi()
    }

    companion object {
        const val DURATA_MS = AmbienteR2.DURATA_MS
        private const val THREAD_IO = 4
        private const val DIMENSIONE_SORGENTE = 64
        private const val ATTESA_CHIUSURA_S = 5L

        /** ADR 0023 "dispatcher inspection" (AC-S143): the kernel dispatcher's own synchronous subscriber list. */
        fun contaSincroni(contesto: ContestoEstensione): Int {
            val campo = contesto.dispatcher.javaClass.getDeclaredField("sincroni").apply { isAccessible = true }
            return (campo.get(contesto.dispatcher) as List<*>).size
        }
    }
}

/**
 * The LLM bound for the tests (ADR 0021 §4's `ModelloLinguisticoFinto` role over the real composition): answers
 * [risposta]; while [blocca] holds, it waits on a latch IGNORING thread interrupts and polling only `annullato()`
 * (AC-S149/S161/S162) — it returns `Errore(Annullato)` as soon as `annullato()` reads true.
 */
internal class ModelloLinguisticoDiProva(
    @Volatile var risposta: Esito<RispostaModello> = Esito.Ok(RISPOSTA),
) : ModelloLinguistico {
    @Volatile private var latch: CountDownLatch? = null
    val chiamate = AtomicInteger()
    val richieste: MutableList<RichiestaRiassunto> = CopyOnWriteArrayList()
    val thread: MutableList<Thread> = CopyOnWriteArrayList()
    val esiti: MutableList<Esito<RispostaModello>> = CopyOnWriteArrayList()

    /** Runs on the queue's worker at the start of every call (what the test observes at claim time). */
    @Volatile var primaDellaRisposta: (RichiestaRiassunto) -> Unit = {}

    fun blocca() {
        latch = CountDownLatch(1)
    }

    fun sblocca() {
        latch?.countDown()
    }

    override fun riassumi(richiesta: RichiestaRiassunto, annullato: () -> Boolean): Esito<RispostaModello> {
        primaDellaRisposta(richiesta)
        richieste += richiesta
        thread += Thread.currentThread()
        chiamate.incrementAndGet()
        val attesa = latch
        var interrotto = false
        var esito: Esito<RispostaModello>? = null
        while (attesa != null && esito == null) {
            if (annullato()) esito = Esito.Errore(ErroreApplicazioneSintesi.Annullato)
            try {
                if (attesa.await(PASSO_MS, TimeUnit.MILLISECONDS)) break
            } catch (e: InterruptedException) {
                interrotto = true // ignored on purpose: an LLM run may not honour an interrupt (ADR 0023 §5)
            }
        }
        if (interrotto) Thread.currentThread().interrupt()
        val finale = esito ?: if (annullato()) Esito.Errore(ErroreApplicazioneSintesi.Annullato) else risposta
        esiti += finale
        return finale
    }

    companion object {
        private const val PASSO_MS = 10L

        /** Valid over [AmbienteR2.DUE_VOCI]'s Trascritto (Segmenti 1..2, Voci 1..2). */
        val RISPOSTA = RispostaModello(
            sommario = "{V1} e {V2} fanno il punto.",
            decisioni = listOf(ElementoRisposta("Si parte dal primo punto.", listOf(1))),
            questioniAperte = listOf(ElementoRisposta("Quando rivedersi.", listOf(2))),
            azioni = listOf(AzioneRisposta("{V2} manda il riepilogo.", listOf(2), responsabile = 2)),
            puntiChiave = listOf(PuntoChiaveRisposta("Il ritmo.", listOf(1, 2), parlante = 1)),
        )
    }
}

/** A scripted [Diarizzatore]: [turni] (default two Voci); [barriera] holds a run; [fallisci] makes it throw. */
internal class DiarizzatoreScriptato : Diarizzatore {
    @Volatile var turni: List<Turno> = AmbienteR2.DUE_VOCI

    @Volatile var barriera: CountDownLatch? = null

    @Volatile var fallisci: Boolean = false

    override fun diarizza(c: CampioniAudio, numeroPersone: NumeroPersone?): List<Turno> {
        barriera?.await()
        check(!fallisci) { "diarizzazione fallita (finta)" }
        return turni
    }
}
