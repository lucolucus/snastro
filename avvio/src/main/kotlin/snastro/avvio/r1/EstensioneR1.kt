package snastro.avvio.r1

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import snastro.avvio.CodaElaborazioni
import snastro.avvio.ContestoEstensione
import snastro.avvio.EstensioneSessione
import snastro.avvio.ProgettoEsteso
import snastro.documento.adattatori.eventi.AbbonatoDocumentoEventi
import snastro.documento.adattatori.porte.LettoreTrascrittoDaTrascrizione
import snastro.documento.adattatori.porte.ScrittoreDocumentoFile
import snastro.documento.applicazione.letture.Documento
import snastro.documento.applicazione.politiche.RigenerazioneDocumentoPolitica
import snastro.kernel.Esito
import snastro.kernel.GeneratoreId
import snastro.kernel.RegistrazioneId
import snastro.progetto.applicazione.letture.CatalogoRegistrazioni
import snastro.trascrizione.adattatori.audio.DecodificatoreAudioFfmpeg
import snastro.trascrizione.adattatori.ml.AllineatorePerTurno
import snastro.trascrizione.adattatori.persistenza.ElaborazioneRepositorySql
import snastro.trascrizione.adattatori.persistenza.TrascrittoRepositorySql
import snastro.trascrizione.adattatori.porte.LettoreRegistrazioneDaProgetto
import snastro.trascrizione.applicazione.comandi.AvviaElaborazioneServizio
import snastro.trascrizione.applicazione.comandi.DividiVoceServizio
import snastro.trascrizione.applicazione.comandi.EseguiProssimaElaborazioneServizio
import snastro.trascrizione.applicazione.comandi.PortePipeline
import snastro.trascrizione.applicazione.comandi.RecuperaElaborazioniInterrotte
import snastro.trascrizione.applicazione.comandi.RecuperaElaborazioniInterrotteServizio
import snastro.trascrizione.applicazione.comandi.RiassegnaSegmentoServizio
import snastro.trascrizione.applicazione.comandi.UnisciVociServizio
import snastro.trascrizione.applicazione.letture.FasiInCorso
import snastro.trascrizione.applicazione.letture.StatiElaborazione
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
 * - ONE [FasiInCorso] per open project, written by the pipeline (through
 *   [SegnalatoreFaseConCambiamenti]) and read by [StatiElaborazione] (AC-353); every phase change and
 *   every Elaborazione/Revisione event is a `Cambiamento` ([AggiornamentiVistaTrascrizione], AC-354).
 * - No synchronous subscriber at all, in particular none on `RegistrazioneAggiunta`: importing never
 *   starts an Elaborazione (ADR 0014, AC-371). `AbbonatoDocumentoEventi` registers itself after-commit
 *   and runs on [io] (never on the UI thread), in a child of the session scope.
 * - The ML adapters' per-Elaborazione memory is released when each run terminates
 *   ([SegnalatoreFaseConRilascio], ADR 0004).
 * - The Documento reads names from [LettoreNomiVuoto] ('Voce n', AC-356): no `:parlanti` class.
 * - The [CodaElaborazioni] is built LAST: its construction runs `RecuperaElaborazioniInterrotte`
 *   strictly before its worker picks any FIFO head (AC-233), and only after every after-commit
 *   subscriber above is registered (a recovered `fallita` still refreshes S2 and the Documento).
 */
@Suppress("LongParameterList") // one parameter per app-wide collaborator of the per-project graph
internal class EstensioneR1(
    private val io: CoroutineDispatcher,
    private val clock: Clock,
    private val generatoreId: GeneratoreId,
    private val ml: AdattatoriMl,
    private val modelliPronti: () -> Boolean,
    private val decodificatore: (Path) -> DecodificatoreAudio = ::DecodificatoreAudioFfmpeg,
) : EstensioneSessione {
    override fun apri(contesto: ContestoEstensione): ProgettoEsteso {
        val dispatcher = contesto.dispatcher
        val uow = dispatcher.unitaDiLavoro
        val elaborazioni = ElaborazioneRepositorySql(contesto.database)
        val trascritti = TrascrittoRepositorySql(contesto.database)
        val catalogo = CatalogoRegistrazioni(contesto.registrazioni)
        val lettoreRegistrazione = LettoreRegistrazioneDaProgetto(catalogo)
        val fasi = FasiInCorso()
        val aggiornamenti = AggiornamentiVistaTrascrizione(dispatcher)

        val lavoroDocumento = avviaRigenerazioneDocumento(contesto, trascritti, catalogo)

        val pipeline = PortePipeline(
            registrazioni = lettoreRegistrazione,
            decodificatore = decodificatore(contesto.cartella),
            diarizzatore = ml.diarizzatore,
            allineatore = AllineatorePerTurno(ml.riconoscitore, ml.vad),
            segnalatore = SegnalatoreFaseConRilascio(
                SegnalatoreFaseConCambiamenti(fasi, aggiornamenti::cambiata),
                ml.rilasciaDopoElaborazione,
            ),
        )
        val esegui = EseguiProssimaElaborazioneServizio(uow, clock, elaborazioni, trascritti, pipeline, dispatcher)
        val recupera = RecuperaElaborazioniInterrotteServizio(uow, elaborazioni, dispatcher)
        val coda = CodaElaborazioni(
            scope = contesto.scope,
            fonte = fonteAvanzamento(esegui),
            recuperaElaborazioniInterrotte = {
                val esito = recupera.esegui(RecuperaElaborazioniInterrotte)
                if (esito is Esito.Errore) log.warning("recupero delle elaborazioni interrotte fallito: $esito")
            },
            modelliPronti = modelliPronti,
            segnalaElaborazioneBloccata = { id -> log.warning("elaborazione $id esclusa dalla coda") },
        )

        val stati = StatiElaborazione(elaborazioni, trascritti, fasi)
        val trascrittoQuery = TrascrittoQuery(trascritti, lettoreRegistrazione)
        return CollaboratoriR1(
            statiElaborazione = stati::stati,
            avvia = AvviaElaborazioneServizio(uow, generatoreId, clock, lettoreRegistrazione, elaborazioni)::esegui,
            trascritto = trascrittoQuery::vista,
            percorsoDocumento = { id -> percorsoDocumento(contesto.cartella, catalogo, id) },
            revisione = ComandiRevisione(
                UnisciVociServizio(uow, trascritti, dispatcher),
                DividiVoceServizio(uow, trascritti, dispatcher),
                RiassegnaSegmentoServizio(uow, trascritti, dispatcher),
            ),
            coda = coda,
            lavoroDocumento = lavoroDocumento,
            aggiornamenti = aggiornamenti,
        )
    }

    /**
     * `AbbonatoDocumentoEventi` (after-commit, startup sweep) on its own child of the session scope, on
     * [io] — never the UI thread; returns that child's [Job], which [CollaboratoriR1.ferma] joins.
     */
    private fun avviaRigenerazioneDocumento(
        contesto: ContestoEstensione,
        trascritti: TrascrittoRepositorySql,
        catalogo: CatalogoRegistrazioni,
    ): Job {
        val lavoro = SupervisorJob(contesto.scope.coroutineContext[Job])
        AbbonatoDocumentoEventi(
            contesto.dispatcher,
            RigenerazioneDocumentoPolitica(
                LettoreTrascrittoDaTrascrizione(VociDelTrascritto(trascritti), catalogo),
                LettoreNomiVuoto,
                ScrittoreDocumentoFile(contesto.cartella.resolve(CARTELLA_DOCUMENTI)),
            ),
            CoroutineScope(contesto.scope.coroutineContext + lavoro + io),
        )
        return lavoro
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
