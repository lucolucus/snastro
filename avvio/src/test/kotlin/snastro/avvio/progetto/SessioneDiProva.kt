package snastro.avvio.progetto

import snastro.kernel.GeneratoreIdFinto
import snastro.progetto.applicazione.porte.RegistroProgetti
import snastro.supporto.test.conScopeDiProva
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** The clock of the delete tests: 2026-10-08, 10:00 UTC. */
internal val OROLOGIO_DI_PROVA: Clock = Clock.fixed(Instant.parse("2026-10-08T10:00:00Z"), ZoneOffset.UTC)

/**
 * Runs [blocco] with a real [SessioneProgettoImpl] over [registro] — ML/audio from [componentiDiProva] — on a scope
 * from [conScopeDiProva]; the session is closed, then its scope cancelled, even when [blocco] fails.
 */
internal fun <T> conSessioneDiProva(registro: RegistroProgetti, blocco: (SessioneProgettoImpl) -> T): T =
    conScopeDiProva { scope ->
        val sessione =
            SessioneProgettoImpl(registro, GeneratoreIdFinto(), OROLOGIO_DI_PROVA, scope, componentiDiProva())
        try {
            blocco(sessione)
        } finally {
            sessione.chiudi()
        }
    }
