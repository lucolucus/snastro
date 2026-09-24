package snastro.avvio.r1

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import snastro.avvio.ApriEsternoDesktop
import snastro.avvio.GrafoR0
import snastro.avvio.cartellaDatiRegistroProgettiReale
import snastro.avvio.costruisciGrafoR0
import snastro.avvio.orologioApp
import snastro.kernel.GeneratoreIdUuid
import snastro.kernel.RegistrazioneId
import snastro.ml.MotoreSherpa
import snastro.modelli.CartellaCacheModelli
import snastro.modelli.ProvisioningModelli
import snastro.trascrizione.applicazione.letture.StatoElaborazioneVista
import snastro.ui.ApriEsterno
import snastro.ui.DestinazioneShell
import snastro.ui.modelli.ServizioModelli
import snastro.ui.modelli.StatoModelli
import java.nio.file.Path

/** AC-355/AC-341: R1's shell still shows only Registrazioni — the Parlanti section arrives with R2. */
internal val SEZIONI_SHELL_R1: Set<DestinazioneShell> = setOf(DestinazioneShell.REGISTRAZIONI)

/**
 * The R1 (Trascrizione) graph: R0's graph ([r0]) EXTENDED with [EstensioneR1] (never a second
 * SessioneProgetto, dispatcher or LettoreAudio), plus the app-wide S5 port [servizioModelli]
 * (models are per user, not per project) and the [apriEsterno] S3 uses for the Documento.
 */
internal class GrafoR1(
    val r0: GrafoR0,
    val servizioModelli: ServizioModelli,
    val apriEsterno: ApriEsterno,
)

/**
 * Builds the R1 graph. [scelta] picks the ML adapters and the model catalogue
 * ([SelezioneAdattatoriMl], `-Dsnastro.ml`); [cartellaModelli] is the per-user model cache (ADR 0008
 * (c)) — `--smoke` injects a throwaway one. The Elaborazione queue waits while S5 is not
 * [StatoModelli.Pronti] (AC-235) — immediately `Pronti` for an empty catalogue (the Finte).
 */
internal fun costruisciGrafoR1(
    cartellaRegistro: Path = cartellaDatiRegistroProgettiReale(),
    scelta: SceltaMl = SceltaMl.daSistema(),
    cartellaModelli: Path = CartellaCacheModelli.risolvi(),
): GrafoR1 {
    val io: CoroutineDispatcher = Dispatchers.IO
    val clock = orologioApp()
    val catalogo = SelezioneAdattatoriMl.catalogo(scelta)
    val provisioning = ProvisioningModelli(catalogo, cartellaModelli)
    val servizioModelli = ServizioModelliProvisioning.di(catalogo, provisioning)
    val estensione = EstensioneR1(
        io = io,
        clock = clock,
        generatoreId = GeneratoreIdUuid(),
        ml = SelezioneAdattatoriMl.adattatori(scelta, MotoreSherpa(), provisioning),
        modelliPronti = { servizioModelli.stato.value == StatoModelli.Pronti },
    )
    return GrafoR1(
        r0 = costruisciGrafoR0(cartellaRegistro, io, clock, estensione),
        servizioModelli = servizioModelli,
        apriEsterno = ApriEsternoDesktop(),
    )
}

/** The open project's first Registrazione whose Elaborazione is COMPLETATA, or `null` (the `--smoke` S3 target). */
internal fun GrafoR1.primaRegistrazioneCompletata(): RegistrazioneId? {
    val collaboratori = r0.sessione.collaboratoriCorrenti()
    val r1 = collaboratori?.estensione as? CollaboratoriR1 ?: return null
    val ids = collaboratori.registrazioni().map { it.registrazioneId }
    return r1.statiElaborazione(ids).firstOrNull { it.stato == StatoElaborazioneVista.COMPLETATA }?.registrazioneId
}
