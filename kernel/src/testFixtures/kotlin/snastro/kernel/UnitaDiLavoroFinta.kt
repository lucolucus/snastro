package snastro.kernel

/**
 * In-memory [UnitaDiLavoro]: the outermost transaction snapshots every [partecipanti] state and
 * restores it when the block returns [Esito.Errore] or throws; a nested call joins the outer one.
 */
public class UnitaDiLavoroFinta(private vararg val partecipanti: Ripristinabile) : UnitaDiLavoro {
    private var profondita = 0

    override fun <T> inTransazione(blocco: () -> Esito<T>): Esito<T> {
        val ripristini = if (profondita == 0) partecipanti.map { it.istantanea() } else emptyList()
        var confermata = false
        profondita++
        try {
            val esito = blocco()
            confermata = esito is Esito.Ok
            return esito
        } finally {
            profondita--
            if (!confermata) ripristini.forEach { it() }
        }
    }
}
