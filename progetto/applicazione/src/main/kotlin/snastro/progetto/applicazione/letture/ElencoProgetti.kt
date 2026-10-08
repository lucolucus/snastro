package snastro.progetto.applicazione.letture

import snastro.progetto.applicazione.porte.CartelleProgetti
import snastro.progetto.applicazione.porte.RegistroProgetti

/**
 * The `schermata-progetti` (S1) data view (AC-159/160): a plain projection over [RegistroProgetti],
 * no domain rule. [RegistroProgetti.elenco] already guarantees most-recent-[ProgettoVista.ultimaAttivita]-first
 * order (AC-28, `tec-registro-progetti`), so this block does not re-sort — it only maps the registry's
 * own shape to the screen's own [ProgettoVista], each project's folder looked for in [cartelle] at every read (R0,
 * EX-7/EX-12: a folder that comes back is found again the next time the list is read).
 */
public class ElencoProgetti(private val registro: RegistroProgetti, private val cartelle: CartelleProgetti) {
    /** Every known project as a [ProgettoVista], most recent activity first; empty if none is known. */
    public fun progetti(): List<ProgettoVista> = registro.elenco().map {
        ProgettoVista(
            progettoId = it.progettoId,
            nome = it.nome,
            percorso = it.percorso,
            numRegistrazioni = it.numRegistrazioni,
            ultimaAttivita = it.ultimaAttivita,
            cartellaTrovata = cartelle.esiste(it.percorso),
        )
    }
}
