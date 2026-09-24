package snastro.avvio.r1

import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import snastro.avvio.CodaElaborazioni
import snastro.avvio.ProgettoEsteso
import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.trascrizione.applicazione.comandi.AvviaElaborazione
import snastro.trascrizione.applicazione.letture.StatoRegistrazioneVista
import snastro.trascrizione.applicazione.letture.TrascrittoView
import snastro.ui.AggiornamentiVista
import java.util.logging.Logger

/**
 * The R1 (Trascrizione) collaborators of ONE open project, built by [EstensioneR1]: the Trascrizione
 * sources S2 needs (AC-355: [statiElaborazione], [avviaElaborazione]), the read-only S3 sources
 * ([trascritto], [percorsoDocumento]), the Revisione commands (no UI in R1), and the two background
 * workers [ferma] waits for: the Elaborazione queue ([coda]) and the Documento regeneration
 * ([lavoroDocumento], the job `AbbonatoDocumentoEventi` runs under).
 */
@Suppress("LongParameterList") // one parameter per per-project collaborator
internal class CollaboratoriR1(
    val statiElaborazione: (List<RegistrazioneId>) -> List<StatoRegistrazioneVista>,
    private val avvia: (AvviaElaborazione) -> Esito<Unit>,
    val trascritto: (RegistrazioneId) -> TrascrittoView?,
    val percorsoDocumento: (RegistrazioneId) -> String?,
    val revisione: ComandiRevisione,
    val coda: CodaElaborazioni,
    private val lavoroDocumento: Job,
    override val aggiornamenti: AggiornamentiVista,
) : ProgettoEsteso {
    /** 'Trascrivi'/'Riprova' (ADR 0014): enqueues, then nudges the queue so the run starts at once. */
    fun avviaElaborazione(comando: AvviaElaborazione): Esito<Unit> =
        avvia(comando).also { if (it is Esito.Ok) coda.avanza() }

    /**
     * Carry-over 1: called after the session scope was cancelled (which already cancelled both
     * workers), before the database closes — waits for each to actually unwind, bounded.
     */
    override fun ferma() {
        if (!coda.fermaEAttendi(TIMEOUT_ARRESTO_MS)) log.warning("la coda non si e' fermata in tempo")
        val fermato = runBlocking { withTimeoutOrNull(TIMEOUT_ARRESTO_MS) { lavoroDocumento.join() } != null }
        if (!fermato) log.warning("la rigenerazione del documento non si e' fermata in tempo")
    }

    private companion object {
        const val TIMEOUT_ARRESTO_MS = 5_000L
        val log: Logger = Logger.getLogger(CollaboratoriR1::class.java.name)
    }
}
