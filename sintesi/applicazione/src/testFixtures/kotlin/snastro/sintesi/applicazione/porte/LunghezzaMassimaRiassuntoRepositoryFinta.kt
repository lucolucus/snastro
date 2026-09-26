package snastro.sintesi.applicazione.porte

import snastro.kernel.Esito
import snastro.kernel.ProgettoId
import snastro.kernel.Ripristinabile
import snastro.kernel.atteso
import snastro.sintesi.dominio.LunghezzaMassimaRiassunto

/**
 * In-memory [LunghezzaMassimaRiassuntoRepository], a stand-in for `impostazioni_sintesi`: stores the word count per
 * Progetto and rebuilds a fresh root on every read (from [LunghezzaMassimaRiassunto.predefinita] + `modifica`, never
 * `ricostituisci`, CR-15). No row → the default. [Ripristinabile]: pass it to `UnitaDiLavoroFinta`.
 */
public class LunghezzaMassimaRiassuntoRepositoryFinta : LunghezzaMassimaRiassuntoRepository, Ripristinabile {
    @Volatile
    private var parole: Map<ProgettoId, Int> = emptyMap()

    override fun trova(p: ProgettoId): LunghezzaMassimaRiassunto =
        LunghezzaMassimaRiassunto.predefinita(p).also { l -> parole[p]?.let { l.modifica(it).atteso() } }

    @Synchronized
    override fun salva(l: LunghezzaMassimaRiassunto): Esito<Unit> {
        parole = parole + (l.progettoId to l.parole.valore)
        return Esito.Ok(Unit)
    }

    override fun istantanea(): () -> Unit {
        val salvate = parole
        return { parole = salvate }
    }
}
