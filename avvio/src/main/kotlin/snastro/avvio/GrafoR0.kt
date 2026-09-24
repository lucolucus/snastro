package snastro.avvio

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.swing.Swing
import snastro.kernel.GeneratoreIdUuid
import snastro.progetto.adattatori.porte.RegistroProgettiFile
import snastro.progetto.applicazione.letture.ElencoProgetti
import java.nio.file.Path
import java.time.Clock
import java.time.Duration

/**
 * The R0 graph (dev-architecture `#pacchetti`: manual wiring, all in `:avvio`). [scope] is a single
 * presenter scope on [io]/`Dispatchers.Swing` with a `SupervisorJob` — one presenter's failure never
 * kills another's collectors (wiring requirement carried over from earlier reviews). [clock] ticks at
 * millisecond precision: the SQL adapters store epoch millis (`Instant.toEpochMilli()`), a finer clock
 * would break save/read equality.
 */
internal class GrafoR0(
    val scope: CoroutineScope,
    val io: CoroutineDispatcher,
    val clock: Clock,
    val sessione: SessioneProgettoImpl,
    val elencoProgetti: ElencoProgetti,
    val cartellaProgettiPredefinita: String,
)

/** The real per-OS, per-user app-data folder (AC-348) — `main()`'s own binding. */
internal fun cartellaDatiRegistroProgettiReale(): Path = CartellaDatiRegistroProgetti.risolvi(
    sistemaOperativo = System.getProperty("os.name").orEmpty(),
    cartellaUtente = System.getProperty("user.home").orEmpty(),
    localAppData = System.getenv("LOCALAPPDATA"),
    xdgDataHome = System.getenv("XDG_DATA_HOME"),
)

/**
 * ADR 0010: S1's default parent folder for new projects — `main()`'s own binding (fix-batch-12 #4:
 * `:ui:schermata-progetti` never calls `System.getProperty` itself). v1 is Mac-only (ADR 0010), so no
 * per-OS resolver is needed here (unlike [CartellaDatiRegistroProgetti]).
 */
internal fun cartellaProgettiPredefinitaReale(): String =
    "${System.getProperty("user.home").orEmpty()}/Documents/snastro"

/** The app's one clock: millisecond ticks (see [GrafoR0]'s KDoc — the SQL adapters store epoch millis). */
internal fun orologioApp(): Clock = Clock.tick(Clock.systemUTC(), Duration.ofMillis(1))

/**
 * [cartellaRegistro] defaults to the real per-user app-data folder ([cartellaDatiRegistroProgettiReale])
 * for `main()`'s own run; `--smoke` (and its test) inject an isolated, throwaway one instead — a smoke
 * run must never add entries to (or collide with) the developer's own recent-projects registry.
 * [estensione] is `null` for R0 alone (AC-350); the R1 composition (`costruisciGrafoR1`) passes its
 * own, built over the same [io] and [clock].
 */
internal fun costruisciGrafoR0(
    cartellaRegistro: Path = cartellaDatiRegistroProgettiReale(),
    io: CoroutineDispatcher = Dispatchers.IO,
    clock: Clock = orologioApp(),
    estensione: EstensioneSessione? = null,
): GrafoR0 {
    val scope = CoroutineScope(Dispatchers.Swing + SupervisorJob())
    val generatoreId = GeneratoreIdUuid()

    val registro = RegistroProgettiFile(cartellaRegistro.resolve("registro-progetti.tsv"))
    val sessione = SessioneProgettoImpl(registro, generatoreId, clock, scopeGenitore = scope, estensione = estensione)
    val elencoProgetti = ElencoProgetti(registro)

    return GrafoR0(scope, io, clock, sessione, elencoProgetti, cartellaProgettiPredefinitaReale())
}
