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
import snastro.progetto.applicazione.letture.RegistrazioneDelProgettoVista
import snastro.trascrizione.applicazione.comandi.AnnullaElaborazione
import snastro.trascrizione.applicazione.comandi.AvviaElaborazione
import snastro.trascrizione.applicazione.letture.StatoElaborazioneVista
import snastro.trascrizione.applicazione.letture.StatoRegistrazioneVista
import snastro.trascrizione.applicazione.porte.FaseElaborazione
import snastro.trascrizione.dominio.ErroreTrascrizione
import snastro.ui.AggiornamentiVistaFinta
import snastro.ui.lettore.LettoreAudioFinta
import snastro.ui.testi.MESSAGGIO_NUMERO_PERSONE_NON_VALIDO
import snastro.ui.testi.messaggioPer
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

private val REG_1 = RegistrazioneId("id-1")
private val ELABORAZIONE_1 = ElaborazioneId("elaborazione-1")
private val ORA_FISSA: Instant = Instant.parse("2026-09-23T10:00:00Z")

private fun rigaVista(id: RegistrazioneId) =
    RegistrazioneDelProgettoVista(id, "Seduta", LocalDate.of(2026, 3, 12), 60_000)

@Suppress("LongParameterList") // one parameter per StatoRegistrazioneVista field (mirrors its own shape)
private fun statoVista(
    id: RegistrazioneId,
    stato: StatoElaborazioneVista,
    trascrittoDisponibile: Boolean,
    fase: FaseElaborazione? = null,
    avviataAlle: Instant? = null,
    motivoFallimento: String? = null,
    posizioneInCoda: Int? = null,
    numeroPersone: Int? = null,
    elaborazioneId: ElaborazioneId? = ELABORAZIONE_1,
) = StatoRegistrazioneVista(
    registrazioneId = id,
    stato = stato,
    fase = fase,
    avviataAlle = avviataAlle,
    motivoFallimento = motivoFallimento,
    posizioneInCoda = posizioneInCoda,
    numVoci = if (trascrittoDisponibile) 3 else null,
    numeroPersone = numeroPersone,
    trascrittoDisponibile = trascrittoDisponibile,
    elaborazioneId = elaborazioneId,
)

/** Shortens `presentatore(this, stati = { ids -> ids.map { statoVista(it, …) } })` call sites below. */
@Suppress("LongParameterList") // one parameter per statoVista field these tests vary
private fun statiCon(
    stato: StatoElaborazioneVista,
    trascrittoDisponibile: Boolean,
    posizioneInCoda: Int? = null,
    motivoFallimento: String? = null,
    numeroPersone: Int? = null,
): (List<RegistrazioneId>) -> List<StatoRegistrazioneVista> = { ids ->
    ids.map {
        statoVista(
            it,
            stato,
            trascrittoDisponibile = trascrittoDisponibile,
            posizioneInCoda = posizioneInCoda,
            motivoFallimento = motivoFallimento,
            numeroPersone = numeroPersone,
        )
    }
}

/**
 * ADR 0018 (+ Amendment (b)): 'Ritrascrivi' over a Completata row (AC-448/449), the "Ritrascrizione
 * in coda/in corso" and "Ritrascrizione non riuscita" row states (AC-450/451), and 'Annulla' on a
 * queued row (AC-475/476) — [RegistrazioniPresenter.ritrascrivi]/[RegistrazioniPresenter.annullaElaborazione],
 * both optional (R1/R2), split from `RegistrazioniPresenterTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RegistrazioniRitrascriviTest {
    private val avvii = mutableListOf<AvviaElaborazione>()
    private val annullamenti = mutableListOf<AnnullaElaborazione>()

    @Suppress("LongParameterList") // one parameter per RegistrazioniPresenter collaborator the tests vary
    private fun presentatore(
        scope: TestScope,
        stati: (List<RegistrazioneId>) -> List<StatoRegistrazioneVista>,
        ritrascriviSupportato: Boolean = true,
        annullaSupportato: Boolean = true,
        ritrascrivi: (AvviaElaborazione) -> Esito<Unit> = { Esito.Ok(Unit) },
        annulla: (AnnullaElaborazione) -> Esito<Unit> = { Esito.Ok(Unit) },
    ): RegistrazioniPresenter {
        val dispatcher = StandardTestDispatcher(scope.testScheduler)
        return RegistrazioniPresenter(
            scope = CoroutineScope(dispatcher),
            io = dispatcher,
            registrazioni = { listOf(rigaVista(REG_1)) },
            aggiungiRegistrazione = { error("aggiungi non atteso in questo test") },
            modificaDataRegistrazione = { error("modificaData non atteso in questo test") },
            rinominaRegistrazione = { error("rinomina non atteso in questo test") },
            lettore = LettoreAudioFinta(),
            aggiornamenti = AggiornamentiVistaFinta(),
            clock = Clock.fixed(ORA_FISSA, ZoneOffset.UTC),
            statiElaborazione = stati,
            avviaElaborazione = { error("avviaElaborazione (Trascrivi/Riprova) non atteso in questo test") },
            ritrascrivi = if (ritrascriviSupportato) {
                { c ->
                    avvii += c
                    ritrascrivi(c)
                }
            } else {
                null
            },
            annullaElaborazione = if (annullaSupportato) {
                { c ->
                    annullamenti += c
                    annulla(c)
                }
            } else {
                null
            },
        )
    }

    private fun RegistrazioniPresenter.riga(): RigaRegistrazione =
        assertIs<RegistrazioniUiStato.Dati>(stato.value).righe.single()

    // --- AC-448: Completata + Trascritto, with/without the `ritrascrivi` source ---------------------

    @Test
    fun `AC-448 Completata con ritrascrivi fornito mostra il campo precompilato e Ritrascrivi disponibile`() = runTest {
        val presenter = presentatore(
            this,
            stati = statiCon(StatoElaborazioneVista.COMPLETATA, trascrittoDisponibile = true, numeroPersone = 5),
        )
        advanceUntilIdle()

        val riga = presenter.riga()
        assertEquals(StatoElaborazioneRiga.Completata, riga.elaborazione)
        assertEquals(true, riga.ritrascriviDisponibile)
        assertEquals("5", riga.numeroPersone)
    }

    @Test
    fun `AC-448 Completata senza il servizio R2 non mostra ne campo ne pulsante`() = runTest {
        val presenter = presentatore(
            this,
            ritrascriviSupportato = false,
            stati = statiCon(StatoElaborazioneVista.COMPLETATA, trascrittoDisponibile = true, numeroPersone = 5),
        )
        advanceUntilIdle()

        val riga = presenter.riga()
        assertEquals(false, riga.ritrascriviDisponibile)
        assertEquals("", riga.numeroPersone)
    }

    @Test
    fun `AC-448 la riga Completata apre S3`() = runTest {
        var aperta: RegistrazioneId? = null
        val dispatcher = StandardTestDispatcher(testScheduler)
        val presenter = RegistrazioniPresenter(
            scope = CoroutineScope(dispatcher),
            io = dispatcher,
            registrazioni = { listOf(rigaVista(REG_1)) },
            aggiungiRegistrazione = { error("non atteso") },
            modificaDataRegistrazione = { error("non atteso") },
            rinominaRegistrazione = { error("non atteso") },
            lettore = LettoreAudioFinta(),
            aggiornamenti = AggiornamentiVistaFinta(),
            clock = Clock.fixed(ORA_FISSA, ZoneOffset.UTC),
            statiElaborazione = statiCon(StatoElaborazioneVista.COMPLETATA, trascrittoDisponibile = true),
            apriRegistrazione = { aperta = it },
        )
        advanceUntilIdle()

        presenter.azioni.apriRiga(REG_1)

        assertEquals(REG_1, aperta)
    }

    // --- AC-449: field validation, the inline confirmation, the confirmed command -------------------

    @Test
    fun `AC-449 un numero non valido mostra il messaggio inline e non apre la conferma`() = runTest {
        val presenter = presentatore(
            this,
            stati = statiCon(StatoElaborazioneVista.COMPLETATA, trascrittoDisponibile = true),
        )
        advanceUntilIdle()
        presenter.azioni.modificaNumeroPersone(REG_1, "11")

        presenter.azioni.ritrascrivi(REG_1)

        val riga = presenter.riga()
        assertEquals(MESSAGGIO_NUMERO_PERSONE_NON_VALIDO, riga.erroreRiga)
        assertEquals(false, riga.confermaRitrascrivi)
        assertEquals(emptyList(), avvii)
    }

    @Test
    fun `AC-449 un numero valido apre la conferma inline senza inviare alcun comando`() = runTest {
        val presenter = presentatore(
            this,
            stati = statiCon(StatoElaborazioneVista.COMPLETATA, trascrittoDisponibile = true),
        )
        advanceUntilIdle()
        presenter.azioni.modificaNumeroPersone(REG_1, "4")

        presenter.azioni.ritrascrivi(REG_1)

        val riga = presenter.riga()
        assertEquals(true, riga.confermaRitrascrivi)
        assertNull(riga.erroreRiga)
        assertEquals(emptyList(), avvii)
    }

    @Test
    fun `AC-449 Annulla sulla conferma non invia alcun comando e lascia il campo invariato`() = runTest {
        val presenter = presentatore(
            this,
            stati = statiCon(StatoElaborazioneVista.COMPLETATA, trascrittoDisponibile = true),
        )
        advanceUntilIdle()
        presenter.azioni.modificaNumeroPersone(REG_1, "4")
        presenter.azioni.ritrascrivi(REG_1)

        presenter.azioni.annullaRitrascrivi(REG_1)

        val riga = presenter.riga()
        assertEquals(false, riga.confermaRitrascrivi)
        assertEquals("4", riga.numeroPersone)
        assertEquals(emptyList(), avvii)
    }

    @Test
    fun `AC-449 la conferma invia esattamente un AvviaElaborazione con il valore validato`() = runTest {
        var statoCorrente = StatoElaborazioneVista.COMPLETATA
        val presenter = presentatore(
            this,
            stati = { ids -> ids.map { statoVista(it, statoCorrente, trascrittoDisponibile = true) } },
            ritrascrivi = {
                statoCorrente = StatoElaborazioneVista.IN_ATTESA
                Esito.Ok(Unit)
            },
        )
        advanceUntilIdle()
        presenter.azioni.modificaNumeroPersone(REG_1, "4")
        presenter.azioni.ritrascrivi(REG_1)

        presenter.azioni.confermaRitrascrivi(REG_1)
        advanceUntilIdle()

        assertEquals(listOf(AvviaElaborazione(REG_1, 4)), avvii)
        val riga = presenter.riga()
        assertIs<StatoElaborazioneRiga.InAttesa>(riga.elaborazione)
        assertEquals(false, riga.confermaRitrascrivi)
    }

    @Test
    fun `AC-449 operazioneInCorso blocca un secondo invio della conferma`() = runTest {
        val presenter = presentatore(
            this,
            stati = statiCon(StatoElaborazioneVista.COMPLETATA, trascrittoDisponibile = true),
        )
        advanceUntilIdle()
        presenter.azioni.modificaNumeroPersone(REG_1, "4")
        presenter.azioni.ritrascrivi(REG_1)

        presenter.azioni.confermaRitrascrivi(REG_1) // starts, operazioneInCorso = true synchronously
        presenter.azioni.confermaRitrascrivi(REG_1) // M3: no-op
        advanceUntilIdle()

        assertEquals(1, avvii.size)
    }

    @Test
    fun `AC-449 un errore del comando resta mostrato sulla riga con la conferma ancora aperta`() = runTest {
        val errore = ErroreTrascrizione.ElaborazioneGiaAperta(REG_1)
        val presenter = presentatore(
            this,
            stati = statiCon(StatoElaborazioneVista.COMPLETATA, trascrittoDisponibile = true),
            ritrascrivi = { Esito.Errore(errore) },
        )
        advanceUntilIdle()
        presenter.azioni.modificaNumeroPersone(REG_1, "4")
        presenter.azioni.ritrascrivi(REG_1)

        presenter.azioni.confermaRitrascrivi(REG_1)
        advanceUntilIdle()

        val riga = presenter.riga()
        assertEquals(messaggioPer(errore), riga.erroreRiga)
        assertEquals(true, riga.confermaRitrascrivi)
        assertEquals(false, riga.operazioneInCorso)
    }

    // --- AC-450: a re-run in progress ----------------------------------------------------------------

    @Test
    fun `AC-450 IN_ATTESA con Trascritto mostra Ritrascrizione in coda e Annulla, la riga apre S3`() = runTest {
        val presenter = presentatore(
            this,
            stati = statiCon(StatoElaborazioneVista.IN_ATTESA, trascrittoDisponibile = true, posizioneInCoda = 2),
        )
        advanceUntilIdle()

        val riga = presenter.riga()
        assertEquals(StatoElaborazioneRiga.InAttesa(2, ritrascrizione = true), riga.elaborazione)
        assertEquals(true, riga.annullabile)
        assertEquals(true, riga.trascrittoDisponibile)
    }

    @Test
    fun `AC-450 IN_CORSO con Trascritto mostra Ritrascrizione in corso senza Annulla`() = runTest {
        val presenter = presentatore(
            this,
            stati = { ids ->
                ids.map {
                    statoVista(
                        it,
                        StatoElaborazioneVista.IN_CORSO,
                        trascrittoDisponibile = true,
                        fase = FaseElaborazione.TRASCRIZIONE,
                        avviataAlle = ORA_FISSA.minusSeconds(5),
                    )
                }
            },
        )
        advanceUntilIdle()

        val riga = presenter.riga()
        val inCorso = assertIs<StatoElaborazioneRiga.InCorso>(riga.elaborazione)
        assertEquals(true, inCorso.ritrascrizione)
        assertEquals(false, riga.annullabile) // AC-450/475: never on IN_CORSO
    }

    @Test
    fun `AC-450 una riga senza Trascritto mantiene le etichette semplici di AC-203`() = runTest {
        val presenter = presentatore(
            this,
            stati = statiCon(StatoElaborazioneVista.IN_ATTESA, trascrittoDisponibile = false, posizioneInCoda = 1),
        )
        advanceUntilIdle()

        assertEquals(StatoElaborazioneRiga.InAttesa(1, ritrascrizione = false), presenter.riga().elaborazione)
    }

    // --- AC-451: a failed re-run ----------------------------------------------------------------------

    @Test
    fun `AC-451 FALLITA con Trascritto mostra Completata con il motivo e il campo precompilato`() = runTest {
        val presenter = presentatore(
            this,
            stati = statiCon(
                StatoElaborazioneVista.FALLITA,
                trascrittoDisponibile = true,
                motivoFallimento = "audio illeggibile",
                numeroPersone = 6,
            ),
        )
        advanceUntilIdle()

        val riga = presenter.riga()
        assertEquals(StatoElaborazioneRiga.Completata, riga.elaborazione)
        assertEquals("audio illeggibile", riga.ritrascrizioneFallita)
        assertEquals("6", riga.numeroPersone)
        assertEquals(true, riga.ritrascriviDisponibile)
        assertEquals(true, riga.trascrittoDisponibile)
    }

    @Test
    fun `AC-451 FALLITA senza Trascritto resta Riprova senza notifica di ritrascrizione`() = runTest {
        val presenter = presentatore(
            this,
            stati = statiCon(
                StatoElaborazioneVista.FALLITA,
                trascrittoDisponibile = false,
                motivoFallimento = "guasto",
            ),
        )
        advanceUntilIdle()

        val riga = presenter.riga()
        assertEquals(StatoElaborazioneRiga.Fallita("guasto"), riga.elaborazione)
        assertNull(riga.ritrascrizioneFallita)
    }

    // --- AC-475/476: 'Annulla' on a queued row -----------------------------------------------------

    @Test
    fun `AC-475 annullabile solo su IN_ATTESA e con la sorgente fornita`() = runTest {
        val presenter = presentatore(
            this,
            annullaSupportato = false,
            stati = statiCon(StatoElaborazioneVista.IN_ATTESA, trascrittoDisponibile = false, posizioneInCoda = 1),
        )
        advanceUntilIdle()

        assertEquals(false, presenter.riga().annullabile)
    }

    @Test
    fun `AC-475 Annulla invia esattamente un AnnullaElaborazione con l id della riga`() = runTest {
        val presenter = presentatore(
            this,
            stati = statiCon(StatoElaborazioneVista.IN_ATTESA, trascrittoDisponibile = false, posizioneInCoda = 1),
        )
        advanceUntilIdle()

        presenter.azioni.annullaElaborazione(REG_1)
        advanceUntilIdle()

        assertEquals(listOf(AnnullaElaborazione(ELABORAZIONE_1)), annullamenti)
    }

    @Test
    fun `AC-475 operazioneInCorso blocca un secondo click su Annulla`() = runTest {
        val presenter = presentatore(
            this,
            stati = statiCon(StatoElaborazioneVista.IN_ATTESA, trascrittoDisponibile = false, posizioneInCoda = 1),
        )
        advanceUntilIdle()

        presenter.azioni.annullaElaborazione(REG_1)
        presenter.azioni.annullaElaborazione(REG_1)
        advanceUntilIdle()

        assertEquals(1, annullamenti.size)
    }

    @Test
    fun `AC-476 su Ok la riga ricarica al suo stato precedente NON_AVVIATA`() = runTest {
        var statoCorrente = StatoElaborazioneVista.IN_ATTESA
        val presenter = presentatore(
            this,
            stati = { ids ->
                ids.map { statoVista(it, statoCorrente, trascrittoDisponibile = false, posizioneInCoda = 1) }
            },
            annulla = {
                statoCorrente = StatoElaborazioneVista.NON_AVVIATA
                Esito.Ok(Unit)
            },
        )
        advanceUntilIdle()

        presenter.azioni.annullaElaborazione(REG_1)
        advanceUntilIdle()

        val riga = presenter.riga()
        assertEquals(StatoElaborazioneRiga.NonAvviata, riga.elaborazione)
        assertEquals("", riga.numeroPersone)
        assertNull(riga.erroreRiga)
        assertEquals(false, riga.operazioneInCorso)
    }

    @Test
    fun `AC-476 ElaborazioneGiaAvviata mostra il messaggio inline e ricarica come IN_CORSO`() = runTest {
        var statoCorrente = StatoElaborazioneVista.IN_ATTESA
        val presenter = presentatore(
            this,
            stati = { ids ->
                ids.map { statoVista(it, statoCorrente, trascrittoDisponibile = false, posizioneInCoda = 1) }
            },
            annulla = {
                statoCorrente = StatoElaborazioneVista.IN_CORSO
                Esito.Errore(ErroreTrascrizione.ElaborazioneGiaAvviata(ELABORAZIONE_1))
            },
        )
        advanceUntilIdle()

        presenter.azioni.annullaElaborazione(REG_1)
        advanceUntilIdle()

        val riga = presenter.riga()
        assertEquals(messaggioPer(ErroreTrascrizione.ElaborazioneGiaAvviata(ELABORAZIONE_1)), riga.erroreRiga)
        assertIs<StatoElaborazioneRiga.InCorso>(riga.elaborazione)
        assertEquals(false, riga.operazioneInCorso)
    }

    @Test
    fun `AC-476 ElaborazioneNonTrovata ricarica senza alcun messaggio inline`() = runTest {
        var statoCorrente = StatoElaborazioneVista.IN_ATTESA
        val presenter = presentatore(
            this,
            stati = { ids ->
                ids.map { statoVista(it, statoCorrente, trascrittoDisponibile = false, posizioneInCoda = 1) }
            },
            annulla = {
                statoCorrente = StatoElaborazioneVista.NON_AVVIATA
                Esito.Errore(ErroreTrascrizione.ElaborazioneNonTrovata(ELABORAZIONE_1))
            },
        )
        advanceUntilIdle()

        presenter.azioni.annullaElaborazione(REG_1)
        advanceUntilIdle()

        val riga = presenter.riga()
        assertNull(riga.erroreRiga)
        assertEquals(StatoElaborazioneRiga.NonAvviata, riga.elaborazione)
    }
}
