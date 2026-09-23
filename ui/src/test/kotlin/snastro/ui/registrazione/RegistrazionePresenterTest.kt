package snastro.ui.registrazione

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.trascrizione.applicazione.letture.SegmentoTrascrittoView
import snastro.trascrizione.applicazione.letture.TrascrittoView
import snastro.trascrizione.applicazione.letture.VoceTrascrittoView
import snastro.ui.ApriEsterno
import snastro.ui.ApriEsternoFinta
import snastro.ui.lettore.LettoreAudio
import snastro.ui.lettore.LettoreAudioFinta
import snastro.ui.lettore.LettoreUiStato
import snastro.ui.lettore.StatoLettore
import snastro.ui.testi.MESSAGGIO_AUDIO_NON_DISPONIBILE
import snastro.ui.testi.MESSAGGIO_ERRORE_CARICAMENTO_TRASCRITTO
import snastro.ui.testi.MESSAGGIO_ERRORE_GENERICO
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val REG_1 = RegistrazioneId("id-1")
private val DATA_1: LocalDate = LocalDate.of(2026, 3, 12)
private const val PERCORSO_DOCUMENTO = "/progetti/demo.snastro/documenti/2026-03-12 Seduta.md"

private fun unaVista(
    segmenti: List<SegmentoTrascrittoView> = listOf(unSegmento()),
    voci: List<VoceTrascrittoView> = listOf(VoceTrascrittoView(VoceId(1), "Voce 1")),
) = TrascrittoView(
    registrazioneId = REG_1,
    titolo = "Seduta del 12 marzo",
    dataRegistrazione = DATA_1,
    durataMs = 125_000,
    segmenti = segmenti,
    voci = voci,
)

private fun unSegmento(
    segmentoId: SegmentoId = SegmentoId(1),
    voceId: VoceId = VoceId(1),
    inizioMs: Long = 0,
    fineMs: Long = 2_000,
    testo: String = "Buongiorno a tutti.",
) = SegmentoTrascrittoView(segmentoId, voceId, inizioMs, fineMs, testo)

/**
 * AC-402: every test below constructs [RegistrazionePresenter] with ONLY the four R1 collaborators —
 * fakes of `TrascrittoView` (the [trascritto] lambda), `Documento` ([documento]), [LettoreAudioFinta]
 * and [ApriEsternoFinta] — proving the presenter is fully green without any Parlanti source/command.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RegistrazionePresenterTest {
    @Suppress("LongParameterList") // one parameter per RegistrazionePresenter collaborator
    private fun presentatore(
        scope: TestScope,
        registrazioneId: RegistrazioneId = REG_1,
        trascritto: () -> TrascrittoView? = { unaVista() },
        documento: () -> String? = { PERCORSO_DOCUMENTO },
        lettore: LettoreAudio = LettoreAudioFinta(),
        apriEsterno: ApriEsterno = ApriEsternoFinta(),
    ): RegistrazionePresenter {
        val dispatcher = StandardTestDispatcher(scope.testScheduler)
        return RegistrazionePresenter(
            CoroutineScope(dispatcher),
            dispatcher,
            registrazioneId,
            trascritto,
            documento,
            lettore,
            apriEsterno,
        )
    }

    @Test
    fun `AC-207 prima del caricamento lo stato e Caricamento`() = runTest {
        val presenter = presentatore(this)
        assertEquals(RegistrazioneUiStato.Caricamento, presenter.stato.value)
    }

    @Test
    fun `AC-207 AC-208 il caricamento produce i segmenti in ordine di tempo con la loro etichetta Voce n`() = runTest {
        val presenter = presentatore(
            this,
            trascritto = {
                unaVista(
                    segmenti = listOf(
                        unSegmento(SegmentoId(1), VoceId(1), inizioMs = 0, fineMs = 1_000, testo = "prima"),
                        unSegmento(SegmentoId(2), VoceId(2), inizioMs = 1_000, fineMs = 2_000, testo = "seconda"),
                    ),
                    voci = listOf(VoceTrascrittoView(VoceId(1), "Voce 1"), VoceTrascrittoView(VoceId(2), "Voce 2")),
                )
            },
        )
        advanceUntilIdle()
        val dati = assertIs<RegistrazioneUiStato.Dati>(presenter.stato.value)
        assertEquals(listOf("prima", "seconda"), dati.segmenti.map { it.testo })
        assertEquals(listOf("Voce 1", "Voce 2"), dati.segmenti.map { it.etichettaVoce })
        assertEquals("Seduta del 12 marzo", dati.titolo)
    }

    @Test
    fun `AC-207 un trascritto senza segmenti e una lista vuota, non un errore`() = runTest {
        val presenter = presentatore(this, trascritto = { unaVista(segmenti = emptyList(), voci = emptyList()) })
        advanceUntilIdle()
        val dati = assertIs<RegistrazioneUiStato.Dati>(presenter.stato.value)
        assertTrue(dati.segmenti.isEmpty())
    }

    @Test
    fun `nessun Trascritto per questa Registrazione produce lo stato Errore`() = runTest {
        val presenter = presentatore(this, trascritto = { null })
        advanceUntilIdle()
        val errore = assertIs<RegistrazioneUiStato.Errore>(presenter.stato.value)
        assertEquals(MESSAGGIO_ERRORE_CARICAMENTO_TRASCRITTO, errore.messaggio)
    }

    @Test
    fun `un guasto nel caricamento produce lo stato Errore, mai bloccato in Caricamento`() = runTest {
        val presenter = presentatore(this, trascritto = { error("guasto simulato") })
        advanceUntilIdle()
        val errore = assertIs<RegistrazioneUiStato.Errore>(presenter.stato.value)
        assertEquals(MESSAGGIO_ERRORE_CARICAMENTO_TRASCRITTO, errore.messaggio)
    }

    @Test
    fun `riprova ricarica dopo un Errore`() = runTest {
        var primaChiamata = true
        val presenter = presentatore(
            this,
            trascritto = {
                if (primaChiamata) {
                    primaChiamata = false
                    null
                } else {
                    unaVista()
                }
            },
        )
        advanceUntilIdle()
        assertIs<RegistrazioneUiStato.Errore>(presenter.stato.value)
        presenter.azioni.riprova()
        advanceUntilIdle()
        assertIs<RegistrazioneUiStato.Dati>(presenter.stato.value)
    }

    @Test
    fun `AC-208 click su un Segmento riproduce dal suo inizio`() = runTest {
        val lettore = LettoreAudioFinta()
        val presenter = presentatore(
            this,
            trascritto = { unaVista(segmenti = listOf(unSegmento(SegmentoId(1), inizioMs = 4_500, fineMs = 6_000))) },
            lettore = lettore,
        )
        advanceUntilIdle()
        presenter.azioni.riproduciSegmento(SegmentoId(1))
        advanceUntilIdle()
        assertEquals(StatoLettore(REG_1, 4_500, inRiproduzione = true), lettore.stato.value)
    }

    @Test
    fun `AC-208 il Segmento la cui posizione e in riproduzione e evidenziato, gli altri no`() = runTest {
        val lettore = LettoreAudioFinta()
        val presenter = presentatore(
            this,
            trascritto = {
                unaVista(
                    segmenti = listOf(
                        unSegmento(SegmentoId(1), inizioMs = 0, fineMs = 1_000),
                        unSegmento(SegmentoId(2), inizioMs = 1_000, fineMs = 2_000),
                    ),
                )
            },
            lettore = lettore,
        )
        advanceUntilIdle()
        lettore.emetti(StatoLettore(REG_1, 1_200, inRiproduzione = true))
        advanceUntilIdle()
        val dati = assertIs<RegistrazioneUiStato.Dati>(presenter.stato.value)
        assertEquals(false, dati.segmenti.single { it.segmentoId == SegmentoId(1) }.inRiproduzione)
        assertEquals(true, dati.segmenti.single { it.segmentoId == SegmentoId(2) }.inRiproduzione)
    }

    @Test
    fun `AC-217 sorgente audio mancante disabilita la barra con un messaggio`() = runTest {
        val lettore = LettoreAudioFinta(nonDisponibili = setOf(REG_1))
        val presenter = presentatore(this, lettore = lettore)
        advanceUntilIdle()
        val dati = assertIs<RegistrazioneUiStato.Dati>(presenter.stato.value)
        assertEquals(LettoreUiStato.NonDisponibile(MESSAGGIO_AUDIO_NON_DISPONIBILE), dati.barra)
        assertEquals(false, dati.audioDisponibile)
    }

    @Test
    fun `AC-217 il click su un Segmento non riproduce quando la sorgente audio e mancante`() = runTest {
        val lettore = LettoreAudioFinta(nonDisponibili = setOf(REG_1))
        val presenter = presentatore(this, lettore = lettore)
        advanceUntilIdle()
        presenter.azioni.riproduciSegmento(SegmentoId(1))
        advanceUntilIdle()
        assertNull(lettore.stato.value.registrazioneId)
    }

    @Test
    fun `AC-217 il trascritto resta leggibile con la sorgente audio mancante`() = runTest {
        val lettore = LettoreAudioFinta(nonDisponibili = setOf(REG_1))
        val presenter = presentatore(this, lettore = lettore)
        advanceUntilIdle()
        val dati = assertIs<RegistrazioneUiStato.Dati>(presenter.stato.value)
        assertEquals(1, dati.segmenti.size)
    }

    @Test
    fun `AC-218 apri documento usa il percorso del Documento`() = runTest {
        val apriEsterno = ApriEsternoFinta()
        val presenter = presentatore(this, apriEsterno = apriEsterno)
        advanceUntilIdle()
        presenter.azioni.apriDocumento()
        advanceUntilIdle()
        assertEquals(listOf(PERCORSO_DOCUMENTO), apriEsterno.fileAperti)
    }

    @Test
    fun `AC-218 mostra nella cartella usa il percorso del Documento`() = runTest {
        val apriEsterno = ApriEsternoFinta()
        val presenter = presentatore(this, apriEsterno = apriEsterno)
        advanceUntilIdle()
        presenter.azioni.mostraDocumentoNellaCartella()
        advanceUntilIdle()
        assertEquals(listOf(PERCORSO_DOCUMENTO), apriEsterno.cartelleMostrate)
    }

    @Test
    fun `AC-218 apri documento non fa nulla finche il percorso non e risolto`() = runTest {
        val apriEsterno = ApriEsternoFinta()
        val presenter = presentatore(this, documento = { null }, apriEsterno = apriEsterno)
        advanceUntilIdle()
        presenter.azioni.apriDocumento()
        advanceUntilIdle()
        assertTrue(apriEsterno.fileAperti.isEmpty())
        assertEquals(null, (presenter.stato.value as RegistrazioneUiStato.Dati).documentoPercorso)
    }

    @Test
    fun `un guasto di ApriEsterno mostra un messaggio inline, dismissibile`() = runTest {
        val apriEsterno = object : ApriEsterno {
            override fun apriFile(percorso: String): Unit = error("guasto simulato")
            override fun mostraNellaCartella(percorso: String) = Unit
        }
        val presenter = presentatore(this, apriEsterno = apriEsterno)
        advanceUntilIdle()
        presenter.azioni.apriDocumento()
        advanceUntilIdle()
        val dati = assertIs<RegistrazioneUiStato.Dati>(presenter.stato.value)
        assertEquals(MESSAGGIO_ERRORE_GENERICO, dati.errore)
        presenter.azioni.chiudiErrore()
        assertNull((presenter.stato.value as RegistrazioneUiStato.Dati).errore)
    }
}
