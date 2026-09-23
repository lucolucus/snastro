package snastro.kernel

/**
 * In-memory [UnitaDiLavoro]: the outermost transaction snapshots every [partecipanti] state and
 * restores it on rollback. A nested call joins the outer transaction; a nested [Esito.Errore] or
 * exception dooms it (the [UnitaDiLavoro] rule).
 *
 * [transazioneAperta] tells whether a block is running inside [inTransazione] right now (nested
 * included): the Parlanti ML fakes read it to refuse being called inside a transaction
 * (ADR 0012 Amendment (b), AC-266).
 */
public class UnitaDiLavoroFinta(private vararg val partecipanti: Ripristinabile) : UnitaDiLavoro {
    private var profondita = 0
    private var erroreAnnidato: Esito.Errore? = null
    private var eccezioneAnnidata: Throwable? = null

    /** True only while a block runs inside [inTransazione]; false again after commit and rollback. */
    public val transazioneAperta: Boolean get() = profondita > 0

    override fun <T> inTransazione(blocco: () -> Esito<T>): Esito<T> =
        if (profondita == 0) esterna(blocco) else annidata(blocco)

    private fun <T> annidata(blocco: () -> Esito<T>): Esito<T> {
        profondita++
        try {
            val esito = runCatching(blocco).onFailure(::condanna).getOrThrow()
            if (esito is Esito.Errore) condanna(esito)
            return esito
        } finally {
            profondita--
        }
    }

    private fun <T> esterna(blocco: () -> Esito<T>): Esito<T> {
        val ripristini = partecipanti.map { it.istantanea() }
        var confermata = false
        profondita++
        try {
            val finale = finale(blocco())
            confermata = finale is Esito.Ok
            return finale
        } finally {
            profondita--
            if (!confermata) ripristini.forEach { it() }
            erroreAnnidato = null
            eccezioneAnnidata = null
        }
    }

    /** The outer Errore wins; under an outer Ok the first nested failure decides. */
    private fun <T> finale(esito: Esito<T>): Esito<T> {
        if (esito is Esito.Errore) return esito
        eccezioneAnnidata?.let {
            throw IllegalStateException("una transazione annidata e fallita con un'eccezione: rollback", it)
        }
        return erroreAnnidato ?: esito
    }

    private fun condanna(errore: Esito.Errore) {
        if (erroreAnnidato == null && eccezioneAnnidata == null) erroreAnnidato = errore
    }

    private fun condanna(eccezione: Throwable) {
        if (erroreAnnidato == null && eccezioneAnnidata == null) eccezioneAnnidata = eccezione
    }
}
