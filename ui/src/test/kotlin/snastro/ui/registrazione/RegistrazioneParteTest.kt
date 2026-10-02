package snastro.ui.registrazione

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.progetto.applicazione.letture.IncontroDelProgettoVista
import snastro.progetto.applicazione.letture.ParteVista
import snastro.trascrizione.applicazione.letture.ParteRef
import snastro.trascrizione.applicazione.letture.RitrascrizioneInCorso
import snastro.trascrizione.applicazione.letture.SegmentoTrascrittoView
import snastro.trascrizione.applicazione.letture.StatoElaborazioneVista
import snastro.trascrizione.applicazione.letture.StatoRegistrazioneVista
import snastro.trascrizione.applicazione.letture.TrascrittoView
import snastro.trascrizione.applicazione.letture.VoceTrascrittoView
import snastro.ui.AggiornamentiVistaFinta
import snastro.ui.ApriEsternoFinta
import snastro.ui.lettore.LettoreAudioFinta
import snastro.ui.testi.messaggioRitrascrizioneParteInCorso
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val PARTI = (1..3).map { ParteRef(RegistrazioneId("parte-$it"), it) }
private val INCONTRO = IncontroId("incontro-1")

private fun vistaDi(
    numero: Int,
    solaLettura: RitrascrizioneInCorso? = null,
    parti: List<ParteRef> = PARTI,
) = TrascrittoView(
    registrazioneId = parti[numero - 1].registrazioneId,
    incontroId = INCONTRO,
    titolo = "Parte $numero della riunione",
    dataRegistrazione = LocalDate.of(2026, 9, 30),
    durataMs = 4_500_000,
    segmenti = listOf(SegmentoTrascrittoView(SegmentoId(1), VoceId(1), 0, 2_000, "testo")),
    voci = listOf(VoceTrascrittoView(VoceId(1), "Voce 1")),
    numeroParte = numero,
    parti = parti,
    solaLettura = solaLettura,
)

private fun incontroVista() = IncontroDelProgettoVista(
    incontroId = INCONTRO,
    titolo = "Riunione di progetto",
    data = LocalDate.of(2026, 9, 30),
    durataMs = 13_500_000,
    numParti = 3,
    parti = PARTI.map {
        ParteVista(
            registrazioneId = it.registrazioneId,
            numero = it.numero,
            titolo = "Parte ${it.numero} della riunione",
            dataRegistrazione = LocalDate.of(2026, 9, 30),
            oraDiInizio = LocalTime.of(9 + it.numero, 25),
            durataMs = 4_500_000,
        )
    },
)

/** AC-I74..I75 (ADR 0035 §4): one Parte of a multi-part Incontro — header, switcher, Incontro-wide read-only. */
@OptIn(ExperimentalCoroutinesApi::class)
class RegistrazioneParteTest {
    @Suppress("LongParameterList") // one parameter per collaborator this file varies
    private fun presentatore(
        scope: TestScope,
        numero: Int,
        vista: TrascrittoView = vistaDi(numero),
        selezione: SelezioneSchedaS3 = SelezioneSchedaS3(),
        lettore: LettoreAudioFinta = LettoreAudioFinta(),
        vaiAllaParte: (RegistrazioneId) -> Unit = {},
    ): RegistrazionePresenter {
        val dispatcher = StandardTestDispatcher(scope.testScheduler)
        val scopeCoroutine = CoroutineScope(dispatcher)
        return RegistrazionePresenter(
            scope = scopeCoroutine,
            io = dispatcher,
            registrazioneId = vista.registrazioneId,
            trascritto = { vista },
            sbobinatura = { null },
            lettore = lettore,
            apriEsterno = ApriEsternoFinta(),
            parlanti = unaSorgentiParlantiInerte(scopeCoroutine),
            stati = { null },
            aggiornamenti = AggiornamentiVistaFinta(),
            riassunto = SorgenteRiassuntoS3(contenuto = {}, segno = { flowOf(null) }),
            selezioneSchedaS3 = selezione,
            parti = SorgentiParti({ incontroVista() }, { incontroVista() }, vaiAllaParte),
        )
    }

    private val RegistrazionePresenter.dati get() = assertIs<RegistrazioneUiStato.Dati>(stato.value)

    @Test
    fun `AC-I74 la Parte 2 di 3 ha intestazione e selettore con la 2 selezionata`() = runTest {
        val presenter = presentatore(this, 2)
        advanceUntilIdle()

        val parte = assertIs<IntestazioneParte>(presenter.dati.parte)
        assertEquals(2, parte.numero)
        assertEquals(3, parte.totale)
        assertEquals("Riunione di progetto", parte.titoloIncontro)
        assertEquals(LocalTime.of(11, 25), parte.ora)
        assertEquals(PARTI, parte.parti)
        assertEquals("Parte 2 della riunione", presenter.dati.titolo)
    }

    @Test
    fun `AC-I74 scegliere la Parte 3 dal Riassunto apre la Parte 3 sulla scheda Riassunto`() = runTest {
        val selezione = SelezioneSchedaS3()
        var aperta: RegistrazionePresenter? = null
        lateinit var presenter: RegistrazionePresenter
        presenter = presentatore(this, 2, selezione = selezione, vaiAllaParte = { id ->
            // what the window's navigation does: a NEW presenter for the target, the SAME per-window holder.
            aperta = presentatore(this, PARTI.single { it.registrazioneId == id }.numero, selezione = selezione)
        })
        advanceUntilIdle()
        presenter.azioni.selezionaScheda(SchedaS3.RIASSUNTO)

        presenter.azioni.vaiAllaParte(PARTI[2].registrazioneId)
        advanceUntilIdle()

        val terza = assertIs<RegistrazioneUiStato.Dati>(aperta!!.stato.value)
        assertEquals(3, terza.parte?.numero)
        assertEquals(SchedaS3.RIASSUNTO, terza.schedaSelezionata)
    }

    @Test
    fun `INV-I3 una sola parte non ha intestazione ne selettore`() = runTest {
        val unica = listOf(PARTI[0])
        val presenter = presentatore(this, 1, vista = vistaDi(1, parti = unica))
        advanceUntilIdle()

        assertNull(presenter.dati.parte)
        assertEquals(false, presenter.dati.soloLettura)
    }

    @Test
    fun `INV-I3 una vista senza parti noto come oggi non ha intestazione`() = runTest {
        val vista = vistaDi(1, parti = listOf(PARTI[0])).copy(parti = emptyList())
        val presenter = presentatore(this, 1, vista = vista)
        advanceUntilIdle()

        assertNull(presenter.dati.parte)
    }

    @Test
    fun `AC-I75 una ritrascrizione sulla parte 3 blocca la parte 1 con il banner della parte 3`() = runTest {
        val lettore = LettoreAudioFinta()
        val presenter = presentatore(this, 1, vista = vistaDi(1, RitrascrizioneInCorso(3)), lettore = lettore)
        advanceUntilIdle()

        assertEquals(true, presenter.dati.soloLettura)
        assertEquals(messaggioRitrascrizioneParteInCorso(3), presenter.dati.bannerRitrascrizione)
        assertTrue(
            presenter.dati.bannerRitrascrizione!!.startsWith(
                "Ritrascrizione della parte 3 in corso: modifiche disabilitate fino al termine",
            ),
        )
        val carte = presenter.dati.pannello!!.carte
        assertTrue(carte.isNotEmpty() && carte.none { it.azioniAbilitate })
        // reading and playback stay enabled
        assertEquals(1, presenter.dati.segmenti.size)
        presenter.azioni.riproduciSegmento(SegmentoId(1))
        advanceUntilIdle()
        assertEquals(true, lettore.stato.value.inRiproduzione)
    }

    @Test
    fun `AC-I75 senza ritrascrizione aperta nessun blocco, e con parte nulla il banner e quello di oggi`() = runTest {
        val libera = presentatore(this, 1)
        val unaParte = presentatore(this, 1, vista = vistaDi(1, RitrascrizioneInCorso(null), listOf(PARTI[0])))
        advanceUntilIdle()

        assertEquals(false, libera.dati.soloLettura)
        assertNull(libera.dati.bannerRitrascrizione)
        assertEquals(snastro.ui.testi.MESSAGGIO_RITRASCRIZIONE_IN_CORSO, unaParte.dati.bannerRitrascrizione)
    }

    @Test
    fun `L199 la ritrascrizione di questa Parte letta da stati nomina la Parte anche senza solaLettura`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scopeCoroutine = CoroutineScope(dispatcher)
        val vista = vistaDi(2) // solaLettura == null: the lock is only known from the `stati` source
        val presenter = RegistrazionePresenter(
            scope = scopeCoroutine, io = dispatcher, registrazioneId = vista.registrazioneId,
            trascritto = { vista }, sbobinatura = { null }, lettore = LettoreAudioFinta(),
            apriEsterno = ApriEsternoFinta(), parlanti = unaSorgentiParlantiInerte(scopeCoroutine),
            stati = {
                StatoRegistrazioneVista(
                    vista.registrazioneId, StatoElaborazioneVista.IN_CORSO, null, null, null, null, null, true, null,
                )
            },
            aggiornamenti = AggiornamentiVistaFinta(),
            riassunto = SorgenteRiassuntoS3(contenuto = {}, segno = { flowOf(null) }),
            selezioneSchedaS3 = SelezioneSchedaS3(),
            parti = SorgentiParti({ incontroVista() }, { incontroVista() }, {}),
        )
        advanceUntilIdle()

        assertEquals(true, presenter.dati.soloLettura)
        assertEquals(messaggioRitrascrizioneParteInCorso(2), presenter.dati.bannerRitrascrizione)
    }

    @Test
    fun `L199 su una sola Parte il banner da stati resta quello di oggi`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scopeCoroutine = CoroutineScope(dispatcher)
        val vista = vistaDi(1, parti = listOf(PARTI[0]))
        val presenter = RegistrazionePresenter(
            scope = scopeCoroutine, io = dispatcher, registrazioneId = vista.registrazioneId,
            trascritto = { vista }, sbobinatura = { null }, lettore = LettoreAudioFinta(),
            apriEsterno = ApriEsternoFinta(), parlanti = unaSorgentiParlantiInerte(scopeCoroutine),
            stati = {
                StatoRegistrazioneVista(
                    vista.registrazioneId, StatoElaborazioneVista.IN_ATTESA, null, null, null, null, null, true, null,
                )
            },
            aggiornamenti = AggiornamentiVistaFinta(),
            riassunto = SorgenteRiassuntoS3(contenuto = {}, segno = { flowOf(null) }),
            selezioneSchedaS3 = SelezioneSchedaS3(),
            parti = unaSorgentiPartiInerte(),
        )
        advanceUntilIdle()

        assertEquals(snastro.ui.testi.MESSAGGIO_RITRASCRIZIONE_IN_CORSO, presenter.dati.bannerRitrascrizione)
    }

    @Test
    fun `AC-I74 un guasto nel leggere l'Incontro non porta la schermata in errore`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scopeCoroutine = CoroutineScope(dispatcher)
        val vista = vistaDi(2)
        val presenter = RegistrazionePresenter(
            scope = scopeCoroutine, io = dispatcher, registrazioneId = vista.registrazioneId,
            trascritto = { vista }, sbobinatura = { null }, lettore = LettoreAudioFinta(),
            apriEsterno = ApriEsternoFinta(), parlanti = unaSorgentiParlantiInerte(scopeCoroutine),
            stati = { null }, aggiornamenti = AggiornamentiVistaFinta(),
            riassunto = SorgenteRiassuntoS3(contenuto = {}, segno = { flowOf(null) }),
            selezioneSchedaS3 = SelezioneSchedaS3(),
            parti = SorgentiParti(incontro = { error("guasto") }, incontroDi = { error("guasto") }, vaiAllaParte = {}),
        )
        advanceUntilIdle()

        val parte = assertIs<IntestazioneParte>(presenter.dati.parte)
        assertEquals("Parte 2 della riunione", parte.titoloIncontro)
        assertNull(parte.ora)
    }
}
