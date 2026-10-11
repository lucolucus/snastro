package snastro.ui.riassunto

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.kernel.unIncontroDi
import snastro.sintesi.applicazione.letture.DisponibilitaVista
import snastro.sintesi.applicazione.letture.FallimentoVista
import snastro.sintesi.applicazione.letture.ImpostazioniSintesiVista
import snastro.sintesi.applicazione.letture.RiassuntoMostrato
import snastro.sintesi.applicazione.letture.RiassuntoVista
import snastro.sintesi.applicazione.letture.RichiestaApertaVista
import snastro.sintesi.applicazione.letture.StatoModelloVista
import snastro.ui.AggiornamentiVistaFinta
import snastro.ui.coda.PosizioniCoda
import snastro.ui.modelli.ServizioModelli
import snastro.ui.modelli.ServizioModelliFinta
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertIs

/*
 * The fixture of [RiassuntoPresenter]'s tests, shared by the classes of this package that drive it: one
 * [Ambiente] per test through [eseguiTest].
 */

internal val REG_1 = RegistrazioneId("id-1")
internal val REG_3 = RegistrazioneId("id-3")
internal val ISTANTE_0: Instant = Instant.parse("2026-09-26T10:00:00Z")

/** A [Clock] whose [instant] a test moves forward explicitly — the running status line ticks off it. */
internal class OrologioFinto(var istante: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
    override fun instant(): Instant = istante
}

@Suppress("LongParameterList")
internal fun unaVista(
    modello: StatoModelloVista = StatoModelloVista.Installato,
    richiestaAperta: RichiestaApertaVista? = null,
    ultimoFallimento: FallimentoVista? = null,
    disponibilita: DisponibilitaVista = DisponibilitaVista.Disponibile,
    argomentoPrecompilato: String? = null,
    mostrato: RiassuntoMostrato? = null,
    numParti: Int = 1,
) = RiassuntoVista(
    unIncontroDi(REG_1),
    numParti,
    modello,
    richiestaAperta,
    ultimoFallimento,
    disponibilita,
    argomentoPrecompilato,
    mostrato,
)

internal fun unaImpostazioni(parole: Int = 2_000, minimo: Int = 300, massimo: Int = 2_500) =
    ImpostazioniSintesiVista(parole, minimo, massimo)

/** ADR 0026 §8, mirrors [RiassuntoPresenter]'s own private constant — the ONE optional catalogue entry. */
internal const val ID_MODELLO_LINGUISTICO = "llm-qwen3.5-9b-q4_k_m"

/**
 * Records [scaricaFacoltativo] calls (AC-S125/S127: "called once with the exact id") without touching
 * `ServizioModelliFinta` (owned by block `servizio-modelli-facoltativo`, already integrated) — an
 * interface-delegating spy, same technique as a decorator, confined to this package's tests.
 */
internal class ServizioModelliSpia(private val delegato: ServizioModelliFinta = ServizioModelliFinta()) :
    ServizioModelli by delegato {
    val chiamateScaricaFacoltativo = mutableListOf<String>()

    /** When set, starting the download throws it (a port fault), after the call is recorded. */
    var guasto: Exception? = null

    override fun scaricaFacoltativo(id: String) {
        chiamateScaricaFacoltativo.add(id)
        guasto?.let { throw it }
        delegato.scaricaFacoltativo(id)
    }
}

internal class Ambiente(scope: TestScope, limiteCaratteriArgomento: Int = 200) {
    val dispatcher = StandardTestDispatcher(scope.testScheduler)

    /** What escaped the presenter's coroutines (D-0065: an `Error` after a commit must get here). */
    val sfuggite = mutableListOf<Throwable>()
    val coroutineScope = CoroutineScope(dispatcher + CoroutineExceptionHandler { _, e -> sfuggite += e })

    /** `true` iff [guasto], or a failure it caused, escaped the presenter's coroutines. */
    fun sfuggito(guasto: Throwable): Boolean =
        sfuggite.any { e -> generateSequence(e) { it.cause }.any { it === guasto } }

    // Pre-release finding #151 (rework, MED): a SEPARATE dispatcher instance (same virtual
    // scheduler, so time control is unaffected) — `withContext` between the SAME dispatcher
    // instance takes a fast, non-suspending path with no cancellation window at all, which would
    // make the AC-S151 test below pass whether or not `ricarica()` is really single-flight.
    val io = StandardTestDispatcher(scope.testScheduler)
    var vistaCorrente: RiassuntoVista? = unaVista()

    /** When set, reading the vista throws it (a port fault). */
    var guastoVista: Throwable? = null
    var impostazioniCorrenti = unaImpostazioni()
    var posizioniCorrenti = PosizioniCoda.VUOTA
    val chiamateRiassumi = mutableListOf<String?>()
    var risultatoRiassumi: Esito<Unit> = Esito.Ok(Unit)

    // Pre-release finding #159 (rework, LOW): simulates `riassumiCmd` throwing (a real port fault),
    // never a legitimate `Esito.Errore`.
    var lanciaRiassumiCmd = false
    val guastoRiassumiCmd = IllegalStateException("guasto simulato di riassumiCmd")
    var lanciaRiproduciDa = false
    val guastoRiproduciDa = IllegalStateException("guasto simulato di riproduciDa")
    val chiamateLunghezza = mutableListOf<Int>()
    var risultatoLunghezza: Esito<Unit> = Esito.Ok(Unit)

    /** When set, `modificaLunghezzaMassimaCmd` throws it after the call is recorded. */
    var guastoLunghezza: Throwable? = null
    val servizioModelli = ServizioModelliSpia()
    val aggiornamenti = AggiornamentiVistaFinta()
    val orologio = OrologioFinto(ISTANTE_0)

    // Pre-release finding #151 (rework, MED): counts every completed `ricarica()` — a
    // single-flight reload only ever lets the LATEST trigger reach this last read.
    var chiamatePosizioni = 0

    val eventi = mutableListOf<String>()

    val presenter = RiassuntoPresenter(
        scope = coroutineScope,
        io = io,
        registrazioneId = REG_1,
        vista = {
            guastoVista?.let { throw it }
            vistaCorrente
        },
        impostazioni = { impostazioniCorrenti },
        posizioni = {
            chiamatePosizioni++
            posizioniCorrenti
        },
        riassumiCmd = { argomento ->
            chiamateRiassumi.add(argomento)
            if (lanciaRiassumiCmd) throw guastoRiassumiCmd
            risultatoRiassumi
        },
        modificaLunghezzaMassimaCmd = { n ->
            chiamateLunghezza.add(n)
            guastoLunghezza?.let { throw it }
            risultatoLunghezza
        },
        servizioModelli = servizioModelli,
        aggiornamenti = aggiornamenti,
        clock = orologio,
        idModelloLinguistico = ID_MODELLO_LINGUISTICO,
        dimensioneModelloLinguisticoByte = 6_169_341_984,
        limiteCaratteriArgomento = limiteCaratteriArgomento,
        vaiAllaParte = { parte -> eventi.add("vai:${parte.valore}") },
        riproduciDa = { parte, ms ->
            eventi.add("play:${parte.valore}@$ms")
            if (lanciaRiproduciDa) throw guastoRiproduciDa
        },
    )
}

/**
 * [RiassuntoPresenter]'s own ticker (`while (true) { delay(1_000) }`, AC-S131) never becomes idle
 * by design: `runTest` would otherwise hang forever on its own final drain once the test body
 * returns (the pre-release `avvio-coda-condivisa` finding #119's own lesson, one level up — here
 * fixed by cancelling [Ambiente.coroutineScope] in a `finally`, unconditionally, rather than
 * relying on `backgroundScope`'s own, less predictable interaction with [runCurrent]/[advanceTimeBy]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun eseguiTest(
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

internal fun dati(presenter: RiassuntoPresenter) = assertIs<RiassuntoUiStato.Dati>(presenter.stato.value)
