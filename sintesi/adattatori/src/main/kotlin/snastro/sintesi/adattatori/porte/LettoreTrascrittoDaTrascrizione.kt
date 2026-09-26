package snastro.sintesi.adattatori.porte

import snastro.kernel.RegistrazioneId
import snastro.sintesi.applicazione.porte.LettoreTrascritto
import snastro.sintesi.applicazione.porte.SegmentoSintesi
import snastro.trascrizione.applicazione.letture.StatiElaborazione
import snastro.trascrizione.applicazione.letture.StatoElaborazioneVista
import snastro.trascrizione.applicazione.letture.VociDelTrascritto

/**
 * [LettoreTrascritto] over Trascrizione's public read API — [VociDelTrascritto] for the Segmenti and
 * [StatiElaborazione] for the state of the latest Elaborazione — boundary `trascritto-per-sintesi`
 * (ADR 0021 §3): calls only the supplier's `applicazione` query API, never a generated `*Queries` type
 * (AC-S52), and maps its answers field by field into Sintesi's own [SegmentoSintesi] — delegates, never
 * re-decides (RC-1). [VociDelTrascritto.segmenti] already returns them ordered by inizio then segmentoId
 * across Voci (its own contract, AC-99): copied over as-is, no re-sorting here (INV-7).
 */
public class LettoreTrascrittoDaTrascrizione(
    private val voci: VociDelTrascritto,
    private val stati: StatiElaborazione,
) : LettoreTrascritto {
    override fun segmenti(r: RegistrazioneId): List<SegmentoSintesi>? =
        voci.segmenti(r)?.map { SegmentoSintesi(it.segmentoId, it.voceId, it.intervallo, it.testo) }

    override fun elaborazioneAperta(r: RegistrazioneId): Boolean = stati.stati(listOf(r)).single().stato in APERTI

    private companion object {
        val APERTI = setOf(StatoElaborazioneVista.IN_ATTESA, StatoElaborazioneVista.IN_CORSO)
    }
}
