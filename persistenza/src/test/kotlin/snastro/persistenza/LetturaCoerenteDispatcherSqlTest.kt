package snastro.persistenza

import org.junit.jupiter.api.io.TempDir
import snastro.kernel.DispatcherEventiInMemoria
import snastro.kernel.LetturaCoerenteContratto
import java.nio.file.Path

/**
 * AC-C15 (ADR 0029 §3): the SAME [UnitaDiLavoroSql] instance is the [snastro.kernel.LetturaCoerente] and the delegate
 * of [DispatcherEventiInMemoria]; the dispatcher's wrapper has no read side and the contract still holds across it.
 */
class LetturaCoerenteDispatcherSqlTest : LetturaCoerenteContratto() {
    @TempDir
    lateinit var cartella: Path

    override fun ambiente(): Ambiente {
        val sql = DatabaseTracciato(cartella)
        val dispatcher = DispatcherEventiInMemoria(sql.uow)
        return object : Ambiente {
            override val lettura = sql.uow

            override val unitaDiLavoro = dispatcher.unitaDiLavoro

            override fun scrivi(effetto: String) = sql.scrivi(effetto)

            override fun effetti() = sql.effetti()

            override fun leggi() = sql.uow.inLettura { sql.effetti().toSet() }
        }
    }
}
