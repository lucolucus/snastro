package snastro.ui.registrazione

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import snastro.kernel.unIncontroDi
import snastro.trascrizione.applicazione.letture.ParteRef
import snastro.trascrizione.applicazione.letture.TrascrittoView
import snastro.ui.AggiornamentiVistaFinta
import snastro.ui.ApriEsterno
import snastro.ui.ApriEsternoFinta
import snastro.ui.causatoDa
import snastro.ui.lettore.LettoreAudioFinta
import snastro.ui.registriLogUi
import snastro.ui.testi.MESSAGGIO_ERRORE_CARICAMENTO_TRASCRITTO
import snastro.ui.testi.MESSAGGIO_ERRORE_GENERICO
import java.time.LocalDate
import java.util.logging.Level
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private fun unaVistaVuota() = TrascrittoView(
    registrazioneId = REG,
    incontroId = unIncontroDi(REG),
    titolo = "Seduta",
    dataRegistrazione = LocalDate.of(2026, 3, 12),
    durataMs = 10_000,
    segmenti = emptyList(),
    voci = emptyList(),
    numeroParte = 1,
    parti = listOf(ParteRef(REG, 1)),
)

/** An [ApriEsterno] whose `apriFile` throws [guasto]. */
private class ApriEsternoRotto(private val guasto: Exception) : ApriEsterno by ApriEsternoFinta() {
    override fun apriFile(percorso: String) = throw guasto
}

/**
 * CR-7 at S3's own edges (ADR 0041 §3): a thrown read of the Trascritto and a thrown 'Apri sbobinatura' are logged at
 * WARNING with their cause, and the screen still shows its message.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RegistrazionePresenterGuastiTest {
    private fun presentatore(
        scope: TestScope,
        trascritto: () -> TrascrittoView? = ::unaVistaVuota,
        apriEsterno: ApriEsterno = ApriEsternoFinta(),
    ): RegistrazionePresenter {
        val dispatcher = StandardTestDispatcher(scope.testScheduler)
        val scopeCoroutine = CoroutineScope(dispatcher)
        return RegistrazionePresenter(
            scope = scopeCoroutine,
            io = dispatcher,
            registrazioneId = REG,
            trascritto = trascritto,
            sbobinatura = { "/progetto/sbobinature/seduta.docx" },
            lettore = LettoreAudioFinta(),
            apriEsterno = apriEsterno,
            parlanti = unaSorgentiParlantiInerte(scopeCoroutine),
            stati = { null },
            aggiornamenti = AggiornamentiVistaFinta(),
            riassunto = SorgenteRiassuntoS3(contenuto = {}, segno = { flowOf(null) }),
            selezioneSchedaS3 = SelezioneSchedaS3(),
            parti = unaSorgentiPartiInerte(),
        )
    }

    @Test
    fun `CR-7 una lettura del trascritto che lancia e registrata e la schermata mostra l errore di caricamento`() =
        runTest {
            val guasto = IllegalStateException("trascritto illeggibile")
            lateinit var presenter: RegistrazionePresenter

            val registri = registriLogUi {
                presenter = presentatore(this, trascritto = { throw guasto })
                advanceUntilIdle()
            }

            assertEquals(Level.WARNING, registri.single { it.causatoDa(guasto) }.level)
            assertEquals(RegistrazioneUiStato.Errore(MESSAGGIO_ERRORE_CARICAMENTO_TRASCRITTO), presenter.stato.value)
        }

    @Test
    fun `CR-7 Apri sbobinatura che lancia e registrata e la schermata mostra l errore generico`() = runTest {
        val guasto = IllegalStateException("nessuna applicazione predefinita")
        val presenter = presentatore(this, apriEsterno = ApriEsternoRotto(guasto))
        advanceUntilIdle()

        val registri = registriLogUi {
            presenter.azioni.apriSbobinatura()
            advanceUntilIdle()
        }

        assertEquals(Level.WARNING, registri.single { it.causatoDa(guasto) }.level)
        assertEquals(MESSAGGIO_ERRORE_GENERICO, assertIs<RegistrazioneUiStato.Dati>(presenter.stato.value).errore)
    }
}
