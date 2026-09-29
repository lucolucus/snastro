package snastro.ui.registrazione

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import snastro.ui.AggiornamentiVistaFinta
import snastro.ui.ApriEsternoFinta
import snastro.ui.lettore.LettoreAudioFinta
import snastro.ui.stile.SegnoScheda
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

private val REG_A = RegistrazioneId("id-a")
private val REG_B = RegistrazioneId("id-b")
private val DATA_1: LocalDate = LocalDate.of(2026, 3, 12)

private fun unaVista(registrazioneId: RegistrazioneId) = TrascrittoView(
    registrazioneId = registrazioneId,
    titolo = "Seduta del 12 marzo",
    dataRegistrazione = DATA_1,
    durataMs = 125_000,
    segmenti = listOf(SegmentoTrascrittoView(SegmentoId(1), VoceId(1), 0, 2_000, "Buongiorno a tutti.")),
    voci = listOf(VoceTrascrittoView(VoceId(1), "Voce 1")),
)

private fun unaSorgente(segno: MutableStateFlow<SegnoScheda?> = MutableStateFlow(null)) =
    SorgenteRiassuntoS3(contenuto = {}, segno = { segno })

/**
 * AC-S120..S122: the presenter half of the Riassunto tab — [RegistrazionePresenter]'s
 * [SorgenteRiassuntoS3]/[SelezioneSchedaS3] collaborators. AC-S123 (the banner precedence table) is a
 * pure predicate of [RegistrazioneUiStato.Dati], tested state-only in `RegistrazioneUiStatoTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RegistrazioneSchedeTest {
    @Suppress("LongParameterList") // one parameter per RegistrazionePresenter collaborator this file exercises
    private fun presentatore(
        scope: TestScope,
        registrazioneId: RegistrazioneId = REG_A,
        riassunto: SorgenteRiassuntoS3 = unaSorgente(),
        selezioneSchedaS3: SelezioneSchedaS3 = SelezioneSchedaS3(),
    ): RegistrazionePresenter {
        val dispatcher = StandardTestDispatcher(scope.testScheduler)
        val scopeCoroutine = CoroutineScope(dispatcher)
        return RegistrazionePresenter(
            scope = scopeCoroutine,
            io = dispatcher,
            registrazioneId = registrazioneId,
            trascritto = { unaVista(registrazioneId) },
            documento = { null },
            lettore = LettoreAudioFinta(),
            apriEsterno = ApriEsternoFinta(),
            parlanti = unaSorgentiParlantiInerte(scopeCoroutine),
            stati = { null },
            aggiornamenti = AggiornamentiVistaFinta(),
            riassunto = riassunto,
            selezioneSchedaS3 = selezioneSchedaS3,
        )
    }

    private val RegistrazionePresenter.dati get() = assertIs<RegistrazioneUiStato.Dati>(stato.value)

    @Test
    fun `AC-S120 con la sorgente Trascrizione e selezionata di default e Riassunto mostra lo slot`() = runTest {
        val presenter = presentatore(this, riassunto = unaSorgente())
        advanceUntilIdle()

        assertEquals(SchedaS3.TRASCRIZIONE, presenter.dati.schedaSelezionata)

        presenter.azioni.selezionaScheda(SchedaS3.RIASSUNTO)
        advanceUntilIdle()
        assertEquals(SchedaS3.RIASSUNTO, presenter.dati.schedaSelezionata)
    }

    @Test
    fun `AC-S121 la scheda selezionata resta per finestra passando da una registrazione all altra`() = runTest {
        val selezione = SelezioneSchedaS3()
        val sorgente = unaSorgente()

        val presenterA = presentatore(this, REG_A, riassunto = sorgente, selezioneSchedaS3 = selezione)
        advanceUntilIdle()
        presenterA.azioni.selezionaScheda(SchedaS3.RIASSUNTO)
        advanceUntilIdle()
        assertEquals(SchedaS3.RIASSUNTO, presenterA.dati.schedaSelezionata)

        // A NEW presenter (a different Registrazione, `registrazioneId` is a constructor `val`) built on
        // the SAME `SelezioneSchedaS3` — the choice must survive the navigation.
        val presenterB = presentatore(this, REG_B, riassunto = sorgente, selezioneSchedaS3 = selezione)
        advanceUntilIdle()
        assertEquals(SchedaS3.RIASSUNTO, presenterB.dati.schedaSelezionata)
    }

    @Test
    fun `AC-S122 il segno della scheda Riassunto segue il flusso InAttesa poi InCorso poi nessuno`() = runTest {
        val segno = MutableStateFlow<SegnoScheda?>(null)
        val presenter = presentatore(this, riassunto = unaSorgente(segno))
        advanceUntilIdle()
        assertNull(presenter.dati.segnoRiassunto)

        segno.value = SegnoScheda.InAttesa
        advanceUntilIdle()
        assertEquals(SegnoScheda.InAttesa, presenter.dati.segnoRiassunto)

        segno.value = SegnoScheda.InCorso
        advanceUntilIdle()
        assertEquals(SegnoScheda.InCorso, presenter.dati.segnoRiassunto)

        segno.value = null
        advanceUntilIdle()
        assertNull(presenter.dati.segnoRiassunto)
    }

    @Test
    fun `AC-S122 il segno resta dopo un ricaricamento`() = runTest {
        val segno = MutableStateFlow<SegnoScheda?>(SegnoScheda.InCorso)
        val presenter = presentatore(this, riassunto = unaSorgente(segno))
        advanceUntilIdle()
        assertEquals(SegnoScheda.InCorso, presenter.dati.segnoRiassunto)

        presenter.azioni.riprova()
        advanceUntilIdle()
        assertEquals(SegnoScheda.InCorso, presenter.dati.segnoRiassunto, "un ricaricamento non deve azzerare il segno")
    }
}
