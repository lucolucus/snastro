package snastro.kernel

/**
 * In-memory [UnitaDiLavoro] and [LetturaCoerente] over ONE state (one depth counter, one mode — ADR 0029 §2).
 * The outermost transaction snapshots every [partecipanti] state and restores it on rollback. A nested
 * call joins the outer transaction; a nested [Esito.Errore] or exception dooms it (the [UnitaDiLavoro]
 * rule), a nested [inLettura] included. [inLettura] joins whatever is open; [inTransazione] inside a read
 * throws [IllegalStateException] before running its block.
 *
 * [transazioneAperta] tells whether a block is running inside [inTransazione] right now (nested
 * included): the Parlanti ML fakes read it to refuse being called inside a transaction
 * (ADR 0012 Amendment (b), AC-266). [letturaAperta] tells whether an outermost [inLettura] is open:
 * a fake repository's write refuses to run while it is true (the SQL `query_only` twin).
 */
public class UnitaDiLavoroFinta(private vararg val partecipanti: Ripristinabile) : UnitaDiLavoro, LetturaCoerente {
    private enum class Modo { NESSUNO, SCRITTURA, LETTURA }

    private var profondita = 0
    private var modo = Modo.NESSUNO
    private var erroreAnnidato: Esito.Errore? = null
    private var eccezioneAnnidata: Throwable? = null

    /** True only while a block runs inside [inTransazione]; false again after commit and rollback. */
    public val transazioneAperta: Boolean get() = modo == Modo.SCRITTURA

    /** True only inside an outermost [inLettura] (and whatever runs nested in it); false again after it ends. */
    public val letturaAperta: Boolean get() = modo == Modo.LETTURA

    override fun <T> inTransazione(blocco: () -> Esito<T>): Esito<T> {
        check(modo != Modo.LETTURA) { "inTransazione dentro inLettura: una lettura non diventa una scrittura" }
        return if (profondita == 0) esterna(blocco) else annidata(blocco)
    }

    override fun <T> inLettura(blocco: () -> T): T = when (modo) {
        Modo.NESSUNO -> letturaEsterna(blocco)
        Modo.LETTURA -> annidataInLettura(blocco)
        Modo.SCRITTURA -> annidataInTransazione(blocco)
    }

    private fun <T> letturaEsterna(blocco: () -> T): T {
        modo = Modo.LETTURA
        try {
            return annidataInLettura(blocco)
        } finally {
            modo = Modo.NESSUNO
        }
    }

    private fun <T> annidataInLettura(blocco: () -> T): T {
        profondita++
        try {
            return blocco()
        } finally {
            profondita--
        }
    }

    private fun <T> annidataInTransazione(blocco: () -> T): T {
        profondita++
        try {
            return runCatching(blocco).onFailure(::condanna).getOrThrow()
        } finally {
            profondita--
        }
    }

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
        modo = Modo.SCRITTURA
        try {
            val finale = finale(blocco())
            confermata = finale is Esito.Ok
            return finale
        } finally {
            profondita--
            modo = Modo.NESSUNO
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
