package snastro.trascrizione.applicazione.letture

import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoRef
import snastro.kernel.VoceRef
import snastro.trascrizione.applicazione.porte.LettoreRegistrazione
import snastro.trascrizione.applicazione.porte.VociDellIncontroRepository
import snastro.trascrizione.dominio.Trascritto

/**
 * Public query API of the Trascrizione context (Published Language): the shapes read-models of other
 * contexts are built from (`voci-per-parlanti`, `trascritto-per-sbobinatura`) — those consumer-owned
 * ports map [VoceVista] / [SegmentoVista] into their own DTOs, never re-deciding anything (RC-1).
 * Read-only: every method reads one Parte through [VociDellIncontroRepository.trascritto] (ADR 0035 §1); no rule
 * lives here.
 */
public class VociDelTrascritto(
    private val trascritti: VociDellIncontroRepository,
    private val registrazioni: LettoreRegistrazione,
) {
    /** AC-98: the Voci of [registrazioneId]'s Trascritto, ordered by id, or `null` without one (INV-5). */
    public fun voci(registrazioneId: RegistrazioneId): List<VoceVista>? =
        trascritti.trascritto(registrazioneId)?.let { t ->
            t.voci.map { voce -> VoceVista(VoceRef(t.incontroId, voce.id), voce.segmenti.map { it.intervallo }) }
        }

    /** AC-99: every Segmento of [registrazioneId]'s Trascritto, ordered across Voci, or `null` without one. */
    public fun segmenti(registrazioneId: RegistrazioneId): List<SegmentoVista>? =
        trascritti.trascritto(registrazioneId)?.segmenti?.map { segmento ->
            SegmentoVista(segmento.id, segmento.voceId, segmento.intervallo, segmento.testo)
        }

    /**
     * AC-550 (ADR 0019): every Segmento of [registrazioneId]'s Trascritto once, ordered by (inizio, segmentoId),
     * with its `confermato` flag as stored and no text, or `null` without a Trascritto.
     */
    public fun segmentiDiVoce(registrazioneId: RegistrazioneId): List<SegmentoDiVoceVista>? =
        trascritti.trascritto(registrazioneId)?.segmenti?.map { segmento ->
            SegmentoDiVoceVista(segmento.id, segmento.voceId, segmento.intervallo, segmento.confermato)
        }

    /** AC-100: every Registrazione that has a Trascritto. */
    public fun registrazioniConTrascritto(): List<RegistrazioneId> = trascritti.conTrascritto()

    /**
     * AC-I39: the Voci of the Incontro [incontroId], each ONCE with its intervals per transcribed Parte (Parte order),
     * or `null` when no Parte is transcribed. One `trova` = one `LetturaCoerente` snapshot (ADR 0029, AC-I42).
     */
    public fun voci(incontroId: IncontroId): List<VoceIncontroVista>? = trascrittiInOrdine(incontroId)?.let { parti ->
        parti.flatMap { it.voci }.map { it.id }.distinct().sortedBy { it.numero }.map { voceId ->
            VoceIncontroVista(
                VoceRef(incontroId, voceId),
                parti.mapNotNull { t -> t.voci.firstOrNull { it.id == voceId }?.let { v -> t.registrazioneId to v } }
                    .associate { (r, v) -> r to v.segmenti.map { it.intervallo } },
            )
        }
    }

    /**
     * AC-I39: every Segmento of the Incontro [incontroId] as a [SegmentoRef] with its Incontro Voce, in Parte order
     * then by (inizio, segmentoId) within a Parte, or `null` when no Parte is transcribed.
     */
    public fun segmenti(incontroId: IncontroId): List<SegmentoDiVoceIncontro>? =
        trascrittiInOrdine(incontroId)?.flatMap { t ->
            t.segmenti.sortedWith(compareBy({ it.intervallo.inizioMs }, { it.id.numero })).map {
                SegmentoDiVoceIncontro(SegmentoRef(t.registrazioneId, it.id), it.voceId, it.intervallo, it.confermato)
            }
        }

    /** AC-I40: the Parti of [incontroId] that have a Trascritto, in Parte order (catalogo-incontro). */
    public fun partiConTrascritto(incontroId: IncontroId): List<RegistrazioneId> =
        trascrittiInOrdine(incontroId)?.map { it.registrazioneId } ?: emptyList()

    /** AC-I40: the text of [registrazioneId]'s Trascritto, carrying its `incontroId`, or `null` without one. */
    public fun trascritto(registrazioneId: RegistrazioneId): TrascrittoTesto? =
        trascritti.trascritto(registrazioneId)?.let { t ->
            TrascrittoTesto(
                registrazioneId,
                t.incontroId,
                t.segmenti.map { SegmentoVista(it.id, it.voceId, it.intervallo, it.testo) },
            )
        }

    /** The transcribed Parti of [incontroId], in the order Progetto gives (never re-ordered here); `null` if none. */
    private fun trascrittiInOrdine(incontroId: IncontroId): List<Trascritto>? {
        val ordine = registrazioni.parti(incontroId)?.map { it.registrazioneId }
        val radice = ordine?.let { trascritti.trova(incontroId) }
        return radice?.let { r -> ordine.mapNotNull { r.trascritto(it) }.ifEmpty { null } }
    }
}
