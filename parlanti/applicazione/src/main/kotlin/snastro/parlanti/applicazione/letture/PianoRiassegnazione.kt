package snastro.parlanti.applicazione.letture

import snastro.kernel.Esito
import snastro.kernel.IntervalloMs
import snastro.kernel.ParlanteId
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.parlanti.applicazione.porte.AttribuzioneRepository
import snastro.parlanti.applicazione.porte.ClassificatoreSomiglianza
import snastro.parlanti.applicazione.porte.Classificazione
import snastro.parlanti.applicazione.porte.DecodificatoreAudio
import snastro.parlanti.applicazione.porte.EstrattoreImpronta
import snastro.parlanti.applicazione.porte.LettoreVoci
import snastro.parlanti.applicazione.porte.ParlanteRepository
import snastro.parlanti.applicazione.porte.SegmentoDiVoce
import snastro.parlanti.dominio.DURATA_MINIMA_SEGMENTO_MS
import snastro.parlanti.dominio.ErroreParlanti
import snastro.parlanti.dominio.Impronta
import snastro.parlanti.dominio.SorgenteImpronta

/**
 * Read-model `piano-per-somiglianza` ([INV-27], ADR 0019 §4.1-4.4, §4.7-4.8 + Amendment 2026-09-24
 * (b).1): the plan of "Riassegna per somiglianza" — computed, never stored, never writes. It:
 * 1. derives, per `attivo` Parlante attributed in the Registrazione, its REFERENCE set ("frasi
 *    confermate" — its `confermato` Segmenti >= 1 000 ms — if it has any, else "intera Voce" — every
 *    Segmento >= 1 000 ms on its Voci, ADR 0019 Amendment (b).1);
 * 2. keeps only the `attivo` Parlanti with >= 1 reference as REFERENCE Parlanti; fewer than 2 →
 *    [ErroreParlanti.RiferimentiInsufficienti], with NO extraction;
 * 3. determines the FROZEN Voci (attributed to an `eliminato`, or to an `attivo` non-reference
 *    Parlante) and the MOVABLE Segmenti (not `confermato`, on a non-frozen Voce);
 * 4. embeds — OUTSIDE any transaction, one [EstrattoreImpronta.estrai] per Mutex hold (ADR 0017
 *    §1.2) — the UNION of every reference Segmento and every movable Segmento >= 1 000 ms, each
 *    Segmento ONCE (a Segmento that is both a reference and a candidate, in "intera Voce" mode, is
 *    extracted once and serves both roles), reporting `progresso` after each extraction;
 * 5. classifies the movable Segmenti through [ClassificatoreSomiglianza] against the reference
 *    centroids, and plans a move to each Sicura Parlante's target Voce (its lowest attributed
 *    voceId) when the Segmento is not already there;
 * 6. applies the [INV-27] last-Segmento guard: a reference Parlante is never left with zero Segmenti
 *    in the Registrazione — every move out of its Voci is dropped (and counted as `incerte`) until
 *    stable.
 *
 * Movable Segmenti shorter than 1 000 ms are never extracted and always count once in `incerte`, as
 * do Segmenti classified [Classificazione.Incerta] and moves dropped by the guard. A Segmento already
 * on its target Voce yields neither a move nor an `incerta`. The result carries no similarity number
 * and no [Impronta] survives the call (ADR 0009, ADR 0019 §4.8).
 */
@Suppress("LongParameterList") // one parameter per collaborator: 2 repositories, 1 cross-ctx port, 3 technical ports
public class PianoRiassegnazioneQuery(
    private val voci: LettoreVoci,
    private val attribuzioni: AttribuzioneRepository,
    private val parlanti: ParlanteRepository,
    private val decodificatore: DecodificatoreAudio,
    private val estrattore: EstrattoreImpronta,
    private val classificatore: ClassificatoreSomiglianza,
) {
    /**
     * Errors: [ErroreParlanti.TrascrittoNonTrovato] (no Trascritto, INV-5),
     * [ErroreParlanti.RiferimentiInsufficienti] (fewer than 2 reference Parlanti, no extraction ran).
     * May throw [InterruptedException] (ADR 0017 §1.5): no result, nothing written, a later `calcola`
     * starts extraction over from the start. [progresso] is called once per extraction, in order,
     * with a constant `totale`.
     */
    public fun calcola(id: RegistrazioneId, progresso: (fatti: Int, totale: Int) -> Unit): Esito<PianoRiassegnazione> {
        val segmenti = voci.segmenti(id) ?: return Esito.Errore(ErroreParlanti.TrascrittoNonTrovato(id))
        return calcolaPiano(id, segmenti, progresso)
    }

    @Suppress("LongMethod") // one read-model pipeline, ADR 0019 S4.1-4.4: splitting it would scatter the steps
    private fun calcolaPiano(
        id: RegistrazioneId,
        segmenti: List<SegmentoDiVoce>,
        progresso: (fatti: Int, totale: Int) -> Unit,
    ): Esito<PianoRiassegnazione> {
        val vociDi: Map<ParlanteId, List<VoceId>> = attribuzioni.diRegistrazione(id)
            .groupBy({ it.parlanteId }, { it.voceRef.voceId })
        val segmentiPerVoce: Map<VoceId, List<SegmentoDiVoce>> = segmenti.groupBy { it.voceId }

        val riferimenti = riferimentiPerParlante(vociDi, segmentiPerVoce)
        if (riferimenti.size < 2) return Esito.Errore(ErroreParlanti.RiferimentiInsufficienti(id))

        val target: Map<ParlanteId, VoceId> =
            riferimenti.keys.associateWith { p -> vociDi.getValue(p).minBy { it.numero } }
        val vociCongelate: Set<VoceId> = vociDi.filterKeys { it !in riferimenti }.values.flatten().toSet()
        val movibili = segmenti.filter { !it.confermato && it.voceId !in vociCongelate }
        val (corti, movibiliValidi) = movibili.partition { it.intervallo.durataMs < DURATA_MINIMA_SEGMENTO_MS }
        var incerte = corti.size

        val daEstrarre = (riferimenti.values.flatten() + movibiliValidi)
            .distinctBy { it.segmentoId }
            .sortedBy { it.segmentoId.numero }
        val impronteDi = estrai(id, daEstrarre, progresso)

        val riferimentiImpronte: Map<ParlanteId, List<Impronta>> =
            riferimenti.mapValues { (_, segs) -> segs.map { impronteDi.getValue(it.segmentoId) } }
        val frasi = movibiliValidi.map { impronteDi.getValue(it.segmentoId) }
        val classificazioni = classificatore.classifica(riferimentiImpronte, frasi)

        val candidati = mutableListOf<SpostamentoProposto>()
        movibiliValidi.forEachIndexed { i, s ->
            when (val c = classificazioni[i]) {
                Classificazione.Incerta -> incerte++
                is Classificazione.Sicura -> {
                    val destinazione = target.getValue(c.parlanteId)
                    if (s.voceId != destinazione) {
                        candidati += SpostamentoProposto(s.segmentoId, s.voceId, destinazione, s.intervallo)
                    }
                }
            }
        }

        val riferimentiPerGuardia = vociDi.filterKeys { it in riferimenti }
        val (attivi, incerteGuardia) = applicaGuardiaUltimoSegmento(candidati, segmenti, riferimentiPerGuardia)
        incerte += incerteGuardia

        val spostamenti = attivi.sortedWith(compareBy({ it.intervallo.inizioMs }, { it.segmentoId.numero }))
        return Esito.Ok(PianoRiassegnazione(id, spostamenti, incerte))
    }

    /** Per `attivo` Parlante attributed in R with >= 1 reference: "frasi confermate" if any, else "intera Voce". */
    private fun riferimentiPerParlante(
        vociDi: Map<ParlanteId, List<VoceId>>,
        segmentiPerVoce: Map<VoceId, List<SegmentoDiVoce>>,
    ): Map<ParlanteId, List<SegmentoDiVoce>> =
        vociDi.keys.mapNotNull { parlanteId ->
            val parlante = parlanti.trova(parlanteId)
            if (parlante == null || !parlante.attivo) return@mapNotNull null
            val delParlante = vociDi.getValue(parlanteId).flatMap { segmentiPerVoce[it].orEmpty() }
            val almenoUnSecondo = delParlante.filter { it.intervallo.durataMs >= DURATA_MINIMA_SEGMENTO_MS }
            if (almenoUnSecondo.isEmpty()) return@mapNotNull null
            val confermate = almenoUnSecondo.filter { it.confermato }
            parlanteId to confermate.ifEmpty { almenoUnSecondo }
        }.toMap()

    /** One [EstrattoreImpronta.estrai] per Segmento of [daEstrarre], outside any transaction (ADR 0017 §1.2). */
    private fun estrai(
        id: RegistrazioneId,
        daEstrarre: List<SegmentoDiVoce>,
        progresso: (fatti: Int, totale: Int) -> Unit,
    ): Map<SegmentoId, Impronta> {
        val totale = daEstrarre.size
        val impronte = LinkedHashMap<SegmentoId, Impronta>(totale)
        daEstrarre.forEachIndexed { i, s ->
            val campioni = decodificatore.campioni(id, SorgenteImpronta.di(listOf(s.intervallo)).intervalli)
            impronte[s.segmentoId] = estrattore.estrai(campioni) // puo lanciare InterruptedException (ADR 0017 S1.5)
            progresso(i + 1, totale)
        }
        return impronte
    }

    /**
     * [INV-27] guard: a reference Parlante's final Segmento count (after applying the surviving
     * [candidati]) never reaches zero. Repeats until stable — each round only drops moves, so it
     * terminates, and dropping one Parlante's moves may newly violate another's (the cascade the
     * amendment describes), caught by the next round.
     */
    private fun applicaGuardiaUltimoSegmento(
        candidati: List<SpostamentoProposto>,
        segmenti: List<SegmentoDiVoce>,
        vociDiRiferimento: Map<ParlanteId, List<VoceId>>,
    ): Pair<List<SpostamentoProposto>, Int> {
        var attivi = candidati
        var incerteAggiuntive = 0
        var cambiato = true
        while (cambiato) {
            fun voceFinale(s: SegmentoDiVoce): VoceId = attivi.find { it.segmentoId == s.segmentoId }?.a ?: s.voceId
            val violanti = vociDiRiferimento.filterValues { voci -> segmenti.none { voceFinale(it) in voci } }.keys
            val vociViolanti = violanti.flatMap { vociDiRiferimento.getValue(it) }.toSet()
            val daRimuovere = attivi.filter { it.da in vociViolanti }
            cambiato = daRimuovere.isNotEmpty()
            if (cambiato) {
                attivi = attivi - daRimuovere.toSet()
                incerteAggiuntive += daRimuovere.size
            }
        }
        return attivi to incerteAggiuntive
    }

    private val IntervalloMs.durataMs: Long get() = fineMs - inizioMs
}

/** `piano` view_shape: the plan of "Riassegna per somiglianza" — no similarity number, no [Impronta]. */
public data class PianoRiassegnazione(
    val registrazioneId: RegistrazioneId,
    val spostamenti: List<SpostamentoProposto>,
    val incerte: Int,
)

/** One planned move, ordered by (intervallo.inizioMs, segmentoId) in [PianoRiassegnazione.spostamenti]. */
public data class SpostamentoProposto(
    val segmentoId: SegmentoId,
    val da: VoceId,
    val a: VoceId,
    val intervallo: IntervalloMs,
)
