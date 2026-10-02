package snastro.trascrizione.dominio

import snastro.kernel.Esito
import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RicostituzioneDaPersistenza
import snastro.kernel.SegmentoRef
import snastro.kernel.VoceId
import snastro.kernel.mappa
import snastro.trascrizione.dominio.ErroreTrascrizione.DivisioneNonAmmessa
import snastro.trascrizione.dominio.ErroreTrascrizione.RiassegnazioneNonAmmessa
import snastro.trascrizione.dominio.ErroreTrascrizione.SegmentoNonTrovato
import snastro.trascrizione.dominio.ErroreTrascrizione.TrascrittoCambiato
import snastro.trascrizione.dominio.ErroreTrascrizione.TrascrittoNonTrovato
import snastro.trascrizione.dominio.ErroreTrascrizione.UnioneNonAmmessa
import snastro.trascrizione.dominio.ErroreTrascrizione.VoceNonTrovata

/**
 * The Voci dell'Incontro (ADR 0035 §1, root, identity [incontroId]): the Voce counter of the Incontro and one
 * [Trascritto]
 * entity per transcribed Parte. Owns INV-6 and INV-8 at Incontro scope, INV-I4…INV-I7 and INV-I16; the `Revisione`
 * operations relate Voci and Segmenti of any Parte of THIS Incontro (INV-I7).
 *
 * A Voce exists iff at least one Segmento of some Parte is assigned to it (INV-6 by construction). Every new Voce takes
 * the counter, which only grows (INV-I4); each Parte numbers its Segmenti from its own counter, kept across
 * replacements (INV-I16). The Parti keep the order in which they were first completed (or rebuilt).
 */
@Suppress("TooManyFunctions") // the pinned API of ADR 0035 §1: seven commands, the named predicates, private helpers
public class VociDellIncontro private constructor(
    public val incontroId: IncontroId,
    trascritti: List<Trascritto>,
    prossimaVoce: Int,
) {
    private val parti: MutableMap<RegistrazioneId, Trascritto> =
        trascritti.associateByTo(LinkedHashMap()) { it.registrazioneId }
    private var _prossimaVoce = prossimaVoce

    /** Persisted counter: the `VoceId` the next new Voce takes. For persistence only — never decide on it. */
    public val prossimaVoce: Int get() = _prossimaVoce

    /** Detached copies of the Parti's Trascritti, in Parte order: changing them never changes the root. */
    public val trascritti: List<Trascritto> get() = parti.values.map { it.copia(_prossimaVoce) }

    /** Every existing Voce of the Incontro, by number. */
    public val voci: List<VoceId> get() = assegnati().map { it.second.voceId }.distinct().sortedBy { it.numero }

    /** True iff [registrazioneId] is a transcribed Parte of this Incontro. */
    public fun haParte(registrazioneId: RegistrazioneId): Boolean = registrazioneId in parti

    /** The Parti where [voce] has Segmenti, in Parte order; empty for a Voce that does not exist. */
    public fun partiDi(voce: VoceId): List<RegistrazioneId> =
        parti.values.filter { t -> t.segmenti.any { it.voceId == voce } }.map { it.registrazioneId }

    /** A detached copy of the Trascritto of [registrazioneId], or null if it is not a transcribed Parte. */
    public fun trascritto(registrazioneId: RegistrazioneId): Trascritto? = parti[registrazioneId]?.copia(_prossimaVoce)

    /**
     * A detached copy of the whole root (same Parti in the same order, same counter): changing either never changes the
     * other. For in-memory stores, which may not reconstitute (CR-15) yet must never alias what they keep.
     */
    public fun copia(): VociDellIncontro = VociDellIncontro(incontroId, trascritti, _prossimaVoce)

    /**
     * INV-I5: the first transcription (or a replacement) of the Parte [registrazioneId] from the pipeline's turns. Only
     * that Parte's Segmenti change: on a replacement its old Segmenti leave their Voci, a Voce left empty is removed,
     * a Voce speaking in another Parte keeps its number; then the turns become NEW Voci numbered from the counter by
     * first appearance (never joined to an existing Voce) and Segmenti numbered after every id the Parte ever used
     * (INV-I16). No turn → `NessunParlatoRilevato`, a turn beyond [durataMs] → `SegmentoOltreLaDurata` (INV-7); a
     * refusal changes nothing.
     */
    public fun completaParte(
        registrazioneId: RegistrazioneId,
        segmentiIniziali: List<SegmentoIniziale>,
        durataMs: Long,
    ): Esito<ConclusioneParte> {
        val vecchio = parti[registrazioneId]
        return Trascritto.generazione(
            registrazioneId,
            incontroId,
            durataMs,
            segmentiIniziali,
            primaVoce = _prossimaVoce,
            primoSegmento = vecchio?.prossimoSegmento ?: 1,
        ).mappa { nuovo ->
            val prima = voci.toSet()
            parti.remove(registrazioneId)
            val rimaste = voci.toSet()
            parti[registrazioneId] = nuovo
            val vociNuove = (_prossimaVoce until nuovo.prossimaVoce).mapTo(LinkedHashSet()) { VoceId(it) }
            _prossimaVoce = nuovo.prossimaVoce
            if (vecchio == null) {
                ConclusioneParte.PrimaTrascrizione(vociNuove)
            } else {
                ConclusioneParte.Sostituzione(vociRimosse = prima - rimaste, vociNuove = vociNuove)
            }
        }
    }

    /**
     * INV-I6: the Parte [registrazioneId] leaves the root with its Segmenti; a Voce left empty is removed and returned,
     * the others keep identity and number. The counter is untouched (INV-I4). Not a Parte → `TrascrittoNonTrovato`.
     */
    public fun rimuoviParte(registrazioneId: RegistrazioneId): Esito<Set<VoceId>> {
        if (registrazioneId !in parti) return Esito.Errore(TrascrittoNonTrovato(registrazioneId))
        val prima = voci.toSet()
        parti.remove(registrazioneId)
        return Esito.Ok(prima - voci.toSet())
    }

    /** INV-9: moves every Segmento of [rimossa], in any Parte, onto [sopravvive]; [rimossa] ceases to exist. */
    public fun unisci(sopravvive: VoceId, rimossa: VoceId): Esito<EventoRevisione.VociUnite> = when {
        sopravvive == rimossa -> Esito.Errore(UnioneNonAmmessa(sopravvive, rimossa))
        !esiste(sopravvive) -> Esito.Errore(VoceNonTrovata(sopravvive))
        !esiste(rimossa) -> Esito.Errore(VoceNonTrovata(rimossa))
        else -> {
            sposta(refsDi(rimossa), verso = sopravvive, conferma = false)
            Esito.Ok(EventoRevisione.VociUnite(incontroId, sopravvissuta = sopravvive, rimossa = rimossa))
        }
    }

    /**
     * INV-10 (AC-I16): [segmenti], a non-empty proper subset of [origine]'s Segmenti over the whole Incontro, become
     * ONE new Voce from the counter; each is `confermato` afterwards (INV-26), the others keep their flags.
     */
    public fun dividi(origine: VoceId, segmenti: Set<SegmentoRef>): Esito<EventoRevisione.VoceDivisa> {
        val diOrigine = refsDi(origine)
        return when {
            diOrigine.isEmpty() -> Esito.Errore(VoceNonTrovata(origine))
            segmenti.isEmpty() || !diOrigine.containsAll(segmenti) || segmenti.size == diOrigine.size ->
                Esito.Errore(DivisioneNonAmmessa(origine, segmenti.toSet()))
            else -> {
                val nuova = nuovaVoce()
                sposta(segmenti, verso = nuova, conferma = true)
                Esito.Ok(EventoRevisione.VoceDivisa(incontroId, origine, nuova, refsDi(nuova)))
            }
        }
    }

    /**
     * INV-11: moves [segmento] to [destinazione], an existing other Voce of the Incontro (the source Voce is removed if
     * emptied, INV-6), or to a NEW Voce when `null` — refused for the only Segmento of its Voce in the whole Incontro.
     * The moved Segmento is `confermato` afterwards (INV-26). A refusal changes nothing, the counter included.
     */
    public fun riassegna(segmento: SegmentoRef, destinazione: VoceId?): Esito<EventoRevisione.SegmentoRiassegnato> {
        val da = trova(segmento)?.voceId ?: return Esito.Errore(SegmentoNonTrovato(segmento))
        return when {
            destinazione == da || destinazione == null && refsDi(da).size == 1 ->
                Esito.Errore(RiassegnazioneNonAmmessa(segmento.segmentoId, destinazione))
            destinazione != null && !esiste(destinazione) -> Esito.Errore(VoceNonTrovata(destinazione))
            else -> {
                val a = destinazione ?: nuovaVoce()
                sposta(setOf(segmento), verso = a, conferma = true)
                Esito.Ok(
                    EventoRevisione.SegmentoRiassegnato(
                        incontroId,
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
     * ADR 0019 §4.5 + Amendment (b).1 at Incontro scope: applies [spostamenti] as ONE all-or-nothing batch, every entry
     * validated against the PRE-batch state, applied in list order; a Voce empty at the END ceases (INV-6). Never
     * creates a Voce, never changes a flag. A stale entry → `TrascrittoCambiato` (its Parte); `a == da` or a duplicated
     * Segmento → `RiassegnazioneNonAmmessa`. One event per move, `daRimossa` on the LAST move out of an emptied Voce.
     */
    public fun riassegnaInBlocco(
        spostamenti: List<SpostamentoNellIncontro>,
    ): Esito<List<EventoRevisione.SegmentoRiassegnato>> {
        val rifiuto = rifiutoDelBlocco(spostamenti)
        if (rifiuto != null) return Esito.Errore(rifiuto)
        spostamenti.forEach { m -> sposta(setOf(m.segmento), verso = m.a, conferma = false) }
        val ultimaUscita = spostamenti.withIndex().associate { (i, m) -> m.da to i }
        return Esito.Ok(
            spostamenti.mapIndexed { i, m ->
                EventoRevisione.SegmentoRiassegnato(
                    incontroId,
                    m.segmento,
                    da = m.da,
                    a = m.a,
                    daRimossa = ultimaUscita[m.da] == i && !esiste(m.da),
                    aNuova = false,
                )
            },
        )
    }

    /** INV-26: sets or revokes the flag of [segmento]; the same value → `Ok(null)`, nothing changes. */
    public fun confermaSegmento(
        segmento: SegmentoRef,
        confermato: Boolean,
    ): Esito<EventoRevisione.SegmentoConfermato?> {
        val attuale = trova(segmento)
        return when {
            attuale == null -> Esito.Errore(SegmentoNonTrovato(segmento))
            attuale.confermato == confermato -> Esito.Ok(null)
            else -> {
                parti.getValue(segmento.registrazioneId).assegna(segmento.segmentoId, attuale.voceId, confermato)
                Esito.Ok(EventoRevisione.SegmentoConfermato(incontroId, segmento, confermato))
            }
        }
    }

    private fun rifiutoDelBlocco(spostamenti: List<SpostamentoNellIncontro>): ErroreTrascrizione? {
        val visti = mutableSetOf<SegmentoRef>()
        return spostamenti.firstNotNullOfOrNull { m ->
            val s = trova(m.segmento)
            when {
                m.a == m.da || !visti.add(m.segmento) -> RiassegnazioneNonAmmessa(m.segmento.segmentoId, m.a)
                s == null || s.voceId != m.da || s.intervallo != m.intervallo || s.confermato || !esiste(m.a) ->
                    TrascrittoCambiato(m.segmento.registrazioneId)
                else -> null
            }
        }
    }

    /** Every Segmento of the Incontro with its Parte, in Parte order then INV-7 order (inizio, id). */
    private fun assegnati(): List<Pair<RegistrazioneId, Segmento>> = parti.values.flatMap { t ->
        t.segmenti.sortedWith(ORDINE_NELLA_PARTE).map { t.registrazioneId to it }
    }

    private fun trova(ref: SegmentoRef): Segmento? = parti[ref.registrazioneId]?.segmento(ref.segmentoId)

    private fun esiste(voce: VoceId): Boolean = parti.values.any { t -> t.segmenti.any { it.voceId == voce } }

    private fun refsDi(voce: VoceId): List<SegmentoRef> =
        assegnati().filter { it.second.voceId == voce }.map { (r, s) -> SegmentoRef(r, s.id) }

    private fun nuovaVoce(): VoceId = VoceId(_prossimaVoce++)

    /** Only the Voce and (with [conferma], set to true) the flag change: never id, intervallo, testo, Parte (INV-8). */
    private fun sposta(segmenti: Collection<SegmentoRef>, verso: VoceId, conferma: Boolean) {
        segmenti.forEach { ref ->
            val t = parti.getValue(ref.registrazioneId)
            t.assegna(ref.segmentoId, verso, t.segmento(ref.segmentoId)?.confermato == true || conferma)
        }
    }

    public companion object {
        private val ORDINE_NELLA_PARTE = compareBy<Segmento>({ it.intervallo.inizioMs }, { it.id.numero })

        /**
         * The empty root of [incontroId], before the first completion of any of its Parti (ADR 0035 §1): no Voce, the
         * counter at 1. It carries no event: the creation fact is the [completaParte] that follows in the same unit.
         */
        public fun crea(incontroId: IncontroId): VociDellIncontro = VociDellIncontro(incontroId, emptyList(), 1)

        /**
         * Rebuilds from persisted state; re-validates no rule (the DB is trusted) but refuses (`require`, programmer
         * error) a Trascritto of another Incontro, a Parte twice, or a counter not past every stored Voce — a stale
         * counter would mint an existing `VoceId` and silently merge Voci (INV-I4). The Trascritti are copied.
         */
        @RicostituzioneDaPersistenza
        public fun ricostituisci(
            incontroId: IncontroId,
            trascritti: List<Trascritto>,
            prossimaVoce: Int,
        ): VociDellIncontro {
            require(trascritti.all { it.incontroId == incontroId }) { "Trascritto di un altro Incontro in $incontroId" }
            require(trascritti.distinctBy { it.registrazioneId }.size == trascritti.size) {
                "Parte duplicata in $incontroId"
            }
            require(trascritti.all { t -> t.segmenti.all { it.voceId.numero < prossimaVoce } }) {
                "prossimaVoce $prossimaVoce non oltre le Voci di $incontroId"
            }
            return VociDellIncontro(incontroId, trascritti.map { it.copia(prossimaVoce) }, prossimaVoce)
        }
    }
}
