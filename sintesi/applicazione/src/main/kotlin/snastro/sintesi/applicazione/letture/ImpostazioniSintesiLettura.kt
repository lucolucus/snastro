package snastro.sintesi.applicazione.letture

import snastro.kernel.ProgettoId
import snastro.sintesi.applicazione.porte.LunghezzaMassimaRiassuntoRepository
import snastro.sintesi.dominio.LunghezzaMassimaParole

/**
 * Read-model `vista-impostazioni-sintesi` (AC-S109, ADR 0022, boundary `vista-impostazioni-sintesi`):
 * builds [ImpostazioniSintesiVista] for one Progetto with no side effect. [LunghezzaMassimaRiassuntoRepository.trova]
 * already reconstitutes the default (2000) when there is no stored row, so [di] never branches on that itself.
 */
public class ImpostazioniSintesiLettura(
    private val lunghezzeMassime: LunghezzaMassimaRiassuntoRepository,
) {
    public fun di(progettoId: ProgettoId): ImpostazioniSintesiVista = ImpostazioniSintesiVista(
        lunghezzaMassimaParole = lunghezzeMassime.trova(progettoId).parole.valore,
        minimo = LunghezzaMassimaParole.MINIMO,
        massimo = LunghezzaMassimaParole.MASSIMO,
    )
}
