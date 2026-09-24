package snastro.avvio.r1

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import snastro.avvio.ApriEsternoDesktop
import snastro.avvio.ContestoEstensione
import snastro.avvio.GrafoR0
import snastro.avvio.cartellaDatiRegistroProgettiReale
import snastro.avvio.costruisciGrafoR0
import snastro.avvio.orologioApp
import snastro.documento.applicazione.porte.LettoreNomi
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
import java.time.Clock

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

/** The app-wide R1 pieces [costruisciGrafoR1] hands the R0 graph: the per-project extension and S5's port. */
internal class ComponentiR1(
    val estensione: EstensioneR1,
    val servizioModelli: ServizioModelli,
    /** The app's ONE engine and model provisioning, shared with R2's print extractor (ADR 0016 §4, 0019 §2). */
    val motore: MotoreSherpa,
    val provisioning: ProvisioningModelli,
)

/**
 * [scelta] picks the ML adapters and the model catalogue ([SelezioneAdattatoriMl], `-Dsnastro.ml`);
 * [cartellaModelli] is the per-user model cache (ADR 0008 (c)). ONE [MotoreSherpa] for the whole app
 * (its Mutex is process-wide, ADR 0016 §4); the ML adapters over it are built once per open project
 * (fix-batch-16 MED-2). The Elaborazione queue waits while S5 is not
 * [StatoModelli.Pronti] (AC-235) — immediately `Pronti` for an empty catalogue (the Finte). [lettoreNomi]
 * is the Documento's names source: 'Voce n' only in R1 ([LettoreNomiVuoto]); R2 passes the Parlanti one.
 */
internal fun componentiR1(
    scelta: SceltaMl,
    cartellaModelli: Path,
    io: CoroutineDispatcher,
    clock: Clock,
    lettoreNomi: (ContestoEstensione) -> LettoreNomi = { LettoreNomiVuoto },
): ComponentiR1 {
    val catalogo = SelezioneAdattatoriMl.catalogo(scelta)
    val provisioning = ProvisioningModelli(catalogo, cartellaModelli)
    val servizioModelli = ServizioModelliProvisioning.di(catalogo, provisioning)
    val motore = MotoreSherpa() // ONE per app; the adapters over it are built per open project (MED-2)
    val estensione = EstensioneR1(
        io = io,
        clock = clock,
        generatoreId = GeneratoreIdUuid(),
        adattatoriMl = { SelezioneAdattatoriMl.adattatori(scelta, motore, provisioning) },
        modelliPronti = { servizioModelli.stato.value == StatoModelli.Pronti },
        lettoreNomi = lettoreNomi,
    )
    return ComponentiR1(estensione, servizioModelli, motore, provisioning)
}

/**
 * S5's port over the REAL model catalogue ([SceltaMl.REALI]) on [cartellaModelli] — the `--smoke` S5
 * (fix-batch-16 LOW-1) over an empty throwaway cache: building it loads and downloads nothing.
 */
internal fun servizioModelliReali(cartellaModelli: Path): ServizioModelli {
    val catalogo = SelezioneAdattatoriMl.catalogo(SceltaMl.REALI)
    return ServizioModelliProvisioning.di(catalogo, ProvisioningModelli(catalogo, cartellaModelli))
}

/** The R1 graph: R0's own ([costruisciGrafoR0]) extended with [componentiR1]; `--smoke` passes throwaway folders. */
internal fun costruisciGrafoR1(
    cartellaRegistro: Path = cartellaDatiRegistroProgettiReale(),
    scelta: SceltaMl = SceltaMl.daSistema(),
    cartellaModelli: Path = CartellaCacheModelli.risolvi(),
): GrafoR1 {
    val io: CoroutineDispatcher = Dispatchers.IO
    val clock = orologioApp()
    val componenti = componentiR1(scelta, cartellaModelli, io, clock)
    return GrafoR1(
        r0 = costruisciGrafoR0(cartellaRegistro, io, clock, componenti.estensione),
        servizioModelli = componenti.servizioModelli,
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
