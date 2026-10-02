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
import snastro.progetto.applicazione.comandi.EliminaRegistrazione
import snastro.progetto.applicazione.letture.IncontroDelProgettoVista
import snastro.progetto.applicazione.letture.ParteVista
import snastro.progetto.applicazione.letture.RegistrazioneDelProgettoVista
import snastro.trascrizione.applicazione.letture.StatoElaborazioneVista
import snastro.trascrizione.applicazione.letture.StatoRegistrazioneVista
import snastro.trascrizione.applicazione.porte.FaseElaborazione
import snastro.ui.AggiornamentiVistaFinta
import snastro.ui.coda.PosizioniCoda
import snastro.ui.lettore.LettoreAudioFinta
import snastro.ui.testi.MESSAGGIO_CONFERMA_ELIMINA_PARTE_CON_TRASCRITTO
import snastro.ui.testi.MESSAGGIO_ELIMINA_DISABILITATA_IN_CODA
import snastro.ui.testi.MESSAGGIO_ELIMINA_DISABILITATA_IN_CORSO
import snastro.ui.testi.messaggioEliminata
import snastro.ui.testi.titoloConfermaEliminaParte
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

private val P1 = RegistrazioneId("parte-1")
private val P2 = RegistrazioneId("parte-2")
private val P3 = RegistrazioneId("parte-3")
private val INCONTRO = IncontroId("incontro-1")
private const val TITOLO = "Riunione di staff"
private val DATA = LocalDate.of(2026, 9, 30)

private fun parte(id: RegistrazioneId, numero: Int) =
    ParteVista(id, numero, if (numero == 1) TITOLO else "file $numero", DATA, null, 60_000)

private fun incontro(ids: List<RegistrazioneId>) = IncontroDelProgettoVista(
    incontroId = INCONTRO,
    titolo = TITOLO,
    data = DATA,
    durataMs = 60_000L * ids.size,
    numParti = ids.size,
    parti = ids.mapIndexed { i, id -> parte(id, i + 1) },
)

private fun stato(id: RegistrazioneId, s: StatoElaborazioneVista, trascritto: Boolean): StatoRegistrazioneVista {
    val inCorso = s == StatoElaborazioneVista.IN_CORSO
    return StatoRegistrazioneVista(
        registrazioneId = id,
        stato = s,
        fase = if (inCorso) FaseElaborazione.DIARIZZAZIONE else null,
        avviataAlle = if (inCorso) Instant.parse("2026-09-30T10:00:00Z") else null,
        motivoFallimento = null,
        numVoci = if (trascritto) 3 else null,
        numeroPersone = null,
        trascrittoDisponibile = trascritto,
        elaborazioneId = ElaborazioneId("e-${id.valore}"),
    )
}

/**
 * `dialogo-elimina-parte` (ADR 0038 §5): the Elimina confirmation of a Parte of a multi-part Incontro, and its
 * disabled cases per Parte.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RegistrazioniEliminaParteTest {
    private val eliminazioni = mutableListOf<EliminaRegistrazione>()
    private var presenti = mutableListOf(P1, P2, P3)
    private var lettureIncontri: () -> List<IncontroDelProgettoVista> = { listOf(incontro(presenti)) }

    private fun presentatore(
        scope: TestScope,
        stati: Map<RegistrazioneId, StatoElaborazioneVista> = emptyMap(),
    ): RegistrazioniPresenter {
        val dispatcher = StandardTestDispatcher(scope.testScheduler)
        return RegistrazioniPresenter(
            scope = CoroutineScope(dispatcher),
            io = dispatcher,
            progettoId = ProgettoId("progetto-1"),
            registrazioni = {
                presenti.mapIndexed { i, id ->
                    RegistrazioneDelProgettoVista(id, if (i == 0) TITOLO else "file ${i + 1}", DATA, 60_000)
                }
            },
            incontri = { lettureIncontri() },
            aggiungiRegistrazione = { error("non atteso") },
            modificaDataRegistrazione = { error("non atteso") },
            rinominaRegistrazione = { error("non atteso") },
            lettore = LettoreAudioFinta(),
            aggiornamenti = AggiornamentiVistaFinta(),
            clock = Clock.fixed(Instant.parse("2026-09-30T10:00:00Z"), ZoneOffset.UTC),
            statiElaborazione = { ids ->
                ids.map { stato(it, stati[it] ?: StatoElaborazioneVista.COMPLETATA, true) }
            },
            avviaElaborazione = { error("non atteso") },
            apriRegistrazione = { error("non atteso") },
            identificazioniIncontri = { emptyMap() },
            ritrascrivi = { error("non atteso") },
            annullaElaborazione = { error("non atteso") },
            eliminaRegistrazione = { c ->
                eliminazioni += c
                presenti.remove(c.registrazioneId)
                Esito.Ok(Unit)
            },
            posizioniNellaCoda = { PosizioniCoda.VUOTA },
            avviaElaborazioniDellIncontro = { error("avviaElaborazioniDellIncontro non atteso in questo test") },
            modificaOraDiInizioRegistrazione = { _, _ -> error("modificaOraDiInizio non atteso in questo test") },
            numeroPersonePrecompilato = { null },
        )
    }

    private fun RegistrazioniPresenter.righe() = assertIs<RegistrazioniUiStato.Dati>(stato.value).righe
    private fun RegistrazioniPresenter.riga(id: RegistrazioneId) = righe().single { it.registrazioneId == id }

    @Test
    fun `AC-I72 una parte non ultima porta numero e titolo dell'incontro per il dialogo`() = runTest {
        val p = presentatore(this)
        advanceUntilIdle()

        assertEquals(ParteDiIncontro(numero = 2, titoloIncontro = TITOLO), p.riga(P2).parte)
        assertEquals(ParteDiIncontro(numero = 3, titoloIncontro = TITOLO), p.riga(P3).parte)
    }

    @Test
    fun `AC-I72 l'ultima parte rimasta e un incontro di una parte tengono il dialogo di oggi`() = runTest {
        presenti = mutableListOf(P1)
        val p = presentatore(this)
        advanceUntilIdle()

        assertNull(p.riga(P1).parte)
    }

    @Test
    fun `AC-I72 i testi ADR 0038 par 5 del dialogo di una parte non ultima`() {
        assertEquals("Eliminare la parte 2 di «$TITOLO»?", titoloConfermaEliminaParte(2, TITOLO))
        assertEquals(
            "Verranno cancellati il file audio copiato nel progetto, la trascrizione con le correzioni delle " +
                "voci, le voci che compaiono solo in questa parte, la sbobinatura e le impronte vocali ricavate " +
                "da questa parte. Il riassunto dell'incontro resta leggibile ma diventa superato. " +
                "Non si può annullare.",
            MESSAGGIO_CONFERMA_ELIMINA_PARTE_CON_TRASCRITTO,
        )
    }

    @Test
    fun `AC-I73 Elimina e disabilitato con le didascalie di oggi solo per la parte in coda o in corso`() = runTest {
        val p = presentatore(
            this,
            mapOf(P2 to StatoElaborazioneVista.IN_ATTESA, P3 to StatoElaborazioneVista.IN_CORSO),
        )
        advanceUntilIdle()

        assertEquals(StatoEliminazione.Disponibile, p.riga(P1).eliminazione)
        assertEquals(StatoEliminazione.NonDisponibile(MESSAGGIO_ELIMINA_DISABILITATA_IN_CODA), p.riga(P2).eliminazione)
        assertEquals(StatoEliminazione.NonDisponibile(MESSAGGIO_ELIMINA_DISABILITATA_IN_CORSO), p.riga(P3).eliminazione)
        p.elimina(P2)
        assertEquals(false, p.riga(P2).confermaElimina)
    }

    @Test
    fun `AC-I73 dopo Elimina la sotto-riga sparisce e compare l'avviso di oggi`() = runTest {
        val p = presentatore(this)
        advanceUntilIdle()

        p.elimina(P2)
        p.confermaElimina(P2)
        advanceUntilIdle()

        assertEquals(listOf(EliminaRegistrazione(P2)), eliminazioni)
        assertEquals(listOf(P1, P3), p.righe().map { it.registrazioneId })
        assertEquals(messaggioEliminata("file 2"), assertIs<RegistrazioniUiStato.Dati>(p.stato.value).avviso)
        assertEquals(ParteDiIncontro(2, TITOLO), p.riga(P3).parte)
    }

    @Test
    fun `L179 dopo Elimina della prima parte le altre si rinumerano e la seconda e la prima`() = runTest {
        val p = presentatore(this)
        advanceUntilIdle()

        p.elimina(P1)
        p.confermaElimina(P1)
        advanceUntilIdle()

        assertEquals(listOf(P2, P3), p.righe().map { it.registrazioneId })
        assertEquals(ParteDiIncontro(1, TITOLO), p.riga(P2).parte)
        assertEquals(ParteDiIncontro(2, TITOLO), p.riga(P3).parte)
    }

    @Test
    fun `L177 L179 un guasto della lettura degli incontri non fa fallire il caricamento e tiene il dialogo di oggi`() =
        runTest {
            lettureIncontri = { error("guasto incontri") }
            val p = presentatore(this)
            advanceUntilIdle()

            assertEquals(listOf(P1, P2, P3), p.righe().map { it.registrazioneId })
            assertEquals(listOf(null, null, null), p.righe().map { it.parte })
            p.elimina(P2)
            assertEquals(true, p.riga(P2).confermaElimina) // today's dialog, still reachable
        }

    @Test
    fun `L203 con gli incontri illeggibili la riga non offre Aggiungi parti, con la lettura riuscita si`() = runTest {
        presenti = mutableListOf(P1)
        lettureIncontri = { error("guasto incontri") }
        val guasto = presentatore(this)
        advanceUntilIdle()
        lettureIncontri = { listOf(incontro(presenti)) }
        val sano = presentatore(this)
        advanceUntilIdle()

        fun RegistrazioniPresenter.disponibile() =
            assertIs<RegistrazioniUiStato.Dati>(stato.value).incontri.single().aggiungiPartiDisponibile
        assertEquals(false, guasto.disponibile())
        assertEquals(true, sano.disponibile())
    }
}
