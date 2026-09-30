package snastro.avvio

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.swing.Swing
import snastro.avvio.modelli.ModelliApp
import snastro.avvio.parlanti.adattatoriParlanti
import snastro.avvio.progetto.ComponentiApp
import snastro.avvio.progetto.SessioneProgettoImpl
import snastro.avvio.progetto.registroProgettiFile
import snastro.avvio.sintesi.modelloLinguistico
import snastro.avvio.trascrizione.SceltaMl
import snastro.avvio.trascrizione.SelezioneAdattatoriMl
import snastro.avvio.trascrizione.decodificatoreFfmpeg
import snastro.kernel.GeneratoreIdUuid
import snastro.ml.MotoreSherpa
import snastro.modelli.CartellaCacheModelli
import snastro.modelli.CatalogoModelli
import snastro.progetto.applicazione.letture.ElencoProgetti
import snastro.ui.ApriEsterno
import snastro.ui.impostazioni.PreferenzeApp
import snastro.ui.modelli.ServizioModelli
import java.nio.file.Path
import java.time.Clock
import java.time.Duration

/**
 * The ONE app graph (ADR 0030 §1: the former per-release graphs flattened in; dev-architecture `#pacchetti`: manual
 * wiring, all in `:avvio`). [scope] is a single presenter scope on `Dispatchers.Swing` with a `SupervisorJob` — one
 * presenter's failure never kills another's collectors. [io] is the ONE background dispatcher. [clock] ticks at
 * millisecond precision: the SQL adapters store epoch millis, a finer clock would break save/read equality.
 * [servizioModelli] is S5's port (models are per user), [apriEsterno] what S3 uses for the Sbobinatura, [preferenze]
 * the app-wide Impostazioni (theme, folder of new projects).
 */
@Suppress("LongParameterList") // one parameter per app-wide collaborator of the screens
internal class Grafo(
    val scope: CoroutineScope,
    val io: CoroutineDispatcher,
    val clock: Clock,
    val sessione: SessioneProgettoImpl,
    val elencoProgetti: ElencoProgetti,
    val cartellaProgettiPredefinita: String,
    val servizioModelli: ServizioModelli,
    val apriEsterno: ApriEsterno,
    val preferenze: PreferenzeApp,
)

/** The real per-OS, per-user app-data folder (AC-348) — `main()`'s own binding. */
internal fun cartellaDatiRegistroProgettiReale(): Path = CartellaDatiRegistroProgetti.risolvi(
    sistemaOperativo = System.getProperty("os.name").orEmpty(),
    cartellaUtente = System.getProperty("user.home").orEmpty(),
    localAppData = System.getenv("LOCALAPPDATA"),
    xdgDataHome = System.getenv("XDG_DATA_HOME"),
)

/**
 * ADR 0010: S1's default parent folder for new projects — `main()`'s own binding (fix-batch-12 #4: `:ui` never calls
 * `System.getProperty` itself). v1 is Mac-only (ADR 0010), so no per-OS resolver is needed here.
 */
internal fun cartellaProgettiPredefinitaReale(): String =
    "${System.getProperty("user.home").orEmpty()}/Documents/snastro"

/** The app's one clock: millisecond ticks (see [Grafo]'s KDoc — the SQL adapters store epoch millis). */
internal fun orologioApp(): Clock = Clock.tick(Clock.systemUTC(), Duration.ofMillis(1))

/**
 * The app graph. [scelta] picks the ML adapters (`-Dsnastro.ml`, [SelezioneAdattatoriMl]); [catalogo] is the models
 * S5 provisions — [scelta]'s own by default, the REAL one under `--smoke`, which also passes throwaway
 * [cartellaRegistro]/[cartellaModelli] (a smoke run never touches the developer's own registry or model cache).
 * ONE [MotoreSherpa] (its Mutex is process-wide, ADR 0016 §4) and ONE `ProvisioningModelli` ([ModelliApp], AC-C75)
 * for the whole app; the ML adapters over them are built per open project (fix-batch-16 MED-2).
 */
internal fun costruisciGrafo(
    cartellaRegistro: Path = cartellaDatiRegistroProgettiReale(),
    scelta: SceltaMl = SceltaMl.daSistema(),
    cartellaModelli: Path = CartellaCacheModelli.risolvi(),
    catalogo: CatalogoModelli = SelezioneAdattatoriMl.catalogo(scelta),
): Grafo {
    val io: CoroutineDispatcher = Dispatchers.IO
    val clock = orologioApp()
    val scope = CoroutineScope(Dispatchers.Swing + SupervisorJob())
    val modelli = ModelliApp(catalogo, cartellaModelli)
    val motore = MotoreSherpa()
    val app = ComponentiApp(
        io = io,
        adattatoriMl = { SelezioneAdattatoriMl.adattatori(scelta, motore, modelli.provisioning) },
        decodificatore = ::decodificatoreFfmpeg,
        adattatoriParlanti = { SelezioneAdattatoriMl.adattatoriParlanti(scelta, motore, modelli.provisioning) },
        modelliPronti = modelli::pronti,
        modello = modelloLinguistico(scelta, modelli.provisioning),
        disponibilita = modelli.disponibilita,
    )
    val registro = registroProgettiFile(cartellaRegistro)
    val sessione = SessioneProgettoImpl(registro, GeneratoreIdUuid(), clock, scopeGenitore = scope, app = app)
    return Grafo(
        scope = scope,
        io = io,
        clock = clock,
        sessione = sessione,
        elencoProgetti = ElencoProgetti(registro),
        cartellaProgettiPredefinita = cartellaProgettiPredefinitaReale(),
        servizioModelli = modelli.servizio,
        apriEsterno = ApriEsternoDesktop(),
        preferenze = preferenzeAppFile(cartellaRegistro),
    )
}
