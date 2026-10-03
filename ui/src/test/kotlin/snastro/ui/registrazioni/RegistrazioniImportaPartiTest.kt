package snastro.ui.registrazioni

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import snastro.kernel.ConsegnaDopoCommitFallita
import snastro.kernel.Esito
import snastro.kernel.IncontroId
import snastro.kernel.ProgettoId
import snastro.progetto.applicazione.comandi.AggiungiRegistrazione
import snastro.progetto.applicazione.comandi.Destinazione
import snastro.progetto.applicazione.porte.ErroreApplicazioneProgetto
import snastro.ui.AggiornamentiVistaFinta
import snastro.ui.coda.PosizioniCoda
import snastro.ui.lettore.LettoreAudioFinta
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private val PROGETTO = ProgettoId("progetto-1")
private val INCONTRO = IncontroId("incontro-1")
private val TRE_FILE = listOf("/sorgenti/a.m4a", "/sorgenti/b.m4a", "/sorgenti/c.m4a")

/** AC-I70/AC-I71: the multi-file import dialog and "Aggiungi parti…" on the presenter. */
@OptIn(ExperimentalCoroutinesApi::class)
class RegistrazioniImportaPartiTest {
    private fun presentatore(
        scope: TestScope,
        aggiungi: (AggiungiRegistrazione) -> Esito<Unit>,
    ): RegistrazioniPresenter {
        val dispatcher = StandardTestDispatcher(scope.testScheduler)
        return RegistrazioniPresenter(
            scope = CoroutineScope(dispatcher),
            io = dispatcher,
            progettoId = PROGETTO,
            registrazioni = { emptyList() },
            incontri = { emptyList() },
            aggiungiRegistrazione = aggiungi,
            modificaDataRegistrazione = { error("non atteso") },
            rinominaRegistrazione = { error("non atteso") },
            lettore = LettoreAudioFinta(),
            aggiornamenti = AggiornamentiVistaFinta(),
            clock = Clock.fixed(Instant.parse("2026-09-23T10:00:00Z"), ZoneOffset.UTC),
            statiElaborazione = { emptyList() },
            avviaElaborazione = { error("non atteso") },
            apriRegistrazione = { error("non atteso") },
            identificazioniIncontri = { emptyMap() },
            ritrascrivi = { error("non atteso") },
            annullaElaborazione = { error("non atteso") },
            eliminaRegistrazione = { error("non atteso") },
            posizioniNellaCoda = { PosizioniCoda.VUOTA },
            avviaElaborazioniDellIncontro = { error("avviaElaborazioniDellIncontro non atteso in questo test") },
            modificaOraDiInizioRegistrazione = { _, _ -> error("modificaOraDiInizio non atteso in questo test") },
            numeroPersonePrecompilato = { null },
        )
    }

    private fun registra(inviati: MutableList<AggiungiRegistrazione>, c: AggiungiRegistrazione): Esito<Unit> {
        inviati += c
        return Esito.Ok(Unit)
    }

    private fun TestScope.dati(p: RegistrazioniPresenter) = assertIs<RegistrazioniUiStato.Dati>(p.stato.value)

    private fun confermatoMaSeguitoFallito(inviati: MutableList<AggiungiRegistrazione>, c: AggiungiRegistrazione):
        Esito<Unit> {
        inviati += c
        throw ConsegnaDopoCommitFallita(IllegalStateException("abbonato dopo-commit fallito"))
    }

    @Test
    fun `L260 un import confermato con un abbonato dopo-commit fallito non mostra errore`() = runTest {
        val inviati = mutableListOf<AggiungiRegistrazione>()
        val p = presentatore(this) { confermatoMaSeguitoFallito(inviati, it) }
        advanceUntilIdle()

        p.azioni.importa(listOf("/sorgenti/a.m4a"))
        advanceUntilIdle()
        assertNull(dati(p).errore)
        assertEquals(false, dati(p).importoInCorso)

        p.azioni.importa(TRE_FILE)
        p.azioni.confermaImporta()
        advanceUntilIdle()
        assertNull(dati(p).dialogoImporta, "il dialogo si chiude come su Ok")

        p.azioni.aggiungiParti(INCONTRO, "Riunione", TRE_FILE)
        advanceUntilIdle()
        assertNull(dati(p).errore)
        assertNotNull(dati(p).avviso)
        assertEquals(3, inviati.size)
    }

    @Test
    fun `AC-I70 un file solo non apre il dialogo e invia NuovoIncontro`() = runTest {
        val inviati = mutableListOf<AggiungiRegistrazione>()
        val p = presentatore(this) { registra(inviati, it) }
        advanceUntilIdle()

        p.azioni.importa(listOf("/sorgenti/a.m4a"))
        advanceUntilIdle()

        val atteso = AggiungiRegistrazione(PROGETTO, listOf("/sorgenti/a.m4a"), Destinazione.NuovoIncontro)
        assertEquals(listOf(atteso), inviati)
        assertNull(dati(p).dialogoImporta)
    }

    @Test
    fun `AC-I70 tre file aprono il dialogo con Un incontro preselezionato e non inviano nulla`() = runTest {
        val p = presentatore(this) { error("nessun comando prima di Importa") }
        advanceUntilIdle()

        p.azioni.importa(TRE_FILE)
        advanceUntilIdle()

        assertEquals(DialogoImporta(TRE_FILE, SceltaImporta.UnIncontro), dati(p).dialogoImporta)
    }

    @Test
    fun `AC-I70 Importa con un incontro in 3 parti invia UN comando NuovoIncontro con i file in ordine`() = runTest {
        val inviati = mutableListOf<AggiungiRegistrazione>()
        val p = presentatore(this) { registra(inviati, it) }
        advanceUntilIdle()
        p.azioni.importa(TRE_FILE)

        p.azioni.confermaImporta()
        advanceUntilIdle()

        assertEquals(listOf(AggiungiRegistrazione(PROGETTO, TRE_FILE, Destinazione.NuovoIncontro)), inviati)
        assertNull(dati(p).dialogoImporta)
    }

    @Test
    fun `AC-I70 3 incontri separati invia UN comando IncontriSeparati`() = runTest {
        val inviati = mutableListOf<AggiungiRegistrazione>()
        val p = presentatore(this) { registra(inviati, it) }
        advanceUntilIdle()
        p.azioni.importa(TRE_FILE)
        p.azioni.scegliImporta(SceltaImporta.IncontriSeparati)

        p.azioni.confermaImporta()
        advanceUntilIdle()

        assertEquals(listOf(AggiungiRegistrazione(PROGETTO, TRE_FILE, Destinazione.IncontriSeparati)), inviati)
    }

    @Test
    fun `AC-I70 Annulla chiude il dialogo e non invia nulla`() = runTest {
        val p = presentatore(this) { error("nessun comando dopo Annulla") }
        advanceUntilIdle()
        p.azioni.importa(TRE_FILE)

        p.azioni.annullaImporta()
        advanceUntilIdle()

        assertNull(dati(p).dialogoImporta)
    }

    @Test
    fun `AC-I71 un file illeggibile lascia il dialogo aperto con Nessun file importato`() = runTest {
        val p = presentatore(this) { Esito.Errore(ErroreApplicazioneProgetto.AudioNonLeggibile("/sorgenti/b.m4a")) }
        advanceUntilIdle()
        p.azioni.importa(TRE_FILE)

        p.azioni.confermaImporta()
        advanceUntilIdle()

        val dialogo = assertNotNull(dati(p).dialogoImporta)
        assertEquals("Nessun file importato: «b.m4a» non è leggibile.", dialogo.errore)
        assertEquals(false, dialogo.invioInCorso)
        assertEquals(TRE_FILE, dialogo.percorsi)
    }

    @Test
    fun `AC-I71 Aggiungi parti invia Incontro(id) e mostra l avviso chiudibile`() = runTest {
        val inviati = mutableListOf<AggiungiRegistrazione>()
        val p = presentatore(this) { registra(inviati, it) }
        advanceUntilIdle()

        p.azioni.aggiungiParti(INCONTRO, "Riunione", listOf("/sorgenti/a.m4a", "/sorgenti/b.m4a"))
        advanceUntilIdle()

        assertEquals(
            listOf(
                AggiungiRegistrazione(
                    PROGETTO,
                    listOf("/sorgenti/a.m4a", "/sorgenti/b.m4a"),
                    Destinazione.Incontro(INCONTRO),
                ),
            ),
            inviati,
        )
        assertEquals("2 parti aggiunte a «Riunione».", dati(p).avviso)
        assertEquals(false, dati(p).importoInCorso)
        p.azioni.chiudiAvviso()
        assertNull(dati(p).avviso)
    }

    @Test
    fun `AC-I71 Aggiungi parti con un file illeggibile mostra l errore e nessun avviso`() = runTest {
        val p = presentatore(this) { Esito.Errore(ErroreApplicazioneProgetto.AudioNonLeggibile("/sorgenti/x.m4a")) }
        advanceUntilIdle()

        p.azioni.aggiungiParti(INCONTRO, "Riunione", listOf("/sorgenti/x.m4a"))
        advanceUntilIdle()

        assertEquals("Nessun file importato: «x.m4a» non è leggibile.", dati(p).errore)
        assertNull(dati(p).avviso)
        assertEquals(false, dati(p).importoInCorso)
    }

    @Test
    fun `L183 un secondo Importa mentre il comando e in corso non ne invia un altro`() = runTest {
        val inviati = mutableListOf<AggiungiRegistrazione>()
        val p = presentatore(this) { registra(inviati, it) }
        advanceUntilIdle()
        p.azioni.importa(TRE_FILE)

        p.azioni.confermaImporta()
        p.azioni.confermaImporta() // still in flight: the first has not run yet
        advanceUntilIdle()

        assertEquals(1, inviati.size)
    }

    @Test
    fun `L183 Annulla e la scelta mentre il comando e in corso non fanno nulla`() = runTest {
        val inviati = mutableListOf<AggiungiRegistrazione>()
        val p = presentatore(this) { registra(inviati, it) }
        advanceUntilIdle()
        p.azioni.importa(TRE_FILE)

        p.azioni.confermaImporta()
        p.azioni.annullaImporta()
        p.azioni.scegliImporta(SceltaImporta.IncontriSeparati)

        val dialogo = assertNotNull(dati(p).dialogoImporta)
        assertEquals(true, dialogo.invioInCorso)
        assertEquals(SceltaImporta.UnIncontro, dialogo.scelta)
        advanceUntilIdle()
        assertEquals(listOf(AggiungiRegistrazione(PROGETTO, TRE_FILE, Destinazione.NuovoIncontro)), inviati)
        assertNull(dati(p).dialogoImporta)
    }

    @Test
    fun `L183 un nuovo drop mentre il dialogo e aperto e ignorato e il dialogo resta com e`() = runTest {
        val p = presentatore(this) { error("nessun comando") }
        advanceUntilIdle()
        p.azioni.importa(TRE_FILE)

        p.azioni.importa(listOf("/altro/d.m4a", "/altro/e.m4a"))
        p.azioni.importa(listOf("/altro/f.m4a"))
        advanceUntilIdle()

        assertEquals(DialogoImporta(TRE_FILE, SceltaImporta.UnIncontro), dati(p).dialogoImporta)
    }
}
