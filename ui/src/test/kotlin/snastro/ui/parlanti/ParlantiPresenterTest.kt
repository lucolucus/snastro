package snastro.ui.parlanti

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import snastro.kernel.Esito
import snastro.kernel.EstrattoRef
import snastro.kernel.IntervalloMs
import snastro.kernel.ParlanteId
import snastro.kernel.RegistrazioneId
import snastro.parlanti.applicazione.comandi.EliminaParlante
import snastro.parlanti.applicazione.comandi.PromuoviParlante
import snastro.parlanti.applicazione.comandi.RinominaParlante
import snastro.parlanti.applicazione.eventi.TipoParlanteVista
import snastro.parlanti.applicazione.letture.ParlanteDelProgetto
import snastro.parlanti.applicazione.letture.StatoParlanteVista
import snastro.parlanti.dominio.ErroreParlanti
import snastro.ui.AggiornamentiVistaFinta
import snastro.ui.Cambiamento
import snastro.ui.lettore.LettoreAudio
import snastro.ui.lettore.LettoreAudioFinta
import snastro.ui.testi.MESSAGGIO_ERRORE_CARICAMENTO_PARLANTI
import snastro.ui.testi.MESSAGGIO_ERRORE_GENERICO
import snastro.ui.testi.messaggioPer
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val PARLANTE_1 = ParlanteId("id-1")
private val PARLANTE_2 = ParlanteId("id-2")
private val REG_1 = RegistrazioneId("reg-1")
private val DATA_1: LocalDate = LocalDate.of(2026, 3, 12)
private val UN_ESTRATTO = EstrattoRef(REG_1, listOf(IntervalloMs(0, 1000)))

@Suppress("LongParameterList")
private fun unParlante(
    id: ParlanteId = PARLANTE_1,
    nome: String = "Marco",
    tipo: TipoParlanteVista = TipoParlanteVista.RICORRENTE,
    stato: StatoParlanteVista = StatoParlanteVista.ATTIVO,
    numImpronte: Int = 2,
    numRegistrazioni: Int = 1,
    ultimaApparizione: LocalDate? = DATA_1,
    estratto: EstrattoRef? = null,
) = ParlanteDelProgetto(id, nome, tipo, stato, numImpronte, numRegistrazioni, ultimaApparizione, estratto)

/**
 * Presenter tests for S4 · Parlanti del Progetto, translating `tests_nl` AC-220..226 into unit tests
 * on plain [ParlantiPresenter] state, exactly like `RegistrazioniPresenterTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ParlantiPresenterTest {
    @Suppress("LongParameterList")
    private fun presentatore(
        scope: TestScope,
        parlanti: () -> List<ParlanteDelProgetto> = { emptyList() },
        rinomina: (RinominaParlante) -> Esito<Unit> = { error("rinomina non atteso in questo test") },
        promuovi: (PromuoviParlante) -> Esito<Unit> = { error("promuovi non atteso in questo test") },
        elimina: (EliminaParlante) -> Esito<Unit> = { error("elimina non atteso in questo test") },
        lettore: LettoreAudio = LettoreAudioFinta(),
        aggiornamenti: AggiornamentiVistaFinta = AggiornamentiVistaFinta(),
    ): ParlantiPresenter {
        val dispatcher = StandardTestDispatcher(scope.testScheduler)
        return ParlantiPresenter(
            CoroutineScope(dispatcher),
            dispatcher,
            parlanti,
            rinomina,
            promuovi,
            elimina,
            lettore,
            aggiornamenti,
        )
    }

    // --- AC-220/AC-221: loading + empty ------------------------------------------------------------

    @Test
    fun `AC-221 prima del caricamento lo stato e Caricamento`() = runTest {
        val presenter = presentatore(this)
        assertEquals(ParlantiUiStato.Caricamento, presenter.stato.value)
    }

    @Test
    fun `AC-220 un catalogo vuoto produce il messaggio dedicato`() = runTest {
        val presenter = presentatore(this)
        advanceUntilIdle()
        val dati = assertIs<ParlantiUiStato.Dati>(presenter.stato.value)
        assertTrue(dati.vuoto)
    }

    // --- AC-222: grouping ----------------------------------------------------------------------

    @Test
    fun `AC-222 i parlanti sono raggruppati per tipo e gli eliminati sono separati`() = runTest {
        val presenter = presentatore(
            this,
            parlanti = {
                listOf(
                    unParlante(id = PARLANTE_1, nome = "Marco", tipo = TipoParlanteVista.RICORRENTE),
                    unParlante(id = PARLANTE_2, nome = "Ospite del 12/03/2026", tipo = TipoParlanteVista.OCCASIONALE),
                    unParlante(id = ParlanteId("id-3"), nome = "Luca", stato = StatoParlanteVista.ELIMINATO),
                )
            },
        )
        advanceUntilIdle()

        val dati = assertIs<ParlantiUiStato.Dati>(presenter.stato.value)
        assertEquals(listOf(PARLANTE_1), dati.ricorrenti.map { it.parlanteId })
        assertEquals(listOf(PARLANTE_2), dati.occasionali.map { it.parlanteId })
        assertEquals(listOf("Luca"), dati.eliminati.map { it.nome })
    }

    // --- AC-223: inline rename -----------------------------------------------------------------

    @Test
    fun `AC-223 rinomina riuscita ricarica la lista con il nuovo nome`() = runTest {
        var nomeCorrente = "Marco"
        val presenter = presentatore(
            this,
            parlanti = { listOf(unParlante(nome = nomeCorrente)) },
            rinomina = { c ->
                nomeCorrente = c.nome
                Esito.Ok(Unit)
            },
        )
        advanceUntilIdle()

        presenter.azioni.rinomina(PARLANTE_1, "Marco Rossi")
        advanceUntilIdle()

        val riga = assertIs<ParlantiUiStato.Dati>(presenter.stato.value).ricorrenti.single()
        assertEquals("Marco Rossi", riga.nome)
    }

    @Test
    fun `AC-223 un errore di rinomina e mostrato inline sulla riga e nulla cambia`() = runTest {
        val presenter = presentatore(
            this,
            parlanti = { listOf(unParlante()) },
            rinomina = { Esito.Errore(ErroreParlanti.NomeGiaInUso("Anna")) },
        )
        advanceUntilIdle()

        presenter.azioni.rinomina(PARLANTE_1, "Anna")
        advanceUntilIdle()

        val riga = assertIs<ParlantiUiStato.Dati>(presenter.stato.value).ricorrenti.single()
        assertEquals(messaggioPer(ErroreParlanti.NomeGiaInUso("Anna")), riga.erroreRiga)
        assertEquals(false, riga.operazioneInCorso)
        assertEquals("Marco", riga.nome)
    }

    @Test
    fun `M3 due rinomina ravvicinate sulla stessa riga eseguono il servizio una sola volta`() = runTest {
        var chiamate = 0
        val presenter = presentatore(
            this,
            parlanti = { listOf(unParlante()) },
            rinomina = {
                chiamate++
                Esito.Ok(Unit)
            },
        )
        advanceUntilIdle()

        presenter.azioni.rinomina(PARLANTE_1, "Uno")
        presenter.azioni.rinomina(PARLANTE_1, "Due")
        advanceUntilIdle()

        assertEquals(1, chiamate)
    }

    // --- AC-224: promote visible/available only for occasionale ---------------------------------

    @Test
    fun `AC-224 promuovi riuscito sposta la riga tra i ricorrenti`() = runTest {
        var tipoCorrente = TipoParlanteVista.OCCASIONALE
        val presenter = presentatore(
            this,
            parlanti = { listOf(unParlante(tipo = tipoCorrente)) },
            promuovi = { c ->
                assertEquals(PARLANTE_1, c.parlanteId)
                tipoCorrente = TipoParlanteVista.RICORRENTE
                Esito.Ok(Unit)
            },
        )
        advanceUntilIdle()
        assertEquals(1, assertIs<ParlantiUiStato.Dati>(presenter.stato.value).occasionali.size)

        presenter.azioni.promuovi(PARLANTE_1)
        advanceUntilIdle()

        val dati = assertIs<ParlantiUiStato.Dati>(presenter.stato.value)
        assertEquals(emptyList(), dati.occasionali)
        assertEquals(listOf(PARLANTE_1), dati.ricorrenti.map { it.parlanteId })
    }

    @Test
    fun `AC-224 un errore di promozione e mostrato inline sulla riga e nulla cambia`() = runTest {
        val presenter = presentatore(
            this,
            parlanti = { listOf(unParlante(tipo = TipoParlanteVista.OCCASIONALE)) },
            promuovi = { Esito.Errore(ErroreParlanti.PromozioneNonAmmessa(PARLANTE_1)) },
        )
        advanceUntilIdle()

        presenter.azioni.promuovi(PARLANTE_1)
        advanceUntilIdle()

        val riga = assertIs<ParlantiUiStato.Dati>(presenter.stato.value).occasionali.single()
        assertEquals(messaggioPer(ErroreParlanti.PromozioneNonAmmessa(PARLANTE_1)), riga.erroreRiga)
        assertEquals(false, riga.operazioneInCorso)
    }

    // --- AC-225: inline delete confirmation ------------------------------------------------------

    @Test
    fun `AC-225 chiediConfermaEliminazione mostra la conferma senza inviare alcun comando`() = runTest {
        var chiamate = 0
        val presenter = presentatore(
            this,
            parlanti = { listOf(unParlante()) },
            elimina = {
                chiamate++
                Esito.Ok(Unit)
            },
        )
        advanceUntilIdle()

        presenter.azioni.chiediConfermaEliminazione(PARLANTE_1)

        val riga = assertIs<ParlantiUiStato.Dati>(presenter.stato.value).ricorrenti.single()
        assertTrue(riga.confermaEliminazione)
        assertEquals(0, chiamate)
    }

    @Test
    fun `AC-225 annullare la conferma non cambia nient altro`() = runTest {
        val presenter = presentatore(this, parlanti = { listOf(unParlante()) })
        advanceUntilIdle()
        presenter.azioni.chiediConfermaEliminazione(PARLANTE_1)
        val primaDiAnnullare = assertIs<ParlantiUiStato.Dati>(presenter.stato.value).ricorrenti.single()

        presenter.azioni.annullaEliminazione(PARLANTE_1)

        val dopoAnnullare = assertIs<ParlantiUiStato.Dati>(presenter.stato.value).ricorrenti.single()
        assertEquals(false, dopoAnnullare.confermaEliminazione)
        assertEquals(primaDiAnnullare.copy(confermaEliminazione = false), dopoAnnullare)
    }

    @Test
    fun `AC-225 confermaEliminazione riuscita sposta il parlante tra gli eliminati`() = runTest {
        var statoCorrente = StatoParlanteVista.ATTIVO
        val presenter = presentatore(
            this,
            parlanti = { listOf(unParlante(stato = statoCorrente)) },
            elimina = { c ->
                assertEquals(PARLANTE_1, c.parlanteId)
                statoCorrente = StatoParlanteVista.ELIMINATO
                Esito.Ok(Unit)
            },
        )
        advanceUntilIdle()
        presenter.azioni.chiediConfermaEliminazione(PARLANTE_1)

        presenter.azioni.confermaEliminazione(PARLANTE_1)
        advanceUntilIdle()

        val dati = assertIs<ParlantiUiStato.Dati>(presenter.stato.value)
        assertEquals(emptyList(), dati.ricorrenti)
        assertEquals(listOf(PARLANTE_1), dati.eliminati.map { it.parlanteId })
    }

    @Test
    fun `AC-225 un errore di eliminazione e mostrato inline e la riga resta attiva`() = runTest {
        val presenter = presentatore(
            this,
            parlanti = { listOf(unParlante()) },
            elimina = { Esito.Errore(ErroreParlanti.ParlanteNonTrovato(PARLANTE_1)) },
        )
        advanceUntilIdle()
        presenter.azioni.chiediConfermaEliminazione(PARLANTE_1)

        presenter.azioni.confermaEliminazione(PARLANTE_1)
        advanceUntilIdle()

        val riga = assertIs<ParlantiUiStato.Dati>(presenter.stato.value).ricorrenti.single()
        assertEquals(messaggioPer(ErroreParlanti.ParlanteNonTrovato(PARLANTE_1)), riga.erroreRiga)
        assertEquals(false, riga.operazioneInCorso)
    }

    // --- AC-226: playback -----------------------------------------------------------------------

    @Test
    fun `AC-226 senza estratto la riproduzione e disabilitata`() = runTest {
        val presenter = presentatore(this, parlanti = { listOf(unParlante(estratto = null)) })
        advanceUntilIdle()
        val riga = assertIs<ParlantiUiStato.Dati>(presenter.stato.value).ricorrenti.single()
        assertEquals(false, riga.riproduzioneAbilitata)
    }

    @Test
    fun `AC-226 con estratto riproduci invoca LettoreAudio con quell estratto`() = runTest {
        val fake = LettoreAudioFinta()
        val presenter = presentatore(
            this,
            parlanti = { listOf(unParlante(estratto = UN_ESTRATTO)) },
            lettore = fake,
        )
        advanceUntilIdle()
        val riga = assertIs<ParlantiUiStato.Dati>(presenter.stato.value).ricorrenti.single()
        assertTrue(riga.riproduzioneAbilitata)

        presenter.azioni.riproduci(PARLANTE_1)
        advanceUntilIdle()

        assertEquals(REG_1, fake.stato.value.registrazioneId)
        assertTrue(fake.stato.value.inRiproduzione)
    }

    // --- H1: dismissible inline messages --------------------------------------------------------

    @Test
    fun `H1 chiudiErroreRiga rimuove solo il messaggio della riga`() = runTest {
        val presenter = presentatore(
            this,
            parlanti = { listOf(unParlante()) },
            rinomina = { Esito.Errore(ErroreParlanti.NomeGiaInUso("Anna")) },
        )
        advanceUntilIdle()
        presenter.azioni.rinomina(PARLANTE_1, "Anna")
        advanceUntilIdle()
        assertEquals(
            true,
            assertIs<ParlantiUiStato.Dati>(presenter.stato.value).ricorrenti.single().erroreRiga != null,
        )

        presenter.azioni.chiudiErroreRiga(PARLANTE_1)

        assertNull(assertIs<ParlantiUiStato.Dati>(presenter.stato.value).ricorrenti.single().erroreRiga)
    }

    // --- M5: distinct error state for the INITIAL load -------------------------------------------

    @Test
    fun `M5 un fallimento del caricamento iniziale mostra uno stato Errore distinto`() = runTest {
        val presenter = presentatore(this, parlanti = { error("guasto di lettura") })
        advanceUntilIdle()

        val stato = assertIs<ParlantiUiStato.Errore>(presenter.stato.value)
        assertEquals(MESSAGGIO_ERRORE_CARICAMENTO_PARLANTI, stato.messaggio)
    }

    @Test
    fun `M5 riprova ricarica dopo un fallimento del caricamento iniziale`() = runTest {
        var fallisce = true
        val presenter = presentatore(
            this,
            parlanti = { if (fallisce) error("guasto") else listOf(unParlante()) },
        )
        advanceUntilIdle()
        assertIs<ParlantiUiStato.Errore>(presenter.stato.value)

        fallisce = false
        presenter.azioni.riprova()
        advanceUntilIdle()

        val dati = assertIs<ParlantiUiStato.Dati>(presenter.stato.value)
        assertEquals(listOf(PARLANTE_1), dati.ricorrenti.map { it.parlanteId })
    }

    @Test
    fun `M5 un fallimento di un refresh successivo mantiene le righe note invece di passare a Errore`() = runTest {
        var fallisce = false
        val aggiornamenti = AggiornamentiVistaFinta()
        val presenter = presentatore(
            this,
            parlanti = { if (fallisce) error("guasto di rete") else listOf(unParlante()) },
            aggiornamenti = aggiornamenti,
        )
        advanceUntilIdle()
        assertIs<ParlantiUiStato.Dati>(presenter.stato.value)

        fallisce = true
        aggiornamenti.emetti(Cambiamento(null))
        advanceUntilIdle()

        val dati = assertIs<ParlantiUiStato.Dati>(presenter.stato.value)
        assertEquals(listOf(PARLANTE_1), dati.ricorrenti.map { it.parlanteId })
        assertEquals(MESSAGGIO_ERRORE_GENERICO, dati.errore)
    }

    // --- R15: refresh on Cambiamento --------------------------------------------------------------

    @Test
    fun `R15 la lista si aggiorna quando arriva un Cambiamento`() = runTest {
        var righeCorrenti = listOf(unParlante(id = PARLANTE_1))
        val aggiornamenti = AggiornamentiVistaFinta()
        val presenter = presentatore(this, parlanti = { righeCorrenti }, aggiornamenti = aggiornamenti)
        advanceUntilIdle()
        assertEquals(1, assertIs<ParlantiUiStato.Dati>(presenter.stato.value).ricorrenti.size)

        righeCorrenti = listOf(unParlante(id = PARLANTE_1), unParlante(id = PARLANTE_2, nome = "Anna"))
        aggiornamenti.emetti(Cambiamento(null))
        advanceUntilIdle()

        assertEquals(2, assertIs<ParlantiUiStato.Dati>(presenter.stato.value).ricorrenti.size)
    }
}
