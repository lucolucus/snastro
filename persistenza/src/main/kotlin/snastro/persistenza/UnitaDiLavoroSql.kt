package snastro.persistenza

import snastro.kernel.Esito
import snastro.kernel.UnitaDiLavoro

/**
 * [UnitaDiLavoro] impl (ADR 0012): the OUTERMOST [inTransazione] call opens one real SQLDelight
 * transaction on [db]; a nested call never opens a savepoint — it just runs its block inside that
 * same transaction and records the first nested [Esito.Errore] or exception here. SQLDelight's own
 * nested `transaction {}` would roll back only its own scope and return normally, which is NOT the
 * [UnitaDiLavoro] contract: a nested failure must doom the whole outermost transaction. Passes the
 * kernel's `UnitaDiLavoroContratto` (`:kernel` testFixtures) — mirrors `UnitaDiLavoroFinta`'s logic.
 */
public class UnitaDiLavoroSql(private val db: SnastroDatabase) : UnitaDiLavoro {
    private var profondita = 0
    private var erroreAnnidato: Esito.Errore? = null
    private var eccezioneAnnidata: Throwable? = null

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
        profondita++
        try {
            return db.transactionWithResult<Esito<T>> {
                val esito = blocco()
                val finale: Esito<T> = when {
                    esito is Esito.Errore -> esito
                    eccezioneAnnidata != null ->
                        throw IllegalStateException(
                            "una transazione annidata e fallita con un'eccezione: rollback",
                            eccezioneAnnidata,
                        )

                    else -> erroreAnnidato ?: esito
                }
                if (finale is Esito.Errore) rollback(finale) else finale
            }
        } finally {
            profondita--
            erroreAnnidato = null
            eccezioneAnnidata = null
        }
    }

    private fun condanna(errore: Esito.Errore) {
        if (erroreAnnidato == null && eccezioneAnnidata == null) erroreAnnidato = errore
    }

    private fun condanna(eccezione: Throwable) {
        if (erroreAnnidato == null && eccezioneAnnidata == null) eccezioneAnnidata = eccezione
    }
}
