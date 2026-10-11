package snastro.ui.registrazione

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import snastro.kernel.Esito
import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.trascrizione.applicazione.comandi.ConfermaSegmento
import snastro.trascrizione.applicazione.comandi.DividiVoce
import snastro.trascrizione.applicazione.comandi.RiassegnaSegmento
import snastro.trascrizione.applicazione.comandi.UnisciVoci
import snastro.trascrizione.applicazione.letture.TrascrittoView
import snastro.ui.comandoConfermato
import snastro.ui.eccezioneRegistrata
import snastro.ui.nonFataleRegistrato
import snastro.ui.testi.MESSAGGIO_ERRORE_GENERICO
import snastro.ui.testi.SPIEGAZIONE_DIVIDI_INTERA_VOCE
import snastro.ui.testi.messaggioPer
import java.time.Instant

/**
 * The selection toolbar of S3's Voci panel and what it starts, owned by [StatoVoci]: the Revisione (AC-209..211,
 * AC-216, AC-404, AC-528) and "Dai un nome a questa frase" (ADR 0019 §5, AC-526..AC-529). One holder, because both
 * read the same [selezione] and the same [revisioneInCorso].
 *
 * The owner's state comes in as functions: [vista]/[dati]/[modificheBloccate] are read live, every command runs
 * through the owner's one runner [lanciaComando], and a re-read goes through [ricaricaDopoRevisione], which sets the
 * selection back through [riallinea]. Mutated only on [scope]'s (UI) dispatcher, like [StatoVoci].
 */
@Suppress("LongParameterList", "TooManyFunctions") // one parameter per callback of StatoVoci; one per toolbar action
internal class RevisioneVoci(
    private val sorgenti: SorgentiParlanti,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val registrazioneId: RegistrazioneId,
    private val trascritto: () -> TrascrittoView?,
    private val vista: () -> TrascrittoView?,
    private val dati: () -> DatiParlanti?,
    private val modificheBloccate: () -> Boolean,
    private val lanciaComando: (operazione: String, libera: () -> Unit, comando: suspend () -> Unit) -> Unit,
    private val ricaricaDopoRevisione: suspend (azzeraSelezione: Boolean) -> Unit,
    private val pubblica: () -> Unit,
    private val impostaErrore: (String) -> Unit,
    private val programmaSoglia: (ref: Any, inizio: Instant) -> Unit,
) {
    /** [esito] `null`: a failure with no domain error, shown as the generic message. */
    private class RisultatoRevisione(val esito: Esito<Unit>?, val modificato: Boolean)

    var selezione: Set<SegmentoId> = emptySet()
        private set

    /** One Revisione at a time (no double submit); the toolbar and the merge banner are disabled meanwhile. */
    var revisioneInCorso = false
        private set

    private val inviiFrasi = mutableMapOf<SegmentoId, Instant>()
    private var frasiAltrove: Map<SegmentoId, Instant> = emptyMap()

    /** AC-530: a naming is pending for this Registrazione, sent from here or from an earlier S3 visit. */
    val frasiPendenti: Boolean
        get() = inviiFrasi.isNotEmpty() || frasiAltrove.isNotEmpty()

    private val incontroId: IncontroId?
        get() = vista()?.incontroId

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

    // --- selection + Revisione (AC-209..211, AC-216, AC-404) ----------------------------------------

    /** AC-209: the selection stays inside ONE Voce — a Segmento of another Voce starts a new selection. */
    fun selezionaSegmento(id: SegmentoId) {
        val v = vista() ?: return
        val voce = v.segmenti.find { it.segmentoId == id }?.voceId ?: return
        selezione = when {
            voceDellaSelezione(v) != voce -> setOf(id)
            id in selezione -> selezione - id
            else -> selezione + id
        }
        pubblica()
    }

    fun deseleziona() {
        selezione = emptySet()
        pubblica()
    }

    /**
     * After a re-read of [nuova]: [azzera] (a success) empties the selection, else it keeps the Segmenti that still
     * exist — unless they now belong to more than one Voce.
     */
    fun riallinea(nuova: TrascrittoView, azzera: Boolean) {
        val esistenti = nuova.segmenti.filter { it.segmentoId in selezione }
        selezione = if (azzera || esistenti.map { it.voceId }.distinct().size > 1) {
            emptySet()
        } else {
            esistenti.map { it.segmentoId }.toSet()
        }
    }

    /** AC-210: only a proper subset of the Voce can be split off (INV-10). */
    fun dividiVoce() {
        val barra = vista()?.let(::barraDi)?.takeIf { it.dividiAbilitato && it.abilitata } ?: return
        val segmenti = selezione
        val operazione = "dividi ${barra.voceId}"
        eseguiRevisione(operazione) {
            val esito = comandoConfermato(operazione) {
                sorgenti.dividi(DividiVoce(registrazioneId, barra.voceId, segmenti, incontroDelleVoci = incontroId))
            }
            RisultatoRevisione(esito, esito is Esito.Ok)
        }
    }

    /** AC-211: RiassegnaSegmento per Segmento, to [destinazione] or (`null`) ONE new Voce for the whole selection. */
    fun riassegnaA(destinazione: VoceId?) {
        val v = vista() ?: return
        val barra = barraDi(v)?.takeIf { it.abilitata && it.voceId != destinazione } ?: return
        val ordinati = v.segmenti
            .filter { it.segmentoId in selezione && it.voceId == barra.voceId }
            .map { it.segmentoId }
        eseguiRevisione("riassegna a ${destinazione ?: "nuova voce"}") { riassegnaTutti(ordinati, destinazione) }
    }

    /**
     * L274: a failure after Segmenti already moved (committed one by one) still reports them as changed, so the
     * transcript is re-read; an Error still propagates (D-0065). 'nuova voce' whose Voce cannot be read back after the
     * first move stops there (generic message) instead of opening one new Voce per Segmento.
     */
    private fun riassegnaTutti(segmenti: List<SegmentoId>, destinazione: VoceId?): RisultatoRevisione {
        var verso = destinazione
        var esito: Esito<Unit>? = Esito.Ok(Unit)
        var spostati = 0
        val restanti = segmenti.iterator()
        while (esito is Esito.Ok && restanti.hasNext()) {
            val segmento = restanti.next()
            esito = riassegnaUno(segmento, verso)
            if (esito is Esito.Ok) {
                spostati++
                // 'nuova voce': the first Segmento opens it, the next ones follow it there (one Voce, not one each).
                val apri = verso == null && restanti.hasNext()
                if (apri) verso = voceDi(segmento)
                if (apri && verso == null) esito = null
            }
        }
        return RisultatoRevisione(esito, modificato = spostati > 0)
    }

    /** One RiassegnaSegmento; a thrown `Exception` is logged and `null` (the generic message); an Error propagates. */
    private fun riassegnaUno(segmento: SegmentoId, verso: VoceId?): Esito<Unit>? =
        eccezioneRegistrata("riassegna $segmento") {
            comandoConfermato("riassegna $segmento") {
                sorgenti.riassegna(RiassegnaSegmento(registrazioneId, segmento, verso, incontroDelleVoci = incontroId))
            }
        }

    private fun voceDi(segmento: SegmentoId): VoceId? =
        nonFataleRegistrato("rileggi la voce di $segmento") {
            trascritto()?.segmenti?.find { it.segmentoId == segmento }?.voceId
        }

    /** 'Unisci con ▾' on a card ([sopravvive] = that card) and the merge banner's 'Unisci' (AC-216). */
    fun unisci(sopravvive: VoceId, rimossa: VoceId) {
        if (sopravvive == rimossa) return
        val operazione = "unisci $rimossa in $sopravvive"
        eseguiRevisione(operazione) {
            val esito = comandoConfermato(operazione) {
                sorgenti.unisci(UnisciVoci(registrazioneId, sopravvive, rimossa, incontroDelleVoci = incontroId))
            }
            RisultatoRevisione(esito, esito is Esito.Ok)
        }
    }

    /** AC-528: 'Togli conferma' → `ConfermaSegmento(false)` on the ONE selected, confirmed Segmento. */
    fun togliConferma() {
        val menu = vista()?.let(::barraDi)?.frase?.takeIf { it.abilitata && it.confermato } ?: return
        val operazione = "togli conferma ${menu.segmentoId}"
        eseguiRevisione(operazione) {
            val comando =
                ConfermaSegmento(registrazioneId, menu.segmentoId, confermato = false, incontroDelleVoci = incontroId)
            val esito = comandoConfermato(operazione) { sorgenti.confermaSegmento(comando) }
            RisultatoRevisione(esito, esito is Esito.Ok)
        }
    }

    /**
     * AC-404: a Revisione error is an inline message in the transcript — the transcript and the selection
     * stay as they were (only a partly applied multi-Segmento Riassegna re-reads what did change). A
     * success re-reads the transcript and the panel (the merge banner disappears on its own, AC-216). A command that
     * committed but whose after-commit follow-up failed is a success (D-0062, [comandoConfermato]).
     * One at a time (no double submit); never blocked by a pending card command (AC-414).
     */
    private fun eseguiRevisione(operazione: String, blocco: () -> RisultatoRevisione) {
        // AC-454: dividiVoce/riassegnaA/unisci (the merge banner's action included) send no command
        // while read-only — the view already disables their controls, this is the presenter's own
        // backstop (unisci() in particular has no `abilitata` guard of its own to rely on).
        if (modificheBloccate() || revisioneInCorso) return
        revisioneInCorso = true
        lanciaComando(operazione, { revisioneInCorso = false }) {
            val risultato = withContext(io) { blocco() }
            if (risultato.modificato) ricaricaDopoRevisione(risultato.esito is Esito.Ok)
            when (val esito = risultato.esito) {
                is Esito.Ok -> Unit
                is Esito.Errore -> impostaErrore(messaggioPer(esito.errore))
                null -> impostaErrore(MESSAGGIO_ERRORE_GENERICO)
            }
        }
    }

    // --- "Dai un nome a questa frase" (ADR 0019 §5) -------------------------------------------------

    /**
     * AC-526/AC-527/AC-529: the case decision ([passiNominaFrase], pure) then ONE `nominaFrase` through the
     * per-project [ComandiVoce] — the presenter never calls a command itself. The row goes pending at once;
     * whatever the outcome the transcript and the panel are re-read (a first step may have committed: e.g.
     * the new Voce of case (d) stays unnamed when naming it fails, NomeGiaInUso shown inline).
     */
    fun nominaFrase(obiettivo: ObiettivoNome) {
        val v = vista()
        val segmento = v?.let(::barraDi)?.frase?.takeIf { it.abilitata }?.segmentoId
        val passi = segmento?.let { passiNominaFrase(it, checkNotNull(v), dati()?.identificate.orEmpty(), obiettivo) }
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

    // --- toolbar view ------------------------------------------------------------------------------

    /** AC-529: the naming of [segmento] in progress, then 'In attesa…' past the threshold. */
    fun attesaFraseDi(segmento: SegmentoId): AttesaComando? =
        attesaDa(inviiFrasi[segmento] ?: frasiAltrove[segmento], sorgenti.clock.instant())

    fun barraDi(v: TrascrittoView): BarraSelezione? {
        val voceId = voceDellaSelezione(v) ?: return null
        val interaVoce = v.segmenti.filter { it.voceId == voceId }.all { it.segmentoId in selezione }
        val nomeDi = { id: VoceId -> dati()?.nomeDi(id) }
        return BarraSelezione(
            voceId = voceId,
            etichetta = etichettaDiVoce(v, voceId, nomeDi(voceId)),
            numeroSegmenti = selezione.size,
            dividiAbilitato = !interaVoce,
            spiegazioneDividi = if (interaVoce) SPIEGAZIONE_DIVIDI_INTERA_VOCE else null,
            destinazioni = opzioniDi(v, nomeDi).filter { it.voceId != voceId },
            // AC-454: 'Riassegna a ▾'/'Dividi voce' disabled while read-only (and during a similarity run).
            abilitata = !revisioneInCorso && !modificheBloccate(),
            frase = if (selezione.size == 1) menuFrase(v, selezione.single()) else null,
        )
    }

    /** AC-526/AC-528/AC-529: the naming menu of the ONE selected Segmento. */
    private fun menuFrase(v: TrascrittoView, segmento: SegmentoId): MenuFrase {
        val d = dati()
        return MenuFrase(
            segmentoId = segmento,
            parlanti = d?.attivi.orEmpty(),
            confermato = v.segmenti.find { it.segmentoId == segmento }?.confermato == true,
            abilitata = !revisioneInCorso && !modificheBloccate() && d != null && attesaFraseDi(segmento) == null,
        )
    }

    private fun voceDellaSelezione(v: TrascrittoView): VoceId? =
        v.segmenti.firstOrNull { it.segmentoId in selezione }?.voceId
}
