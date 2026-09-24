package snastro.ui.registrazione

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.parlanti.applicazione.eventi.TipoParlanteVista
import snastro.parlanti.applicazione.letture.PropostaDiUnione
import snastro.parlanti.applicazione.letture.PropostaVista
import snastro.parlanti.applicazione.letture.VoceIdentificata
import snastro.parlanti.applicazione.porte.Fascia
import snastro.parlanti.dominio.ErroreParlanti
import snastro.trascrizione.applicazione.comandi.DividiVoce
import snastro.trascrizione.applicazione.comandi.RiassegnaSegmento
import snastro.trascrizione.applicazione.comandi.UnisciVoci
import snastro.trascrizione.dominio.ErroreTrascrizione
import snastro.ui.ApriEsternoFinta
import snastro.ui.Cambiamento
import snastro.ui.lettore.LettoreAudioFinta
import snastro.ui.lettore.StatoLettore
import snastro.ui.testi.MESSAGGIO_ERRORE_VOCI
import snastro.ui.testi.SPIEGAZIONE_DIVIDI_INTERA_VOCE
import snastro.ui.testi.messaggioPer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val SOGLIA = RegistrazionePresenter.SOGLIA_ATTESA_VISIBILE_MS

/**
 * R2 (schermata-registrazione-identificazione): S3's Voci panel, Nome labels, selection toolbar and
 * Revisione UI, with every R2 collaborator a hand-written fake ([AmbienteVoci]) and virtual time
 * ([OrologioVirtuale]) for the ADR 0017 pending state. The R1 read-only mode stays covered, untouched,
 * by `RegistrazionePresenterTest` (AC-402).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LargeClass", "TooManyFunctions") // one test per tests_nl item
class RegistrazioneIdentificazioneTest {
    // The project scope of ComandiVoceFinta is NOT `backgroundScope`: `advanceUntilIdle` skips background-only work.
    private fun TestScope.ambiente() =
        AmbienteVoci(CoroutineScope(StandardTestDispatcher(testScheduler)), OrologioVirtuale(testScheduler))

    private fun TestScope.avvia(a: AmbienteVoci): RegistrazionePresenter {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return a.presenter(CoroutineScope(dispatcher), dispatcher)
    }

    private val RegistrazionePresenter.dati get() = assertIs<RegistrazioneUiStato.Dati>(stato.value)

    private fun RegistrazionePresenter.carta(voce: VoceId): CartaVoce =
        assertNotNull(dati.pannello).carte.single { it.voceId == voce }

    private fun CartaVoce.proposta(): StatoProposta =
        assertIs<ContenutoCarta.DaIdentificare>(contenuto).proposta

    // --- AC-402 / AC-405 ----------------------------------------------------------------------------

    @Test
    fun `AC-402 senza sorgenti Parlanti le azioni R2 non fanno nulla e non c e pannello`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val presenter = RegistrazionePresenter(
            scope = CoroutineScope(dispatcher),
            io = dispatcher,
            registrazioneId = REG,
            trascritto = { unTrascritto() },
            documento = { null },
            lettore = LettoreAudioFinta(),
            apriEsterno = ApriEsternoFinta(),
        )
        advanceUntilIdle()
        presenter.azioni.selezionaSegmento(SegmentoId(1))
        presenter.azioni.conferma(V1)
        advanceUntilIdle()
        assertNull(presenter.dati.pannello)
        assertTrue(presenter.dati.selezione.isEmpty())
        assertNull(presenter.dati.barraSelezione)
    }

    @Test
    fun `AC-405 prima della lettura ogni card mostra il caricamento, mai una galleria vuota`() = runTest {
        val presenter = avvia(ambiente())
        val stati = mutableListOf<RegistrazioneUiStato>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { presenter.stato.toList(stati) }
        advanceUntilIdle()
        val pannelli = stati.filterIsInstance<RegistrazioneUiStato.Dati>().mapNotNull { it.pannello }
        assertTrue(pannelli.first().carte.all { it.contenuto == ContenutoCarta.Caricamento })
        val galleriaVuota = pannelli.flatMap { it.carte }.map { it.contenuto }
            .filterIsInstance<ContenutoCarta.DaIdentificare>().any { it.galleriaVuota }
        assertFalse(galleriaVuota)
    }

    @Test
    fun `AC-405 un errore di lettura mostra un messaggio nelle card e lascia il trascritto usabile`() = runTest {
        val a = ambiente().apply { identificazioneRotta = true }
        val presenter = avvia(a)
        advanceUntilIdle()
        assertEquals(ContenutoCarta.Errore(MESSAGGIO_ERRORE_VOCI), presenter.carta(V1).contenuto)
        assertEquals(4, presenter.dati.segmenti.size)
        presenter.azioni.riproduciSegmento(SegmentoId(2))
        advanceUntilIdle()
        assertEquals(StatoLettore(REG, 2_000, inRiproduzione = true), a.lettore.stato.value)
    }

    @Test
    fun `L665a un ricaricamento fallito mantiene l ultimo nome buono, non lo wipe a Voce n`() = runTest {
        val a = ambiente().apply {
            identificate = listOf(VoceIdentificata(V1, MARCO.parlanteId, "Marco", TipoParlanteVista.RICORRENTE))
        }
        val presenter = avvia(a)
        advanceUntilIdle()
        assertEquals("Marco", presenter.dati.segmenti.first { it.voceId == V1 }.etichettaVoce)
        assertEquals(listOf(GIULIA, MARCO), presenter.dati.pannello?.parlantiAttivi)

        // AC-319-style: a Cambiamento re-triggers ricaricaParlanti(); this time the read fails.
        a.identificazioneRotta = true
        a.aggiornamenti.emetti(Cambiamento(REG))
        advanceUntilIdle()

        // (rework cycle 1, MED / L665a): the failed reload keeps the LAST GOOD data — the Nome and the
        // roster survive; only the card-level content (checked elsewhere) shows the read error.
        assertEquals("Marco", presenter.dati.segmenti.first { it.voceId == V1 }.etichettaVoce)
        assertEquals(listOf(GIULIA, MARCO), presenter.dati.pannello?.parlantiAttivi)
        assertEquals(ContenutoCarta.Errore(MESSAGGIO_ERRORE_VOCI), presenter.carta(V1).contenuto)
    }

    @Test
    fun `AC-405 le etichette mostrano il Nome attribuito al posto di Voce n`() = runTest {
        val a = ambiente().apply {
            identificate = listOf(
                VoceIdentificata(V1, MARCO.parlanteId, "Marco", TipoParlanteVista.RICORRENTE),
                VoceIdentificata(V2),
                VoceIdentificata(V3),
            )
        }
        val presenter = avvia(a)
        advanceUntilIdle()
        assertEquals(listOf("Marco", "Voce 2", "Marco", "Voce 3"), presenter.dati.segmenti.map { it.etichettaVoce })
        assertEquals("Voce 1", presenter.carta(V1).titolo)
    }

    // --- cards: AC-212..214, AC-219 -----------------------------------------------------------------

    @Test
    fun `AC-212 galleria vuota solo nuovo e salta con il suggerimento, nessuna estrazione`() = runTest {
        val a = ambiente().apply { attivi = emptyList() }
        val presenter = avvia(a)
        advanceUntilIdle()
        val contenuto = assertIs<ContenutoCarta.DaIdentificare>(presenter.carta(V1).contenuto)
        assertTrue(contenuto.galleriaVuota)
        assertEquals(StatoProposta.Pronta(emptyList(), nuovoEvidenziato = false), contenuto.proposta)
        assertFalse(presenter.carta(V1).confermaAbilitata)
        assertTrue(presenter.carta(V1).azioniAbilitate)
        assertTrue(a.chiamateProposta.isEmpty())
    }

    @Test
    fun `AC-213 tutti i Candidati nessuna restano elencati e nuovo e evidenziato`() = runTest {
        val a = ambiente().apply {
            proposta = {
                PropostaVista(
                    it.voceId,
                    listOf(unCandidato(MARCO, Fascia.NESSUNA), unCandidato(GIULIA, Fascia.NESSUNA)),
                )
            }
        }
        val presenter = avvia(a)
        advanceUntilIdle()
        val proposta = assertIs<StatoProposta.Pronta>(presenter.carta(V1).proposta())
        assertEquals(2, proposta.candidati.size)
        assertTrue(proposta.nuovoEvidenziato)
    }

    @Test
    fun `AC-214 i Candidati portano la Fascia, nell ordine della Proposta, mai un numero`() = runTest {
        val a = ambiente().apply {
            proposta = {
                PropostaVista(it.voceId, listOf(unCandidato(MARCO, Fascia.FORTE), unCandidato(GIULIA, Fascia.DEBOLE)))
            }
        }
        val presenter = avvia(a)
        advanceUntilIdle()
        val proposta = assertIs<StatoProposta.Pronta>(presenter.carta(V1).proposta())
        assertEquals(listOf(Fascia.FORTE, Fascia.DEBOLE), proposta.candidati.map { it.fascia })
        assertFalse(proposta.nuovoEvidenziato)
    }

    @Test
    fun `AC-219 una Voce attribuita mostra il Nome e cambia, e salta non invia nulla`() = runTest {
        val a = ambiente().apply {
            identificate = listOf(VoceIdentificata(V1, MARCO.parlanteId, "Marco", TipoParlanteVista.RICORRENTE))
        }
        val presenter = avvia(a)
        advanceUntilIdle()
        assertEquals(
            ContenutoCarta.Attribuita(MARCO.parlanteId, "Marco", TipoParlanteVista.RICORRENTE),
            presenter.carta(V1).contenuto,
        )
        assertFalse(ref(V1) in a.chiamateProposta)
        presenter.azioni.salta(V1)
        advanceUntilIdle()
        assertTrue(a.comandi.eseguiti.isEmpty())
        // 'cambia' → ConfermaAttribuzione with another Parlante
        presenter.azioni.confermaParlante(V1, GIULIA.parlanteId)
        advanceUntilIdle()
        assertEquals(listOf<ComandoVoce>(ComandoVoce.Conferma(ref(V1), GIULIA.parlanteId)), a.comandi.eseguiti)
        assertEquals("Giulia", assertIs<ContenutoCarta.Attribuita>(presenter.carta(V1).contenuto).nome)
    }

    // --- commands + errors: AC-215, AC-318, AC-411..AC-415 -----------------------------------------

    @Test
    fun `AC-411 Conferma mette subito la card in corso, niente secondo comando, le altre card restano usabili`() =
        runTest {
            val a = ambiente()
            a.comandi.trattieni = true
            val presenter = avvia(a)
            advanceUntilIdle()
            presenter.azioni.conferma(V1)
            assertEquals(AttesaComando.IN_CORSO, presenter.carta(V1).inCorso)
            assertFalse(presenter.carta(V1).azioniAbilitate)
            presenter.azioni.conferma(V1)
            presenter.azioni.salta(V1)
            runCurrent()
            assertEquals(1, a.comandi.stato.value.size)
            assertTrue(presenter.carta(V2).azioniAbilitate)
            presenter.azioni.salta(V2)
            runCurrent()
            assertEquals(setOf(ref(V1), ref(V2)), a.comandi.stato.value.keys)
            presenter.azioni.selezionaSegmento(SegmentoId(4))
            assertNotNull(presenter.dati.barraSelezione)
        }

    @Test
    fun `AC-412 oltre la soglia la card mostra In attesa con Annulla, prima no`() = runTest {
        val a = ambiente()
        a.comandi.trattieni = true
        val presenter = avvia(a)
        advanceUntilIdle()
        presenter.azioni.nuovoParlante(V1, "Anna", TipoParlanteVista.OCCASIONALE)
        advanceTimeBy(SOGLIA - 1)
        runCurrent()
        assertEquals(AttesaComando.IN_CORSO, presenter.carta(V1).inCorso)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(AttesaComando.IN_ATTESA, presenter.carta(V1).inCorso)
        assertFalse(presenter.carta(V1).azioniAbilitate)
    }

    @Test
    fun `AC-413 Annulla riporta la card allo stato di prima senza errore e il comando vede l annullamento`() = runTest {
        val a = ambiente()
        a.comandi.trattieni = true
        val presenter = avvia(a)
        advanceUntilIdle()
        val prima = presenter.carta(V1)
        presenter.azioni.conferma(V1)
        advanceTimeBy(SOGLIA + 1)
        runCurrent()
        assertEquals(AttesaComando.IN_ATTESA, presenter.carta(V1).inCorso)
        presenter.azioni.annullaComando(V1)
        advanceUntilIdle()
        assertEquals(prima, presenter.carta(V1))
        assertNull(presenter.dati.errore)
        assertEquals(listOf<ComandoVoce>(ComandoVoce.Conferma(ref(V1), MARCO.parlanteId)), a.comandi.annullati)
        assertTrue(a.comandi.eseguiti.isEmpty())
    }

    @Test
    fun `AC-414 l esito di un comando in attesa e mostrato come ogni altro e la Revisione resta attiva`() = runTest {
        val a = ambiente()
        a.comandi.trattieni = true
        val presenter = avvia(a)
        advanceUntilIdle()
        presenter.azioni.conferma(V1)
        advanceTimeBy(SOGLIA + 1)
        runCurrent()
        presenter.azioni.selezionaSegmento(SegmentoId(1))
        assertTrue(assertNotNull(presenter.dati.barraSelezione).abilitata)
        assertTrue(assertNotNull(presenter.dati.pannello).unioneAbilitata)
        a.comandi.rilascia(ref(V1))
        advanceUntilIdle()
        assertNull(presenter.carta(V1).inCorso)
        assertEquals("Marco", assertIs<ContenutoCarta.Attribuita>(presenter.carta(V1).contenuto).nome)
        assertEquals("Marco", presenter.dati.segmenti.first().etichettaVoce)
    }

    @Test
    fun `AC-215 un errore di comando e un messaggio sulla card e nulla cambia`() = runTest {
        val a = ambiente().apply { esitoComando = { Esito.Errore(ErroreParlanti.NomeGiaInUso("Marco")) } }
        val presenter = avvia(a)
        advanceUntilIdle()
        val prima = presenter.carta(V1)
        presenter.azioni.nuovoParlante(V1, "marco", TipoParlanteVista.RICORRENTE)
        advanceUntilIdle()
        val dopo = presenter.carta(V1)
        assertEquals(messaggioPer(ErroreParlanti.NomeGiaInUso("Marco")), dopo.errore)
        assertEquals(prima.copy(errore = dopo.errore), dopo)
        presenter.azioni.chiudiErroreVoce(V1)
        assertNull(presenter.carta(V1).errore)
    }

    @Test
    fun `AC-318 VoceCambiata su salta e un messaggio semplice sulla card e il comando si puo ripetere`() = runTest {
        var primo = true
        val a = ambiente().apply {
            esitoComando = {
                if (primo) {
                    primo = false
                    Esito.Errore(ErroreParlanti.VoceCambiata(ref(V2)))
                } else {
                    Esito.Ok(Unit)
                }
            }
        }
        val presenter = avvia(a)
        advanceUntilIdle()
        presenter.azioni.salta(V2)
        advanceUntilIdle()
        assertEquals("La voce è cambiata nel frattempo: riprova.", presenter.carta(V2).errore)
        assertIs<ContenutoCarta.DaIdentificare>(presenter.carta(V2).contenuto)
        presenter.azioni.salta(V2)
        advanceUntilIdle()
        assertNull(presenter.carta(V2).errore)
        assertIs<ContenutoCarta.Attribuita>(presenter.carta(V2).contenuto)
    }

    @Test
    fun `AC-415 un presenter ricreato durante il comando mostra di nuovo la card in corso e poi l esito`() = runTest {
        val a = ambiente()
        a.comandi.trattieni = true
        val dispatcher = StandardTestDispatcher(testScheduler)
        val schermata1 = CoroutineScope(dispatcher)
        val primo = a.presenter(schermata1, dispatcher)
        advanceUntilIdle()
        primo.azioni.conferma(V1)
        runCurrent()
        schermata1.cancel() // l'utente esce da S3: il comando resta nello scope del progetto
        runCurrent()
        assertEquals(setOf(ref(V1)), a.comandi.stato.value.keys)

        val secondo = avvia(a)
        advanceTimeBy(SOGLIA / 2)
        runCurrent()
        assertEquals(AttesaComando.IN_CORSO, secondo.carta(V1).inCorso)
        advanceTimeBy(SOGLIA)
        runCurrent()
        assertEquals(AttesaComando.IN_ATTESA, secondo.carta(V1).inCorso)

        val terzo = avvia(a) // tornato dopo la soglia: subito in attesa
        runCurrent()
        advanceTimeBy(1)
        runCurrent()
        assertEquals(AttesaComando.IN_ATTESA, terzo.carta(V1).inCorso)

        a.comandi.rilascia(ref(V1))
        advanceUntilIdle()
        assertNull(terzo.carta(V1).inCorso)
        assertEquals("Marco", assertIs<ContenutoCarta.Attribuita>(terzo.carta(V1).contenuto).nome)
    }

    @Test
    fun `AC-415 Annulla da un presenter ricreato annulla il comando del progetto`() = runTest {
        val a = ambiente()
        a.comandi.trattieni = true
        val dispatcher = StandardTestDispatcher(testScheduler)
        val schermata1 = CoroutineScope(dispatcher)
        a.presenter(schermata1, dispatcher).also {
            advanceUntilIdle()
            it.azioni.salta(V3)
            runCurrent()
        }
        schermata1.cancel()
        val secondo = avvia(a)
        advanceTimeBy(SOGLIA + 1)
        runCurrent()
        secondo.azioni.annullaComando(V3)
        advanceUntilIdle()
        assertNull(secondo.carta(V3).inCorso)
        assertTrue(a.comandi.eseguiti.isEmpty())
        assertEquals(1, a.comandi.annullati.size)
    }

    // --- Proposta: AC-319, AC-416 ---------------------------------------------------------------------

    @Test
    fun `AC-319 dopo ImpronteRiallineate la Proposta visibile e ricaricata senza perdere la selezione`() = runTest {
        val a = ambiente()
        val presenter = avvia(a)
        advanceUntilIdle()
        presenter.azioni.selezionaSegmento(SegmentoId(1))
        a.proposta = { PropostaVista(it.voceId, listOf(unCandidato(GIULIA, Fascia.DEBOLE))) }
        a.aggiornamenti.emetti(Cambiamento(RegistrazioneId("id-altra")))
        advanceUntilIdle()
        assertEquals("Marco", assertIs<StatoProposta.Pronta>(presenter.carta(V1).proposta()).candidati.single().nome)
        a.aggiornamenti.emetti(Cambiamento(REG))
        advanceUntilIdle()
        assertEquals("Giulia", assertIs<StatoProposta.Pronta>(presenter.carta(V1).proposta()).candidati.single().nome)
        assertEquals(setOf(SegmentoId(1)), presenter.dati.selezione)
    }

    @Test
    fun `AC-421 le Proposte sono calcolate una Voce alla volta, in ordine di pannello`() = runTest {
        val a = ambiente()
        avvia(a)
        advanceUntilIdle()
        assertEquals(listOf(ref(V1), ref(V2), ref(V3)), a.chiamateProposta)
    }

    // --- selection + Revisione: AC-209..211, AC-216, AC-404 ----------------------------------------

    @Test
    fun `AC-209 la selezione e limitata a una Voce`() = runTest {
        val presenter = avvia(ambiente())
        advanceUntilIdle()
        presenter.azioni.selezionaSegmento(SegmentoId(1))
        presenter.azioni.selezionaSegmento(SegmentoId(3))
        assertEquals(setOf(SegmentoId(1), SegmentoId(3)), presenter.dati.selezione)
        presenter.azioni.selezionaSegmento(SegmentoId(2))
        assertEquals(setOf(SegmentoId(2)), presenter.dati.selezione)
        assertEquals(V2, presenter.dati.barraSelezione?.voceId)
        presenter.azioni.selezionaSegmento(SegmentoId(2))
        assertTrue(presenter.dati.selezione.isEmpty())
        assertNull(presenter.dati.barraSelezione)
    }

    @Test
    fun `AC-210 Dividi voce e disabilitato con spiegazione se la selezione e l intera Voce`() = runTest {
        val a = ambiente()
        val presenter = avvia(a)
        advanceUntilIdle()
        presenter.azioni.selezionaSegmento(SegmentoId(1))
        presenter.azioni.selezionaSegmento(SegmentoId(3))
        val barra = assertNotNull(presenter.dati.barraSelezione)
        assertFalse(barra.dividiAbilitato)
        assertEquals(SPIEGAZIONE_DIVIDI_INTERA_VOCE, barra.spiegazioneDividi)
        presenter.azioni.dividiVoce()
        advanceUntilIdle()
        assertTrue(a.revisioni.isEmpty())

        presenter.azioni.selezionaSegmento(SegmentoId(3))
        assertTrue(assertNotNull(presenter.dati.barraSelezione).dividiAbilitato)
        presenter.azioni.dividiVoce()
        advanceUntilIdle()
        assertEquals(listOf<Any>(DividiVoce(REG, V1, setOf(SegmentoId(1)))), a.revisioni)
        assertEquals(VoceId(4), presenter.dati.segmenti.first().voceId)
        assertTrue(presenter.dati.selezione.isEmpty())
        assertEquals(4, assertNotNull(presenter.dati.pannello).carte.size)
    }

    @Test
    fun `AC-211 Riassegna verso una Voce esistente`() = runTest {
        val a = ambiente()
        val presenter = avvia(a)
        advanceUntilIdle()
        presenter.azioni.selezionaSegmento(SegmentoId(1))
        presenter.azioni.selezionaSegmento(SegmentoId(3))
        assertEquals(listOf(V2, V3), assertNotNull(presenter.dati.barraSelezione).destinazioni.map { it.voceId })
        presenter.azioni.riassegnaA(V2)
        advanceUntilIdle()
        assertEquals(
            listOf<Any>(RiassegnaSegmento(REG, SegmentoId(1), V2), RiassegnaSegmento(REG, SegmentoId(3), V2)),
            a.revisioni,
        )
        assertTrue(presenter.dati.segmenti.filter { it.segmentoId.numero in setOf(1, 3) }.all { it.voceId == V2 })
    }

    @Test
    fun `AC-211 Riassegna verso nuova voce porta tutta la selezione in UNA nuova Voce`() = runTest {
        val a = ambiente()
        val presenter = avvia(a)
        advanceUntilIdle()
        presenter.azioni.selezionaSegmento(SegmentoId(1))
        presenter.azioni.selezionaSegmento(SegmentoId(3))
        presenter.azioni.riassegnaA(null)
        advanceUntilIdle()
        assertEquals(
            listOf<Any>(RiassegnaSegmento(REG, SegmentoId(1), null), RiassegnaSegmento(REG, SegmentoId(3), VoceId(4))),
            a.revisioni,
        )
    }

    @Test
    fun `L665b un riassegna multi Segmento che fallisce a meta applica il primo spostamento e mostra l errore`() =
        runTest {
            val errore = ErroreTrascrizione.RiassegnazioneNonAmmessa(SegmentoId(3), V2)
            var chiamate = 0
            val a = ambiente().apply {
                esitoRevisione = { c ->
                    chiamate++
                    if (chiamate == 1) Esito.Ok(Unit) else Esito.Errore(errore)
                }
            }
            val presenter = avvia(a)
            advanceUntilIdle()
            presenter.azioni.selezionaSegmento(SegmentoId(1))
            presenter.azioni.selezionaSegmento(SegmentoId(3))

            presenter.azioni.riassegnaA(V2)
            advanceUntilIdle()

            // Both moves were attempted, in selection order, and the SECOND one failed.
            assertEquals(
                listOf<Any>(RiassegnaSegmento(REG, SegmentoId(1), V2), RiassegnaSegmento(REG, SegmentoId(3), V2)),
                a.revisioni,
            )
            // The one that succeeded is APPLIED — a partial failure is not treated as "nothing changed".
            assertEquals(V2, presenter.dati.segmenti.single { it.segmentoId == SegmentoId(1) }.voceId)
            assertEquals(V1, presenter.dati.segmenti.single { it.segmentoId == SegmentoId(3) }.voceId)
            // The inline error names the failure, and the selection (now split across two Voci) is cleared.
            assertEquals(messaggioPer(errore), presenter.dati.errore)
            assertTrue(presenter.dati.selezione.isEmpty())
        }

    @Test
    fun `AC-404 un errore di Revisione e un messaggio inline e trascritto e selezione restano invariati`() = runTest {
        val errore = ErroreTrascrizione.RiassegnazioneNonAmmessa(SegmentoId(2), null)
        val a = ambiente().apply { esitoRevisione = { Esito.Errore(errore) } }
        val presenter = avvia(a)
        advanceUntilIdle()
        presenter.azioni.selezionaSegmento(SegmentoId(2))
        val prima = presenter.dati
        presenter.azioni.riassegnaA(null)
        advanceUntilIdle()
        assertEquals(messaggioPer(errore), presenter.dati.errore)
        assertEquals(prima.copy(errore = presenter.dati.errore), presenter.dati)
        presenter.azioni.chiudiErrore()
        assertNull(presenter.dati.errore)
    }

    @Test
    fun `AC-216 il banner di unione unisce con un click e scompare quando la condizione cade`() = runTest {
        val a = ambiente().apply {
            identificate = listOf(
                VoceIdentificata(V1, MARCO.parlanteId, "Marco", TipoParlanteVista.RICORRENTE),
                VoceIdentificata(V2),
                VoceIdentificata(V3, MARCO.parlanteId, "Marco", TipoParlanteVista.RICORRENTE),
            )
            unioni = listOf(PropostaDiUnione(V1, V3, MARCO.parlanteId, "Marco"))
        }
        val presenter = avvia(a)
        advanceUntilIdle()
        assertEquals(a.unioni, assertNotNull(presenter.dati.pannello).unioni)
        a.esitoRevisione = {
            a.unioni = emptyList()
            Esito.Ok(Unit)
        }
        presenter.azioni.unisci(V1, V3)
        advanceUntilIdle()
        assertEquals(listOf<Any>(UnisciVoci(REG, V1, V3)), a.revisioni)
        assertTrue(assertNotNull(presenter.dati.pannello).unioni.isEmpty())
        assertEquals(2, assertNotNull(presenter.dati.pannello).carte.size)
    }

    @Test
    fun `Unisci con elenca le altre Voci e una Revisione alla volta`() = runTest {
        val a = ambiente()
        val presenter = avvia(a)
        advanceUntilIdle()
        assertEquals(listOf(V2, V3), presenter.carta(V1).altreVoci.map { it.voceId })
        presenter.azioni.unisci(V1, V2)
        presenter.azioni.unisci(V1, V3)
        advanceUntilIdle()
        assertEquals(listOf<Any>(UnisciVoci(REG, V1, V2)), a.revisioni)
    }

    // --- excerpts: AC-403 ---------------------------------------------------------------------------

    @Test
    fun `AC-403 audio mancante disabilita gli estratti e il pannello resta utilizzabile`() = runTest {
        val a = ambiente()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val lettore = LettoreAudioFinta(nonDisponibili = setOf(REG))
        val presenter = RegistrazionePresenter(
            scope = CoroutineScope(dispatcher),
            io = dispatcher,
            registrazioneId = REG,
            trascritto = { a.vista },
            documento = { null },
            lettore = lettore,
            apriEsterno = ApriEsternoFinta(),
            parlanti = a.sorgenti,
        )
        advanceUntilIdle()
        assertFalse(assertNotNull(presenter.dati.pannello).estrattiDisponibili)
        presenter.azioni.riproduciEstrattoVoce(V1)
        presenter.azioni.riproduciEstratto(unCandidato().estratto)
        advanceUntilIdle()
        assertFalse(lettore.stato.value.inRiproduzione)
        presenter.azioni.conferma(V1)
        advanceUntilIdle()
        assertIs<ContenutoCarta.Attribuita>(presenter.carta(V1).contenuto)
    }

    @Test
    fun `estratto di una Voce e di un Candidato suonano dal lettore condiviso`() = runTest {
        val a = ambiente()
        val presenter = avvia(a)
        advanceUntilIdle()
        assertTrue(assertNotNull(presenter.dati.pannello).estrattiDisponibili)
        presenter.azioni.riproduciEstrattoVoce(V2)
        advanceUntilIdle()
        assertEquals(StatoLettore(REG, 0, inRiproduzione = true), a.lettore.stato.value)
        presenter.azioni.riproduciEstratto(unCandidato().estratto)
        advanceUntilIdle()
        assertEquals(RegistrazioneId("id-0"), a.lettore.stato.value.registrazioneId)
    }
}
