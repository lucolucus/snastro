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
import snastro.progetto.applicazione.comandi.AggiungiRegistrazione
import snastro.progetto.applicazione.letture.RegistrazioneDelProgettoVista
import snastro.trascrizione.applicazione.comandi.AvviaElaborazione
import snastro.trascrizione.applicazione.letture.StatoElaborazioneVista
import snastro.trascrizione.applicazione.letture.StatoRegistrazioneVista
import snastro.trascrizione.dominio.ErroreTrascrizione
import snastro.ui.AggiornamentiVistaFinta
import snastro.ui.Cambiamento
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

private fun rigaVista(id: RegistrazioneId) =
    RegistrazioneDelProgettoVista(id, "Seduta", LocalDate.of(2026, 3, 12), 60_000)

private val NON_AVVIATE: (List<RegistrazioneId>) -> List<StatoRegistrazioneVista> =
    { ids -> ids.map { statoVista(it, StatoElaborazioneVista.NON_AVVIATA) } }

private fun statoVista(id: RegistrazioneId, stato: StatoElaborazioneVista, numeroPersone: Int? = null) =
    StatoRegistrazioneVista(
        registrazioneId = id,
        stato = stato,
        fase = null,
        avviataAlle = null,
        motivoFallimento = if (stato == StatoElaborazioneVista.FALLITA) "audio illeggibile" else null,
        posizioneInCoda = if (stato == StatoElaborazioneVista.IN_ATTESA) 1 else null,
        numVoci = null,
        numeroPersone = numeroPersone,
        trascrittoDisponibile = false,
        elaborazioneId = ElaborazioneId("elaborazione-${id.valore}")
            .takeIf { stato != StatoElaborazioneVista.NON_AVVIATA },
    )

/**
 * ADR 0014 (R1, Trascrizione sources supplied): the optional 'Numero di persone' field on the S2 row next to
 * 'Trascrivi' (NON_AVVIATA) and 'Riprova' (FALLITA) — presenter-side validation (AC-375), prefill on 'Riprova'
 * (AC-376), no automatic start after an import (AC-372), the command carrying the value (AC-344).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RegistrazioniNumeroPersoneTest {
    private val comandi = mutableListOf<AvviaElaborazione>()

    @Suppress("LongParameterList") // one parameter per RegistrazioniPresenter collaborator the tests vary
    private fun presentatore(
        scope: TestScope,
        stati: (List<RegistrazioneId>) -> List<StatoRegistrazioneVista>,
        registrazioni: () -> List<RegistrazioneDelProgettoVista> = { listOf(rigaVista(REG_1)) },
        aggiungi: (AggiungiRegistrazione) -> Esito<Unit> = { error("aggiungi non atteso in questo test") },
        avvia: (AvviaElaborazione) -> Esito<Unit> = { Esito.Ok(Unit) },
        aggiornamenti: AggiornamentiVistaFinta = AggiornamentiVistaFinta(),
    ): RegistrazioniPresenter {
        val dispatcher = StandardTestDispatcher(scope.testScheduler)
        return RegistrazioniPresenter(
            scope = CoroutineScope(dispatcher),
            io = dispatcher,
            registrazioni = registrazioni,
            aggiungiRegistrazione = aggiungi,
            modificaDataRegistrazione = { error("modificaData non atteso in questo test") },
            rinominaRegistrazione = { error("rinomina non atteso in questo test") },
            lettore = LettoreAudioFinta(),
            aggiornamenti = aggiornamenti,
            clock = Clock.fixed(Instant.parse("2026-09-23T10:00:00Z"), ZoneOffset.UTC),
            statiElaborazione = stati,
            avviaElaborazione = { c ->
                comandi += c
                avvia(c)
            },
        )
    }

    private fun RegistrazioniPresenter.riga(): RigaRegistrazione =
        assertIs<RegistrazioniUiStato.Dati>(stato.value).righe.single()

    @Test
    fun `AC-372 una Registrazione appena aggiunta resta NON_AVVIATA e AvviaElaborazione non e invocato`() = runTest {
        var catalogo = emptyList<RegistrazioneDelProgettoVista>()
        val presenter = presentatore(
            this,
            registrazioni = { catalogo },
            aggiungi = {
                catalogo = listOf(rigaVista(REG_1))
                Esito.Ok(Unit)
            },
            stati = { ids -> ids.map { statoVista(it, StatoElaborazioneVista.NON_AVVIATA) } },
        )
        advanceUntilIdle()

        presenter.azioni.importa(listOf("/tmp/seduta.m4a"))
        advanceUntilIdle()

        assertEquals(emptyList(), comandi)
        assertEquals(StatoElaborazioneRiga.NonAvviata, presenter.riga().elaborazione)
        assertEquals("", presenter.riga().numeroPersone)
    }

    @Test
    fun `AC-344 Trascrivi invia AvviaElaborazione con il numero di persone del campo`() = runTest {
        val presenter = presentatore(this, stati = NON_AVVIATE)
        advanceUntilIdle()

        presenter.azioni.modificaNumeroPersone(REG_1, "4")
        presenter.azioni.avviaElaborazione(REG_1)
        advanceUntilIdle()

        assertEquals(listOf(AvviaElaborazione(REG_1, numeroPersone = 4)), comandi)
    }

    @Test
    fun `AC-344 un NumeroPersoneFuoriIntervallo del comando e mostrato inline e nulla cambia`() = runTest {
        val errore = ErroreTrascrizione.NumeroPersoneFuoriIntervallo(4)
        val presenter = presentatore(
            this,
            stati = { ids -> ids.map { statoVista(it, StatoElaborazioneVista.NON_AVVIATA) } },
            avvia = { Esito.Errore(errore) },
        )
        advanceUntilIdle()
        presenter.azioni.modificaNumeroPersone(REG_1, "4")

        presenter.azioni.avviaElaborazione(REG_1)
        advanceUntilIdle()

        assertEquals(messaggioPer(errore), presenter.riga().erroreRiga)
        assertEquals(StatoElaborazioneRiga.NonAvviata, presenter.riga().elaborazione)
        assertEquals("4", presenter.riga().numeroPersone)
    }

    @Test
    fun `AC-375 vuoto o un intero da 1 a 10 invia il comando con quel valore`() = runTest {
        val casi = listOf("" to null, "1" to 1, "10" to 10, " 7 " to 7)
        val presenter = presentatore(this, stati = NON_AVVIATE)
        advanceUntilIdle()

        casi.forEach { (testo, _) ->
            presenter.azioni.modificaNumeroPersone(REG_1, testo)
            presenter.azioni.avviaElaborazione(REG_1)
            advanceUntilIdle()
        }

        assertEquals(casi.map { (_, n) -> AvviaElaborazione(REG_1, n) }, comandi)
        assertNull(presenter.riga().erroreRiga)
    }

    @Test
    fun `AC-375 L548b valori invalidi mostrano il messaggio inline e nessun comando e invocato`() =
        runTest {
            listOf(StatoElaborazioneVista.NON_AVVIATA, StatoElaborazioneVista.FALLITA).forEach { stato ->
                val presenter = presentatore(this, stati = { ids -> ids.map { statoVista(it, stato) } })
                advanceUntilIdle()

                // L548b: "+4"/"04" (leading sign / leading zero) are no more a sane person count than
                // "0"/"11" — `String.toIntOrNull()` alone would wrongly accept both.
                listOf("0", "11", "tre", "2.5", "-1", "+4", "04").forEach { testo ->
                    presenter.azioni.chiudiErroreRiga(REG_1)
                    presenter.azioni.modificaNumeroPersone(REG_1, testo)
                    presenter.azioni.avviaElaborazione(REG_1)
                    advanceUntilIdle()

                    assertEquals(MESSAGGIO_NUMERO_PERSONE_NON_VALIDO, presenter.riga().erroreRiga, "$stato, '$testo'")
                    assertEquals(testo, presenter.riga().numeroPersone, "il campo resta com'era")
                }
            }

            assertEquals(emptyList(), comandi)
        }

    @Test
    fun `AC-376 su una riga fallita il campo e precompilato col numeroPersone dell Elaborazione fallita`() = runTest {
        val presenter = presentatore(
            this,
            registrazioni = { listOf(rigaVista(REG_1), rigaVista(RegistrazioneId("id-2"))) },
            stati = { ids ->
                listOf(
                    statoVista(ids[0], StatoElaborazioneVista.FALLITA, numeroPersone = 3),
                    statoVista(ids[1], StatoElaborazioneVista.FALLITA, numeroPersone = null),
                )
            },
        )
        advanceUntilIdle()

        val righe = assertIs<RegistrazioniUiStato.Dati>(presenter.stato.value).righe
        assertEquals(listOf("3", ""), righe.map { it.numeroPersone })
    }

    @Test
    fun `AC-376 Riprova invia il valore presente nel campo al momento del click`() = runTest {
        val presenter = presentatore(
            this,
            stati = { ids -> ids.map { statoVista(it, StatoElaborazioneVista.FALLITA, numeroPersone = 3) } },
            avvia = { Esito.Errore(ErroreTrascrizione.ElaborazioneGiaAperta(REG_1)) }, // the row stays fallita
        )
        advanceUntilIdle()

        presenter.azioni.avviaElaborazione(REG_1) // prefilled, untouched
        advanceUntilIdle()
        presenter.azioni.modificaNumeroPersone(REG_1, "5")
        presenter.azioni.avviaElaborazione(REG_1)
        advanceUntilIdle()
        presenter.azioni.modificaNumeroPersone(REG_1, "")
        presenter.azioni.avviaElaborazione(REG_1)
        advanceUntilIdle()

        assertEquals(
            listOf(AvviaElaborazione(REG_1, 3), AvviaElaborazione(REG_1, 5), AvviaElaborazione(REG_1, null)),
            comandi,
        )
    }

    @Test
    fun `AC-376 il valore modificato nel campo sopravvive a un refresh della stessa riga fallita`() = runTest {
        val aggiornamenti = AggiornamentiVistaFinta()
        val presenter = presentatore(
            this,
            stati = { ids -> ids.map { statoVista(it, StatoElaborazioneVista.FALLITA, numeroPersone = 3) } },
            aggiornamenti = aggiornamenti,
        )
        advanceUntilIdle()
        presenter.azioni.modificaNumeroPersone(REG_1, "6")

        aggiornamenti.emetti(Cambiamento(REG_1))
        advanceUntilIdle()

        assertEquals("6", presenter.riga().numeroPersone)
    }

    @Test
    fun `AC-376 quando la riga cambia stato il campo riprende il valore della vista`() = runTest {
        var stato = StatoElaborazioneVista.IN_ATTESA
        val aggiornamenti = AggiornamentiVistaFinta()
        val presenter = presentatore(
            this,
            stati = { ids -> ids.map { statoVista(it, stato, numeroPersone = 4) } },
            aggiornamenti = aggiornamenti,
        )
        advanceUntilIdle()

        stato = StatoElaborazioneVista.FALLITA
        aggiornamenti.emetti(Cambiamento(REG_1))
        advanceUntilIdle()

        assertEquals("4", presenter.riga().numeroPersone)
    }
}
