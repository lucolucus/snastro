package snastro.avvio.r1

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import snastro.avvio.CodaCondivisa
import snastro.avvio.ContestoEstensione
import snastro.avvio.EstensioneSessione
import snastro.avvio.ProgettoEsteso
import snastro.avvio.gestoreErrori
import snastro.avvio.segnalazioneApp
import snastro.documento.adattatori.eventi.AbbonatoDocumentoEventi
import snastro.documento.adattatori.porte.LettoreTrascrittoDaTrascrizione
import snastro.documento.adattatori.porte.ScrittoreDocumentoFile
import snastro.documento.applicazione.letture.Documento
import snastro.documento.applicazione.politiche.RigenerazioneDocumentoPolitica
import snastro.documento.applicazione.porte.LettoreNomi
import snastro.kernel.Esito
import snastro.kernel.GeneratoreId
import snastro.kernel.RegistrazioneId
import snastro.progetto.applicazione.letture.CatalogoRegistrazioni
import snastro.supporto.figlioDi
import snastro.trascrizione.adattatori.audio.DecodificatoreAudioFfmpeg
import snastro.trascrizione.adattatori.ml.AllineatorePerTurno
import snastro.trascrizione.adattatori.porte.LettoreRegistrazioneDaProgetto
import snastro.trascrizione.applicazione.comandi.AnnullaElaborazioneServizio
import snastro.trascrizione.applicazione.comandi.AvviaElaborazioneServizio
import snastro.trascrizione.applicazione.comandi.DividiVoceServizio
import snastro.trascrizione.applicazione.comandi.EseguiProssimaElaborazioneServizio
import snastro.trascrizione.applicazione.comandi.PortePipeline
import snastro.trascrizione.applicazione.comandi.RecuperaElaborazioniInterrotte
import snastro.trascrizione.applicazione.comandi.RecuperaElaborazioniInterrotteServizio
import snastro.trascrizione.applicazione.comandi.RiassegnaSegmentoServizio
import snastro.trascrizione.applicazione.comandi.UnisciVociServizio
import snastro.trascrizione.applicazione.letture.ElaborazioniInAttesa
import snastro.trascrizione.applicazione.letture.TrascrittoQuery
import snastro.trascrizione.applicazione.letture.VociDelTrascritto
import snastro.trascrizione.applicazione.porte.DecodificatoreAudio
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.util.logging.Logger

/**
 * The R1 (Trascrizione) extension of the R0 per-project graph ([EstensioneSessione]): built over the
 * project's OWN database, event dispatcher and session scope — never a second SessioneProgetto,
 * dispatcher or LettoreAudio.
 *
 * - Every Trascrizione command service gets `dispatcher.unitaDiLavoro`, never the raw
 *   `UnitaDiLavoroSql` (AC-355, the AC-346 pattern) — otherwise `pubblica` would throw.
 * - The project's ONE `FasiInCorso` (`ContestoEstensione.porte`, ADR 0030 §1), written by the pipeline
 *   (through [SegnalatoreFaseConCambiamenti]) and read by `porte.statiElaborazione` (AC-353) — the SAME
 *   instance Sintesi's cross-context read uses (AC-C63), never a second one; every phase change and
 *   every Elaborazione/Revisione event is a `Cambiamento` ([AggiornamentiVistaTrascrizione], AC-354).
 * - No synchronous subscriber at all, in particular none on `RegistrazioneAggiunta`: importing never
 *   starts an Elaborazione (ADR 0014, AC-371). `AbbonatoDocumentoEventi` registers itself after-commit
 *   and runs on [io] (never on the UI thread), in a child of the session scope.
 * - The ML adapters' per-Elaborazione memory is released when each run terminates
 *   ([SegnalatoreFaseConRilascio], ADR 0004). fix-batch-16 MED-2: the adapters themselves are built
 *   PER OPEN PROJECT ([adattatoriMl], called once per [apri]) over the app's ONE `MotoreSherpa` — a
 *   worker of a just-closed project still finishing in the background never shares a cached native
 *   model (nor its release) with the next project's own worker.
 * - The Documento reads names from [lettoreNomi]: [LettoreNomiVuoto] in R1 ('Voce n', AC-356: no
 *   `:parlanti` class); R2 (`snastro.avvio.r2.EstensioneR2`) passes `LettoreNomiDaParlanti` (AC-359).
 * - The [CodaCondivisa] is built LAST, with the Elaborazione source plus the context's
 *   [ContestoEstensione.fontiCoda] (ADR 0023: none in R0–R2, behaviour unchanged; R3 adds the Riassunto one):
 *   its construction runs `RecuperaElaborazioniInterrotte`
 *   strictly before its worker picks any FIFO head (AC-233), and only after every after-commit
 *   subscriber above is registered (a recovered `fallita` still refreshes S2 and the Documento). Its
 *   first recovery completes [CollaboratoriR1.recuperoConcluso] — what R2's `RiallineaTutteLeImpronte`
 *   waits for (ADR 0012 Amendment (b): "after the Elaborazione queue recovery", AC-316).
 */
@Suppress("LongParameterList") // one parameter per app-wide collaborator of the per-project graph
internal class EstensioneR1(
    private val io: CoroutineDispatcher,
    private val clock: Clock,
    private val generatoreId: GeneratoreId,
    private val adattatoriMl: () -> AdattatoriMl,
    private val modelliPronti: () -> Boolean,
    private val decodificatore: (Path) -> DecodificatoreAudio = ::DecodificatoreAudioFfmpeg,
    private val lettoreNomi: (ContestoEstensione) -> LettoreNomi = { LettoreNomiVuoto },
) : EstensioneSessione {
    override fun apri(contesto: ContestoEstensione): ProgettoEsteso {
        val dispatcher = contesto.dispatcher
        val uow = dispatcher.unitaDiLavoro
        val porte = contesto.porte
        val elaborazioni = porte.elaborazioni
        val trascritti = porte.trascritti
        val catalogo = porte.catalogo
        val lettoreRegistrazione = LettoreRegistrazioneDaProgetto(catalogo)
        val aggiornamenti = AggiornamentiVistaTrascrizione(dispatcher)
        val ml = adattatoriMl()

        val (lavoroDocumento, rigenerazioneDocumento) = avviaRigenerazioneDocumento(contesto)

        val pipeline = PortePipeline(
            registrazioni = lettoreRegistrazione,
            decodificatore = decodificatore(contesto.cartella),
            diarizzatore = ml.diarizzatore,
            allineatore = AllineatorePerTurno(ml.riconoscitore, ml.vad),
            segnalatore = SegnalatoreFaseConRilascio(
                // ADR 0030 §1/AC-C63: the project's ONE FasiInCorso (porte) — the very instance Sintesi's
                // LettoreTrascrittoDaTrascrizione reads through porte.statiElaborazione, never a second one.
                SegnalatoreFaseConCambiamenti(porte.fasiInCorso, aggiornamenti::cambiata),
                ml.rilasciaDopoElaborazione,
            ),
        )
        val esegui = EseguiProssimaElaborazioneServizio(uow, clock, elaborazioni, trascritti, pipeline, dispatcher)
        val recupera = RecuperaElaborazioniInterrotteServizio(uow, elaborazioni, dispatcher)
        val recuperoConcluso = CompletableDeferred<Unit>()
        val coda = CodaCondivisa(
            scope = contesto.scope,
            fonti = listOf(
                fonteCodaElaborazione(
                    servizio = esegui,
                    elenco = ElaborazioniInAttesa(elaborazioni),
                    recupera = recuperoElaborazioni(recupera, recuperoConcluso),
                    modelliPronti = modelliPronti,
                ),
            ) + contesto.fontiCoda, // ADR 0023 §1: R3 adds the Riassunto source; R0–R2 none (behaviour unchanged)
            // AC-C54/AC-C57/AC-C58 (ADR 0028 §7.5): the ONE JUL-backed Segnalazione, never a local `log.warning`.
            // `Segnalazione.segnala` is pinned (message, cause?) and never string-matched: a NON-NULL cause
            // (never a real throwable, only a descriptive one) is what makes segnalazioneApp log this at
            // WARNING instead of the INFO it reserves for a cause-less recovery.
            segnalaBloccato = { id ->
                segnalazioneApp.segnala("elemento della coda condivisa escluso", ElementoCodaEscluso(id))
            },
            segnalaSfuggito = { e -> segnalazioneApp.segnala("elemento della coda condivisa sfuggito", e) },
        )

        // ADR 0030 §1/AC-C63: porte.statiElaborazione, the SAME instance Sintesi's cross-context read uses —
        // never a fresh, locally-built StatiElaborazione.
        val stati = porte.statiElaborazione
        val trascrittoQuery = TrascrittoQuery(trascritti, lettoreRegistrazione)
        return CollaboratoriR1(
            statiElaborazione = stati::stati,
            avvia = AvviaElaborazioneServizio(uow, generatoreId, clock, lettoreRegistrazione, elaborazioni)::esegui,
            // AC-478: no queue signal — a cancellation never makes work available (ADR 0018 Amendment (b) §3).
            annullaElaborazione = AnnullaElaborazioneServizio(uow, elaborazioni, dispatcher)::esegui,
            trascritto = trascrittoQuery::vista,
            percorsoDocumento = { id -> percorsoDocumento(contesto.cartella, catalogo, id) },
            revisione = ComandiRevisione(
                UnisciVociServizio(uow, trascritti, dispatcher),
                DividiVoceServizio(uow, trascritti, dispatcher),
                RiassegnaSegmentoServizio(uow, trascritti, dispatcher),
            ),
            coda = coda,
            lavoroDocumento = lavoroDocumento,
            rigenerazioneDocumento = rigenerazioneDocumento,
            aggiornamenti = aggiornamenti,
            recuperoConcluso = recuperoConcluso,
        )
    }

    /**
     * `AbbonatoDocumentoEventi` (after-commit, startup sweep) on its own child of the session scope
     * ([figlioDi], AC-C56), on [io] — never the UI thread; returns that child's [Job] (which
     * [CollaboratoriR1.ferma] joins) paired with the [RigenerazioneDocumentoPolitica] it subscribes — AC-C61:
     * R2's `PuliziaDerivatiFile` reuses this SAME instance instead of building its own.
     */
    private fun avviaRigenerazioneDocumento(contesto: ContestoEstensione): Pair<Job, RigenerazioneDocumentoPolitica> {
        val scope = figlioDi(contesto.scope, io, gestoreErrori)
        val porte = contesto.porte
        val lettoreTrascritto = LettoreTrascrittoDaTrascrizione(VociDelTrascritto(porte.trascritti), porte.catalogo)
        val politica = RigenerazioneDocumentoPolitica(
            lettoreTrascritto,
            lettoreNomi(contesto),
            ScrittoreDocumentoFile(contesto.cartella.resolve(CARTELLA_DOCUMENTI)),
        )
        AbbonatoDocumentoEventi(
            contesto.dispatcher,
            politica,
            // AC-C47: the startup sweep lists ids itself, so a poisoned Registrazione's retries never block or
            // re-run every other one (never through RigenerazioneDocumentoPolitica's all-or-nothing fold).
            lettoreTrascritto::registrazioniConTrascritto,
            scope,
            // AC-C54: the ONE JUL-backed Segnalazione of `:avvio` — the a2 local lambda is gone.
            segnalazioneApp,
        )
        val job = checkNotNull(scope.coroutineContext[Job]) { "figlioDi restituisce sempre uno scope con un Job" }
        return job to politica
    }

    /**
     * The Elaborazione source's [snastro.avvio.FonteCoda.recupera] (AC-233): runs
     * `RecuperaElaborazioniInterrotte`, then completes [recuperoConcluso] in a `finally` — ALSO on
     * failure, so [CollaboratoriR1.recuperoConcluso]'s waiters never hang.
     */
    private fun recuperoElaborazioni(
        recupera: RecuperaElaborazioniInterrotteServizio,
        recuperoConcluso: CompletableDeferred<Unit>,
    ): () -> Unit = {
        try {
            val esito = recupera.esegui(RecuperaElaborazioniInterrotte)
            if (esito is Esito.Errore) log.warning("recupero delle elaborazioni interrotte fallito: $esito")
        } finally {
            recuperoConcluso.complete(Unit)
        }
    }

    private companion object {
        const val CARTELLA_DOCUMENTI = "documenti"
        val log: Logger = Logger.getLogger(EstensioneR1::class.java.name)

        /**
         * S3's "Apri documento" target (AC-218): `documenti/<Documento.nomeFile>` of [id] — the same pure
         * name the regeneration writes (ADR 0010) — or `null` while it has not been written yet.
         */
        fun percorsoDocumento(cartella: Path, catalogo: CatalogoRegistrazioni, id: RegistrazioneId): String? =
            catalogo.registrazione(id)
                ?.let { r -> Documento.nomeFile(r.dataRegistrazione, r.titolo) }
                ?.let { nome -> cartella.resolve(CARTELLA_DOCUMENTI).resolve(nome) }
                ?.takeIf(Files::isRegularFile)
                ?.toAbsolutePath()
                ?.toString()
    }
}

/**
 * AC-C54: [CodaCondivisa]'s `segnalaBloccato` hook's own report needs a non-null `causa` so
 * [snastro.avvio.segnalazioneApp] logs it at WARNING — never a real thrown exception (nothing threw), so a
 * dedicated, descriptive marker type, never string-matched.
 */
private class ElementoCodaEscluso(id: String) : Exception("elemento '$id' escluso dalla coda")
