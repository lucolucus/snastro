package snastro.ui.registrazione

import kotlinx.coroutines.CoroutineDispatcher
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

/**
 * The selection toolbar of S3's Voci panel and the Revisione it starts (AC-209..211, AC-216, AC-404, AC-528), owned
 * by [StatoVoci]. The toolbar's naming menu reads the pending namings of [NominaFraseVoci] through [attesaFraseDi].
 *
 * The owner's state comes in as functions: [vista]/[dati]/[modificheBloccate] are read live, every command runs
 * through the owner's one runner [lanciaComando], and a re-read goes through [ricaricaDopoRevisione], which sets the
 * selection back through [riallinea]. Mutated only on the (UI) dispatcher of [StatoVoci]'s scope, like [StatoVoci].
 */
@Suppress("LongParameterList") // one parameter per collaborator/callback of the owning StatoVoci
internal class RevisioneVoci(
    private val sorgenti: SorgentiParlanti,
    private val io: CoroutineDispatcher,
    private val registrazioneId: RegistrazioneId,
    /** Repository read: call only on [io]. */
    private val trascritto: () -> TrascrittoView?,
    private val vista: () -> TrascrittoView?,
    private val dati: () -> DatiParlanti?,
    private val modificheBloccate: () -> Boolean,
    private val attesaFraseDi: (SegmentoId) -> AttesaComando?,
    private val lanciaComando: (operazione: String, libera: () -> Unit, comando: suspend () -> Unit) -> Unit,
    private val ricaricaDopoRevisione: suspend (azzeraSelezione: Boolean) -> Unit,
    private val pubblica: () -> Unit,
    private val impostaErrore: (String) -> Unit,
) {
    var selezione: Set<SegmentoId> = emptySet()
        private set

    /** One Revisione at a time (no double submit); the toolbar and the merge banner are disabled meanwhile. */
    var revisioneInCorso = false
        private set

    private val incontroId: IncontroId?
        get() = vista()?.incontroId

    /** AC-209: the selection stays inside ONE Voce — a Segmento of another Voce starts a new selection. */
    fun selezionaSegmento(id: SegmentoId) {
        val v = vista() ?: return
        val voce = v.segmenti.find { it.segmentoId == id }?.voceId ?: return
        selezione = when {
            voceDellaSelezione(v, selezione) != voce -> setOf(id)
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
        val riassegnazione = Riassegnazione(sorgenti, registrazioneId, { incontroId }, trascritto)
        eseguiRevisione("riassegna a ${destinazione ?: "nuova voce"}") {
            riassegnaTutti(riassegnazione, ordinati, destinazione)
        }
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

    /** The toolbar over the selection; with ONE Segmento selected, its naming menu (AC-526/AC-528/AC-529). */
    fun barraDi(v: TrascrittoView): BarraSelezione? {
        val voceId = voceDellaSelezione(v, selezione) ?: return null
        val interaVoce = v.segmenti.filter { it.voceId == voceId }.all { it.segmentoId in selezione }
        val d = dati()
        val nomeDi = { id: VoceId -> d?.nomeDi(id) }
        // AC-454: 'Riassegna a ▾'/'Dividi voce' disabled while read-only (and during a similarity run).
        val abilitata = !revisioneInCorso && !modificheBloccate()
        return BarraSelezione(
            voceId = voceId,
            etichetta = etichettaDiVoce(v, voceId, nomeDi(voceId)),
            numeroSegmenti = selezione.size,
            dividiAbilitato = !interaVoce,
            spiegazioneDividi = if (interaVoce) SPIEGAZIONE_DIVIDI_INTERA_VOCE else null,
            destinazioni = opzioniDi(v, nomeDi).filter { it.voceId != voceId },
            abilitata = abilitata,
            frase = selezione.singleOrNull()?.let { segmento ->
                menuFrase(v, segmento, d, libera = abilitata && attesaFraseDi(segmento) == null)
            },
        )
    }
}

/** [esito] `null`: a failure with no domain error, shown as the generic message. */
private class RisultatoRevisione(val esito: Esito<Unit>?, val modificato: Boolean)

/** What a multi-Segmento Riassegna writes through and reads back from: [incontroId] live, [trascritto] on `io`. */
private class Riassegnazione(
    val sorgenti: SorgentiParlanti,
    val registrazioneId: RegistrazioneId,
    val incontroId: () -> IncontroId?,
    val trascritto: () -> TrascrittoView?,
)

/**
 * L274: a failure after Segmenti already moved (committed one by one) still reports them as changed, so the
 * transcript is re-read; an Error still propagates (D-0065). 'nuova voce' whose Voce cannot be read back after the
 * first move stops there (generic message) instead of opening one new Voce per Segmento. Runs on `io`.
 */
private fun riassegnaTutti(r: Riassegnazione, segmenti: List<SegmentoId>, destinazione: VoceId?): RisultatoRevisione {
    var verso = destinazione
    var esito: Esito<Unit>? = Esito.Ok(Unit)
    var spostati = 0
    val restanti = segmenti.iterator()
    while (esito is Esito.Ok && restanti.hasNext()) {
        val segmento = restanti.next()
        esito = riassegnaUno(r, segmento, verso)
        if (esito is Esito.Ok) {
            spostati++
            // 'nuova voce': the first Segmento opens it, the next ones follow it there (one Voce, not one each).
            val apri = verso == null && restanti.hasNext()
            if (apri) verso = voceDi(r, segmento)
            if (apri && verso == null) esito = null
        }
    }
    return RisultatoRevisione(esito, modificato = spostati > 0)
}

/** One RiassegnaSegmento; a thrown `Exception` is logged and `null` (the generic message); an Error propagates. */
private fun riassegnaUno(r: Riassegnazione, segmento: SegmentoId, verso: VoceId?): Esito<Unit>? =
    eccezioneRegistrata("riassegna $segmento") {
        val comando = RiassegnaSegmento(r.registrazioneId, segmento, verso, incontroDelleVoci = r.incontroId())
        comandoConfermato("riassegna $segmento") { r.sorgenti.riassegna(comando) }
    }

private fun voceDi(r: Riassegnazione, segmento: SegmentoId): VoceId? =
    nonFataleRegistrato("rileggi la voce di $segmento") {
        r.trascritto()?.segmenti?.find { it.segmentoId == segmento }?.voceId
    }

/** AC-526/AC-528/AC-529: the naming menu of the ONE selected Segmento, usable when [libera] and the roster is read. */
private fun menuFrase(v: TrascrittoView, segmento: SegmentoId, d: DatiParlanti?, libera: Boolean) = MenuFrase(
    segmentoId = segmento,
    parlanti = d?.attivi.orEmpty(),
    confermato = v.segmenti.find { it.segmentoId == segmento }?.confermato == true,
    abilitata = libera && d != null,
)

private fun voceDellaSelezione(v: TrascrittoView, selezione: Set<SegmentoId>): VoceId? =
    v.segmenti.firstOrNull { it.segmentoId in selezione }?.voceId
