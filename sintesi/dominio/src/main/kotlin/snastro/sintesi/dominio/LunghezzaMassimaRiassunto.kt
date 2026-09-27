package snastro.sintesi.dominio

import snastro.kernel.Esito
import snastro.kernel.ProgettoId
import snastro.kernel.RicostituzioneDaPersistenza
import snastro.kernel.mappa
import snastro.kernel.valoreOppureErrore

/**
 * The per-Progetto lunghezza massima del Riassunto (one per [progettoId]; no stored row ⇒ [predefinita]).
 * The range (INV-S9) is owned by [LunghezzaMassimaParole]: [modifica] delegates to its factory, never re-checks it.
 * Never deleted (ADR 0021 §9).
 */
public class LunghezzaMassimaRiassunto private constructor(
    public val progettoId: ProgettoId,
    parole: LunghezzaMassimaParole,
) {
    // Backing field, not `public var … private set`: CR-4's Konsist rule bans any public var in dominio.
    private var _parole = parole
    public val parole: LunghezzaMassimaParole get() = _parole

    public fun modifica(parole: Int): Esito<LunghezzaMassimaRiassuntoModificataDominio> =
        LunghezzaMassimaParole.di(parole).mappa {
            _parole = it
            LunghezzaMassimaRiassuntoModificataDominio(progettoId)
        }

    public companion object {
        /** The value read for a Progetto with no stored setting (e.g. created before Sintesi). */
        public fun predefinita(progettoId: ProgettoId): LunghezzaMassimaRiassunto {
            val parole = LunghezzaMassimaParole.di(LunghezzaMassimaParole.PREDEFINITA)
                .valoreOppureErrore { "PREDEFINITA fuori intervallo" }
            return LunghezzaMassimaRiassunto(progettoId, parole)
        }

        /** Rebuilds from persisted state (the DB is trusted). */
        @RicostituzioneDaPersistenza
        public fun ricostituisci(progettoId: ProgettoId, parole: LunghezzaMassimaParole): LunghezzaMassimaRiassunto =
            LunghezzaMassimaRiassunto(progettoId, parole)
    }
}
