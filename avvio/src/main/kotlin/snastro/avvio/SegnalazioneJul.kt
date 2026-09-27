package snastro.avvio

import kotlinx.coroutines.CoroutineExceptionHandler
import snastro.supporto.Segnalazione
import snastro.supporto.gestoreErroriNonCatturati
import java.util.logging.Level
import java.util.logging.Logger

/**
 * The ONE JUL-backed [Segnalazione] of `:avvio` src/main (ADR 0028 §2, AC-C54): the Documento worker
 * ([snastro.avvio.r1.EstensioneR1]), the Parlanti realignment worker ([snastro.avvio.r2.EstensioneR2]) and
 * [CodaCondivisa]'s own escape hook all report through this SAME instance — the a2/a3 per-extension local
 * `Segnalazione { … }` lambdas are gone.
 *
 * It logs through the `"snastro"` logger — the SAME one [configuraLoggingApp] attaches the rotating file
 * handler to (AC-C87..AC-C90). With no handler installed (every test, `--smoke`) it falls back to the
 * JVM's own default console handler, exactly like every other `Logger.getLogger(…)` already in this
 * codebase — no write to the real user folder happens unless [configuraLoggingApp] was called.
 *
 * [causa] `null` means nothing actually threw (a `RitentaConBackoff` recovery, or its own false-return
 * failure): logged at INFO, never WARNING — a recovery must not read like a failure (open pre-release
 * LOWs on the retired a2/a3 lambdas, which logged both alike at WARNING).
 */
internal val segnalazioneApp: Segnalazione = Segnalazione { messaggio, causa ->
    Logger.getLogger("snastro").log(if (causa != null) Level.WARNING else Level.INFO, messaggio, causa)
}

/**
 * ADR 0028 §2, AC-C55/AC-C56: the ONE [CoroutineExceptionHandler] every per-project child scope built with
 * [figlioDi] carries — an exception escaping a per-project coroutine is reported exactly once through
 * [segnalazioneApp] and cancels neither the project scope nor its sibling jobs (a `SupervisorJob`'s own
 * guarantee). Stateless (it only forwards to [segnalazioneApp]): one shared instance is as correct as a
 * fresh one per project, and simpler.
 */
internal val gestoreErrori: CoroutineExceptionHandler = gestoreErroriNonCatturati(segnalazioneApp)
