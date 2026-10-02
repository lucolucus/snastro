package snastro.parlanti.applicazione.letture

import snastro.kernel.Esito
import snastro.kernel.IncontroId
import snastro.kernel.IntervalloMs
import snastro.kernel.ParlanteId
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoRef
import snastro.kernel.VoceId
import snastro.parlanti.applicazione.porte.AttribuzioneRepository
import snastro.parlanti.applicazione.porte.ClassificatoreSomiglianza
import snastro.parlanti.applicazione.porte.Classificazione
import snastro.parlanti.applicazione.porte.DecodificatoreAudio
import snastro.parlanti.applicazione.porte.EstrattoreImpronta
import snastro.parlanti.applicazione.porte.LettoreRegistrazione
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
 * 1. derives, per `attivo` Parlante attributed in the Incontro, its REFERENCE set ("frasi
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
 *    in the Incontro — every move out of its Voci is dropped (and counted as `incerte`) until
 *    stable.
 *
 * Movable Segmenti shorter than 1 000 ms are never extracted and always count once in `incerte`, as
 * do Segmenti classified [Classificazione.Incerta] and moves dropped by the guard. A Segmento already
 * on its target Voce yields neither a move nor an `incerta`. The result carries no similarity number
 * and no [Impronta] survives the call (ADR 0009, ADR 0019 §4.8).
 */
@Suppress("LongParameterList") // one parameter per collaborator: 2 repositories, 2 cross-ctx ports, 3 technical ports
public class PianoRiassegnazioneQuery(
    private val voci: LettoreVoci,
    private val attribuzioni: AttribuzioneRepository,
    private val parlanti: ParlanteRepository,
    private val decodificatore: DecodificatoreAudio,
    private val estrattore: EstrattoreImpronta,
    private val classificatore: ClassificatoreSomiglianza,
    private val registrazioni: LettoreRegistrazione,
) {
    /**
     * [INV-27] over the WHOLE Incontro the Parte [id] belongs to (the Parte the user asked from): references, target
     * Voci, movable Segmenti and the last-Segmento guard span every transcribed Parte of the Incontro.
     * Errors: [ErroreParlanti.TrascrittoNonTrovato] ([id] unknown, or no Parte of its Incontro has a Trascritto,
     * INV-5),
     * [ErroreParlanti.RiferimentiInsufficienti] (fewer than 2 reference Parlanti, no extraction ran).
     * May throw [InterruptedException] (ADR 0017 §1.5): no result, nothing written, a later `calcola`
     * starts extraction over from the start. [progresso] is called once per extraction, in order,
     * with a constant `totale`.
     */
    public fun calcola(id: RegistrazioneId, progresso: (fatti: Int, totale: Int) -> Unit): Esito<PianoRiassegnazione> {
        val incontroId = registrazioni.registrazione(id)?.incontroId
        val segmenti = incontroId?.let { voci.segmenti(it) }
            ?: return Esito.Errore(ErroreParlanti.TrascrittoNonTrovato(id))
        // [INV-27] over the Incontro: the Parti in the Incontro's order, each Parte's Segmenti by (inizio, segmentoId).
        val numero = registrazioni.parti(incontroId).orEmpty().associate { it.registrazioneId to it.numero }
        val ordinati = segmenti.sortedWith(
            compareBy(
                { numero[it.segmento.registrazioneId] ?: Int.MAX_VALUE },
                { it.intervallo.inizioMs },
                { it.segmento.segmentoId.numero },
            ),
        )
        return calcolaPiano(id, incontroId, ordinati, progresso)
    }

    @Suppress("LongMethod") // one read-model pipeline, ADR 0019 S4.1-4.4: splitting it would scatter the steps
    private fun calcolaPiano(
        id: RegistrazioneId,
        incontroId: IncontroId,
        segmenti: List<SegmentoDiVoce>,
        progresso: (fatti: Int, totale: Int) -> Unit,
    ): Esito<PianoRiassegnazione> {
        // [INV-27]: the Attribuzioni of the CURRENT Voci of the whole Incontro.
        val vociDi: Map<ParlanteId, List<VoceId>> = voci.voci(incontroId).orEmpty()
            .mapNotNull { attribuzioni.trova(it.voceRef) }
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

        val scelti = (riferimenti.values.flatten() + movibiliValidi).map { it.segmento }.toSet()
        val daEstrarre = segmenti.filter { it.segmento in scelti } // each once, in (Parte, inizio, segmentoId) order
        val impronteDi = estrai(daEstrarre, progresso)

        val riferimentiImpronte: Map<ParlanteId, List<Impronta>> =
            riferimenti.mapValues { (_, segs) -> segs.map { impronteDi.getValue(it.segmento) } }
        val frasi = movibiliValidi.map { impronteDi.getValue(it.segmento) }
        val classificazioni = classificatore.classifica(riferimentiImpronte, frasi)

        val candidati = mutableListOf<SpostamentoProposto>()
        movibiliValidi.forEachIndexed { i, s ->
            when (val c = classificazioni[i]) {
                Classificazione.Incerta -> incerte++
                is Classificazione.Sicura -> {
                    val destinazione = target.getValue(c.parlanteId)
                    if (s.voceId != destinazione) {
                        candidati += SpostamentoProposto(s.segmento, s.voceId, destinazione, s.intervallo)
                    }
                }
            }
        }

        val riferimentiPerGuardia = vociDi.filterKeys { it in riferimenti }
        val (attivi, incerteGuardia) = applicaGuardiaUltimoSegmento(candidati, segmenti, riferimentiPerGuardia)
        incerte += incerteGuardia

        val ordine = segmenti.map { it.segmento }.withIndex().associate { (i, ref) -> ref to i }
        val spostamenti = attivi.sortedBy { ordine.getValue(it.segmento) }
        return Esito.Ok(PianoRiassegnazione(incontroId, spostamenti, incerte))
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
        daEstrarre: List<SegmentoDiVoce>,
        progresso: (fatti: Int, totale: Int) -> Unit,
    ): Map<SegmentoRef, Impronta> {
        val totale = daEstrarre.size
        val impronte = LinkedHashMap<SegmentoRef, Impronta>(totale)
        daEstrarre.forEachIndexed { i, s ->
            val parte = s.segmento.registrazioneId
            val campioni = decodificatore.campioni(parte, SorgenteImpronta.di(listOf(s.intervallo)).intervalli)
            impronte[s.segmento] = estrattore.estrai(campioni) // puo lanciare InterruptedException (ADR 0017 S1.5)
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
            fun voceFinale(s: SegmentoDiVoce): VoceId = attivi.find { it.segmento == s.segmento }?.a ?: s.voceId
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

/**
 * `piano` view_shape: the plan of "Riassegna per somiglianza" over the Incontro [incontroId] ([INV-27]) — no
 * similarity number, no [Impronta].
 */
public data class PianoRiassegnazione(
    val incontroId: IncontroId,
    val spostamenti: List<SpostamentoProposto>,
    val incerte: Int,
)

/**
 * One planned move of the Segmento [segmento] (its Parte + segmentoId), ordered in [PianoRiassegnazione.spostamenti]
 * by (the Parte's place in the Incontro, intervallo.inizioMs, segmentoId).
 */
public data class SpostamentoProposto(
    val segmento: SegmentoRef,
    val da: VoceId,
    val a: VoceId,
    val intervallo: IntervalloMs,
)
