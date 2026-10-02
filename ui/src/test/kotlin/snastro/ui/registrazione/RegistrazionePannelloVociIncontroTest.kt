package snastro.ui.registrazione

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import snastro.kernel.EstrattoRef
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.parlanti.applicazione.eventi.TipoParlanteVista
import snastro.parlanti.applicazione.letture.Candidato
import snastro.parlanti.applicazione.letture.PropostaVista
import snastro.parlanti.applicazione.letture.VoceIdentificata
import snastro.parlanti.applicazione.porte.Fascia
import snastro.trascrizione.applicazione.comandi.DividiVoce
import snastro.trascrizione.applicazione.comandi.UnisciVoci
import snastro.trascrizione.applicazione.letture.ParteRef
import snastro.trascrizione.applicazione.letture.VoceIncontroRiga
import snastro.trascrizione.applicazione.letture.VociIncontro
import snastro.ui.testi.testoAncheInParti
import snastro.ui.testi.testoEstrattoParte
import snastro.ui.testi.testoGruppoPerParte
import snastro.ui.testi.testoParti
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val PARTE_1 = RegistrazioneId("parte-1")
private val PARTE_3 = RegistrazioneId("parte-3")
private val V5 = VoceId(5)

/**
 * AC-I77..AC-I79 (UX S3): the Voci panel of Parte 2 of an Incontro of three Parti. Voce 2 speaks here and in
 * Parti 1 and 3; Voci 1 and 3 speak here; Voce 5 only in Parte 1.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RegistrazionePannelloVociIncontroTest {
    private fun TestScope.ambiente(conParti: Boolean = true): AmbienteVoci {
        val base = unTrascritto()
        val a = AmbienteVoci(CoroutineScope(StandardTestDispatcher(testScheduler)), OrologioVirtuale(testScheduler))
        if (conParti) {
            a.vista = base.copy(
                parti = listOf(ParteRef(PARTE_1, 1), ParteRef(REG, 2), ParteRef(PARTE_3, 3)),
                numeroParte = 2,
                voci = base.voci.map { if (it.voceId == V2) it.copy(altreParti = listOf(1, 3)) else it },
            )
            a.vociIncontro = VociIncontro(
                listOf(
                    VoceIncontroRiga(V1, "Voce 1", listOf(2)),
                    VoceIncontroRiga(V2, "Voce 2", listOf(1, 2, 3)),
                    VoceIncontroRiga(V3, "Voce 3", listOf(2)),
                    VoceIncontroRiga(V5, "Voce 5", listOf(1)),
                ),
                4,
            )
        }
        return a
    }

    /** 'Riassegna per somiglianza' needs two reference persons with a Segmento of at least 1 s. */
    private fun AmbienteVoci.conRiferimenti() {
        vista = vista.copy(segmenti = vista.segmenti.map { it.copy(fineMs = it.inizioMs + 2_000) })
        identificate = listOf(
            VoceIdentificata(V1, MARCO.parlanteId, MARCO.nome, MARCO.tipoParlante),
            VoceIdentificata(V2, GIULIA.parlanteId, GIULIA.nome, GIULIA.tipoParlante),
            VoceIdentificata(V3),
        )
    }

    private fun TestScope.avvia(a: AmbienteVoci): RegistrazionePresenter {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return a.presenter(CoroutineScope(dispatcher), dispatcher)
    }

    private val RegistrazionePresenter.dati get() = assertIs<RegistrazioneUiStato.Dati>(stato.value)
    private val RegistrazionePresenter.pannello get() = assertNotNull(dati.pannello)

    @Test
    fun `AC-I77 una Voce in piu parti dice anche in parte 1, 3 e le altre carte nessuna riga`() = runTest {
        val presenter = avvia(ambiente().also { it.identificate += VoceIdentificata(V5) })
        advanceUntilIdle()
        val carte = presenter.pannello.carte.associateBy { it.voceId }
        assertEquals(listOf(1, 3), carte.getValue(V2).altreParti)
        assertEquals("anche in parte 1, 3", testoAncheInParti(carte.getValue(V2).altreParti))
        assertTrue(carte.getValue(V1).altreParti.isEmpty())
    }

    @Test
    fun `AC-I77 Unisci con elenca prima le Voci di questa parte poi le altre con parte n`() = runTest {
        val presenter = avvia(ambiente())
        advanceUntilIdle()
        val carta = presenter.pannello.carte.single { it.voceId == V2 }
        assertEquals(listOf(V1, V3), carta.altreVoci.map { it.voceId })
        assertEquals(listOf(OpzioneVoce(V5, "Voce 5", listOf(1))), carta.vociAltreParti)
        assertEquals("parte 1", testoParti(carta.vociAltreParti.single().parti))
    }

    @Test
    fun `AC-I77 scegliere una Voce di un altra parte invia UnisciVoci con l Incontro delle Voci`() = runTest {
        val a = ambiente()
        val presenter = avvia(a)
        advanceUntilIdle()
        presenter.azioni.unisci(V2, V5)
        advanceUntilIdle()
        assertEquals(listOf<Any>(UnisciVoci(REG, V2, V5, incontroDelleVoci = INCONTRO_REG)), a.revisioni)
    }

    @Test
    fun `AC-I77 INV-I7 anche Dividi porta l Incontro delle Voci`() = runTest {
        val a = ambiente()
        val presenter = avvia(a)
        advanceUntilIdle()
        presenter.azioni.selezionaSegmento(SegmentoId(1))
        advanceUntilIdle()
        presenter.azioni.dividiVoce()
        advanceUntilIdle()
        assertEquals(
            listOf<Any>(DividiVoce(REG, V1, setOf(SegmentoId(1)), incontroDelleVoci = INCONTRO_REG)),
            a.revisioni,
        )
    }

    @Test
    fun `AC-I77 un Nome di una Voce di un altra parte compare nell opzione`() = runTest {
        val a = ambiente().also {
            it.identificate += VoceIdentificata(V5, MARCO.parlanteId, "Marco", TipoParlanteVista.RICORRENTE)
        }
        val presenter = avvia(a)
        advanceUntilIdle()
        val opzione = presenter.pannello.carte.first().vociAltreParti.single()
        assertEquals("Marco", opzione.nome)
    }

    @Test
    fun `AC-I77 un Incontro con una sola parte e come oggi, senza altre Voci ne letture dell Incontro`() = runTest {
        val a = ambiente(conParti = false)
        val presenter = avvia(a)
        advanceUntilIdle()
        assertTrue(a.chiamateVociIncontro.isEmpty())
        presenter.pannello.carte.forEach {
            assertTrue(it.altreParti.isEmpty())
            assertTrue(it.vociAltreParti.isEmpty())
        }
        assertTrue(presenter.pannello.altreParti.isEmpty())
    }

    @Test
    fun `AC-I78 l estratto di un altra parte dice estratto parte n, quello di questa nessuna etichetta`() = runTest {
        val daParte1 = Candidato(
            MARCO.parlanteId,
            "Marco",
            TipoParlanteVista.RICORRENTE,
            Fascia.FORTE,
            EstrattoRef(PARTE_1, listOf(IntervalloMs(0, 1_000))),
        )
        val daQui = daParte1.copy(estratto = EstrattoRef(REG, listOf(IntervalloMs(0, 1_000))))
        val presenter = avvia(ambiente())
        advanceUntilIdle()
        assertEquals(1, presenter.pannello.parteDelloEstratto(daParte1.estratto))
        assertEquals("estratto · parte 1", testoEstrattoParte(1))
        assertNull(presenter.pannello.parteDelloEstratto(daQui.estratto))
    }

    @Test
    fun `AC-I78 una proposta con candidato di un altra parte arriva al pannello`() = runTest {
        val a = ambiente().also {
            it.proposta = { r ->
                PropostaVista(
                    r.voceId,
                    listOf(
                        Candidato(
                            MARCO.parlanteId,
                            "Marco",
                            TipoParlanteVista.RICORRENTE,
                            Fascia.FORTE,
                            EstrattoRef(PARTE_3, listOf(IntervalloMs(0, 1_000))),
                        ),
                    ),
                )
            }
        }
        val presenter = avvia(a)
        advanceUntilIdle()
        assertEquals(3, presenter.pannello.parteDelloEstratto(EstrattoRef(PARTE_3, listOf(IntervalloMs(0, 1)))))
    }

    @Test
    fun `AC-I79 la riga di anteprima somma le parti, solo su un Incontro di piu parti`() = runTest {
        val gruppo = GruppoSpostamenti(V3, V2, 8, listOf(FrasiInParte(PARTE_1, 3), FrasiInParte(REG, 5)))
        val a = ambiente().also { it.somiglianza.gruppi = listOf(gruppo) }
        a.conRiferimenti()
        val presenter = avvia(a)
        advanceUntilIdle()
        presenter.azioni.calcolaSomiglianza()
        advanceUntilIdle()
        val anteprima = assertIs<FaseSomiglianza.Anteprima>(presenter.pannello.somiglianza?.fase)
        assertEquals(listOf("Voce 3 → Giulia: 8 (parte 1: 3, parte 2: 5)"), anteprima.righe)
        assertEquals(
            "Voce 3 → Anna: 8 (parte 1: 3, parte 2: 5)",
            testoGruppoPerParte("Voce 3", "Anna", 8, listOf(1 to 3, 2 to 5)),
        )
    }

    @Test
    fun `AC-I79 con una sola parte la riga e quella di oggi`() = runTest {
        val gruppo = GruppoSpostamenti(V3, V2, 8, listOf(FrasiInParte(REG, 8)))
        val a = ambiente(conParti = false).also { it.somiglianza.gruppi = listOf(gruppo) }
        a.conRiferimenti()
        val presenter = avvia(a)
        advanceUntilIdle()
        presenter.azioni.calcolaSomiglianza()
        advanceUntilIdle()
        val anteprima = assertIs<FaseSomiglianza.Anteprima>(presenter.pannello.somiglianza?.fase)
        assertEquals(listOf("Voce 3 → Giulia: 8"), anteprima.righe)
    }
}
