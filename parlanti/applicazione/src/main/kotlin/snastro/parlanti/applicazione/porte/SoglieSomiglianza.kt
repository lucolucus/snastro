package snastro.parlanti.applicazione.porte

/**
 * Thresholds of [ClassificatoreSomiglianza] (ADR 0019 §4.3), injected as configuration.
 * PROVISIONAL [hypothesis]: calibrated later (Via Roquel run with the user's references, recorded
 * by amendment). [minima] must lie in `-1.0..1.0`; [margine] must be strictly positive; a NaN in
 * either is rejected.
 */
public data class SoglieSomiglianza(val minima: Double, val margine: Double) {
    init {
        require(!minima.isNaN() && minima >= -1.0 && minima <= 1.0) {
            "SoglieSomiglianza: minima ($minima) deve stare in -1.0..1.0"
        }
        require(!margine.isNaN() && margine > 0) { "SoglieSomiglianza: margine ($margine) deve essere positivo" }
    }
}
