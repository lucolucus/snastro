package snastro.ui.coda

import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId

/** The two kinds of item the shared queue orders (ADR 0023 §1). */
enum class TipoInCoda { ELABORAZIONE, RIASSUNTO }

/** One queued item of the scenario, in Published Language only. */
data class ElementoScenario(val tipo: TipoInCoda, val registrazioneId: RegistrazioneId)

/**
 * The state of the shared queue a [PosizioniNellaCodaContratto] case seeds: [inAttesa] listed in the
 * queue's global order (an implementation seeds them with strictly increasing instants), plus at most
 * one item [inCorso] (claimed, running, never counted).
 */
data class ScenarioCoda(
    val inAttesa: List<ElementoScenario>,
    val inCorso: ElementoScenario? = null,
) {
    /** The snapshot the owner must report for this scenario — what the fake is seeded with (D1). */
    fun posizioniAttese(): PosizioniCoda {
        val numerati = inAttesa.mapIndexed { i, e -> e to i + 1 }
        fun di(tipo: TipoInCoda) = numerati.filter { (e, _) -> e.tipo == tipo }
        // a Riassunto is keyed by its Incontro (AC-I51): the scenario names it by the same id text
        return PosizioniCoda(
            di(TipoInCoda.ELABORAZIONE).associate { (e, n) -> e.registrazioneId to n },
            di(TipoInCoda.RIASSUNTO).associate { (e, n) -> IncontroId(e.registrazioneId.valore) to n },
        )
    }
}

/** Fixture builders (dev-architecture `#test`). */
fun unaElaborazione(registrazioneId: String): ElementoScenario =
    ElementoScenario(TipoInCoda.ELABORAZIONE, RegistrazioneId(registrazioneId))

fun unRiassunto(registrazioneId: String): ElementoScenario =
    ElementoScenario(TipoInCoda.RIASSUNTO, RegistrazioneId(registrazioneId))
