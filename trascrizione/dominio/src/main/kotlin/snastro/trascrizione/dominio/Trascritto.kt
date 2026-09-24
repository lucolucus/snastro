package snastro.trascrizione.dominio

import snastro.kernel.Creato
import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.kernel.RicostituzioneDaPersistenza
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.trascrizione.dominio.ErroreTrascrizione.DivisioneNonAmmessa
import snastro.trascrizione.dominio.ErroreTrascrizione.NessunParlatoRilevato
import snastro.trascrizione.dominio.ErroreTrascrizione.RiassegnazioneNonAmmessa
import snastro.trascrizione.dominio.ErroreTrascrizione.SegmentoNonTrovato
import snastro.trascrizione.dominio.ErroreTrascrizione.SegmentoOltreLaDurata
import snastro.trascrizione.dominio.ErroreTrascrizione.TrascrittoCambiato
import snastro.trascrizione.dominio.ErroreTrascrizione.UnioneNonAmmessa
import snastro.trascrizione.dominio.ErroreTrascrizione.VoceNonTrovata

/**
 * The transcript of one `Registrazione` (identity [registrazioneId]): its Voci and Segmenti, and the
 * `Revisione` operations [unisci], [dividi], [riassegna], [riassegnaInBlocco], [confermaSegmento].
 * Owns INV-6…INV-12 and INV-26.
 *
 * The only state is the Segmento→Voce assignment, the `confermato` flags (INV-26) and the counters: a
 * Voce exists iff at least one Segmento is assigned to it, so no Voce is ever empty (INV-6) by
 * construction; Segmenti are never created after [crea] nor edited except for their Voce and their
 * flag (INV-8); new Voci take [prossimaVoce]`++`, so a `VoceId` is never reused nor renumbered (INV-12).
 */
public class Trascritto private constructor(
    public val registrazioneId: RegistrazioneId,
    segmenti: List<Segmento>,
    prossimaVoce: Int,
    prossimoSegmento: Int,
) {
    // Backing fields, not `public var … private set`: CR-4's Konsist rule bans any public var in dominio.
    private val _segmenti: MutableMap<SegmentoId, Segmento> = segmenti.associateByTo(LinkedHashMap()) { it.id }
    private var _prossimaVoce = prossimaVoce

    /** Persisted counter: the `VoceId` the next new Voce takes. For persistence only — never decide on it. */
    public val prossimaVoce: Int get() = _prossimaVoce

    /** Persisted counter: one past the last `SegmentoId` minted at creation (none is minted afterwards). */
    public val prossimoSegmento: Int = prossimoSegmento

    /** Every Segmento, ordered by id (= by inizio, then Voce, at creation). A read-only copy. */
    public val segmenti: List<Segmento> get() = _segmenti.values.sortedBy { it.id.numero }

    /** Every existing Voce ordered by id, each with its Segmenti in INV-7 order. A read-only copy. */
    public val voci: List<Voce>
        get() = _segmenti.values
            .groupBy { it.voceId }
            .map { (id, suoi) -> Voce(id, suoi.sortedWith(ORDINE_NELLA_VOCE)) }
            .sortedBy { it.id.numero }

    /** INV-9: moves every Segmento of [rimossa] onto [sopravvive]; [rimossa] ceases to exist. */
    public fun unisci(sopravvive: VoceId, rimossa: VoceId): Esito<VociUnite> = when {
        sopravvive == rimossa -> Esito.Errore(UnioneNonAmmessa(sopravvive, rimossa))
        !esiste(sopravvive) -> Esito.Errore(VoceNonTrovata(sopravvive))
        !esiste(rimossa) -> Esito.Errore(VoceNonTrovata(rimossa))
        else -> {
            sposta(idsDi(rimossa), verso = sopravvive, conferma = false)
            Esito.Ok(VociUnite(registrazioneId, sopravvissuta = sopravvive, rimossa = rimossa))
        }
    }

    /**
     * INV-10: [segmenti], a non-empty proper subset of [origine]'s Segmenti, become a NEW Voce; each of them is
     * `confermato` afterwards (INV-26), the Segmenti left on [origine] keep their flags.
     */
    public fun dividi(origine: VoceId, segmenti: Set<SegmentoId>): Esito<VoceDivisa> {
        val diOrigine = idsDi(origine)
        return when {
            diOrigine.isEmpty() -> Esito.Errore(VoceNonTrovata(origine))
            segmenti.isEmpty() || !diOrigine.containsAll(segmenti) || segmenti.size == diOrigine.size ->
                Esito.Errore(DivisioneNonAmmessa(origine, segmenti))
            else -> {
                val nuova = nuovaVoce()
                sposta(segmenti, verso = nuova, conferma = true)
                val spostati = segmenti.map { _segmenti.getValue(it) }.sortedWith(ORDINE_NELLA_VOCE).map { it.id }
                Esito.Ok(VoceDivisa(registrazioneId, origine, nuova, spostati))
            }
        }
    }

    /**
     * INV-11: moves [segmento] to [destinazione], an existing other Voce (the source Voce is removed if
     * emptied, INV-6), or to a NEW Voce when `null`. A NEW Voce is refused for the only Segmento of its
     * Voce [user decision]: it would change no grouping yet remove the Voce, losing its Attribuzione and
     * ImprontaVocale (INV-21). A refusal changes nothing, [prossimaVoce] included. The moved Segmento is
     * `confermato` afterwards: a manual move is an explicit user act (INV-26).
     */
    public fun riassegna(segmento: SegmentoId, destinazione: VoceId?): Esito<SegmentoRiassegnato> {
        val da = _segmenti[segmento]?.voceId ?: return Esito.Errore(SegmentoNonTrovato(segmento))
        return when {
            destinazione == da || destinazione == null && idsDi(da).size == 1 ->
                Esito.Errore(RiassegnazioneNonAmmessa(segmento, destinazione))
            destinazione != null && !esiste(destinazione) -> Esito.Errore(VoceNonTrovata(destinazione))
            else -> {
                val a = destinazione ?: nuovaVoce()
                sposta(setOf(segmento), verso = a, conferma = true)
                Esito.Ok(
                    SegmentoRiassegnato(
                        registrazioneId,
                        segmento,
                        da = da,
                        a = a,
                        daRimossa = !esiste(da),
                        aNuova = destinazione == null,
                    ),
                )
            }
        }
    }

    /**
     * ADR 0019 §4.5 + Amendment (b).1: applies [spostamenti] as ONE all-or-nothing batch. Every entry is validated
     * against the PRE-batch state, the moves are applied in list order, and a Voce empty at the END of the batch
     * ceases to exist (INV-6) — one emptied and refilled within the batch is kept. Never creates a Voce, never
     * changes a `confermato` flag (INV-26). A stale entry → [TrascrittoCambiato]; `a == da` or a duplicated
     * Segmento → [RiassegnazioneNonAmmessa]; a refusal changes nothing. One event per move in list order,
     * `aNuova = false`, `daRimossa` on the LAST move out of each Voce empty at the end.
     */
    public fun riassegnaInBlocco(spostamenti: List<SpostamentoSegmento>): Esito<List<SegmentoRiassegnato>> {
        val rifiuto = rifiutoDelBlocco(spostamenti)
        if (rifiuto != null) return Esito.Errore(rifiuto)
        spostamenti.forEach { m -> sposta(setOf(m.segmentoId), verso = m.a, conferma = false) }
        val ultimaUscita = spostamenti.withIndex().associate { (i, m) -> m.da to i }
        return Esito.Ok(
            spostamenti.mapIndexed { i, m ->
                SegmentoRiassegnato(
                    registrazioneId,
                    m.segmentoId,
                    da = m.da,
                    a = m.a,
                    daRimossa = ultimaUscita[m.da] == i && !esiste(m.da),
                    aNuova = false,
                )
            },
        )
    }

    /**
     * INV-26: sets ([confermato] = true) or revokes ("Togli conferma") the flag of [segmento]. The same value →
     * `Ok(null)`, nothing changes; an unknown Segmento → [SegmentoNonTrovato].
     */
    public fun confermaSegmento(segmento: SegmentoId, confermato: Boolean): Esito<SegmentoConfermato?> {
        val attuale = _segmenti[segmento]
        return when {
            attuale == null -> Esito.Errore(SegmentoNonTrovato(segmento))
            attuale.confermato == confermato -> Esito.Ok(null)
            else -> {
                _segmenti[segmento] = attuale.copy(confermato = confermato)
                Esito.Ok(SegmentoConfermato(registrazioneId, segmento, confermato))
            }
        }
    }

    /** The first refusal of a [riassegnaInBlocco] batch, every entry checked against the PRE-batch state. */
    private fun rifiutoDelBlocco(spostamenti: List<SpostamentoSegmento>): ErroreTrascrizione? {
        val visti = mutableSetOf<SegmentoId>()
        return spostamenti.firstNotNullOfOrNull { m ->
            val s = _segmenti[m.segmentoId]
            when {
                m.a == m.da || !visti.add(m.segmentoId) -> RiassegnazioneNonAmmessa(m.segmentoId, m.a)
                s == null || s.voceId != m.da || s.intervallo != m.intervallo || s.confermato || !esiste(m.a) ->
                    TrascrittoCambiato(registrazioneId)
                else -> null
            }
        }
    }

    private fun esiste(voce: VoceId): Boolean = _segmenti.values.any { it.voceId == voce }

    private fun idsDi(voce: VoceId): Set<SegmentoId> =
        _segmenti.values.filter { it.voceId == voce }.mapTo(mutableSetOf()) { it.id }

    private fun nuovaVoce(): VoceId = VoceId(_prossimaVoce++)

    /**
     * Moves [segmenti] to [verso]: only the Voce (and, when [conferma], the flag set to true) changes, never id,
     * intervallo or testo (INV-8). Without [conferma] every flag is kept (INV-26).
     */
    private fun sposta(segmenti: Set<SegmentoId>, verso: VoceId, conferma: Boolean) {
        segmenti.forEach { id ->
            val s = _segmenti.getValue(id)
            _segmenti[id] = s.copy(voceId = verso, confermato = s.confermato || conferma)
        }
    }

    public companion object {
        private val ORDINE_NELLA_VOCE = compareBy<Segmento>({ it.intervallo.inizioMs }, { it.id.numero })

        /**
         * Builds the Trascritto from the pipeline's turns (AC-20): Voci are numbered 1..n by FIRST
         * APPEARANCE (smallest inizio, tie: lower [SegmentoIniziale.voceIndice]); Segmenti 1..m by
         * (inizio, Voce, fine), independent of the input order. No turn → [NessunParlatoRilevato] (AC-21);
         * a turn ending after [durataMs] → [SegmentoOltreLaDurata] (INV-7).
         */
        public fun crea(
            registrazioneId: RegistrazioneId,
            durataMs: Long,
            segmenti: List<SegmentoIniziale>,
        ): Esito<Creato<Trascritto, TrascrittoCreato>> {
            val oltre = segmenti.firstOrNull { it.intervallo.fineMs > durataMs }
            return when {
                segmenti.isEmpty() -> Esito.Errore(NessunParlatoRilevato)
                oltre != null -> Esito.Errore(SegmentoOltreLaDurata(oltre.intervallo, durataMs))
                else -> {
                    val voceDi = numeraPerPrimaApparizione(segmenti)
                    val numerati = segmenti
                        .sortedWith(
                            compareBy(
                                { it.intervallo.inizioMs },
                                { voceDi.getValue(it.voceIndice).numero },
                                { it.intervallo.fineMs },
                            ),
                        )
                        .mapIndexed { i, s ->
                            Segmento(SegmentoId(i + 1), voceDi.getValue(s.voceIndice), s.intervallo, s.testo)
                        }
                    val trascritto = Trascritto(registrazioneId, numerati, voceDi.size + 1, numerati.size + 1)
                    Esito.Ok(Creato(trascritto, TrascrittoCreato(registrazioneId)))
                }
            }
        }

        private fun numeraPerPrimaApparizione(segmenti: List<SegmentoIniziale>): Map<Int, VoceId> =
            segmenti
                .groupBy { it.voceIndice }
                .mapValues { (_, turni) -> turni.minOf { it.intervallo.inizioMs } }
                .entries
                .sortedWith(compareBy({ it.value }, { it.key }))
                .mapIndexed { i, (voceIndice, _) -> voceIndice to VoceId(i + 1) }
                .toMap()

        /**
         * Rebuilds from persisted state; re-validates no rule (the DB is trusted), but refuses (`require`,
         * programmer error, ADR 0003) counters behind the stored ids or duplicate ids: a stale
         * [prossimaVoce] would let [dividi]/[riassegna] mint an existing `VoceId` and silently merge Voci.
         * Not testable in `dominio` (CR-15): the repository round-trip test (AC-30) must cover it.
         */
        @RicostituzioneDaPersistenza
        public fun ricostituisci(
            registrazioneId: RegistrazioneId,
            segmenti: List<Segmento>,
            prossimaVoce: Int,
            prossimoSegmento: Int,
        ): Trascritto {
            require(segmenti.distinctBy { it.id }.size == segmenti.size) { "SegmentoId duplicati in $registrazioneId" }
            require(segmenti.all { it.voceId.numero < prossimaVoce }) { "prossimaVoce $prossimaVoce non oltre le Voci" }
            require(segmenti.all { it.id.numero < prossimoSegmento }) {
                "prossimoSegmento $prossimoSegmento non oltre i Segmenti"
            }
            return Trascritto(registrazioneId, segmenti, prossimaVoce, prossimoSegmento)
        }
    }
}
