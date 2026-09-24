package snastro.ui.registrazioni

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import snastro.kernel.ElaborazioneId
import snastro.kernel.RegistrazioneId
import snastro.parlanti.applicazione.letture.ConteggioIdentificazione
import snastro.progetto.applicazione.letture.RegistrazioneDelProgettoVista
import snastro.trascrizione.applicazione.letture.StatoElaborazioneVista
import snastro.trascrizione.applicazione.letture.StatoRegistrazioneVista
import snastro.trascrizione.applicazione.porte.FaseElaborazione
import snastro.ui.AggiornamentiVistaFinta
import snastro.ui.Cambiamento
import snastro.ui.lettore.LettoreAudioFinta
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

private val REG_1 = RegistrazioneId("id-1")

private fun rigaVista(id: RegistrazioneId, titolo: String = "Seduta") =
    RegistrazioneDelProgettoVista(id, titolo, LocalDate.of(2026, 3, 12), 60_000)

@Suppress("LongParameterList") // one parameter per StatoRegistrazioneVista field (mirrors its own shape)
private fun statoVista(
    id: RegistrazioneId,
    stato: StatoElaborazioneVista,
    fase: FaseElaborazione? = null,
    avviataAlle: Instant? = null,
    motivoFallimento: String? = null,
    posizioneInCoda: Int? = null,
    numVoci: Int? = null,
    numeroPersone: Int? = null,
) = StatoRegistrazioneVista(
    id,
    stato,
    fase,
    avviataAlle,
    motivoFallimento,
    posizioneInCoda,
    numVoci,
    numeroPersone,
    trascrittoDisponibile = numVoci != null, // ADR 0018: numVoci is non-null iff a Trascritto exists
    elaborazioneId = ElaborazioneId("elaborazione-${id.valore}").takeIf { stato != StatoElaborazioneVista.NON_AVVIATA },
)

/**
 * AC-204/AC-345 (R2, fetta Parlanti): the identification badge — [RegistrazioniPresenter.identificazioni]
 * (split from `RegistrazioniPresenterTest`, which only builds the presenter with `identificazioni` absent,
 * the R0/R1 default).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RegistrazioniIdentificazioneTest {
    @Suppress("LongParameterList") // one parameter per RegistrazioniPresenter collaborator this file exercises
    private fun presentatore(
        scope: TestScope,
        registrazioni: () -> List<RegistrazioneDelProgettoVista>,
        stati: (List<RegistrazioneId>) -> List<StatoRegistrazioneVista>,
        identificazioni: ((List<RegistrazioneId>) -> List<ConteggioIdentificazione>)? = null,
        aggiornamenti: AggiornamentiVistaFinta = AggiornamentiVistaFinta(),
    ): RegistrazioniPresenter {
        val dispatcher = StandardTestDispatcher(scope.testScheduler)
        return RegistrazioniPresenter(
            scope = CoroutineScope(dispatcher),
            io = dispatcher,
            registrazioni = registrazioni,
            aggiungiRegistrazione = { error("aggiungi non atteso in questo test") },
            modificaDataRegistrazione = { error("modificaData non atteso in questo test") },
            rinominaRegistrazione = { error("rinomina non atteso in questo test") },
            lettore = LettoreAudioFinta(),
            aggiornamenti = aggiornamenti,
            clock = Clock.fixed(Instant.parse("2026-09-23T10:00:00Z"), ZoneOffset.UTC),
            statiElaborazione = stati,
            identificazioni = identificazioni,
        )
    }

    @Test
    fun `AC-204 con numVoci e conteggio noti la riga espone il badge di identificazione`() = runTest {
        val presenter = presentatore(
            this,
            registrazioni = { listOf(rigaVista(REG_1)) },
            stati = { ids -> ids.map { statoVista(it, StatoElaborazioneVista.COMPLETATA, numVoci = 3) } },
            identificazioni = { ids -> ids.map { ConteggioIdentificazione(it, numVociDaIdentificare = 1) } },
        )
        advanceUntilIdle()

        val riga = assertIs<RegistrazioniUiStato.Dati>(presenter.stato.value).righe.single()
        assertEquals(IdentificazioneRiga(numVoci = 3, numVociDaIdentificare = 1), riga.identificazione)
    }

    @Test
    fun `AC-345 senza la sorgente di identificazione la riga non ha badge R0 R1`() = runTest {
        val presenter = presentatore(
            this,
            registrazioni = { listOf(rigaVista(REG_1)) },
            stati = { ids -> ids.map { statoVista(it, StatoElaborazioneVista.COMPLETATA, numVoci = 3) } },
            // identificazioni resta null (default)
        )
        advanceUntilIdle()

        val riga = assertIs<RegistrazioniUiStato.Dati>(presenter.stato.value).righe.single()
        assertNull(riga.identificazione)
    }

    @Test
    fun `AC-345 una riga senza ancora un conteggio non mostra un badge provvisorio`() = runTest {
        val presenter = presentatore(
            this,
            registrazioni = { listOf(rigaVista(REG_1)) },
            stati = { ids -> ids.map { statoVista(it, StatoElaborazioneVista.COMPLETATA, numVoci = 3) } },
            identificazioni = { emptyList() }, // nessun Trascritto ancora per questa riga
        )
        advanceUntilIdle()

        val riga = assertIs<RegistrazioniUiStato.Dati>(presenter.stato.value).righe.single()
        assertNull(riga.identificazione)
    }

    @Test
    fun `AC-345 numVociDaIdentificare a zero espone comunque il badge con il conteggio a zero`() = runTest {
        val presenter = presentatore(
            this,
            registrazioni = { listOf(rigaVista(REG_1)) },
            stati = { ids -> ids.map { statoVista(it, StatoElaborazioneVista.COMPLETATA, numVoci = 3) } },
            identificazioni = { ids -> ids.map { ConteggioIdentificazione(it, numVociDaIdentificare = 0) } },
        )
        advanceUntilIdle()

        val riga = assertIs<RegistrazioniUiStato.Dati>(presenter.stato.value).righe.single()
        assertEquals(IdentificazioneRiga(numVoci = 3, numVociDaIdentificare = 0), riga.identificazione)
    }

    @Test
    fun `AC-345 un errore della sorgente di identificazione lascia la riga senza badge`() = runTest {
        val presenter = presentatore(
            this,
            registrazioni = { listOf(rigaVista(REG_1, titolo = "Seduta del 12 marzo")) },
            stati = { ids -> ids.map { statoVista(it, StatoElaborazioneVista.COMPLETATA, numVoci = 3) } },
            identificazioni = { error("guasto di lettura Parlanti") },
        )
        advanceUntilIdle()

        val riga = assertIs<RegistrazioniUiStato.Dati>(presenter.stato.value).righe.single()
        assertNull(riga.identificazione)
        assertEquals("Seduta del 12 marzo", riga.titolo)
        assertEquals(StatoElaborazioneRiga.Completata, riga.elaborazione)
    }

    @Test
    fun `AC-204 il badge si aggiorna quando arriva un Cambiamento`() = runTest {
        var daIdentificare = 2
        val aggiornamenti = AggiornamentiVistaFinta()
        val presenter = presentatore(
            this,
            registrazioni = { listOf(rigaVista(REG_1)) },
            stati = { ids -> ids.map { statoVista(it, StatoElaborazioneVista.COMPLETATA, numVoci = 3) } },
            identificazioni = { ids ->
                ids.map { ConteggioIdentificazione(it, numVociDaIdentificare = daIdentificare) }
            },
            aggiornamenti = aggiornamenti,
        )
        advanceUntilIdle()
        assertEquals(
            IdentificazioneRiga(3, 2),
            assertIs<RegistrazioniUiStato.Dati>(presenter.stato.value).righe.single().identificazione,
        )

        daIdentificare = 0
        aggiornamenti.emetti(Cambiamento(REG_1))
        advanceUntilIdle()

        assertEquals(
            IdentificazioneRiga(3, 0),
            assertIs<RegistrazioniUiStato.Dati>(presenter.stato.value).righe.single().identificazione,
        )
    }
}
