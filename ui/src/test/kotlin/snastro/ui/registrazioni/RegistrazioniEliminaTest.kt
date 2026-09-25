package snastro.ui.registrazioni

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import snastro.kernel.ElaborazioneId
import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.progetto.applicazione.comandi.EliminaRegistrazione
import snastro.progetto.applicazione.letture.RegistrazioneDelProgettoVista
import snastro.progetto.dominio.ErroreProgetto
import snastro.trascrizione.applicazione.letture.StatoElaborazioneVista
import snastro.trascrizione.applicazione.letture.StatoRegistrazioneVista
import snastro.trascrizione.applicazione.porte.FaseElaborazione
import snastro.trascrizione.dominio.ErroreTrascrizione
import snastro.ui.AggiornamentiVistaFinta
import snastro.ui.lettore.LettoreAudioFinta
import snastro.ui.testi.MESSAGGIO_ELIMINAZIONE_RIFIUTATA
import snastro.ui.testi.messaggioEliminata
import snastro.ui.testi.messaggioPer
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val REG_1 = RegistrazioneId("id-1")
private val ELABORAZIONE_1 = ElaborazioneId("elaborazione-1")
private val ORA_FISSA: Instant = Instant.parse("2026-09-25T10:00:00Z")
private const val TITOLO_1 = "Seduta del 12 marzo"

private fun rigaVista(id: RegistrazioneId, titolo: String = TITOLO_1) =
    RegistrazioneDelProgettoVista(id, titolo, LocalDate.of(2026, 3, 12), 60_000)

@Suppress("LongParameterList") // one parameter per StatoRegistrazioneVista field (mirrors its own shape)
private fun statoVista(
    id: RegistrazioneId,
    stato: StatoElaborazioneVista,
    trascrittoDisponibile: Boolean = false,
    fase: FaseElaborazione? = null,
    avviataAlle: Instant? = null,
    posizioneInCoda: Int? = null,
    elaborazioneId: ElaborazioneId? = ELABORAZIONE_1,
) = StatoRegistrazioneVista(
    registrazioneId = id,
    stato = stato,
    fase = fase,
    avviataAlle = avviataAlle,
    motivoFallimento = null,
    posizioneInCoda = posizioneInCoda,
    numVoci = if (trascrittoDisponibile) 3 else null,
    numeroPersone = null,
    trascrittoDisponibile = trascrittoDisponibile,
    elaborazioneId = elaborazioneId,
)

/** Shortens `presentatore(this, stati = { ids -> ids.map { statoVista(it, …) } })` call sites below. */
private fun statiCon(
    stato: StatoElaborazioneVista,
    trascrittoDisponibile: Boolean = false,
    fase: FaseElaborazione? = null,
    avviataAlle: Instant? = null,
): (List<RegistrazioneId>) -> List<StatoRegistrazioneVista> = { ids ->
    ids.map {
        statoVista(it, stato, trascrittoDisponibile = trascrittoDisponibile, fase = fase, avviataAlle = avviataAlle)
    }
}

/**
 * ADR 0020 §6: the More menu ('Elimina…' state per row, AC-625), its confirmation (AC-626), the
 * confirmed command's three outcomes (AC-627/628) and the post-elimination notice —
 * [RegistrazioniPresenter.elimina]/`annullaElimina`/`confermaElimina`/`chiudiAvviso`, all optional
 * (R2 only), split from `RegistrazioniPresenterTest` like `RegistrazioniRitrascriviTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RegistrazioniEliminaTest {
    private val eliminazioni = mutableListOf<EliminaRegistrazione>()

    @Suppress("LongParameterList") // one parameter per RegistrazioniPresenter collaborator the tests vary
    private fun presentatore(
        scope: TestScope,
        stati: (List<RegistrazioneId>) -> List<StatoRegistrazioneVista> = statiCon(StatoElaborazioneVista.NON_AVVIATA),
        eliminaSupportato: Boolean = true,
        ritrascriviSupportato: Boolean = false,
        lettore: LettoreAudioFinta = LettoreAudioFinta(),
        elimina: (EliminaRegistrazione) -> Esito<Unit> = { Esito.Ok(Unit) },
    ): RegistrazioniPresenter {
        val dispatcher = StandardTestDispatcher(scope.testScheduler)
        return RegistrazioniPresenter(
            scope = CoroutineScope(dispatcher),
            io = dispatcher,
            registrazioni = { listOf(rigaVista(REG_1)) },
            aggiungiRegistrazione = { error("aggiungi non atteso in questo test") },
            modificaDataRegistrazione = { error("modificaData non atteso in questo test") },
            rinominaRegistrazione = { error("rinomina non atteso in questo test") },
            lettore = lettore,
            aggiornamenti = AggiornamentiVistaFinta(),
            clock = Clock.fixed(ORA_FISSA, ZoneOffset.UTC),
            statiElaborazione = stati,
            avviaElaborazione = { error("avviaElaborazione non atteso in questo test") },
            ritrascrivi = if (ritrascriviSupportato) {
                { error("ritrascrivi non atteso in questo test") }
            } else {
                null
            },
            eliminaRegistrazione = if (eliminaSupportato) {
                { c ->
                    eliminazioni += c
                    elimina(c)
                }
            } else {
                null
            },
        )
    }

    private fun RegistrazioniPresenter.riga(): RigaRegistrazione =
        assertIs<RegistrazioniUiStato.Dati>(stato.value).righe.single()

    private fun RegistrazioniPresenter.righeOVuota(): List<RigaRegistrazione> =
        assertIs<RegistrazioniUiStato.Dati>(stato.value).righe

    // --- AC-625: the row's `eliminazione` state, per row state and per combination of sources -------

    @Test
    fun `AC-625 NON_AVVIATA con entrambe le sorgenti e Disponibile e Ritrascrivi assente`() = runTest {
        val presenter = presentatore(this, stati = statiCon(StatoElaborazioneVista.NON_AVVIATA))
        advanceUntilIdle()

        val riga = presenter.riga()
        assertEquals(StatoEliminazione.Disponibile, riga.eliminazione)
        assertEquals(false, riga.ritrascriviDisponibile)
    }

    @Test
    fun `AC-625 IN_ATTESA e NonDisponibile con la didascalia In coda`() = runTest {
        val presenter = presentatore(
            this,
            stati = statiCon(StatoElaborazioneVista.IN_ATTESA, trascrittoDisponibile = false),
        )
        advanceUntilIdle()

        val eliminazione = assertIs<StatoEliminazione.NonDisponibile>(presenter.riga().eliminazione)
        assertEquals("Annulla prima la trascrizione in coda.", eliminazione.motivo)
    }

    @Test
    fun `AC-625 IN_CORSO e NonDisponibile con la didascalia durante la trascrizione`() = runTest {
        val presenter = presentatore(
            this,
            stati = statiCon(
                StatoElaborazioneVista.IN_CORSO,
                fase = FaseElaborazione.TRASCRIZIONE,
                avviataAlle = ORA_FISSA.minusSeconds(5),
            ),
        )
        advanceUntilIdle()

        val eliminazione = assertIs<StatoEliminazione.NonDisponibile>(presenter.riga().eliminazione)
        assertEquals("Non puoi eliminarla durante la trascrizione.", eliminazione.motivo)
    }

    @Test
    fun `AC-625 FALLITA e Completata sono Disponibile`() = runTest {
        val presenter = presentatore(this, stati = statiCon(StatoElaborazioneVista.FALLITA))
        advanceUntilIdle()
        assertEquals(StatoEliminazione.Disponibile, presenter.riga().eliminazione)

        val presenterCompletata =
            presentatore(this, stati = statiCon(StatoElaborazioneVista.COMPLETATA, trascrittoDisponibile = true))
        advanceUntilIdle()
        assertEquals(StatoEliminazione.Disponibile, presenterCompletata.riga().eliminazione)
    }

    @Test
    fun `AC-625 senza la sorgente eliminaRegistrazione la riga e Assente qualunque sia lo stato`() = runTest {
        val presenter = presentatore(
            this,
            eliminaSupportato = false,
            stati = statiCon(StatoElaborazioneVista.IN_CORSO, fase = FaseElaborazione.TRASCRIZIONE),
        )
        advanceUntilIdle()

        assertEquals(StatoEliminazione.Assente, presenter.riga().eliminazione)
    }

    @Test
    fun `AC-625 solo Ritrascrivi fornito la riga resta Assente e Ritrascrivi disponibile`() = runTest {
        val presenter = presentatore(
            this,
            eliminaSupportato = false,
            ritrascriviSupportato = true,
            stati = statiCon(StatoElaborazioneVista.COMPLETATA, trascrittoDisponibile = true),
        )
        advanceUntilIdle()

        val riga = presenter.riga()
        assertEquals(StatoEliminazione.Assente, riga.eliminazione)
        assertEquals(true, riga.ritrascriviDisponibile)
    }

    @Test
    fun `AC-625 entrambe le sorgenti su Completata sono Disponibile e Ritrascrivi disponibile`() = runTest {
        val presenter = presentatore(
            this,
            eliminaSupportato = true,
            ritrascriviSupportato = true,
            stati = statiCon(StatoElaborazioneVista.COMPLETATA, trascrittoDisponibile = true),
        )
        advanceUntilIdle()

        val riga = presenter.riga()
        assertEquals(StatoEliminazione.Disponibile, riga.eliminazione)
        assertEquals(true, riga.ritrascriviDisponibile)
    }

    // --- AC-626: 'Elimina…' opens the confirmation; a disabled item sends nothing --------------------

    @Test
    fun `AC-626 Elimina su una riga Disponibile apre la conferma`() = runTest {
        val presenter = presentatore(this, stati = statiCon(StatoElaborazioneVista.NON_AVVIATA))
        advanceUntilIdle()

        presenter.azioni.elimina(REG_1)

        assertEquals(true, presenter.riga().confermaElimina)
        assertEquals(emptyList(), eliminazioni)
    }

    @Test
    fun `AC-625 AC-626 Elimina su una riga NonDisponibile non invia alcun comando e non apre la conferma`() = runTest {
        val presenter = presentatore(
            this,
            stati = statiCon(StatoElaborazioneVista.IN_CORSO, fase = FaseElaborazione.TRASCRIZIONE),
        )
        advanceUntilIdle()

        presenter.azioni.elimina(REG_1)

        assertEquals(false, presenter.riga().confermaElimina)
        assertEquals(emptyList(), eliminazioni)
    }

    @Test
    fun `AC-626 Annulla sulla conferma non invia alcun comando`() = runTest {
        val presenter = presentatore(this, stati = statiCon(StatoElaborazioneVista.NON_AVVIATA))
        advanceUntilIdle()
        presenter.azioni.elimina(REG_1)

        presenter.azioni.annullaElimina(REG_1)

        assertEquals(false, presenter.riga().confermaElimina)
        assertEquals(emptyList(), eliminazioni)
    }

    @Test
    fun `AC-626 senza la sorgente elimina e annullaElimina non fanno nulla`() = runTest {
        val presenter = presentatore(
            this,
            eliminaSupportato = false,
            stati = statiCon(StatoElaborazioneVista.NON_AVVIATA),
        )
        advanceUntilIdle()

        presenter.azioni.elimina(REG_1)
        presenter.azioni.annullaElimina(REG_1)

        assertEquals(false, presenter.riga().confermaElimina)
    }

    // --- AC-627: the confirmed 'Elimina' — pause, reload, notice ---------------------------------------

    @Test
    fun `AC-627 la conferma invia esattamente un EliminaRegistrazione`() = runTest {
        val presenter = presentatore(this, stati = statiCon(StatoElaborazioneVista.NON_AVVIATA))
        advanceUntilIdle()
        presenter.azioni.elimina(REG_1)

        presenter.azioni.confermaElimina(REG_1)
        advanceUntilIdle()

        assertEquals(listOf(EliminaRegistrazione(REG_1)), eliminazioni)
    }

    @Test
    fun `AC-627 su Ok la riga scompare e appare l avviso con il titolo`() = runTest {
        var registrazioniCorrenti = listOf(rigaVista(REG_1))
        val dispatcher = StandardTestDispatcher(testScheduler)
        val presenter = RegistrazioniPresenter(
            scope = CoroutineScope(dispatcher),
            io = dispatcher,
            registrazioni = { registrazioniCorrenti },
            aggiungiRegistrazione = { error("non atteso") },
            modificaDataRegistrazione = { error("non atteso") },
            rinominaRegistrazione = { error("non atteso") },
            lettore = LettoreAudioFinta(),
            aggiornamenti = AggiornamentiVistaFinta(),
            clock = Clock.fixed(ORA_FISSA, ZoneOffset.UTC),
            statiElaborazione = statiCon(StatoElaborazioneVista.NON_AVVIATA),
            eliminaRegistrazione = {
                eliminazioni += it
                registrazioniCorrenti = emptyList()
                Esito.Ok(Unit)
            },
        )
        advanceUntilIdle()
        presenter.azioni.elimina(REG_1)

        presenter.azioni.confermaElimina(REG_1)
        advanceUntilIdle()

        assertEquals(emptyList(), presenter.righeOVuota())
        val dati = assertIs<RegistrazioniUiStato.Dati>(presenter.stato.value)
        assertEquals(messaggioEliminata(TITOLO_1), dati.avviso)
    }

    @Test
    fun `AC-627 se la riga sta suonando il lettore va in pausa prima della ricarica`() = runTest {
        val lettore = LettoreAudioFinta()
        var registrazioniCorrenti = listOf(rigaVista(REG_1))
        val dispatcher = StandardTestDispatcher(testScheduler)
        val presenter = RegistrazioniPresenter(
            scope = CoroutineScope(dispatcher),
            io = dispatcher,
            registrazioni = { registrazioniCorrenti },
            aggiungiRegistrazione = { error("non atteso") },
            modificaDataRegistrazione = { error("non atteso") },
            rinominaRegistrazione = { error("non atteso") },
            lettore = lettore,
            aggiornamenti = AggiornamentiVistaFinta(),
            clock = Clock.fixed(ORA_FISSA, ZoneOffset.UTC),
            statiElaborazione = statiCon(StatoElaborazioneVista.NON_AVVIATA),
            eliminaRegistrazione = {
                eliminazioni += it
                registrazioniCorrenti = emptyList()
                Esito.Ok(Unit)
            },
        )
        advanceUntilIdle()
        lettore.riproduciDa(REG_1, 0)
        presenter.azioni.elimina(REG_1)

        presenter.azioni.confermaElimina(REG_1)
        advanceUntilIdle()

        assertEquals(false, lettore.stato.value.inRiproduzione)
    }

    @Test
    fun `AC-627 se un altra riga sta suonando il lettore non va in pausa`() = runTest {
        val altra = RegistrazioneId("id-2")
        val lettore = LettoreAudioFinta()
        val presenter = presentatore(this, stati = statiCon(StatoElaborazioneVista.NON_AVVIATA), lettore = lettore)
        advanceUntilIdle()
        lettore.riproduciDa(altra, 0)
        presenter.azioni.elimina(REG_1)

        presenter.azioni.confermaElimina(REG_1)
        advanceUntilIdle()

        assertEquals(true, lettore.stato.value.inRiproduzione)
        assertEquals(altra, lettore.stato.value.registrazioneId)
    }

    @Test
    fun `AC-627 operazioneInCorso blocca un secondo invio della conferma`() = runTest {
        val presenter = presentatore(this, stati = statiCon(StatoElaborazioneVista.NON_AVVIATA))
        advanceUntilIdle()
        presenter.azioni.elimina(REG_1)

        presenter.azioni.confermaElimina(REG_1) // starts, operazioneInCorso = true synchronously
        presenter.azioni.confermaElimina(REG_1) // M3: no-op
        advanceUntilIdle()

        assertEquals(1, eliminazioni.size)
    }

    // --- AC-628: the three error outcomes ---------------------------------------------------------------

    @Test
    fun `AC-628 RegistrazioneNonTrovata ricarica senza alcun messaggio inline`() = runTest {
        val presenter = presentatore(
            this,
            stati = statiCon(StatoElaborazioneVista.NON_AVVIATA),
            elimina = { Esito.Errore(ErroreProgetto.RegistrazioneNonTrovata(REG_1)) },
        )
        advanceUntilIdle()
        presenter.azioni.elimina(REG_1)

        presenter.azioni.confermaElimina(REG_1)
        advanceUntilIdle()

        // the row is still returned by the (unchanged) fake read-model, so it survives the reload —
        // what matters is that no erroreRiga/avviso appear and the dialog/spinner both close (AC-628).
        val riga = presenter.riga()
        assertNull(riga.erroreRiga)
        assertEquals(false, riga.confermaElimina)
        assertEquals(false, riga.operazioneInCorso)
        val dati = assertIs<RegistrazioniUiStato.Dati>(presenter.stato.value)
        assertNull(dati.avviso)
    }

    @Test
    fun `AC-628 ElaborazioneGiaAperta mostra il messaggio dedicato e ricarica`() = runTest {
        var statoCorrente = StatoElaborazioneVista.NON_AVVIATA
        val presenter = presentatore(
            this,
            stati = { ids -> ids.map { statoVista(it, statoCorrente) } },
            elimina = {
                statoCorrente = StatoElaborazioneVista.IN_CORSO
                Esito.Errore(ErroreTrascrizione.ElaborazioneGiaAperta(REG_1))
            },
        )
        advanceUntilIdle()
        presenter.azioni.elimina(REG_1)

        presenter.azioni.confermaElimina(REG_1)
        advanceUntilIdle()

        val riga = presenter.riga()
        assertEquals(MESSAGGIO_ELIMINAZIONE_RIFIUTATA, riga.erroreRiga)
        assertEquals(false, riga.confermaElimina)
        assertEquals(false, riga.operazioneInCorso)
        // the dedicated text is NOT the generic messaggioPer line for the same error type (used by
        // AvviaElaborazione/Ritrascrivi) — the two must differ, or this test would not discriminate.
        assertTrue(MESSAGGIO_ELIMINAZIONE_RIFIUTATA != messaggioPer(ErroreTrascrizione.ElaborazioneGiaAperta(REG_1)))
        // AC-625: the row reloaded, now IN_CORSO — the menu's Elimina… item is disabled again.
        assertIs<StatoEliminazione.NonDisponibile>(riga.eliminazione)
    }

    @Test
    fun `AC-628 un altro Errore mostra il messaggio generico senza ricaricare`() = runTest {
        val errore = ErroreProgetto.TitoloVuoto
        val presenter = presentatore(
            this,
            stati = statiCon(StatoElaborazioneVista.NON_AVVIATA),
            elimina = { Esito.Errore(errore) },
        )
        advanceUntilIdle()
        presenter.azioni.elimina(REG_1)

        presenter.azioni.confermaElimina(REG_1)
        advanceUntilIdle()

        val riga = presenter.riga()
        assertEquals(messaggioPer(errore), riga.erroreRiga)
        assertEquals(false, riga.confermaElimina)
        assertEquals(false, riga.operazioneInCorso)
    }

    // --- avviso lifecycle (ADR 0020 §6, "fino a chiudiAvviso o al comando successivo") ------------------

    @Test
    fun `chiudiAvviso cancella l avviso`() = runTest {
        var registrazioniCorrenti = listOf(rigaVista(REG_1))
        val dispatcher = StandardTestDispatcher(testScheduler)
        val presenter = RegistrazioniPresenter(
            scope = CoroutineScope(dispatcher),
            io = dispatcher,
            registrazioni = { registrazioniCorrenti },
            aggiungiRegistrazione = { error("non atteso") },
            modificaDataRegistrazione = { error("non atteso") },
            rinominaRegistrazione = { error("non atteso") },
            lettore = LettoreAudioFinta(),
            aggiornamenti = AggiornamentiVistaFinta(),
            clock = Clock.fixed(ORA_FISSA, ZoneOffset.UTC),
            statiElaborazione = statiCon(StatoElaborazioneVista.NON_AVVIATA),
            eliminaRegistrazione = {
                eliminazioni += it
                registrazioniCorrenti = emptyList()
                Esito.Ok(Unit)
            },
        )
        advanceUntilIdle()
        presenter.azioni.elimina(REG_1)
        presenter.azioni.confermaElimina(REG_1)
        advanceUntilIdle()
        assertEquals(messaggioEliminata(TITOLO_1), assertIs<RegistrazioniUiStato.Dati>(presenter.stato.value).avviso)

        presenter.azioni.chiudiAvviso()

        assertNull(assertIs<RegistrazioniUiStato.Dati>(presenter.stato.value).avviso)
    }
}
