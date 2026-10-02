package snastro.ui.registrazione

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.sintesi.applicazione.letture.DisponibilitaVista
import snastro.sintesi.applicazione.letture.ImpostazioniSintesiVista
import snastro.sintesi.applicazione.letture.RiassuntoVista
import snastro.sintesi.applicazione.letture.StatoModelloVista
import snastro.trascrizione.applicazione.letture.ParteRef
import snastro.trascrizione.applicazione.letture.SegmentoTrascrittoView
import snastro.trascrizione.applicazione.letture.TrascrittoView
import snastro.trascrizione.applicazione.letture.VoceTrascrittoView
import snastro.ui.AggiornamentiVistaFinta
import snastro.ui.ApriEsternoFinta
import snastro.ui.coda.PosizioniCoda
import snastro.ui.lettore.LettoreAudioFinta
import snastro.ui.modelli.ServizioModelliFinta
import snastro.ui.riassunto.RiassuntoPresenter
import java.time.Clock
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private val INCONTRO = IncontroId("incontro-1")
private val PARTI = (1..3).map { ParteRef(RegistrazioneId("parte-$it"), it) }

private fun vistaDi(numero: Int) = TrascrittoView(
    registrazioneId = PARTI[numero - 1].registrazioneId,
    incontroId = INCONTRO,
    titolo = "Parte $numero",
    dataRegistrazione = LocalDate.of(2026, 9, 30),
    durataMs = 4_500_000,
    segmenti = listOf(SegmentoTrascrittoView(SegmentoId(1), VoceId(1), 0, 2_000, "testo")),
    voci = listOf(VoceTrascrittoView(VoceId(1), "Voce 1")),
    numeroParte = numero,
    parti = PARTI,
)

/** AC-I81 end to end at presenter level: a Fonte chip of another Parte, from the Riassunto tab. */
@OptIn(ExperimentalCoroutinesApi::class)
class RegistrazioneChipRiassuntoTest {
    private fun presentatoreParte(
        scope: TestScope,
        numero: Int,
        selezione: SelezioneSchedaS3,
    ): RegistrazionePresenter {
        val dispatcher = StandardTestDispatcher(scope.testScheduler)
        val scopeCoroutine = CoroutineScope(dispatcher)
        val vista = vistaDi(numero)
        return RegistrazionePresenter(
            scope = scopeCoroutine,
            io = dispatcher,
            registrazioneId = vista.registrazioneId,
            trascritto = { vista },
            sbobinatura = { null },
            lettore = LettoreAudioFinta(),
            apriEsterno = ApriEsternoFinta(),
            parlanti = unaSorgentiParlantiInerte(scopeCoroutine),
            stati = { null },
            aggiornamenti = AggiornamentiVistaFinta(),
            riassunto = SorgenteRiassuntoS3(contenuto = {}, segno = { flowOf(null) }),
            selezioneSchedaS3 = selezione,
            parti = unaSorgentiPartiInerte(),
        )
    }

    @Test
    fun `AC-I81 la chip della parte 3 dal Riassunto della parte 1 apre la parte 3 sul Riassunto e suona`() =
        runTest {
            val selezione = SelezioneSchedaS3()
            val eventi = mutableListOf<String>()
            var aperta: RegistrazionePresenter? = null
            val dispatcher = StandardTestDispatcher(testScheduler)
            val scopeRiassunto = CoroutineScope(dispatcher)
            val riassunto = RiassuntoPresenter(
                scope = scopeRiassunto,
                io = StandardTestDispatcher(testScheduler),
                registrazioneId = PARTI[0].registrazioneId,
                vista = {
                    RiassuntoVista(
                        incontroId = INCONTRO,
                        numParti = 3,
                        modello = StatoModelloVista.Installato,
                        richiestaAperta = null,
                        ultimoFallimento = null,
                        disponibilita = DisponibilitaVista.Disponibile,
                        argomentoPrecompilato = null,
                        mostrato = null,
                    )
                },
                impostazioni = { ImpostazioniSintesiVista(2_000, 300, 2_500) },
                posizioni = { PosizioniCoda.VUOTA },
                riassumiCmd = { error("non usato") },
                modificaLunghezzaMassimaCmd = { error("non usato") },
                servizioModelli = ServizioModelliFinta(),
                aggiornamenti = AggiornamentiVistaFinta(),
                clock = Clock.systemUTC(),
                idModelloLinguistico = "llm",
                dimensioneModelloLinguisticoByte = 1,
                limiteCaratteriArgomento = 200,
                vaiAllaParte = { id ->
                    eventi += "vai:${id.valore}"
                    aperta = presentatoreParte(this, PARTI.single { it.registrazioneId == id }.numero, selezione)
                },
                riproduciDa = { id, ms -> eventi += "play:${id.valore}@$ms" },
            )
            try {
                val prima = presentatoreParte(this, 1, selezione)
                advanceUntilIdle()
                prima.azioni.selezionaScheda(SchedaS3.RIASSUNTO)

                riassunto.azioni.apriFonte(PARTI[2].registrazioneId, 750_000)
                advanceUntilIdle()

                assertEquals(listOf("play:parte-3@750000", "vai:parte-3"), eventi)
                val terza = assertIs<RegistrazioneUiStato.Dati>(aperta!!.stato.value)
                assertEquals(3, terza.parte?.numero)
                assertEquals(SchedaS3.RIASSUNTO, terza.schedaSelezionata)
            } finally {
                scopeRiassunto.cancel()
            }
        }
}
