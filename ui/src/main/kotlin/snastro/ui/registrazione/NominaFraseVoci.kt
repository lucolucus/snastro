package snastro.ui.registrazione

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.trascrizione.applicazione.letture.TrascrittoView
import snastro.ui.testi.messaggioPer
import java.time.Instant

/**
 * "Dai un nome a questa frase" on S3's selection toolbar (ADR 0019 §5, AC-526..AC-529), owned by [StatoVoci]: it
 * sends the naming of the ONE selected Segmento ([fraseSelezionata], the toolbar's [MenuFrase] read live off
 * [RevisioneVoci]) and mirrors the pending namings of [registrazioneId], sent from here or from an earlier S3 visit.
 *
 * The owner's state comes in as functions, like [RevisioneVoci]: the command runs through the owner's one runner
 * [lanciaComando] and the re-read goes through [ricaricaDopoRevisione]. Mutated only on [scope]'s (UI) dispatcher.
 */
@Suppress("LongParameterList") // one parameter per collaborator/callback of the owning StatoVoci
internal class NominaFraseVoci(
    private val sorgenti: SorgentiParlanti,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val registrazioneId: RegistrazioneId,
    private val vista: () -> TrascrittoView?,
    private val dati: () -> DatiParlanti?,
    private val fraseSelezionata: () -> MenuFrase?,
    private val lanciaComando: (operazione: String, libera: () -> Unit, comando: suspend () -> Unit) -> Unit,
    private val ricaricaDopoRevisione: suspend (azzeraSelezione: Boolean) -> Unit,
    private val pubblica: () -> Unit,
    private val impostaErrore: (String) -> Unit,
    private val programmaSoglia: (ref: Any, inizio: Instant) -> Unit,
) {
    private val inviiFrasi = mutableMapOf<SegmentoId, Instant>()
    private var frasiAltrove: Map<SegmentoId, Instant> = emptyMap()

    /** AC-530: a naming is pending for this Registrazione, sent from here or from an earlier S3 visit. */
    val frasiPendenti: Boolean
        get() = inviiFrasi.isNotEmpty() || frasiAltrove.isNotEmpty()

    fun avvia() {
        scope.launch { sorgenti.comandi.statoFrasi.collect { mappa -> riflettiFrasi(mappa) } }
    }

    /** AC-529/AC-415: the pending namings of this Registrazione, as [StatoVoci] does for the cards. */
    private suspend fun riflettiFrasi(mappa: Map<FraseRef, StatoComando>) {
        val mie = mappa.entries.filter { it.key.registrazioneId == registrazioneId }
            .associate { it.key.segmentoId to it.value.avviatoAlle }
        val conclusiAltrove = frasiAltrove.keys - mie.keys - inviiFrasi.keys
        frasiAltrove = mie
        mie.forEach { (segmento, inizio) -> programmaSoglia(FraseRef(registrazioneId, segmento), inizio) }
        pubblica()
        if (conclusiAltrove.isNotEmpty() && vista() != null) ricaricaDopoRevisione(false)
    }

    /**
     * AC-526/AC-527/AC-529: the case decision ([passiNominaFrase], pure) then ONE `nominaFrase` through the
     * per-project [ComandiVoce] — the presenter never calls a command itself. The row goes pending at once;
     * whatever the outcome the transcript and the panel are re-read (a first step may have committed: e.g.
     * the new Voce of case (d) stays unnamed when naming it fails, NomeGiaInUso shown inline).
     */
    fun nominaFrase(obiettivo: ObiettivoNome) {
        val segmento = fraseSelezionata()?.takeIf { it.abilitata }?.segmentoId
        val passi = segmento?.let {
            passiNominaFrase(it, checkNotNull(vista()), dati()?.identificate.orEmpty(), obiettivo)
        }
        if (segmento == null || passi == null) return
        val inizio = sorgenti.clock.instant()
        inviiFrasi[segmento] = inizio
        programmaSoglia(FraseRef(registrazioneId, segmento), inizio)
        lanciaComando("nomina la frase $segmento", { inviiFrasi.remove(segmento) }) {
            val esito = withContext(io) { sorgenti.comandi.nominaFrase(registrazioneId, segmento, passi) }
            ricaricaDopoRevisione(esito is Esito.Ok)
            (esito as? Esito.Errore)?.let { impostaErrore(messaggioPer(it.errore)) }
        }
    }

    /** AC-529: 'Annulla' on a naming past the threshold — the steps not yet run are skipped. */
    fun annullaFrase(segmento: SegmentoId) = sorgenti.comandi.annullaFrase(FraseRef(registrazioneId, segmento))

    /** AC-529: the naming of [segmento] in progress, then 'In attesa…' past the threshold. */
    fun attesaFraseDi(segmento: SegmentoId): AttesaComando? =
        attesaDa(inviiFrasi[segmento] ?: frasiAltrove[segmento], sorgenti.clock.instant())
}
