package snastro.ui.riassunto

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.kernel.erroreAtteso
import snastro.kernel.unIncontroDi
import snastro.sintesi.applicazione.letture.AzioneVista
import snastro.sintesi.applicazione.letture.DisponibilitaVista
import snastro.sintesi.applicazione.letture.ElementoVista
import snastro.sintesi.applicazione.letture.FallimentoVista
import snastro.sintesi.applicazione.letture.FonteVista
import snastro.sintesi.applicazione.letture.ImpostazioniSintesiVista
import snastro.sintesi.applicazione.letture.MotivoNonDisponibile
import snastro.sintesi.applicazione.letture.ParteTestoVista
import snastro.sintesi.applicazione.letture.RiassuntoMostrato
import snastro.sintesi.applicazione.letture.RiassuntoVista
import snastro.sintesi.applicazione.letture.RichiestaApertaVista
import snastro.sintesi.applicazione.letture.StatoModelloVista
import snastro.sintesi.applicazione.letture.VoceVista
import snastro.sintesi.applicazione.porte.MotivoDownload
import snastro.sintesi.dominio.ErroreSintesi
import snastro.supporto.test.attendiFinche
import snastro.supporto.test.restaVeroPer
import snastro.ui.AggiornamentiVistaFinta
import snastro.ui.Cambiamento
import snastro.ui.coda.PosizioniCoda
import snastro.ui.modelli.ServizioModelli
import snastro.ui.modelli.ServizioModelliFinta
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

private val REG_1 = RegistrazioneId("id-1")
private val ISTANTE_0: Instant = Instant.parse("2026-09-26T10:00:00Z")

/** A [Clock] whose [instant] a test moves forward explicitly — the running status line ticks off it. */
private class OrologioFinto(var istante: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
    override fun instant(): Instant = istante
}

/**
 * Pre-release finding #159 rework 2 (regression): a [Clock] whose [instant] can be ARMED to pause,
 * with real [CountDownLatch]es, the FIRST caller that reaches it after arming — right where the
 * ticker's own tick reads the clock to build its write, AFTER `delay` resumed but BEFORE the write
 * itself lands. Real thread identity is the actual subject here (same rationale as
 * `StatoVociRicaricaTest`'s own L665a): this is the only way to force the EXACT interleaving the
 * regression describes, deterministically rather than by sleep-timing luck.
 */
private class OrologioConCancello(@Volatile var istante: Instant) : Clock() {
    @Volatile private var armato = false
    private val raggiunto = CountDownLatch(1)
    private val rilascio = CountDownLatch(1)
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
    override fun instant(): Instant {
        if (armato) {
            raggiunto.countDown()
            check(rilascio.await(5, TimeUnit.SECONDS)) { "timeout in attesa del rilascio del cancello" }
        }
        return istante
    }

    /** Test-only: the NEXT [instant] call blocks until [rilascia]. */
    fun arma() {
        armato = true
    }

    /** Blocks until a caller has reached the (armed) gate. */
    fun attendiRaggiunto() = check(raggiunto.await(5, TimeUnit.SECONDS)) { "il cancello non e' stato raggiunto" }

    fun rilascia() = rilascio.countDown()
}

/**
 * [FallimentoVista.motivo] is `:sintesi:dominio MotivoFallimento` — CR-1(b) forbids a source-level
 * `:ui` import of it (a plain enum, not `ErroreSintesi`); built here ONLY by reflection, the same
 * technique `MessaggiErroreTest.transizioneNonAmmessa` uses for `ErroreTrascrizione.TransizioneNonAmmessa`.
 */
private fun unFallimentoVista(codice: String = "INTERROTTO"): FallimentoVista {
    val classe = Class.forName("snastro.sintesi.dominio.MotivoFallimento")
    val valore = classe.enumConstants.first { (it as Enum<*>).name == codice }
    val costruttore = FallimentoVista::class.java.getDeclaredConstructor(classe)
    costruttore.isAccessible = true
    return costruttore.newInstance(valore) as FallimentoVista
}

@Suppress("LongParameterList")
private fun unaVista(
    modello: StatoModelloVista = StatoModelloVista.Installato,
    richiestaAperta: RichiestaApertaVista? = null,
    ultimoFallimento: FallimentoVista? = null,
    disponibilita: DisponibilitaVista = DisponibilitaVista.Disponibile,
    argomentoPrecompilato: String? = null,
    mostrato: RiassuntoMostrato? = null,
) = RiassuntoVista(
    unIncontroDi(REG_1),
    modello,
    richiestaAperta,
    ultimoFallimento,
    disponibilita,
    argomentoPrecompilato,
    mostrato,
)

private fun unaVoce(numero: Int, nome: String? = "Marco") = VoceVista(numero, "Voce $numero", nome)

private fun unTesto(testo: String) = listOf(ParteTestoVista.Testo(testo))

private fun unElemento(testo: String, fonti: List<FonteVista> = emptyList()) = ElementoVista(unTesto(testo), fonti)

@Suppress("LongParameterList")
private fun unMostrato(
    superato: Boolean = false,
    omessi: Int = 0,
    argomento: String? = null,
    lunghezzaMassimaParole: Int = 2_000,
    sommario: List<ParteTestoVista>? = unTesto("Un sommario."),
    decisioni: List<ElementoVista> = listOf(unElemento("Prima decisione")),
    azioni: List<AzioneVista> = emptyList(),
    questioniAperte: List<ElementoVista> = emptyList(),
    puntiChiave: List<snastro.sintesi.applicazione.letture.PuntoChiaveVista> = emptyList(),
) = RiassuntoMostrato(
    argomento = argomento,
    lunghezzaMassimaParole = lunghezzaMassimaParole,
    superato = superato,
    omessi = omessi,
    sommario = sommario,
    decisioni = decisioni,
    azioni = azioni,
    questioniAperte = questioniAperte,
    puntiChiave = puntiChiave,
)

private fun unaImpostazioni(parole: Int = 2_000, minimo: Int = 300, massimo: Int = 2_500) =
    ImpostazioniSintesiVista(parole, minimo, massimo)

/** ADR 0026 §8, mirrors [RiassuntoPresenter]'s own private constant — the ONE optional catalogue entry. */
private const val ID_MODELLO_LINGUISTICO = "llm-qwen3.5-9b-q4_k_m"

/**
 * Records [scaricaFacoltativo] calls (AC-S125/S127: "called once with the exact id") without touching
 * `ServizioModelliFinta` (owned by block `servizio-modelli-facoltativo`, already integrated) — an
 * interface-delegating spy, same technique as a decorator, confined to this block's own test file.
 */
private class ServizioModelliSpia(private val delegato: ServizioModelliFinta = ServizioModelliFinta()) :
    ServizioModelli by delegato {
    val chiamateScaricaFacoltativo = mutableListOf<String>()

    override fun scaricaFacoltativo(id: String) {
        chiamateScaricaFacoltativo.add(id)
        delegato.scaricaFacoltativo(id)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class RiassuntoPresenterTest {
    private class Ambiente(scope: TestScope, limiteCaratteriArgomento: Int = 200) {
        val dispatcher = StandardTestDispatcher(scope.testScheduler)
        val coroutineScope = CoroutineScope(dispatcher)

        // Pre-release finding #151 (rework, MED): a SEPARATE dispatcher instance (same virtual
        // scheduler, so time control is unaffected) — `withContext` between the SAME dispatcher
        // instance takes a fast, non-suspending path with no cancellation window at all, which would
        // make the AC-S151 test below pass whether or not `ricarica()` is really single-flight.
        val io = StandardTestDispatcher(scope.testScheduler)
        var vistaCorrente: RiassuntoVista? = unaVista()
        var impostazioniCorrenti = unaImpostazioni()
        var posizioniCorrenti = PosizioniCoda.VUOTA
        val chiamateRiassumi = mutableListOf<String?>()
        var risultatoRiassumi: Esito<Unit> = Esito.Ok(Unit)

        // Pre-release finding #159 (rework, LOW): simulates `riassumiCmd` throwing (a real port fault),
        // never a legitimate `Esito.Errore`.
        var lanciaRiassumiCmd = false
        val chiamateLunghezza = mutableListOf<Int>()
        var risultatoLunghezza: Esito<Unit> = Esito.Ok(Unit)
        val servizioModelli = ServizioModelliSpia()
        val aggiornamenti = AggiornamentiVistaFinta()
        val orologio = OrologioFinto(ISTANTE_0)

        // Pre-release finding #151 (rework, MED): counts every completed `ricarica()` — a
        // single-flight reload only ever lets the LATEST trigger reach this last read.
        var chiamatePosizioni = 0

        val presenter = RiassuntoPresenter(
            scope = coroutineScope,
            io = io,
            registrazioneId = REG_1,
            vista = { vistaCorrente },
            impostazioni = { impostazioniCorrenti },
            posizioni = {
                chiamatePosizioni++
                posizioniCorrenti
            },
            riassumiCmd = { argomento ->
                chiamateRiassumi.add(argomento)
                if (lanciaRiassumiCmd) error("guasto simulato di riassumiCmd")
                risultatoRiassumi
            },
            modificaLunghezzaMassimaCmd = { n ->
                chiamateLunghezza.add(n)
                risultatoLunghezza
            },
            servizioModelli = servizioModelli,
            aggiornamenti = aggiornamenti,
            clock = orologio,
            idModelloLinguistico = ID_MODELLO_LINGUISTICO,
            dimensioneModelloLinguisticoByte = 6_169_341_984,
            limiteCaratteriArgomento = limiteCaratteriArgomento,
        )
    }

    /**
     * [RiassuntoPresenter]'s own ticker (`while (true) { delay(1_000) }`, AC-S131) never becomes idle
     * by design: `runTest` would otherwise hang forever on its own final drain once the test body
     * returns (the pre-release `avvio-coda-condivisa` finding #119's own lesson, one level up — here
     * fixed by cancelling [Ambiente.coroutineScope] in a `finally`, unconditionally, rather than
     * relying on `backgroundScope`'s own, less predictable interaction with [runCurrent]/[advanceTimeBy]).
     */
    private fun eseguiTest(
        limiteCaratteriArgomento: Int = 200,
        corpo: suspend TestScope.(Ambiente) -> Unit,
    ) = runTest {
        val a = Ambiente(this, limiteCaratteriArgomento)
        try {
            corpo(a)
        } finally {
            a.coroutineScope.cancel()
        }
    }

    private fun dati(presenter: RiassuntoPresenter) = assertIs<RiassuntoUiStato.Dati>(presenter.stato.value)

    @Test
    fun `AC-S136 prima del caricamento lo stato e Caricamento`() = eseguiTest { a ->
        assertEquals(RiassuntoUiStato.Caricamento, a.presenter.stato.value)
    }

    @Test
    fun `AC-S128 installato senza Riassunto e Azionabile con Nessun riassunto ancora`() = eseguiTest { a ->
        runCurrent()
        val dati = dati(a.presenter)
        assertEquals(AreaAzione.Azionabile(nuovo = false), dati.areaAzione)
        assertNull(dati.contenuto)
    }

    @Test
    fun `AC-S128 Riassumi invia il comando una sola volta anche a doppio click`() = eseguiTest { a ->
        runCurrent()
        a.presenter.azioni.riassumi()
        a.presenter.azioni.riassumi()
        runCurrent()
        assertEquals(1, a.chiamateRiassumi.size)
    }

    // Pre-release finding #159 (rework, LOW): a THROWN `riassumiCmd` (never a legitimate
    // `Esito.Errore`) used to leave the in-flight guard stuck `true` forever — a real fault would
    // then have permanently disabled Riassumi for the rest of this presenter's life.
    @Test
    fun `AC-S128 rework riassumiCmd che lancia non blocca Riassumi per sempre`() = eseguiTest { a ->
        runCurrent()
        a.lanciaRiassumiCmd = true
        a.presenter.azioni.riassumi()
        runCurrent()
        assertEquals(1, a.chiamateRiassumi.size)

        a.lanciaRiassumiCmd = false
        a.presenter.azioni.riassumi()
        runCurrent()
        assertEquals(2, a.chiamateRiassumi.size, "invioInCorso e' rimasto vero dopo il guasto")
    }

    // Pre-release finding #151 (rework, MED): three independent triggers (init, cambiamenti,
    // statoFacoltativi) each `launch`ed their OWN `ricarica()` — none cancelling another. Firing TWO
    // of them back-to-back (before draining the scheduler) used to let BOTH complete their own full
    // read chain; single-flight (`collectLatest` over one merged flow) cancels the older one, so at
    // most ONE reload ever reaches its last read (`posizioni()`) per settle.
    @Test
    fun `AC-S151 rework due trigger ravvicinati non completano due ricariche in parallelo`() = eseguiTest { a ->
        runCurrent()
        val basale = a.chiamatePosizioni
        a.aggiornamenti.emetti(Cambiamento(REG_1))
        a.servizioModelli.scaricaFacoltativo(ID_MODELLO_LINGUISTICO)
        runCurrent()
        assertEquals(basale + 1, a.chiamatePosizioni, "due ricariche indipendenti sono arrivate in fondo insieme")
    }

    @Test
    fun `AC-S125 modello non installato mostra la dimensione e Scarica il modello`() = eseguiTest { a ->
        a.vistaCorrente = unaVista(modello = StatoModelloVista.NonInstallato(6_169_341_984))
        runCurrent()
        val area = assertIs<AreaAzione.ScaricaModello>(dati(a.presenter).areaAzione)
        assertEquals(
            "Per riassumere serve il modello di linguaggio (6,2 GB), da scaricare una volta sola.",
            area.messaggio,
        )
        assertEquals("Scarica il modello (6,2 GB)", area.etichettaBottone)
    }

    @Test
    fun `AC-S125 Scarica il modello chiama scaricaFacoltativo una sola volta con l id esatto`() = eseguiTest { a ->
        a.vistaCorrente = unaVista(modello = StatoModelloVista.NonInstallato(6_169_341_984))
        runCurrent()
        a.presenter.azioni.scaricaModello()
        runCurrent()
        assertEquals(listOf(ID_MODELLO_LINGUISTICO), a.servizioModelli.chiamateScaricaFacoltativo)
        assertTrue(a.servizioModelli.statoFacoltativi.value.isNotEmpty())
    }

    // Pre-release finding #157 (rework, MED): no in-flight guard on `scaricaModello` — a double click
    // started two 6 GB downloads. Same pattern as the Riassumi double-click test above.
    @Test
    fun `AC-S125 rework un doppio clic su Scarica il modello avvia un solo download`() = eseguiTest { a ->
        a.vistaCorrente = unaVista(modello = StatoModelloVista.NonInstallato(6_169_341_984))
        runCurrent()
        a.presenter.azioni.scaricaModello()
        a.presenter.azioni.scaricaModello()
        runCurrent()
        assertEquals(1, a.servizioModelli.chiamateScaricaFacoltativo.size)
    }

    @Test
    fun `AC-S126 download in corso mostra i byte scaricati e totali`() = eseguiTest { a ->
        a.vistaCorrente = unaVista(modello = StatoModelloVista.InDownload(2_100_000_000, 6_169_341_984))
        runCurrent()
        val area = assertIs<AreaAzione.Scaricando>(dati(a.presenter).areaAzione)
        assertEquals("Scarico il modello… 2,1 di 6,2 GB", area.testo)
    }

    @Test
    fun `AC-S127 download fallito mostra il motivo e Riprova richiama scaricaFacoltativo`() = eseguiTest { a ->
        a.vistaCorrente = unaVista(modello = StatoModelloVista.DownloadFallito(MotivoDownload.ConnessioneInterrotta))
        runCurrent()
        val area = assertIs<AreaAzione.DownloadFallito>(dati(a.presenter).areaAzione)
        assertEquals("La connessione si è interrotta.", area.messaggio)

        a.presenter.azioni.scaricaModello()
        runCurrent()
        assertEquals(listOf(ID_MODELLO_LINGUISTICO), a.servizioModelli.chiamateScaricaFacoltativo)
    }

    @Test
    fun `AC-S129 non disponibile mostra il motivo e il Riassunto mostrato resta visibile`() = eseguiTest { a ->
        a.vistaCorrente = unaVista(
            disponibilita = DisponibilitaVista.NonDisponibile(MotivoNonDisponibile.ElaborazioneAperta),
            mostrato = unMostrato(),
        )
        runCurrent()
        val dati = dati(a.presenter)
        assertEquals(AreaAzione.NonDisponibile("Aspetta la fine della trascrizione."), dati.areaAzione)
        assertTrue(dati.contenuto != null)
    }

    @Test
    fun `AC-S130 in_attesa mostra la posizione dalla coda o In coda se assente`() = eseguiTest { a ->
        a.vistaCorrente = unaVista(richiestaAperta = RichiestaApertaVista.InAttesa(ISTANTE_0))
        a.posizioniCorrenti = PosizioniCoda(elaborazioni = emptyMap(), riassunti = mapOf(REG_1 to 2))
        runCurrent()
        assertEquals(AreaAzione.InCoda(2), dati(a.presenter).areaAzione)

        a.posizioniCorrenti = PosizioniCoda.VUOTA
        a.aggiornamenti.emetti(Cambiamento(REG_1))
        runCurrent()
        assertEquals(AreaAzione.InCoda(null), dati(a.presenter).areaAzione)
    }

    @Test
    fun `AC-S131 in_corso mostra il tempo trascorso e avanza ogni secondo`() = eseguiTest { a ->
        a.vistaCorrente = unaVista(richiestaAperta = RichiestaApertaVista.InCorso(ISTANTE_0))
        a.orologio.istante = ISTANTE_0.plusSeconds(72)
        runCurrent()
        assertEquals(AreaAzione.InCorso(72_000), dati(a.presenter).areaAzione)

        a.orologio.istante = ISTANTE_0.plusSeconds(73)
        advanceTimeBy(1_100)
        runCurrent()
        assertEquals(AreaAzione.InCorso(73_000), dati(a.presenter).areaAzione)
    }

    @Test
    fun `AC-S132 pronto mostra le sezioni gli omessi solo se maggiori di zero e i metadati`() = eseguiTest { a ->
        val f1 = FonteVista(2, unaVoce(1), 5_000)
        val f2 = FonteVista(1, unaVoce(2), 1_000)
        a.vistaCorrente = unaVista(
            mostrato = unMostrato(
                omessi = 3,
                argomento = "Via Roquel",
                decisioni = listOf(unElemento("Si va avanti", listOf(f1, f2))),
            ),
        )
        runCurrent()
        val contenuto = requireNotNull(dati(a.presenter).contenuto)
        assertEquals(listOf(1_000L, 5_000L), contenuto.decisioni.single().fonti.map { it.inizioMs })
        assertEquals("3 elementi omessi perché non trovavo le frasi citate.", contenuto.omessiTesto)
        assertEquals("Argomento: Via Roquel · Lunghezza massima: 2000 parole", contenuto.metadatiTesto)
        assertEquals(AreaAzione.Azionabile(nuovo = true), dati(a.presenter).areaAzione)
    }

    @Test
    fun `AC-S132 nessun elemento omesso non mostra il testo degli omessi`() = eseguiTest { a ->
        a.vistaCorrente = unaVista(mostrato = unMostrato(omessi = 0))
        runCurrent()
        assertNull(requireNotNull(dati(a.presenter).contenuto).omessiTesto)
    }

    @Test
    fun `AC-S133 superato resta leggibile e offre comunque Riassumi di nuovo`() = eseguiTest { a ->
        a.vistaCorrente = unaVista(mostrato = unMostrato(superato = true))
        runCurrent()
        val dati = dati(a.presenter)
        assertTrue(requireNotNull(dati.contenuto).superato)
        assertEquals(AreaAzione.Azionabile(nuovo = true), dati.areaAzione)
    }

    @Test
    fun `Riassumi di nuovo apre il modulo, Annulla lo chiude e un invio riuscito lo richiude`() = eseguiTest { a ->
        a.vistaCorrente = unaVista(mostrato = unMostrato())
        runCurrent()
        assertEquals(false, dati(a.presenter).moduloAperto)

        a.presenter.azioni.apriModulo()
        assertEquals(true, dati(a.presenter).moduloAperto)
        a.presenter.azioni.chiudiModulo()
        assertEquals(false, dati(a.presenter).moduloAperto)

        a.presenter.azioni.apriModulo()
        a.presenter.azioni.riassumi()
        runCurrent()
        assertEquals(1, a.chiamateRiassumi.size)
        assertEquals(false, dati(a.presenter).moduloAperto)
    }

    @Test
    fun `AC-S134 fallito mappa il motivo precompila l Argomento e Riprova invia Riassumi`() = eseguiTest { a ->
        a.vistaCorrente = unaVista(
            ultimoFallimento = unFallimentoVista("INTERROTTO"),
            argomentoPrecompilato = "Via Roquel",
        )
        runCurrent()
        val dati = dati(a.presenter)
        assertEquals(AreaAzione.Fallito("Il riassunto non è riuscito: interrotto."), dati.areaAzione)
        assertEquals("Via Roquel", dati.argomento.valore)

        a.presenter.azioni.riassumi()
        runCurrent()
        assertEquals(listOf<String?>("Via Roquel"), a.chiamateRiassumi)
    }

    @Test
    fun `AC-S135 dopo un Ritrascrivi il Cambiamento ricarica dalla nuova generazione della vista`() = eseguiTest { a ->
        a.vistaCorrente = unaVista(
            disponibilita = DisponibilitaVista.NonDisponibile(MotivoNonDisponibile.ElaborazioneAperta),
            mostrato = unMostrato(),
        )
        runCurrent()
        assertTrue(dati(a.presenter).contenuto != null)

        // The re-run completes: the old Riassunto is gone, a fresh one is queued automatically.
        a.vistaCorrente = unaVista(richiestaAperta = RichiestaApertaVista.InAttesa(ISTANTE_0))
        a.aggiornamenti.emetti(Cambiamento(REG_1))
        runCurrent()
        val dati = dati(a.presenter)
        assertNull(dati.contenuto)
        assertIs<AreaAzione.InCoda>(dati.areaAzione)
    }

    @Test
    fun `AC-S135 dopo Ritrascrivi senza nuovo Riassunto per limite superato mostra stato 5`() = eseguiTest { a ->
        a.vistaCorrente = unaVista(
            disponibilita = DisponibilitaVista.NonDisponibile(MotivoNonDisponibile.ElaborazioneAperta),
            mostrato = unMostrato(),
        )
        runCurrent()
        assertTrue(dati(a.presenter).contenuto != null)

        // §6: the re-run's Trascritto fails Riassumibilita (over the limit) — no new Riassunto created.
        a.vistaCorrente = unaVista(disponibilita = DisponibilitaVista.NonDisponibile(MotivoNonDisponibile.TroppoLunga))
        a.aggiornamenti.emetti(Cambiamento(REG_1))
        runCurrent()
        val dati = dati(a.presenter)
        assertNull(dati.contenuto)
        assertEquals(
            AreaAzione.NonDisponibile("La registrazione è troppo lunga per il riassunto (oltre 1 h 10 circa)."),
            dati.areaAzione,
        )
    }

    @Test
    fun `AC-S135 dopo Ritrascrivi senza nuovo Riassunto e modello installato mostra stato 4`() = eseguiTest { a ->
        a.vistaCorrente = unaVista(
            disponibilita = DisponibilitaVista.NonDisponibile(MotivoNonDisponibile.ElaborazioneAperta),
            mostrato = unMostrato(),
        )
        runCurrent()
        assertTrue(dati(a.presenter).contenuto != null)

        // §6: nothing was previously removed's Argomento to carry, and the new Trascritto is available.
        a.vistaCorrente = unaVista()
        a.aggiornamenti.emetti(Cambiamento(REG_1))
        runCurrent()
        val dati = dati(a.presenter)
        assertNull(dati.contenuto)
        assertEquals(AreaAzione.Azionabile(nuovo = false), dati.areaAzione)
    }

    @Test
    fun `AC-S135 dopo Ritrascrivi senza nuovo Riassunto e modello mancante mostra stato 1`() = eseguiTest { a ->
        a.vistaCorrente = unaVista(
            disponibilita = DisponibilitaVista.NonDisponibile(MotivoNonDisponibile.ElaborazioneAperta),
            mostrato = unMostrato(),
        )
        runCurrent()
        assertTrue(dati(a.presenter).contenuto != null)

        // §6's Riassumibilita guard also needs the model Installato — removed after the re-run started.
        a.vistaCorrente = unaVista(modello = StatoModelloVista.NonInstallato(6_169_341_984))
        a.aggiornamenti.emetti(Cambiamento(REG_1))
        runCurrent()
        val dati = dati(a.presenter)
        assertNull(dati.contenuto)
        assertIs<AreaAzione.ScaricaModello>(dati.areaAzione)
    }

    @Test
    fun `AC-S135 un Cambiamento di un altra registrazione non ricarica`() = eseguiTest { a ->
        runCurrent()
        a.vistaCorrente = unaVista(mostrato = unMostrato())
        a.aggiornamenti.emetti(Cambiamento(RegistrazioneId("altra")))
        runCurrent()
        assertNull(dati(a.presenter).contenuto)
    }

    @Test
    fun `AC-S137 oltre 200 caratteri mostra l errore e non invia Riassumi`() = eseguiTest { a ->
        runCurrent()
        a.presenter.azioni.cambiaArgomento("x".repeat(201))
        val dati = dati(a.presenter)
        assertEquals("Al massimo 200 caratteri.", dati.argomento.errore)
        assertEquals("201/200", dati.argomento.contatore)

        a.presenter.azioni.riassumi()
        runCurrent()
        assertTrue(a.chiamateRiassumi.isEmpty())
    }

    // Pre-release finding #153 (rework, LOW): the counter/errore used to count the RAW (untrimmed)
    // length — 200 real characters plus surrounding whitespace showed as "over the limit" even though
    // `Argomento.di` trims first and would accept it.
    @Test
    fun `AC-S137 rework 200 caratteri con spazi intorno non supera il limite, come il dominio`() = eseguiTest { a ->
        runCurrent()
        a.presenter.azioni.cambiaArgomento("  ${"x".repeat(200)}  ")
        val dati = dati(a.presenter)
        assertEquals("200/200", dati.argomento.contatore)
        assertNull(dati.argomento.errore)
    }

    // Pre-release finding #148, rework 2 (verifier FAIL at c020b93a): every OTHER test injects the
    // SAME value (200) the domain actually uses, so a regression that silently ignores
    // `limiteCaratteriArgomento` and hardcodes 200 back stays green everywhere else. A NON-200
    // limit is the only way to prove the parameter is genuinely load-bearing — proven red on a
    // throwaway copy with the literal 200 restored in TestiRiassunto/RiassuntoPresenter.
    @Test
    fun `AC-S137 rework 2 un limite iniettato diverso da 200 guida contatore errore e blocco`() =
        eseguiTest(limiteCaratteriArgomento = 10) { a ->
            runCurrent()
            a.presenter.azioni.cambiaArgomento("  ${"x".repeat(11)}  ")
            val dati = dati(a.presenter)
            assertEquals("11/10", dati.argomento.contatore)
            assertEquals("Al massimo 10 caratteri.", dati.argomento.errore)

            a.presenter.azioni.riassumi()
            runCurrent()
            assertTrue(a.chiamateRiassumi.isEmpty(), "Riassumi non doveva essere inviato oltre il limite iniettato")
        }

    @Test
    fun `AC-S138 Cambia poi un valore fuori intervallo mostra l errore e non invia il comando`() = eseguiTest { a ->
        runCurrent()
        a.presenter.azioni.modificaLunghezzaMassima()
        a.presenter.azioni.cambiaLunghezzaMassima("299")
        a.presenter.azioni.salvaLunghezzaMassima()
        runCurrent()
        val modifica = assertIs<LunghezzaMassimaUiStato.Modifica>(dati(a.presenter).lunghezzaMassima)
        assertEquals("Scegli fra 300 e 2500 parole.", modifica.errore)
        assertTrue(a.chiamateLunghezza.isEmpty())
    }

    // Pre-release findings #153/#159 (rework, LOW): a COMMAND failure (in range, but rejected by
    // `modificaLunghezzaMassimaCmd`, e.g. a race) used to ALWAYS show the generic range text, hiding
    // the actual `ErroreSintesi` it carried.
    @Test
    fun `AC-S138 rework un comando fallito in range mostra il suo vero messaggio, non il range`() = eseguiTest { a ->
        runCurrent()
        a.risultatoLunghezza = Esito.Errore(ErroreSintesi.RiassuntoGiaAperto(unIncontroDi(REG_1)))
        a.presenter.azioni.modificaLunghezzaMassima()
        a.presenter.azioni.cambiaLunghezzaMassima("1500")
        a.presenter.azioni.salvaLunghezzaMassima()
        runCurrent()
        val modifica = assertIs<LunghezzaMassimaUiStato.Modifica>(dati(a.presenter).lunghezzaMassima)
        assertEquals("C'è già un riassunto in coda o in corso per questa registrazione.", modifica.errore)
    }

    // Pre-release finding #153 (rework, LOW): no in-flight guard — a double click on Salva could send
    // `modificaLunghezzaMassimaCmd` twice.
    @Test
    fun `AC-S138 rework un doppio clic su Salva invia il comando una sola volta`() = eseguiTest { a ->
        runCurrent()
        a.presenter.azioni.modificaLunghezzaMassima()
        a.presenter.azioni.cambiaLunghezzaMassima("1500")
        a.presenter.azioni.salvaLunghezzaMassima()
        a.presenter.azioni.salvaLunghezzaMassima()
        runCurrent()
        assertEquals(listOf(1_500), a.chiamateLunghezza)
    }

    @Test
    fun `AC-S138 un valore valido invia il comando mostra Salvato e poi torna al testo`() = eseguiTest { a ->
        runCurrent()
        a.presenter.azioni.modificaLunghezzaMassima()
        a.presenter.azioni.cambiaLunghezzaMassima("1500")
        a.presenter.azioni.salvaLunghezzaMassima()
        runCurrent()
        assertEquals(listOf(1_500), a.chiamateLunghezza)
        val salvato = assertIs<LunghezzaMassimaUiStato.Salvato>(dati(a.presenter).lunghezzaMassima)
        assertEquals(1_500, salvato.parole)

        a.impostazioniCorrenti = unaImpostazioni(parole = 1_500)
        advanceTimeBy(2_100)
        runCurrent()
        assertEquals(LunghezzaMassimaUiStato.Testo(1_500), dati(a.presenter).lunghezzaMassima)
    }

    // Pre-release finding #153 (rework, LOW): the "Salvato" 2 s timer used to close a REOPENED editor
    // too — reopening "Cambia" during that window must survive the timer's own delayed reset.
    @Test
    fun `AC-S138 rework il timer di Salvato non chiude un editor riaperto nel frattempo`() = eseguiTest { a ->
        runCurrent()
        a.presenter.azioni.modificaLunghezzaMassima()
        a.presenter.azioni.cambiaLunghezzaMassima("1500")
        a.presenter.azioni.salvaLunghezzaMassima()
        runCurrent()
        assertIs<LunghezzaMassimaUiStato.Salvato>(dati(a.presenter).lunghezzaMassima)

        // The user reopens the editor before the 2 s "Salvato" window elapses.
        a.presenter.azioni.modificaLunghezzaMassima()
        advanceTimeBy(2_100)
        runCurrent()
        assertIs<LunghezzaMassimaUiStato.Modifica>(
            dati(a.presenter).lunghezzaMassima,
            "il timer di Salvato ha chiuso un editor riaperto nel frattempo",
        )
    }

    @Test
    fun `AC-S138 Annulla ripristina l ultimo valore letto senza inviare comandi`() = eseguiTest { a ->
        runCurrent()
        a.presenter.azioni.modificaLunghezzaMassima()
        a.presenter.azioni.cambiaLunghezzaMassima("999")
        a.presenter.azioni.annullaLunghezzaMassima()
        runCurrent()
        assertEquals(LunghezzaMassimaUiStato.Testo(2_000), dati(a.presenter).lunghezzaMassima)
        assertTrue(a.chiamateLunghezza.isEmpty())
    }

    @Test
    fun `AC-S139 un ErroreSintesi mostra il messaggio e ricarica la vista`() = eseguiTest { a ->
        runCurrent()
        a.risultatoRiassumi = Esito.Errore(ErroreSintesi.RiassuntoGiaAperto(unIncontroDi(REG_1)))
        a.presenter.azioni.riassumi()
        runCurrent()
        assertEquals(
            "C'è già un riassunto in coda o in corso per questa registrazione.",
            dati(a.presenter).messaggioErrore,
        )
        // The guard clears once the command returns, whatever its outcome — a NEXT click is possible.
        a.risultatoRiassumi = Esito.Ok(Unit)
        a.presenter.azioni.riassumi()
        runCurrent()
        assertEquals(2, a.chiamateRiassumi.size)
    }

    // Pre-release finding #154 (rework, MED): messaggioErrore used to be cleared ONLY by a later
    // successful Riassumi — after a race it stayed shown through in_attesa→in_corso→pronto. It must
    // clear on the NEXT independent reload too (never just a successful Riassumi).
    @Test
    fun `AC-S139 rework un ErroreSintesi si azzera al ricarico successivo, non solo dopo un Riassumi riuscito`() =
        eseguiTest { a ->
            runCurrent()
            a.risultatoRiassumi = Esito.Errore(ErroreSintesi.RiassuntoGiaAperto(unIncontroDi(REG_1)))
            a.presenter.azioni.riassumi()
            runCurrent()
            assertEquals(
                "C'è già un riassunto in coda o in corso per questa registrazione.",
                dati(a.presenter).messaggioErrore,
            )
            // A completely independent reload (a Cambiamento the natural in_attesa→in_corso→pronto
            // progression would fire) — no successful Riassumi involved at all.
            a.aggiornamenti.emetti(Cambiamento(REG_1))
            runCurrent()
            assertNull(
                dati(a.presenter).messaggioErrore,
                "il messaggio d'errore di una race e' rimasto oltre il suo stato",
            )
        }

    @Test
    fun `AC-S139 il testo mappato usa erroreAtteso per confermare l istanza del kernel`() {
        val esito: Esito<Unit> = Esito.Errore(ErroreSintesi.RiassuntoGiaAperto(unIncontroDi(REG_1)))
        val errore = esito.erroreAtteso<ErroreSintesi.RiassuntoGiaAperto>()
        assertEquals(unIncontroDi(REG_1), errore.incontroId)
    }

    /**
     * Pre-release finding #159, rework 2 (verifier FAIL at c020b93a): the ticker's tick can resume
     * from `delay` and be part-way through building its own write (reading the clock) exactly when a
     * reload lands and moves `Dati` on to `pronto`/`fallito` — real threads + [OrologioConCancello]
     * force this EXACT interleaving deterministically (real thread identity IS the subject, same
     * rationale as `StatoVociRicaricaTest`'s own L665a), rather than guessing at sleep timings.
     */
    @Test
    fun `AC-S159 rework 2 un tick in volo non scrive InCorso su un Dati gia ricaricato al bordo`() {
        val eseguitori = Executors.newFixedThreadPool(4)
        val ioReale = eseguitori.asCoroutineDispatcher()
        val scopeReale = CoroutineScope(SupervisorJob() + ioReale)
        try {
            val orologio = OrologioConCancello(ISTANTE_0)
            var vistaCorrente: RiassuntoVista? = unaVista(richiestaAperta = RichiestaApertaVista.InCorso(ISTANTE_0))
            val aggiornamenti = AggiornamentiVistaFinta()
            val presenter = RiassuntoPresenter(
                scope = scopeReale,
                io = ioReale,
                registrazioneId = REG_1,
                vista = { vistaCorrente },
                impostazioni = { unaImpostazioni() },
                posizioni = { PosizioniCoda.VUOTA },
                riassumiCmd = { Esito.Ok(Unit) },
                modificaLunghezzaMassimaCmd = { Esito.Ok(Unit) },
                servizioModelli = ServizioModelliFinta(),
                aggiornamenti = aggiornamenti,
                clock = orologio,
                idModelloLinguistico = ID_MODELLO_LINGUISTICO,
                dimensioneModelloLinguisticoByte = 1,
                limiteCaratteriArgomento = 200,
            )
            attendiFinche(messaggio = "il primo caricamento non e' arrivato a in_corso") {
                (presenter.stato.value as? RiassuntoUiStato.Dati)?.areaAzione is AreaAzione.InCorso
            }
            // Arm the gate: the tick due in ~1s will resume from `delay`, then block right where it
            // reads the clock to build its own write — BEFORE that write lands.
            orologio.arma()
            orologio.attendiRaggiunto()
            // Land a full reload to `pronto` while the tick waits at the gate.
            vistaCorrente = unaVista(mostrato = unMostrato())
            aggiornamenti.emetti(Cambiamento(REG_1))
            attendiFinche(messaggio = "la ricarica non si e' conclusa pronto") {
                (presenter.stato.value as? RiassuntoUiStato.Dati)?.areaAzione is AreaAzione.Azionabile
            }
            // Release the stale tick — on the OLD (unguarded) code this now clobbers the fresh state.
            orologio.rilascia()
            restaVeroPer(300.milliseconds, messaggio = "un tick in volo ha sovrascritto il Dati gia ricaricato") {
                (presenter.stato.value as? RiassuntoUiStato.Dati)?.areaAzione is AreaAzione.Azionabile
            }
            val dati = presenter.stato.value as RiassuntoUiStato.Dati
            assertIs<AreaAzione.Azionabile>(
                dati.areaAzione,
                "un tick in volo ha scritto InCorso su un Dati gia ricaricato",
            )
        } finally {
            scopeReale.cancel()
            eseguitori.shutdownNow()
        }
    }
}
