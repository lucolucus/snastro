package snastro.supporto.test

import kotlin.time.Duration

/**
 * The only sanctioned fixed pause of the tests (CR-19a): for when real time IS the subject — a fake standing in
 * for slow work, a sampler, letting a real thread reach a blocking call. [motivo] says why; never use it to wait
 * for a result ([attendiFinche]) or to check that nothing happens ([restaVeroPer]).
 */
public fun pausaInTempoReale(durata: Duration, motivo: String) {
    require(motivo.isNotBlank()) { "pausaInTempoReale: serve il motivo" }
    Thread.sleep(durata.inWholeMilliseconds)
}
