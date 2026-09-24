package snastro.trascrizione.applicazione.eventi

import snastro.kernel.DispatcherEventiInMemoria
import snastro.kernel.ErroreDiProva
import snastro.kernel.Esito
import snastro.kernel.EventoPubblicato
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso

/** A real [DispatcherEventiInMemoria] with one recording synchronous and one after-commit subscriber. */
internal class Consegna {
    private val dispatcher = DispatcherEventiInMemoria(UnitaDiLavoroFinta())
    private var inTransazione = false
    val sincroni = mutableListOf<EventoPubblicato>()
    val sincroniDentroLaTransazione = mutableListOf<Boolean>()
    val dopoCommit = mutableListOf<EventoPubblicato>()

    init {
        dispatcher.registraSincrono { e ->
            sincroni += e
            sincroniDentroLaTransazione += inTransazione
            Esito.Ok(Unit)
        }
        dispatcher.registraDopoCommit { e -> dopoCommit += e }
    }

    fun inTransazione(evento: EventoPubblicato, conferma: Boolean) {
        val esito = dispatcher.unitaDiLavoro.inTransazione {
            inTransazione = true
            dispatcher.pubblica(evento)
            inTransazione = false
            if (conferma) Esito.Ok(Unit) else Esito.Errore(ErroreDiProva.Fallito("rollback"))
        }
        if (conferma) esito.atteso() else esito.erroreAtteso<ErroreDiProva.Fallito>()
    }
}
