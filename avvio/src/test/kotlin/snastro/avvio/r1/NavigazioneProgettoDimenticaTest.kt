package snastro.avvio.r1

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.jupiter.api.AfterEach
import snastro.avvio.r2.SEZIONI_SHELL_R2
import snastro.kernel.RegistrazioneId
import snastro.ui.SessioneProgettoFinta
import snastro.ui.ShellPresenter
import kotlin.test.Test
import kotlin.test.assertEquals

/** AC-632 step (4), ADR 0020 §5: [NavigazioneProgetto.dimentica] on the S3 place of a deleted Registrazione. */
class NavigazioneProgettoDimenticaTest {
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val shell = ShellPresenter(
        scope,
        Dispatchers.Unconfined,
        SessioneProgettoFinta().also { it.crea("/tmp", "Prova") },
        SEZIONI_SHELL_R2,
    )

    @AfterEach
    fun chiudi() = scope.cancel()

    @Test
    fun `AC-632 S3 della Registrazione eliminata diventa l elenco`() {
        val navigazione = NavigazioneProgetto(shell.azioni, SchermataR1.Registrazioni)
        navigazione.apriRegistrazione(R)

        navigazione.dimentica(R)

        assertEquals(SchermataR1.Registrazioni, navigazione.schermata)
    }

    @Test
    fun `AC-632 S3 di un altra Registrazione resta`() {
        val navigazione = NavigazioneProgetto(shell.azioni, SchermataR1.Registrazioni)
        navigazione.apriRegistrazione(ALTRA)

        navigazione.dimentica(R)

        assertEquals(SchermataR1.Registrazione(ALTRA), navigazione.schermata)
    }

    @Test
    fun `AC-632 S5 aperto da S3 della Registrazione eliminata torna all elenco, non a S3`() {
        val navigazione = NavigazioneProgetto(shell.azioni, SchermataR1.Registrazioni)
        navigazione.apriRegistrazione(R)
        navigazione.apriModelli()

        navigazione.dimentica(R)

        assertEquals(SchermataR1.Modelli, navigazione.schermata, "S5 resta dov'e'")
        navigazione.tornaAllElenco()
        assertEquals(SchermataR1.Registrazioni, navigazione.schermata)
    }

    @Test
    fun `AC-632 S5 aperto da S3 di un altra Registrazione torna a quella S3`() {
        val navigazione = NavigazioneProgetto(shell.azioni, SchermataR1.Registrazioni)
        navigazione.apriRegistrazione(ALTRA)
        navigazione.apriModelli()

        navigazione.dimentica(R)

        navigazione.tornaAllElenco()
        assertEquals(SchermataR1.Registrazione(ALTRA), navigazione.schermata)
    }

    private companion object {
        val R = RegistrazioneId("r-eliminata")
        val ALTRA = RegistrazioneId("r-altra")
    }
}
