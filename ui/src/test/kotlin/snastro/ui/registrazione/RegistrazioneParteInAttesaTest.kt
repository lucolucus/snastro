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
import snastro.progetto.applicazione.letture.IncontroDelProgettoVista
import snastro.progetto.applicazione.letture.ParteVista
import snastro.trascrizione.applicazione.letture.ParteRef
import snastro.trascrizione.applicazione.letture.StatoElaborazioneVista
import snastro.trascrizione.applicazione.letture.StatoRegistrazioneVista
import snastro.ui.AggiornamentiVistaFinta
import snastro.ui.ApriEsternoFinta
import snastro.ui.lettore.LettoreAudioFinta
import snastro.ui.testi.MESSAGGIO_ERRORE_CARICAMENTO_TRASCRITTO
import snastro.ui.testi.messaggioParteInTrascrizione
import snastro.ui.testi.messaggioParteNonTrascritta
import snastro.ui.testi.messaggioParteTrascrizioneFallita
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private val PARTI = (1..3).map { RegistrazioneId("parte-$it") }
private val QUESTA = PARTI[2]

private fun incontro(numParti: Int = 3) = IncontroDelProgettoVista(
    incontroId = IncontroId("incontro-1"),
    titolo = "Riunione di progetto",
    data = LocalDate.of(2026, 9, 30),
    durataMs = 9_000_000,
    numParti = numParti,
    parti = PARTI.take(numParti).mapIndexed { i, id ->
        val ora = LocalTime.of(10 + i, 0)
        ParteVista(id, i + 1, "Parte ${i + 1} della riunione", LocalDate.of(2026, 9, 30), ora, 3_000_000)
    },
)

private fun stato(s: StatoElaborazioneVista) = StatoRegistrazioneVista(
    registrazioneId = QUESTA,
    stato = s,
    fase = null,
    avviataAlle = null,
    motivoFallimento = null,
    numVoci = null,
    numeroPersone = null,
    trascrittoDisponibile = false,
    elaborazioneId = null,
)

/** D-0051 (L198): a Parte with no Trascritto yet is "Parte in attesa" with the switcher, never the generic Errore. */
@OptIn(ExperimentalCoroutinesApi::class)
class RegistrazioneParteInAttesaTest {
    private fun presentatore(
        scope: TestScope,
        stati: () -> StatoRegistrazioneVista? = { stato(StatoElaborazioneVista.IN_CORSO) },
        incontroDi: () -> IncontroDelProgettoVista? = { incontro() },
        vaiAllaParte: (RegistrazioneId) -> Unit = {},
    ): RegistrazionePresenter {
        val dispatcher = StandardTestDispatcher(scope.testScheduler)
        val scopeCoroutine = CoroutineScope(dispatcher)
        return RegistrazionePresenter(
            scope = scopeCoroutine, io = dispatcher, registrazioneId = QUESTA,
            trascritto = { null }, sbobinatura = { null }, lettore = LettoreAudioFinta(),
            apriEsterno = ApriEsternoFinta(), parlanti = unaSorgentiParlantiInerte(scopeCoroutine),
            stati = stati, aggiornamenti = AggiornamentiVistaFinta(),
            riassunto = SorgenteRiassuntoS3(contenuto = {}, segno = { flowOf(null) }),
            selezioneSchedaS3 = SelezioneSchedaS3(),
            parti = SorgentiParti({ null }, { incontroDi() }, vaiAllaParte),
        )
    }

    private fun TestScope.attesa(presenter: RegistrazionePresenter): RegistrazioneUiStato.ParteInAttesa {
        advanceUntilIdle()
        return assertIs<RegistrazioneUiStato.ParteInAttesa>(presenter.stato.value)
    }

    @Test
    fun `D-0051 una Parte in trascrizione mostra Parte in attesa con il selettore`() = runTest {
        val s = attesa(presentatore(this))

        assertEquals(messaggioParteInTrascrizione(3), s.messaggio)
        assertEquals("Parte 3 della riunione", s.titolo)
        assertEquals(3, s.parte.numero)
        assertEquals(3, s.parte.totale)
        assertEquals("Riunione di progetto", s.parte.titoloIncontro)
        assertEquals(PARTI.mapIndexed { i, id -> ParteRef(id, i + 1) }, s.parte.parti)
    }

    @Test
    fun `D-0051 una Parte in coda dice lo stesso, mai ancora trascritta e fallita hanno il loro testo`() = runTest {
        assertEquals(
            messaggioParteInTrascrizione(3),
            attesa(presentatore(this, stati = { stato(StatoElaborazioneVista.IN_ATTESA) })).messaggio,
        )
        assertEquals(
            messaggioParteNonTrascritta(3),
            attesa(presentatore(this, stati = { stato(StatoElaborazioneVista.NON_AVVIATA) })).messaggio,
        )
        assertEquals(
            messaggioParteTrascrizioneFallita(3),
            attesa(presentatore(this, stati = { stato(StatoElaborazioneVista.FALLITA) })).messaggio,
        )
    }

    @Test
    fun `D-0051 il selettore apre un altra Parte`() = runTest {
        val aperte = mutableListOf<RegistrazioneId>()
        val presenter = presentatore(this, vaiAllaParte = { aperte += it })
        attesa(presenter)

        presenter.azioni.vaiAllaParte(PARTI[0])

        assertEquals(listOf(PARTI[0]), aperte)
    }

    @Test
    fun `D-0051 una sola Parte o un Incontro illeggibile restano l'errore generico`() = runTest {
        listOf(
            presentatore(this, incontroDi = { incontro(numParti = 1) }),
            presentatore(this, incontroDi = { error("guasto") }),
            presentatore(this, incontroDi = { null }),
            presentatore(this, stati = { null }),
            presentatore(this, stati = { error("guasto") }),
            presentatore(this, stati = { stato(StatoElaborazioneVista.COMPLETATA) }),
        ).forEach { presenter ->
            advanceUntilIdle()
            assertEquals(
                RegistrazioneUiStato.Errore(MESSAGGIO_ERRORE_CARICAMENTO_TRASCRITTO),
                presenter.stato.value,
            )
        }
    }
}
