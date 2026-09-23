// A private copy of a Trascritto for the in-memory repository, built through the aggregate's own API.
@file:Suppress("MatchingDeclarationName", "Filename")

package snastro.trascrizione.applicazione.porte

import snastro.kernel.Esito
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.trascrizione.dominio.Segmento
import snastro.trascrizione.dominio.SegmentoIniziale
import snastro.trascrizione.dominio.Trascritto

/**
 * An independent [Trascritto] with the same observable state (Segmenti with their Voce, `prossimaVoce`,
 * `prossimoSegmento`), rebuilt WITHOUT `ricostituisci` (CR-15 reserves it to persistence adapters):
 * 1. `crea` with the same Segmenti, each Segmento's diarizer index chosen so that creation mints the same
 *    `SegmentoId`s (ties on `inizioMs` keep the id order) with as few Voci as possible;
 * 2. every target Voce keeps an ANCHOR (one of its own Segmenti that never leaves it), so it is never emptied:
 *    the creation Voci the copy keeps receive theirs first; the ids above creation are then minted in order
 *    by moving one Segmento to a new Voce (a target Voce) or out and straight back (an id used in the past
 *    and gone since);
 * 3. every other Segmento moves to its target Voce; Voci that are not targets empty out and disappear.
 * The result is checked against the source: a state this cannot rebuild fails loudly, never silently.
 */
internal fun Trascritto.copia(): Trascritto {
    val copia = creaConGliStessiId(this)
    Ricostruzione(copia, bersaglio = segmenti.associate { it.id to it.voceId }, prossimaVoce).esegui()
    check(copia.statoOsservabile() == statoOsservabile()) { "copia del Trascritto $registrazioneId non riuscita" }
    return copia
}

private fun Trascritto.statoOsservabile(): Triple<List<Segmento>, Int, Int> =
    Triple(segmenti, prossimaVoce, prossimoSegmento)

/**
 * `crea` sorts by (inizio, Voce, fine): within a group of equal `inizioMs` (in id order) the diarizer index
 * grows by one exactly where `fineMs` decreases, so creation reproduces the id order; index `l` first appears
 * no later than `l + 1`, hence creation numbers it Voce `l + 1`.
 */
private fun creaConGliStessiId(sorgente: Trascritto): Trascritto {
    val iniziali = sorgente.segmenti.groupBy { it.intervallo.inizioMs }.values.flatMap { gruppo ->
        var livello = 0
        gruppo.mapIndexed { i, s ->
            if (i > 0 && s.intervallo.fineMs < gruppo[i - 1].intervallo.fineMs) livello++
            SegmentoIniziale(livello, s.intervallo, s.testo)
        }
    }
    val durata = sorgente.segmenti.maxOf { it.intervallo.fineMs }
    val creato = Trascritto.crea(sorgente.registrazioneId, durata, iniziali)
    check(creato is Esito.Ok) { "crea rifiutata per la copia di ${sorgente.registrazioneId}: $creato" }
    return creato.valore.aggregato
}

private class Ricostruzione(
    private val t: Trascritto,
    private val bersaglio: Map<SegmentoId, VoceId>,
    private val prossimaVoce: Int,
) {
    private val ancore = mutableSetOf<SegmentoId>()

    fun esegui() {
        val creazione = t.prossimaVoce
        check(creazione <= prossimaVoce) { "la creazione ha piu Voci del Trascritto da copiare" }
        ancoraCreazione((1 until creazione).map(::VoceId).filter { it in bersaglio.values })
        for (v in creazione until prossimaVoce) conia(VoceId(v))
        bersaglio.filterKeys { it !in ancore }.forEach { (s, v) -> if (voceDi(s) != v) sposta(s, v) }
    }

    /**
     * The Voci minted by creation that the copy keeps get an anchor among their own Segmenti, in any order that
     * works: a Segmento already there, or one whose Voce keeps others or is not kept. A Segmento alone in a
     * kept Voce waits until that Voce has received its own anchor.
     */
    private fun ancoraCreazione(voci: List<VoceId>) {
        val restanti = voci.toMutableList()
        while (restanti.isNotEmpty()) {
            val v = restanti.firstOrNull { ancoraPer(it) != null }
            checkNotNull(v) { "nessuna ancora disponibile per le Voci $restanti" }
            val s = checkNotNull(ancoraPer(v))
            if (voceDi(s) != v) sposta(s, v)
            ancore += s
            restanti -= v
        }
    }

    private fun ancoraPer(v: VoceId): SegmentoId? {
        val propri = segmentiBersaglio(v)
        return propri.firstOrNull { voceDi(it) == v }
            ?: propri.firstOrNull { diPiu(voceDi(it)) || voceDi(it) !in bersaglio.values }
    }

    /**
     * Mints [v], the next id, by moving one Segmento out of a Voce that keeps others: a target Voce gets its
     * anchor; an id the copy no longer has is minted and emptied at once.
     */
    private fun conia(v: VoceId) {
        if (v in bersaglio.values) {
            val propri = segmentiBersaglio(v)
            val s = propri.firstOrNull { diPiu(voceDi(it)) } ?: propri.first().also(::accompagna)
            nuovaVoce(s, v)
            ancore += s
        } else {
            val s = t.segmenti.map { it.id }.firstOrNull { diPiu(voceDi(it)) } ?: sacrificabile().also(::accompagna)
            val origine = voceDi(s)
            nuovaVoce(s, v)
            sposta(s, origine)
        }
    }

    /**
     * [s] is alone in its Voce, which is not a Voce of the copy (a kept Voce holds its anchor too): move it into
     * another Voce, which then has two Segmenti and can give [s] away; its own Voce disappears.
     */
    private fun accompagna(s: SegmentoId) {
        check(voceDi(s) !in bersaglio.values) { "$s e solo in una Voce da conservare" }
        sposta(s, t.voci.map { it.id }.first { it != voceDi(s) })
    }

    /** A Segmento in a Voce the copy does not keep (so it may disappear). */
    private fun sacrificabile(): SegmentoId = t.segmenti.first { it.voceId !in bersaglio.values }.id

    private fun nuovaVoce(s: SegmentoId, attesa: VoceId) {
        val esito = t.riassegna(s, null)
        check(esito is Esito.Ok && esito.valore.a == attesa) { "conio di $attesa fallito: $esito" }
    }

    private fun sposta(s: SegmentoId, verso: VoceId) {
        val esito = t.riassegna(s, verso)
        check(esito is Esito.Ok) { "spostamento di $s verso $verso fallito: $esito" }
    }

    private fun segmentiBersaglio(v: VoceId): List<SegmentoId> =
        bersaglio.filterValues { it == v }.keys.sortedBy { it.numero }

    private fun voceDi(s: SegmentoId): VoceId = t.segmenti.first { it.id == s }.voceId

    private fun diPiu(v: VoceId): Boolean = t.segmenti.count { it.voceId == v } >= 2
}
