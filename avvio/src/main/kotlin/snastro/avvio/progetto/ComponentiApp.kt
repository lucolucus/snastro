package snastro.avvio.progetto

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import snastro.avvio.parlanti.AdattatoriParlanti
import snastro.avvio.trascrizione.AdattatoriMl
import snastro.kernel.GeneratoreId
import snastro.kernel.ProgettoId
import snastro.progetto.applicazione.porte.SondaAudio
import snastro.sintesi.applicazione.porte.DisponibilitaModelloLinguistico
import snastro.sintesi.applicazione.porte.ModelloLinguistico
import snastro.trascrizione.applicazione.porte.DecodificatoreAudio
import snastro.ui.lettore.LettoreAudio
import java.nio.file.Path
import java.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The app-wide pieces every open project is composed over (ADR 0030 §1), built ONCE by `costruisciGrafo` (the app,
 * `--smoke`) or by a test's `AmbienteProgetto` with the non-headless edges faked: [io] for the background work, the
 * ML adapters of the ONE `MotoreSherpa` ([adattatoriMl], [adattatoriParlanti] — built per open project, fix-batch-16
 * MED-2), the pipeline's [decodificatore], whether the pipeline's models are ready ([modelliPronti], AC-235), the
 * LLM ([modello]) and its availability ([disponibilita], over the ONE `ProvisioningModelli`, AC-C75), and the ONE
 * shared shutdown deadline ([scadenzaArresto], AC-C73).
 */
@Suppress("LongParameterList") // one parameter per app-wide collaborator of the per-project composition
internal class ComponentiApp(
    val io: CoroutineDispatcher,
    val adattatoriMl: () -> AdattatoriMl,
    val decodificatore: (Path) -> DecodificatoreAudio,
    val adattatoriParlanti: () -> AdattatoriParlanti,
    val modelliPronti: () -> Boolean,
    val modello: ModelloLinguistico,
    val disponibilita: DisponibilitaModelloLinguistico,
    val scadenzaArresto: Duration = SCADENZA_ARRESTO,
) {
    companion object {
        /** ADR 0017 §3 / fix-batch-16: the bound of the whole shutdown of an open project. */
        val SCADENZA_ARRESTO: Duration = 5.seconds
    }
}

/**
 * What [SessioneProgettoImpl] hands [apriProgetto] for ONE open project besides its [PorteProgetto]: the Progetto,
 * its folder, the session's own child [scope] (cancelled by `chiudi` before `ArrestoProgetto` runs), the project's ONE
 * [lettoreAudio], the app's [generatoreId] and [clock], and the [sondaAudio] of `AggiungiRegistrazione`.
 */
@Suppress("LongParameterList") // one parameter per session-owned value the composition builds over
internal class AperturaProgetto(
    val progettoId: ProgettoId,
    val cartella: Path,
    val scope: CoroutineScope,
    val lettoreAudio: LettoreAudio,
    val generatoreId: GeneratoreId,
    val clock: Clock,
    val sondaAudio: SondaAudio,
)
