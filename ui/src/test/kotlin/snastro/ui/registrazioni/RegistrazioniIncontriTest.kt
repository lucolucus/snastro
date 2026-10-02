package snastro.ui.registrazioni

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import snastro.kernel.ElaborazioneId
import snastro.kernel.Esito
import snastro.kernel.IncontroId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.parlanti.applicazione.letture.IdentificazioneIncontro
import snastro.progetto.applicazione.letture.IncontroDelProgettoVista
import snastro.progetto.applicazione.letture.ParteVista
import snastro.progetto.applicazione.letture.RegistrazioneDelProgettoVista
import snastro.progetto.dominio.ErroreProgetto
import snastro.trascrizione.applicazione.comandi.AvviaElaborazioniDellIncontro
import snastro.trascrizione.applicazione.letture.StatoElaborazioneVista
import snastro.trascrizione.applicazione.letture.StatoRegistrazioneVista
import snastro.trascrizione.applicazione.porte.FaseElaborazione
import snastro.ui.AggiornamentiVistaFinta
import snastro.ui.Cambiamento
import snastro.ui.coda.PosizioniCoda
import snastro.ui.lettore.LettoreAudioFinta
import snastro.ui.testi.MESSAGGIO_NUMERO_PERSONE_NON_VALIDO
import snastro.ui.testi.etichettaParteInCoda
import snastro.ui.testi.etichettaParteInCorso
import snastro.ui.testi.etichettaParteNonRiuscita
import snastro.ui.testi.etichettaTrascriviParti
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val P1 = RegistrazioneId("parte-1")
private val P2 = RegistrazioneId("parte-2")
private val P3 = RegistrazioneId("parte-3")
private val INCONTRO = IncontroId("incontro-1")
private const val TITOLO = "Riunione di staff"
private val DATA = LocalDate.of(2026, 9, 30)
private val AVVIATA = Instant.parse("2026-09-30T10:00:00Z")
private val ORA_FISSA = AVVIATA.plusSeconds(192) // 3:12 after `avviataAlle`

private fun vista(id: RegistrazioneId, titolo: String) = RegistrazioneDelProgettoVista(id, titolo, DATA, 60_000)

private fun stato(id: RegistrazioneId, s: StatoElaborazioneVista, numeroPersone: Int? = null): StatoRegistrazioneVista {
    val inCorso = s == StatoElaborazioneVista.IN_CORSO
    val completata = s == StatoElaborazioneVista.COMPLETATA
    return StatoRegistrazioneVista(
        registrazioneId = id,
        stato = s,
        fase = if (inCorso) FaseElaborazione.DIARIZZAZIONE else null,
        avviataAlle = if (inCorso) AVVIATA else null,
        motivoFallimento = if (s == StatoElaborazioneVista.FALLITA) "audio illeggibile" else null,
        numVoci = if (completata) 3 else null,
        numeroPersone = numeroPersone,
        trascrittoDisponibile = completata,
        elaborazioneId = ElaborazioneId("e-${id.valore}"),
    )
}

/**
 * `schermata-incontri`: S2 per Incontro — the aggregated state (AC-I66), 'Trascrivi N parti' (AC-I67), the Parte
 * sub-rows and their start time (AC-I68), the states and the expansion (AC-I69).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RegistrazioniIncontriTest {
    private var ordine = mutableListOf(P1 to null as LocalTime?, P2 to null, P3 to null)
    private var stati = mapOf<RegistrazioneId, StatoElaborazioneVista>()
    private var posizioni = PosizioniCoda.VUOTA
    private var precompilato: Int? = null
    private var rifiutaOra = false
    private var lettureIncontri: () -> List<IncontroDelProgettoVista> = { incontri() }
    private var lettureRegistrazioni: () -> List<RegistrazioneDelProgettoVista> = {
        ordine.mapIndexed { i, (id, _) -> vista(id, if (i == 0) TITOLO else "file ${i + 1}") }
    }
    private val avvii = mutableListOf<AvviaElaborazioniDellIncontro>()
    private val ore = mutableListOf<Pair<RegistrazioneId, LocalTime?>>()
    private val aggiornamenti = AggiornamentiVistaFinta()

    private fun incontri() = listOf(
        IncontroDelProgettoVista(
            incontroId = INCONTRO,
            titolo = TITOLO,
            data = DATA,
            durataMs = 60_000L * ordine.size,
            numParti = ordine.size,
            parti = ordine.mapIndexed { i, (id, ora) ->
                ParteVista(id, i + 1, "file ${i + 1}", DATA, ora, 60_000)
            },
        ),
    )

    private fun presentatore(scope: TestScope): RegistrazioniPresenter {
        val dispatcher = StandardTestDispatcher(scope.testScheduler)
        return RegistrazioniPresenter(
            scope = CoroutineScope(dispatcher),
            io = dispatcher,
            progettoId = ProgettoId("progetto-1"),
            registrazioni = { lettureRegistrazioni() },
            incontri = { lettureIncontri() },
            aggiungiRegistrazione = { error("non atteso") },
            modificaDataRegistrazione = { error("non atteso") },
            rinominaRegistrazione = { error("non atteso") },
            lettore = LettoreAudioFinta(),
            aggiornamenti = aggiornamenti,
            clock = Clock.fixed(ORA_FISSA, ZoneOffset.UTC),
            statiElaborazione = { ids ->
                ids.map { stato(it, stati[it] ?: StatoElaborazioneVista.COMPLETATA, numeroPersone = precompilato) }
            },
            avviaElaborazione = { error("non atteso") },
            apriRegistrazione = {},
            identificazioniIncontri = { ids -> ids.associateWith { IdentificazioneIncontro(4, 1) } },
            ritrascrivi = { error("non atteso") },
            annullaElaborazione = { error("non atteso") },
            eliminaRegistrazione = { error("non atteso") },
            posizioniNellaCoda = { posizioni },
            avviaElaborazioniDellIncontro = { c ->
                avvii += c
                Esito.Ok(Unit)
            },
            modificaOraDiInizioRegistrazione = { id, ora ->
                ore += id to ora
                if (rifiutaOra) Esito.Errore(ErroreProgetto.OraDiInizioNonValida) else Esito.Ok(Unit)
            },
            numeroPersonePrecompilato = { precompilato },
        )
    }

    private fun RegistrazioniPresenter.dati() = assertIs<RegistrazioniUiStato.Dati>(stato.value)
    private fun RegistrazioniPresenter.incontro() = dati().incontri.single()

    // ---- AC-I66 -----------------------------------------------------------------------------------------------

    @Test
    fun `AC-I66 una Parte in corso vince su tutte e il testo nomina la Parte`() = runTest {
        stati = mapOf(
            P1 to StatoElaborazioneVista.IN_ATTESA,
            P2 to StatoElaborazioneVista.IN_CORSO,
            P3 to StatoElaborazioneVista.FALLITA,
        )
        val p = presentatore(this)
        advanceUntilIdle()

        val s = assertIs<StatoIncontro.ParteInCorso>(p.incontro().stato)
        assertEquals(2, s.numero)
        assertEquals(
            "Parte 2 · In corso · separazione voci · 3:12",
            etichettaParteInCorso(s.numero, s.faseEtichetta, s.trascorsoMs, s.ritrascrizione),
        )
    }

    @Test
    fun `AC-I66 una Parte in coda vince su una fallita e porta la sua posizione e il suo id per Annulla`() = runTest {
        stati = mapOf(
            P1 to StatoElaborazioneVista.COMPLETATA,
            P2 to StatoElaborazioneVista.IN_ATTESA,
            P3 to StatoElaborazioneVista.FALLITA,
        )
        posizioni = PosizioniCoda(mapOf(P2 to 1), emptyMap())
        val p = presentatore(this)
        advanceUntilIdle()

        val s = assertIs<StatoIncontro.ParteInCoda>(p.incontro().stato)
        assertEquals(StatoIncontro.ParteInCoda(2, 1, false, P2), s)
        assertEquals("Parte 2 · In coda · 1", etichettaParteInCoda(s.numero, s.posizione, s.ritrascrizione))
    }

    @Test
    fun `AC-I66 una Parte fallita vince sulle Parti mai avviate`() = runTest {
        stati = mapOf(
            P1 to StatoElaborazioneVista.COMPLETATA,
            P2 to StatoElaborazioneVista.FALLITA,
            P3 to StatoElaborazioneVista.NON_AVVIATA,
        )
        val p = presentatore(this)
        advanceUntilIdle()

        val s = assertIs<StatoIncontro.ParteNonRiuscita>(p.incontro().stato)
        assertEquals("Parte 2 non riuscita", etichettaParteNonRiuscita(s.numero))
    }

    @Test
    fun `AC-I66 Parti mai avviate offrono Trascrivi N parti, una sola Trascrivi`() = runTest {
        stati = mapOf(
            P1 to StatoElaborazioneVista.COMPLETATA,
            P2 to StatoElaborazioneVista.NON_AVVIATA,
            P3 to StatoElaborazioneVista.NON_AVVIATA,
        )
        val p = presentatore(this)
        advanceUntilIdle()

        val s = assertIs<StatoIncontro.DaTrascrivere>(p.incontro().stato)
        assertEquals(2, s.numParti)
        assertEquals("Trascrivi 2 parti", etichettaTrascriviParti(s.numParti))
        assertEquals("Trascrivi", etichettaTrascriviParti(1))
    }

    @Test
    fun `AC-I66 tutte le Parti completate danno Completata con il badge dell'Incontro`() = runTest {
        val p = presentatore(this)
        advanceUntilIdle()

        assertEquals(StatoIncontro.Completata, p.incontro().stato)
        assertEquals(IdentificazioneRiga(4, 1), p.incontro().identificazione)
        assertTrue(p.dati().righe.all { it.identificazione == null }) // the badge lives on the Incontro row
    }

    // ---- AC-I67 -----------------------------------------------------------------------------------------------

    @Test
    fun `AC-I67 Trascrivi N parti invia AvviaElaborazioniDellIncontro col campo precompilato`() = runTest {
        stati = mapOf(
            P1 to StatoElaborazioneVista.NON_AVVIATA,
            P2 to StatoElaborazioneVista.NON_AVVIATA,
            P3 to StatoElaborazioneVista.COMPLETATA,
        )
        precompilato = 4
        val p = presentatore(this)
        advanceUntilIdle()
        assertEquals("4", p.incontro().numeroPersone)

        p.avviaElaborazioniIncontro(INCONTRO)
        advanceUntilIdle()

        assertEquals(listOf(AvviaElaborazioniDellIncontro(INCONTRO, 4)), avvii)
        assertNull(p.incontro().errore)
        assertFalse(p.incontro().operazioneInCorso)
    }

    @Test
    fun `AC-I67 il campo vuoto e automatico e un valore non valido non invia nulla`() = runTest {
        stati = mapOf(P1 to StatoElaborazioneVista.NON_AVVIATA, P2 to StatoElaborazioneVista.NON_AVVIATA)
        ordine = mutableListOf(P1 to null, P2 to null)
        val p = presentatore(this)
        advanceUntilIdle()

        p.modificaNumeroPersoneIncontro(INCONTRO, "11")
        p.avviaElaborazioniIncontro(INCONTRO)
        advanceUntilIdle()
        assertEquals(MESSAGGIO_NUMERO_PERSONE_NON_VALIDO, p.incontro().errore)
        assertTrue(avvii.isEmpty())

        p.modificaNumeroPersoneIncontro(INCONTRO, "")
        p.avviaElaborazioniIncontro(INCONTRO)
        advanceUntilIdle()
        assertEquals(listOf(AvviaElaborazioniDellIncontro(INCONTRO, null)), avvii)
        assertNull(p.incontro().errore)
    }

    // ---- AC-I68 -----------------------------------------------------------------------------------------------

    @Test
    fun `AC-I68 le sotto-righe seguono l'ordine delle Parti con numero e ora`() = runTest {
        ordine = mutableListOf(P1 to LocalTime.of(10, 0), P2 to null, P3 to LocalTime.of(11, 30))
        val p = presentatore(this)
        advanceUntilIdle()

        assertEquals(listOf(P1, P2, P3), p.incontro().parti)
        assertEquals(listOf(P1, P2, P3), p.dati().righe.map { it.registrazioneId })
        assertEquals(listOf(1, 2, 3), p.dati().righe.map { it.parte?.numero })
        assertEquals(listOf(LocalTime.of(10, 0), null, LocalTime.of(11, 30)), p.dati().righe.map { it.oraDiInizio })
        assertEquals(TITOLO, p.incontro().titolo)
        assertEquals(180_000, p.incontro().durataMs)
    }

    @Test
    fun `AC-I68 modificare l'ora invia ModificaOraDiInizio e le righe si riordinano al ricaricamento`() = runTest {
        val p = presentatore(this)
        advanceUntilIdle()

        p.modificaOraDiInizio(P2, LocalTime.of(9, 5))
        // the read model's answer after the edit
        ordine = mutableListOf(P2 to LocalTime.of(9, 5), P1 to LocalTime.of(10, 0), P3 to null)
        advanceUntilIdle()

        assertEquals(listOf(P2 to LocalTime.of(9, 5)), ore)
        assertEquals(listOf(P2, P1, P3), p.incontro().parti)
        assertEquals(listOf(P2, P1, P3), p.dati().righe.map { it.registrazioneId })
        assertEquals(listOf(1, 2, 3), p.dati().righe.map { it.parte?.numero })
    }

    @Test
    fun `AC-I68 svuotare l'ora la cancella, un rifiuto resta sulla riga della Parte`() = runTest {
        val p = presentatore(this)
        advanceUntilIdle()

        p.modificaOraDiInizio(P1, null)
        advanceUntilIdle()
        assertEquals(listOf<Pair<RegistrazioneId, LocalTime?>>(P1 to null), ore)

        rifiutaOra = true
        p.modificaOraDiInizio(P2, LocalTime.of(9, 0))
        advanceUntilIdle()
        assertNotNull(p.dati().righe.single { it.registrazioneId == P2 }.erroreRiga)
        assertNull(p.dati().righe.single { it.registrazioneId == P1 }.erroreRiga)
    }

    // ---- AC-I69 -----------------------------------------------------------------------------------------------

    @Test
    fun `AC-I69 un Progetto vuoto resta lo stato vuoto di oggi`() = runTest {
        lettureRegistrazioni = { emptyList() }
        lettureIncontri = { emptyList() }
        val p = presentatore(this)
        advanceUntilIdle()

        assertEquals(RegistrazioniUiStato.Dati(righe = emptyList()), p.stato.value)
        assertTrue(p.dati().incontri.isEmpty())
    }

    @Test
    fun `AC-I69 il caricamento iniziale mostra Caricamento e un errore del read model mostra l'errore`() = runTest {
        val p = presentatore(this)
        assertEquals(RegistrazioniUiStato.Caricamento, p.stato.value)

        lettureRegistrazioni = { error("guasto") }
        val guasto = presentatore(this)
        advanceUntilIdle()
        assertIs<RegistrazioniUiStato.Errore>(guasto.stato.value)
    }

    @Test
    fun `AC-I69 un guasto della lettura degli incontri non fa fallire il caricamento`() = runTest {
        lettureIncontri = { error("guasto incontri") }
        val p = presentatore(this)
        advanceUntilIdle()

        val dati = p.dati()
        assertEquals(listOf(P1, P2, P3), dati.righe.map { it.registrazioneId })
        assertEquals(3, dati.incontri.size) // each Registrazione alone, today's S2
        assertTrue(dati.incontri.none { it.multiParte })
        assertTrue(dati.righe.all { it.parte == null })
    }

    @Test
    fun `AC-I69 un Incontro di una Parte e la riga di oggi, senza numero ne ora`() = runTest {
        ordine = mutableListOf(P1 to LocalTime.of(10, 0))
        val p = presentatore(this)
        advanceUntilIdle()

        assertFalse(p.incontro().multiParte)
        assertNull(p.dati().righe.single().parte)
        assertNull(p.dati().righe.single().oraDiInizio)
        assertEquals(IdentificazioneRiga(3, 1), p.dati().righe.single().identificazione)
    }

    @Test
    fun `AC-I69 l'espansione e chiusa all'inizio e sopravvive a un giro su S3 e ai ricaricamenti`() = runTest {
        val p = presentatore(this)
        advanceUntilIdle()
        assertFalse(p.incontro().espanso)

        p.espandiIncontro(INCONTRO)
        assertTrue(p.incontro().espanso)

        p.apriRiga(P1) // S3 opens …
        aggiornamenti.emetti(Cambiamento(P1)) // … and a refresh lands while the user is away
        advanceUntilIdle()
        assertTrue(p.incontro().espanso)

        p.espandiIncontro(INCONTRO)
        advanceUntilIdle()
        assertFalse(p.incontro().espanso)
    }
}
