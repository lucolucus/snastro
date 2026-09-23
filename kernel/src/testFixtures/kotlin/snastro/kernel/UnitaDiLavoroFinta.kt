package snastro.kernel

/**
 * In-memory [UnitaDiLavoro]: the outermost transaction snapshots every [partecipanti] state and
 * restores it on rollback. A nested call joins the outer transaction; a nested [Esito.Errore] or
 * exception dooms it (the [UnitaDiLavoro] rule).
 */
public class UnitaDiLavoroFinta(private vararg val partecipanti: Ripristinabile) : UnitaDiLavoro {
    private var profondita = 0
    private var erroreAnnidato: Esito.Errore? = null
    private var eccezioneAnnidata = false

    override fun <T> inTransazione(blocco: () -> Esito<T>): Esito<T> {
        val esterna = profondita == 0
        val ripristini = if (esterna) partecipanti.map { it.istantanea() } else emptyList()
        var terminato = false
        var confermata = false
        profondita++
        try {
            val esito = blocco()
            terminato = true
            if (!esterna) {
                if (esito is Esito.Errore && erroreAnnidato == null) erroreAnnidato = esito
                return esito
            }
            check(!eccezioneAnnidata) { "una transazione annidata e fallita con un'eccezione: rollback" }
            val finale = erroreAnnidato ?: esito
            confermata = finale is Esito.Ok
            return finale
        } finally {
            profondita--
            if (!esterna && !terminato) eccezioneAnnidata = true
            if (esterna) {
                if (!confermata) ripristini.forEach { it() }
                erroreAnnidato = null
                eccezioneAnnidata = false
            }
        }
    }
}
