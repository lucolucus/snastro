package snastro.parlanti.applicazione.porte

/**
 * Thresholds of the [Fascia] bands, injected as configuration (values from spike impronta-vocale-affidabilita).
 * [forte] must be strictly greater than [debole] (a NaN is rejected too).
 */
public data class SoglieFascia(val forte: Double, val debole: Double) {
    init {
        require(forte > debole) { "SoglieFascia: forte ($forte) deve superare debole ($debole)" }
    }
}
