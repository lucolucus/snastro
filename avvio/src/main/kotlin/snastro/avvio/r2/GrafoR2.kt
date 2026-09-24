package snastro.avvio.r2

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import snastro.avvio.ApriEsternoDesktop
import snastro.avvio.GrafoR0
import snastro.avvio.cartellaDatiRegistroProgettiReale
import snastro.avvio.costruisciGrafoR0
import snastro.avvio.orologioApp
import snastro.avvio.r1.SceltaMl
import snastro.avvio.r1.SelezioneAdattatoriMl
import snastro.avvio.r1.componentiR1
import snastro.kernel.GeneratoreIdUuid
import snastro.kernel.RegistrazioneId
import snastro.modelli.CartellaCacheModelli
import snastro.trascrizione.applicazione.letture.StatoElaborazioneVista
import snastro.ui.ApriEsterno
import snastro.ui.DestinazioneShell
import snastro.ui.modelli.ServizioModelli
import java.nio.file.Path
import java.time.Clock

/** AC-341/AC-177: R2's shell shows Registrazioni AND the Parlanti section (S4). */
internal val SEZIONI_SHELL_R2: Set<DestinazioneShell> =
    setOf(DestinazioneShell.REGISTRAZIONI, DestinazioneShell.PARLANTI)

/**
 * The R2 (Parlanti) graph: R0's graph ([r0]) EXTENDED with [EstensioneR2] — itself R1's extension
 * extended — plus the app-wide S5 port [servizioModelli] and the [apriEsterno] S3 uses for the Documento.
 */
internal class GrafoR2(
    val r0: GrafoR0,
    val servizioModelli: ServizioModelli,
    val apriEsterno: ApriEsterno,
)

/** The app-wide R2 pieces handed to the R0 graph: the per-project extension and S5's port. */
internal class ComponentiR2(val estensione: EstensioneR2, val servizioModelli: ServizioModelli)

/**
 * R1's app-wide components ([componentiR1]: ONE `MotoreSherpa`, the model catalogue, S5) with the Documento
 * reading the Nomi from the Parlanti ([lettoreNomiDaParlanti], AC-359), wrapped by [EstensioneR2] whose
 * print extractor comes from the same single ML selection point ([SelezioneAdattatoriMl.adattatoriParlanti]).
 */
internal fun componentiR2(
    scelta: SceltaMl,
    cartellaModelli: Path,
    io: CoroutineDispatcher,
    clock: Clock,
): ComponentiR2 {
    val r1 = componentiR1(scelta, cartellaModelli, io, clock, ::lettoreNomiDaParlanti)
    val estensione = EstensioneR2(
        r1 = r1.estensione,
        io = io,
        clock = clock,
        generatoreId = GeneratoreIdUuid(),
        adattatori = { SelezioneAdattatoriMl.adattatoriParlanti(scelta, r1.motore, r1.provisioning) },
    )
    return ComponentiR2(estensione, r1.servizioModelli)
}

/** The R2 graph (the app's): R0's own ([costruisciGrafoR0]) extended with [componentiR2]. */
internal fun costruisciGrafoR2(
    cartellaRegistro: Path = cartellaDatiRegistroProgettiReale(),
    scelta: SceltaMl = SceltaMl.daSistema(),
    cartellaModelli: Path = CartellaCacheModelli.risolvi(),
): GrafoR2 {
    val io: CoroutineDispatcher = Dispatchers.IO
    val clock = orologioApp()
    val componenti = componentiR2(scelta, cartellaModelli, io, clock)
    return GrafoR2(
        r0 = costruisciGrafoR0(cartellaRegistro, io, clock, componenti.estensione),
        servizioModelli = componenti.servizioModelli,
        apriEsterno = ApriEsternoDesktop(),
    )
}

/** The open project's first Registrazione whose Elaborazione is COMPLETATA, or `null` (the `--smoke` S3 target). */
internal fun GrafoR2.primaRegistrazioneCompletata(): RegistrazioneId? {
    val collaboratori = r0.sessione.collaboratoriCorrenti()
    val r2 = collaboratori?.estensione as? CollaboratoriR2 ?: return null
    val ids = collaboratori.registrazioni().map { it.registrazioneId }
    return r2.r1.statiElaborazione(ids).firstOrNull { it.stato == StatoElaborazioneVista.COMPLETATA }?.registrazioneId
}
