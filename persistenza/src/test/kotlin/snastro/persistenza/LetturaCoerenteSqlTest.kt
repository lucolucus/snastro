package snastro.persistenza

import org.junit.jupiter.api.io.TempDir
import snastro.kernel.LetturaCoerenteContratto
import java.nio.file.Path

/** AC-C15: [UnitaDiLavoroSql], as both ports, passes [LetturaCoerenteContratto] on the production file driver. */
class LetturaCoerenteSqlTest : LetturaCoerenteContratto() {
    @TempDir
    lateinit var cartella: Path

    override fun ambiente(): Ambiente {
        val sql = DatabaseTracciato(cartella)
        return object : Ambiente {
            override val lettura = sql.uow

            override val unitaDiLavoro = sql.uow

            override fun scrivi(effetto: String) = sql.scrivi(effetto)

            override fun effetti() = sql.effetti()

            override fun leggi() = sql.uow.inLettura { sql.effetti().toSet() }
        }
    }
}
