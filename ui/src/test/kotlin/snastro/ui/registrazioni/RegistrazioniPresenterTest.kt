package snastro.ui.registrazioni

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.progetto.applicazione.comandi.AggiungiRegistrazione
import snastro.progetto.applicazione.comandi.ModificaDataRegistrazione
import snastro.progetto.applicazione.letture.RegistrazioneDelProgettoVista
import snastro.progetto.applicazione.porte.ErroreApplicazioneProgetto
import snastro.progetto.dominio.ErroreProgetto
import snastro.trascrizione.applicazione.comandi.AvviaElaborazione
import snastro.trascrizione.applicazione.letture.StatoElaborazioneVista
import snastro.trascrizione.applicazione.letture.StatoRegistrazioneVista
import snastro.trascrizione.applicazione.porte.FaseElaborazione
import snastro.trascrizione.dominio.ErroreTrascrizione
import snastro.ui.AggiornamentiVistaFinta
import snastro.ui.Cambiamento
import snastro.ui.lettore.LettoreAudio
import snastro.ui.lettore.LettoreAudioFinta
import snastro.ui.lettore.StatoLettore
import snastro.ui.testi.MESSAGGIO_ERRORE_GENERICO
import snastro.ui.testi.messaggioPer
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private val REG_1 = RegistrazioneId("id-1")
private val REG_2 = RegistrazioneId("id-2")
private val DATA_1: LocalDate = LocalDate.of(2026, 3, 12)
private val ORA_FISSA: Instant = Instant.parse("2026-09-23T10:00:00Z")

private fun rigaVista(
    id: RegistrazioneId,
    titolo: String = "Seduta",
    data: LocalDate = DATA_1,
    durataMs: Long = 60_000,
) = RegistrazioneDelProgettoVista(id, titolo, data, durataMs)

@Suppress("LongParameterList") // one parameter per field of the view (mirrors StatoRegistrazioneVista's own shape)
private fun statoVista(
    id: RegistrazioneId,
    stato: StatoElaborazioneVista,
    fase: FaseElaborazione? = null,
    avviataAlle: Instant? = null,
    motivoFallimento: String? = null,
    posizioneInCoda: Int? = null,
    numVoci: Int? = null,
) = StatoRegistrazioneVista(id, stato, fase, avviataAlle, motivoFallimento, posizioneInCoda, numVoci)

/**
 * AC-342: the R0 variant is exercised by simply omitting `stati`/`avvia` from [presentatore] (their
 * defaults) — the same presenter class, constructed with fakes of `RegistrazioniDelProgetto`,
 * `AggiungiRegistrazione`, `ModificaDataRegistrazione` and [LettoreAudio] only.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RegistrazioniPresenterTest {
    @Suppress("LongParameterList") // one parameter per RegistrazioniPresenter collaborator
    private fun presentatore(
        scope: TestScope,
        registrazioni: () -> List<RegistrazioneDelProgettoVista> = { emptyList() },
        aggiungi: (AggiungiRegistrazione) -> Esito<Unit> = { error("aggiungi non atteso in questo test") },
        modificaData: (ModificaDataRegistrazione) -> Esito<Unit> = { error("modificaData non atteso in questo test") },
        lettore: LettoreAudio = LettoreAudioFinta(),
        aggiornamenti: AggiornamentiVistaFinta = AggiornamentiVistaFinta(),
        clock: Clock = Clock.fixed(ORA_FISSA, ZoneOffset.UTC),
        stati: ((List<RegistrazioneId>) -> List<StatoRegistrazioneVista>)? = null,
        avvia: ((AvviaElaborazione) -> Esito<Unit>)? = null,
        apriRegistrazione: (RegistrazioneId) -> Unit = {},
    ): RegistrazioniPresenter {
        val dispatcher = StandardTestDispatcher(scope.testScheduler)
        return RegistrazioniPresenter(
            CoroutineScope(dispatcher),
            dispatcher,
            registrazioni,
            aggiungi,
            modificaData,
            lettore,
            aggiornamenti,
            clock,
            stati,
            avvia,
            apriRegistrazione,
        )
    }

    @Test
    fun `AC-200 prima del caricamento lo stato e Caricamento`() = runTest {
        val presenter = presentatore(this)
        assertEquals(RegistrazioniUiStato.Caricamento, presenter.stato.value)
    }

    @Test
    fun `AC-199 un catalogo vuoto produce una lista vuota`() = runTest {
        val presenter = presentatore(this)
        advanceUntilIdle()
        assertEquals(RegistrazioniUiStato.Dati(righe = emptyList()), presenter.stato.value)
    }

    @Test
    fun `AC-202 l ordine delle righe e quello del catalogo, non riordinato`() = runTest {
        val presenter = presentatore(this, registrazioni = { listOf(rigaVista(REG_2), rigaVista(REG_1)) })
        advanceUntilIdle()
        val dati = assertIs<RegistrazioniUiStato.Dati>(presenter.stato.value)
        assertEquals(listOf(REG_2, REG_1), dati.righe.map { it.registrazioneId })
    }

    @Test
    fun `le righe espongono titolo data e durata dal catalogo`() = runTest {
        val presenter = presentatore(
            this,
            registrazioni = { listOf(rigaVista(REG_1, titolo = "Seduta del 12 marzo", durataMs = 125_000)) },
        )
        advanceUntilIdle()
        val riga = assertIs<RegistrazioniUiStato.Dati>(presenter.stato.value).righe.single()
        assertEquals("Seduta del 12 marzo", riga.titolo)
        assertEquals(DATA_1, riga.dataRegistrazione)
        assertEquals(125_000L, riga.durataMs)
    }

    @Test
    fun `un fallimento nel caricare il catalogo mostra un errore invece di restare in Caricamento`() = runTest {
        val presenter = presentatore(this, registrazioni = { error("guasto di lettura") })
        advanceUntilIdle()

        val dati = assertIs<RegistrazioniUiStato.Dati>(presenter.stato.value)
        assertEquals(MESSAGGIO_ERRORE_GENERICO, dati.errore)
        assertEquals(emptyList(), dati.righe)
    }

    // --- AC-342: R0 variant, no Trascrizione sources -------------------------------------------

    @Test
    fun `AC-342 senza StatiElaborazione le righe non hanno colonna di stato`() = runTest {
        val presenter = presentatore(this, registrazioni = { listOf(rigaVista(REG_1)) })
        advanceUntilIdle()
        val riga = assertIs<RegistrazioniUiStato.Dati>(presenter.stato.value).righe.single()
        assertNull(riga.elaborazione)
    }

    @Test
    fun `AC-342 il click su una riga non apre S3 quando le sorgenti sono assenti`() = runTest {
        var aperta: RegistrazioneId? = null
        val presenter = presentatore(
            this,
            registrazioni = { listOf(rigaVista(REG_1)) },
            apriRegistrazione = { aperta = it },
        )
        advanceUntilIdle()

        presenter.azioni.apriRiga(REG_1)

        assertNull(aperta)
    }

    @Test
    fun `AC-342 avviaElaborazione senza il servizio R1 non fa nulla`() = runTest {
        val presenter = presentatore(this, registrazioni = { listOf(rigaVista(REG_1)) })
        advanceUntilIdle()

        presenter.azioni.avviaElaborazione(REG_1) // `avvia` è `null` (R0)

        val riga = assertIs<RegistrazioniUiStato.Dati>(presenter.stato.value).righe.single()
        assertEquals(false, riga.operazioneInCorso)
        assertNull(riga.erroreRiga)
    }

    // --- AC-343: per-row playback ----------------------------------------------------------------

    @Test
    fun `AC-343 disponibile falso disabilita il controllo di riproduzione`() = runTest {
        val presenter = presentatore(
            this,
            registrazioni = { listOf(rigaVista(REG_1)) },
            lettore = LettoreAudioFinta(nonDisponibili = setOf(REG_1)),
        )
        advanceUntilIdle()
        val riga = assertIs<RegistrazioniUiStato.Dati>(presenter.stato.value).righe.single()
        assertEquals(StatoRiproduzioneRiga.NonDisponibile, riga.riproduzione)
    }

    @Test
    fun `AC-343 riproduci avvia la riproduzione dall inizio e la riga mostra pausa`() = runTest {
        val fake = LettoreAudioFinta()
        val presenter = presentatore(this, registrazioni = { listOf(rigaVista(REG_1)) }, lettore = fake)
        advanceUntilIdle()

        presenter.azioni.riproduci(REG_1)
        advanceUntilIdle()

        assertEquals(StatoLettore(REG_1, 0, true), fake.stato.value)
        val riga = assertIs<RegistrazioniUiStato.Dati>(presenter.stato.value).righe.single()
        assertEquals(StatoRiproduzioneRiga.InRiproduzione, riga.riproduzione)
    }

    @Test
    fun `AC-343 riprodurre un altra riga sostituisce la riproduzione in corso`() = runTest {
        val fake = LettoreAudioFinta()
        val presenter = presentatore(
            this,
            registrazioni = { listOf(rigaVista(REG_1), rigaVista(REG_2)) },
            lettore = fake,
        )
        advanceUntilIdle()
        presenter.azioni.riproduci(REG_1)
        advanceUntilIdle()

        presenter.azioni.riproduci(REG_2)
        advanceUntilIdle()

        val righe = assertIs<RegistrazioniUiStato.Dati>(presenter.stato.value).righe.associateBy { it.registrazioneId }
        assertEquals(StatoRiproduzioneRiga.Disponibile, righe.getValue(REG_1).riproduzione)
        assertEquals(StatoRiproduzioneRiga.InRiproduzione, righe.getValue(REG_2).riproduzione)
    }

    @Test
    fun `AC-343 pausa ferma la riproduzione della riga attiva`() = runTest {
        val fake = LettoreAudioFinta()
        val presenter = presentatore(this, registrazioni = { listOf(rigaVista(REG_1)) }, lettore = fake)
        advanceUntilIdle()
        presenter.azioni.riproduci(REG_1)
        advanceUntilIdle()

        presenter.azioni.pausa()
        advanceUntilIdle()

        val riga = assertIs<RegistrazioniUiStato.Dati>(presenter.stato.value).righe.single()
        assertEquals(StatoRiproduzioneRiga.Disponibile, riga.riproduzione)
        assertEquals(false, fake.stato.value.inRiproduzione)
    }

    // --- AC-199/200/201: import -------------------------------------------------------------------

    @Test
    fun `AC-201 un errore di importazione e mostrato inline e non aggiunge righe`() = runTest {
        val presenter = presentatore(
            this,
            registrazioni = { emptyList() },
            aggiungi = { Esito.Errore(ErroreApplicazioneProgetto.AudioNonLeggibile(it.percorsoSorgente)) },
        )
        advanceUntilIdle()

        presenter.azioni.importa("/sorgenti/x.m4a")
        advanceUntilIdle()

        val dati = assertIs<RegistrazioniUiStato.Dati>(presenter.stato.value)
        assertEquals(messaggioPer(ErroreApplicazioneProgetto.AudioNonLeggibile("/sorgenti/x.m4a")), dati.errore)
        assertEquals(emptyList(), dati.righe)
        assertEquals(false, dati.importoInCorso)
    }

    @Test
    fun `un import riuscito ricarica la lista`() = runTest {
        var righeCorrenti = listOf<RegistrazioneDelProgettoVista>()
        val presenter = presentatore(
            this,
            registrazioni = { righeCorrenti },
            aggiungi = {
                righeCorrenti = listOf(rigaVista(REG_1))
                Esito.Ok(Unit)
            },
        )
        advanceUntilIdle()
        assertEquals(emptyList(), assertIs<RegistrazioniUiStato.Dati>(presenter.stato.value).righe)

        presenter.azioni.importa("/sorgenti/x.m4a")
        advanceUntilIdle()

        val dati = assertIs<RegistrazioniUiStato.Dati>(presenter.stato.value)
        assertEquals(listOf(REG_1), dati.righe.map { it.registrazioneId })
        assertEquals(false, dati.importoInCorso)
    }

    @Test
    fun `M3 due importa ravvicinati eseguono aggiungiRegistrazione una sola volta`() = runTest {
        var chiamate = 0
        val presenter = presentatore(
            this,
            aggiungi = {
                chiamate++
                Esito.Ok(Unit)
            },
        )
        advanceUntilIdle()

        presenter.azioni.importa("/sorgenti/a.m4a")
        presenter.azioni.importa("/sorgenti/b.m4a")
        advanceUntilIdle()

        assertEquals(1, chiamate)
    }

    @Test
    fun `un eccezione non di cancellazione durante l import mostra il messaggio generico`() = runTest {
        val presenter = presentatore(this, aggiungi = { throw IllegalStateException("guasto") })
        advanceUntilIdle()

        presenter.azioni.importa("/sorgenti/x.m4a")
        advanceUntilIdle()

        val dati = assertIs<RegistrazioniUiStato.Dati>(presenter.stato.value)
        assertEquals(MESSAGGIO_ERRORE_GENERICO, dati.errore)
        assertEquals(false, dati.importoInCorso)
    }

    // --- AC-206: date editing -----------------------------------------------------------------

    @Test
    fun `AC-206 modificaData riuscita ricarica la lista con la nuova data`() = runTest {
        var dataCorrente = DATA_1
        val nuovaData = LocalDate.of(2026, 1, 2)
        val presenter = presentatore(
            this,
            registrazioni = { listOf(rigaVista(REG_1, data = dataCorrente)) },
            modificaData = { c ->
                dataCorrente = c.nuovaData
                Esito.Ok(Unit)
            },
        )
        advanceUntilIdle()

        presenter.azioni.modificaData(REG_1, nuovaData)
        advanceUntilIdle()

        val riga = assertIs<RegistrazioniUiStato.Dati>(presenter.stato.value).righe.single()
        assertEquals(nuovaData, riga.dataRegistrazione)
    }

    @Test
    fun `AC-206 un errore di modificaData e mostrato inline sulla riga e nulla cambia`() = runTest {
        val presenter = presentatore(
            this,
            registrazioni = { listOf(rigaVista(REG_1)) },
            modificaData = { Esito.Errore(ErroreProgetto.RegistrazioneNonTrovata(REG_1)) },
        )
        advanceUntilIdle()

        presenter.azioni.modificaData(REG_1, LocalDate.of(2026, 1, 2))
        advanceUntilIdle()

        val riga = assertIs<RegistrazioniUiStato.Dati>(presenter.stato.value).righe.single()
        assertEquals(messaggioPer(ErroreProgetto.RegistrazioneNonTrovata(REG_1)), riga.erroreRiga)
        assertEquals(false, riga.operazioneInCorso)
        assertEquals(DATA_1, riga.dataRegistrazione)
    }

    @Test
    fun `M3 due modificaData ravvicinate sulla stessa riga eseguono il servizio una sola volta`() = runTest {
        var chiamate = 0
        val presenter = presentatore(
            this,
            registrazioni = { listOf(rigaVista(REG_1)) },
            modificaData = {
                chiamate++
                Esito.Ok(Unit)
            },
        )
        advanceUntilIdle()

        presenter.azioni.modificaData(REG_1, LocalDate.of(2026, 1, 2))
        presenter.azioni.modificaData(REG_1, LocalDate.of(2026, 1, 3))
        advanceUntilIdle()

        assertEquals(1, chiamate)
    }

    // --- AC-203/AC-344 (R1): Trascrizione sources supplied --------------------------------------

    @Test
    fun `AC-203 NON_AVVIATA e mappata su NonAvviata`() = runTest {
        val presenter = presentatore(
            this,
            registrazioni = { listOf(rigaVista(REG_1)) },
            stati = { ids -> ids.map { statoVista(it, StatoElaborazioneVista.NON_AVVIATA) } },
        )
        advanceUntilIdle()
        val riga = assertIs<RegistrazioniUiStato.Dati>(presenter.stato.value).righe.single()
        assertEquals(StatoElaborazioneRiga.NonAvviata, riga.elaborazione)
    }

    @Test
    fun `AC-203 IN_ATTESA espone la posizione in coda`() = runTest {
        val presenter = presentatore(
            this,
            registrazioni = { listOf(rigaVista(REG_1)) },
            stati = { ids -> ids.map { statoVista(it, StatoElaborazioneVista.IN_ATTESA, posizioneInCoda = 3) } },
        )
        advanceUntilIdle()
        val riga = assertIs<RegistrazioniUiStato.Dati>(presenter.stato.value).righe.single()
        assertEquals(StatoElaborazioneRiga.InAttesa(3), riga.elaborazione)
    }

    @Test
    fun `AC-203 IN_CORSO espone la fase e il tempo trascorso da avviataAlle`() = runTest {
        val avviataAlle = ORA_FISSA.minusSeconds(192) // 3:12
        val presenter = presentatore(
            this,
            registrazioni = { listOf(rigaVista(REG_1)) },
            stati = { ids ->
                ids.map {
                    statoVista(
                        it,
                        StatoElaborazioneVista.IN_CORSO,
                        fase = FaseElaborazione.DIARIZZAZIONE,
                        avviataAlle = avviataAlle,
                    )
                }
            },
        )
        advanceUntilIdle()
        val riga = assertIs<RegistrazioniUiStato.Dati>(presenter.stato.value).righe.single()
        val inCorso = assertIs<StatoElaborazioneRiga.InCorso>(riga.elaborazione)
        assertEquals("separazione voci", inCorso.faseEtichetta)
        assertEquals(192_000L, inCorso.trascorsoMs)
    }

    @Test
    fun `AC-203 FALLITA espone il motivo`() = runTest {
        val presenter = presentatore(
            this,
            registrazioni = { listOf(rigaVista(REG_1)) },
            stati = { ids ->
                ids.map { statoVista(it, StatoElaborazioneVista.FALLITA, motivoFallimento = "audio illeggibile") }
            },
        )
        advanceUntilIdle()
        val riga = assertIs<RegistrazioniUiStato.Dati>(presenter.stato.value).righe.single()
        assertEquals(StatoElaborazioneRiga.Fallita("audio illeggibile"), riga.elaborazione)
    }

    @Test
    fun `AC-203 COMPLETATA rende la riga apribile`() = runTest {
        var aperta: RegistrazioneId? = null
        val presenter = presentatore(
            this,
            registrazioni = { listOf(rigaVista(REG_1)) },
            stati = { ids -> ids.map { statoVista(it, StatoElaborazioneVista.COMPLETATA) } },
            apriRegistrazione = { aperta = it },
        )
        advanceUntilIdle()

        presenter.azioni.apriRiga(REG_1)

        assertEquals(REG_1, aperta)
    }

    @Test
    fun `AC-205 la riga si aggiorna quando arriva un Cambiamento`() = runTest {
        var statoCorrente = StatoElaborazioneVista.IN_ATTESA
        val aggiornamenti = AggiornamentiVistaFinta()
        val presenter = presentatore(
            this,
            registrazioni = { listOf(rigaVista(REG_1)) },
            stati = { ids -> ids.map { statoVista(it, statoCorrente, posizioneInCoda = 1) } },
            aggiornamenti = aggiornamenti,
        )
        advanceUntilIdle()
        assertEquals(
            StatoElaborazioneRiga.InAttesa(1),
            assertIs<RegistrazioniUiStato.Dati>(presenter.stato.value).righe.single().elaborazione,
        )

        statoCorrente = StatoElaborazioneVista.COMPLETATA
        aggiornamenti.emetti(Cambiamento(REG_1))
        advanceUntilIdle()

        assertEquals(
            StatoElaborazioneRiga.Completata,
            assertIs<RegistrazioniUiStato.Dati>(presenter.stato.value).righe.single().elaborazione,
        )
    }

    @Test
    fun `AC-344 Trascrivi invoca AvviaElaborazione e ricarica la lista`() = runTest {
        var chiamata: AvviaElaborazione? = null
        var statoCorrente = StatoElaborazioneVista.NON_AVVIATA
        val presenter = presentatore(
            this,
            registrazioni = { listOf(rigaVista(REG_1)) },
            stati = { ids -> ids.map { statoVista(it, statoCorrente) } },
            avvia = { c ->
                chiamata = c
                statoCorrente = StatoElaborazioneVista.IN_ATTESA
                Esito.Ok(Unit)
            },
        )
        advanceUntilIdle()

        presenter.azioni.avviaElaborazione(REG_1)
        advanceUntilIdle()

        assertEquals(AvviaElaborazione(REG_1), chiamata)
        val riga = assertIs<RegistrazioniUiStato.Dati>(presenter.stato.value).righe.single()
        assertIs<StatoElaborazioneRiga.InAttesa>(riga.elaborazione)
    }

    @Test
    fun `AC-344 un errore del comando e mostrato inline sulla riga e nulla cambia`() = runTest {
        val presenter = presentatore(
            this,
            registrazioni = { listOf(rigaVista(REG_1)) },
            stati = { ids -> ids.map { statoVista(it, StatoElaborazioneVista.NON_AVVIATA) } },
            avvia = { Esito.Errore(ErroreTrascrizione.RegistrazioneNonTrovata(REG_1)) },
        )
        advanceUntilIdle()

        presenter.azioni.avviaElaborazione(REG_1)
        advanceUntilIdle()

        val riga = assertIs<RegistrazioniUiStato.Dati>(presenter.stato.value).righe.single()
        assertEquals(messaggioPer(ErroreTrascrizione.RegistrazioneNonTrovata(REG_1)), riga.erroreRiga)
        assertEquals(StatoElaborazioneRiga.NonAvviata, riga.elaborazione)
        assertEquals(false, riga.operazioneInCorso)
    }

    // --- H1: dismissible inline messages --------------------------------------------------------

    @Test
    fun `H1 chiudiErrore e chiudiErroreRiga rimuovono solo il rispettivo messaggio`() = runTest {
        val presenter = presentatore(
            this,
            registrazioni = { listOf(rigaVista(REG_1)) },
            aggiungi = { Esito.Errore(ErroreApplicazioneProgetto.AudioNonLeggibile(it.percorsoSorgente)) },
            modificaData = { Esito.Errore(ErroreProgetto.RegistrazioneNonTrovata(REG_1)) },
        )
        advanceUntilIdle()
        presenter.azioni.importa("/sorgenti/x.m4a")
        advanceUntilIdle()
        presenter.azioni.modificaData(REG_1, LocalDate.of(2026, 1, 2))
        advanceUntilIdle()

        var dati = assertIs<RegistrazioniUiStato.Dati>(presenter.stato.value)
        assertNotNull(dati.errore)
        assertNotNull(dati.righe.single().erroreRiga)

        presenter.azioni.chiudiErrore()
        dati = assertIs<RegistrazioniUiStato.Dati>(presenter.stato.value)
        assertNull(dati.errore)
        assertNotNull(dati.righe.single().erroreRiga)

        presenter.azioni.chiudiErroreRiga(REG_1)
        dati = assertIs<RegistrazioniUiStato.Dati>(presenter.stato.value)
        assertNull(dati.righe.single().erroreRiga)
    }
}
