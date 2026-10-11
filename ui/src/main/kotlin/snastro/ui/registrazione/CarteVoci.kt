package snastro.ui.registrazione

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import snastro.kernel.Esito
import snastro.kernel.ParlanteId
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.parlanti.applicazione.eventi.TipoParlanteVista
import snastro.trascrizione.applicazione.letture.TrascrittoView
import snastro.ui.testi.MESSAGGIO_ERRORE_GENERICO
import snastro.ui.testi.messaggioPer
import java.time.Instant

/**
 * The card commands of S3's Voci panel (ADR 0017 §3, AC-411..AC-415), owned by [StatoVoci]: 'Conferma', 'altri ▾',
 * 'nuovo…', 'salta', 'cambia' and their 'Annulla', through the per-project [ComandiVoce], and the pending commands
 * of this Incontro, sent from here or from an earlier S3 visit.
 *
 * The owner's state comes in as functions, like [RevisioneVoci]: a command runs through the owner's one runner
 * [lanciaComando] and its success re-reads the panel through [ricaricaParlanti]. Mutated only on [scope]'s (UI)
 * dispatcher.
 */
@Suppress("LongParameterList") // one parameter per collaborator/callback of the owning StatoVoci
internal class CarteVoci(
    private val sorgenti: SorgentiParlanti,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val vista: () -> TrascrittoView?,
    private val cartaDi: (VoceId) -> CartaVoce?,
    private val lanciaComando: (
        operazione: String,
        libera: () -> Unit,
        suGuasto: () -> Unit,
        comando: suspend () -> Unit,
    ) -> Unit,
    private val ricaricaParlanti: suspend () -> Unit,
    private val pubblica: () -> Unit,
    private val programmaSoglia: (ref: Any, inizio: Instant) -> Unit,
) {
    private val invii = mutableMapOf<VoceRef, Instant>()
    private var inCorsoAltrove: Map<VoceRef, Instant> = emptyMap()
    private val erroriCarta = mutableMapOf<VoceId, String>()

    /** AC-414: the outcome message shown on each card. */
    val errori: Map<VoceId, String>
        get() = erroriCarta

    /** AC-530: a card command is pending for this Incontro. */
    val pendenti: Boolean
        get() = invii.isNotEmpty() || inCorsoAltrove.isNotEmpty()

    /**
     * AC-415: the per-project pending state. A command that ended while THIS presenter did not send it
     * (sent by an earlier S3 visit) → re-read the panel, so its outcome (the Nome) shows here too.
     */
    fun avvia() {
        scope.launch {
            sorgenti.comandi.stato.collect { mappa ->
                val conclusiAltrove = riflettiInCorso(mappa)
                pubblica()
                if (conclusiAltrove.isNotEmpty() && vista() != null) ricaricaParlanti()
            }
        }
    }

    /** Keeps this Incontro's pending commands of [mappa]; returns those that ended while not sent from here. */
    fun riflettiInCorso(mappa: Map<VoceRef, StatoComando>): Set<VoceRef> {
        val incontroId = vista()?.incontroId
        val mie = mappa.filterKeys { it.incontroId == incontroId }.mapValues { it.value.avviatoAlle }
        val conclusiAltrove = inCorsoAltrove.keys - mie.keys - invii.keys
        inCorsoAltrove = mie
        mie.forEach { (ref, inizio) -> programmaSoglia(ref, inizio) }
        return conclusiAltrove
    }

    /** AC-412: the card's command in progress, then 'In attesa…' past the threshold. */
    fun attesaDi(ref: VoceRef): AttesaComando? = attesaDa(invii[ref] ?: inCorsoAltrove[ref], sorgenti.clock.instant())

    /** 'Conferma': the top Candidato of the Proposta. */
    fun conferma(voceId: VoceId) {
        val carta = cartaDi(voceId)?.takeIf { it.confermaAbilitata } ?: return
        val primo = ((carta.contenuto as? ContenutoCarta.DaIdentificare)?.proposta as? StatoProposta.Pronta)
            ?.candidati?.firstOrNull() ?: return
        invia(voceId) { ComandoVoce.Conferma(it, primo.parlanteId) }
    }

    /** 'altri ▾ → Conferma' on a card to identify, 'cambia' on an attributed one. */
    fun confermaParlante(voceId: VoceId, parlanteId: ParlanteId) =
        invia(voceId) { ComandoVoce.Conferma(it, parlanteId) }

    /** 'nuovo…' (also from 'cambia'): a new Parlante; the Nome rules are the domain's (AC-215). */
    fun nuovoParlante(voceId: VoceId, nome: String, tipo: TipoParlanteVista) =
        invia(voceId) { ComandoVoce.Nuovo(it, nome, tipo) }

    /** 'salta' — AC-219: never on an attributed Voce ('cambia' there). */
    fun salta(voceId: VoceId) {
        if (cartaDi(voceId)?.contenuto !is ContenutoCarta.DaIdentificare) return
        invia(voceId) { ComandoVoce.Salta(it) }
    }

    /** AC-413: 'Annulla' — cancels the pending command; not a domain command, never an error. */
    fun annulla(voceId: VoceId) = sorgenti.comandi.annulla(voceRef(vista(), voceId))

    fun chiudiErrore(voceId: VoceId) {
        erroriCarta.remove(voceId)
        pubblica()
    }

    /**
     * AC-411: the card goes pending AT ONCE (published before the command even starts — a second click
     * finds its actions disabled); AC-414: the outcome is shown like any command's; AC-413: a cancelled
     * one (`null`) restores the card with no message; a thrown one is the generic message on the card.
     */
    private fun invia(voceId: VoceId, comando: (VoceRef) -> ComandoVoce) {
        // AC-454: 'Conferma'/'altri ▾'/'nuovo…'/'salta'/'cambia' send no command while read-only —
        // `azioniAbilitate` already folds in `soloLettura` (CartaVoce, built by StatoVoci's pannelloDi).
        if (cartaDi(voceId)?.azioniAbilitate != true) return
        val ref = voceRef(vista(), voceId)
        val inizio = sorgenti.clock.instant()
        invii[ref] = inizio
        erroriCarta.remove(voceId)
        programmaSoglia(ref, inizio)
        lanciaComando(
            "comando sulla voce $voceId",
            { invii.remove(ref) },
            { erroriCarta[voceId] = MESSAGGIO_ERRORE_GENERICO },
        ) {
            when (val esito = withContext(io) { sorgenti.comandi.esegui(comando(ref)) }) {
                null -> Unit
                is Esito.Ok -> ricaricaParlanti()
                is Esito.Errore -> erroriCarta[voceId] = messaggioPer(esito.errore)
            }
        }
    }
}
