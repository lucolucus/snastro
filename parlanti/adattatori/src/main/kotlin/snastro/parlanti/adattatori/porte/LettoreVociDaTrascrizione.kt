package snastro.parlanti.adattatori.porte

import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoRef
import snastro.parlanti.applicazione.porte.LettoreRegistrazione
import snastro.parlanti.applicazione.porte.LettoreVoci
import snastro.parlanti.applicazione.porte.SegmentoDiVoce
import snastro.parlanti.applicazione.porte.VoceVista
import snastro.trascrizione.applicazione.letture.VociDelTrascritto

/**
 * [LettoreVoci] over Trascrizione's public read API [VociDelTrascritto] (boundary `voci-per-parlanti`,
 * ADR 0002): calls the supplier's `voci`/`segmentiDiVoce` and maps their answer field by field into
 * this context's own [VoceVista]/[SegmentoDiVoce] — delegates, never re-decides. Both suppliers'
 * methods already carry their pinned order (Voci by voceId, each Voce's intervalli by inizio then
 * segmentoId — AC-98; segmenti by inizio then segmentoId — AC-550): copied over as-is, no re-sorting
 * here, and never the text (ADR 0019 §4.1).
 *
 * ONE-PARTE TRANSITION (D-0032, D-0037): Trascrizione's reads are still per Registrazione until
 * `voci-del-trascritto-incontro`, so the Incontro's Parte is read through [registrazioni] and an Incontro with more
 * than one Parte FAILS CLOSED (an [IllegalStateException], never a partial answer): no Incontro has a second Parte
 * before the I2 multi-file import, which waits for that block.
 */
public class LettoreVociDaTrascrizione(
    private val trascrizione: VociDelTrascritto,
    private val registrazioni: LettoreRegistrazione,
) : LettoreVoci {
    override fun voci(incontroId: IncontroId): List<VoceVista>? =
        unicaParte(incontroId)?.let { parte ->
            trascrizione.voci(parte)?.map { VoceVista(it.voceRef, intervalliPerParte = mapOf(parte to it.intervalli)) }
        }

    override fun segmenti(incontroId: IncontroId): List<SegmentoDiVoce>? =
        unicaParte(incontroId)?.let { parte ->
            trascrizione.segmentiDiVoce(parte)?.map {
                SegmentoDiVoce(SegmentoRef(parte, it.segmentoId), it.voceId, it.intervallo, it.confermato)
            }
        }

    private fun unicaParte(incontroId: IncontroId): RegistrazioneId? {
        val parti = registrazioni.parti(incontroId) ?: return null
        check(parti.size == 1) {
            "Incontro ${incontroId.valore} con ${parti.size} parti: lettura per Incontro non ancora disponibile " +
                "(transizione a una parte fino a voci-del-trascritto-incontro)"
        }
        return parti.single().registrazioneId
    }
}
