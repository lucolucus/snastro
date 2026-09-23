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
import snastro.trascrizione.dominio.ErroreTrascrizione.UnioneNonAmmessa
import snastro.trascrizione.dominio.ErroreTrascrizione.VoceNonTrovata

/**
 * The transcript of one `Registrazione` (identity [registrazioneId]): its Voci and Segmenti, and the
 * `Revisione` operations [unisci], [dividi], [riassegna]. Owns INV-6…INV-12.
 *
 * The only state is the Segmento→Voce assignment plus the counters: a Voce exists iff at least one
 * Segmento is assigned to it, so no Voce is ever empty (INV-6) by construction; Segmenti are never
 * created after [crea] nor edited (INV-8); new Voci take [prossimaVoce]`++`, so a `VoceId` is never
 * reused nor renumbered (INV-12).
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
            sposta(idsDi(rimossa), verso = sopravvive)
            Esito.Ok(VociUnite(registrazioneId, sopravvissuta = sopravvive, rimossa = rimossa))
        }
    }

    /** INV-10: [segmenti], a non-empty proper subset of [origine]'s Segmenti, become a NEW Voce. */
    public fun dividi(origine: VoceId, segmenti: Set<SegmentoId>): Esito<VoceDivisa> {
        val diOrigine = idsDi(origine)
        return when {
            diOrigine.isEmpty() -> Esito.Errore(VoceNonTrovata(origine))
            segmenti.isEmpty() || !diOrigine.containsAll(segmenti) || segmenti.size == diOrigine.size ->
                Esito.Errore(DivisioneNonAmmessa(origine, segmenti))
            else -> {
                val nuova = nuovaVoce()
                sposta(segmenti, verso = nuova)
                val spostati = segmenti.map { _segmenti.getValue(it) }.sortedWith(ORDINE_NELLA_VOCE).map { it.id }
                Esito.Ok(VoceDivisa(registrazioneId, origine, nuova, spostati))
            }
        }
    }

    /**
     * INV-11: moves [segmento] to [destinazione], an existing other Voce, or to a NEW Voce when `null`;
     * the source Voce is removed if emptied (INV-6).
     */
    public fun riassegna(segmento: SegmentoId, destinazione: VoceId?): Esito<SegmentoRiassegnato> {
        val da = _segmenti[segmento]?.voceId ?: return Esito.Errore(SegmentoNonTrovato(segmento))
        return when {
            destinazione == da -> Esito.Errore(RiassegnazioneNonAmmessa(segmento, destinazione))
            destinazione != null && !esiste(destinazione) -> Esito.Errore(VoceNonTrovata(destinazione))
            else -> {
                val a = destinazione ?: nuovaVoce()
                sposta(setOf(segmento), verso = a)
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

    private fun esiste(voce: VoceId): Boolean = _segmenti.values.any { it.voceId == voce }

    private fun idsDi(voce: VoceId): Set<SegmentoId> =
        _segmenti.values.filter { it.voceId == voce }.mapTo(mutableSetOf()) { it.id }

    private fun nuovaVoce(): VoceId = VoceId(_prossimaVoce++)

    /** The only mutation of the aggregate: only the Voce changes, never id, intervallo or testo (INV-8). */
    private fun sposta(segmenti: Set<SegmentoId>, verso: VoceId) {
        segmenti.forEach { id -> _segmenti[id] = _segmenti.getValue(id).copy(voceId = verso) }
    }

    public companion object {
        private val ORDINE_NELLA_VOCE = compareBy<Segmento>({ it.intervallo.inizioMs }, { it.id.numero })

        /**
         * Builds the Trascritto from the pipeline's turns (AC-20): Voci are numbered 1..n by FIRST
         * APPEARANCE (smallest inizio, tie: lower [SegmentoIniziale.voceIndice]); Segmenti 1..m by
         * (inizio, Voce). No turn → [NessunParlatoRilevato] (AC-21); a turn ending after [durataMs] →
         * [SegmentoOltreLaDurata] (INV-7).
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
                        .sortedWith(compareBy({ it.intervallo.inizioMs }, { voceDi.getValue(it.voceIndice).numero }))
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

        /** Rebuilds from persisted state; re-validates nothing (the DB is trusted). */
        @RicostituzioneDaPersistenza
        public fun ricostituisci(
            registrazioneId: RegistrazioneId,
            segmenti: List<Segmento>,
            prossimaVoce: Int,
            prossimoSegmento: Int,
        ): Trascritto = Trascritto(registrazioneId, segmenti, prossimaVoce, prossimoSegmento)
    }
}
