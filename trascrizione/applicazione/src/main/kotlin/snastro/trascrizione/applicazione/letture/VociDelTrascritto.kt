package snastro.trascrizione.applicazione.letture

import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceRef
import snastro.trascrizione.applicazione.porte.TrascrittoRepository

/**
 * Public query API of the Trascrizione context (Published Language): the shapes read-models of other
 * contexts are built from (`voci-per-parlanti`, `trascritto-per-documento`) — those consumer-owned
 * ports map [VoceVista] / [SegmentoVista] into their own DTOs, never re-deciding anything (RC-1).
 * Read-only: every method reads through [TrascrittoRepository], no rule lives here.
 */
public class VociDelTrascritto(private val trascritti: TrascrittoRepository) {
    /** AC-98: the Voci of [registrazioneId]'s Trascritto, ordered by id, or `null` without one (INV-5). */
    public fun voci(registrazioneId: RegistrazioneId): List<VoceVista>? =
        trascritti.trova(registrazioneId)?.voci?.map { voce ->
            VoceVista(VoceRef(registrazioneId, voce.id), voce.segmenti.map { it.intervallo })
        }

    /** AC-99: every Segmento of [registrazioneId]'s Trascritto, ordered across Voci, or `null` without one. */
    public fun segmenti(registrazioneId: RegistrazioneId): List<SegmentoVista>? =
        trascritti.trova(registrazioneId)?.segmenti?.map { segmento ->
            SegmentoVista(segmento.id, segmento.voceId, segmento.intervallo, segmento.testo)
        }

    /**
     * AC-550 (ADR 0019): every Segmento of [registrazioneId]'s Trascritto once, ordered by (inizio, segmentoId),
     * with its `confermato` flag as stored and no text, or `null` without a Trascritto.
     */
    public fun segmentiDiVoce(registrazioneId: RegistrazioneId): List<SegmentoDiVoceVista>? =
        trascritti.trova(registrazioneId)?.segmenti?.map { segmento ->
            SegmentoDiVoceVista(segmento.id, segmento.voceId, segmento.intervallo, segmento.confermato)
        }

    /** AC-100: every Registrazione that has a Trascritto. */
    public fun registrazioniConTrascritto(): List<RegistrazioneId> = trascritti.conTrascritto()
}
