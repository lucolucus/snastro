package snastro.ui.registrazione

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import snastro.kernel.ElaborazioneId
import snastro.kernel.Esito
import snastro.kernel.ParlanteId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.parlanti.applicazione.letture.VoceIdentificata
import snastro.parlanti.dominio.ErroreParlanti
import snastro.trascrizione.applicazione.comandi.ConfermaSegmento
import snastro.trascrizione.applicazione.letture.SegmentoTrascrittoView
import snastro.trascrizione.applicazione.letture.StatoElaborazioneVista
import snastro.trascrizione.applicazione.letture.StatoRegistrazioneVista
import snastro.trascrizione.applicazione.letture.TrascrittoView
import snastro.trascrizione.applicazione.letture.VoceTrascrittoView
import snastro.ui.Cambiamento
import snastro.ui.lettore.StatoLettore
import snastro.ui.testi.AVVISO_TUTTA_LA_VOCE
import snastro.ui.testi.SUGGERIMENTO_RIFERIMENTI_INSUFFICIENTI
import snastro.ui.testi.messaggioPer
import java.time.Clock
import java.time.LocalDate
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

private const val SOGLIA = RegistrazionePresenter.SOGLIA_ATTESA_VISIBILE_MS
private val V4 = VoceId(4)

private fun frase(n: Int, voce: VoceId, inizio: Long, fine: Long, confermato: Boolean = false) =
    SegmentoTrascrittoView(SegmentoId(n), voce, inizio, fine, "Frase $n.", confermato)

/**
 * Durations >= 1 s (the reference threshold). Voce 1 → Marco: Segmento 1 CONFIRMED, Segmento 4 short;
 * Voce 2 → Giulia: Segmento 2 (no confirmed one: "tutta la voce"); Voce 3 unattributed: 3 and 5.
 */
private fun trascrittoRiferimenti(): TrascrittoView {
    val segmenti = listOf(
        frase(1, V1, 0, 2_000, confermato = true),
        frase(2, V2, 2_000, 4_000),
        frase(3, V3, 4_000, 6_000),
        frase(4, V1, 6_000, 6_500),
        frase(5, V3, 6_500, 8_000),
    )
    return TrascrittoView(
        REG,
        "Seduta",
        LocalDate.of(2026, 3, 12),
        8_000,
        segmenti,
        listOf(V1, V2, V3).map { VoceTrascrittoView(it, "Voce ${it.numero}") },
    )
}

private val IDENTIFICATE = listOf(
    VoceIdentificata(V1, MARCO.parlanteId, MARCO.nome, MARCO.tipoParlante),
    VoceIdentificata(V2, GIULIA.parlanteId, GIULIA.nome, GIULIA.tipoParlante),
    VoceIdentificata(V3),
)

private val GRUPPI = listOf(
    GruppoSpostamenti(V3, V2, 1),
    GruppoSpostamenti(V3, V1, 2),
    GruppoSpostamenti(V4, V1, 1),
)

private fun statoVista(stato: StatoElaborazioneVista) = StatoRegistrazioneVista(
    registrazioneId = REG,
    stato = stato,
    fase = null,
    avviataAlle = null,
    motivoFallimento = null,
    posizioneInCoda = null,
    numVoci = 3,
    numeroPersone = null,
    trascrittoDisponibile = true,
    elaborazioneId = ElaborazioneId("elaborazione-1"),
)

/**
 * ADR 0019 §5/§6 + Amendment (b) on the S3 presenter: 'Dai un nome a questa frase', the pin and 'Togli
 * conferma', and 'Riassegna per somiglianza' compute → preview → apply over [AzioniSomiglianzaFinta]
 * (AC-526..AC-536, AC-545..AC-548), virtual time for the ADR 0017 waits.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LargeClass", "TooManyFunctions") // one test per tests_nl item
class RegistrazioneSomiglianzaTest {
    private fun TestScope.ambiente() = AmbienteVoci(
        CoroutineScope(StandardTestDispatcher(testScheduler)),
        OrologioVirtuale(testScheduler),
        vista = trascrittoRiferimenti(),
        identificate = IDENTIFICATE,
    )

    private fun TestScope.avvia(a: AmbienteVoci, conStati: Boolean = false): RegistrazionePresenter {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return a.presenter(CoroutineScope(dispatcher), dispatcher, conStati)
    }

    private val RegistrazionePresenter.dati get() = assertIs<RegistrazioneUiStato.Dati>(stato.value)
    private val RegistrazionePresenter.somiglianza get() = assertNotNull(dati.pannello?.somiglianza)
    private val RegistrazionePresenter.fase get() = somiglianza.fase

    private fun RegistrazionePresenter.riga(n: Int) = dati.segmenti.single { it.segmentoId == SegmentoId(n) }

    private fun TestScope.inAnteprima(a: AmbienteVoci, gruppi: List<GruppoSpostamenti> = GRUPPI, incerte: Int = 2):
        RegistrazionePresenter {
        a.somiglianza.gruppi = gruppi
        a.somiglianza.incerte = incerte
        val presenter = avvia(a)
        advanceUntilIdle()
        presenter.azioni.calcolaSomiglianza()
        advanceUntilIdle()
        return presenter
    }

    /** No editing action sends anything (AC-531/AC-545), playback and '▶ estratto' still do. */
    private fun TestScope.verificaModificheBloccate(a: AmbienteVoci, presenter: RegistrazionePresenter) {
        val d = presenter.dati
        assertTrue(assertNotNull(d.pannello).carte.none { it.azioniAbilitate })
        assertFalse(assertNotNull(d.pannello).unioneAbilitata)
        presenter.azioni.conferma(V3)
        presenter.azioni.confermaParlante(V3, MARCO.parlanteId)
        presenter.azioni.salta(V3)
        presenter.azioni.unisci(V1, V3)
        presenter.azioni.selezionaSegmento(SegmentoId(3))
        assertFalse(assertNotNull(presenter.dati.barraSelezione).abilitata)
        assertFalse(assertNotNull(presenter.dati.barraSelezione?.frase).abilitata)
        presenter.azioni.riassegnaA(V1)
        presenter.azioni.dividiVoce()
        presenter.azioni.nominaFrase(ObiettivoNome.Esistente(MARCO.parlanteId))
        presenter.azioni.deseleziona()
        presenter.azioni.riproduciSegmento(SegmentoId(2))
        runCurrent()
        assertEquals(StatoLettore(REG, 2_000, inRiproduzione = true), a.lettore.stato.value)
        presenter.azioni.riproduciEstrattoVoce(V1)
        runCurrent()
        assertEquals(StatoLettore(REG, 0, inRiproduzione = true), a.lettore.stato.value)
        assertTrue(a.comandi.eseguiti.isEmpty() && a.comandi.frasi.isEmpty())
        assertTrue(a.revisioni.isEmpty())
    }

    // --- 'Dai un nome a questa frase', pin, 'Togli conferma' ----------------------------------------

    @Test
    fun `AC-526 con UNA frase il menu elenca i Parlanti attivi, assente con 0 o 2, disabilitato in sola lettura`() =
        runTest {
            val a = ambiente().apply { statoElaborazione = statoVista(StatoElaborazioneVista.COMPLETATA) }
            val presenter = avvia(a, conStati = true)
            advanceUntilIdle()
            assertNull(presenter.dati.barraSelezione)
            presenter.azioni.selezionaSegmento(SegmentoId(3))
            val menu = assertNotNull(presenter.dati.barraSelezione?.frase)
            assertEquals(listOf(GIULIA, MARCO), menu.parlanti)
            assertTrue(menu.abilitata)
            presenter.azioni.selezionaSegmento(SegmentoId(5))
            assertNull(assertNotNull(presenter.dati.barraSelezione).frase)

            a.statoElaborazione = statoVista(StatoElaborazioneVista.IN_ATTESA)
            a.aggiornamenti.emetti(Cambiamento(REG))
            advanceUntilIdle()
            presenter.azioni.selezionaSegmento(SegmentoId(3))
            assertFalse(assertNotNull(presenter.dati.barraSelezione?.frase).abilitata)
            presenter.azioni.nominaFrase(ObiettivoNome.Esistente(MARCO.parlanteId))
            runCurrent()
            assertTrue(a.comandi.frasi.isEmpty())
        }

    @Test
    fun `AC-527 la decisione dei passi segue la tabella di ADR 0019 5`() {
        val v = trascrittoRiferimenti()
        val id = IDENTIFICATE.associateBy { it.voceId }
        val marco = ObiettivoNome.Esistente(MARCO.parlanteId)
        val giulia = ObiettivoNome.Esistente(GIULIA.parlanteId)
        val anna = ObiettivoNome.Esistente(ParlanteId("p-anna"))
        val nuovo = ObiettivoNome.Nuovo("Dario", ricorrente = true)
        // (a) on a Voce of P
        assertEquals(PassiNominaFrase.SoloConferma, passiNominaFrase(SegmentoId(4), v, id, marco))
        // (b) alone in its Voce (Voce 2 has only Segmento 2): attribute that Voce, no move
        assertEquals(PassiNominaFrase.AttribuisciVoce(V2, marco), passiNominaFrase(SegmentoId(2), v, id, marco))
        assertEquals(PassiNominaFrase.AttribuisciVoce(V2, nuovo), passiNominaFrase(SegmentoId(2), v, id, nuovo))
        // (c) P already has a Voce here → its lowest voceId
        assertEquals(PassiNominaFrase.Sposta(V2), passiNominaFrase(SegmentoId(3), v, id, giulia))
        // (d) otherwise
        assertEquals(PassiNominaFrase.NuovaVoce(anna), passiNominaFrase(SegmentoId(3), v, id, anna))
        assertEquals(PassiNominaFrase.NuovaVoce(nuovo), passiNominaFrase(SegmentoId(3), v, id, nuovo))
        assertNull(passiNominaFrase(SegmentoId(99), v, id, marco))
    }

    @Test
    fun `AC-527 ogni caso passa da ComandiVoce nominaFrase, mai da un comando diretto`() = runTest {
        val a = ambiente()
        val presenter = avvia(a)
        advanceUntilIdle()
        presenter.azioni.selezionaSegmento(SegmentoId(3))
        presenter.azioni.nominaFrase(ObiettivoNome.Esistente(MARCO.parlanteId))
        advanceUntilIdle()
        val attesa: Pair<FraseRef, PassiNominaFrase> = FraseRef(REG, SegmentoId(3)) to PassiNominaFrase.Sposta(V1)
        assertEquals(listOf(attesa), a.comandi.frasi.toList())
        assertTrue(a.revisioni.isEmpty())
        assertEquals(V1, presenter.riga(3).voceId)
        assertTrue(presenter.riga(3).confermato)
        assertEquals("Marco", presenter.riga(3).etichettaVoce)
    }

    @Test
    fun `AC-528 una frase confermata ha la puntina e da sola offre Togli conferma che invia ConfermaSegmento false`() =
        runTest {
            val a = ambiente()
            val presenter = avvia(a)
            advanceUntilIdle()
            assertTrue(presenter.riga(1).confermato)
            assertFalse(presenter.riga(3).confermato)
            presenter.azioni.selezionaSegmento(SegmentoId(1))
            assertTrue(assertNotNull(presenter.dati.barraSelezione?.frase).confermato)
            presenter.azioni.togliConferma()
            advanceUntilIdle()
            assertEquals(listOf<Any>(ConfermaSegmento(REG, SegmentoId(1), confermato = false)), a.revisioni)
            assertFalse(presenter.riga(1).confermato)
        }

    @Test
    fun `AC-529 nominaFrase in corso mostra la riga in corso, oltre la soglia In attesa con Annulla`() = runTest {
        val a = ambiente()
        a.comandi.trattieni = true
        val presenter = avvia(a)
        advanceUntilIdle()
        presenter.azioni.selezionaSegmento(SegmentoId(3))
        presenter.azioni.nominaFrase(ObiettivoNome.Nuovo("Dario", ricorrente = true))
        assertEquals(AttesaComando.IN_CORSO, presenter.riga(3).attesaFrase)
        assertFalse(assertNotNull(presenter.dati.barraSelezione?.frase).abilitata)
        advanceTimeBy(SOGLIA + 1)
        runCurrent()
        assertEquals(AttesaComando.IN_ATTESA, presenter.riga(3).attesaFrase)
        presenter.azioni.annullaFrase(SegmentoId(3))
        advanceUntilIdle()
        assertNull(presenter.riga(3).attesaFrase)
        assertNull(presenter.dati.errore)
    }

    @Test
    fun `AC-529 un errore del passo d e un messaggio semplice e la nuova Voce resta senza nome`() = runTest {
        val a = ambiente()
        a.esitoFrase = { Esito.Errore(ErroreParlanti.NomeGiaInUso("Marco")) }
        val presenter = avvia(a)
        advanceUntilIdle()
        presenter.azioni.selezionaSegmento(SegmentoId(3))
        presenter.azioni.nominaFrase(ObiettivoNome.Nuovo("Marco", ricorrente = true))
        advanceUntilIdle()
        assertEquals(messaggioPer(ErroreParlanti.NomeGiaInUso("Marco")), presenter.dati.errore)
        assertNull(presenter.riga(3).attesaFrase)
    }

    // --- 'Riassegna per somiglianza': enabling rule -------------------------------------------------

    @Test
    fun `AC-530 abilitato con due persone di riferimento, righe per modalita e avviso tutta la voce`() = runTest {
        val a = ambiente()
        val presenter = avvia(a)
        advanceUntilIdle()
        val s = presenter.somiglianza
        assertTrue(s.abilitato)
        assertNull(s.suggerimento)
        assertEquals("Riferimenti: Marco (frasi confermate) · Giulia (tutta la voce)", s.riferimenti)
        assertEquals(AVVISO_TUTTA_LA_VOCE, s.avvisoTuttaLaVoce)
        assertNull(s.nonToccate)
        assertEquals(FaseSomiglianza.Inattiva, s.fase)
    }

    @Test
    fun `AC-530 una persona senza frasi di almeno 1 s e non toccata e il pulsante resta disabilitato`() = runTest {
        val a = ambiente()
        a.vista = a.vista.copy(segmenti = a.vista.segmenti.map { if (it.voceId == V2) it.copy(fineMs = 2_900) else it })
        val presenter = avvia(a)
        advanceUntilIdle()
        val s = presenter.somiglianza
        assertFalse(s.abilitato)
        assertEquals(SUGGERIMENTO_RIFERIMENTI_INSUFFICIENTI, s.suggerimento)
        assertEquals("Riferimenti: Marco (frasi confermate)", s.riferimenti)
        assertNull(s.avvisoTuttaLaVoce)
        assertEquals("Non toccate: Giulia", s.nonToccate)
        presenter.azioni.calcolaSomiglianza()
        advanceUntilIdle()
        assertTrue(a.somiglianza.calcoli.isEmpty())
    }

    @Test
    fun `AC-530 disabilitato in sola lettura, con un comando o una nominaFrase in corso`() = runTest {
        val a = ambiente().apply { statoElaborazione = statoVista(StatoElaborazioneVista.IN_CORSO) }
        val presenter = avvia(a, conStati = true)
        advanceUntilIdle()
        assertFalse(presenter.somiglianza.abilitato)

        a.statoElaborazione = statoVista(StatoElaborazioneVista.COMPLETATA)
        a.aggiornamenti.emetti(Cambiamento(REG))
        advanceUntilIdle()
        assertTrue(presenter.somiglianza.abilitato)

        a.comandi.trattieni = true
        presenter.azioni.salta(V3)
        runCurrent()
        assertFalse(presenter.somiglianza.abilitato)
        a.comandi.rilascia(ref(V3))
        advanceUntilIdle()
        assertTrue(presenter.somiglianza.abilitato)

        presenter.azioni.selezionaSegmento(SegmentoId(5))
        presenter.azioni.nominaFrase(ObiettivoNome.Esistente(MARCO.parlanteId))
        runCurrent()
        assertFalse(presenter.somiglianza.abilitato)
    }

    // --- computing ----------------------------------------------------------------------------------

    @Test
    fun `AC-531 durante il confronto avanzamento n di N, modifiche disabilitate, attesa oltre la soglia`() = runTest {
        val a = ambiente()
        a.somiglianza.trattieni = true
        val presenter = avvia(a)
        advanceUntilIdle()
        presenter.azioni.calcolaSomiglianza()
        advanceUntilIdle()
        assertEquals(FaseSomiglianza.Calcolo("Confronto le frasi…", 0, 0, inAttesa = true), presenter.fase)
        a.somiglianza.avanza(REG, 3, 10)
        runCurrent()
        assertEquals(FaseSomiglianza.Calcolo("Confronto le frasi… 3 di 10", 3, 10, inAttesa = false), presenter.fase)
        assertFalse(presenter.somiglianza.abilitato)
        verificaModificheBloccate(a, presenter)
        advanceTimeBy(SOGLIA - 1)
        runCurrent()
        assertFalse(assertIs<FaseSomiglianza.Calcolo>(presenter.fase).inAttesa)
        advanceTimeBy(2)
        runCurrent()
        assertTrue(assertIs<FaseSomiglianza.Calcolo>(presenter.fase).inAttesa)
    }

    @Test
    fun `AC-532 Annulla durante il confronto chiama annulla e il pannello torna com era senza errore`() = runTest {
        val a = ambiente()
        a.somiglianza.trattieni = true
        val presenter = avvia(a)
        advanceUntilIdle()
        val prima = presenter.dati.pannello
        presenter.azioni.calcolaSomiglianza()
        advanceUntilIdle()
        presenter.azioni.annullaSomiglianza()
        advanceUntilIdle()
        assertEquals(listOf(REG), a.somiglianza.annullamenti)
        assertEquals(prima, presenter.dati.pannello)
        assertNull(presenter.dati.errore)
    }

    // --- preview + apply ----------------------------------------------------------------------------

    @Test
    fun `AC-545 anteprima con titolo, una riga per gruppo ordinata per destinazione e nomi, modifiche bloccate`() =
        runTest {
            val a = ambiente()
            val presenter = inAnteprima(a)
            val anteprima = assertIs<FaseSomiglianza.Anteprima>(presenter.fase)
            assertEquals("Sposterò 4 frasi, 2 incerte restano dove sono", anteprima.titolo)
            assertEquals(listOf("Voce 3 → Marco: 2", "Voce 4 → Marco: 1", "Voce 3 → Giulia: 1"), anteprima.righe)
            assertTrue(anteprima.applicabile)
            assertFalse(anteprima.inApplicazione)
            verificaModificheBloccate(a, presenter)
        }

    @Test
    fun `AC-545 singolari e N zero con solo Chiudi`() = runTest {
        val uno = inAnteprima(ambiente(), listOf(GruppoSpostamenti(V3, V1, 1)), incerte = 1)
        assertEquals("Sposterò 1 frase, 1 incerta resta dove è", assertIs<FaseSomiglianza.Anteprima>(uno.fase).titolo)
        val zero = assertIs<FaseSomiglianza.Anteprima>(inAnteprima(ambiente(), emptyList(), incerte = 3).fase)
        assertEquals("Nessuna frase da spostare (3 incerte restano dove sono)", zero.titolo)
        assertFalse(zero.applicabile)
    }

    @Test
    fun `AC-546 Applica chiama applica una volta sola, poi i pulsanti sono disabilitati`() = runTest {
        val a = ambiente()
        val presenter = inAnteprima(a)
        a.somiglianza.trattieni = true
        presenter.azioni.applicaSomiglianza()
        presenter.azioni.applicaSomiglianza()
        assertTrue(assertIs<FaseSomiglianza.Anteprima>(presenter.fase).inApplicazione)
        advanceUntilIdle()
        assertEquals(listOf(REG), a.somiglianza.applicazioni)
        val inApplicazione = assertIs<FaseSomiglianza.Anteprima>(presenter.fase)
        assertTrue(inApplicazione.inApplicazione)
        presenter.azioni.annullaSomiglianza()
        advanceUntilIdle()
        assertTrue(a.somiglianza.annullamenti.isEmpty())
        a.somiglianza.concludiApplicazione(REG)
        advanceUntilIdle()
        assertEquals(FaseSomiglianza.Esito("4 frasi spostate, 2 incerte (rimaste dov'erano)"), presenter.fase)
    }

    @Test
    fun `AC-546 Annulla sull anteprima e Chiudi con N zero chiamano annulla senza messaggi ne comandi`() = runTest {
        val a = ambiente()
        val presenter = inAnteprima(a)
        presenter.azioni.annullaSomiglianza()
        advanceUntilIdle()
        assertEquals(FaseSomiglianza.Inattiva, presenter.fase)
        assertNull(presenter.dati.errore)

        val b = ambiente()
        val zero = inAnteprima(b, emptyList(), incerte = 1)
        zero.azioni.applicaSomiglianza()
        zero.azioni.annullaSomiglianza()
        advanceUntilIdle()
        assertTrue(b.somiglianza.applicazioni.isEmpty())
        assertEquals(listOf(REG), b.somiglianza.annullamenti)
        assertEquals(FaseSomiglianza.Inattiva, zero.fase)
        assertTrue(a.comandi.eseguiti.isEmpty() && a.revisioni.isEmpty())
    }

    @Test
    fun `AC-533 testi dell esito, errori e chiusura del messaggio`() = runTest {
        val a = ambiente()
        val presenter = inAnteprima(a, listOf(GruppoSpostamenti(V3, V1, 1)), incerte = 1)
        presenter.azioni.applicaSomiglianza()
        advanceUntilIdle()
        assertEquals(FaseSomiglianza.Esito("1 frase spostata, 1 incerta (rimasta dov'era)"), presenter.fase)
        presenter.azioni.annullaSomiglianza()
        advanceUntilIdle()
        assertEquals(FaseSomiglianza.Inattiva, presenter.fase)

        a.somiglianza.erroreApplica = ErroreSomiglianzaUi.Altro("Impossibile leggere l'audio.")
        presenter.azioni.calcolaSomiglianza()
        advanceUntilIdle()
        presenter.azioni.applicaSomiglianza()
        advanceUntilIdle()
        assertEquals(FaseSomiglianza.Errore("Impossibile leggere l'audio.", ricalcola = false), presenter.fase)
    }

    @Test
    fun `AC-547 TrascrittoCambiato dopo Applica mostra il testo e Ricalcola che ricalcola, mai la vecchia anteprima`() =
        runTest {
            val a = ambiente()
            val presenter = inAnteprima(a)
            a.somiglianza.erroreApplica = ErroreSomiglianzaUi.TrascrittoCambiato
            presenter.azioni.applicaSomiglianza()
            advanceUntilIdle()
            val errore = assertIs<FaseSomiglianza.Errore>(presenter.fase)
            assertEquals("La trascrizione è cambiata dopo il confronto: ricalcola l'anteprima", errore.testo)
            assertTrue(errore.ricalcola)
            assertTrue(presenter.somiglianza.abilitato)
            assertTrue(a.comandi.eseguiti.isEmpty() && a.revisioni.isEmpty())

            a.somiglianza.gruppi = listOf(GruppoSpostamenti(V3, V2, 2))
            a.somiglianza.trattieni = true
            presenter.azioni.calcolaSomiglianza()
            advanceUntilIdle()
            assertEquals(2, a.somiglianza.calcoli.size)
            assertIs<FaseSomiglianza.Calcolo>(presenter.fase)
            a.somiglianza.concludi(REG)
            advanceUntilIdle()
            assertEquals(listOf("Voce 3 → Giulia: 2"), assertIs<FaseSomiglianza.Anteprima>(presenter.fase).righe)
        }

    @Test
    fun `AC-533 RiferimentiInsufficienti e un messaggio semplice senza Ricalcola`() = runTest {
        val a = ambiente()
        a.somiglianza.errore = ErroreSomiglianzaUi.RiferimentiInsufficienti
        val presenter = avvia(a)
        advanceUntilIdle()
        presenter.azioni.calcolaSomiglianza()
        advanceUntilIdle()
        assertEquals(FaseSomiglianza.Errore(SUGGERIMENTO_RIFERIMENTI_INSUFFICIENTI, ricalcola = false), presenter.fase)
    }

    // --- per-project state, read-only, threads ----------------------------------------------------

    @Test
    fun `AC-534 un presenter ricreato durante il confronto lo mostra ancora con il suo avanzamento`() = runTest {
        val a = ambiente()
        a.somiglianza.trattieni = true
        val schermata = CoroutineScope(StandardTestDispatcher(testScheduler))
        val primo = a.presenter(schermata, StandardTestDispatcher(testScheduler))
        advanceUntilIdle()
        primo.azioni.calcolaSomiglianza()
        advanceUntilIdle()
        a.somiglianza.avanza(REG, 4, 9)
        schermata.cancel()
        val secondo = avvia(a)
        advanceUntilIdle()
        assertEquals("Confronto le frasi… 4 di 9", assertIs<FaseSomiglianza.Calcolo>(secondo.fase).testo)
        assertTrue(a.somiglianza.annullamenti.isEmpty())
    }

    @Test
    fun `AC-548 un presenter ricreato in anteprima mostra la stessa anteprima`() = runTest {
        val a = ambiente()
        val primo = inAnteprima(a)
        val attesa = primo.fase
        val secondo = avvia(a)
        advanceUntilIdle()
        assertEquals(attesa, secondo.fase)
    }

    @Test
    fun `AC-533 lasciare S3 cancella il messaggio dell esito`() = runTest {
        val a = ambiente()
        val primo = inAnteprima(a)
        primo.azioni.applicaSomiglianza()
        advanceUntilIdle()
        assertIs<FaseSomiglianza.Esito>(primo.fase)
        val secondo = avvia(a)
        advanceUntilIdle()
        assertEquals(FaseSomiglianza.Inattiva, secondo.fase)
        assertNull(a.somiglianza.stato.value[REG])
    }

    @Test
    fun `AC-535 AC-548 S3 in sola lettura durante il confronto o l anteprima chiama annulla`() = runTest {
        val a = ambiente().apply { statoElaborazione = statoVista(StatoElaborazioneVista.COMPLETATA) }
        a.somiglianza.trattieni = true
        val presenter = avvia(a, conStati = true)
        advanceUntilIdle()
        presenter.azioni.calcolaSomiglianza()
        advanceUntilIdle()
        a.statoElaborazione = statoVista(StatoElaborazioneVista.IN_ATTESA)
        a.aggiornamenti.emetti(Cambiamento(REG))
        advanceUntilIdle()
        assertEquals(listOf(REG), a.somiglianza.annullamenti)
        assertEquals(FaseSomiglianza.Inattiva, presenter.fase)

        val b = ambiente().apply { statoElaborazione = statoVista(StatoElaborazioneVista.COMPLETATA) }
        b.somiglianza.gruppi = GRUPPI
        val inPreview = avvia(b, conStati = true)
        advanceUntilIdle()
        inPreview.azioni.calcolaSomiglianza()
        advanceUntilIdle()
        assertIs<FaseSomiglianza.Anteprima>(inPreview.fase)
        b.statoElaborazione = statoVista(StatoElaborazioneVista.IN_ATTESA)
        b.aggiornamenti.emetti(Cambiamento(REG))
        advanceUntilIdle()
        assertEquals(listOf(REG), b.somiglianza.annullamenti)
        assertEquals(FaseSomiglianza.Inattiva, inPreview.fase)
    }

    @Test
    fun `AC-536 calcola, applica e nominaFrase non girano mai sul thread UI`() = runBlocking {
        val ui = pool("ui-test")
        val io = pool("io-test")
        val a = AmbienteVoci(
            CoroutineScope(Dispatchers.Default),
            Clock.systemUTC(),
            vista = trascrittoRiferimenti(),
            identificate = IDENTIFICATE,
        )
        a.somiglianza.gruppi = GRUPPI
        val schermata = CoroutineScope(ui)
        val presenter = a.presenter(schermata, io)
        attendi { (presenter.stato.value as? RegistrazioneUiStato.Dati)?.pannello?.somiglianza?.abilitato == true }
        withContext(ui) { presenter.azioni.calcolaSomiglianza() }
        attendi { a.somiglianza.stato.value[REG] is StatoSomiglianza.Anteprima }
        attendi {
            val dati = presenter.stato.value as? RegistrazioneUiStato.Dati
            dati?.pannello?.somiglianza?.fase is FaseSomiglianza.Anteprima
        }
        withContext(ui) { presenter.azioni.applicaSomiglianza() }
        attendi { a.somiglianza.stato.value[REG] is StatoSomiglianza.Esito }
        withContext(ui) {
            presenter.azioni.selezionaSegmento(SegmentoId(5))
            presenter.azioni.nominaFrase(ObiettivoNome.Esistente(MARCO.parlanteId))
        }
        attendi { a.comandi.frasi.isNotEmpty() }
        val usati = a.somiglianza.thread + a.comandi.thread
        assertTrue(a.somiglianza.thread.size >= 2)
        usati.forEach {
            // Known flaky (rework cycle 1): kotlinx.coroutines debug mode can append " @coroutine#N" to
            // the pool's own thread name — `startsWith` still proves it is one of `io`'s threads, never
            // the exact string a debug build happens to decorate it with.
            assertTrue(it.name.startsWith("io-test"))
            assertNotEquals(Thread.currentThread(), it)
        }
        schermata.cancel()
        ui.close()
        io.close()
    }

    private fun pool(nome: String): ExecutorCoroutineDispatcher =
        Executors.newFixedThreadPool(2) { r -> Thread(r, nome).apply { isDaemon = true } }.asCoroutineDispatcher()

    private fun attendi(condizione: () -> Boolean) {
        repeat(TENTATIVI) {
            if (condizione()) return
            Thread.sleep(PAUSA_MS)
        }
        fail("condizione non raggiunta")
    }

    private companion object {
        // L713a: AC-536 (this test) failed once on a full run at the old 2 s budget (200 * 10ms) under
        // load, green on rerun (dispatch.log, "timing-flaky"). 1000 * 10ms = a 10 s deadline — robust
        // against a loaded machine, still bounded so a genuine regression fails the test, not hangs it.
        const val TENTATIVI = 1_000
        const val PAUSA_MS = 10L
    }
}
