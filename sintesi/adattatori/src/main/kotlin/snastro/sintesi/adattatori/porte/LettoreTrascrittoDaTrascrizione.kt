package snastro.sintesi.adattatori.porte

import snastro.kernel.RegistrazioneId
import snastro.sintesi.applicazione.porte.LettoreTrascritto
import snastro.sintesi.applicazione.porte.SegmentoSintesi
import snastro.sintesi.applicazione.porte.StatoParteSintesi
import snastro.trascrizione.applicazione.letture.StatiElaborazione
import snastro.trascrizione.applicazione.letture.StatoParte
import snastro.trascrizione.applicazione.letture.VociDelTrascritto

/**
 * [LettoreTrascritto] over Trascrizione's public read API — [VociDelTrascritto] for the Segmenti and
 * [StatiElaborazione] for the state of the latest Elaborazione — boundaries `trascritto-per-sintesi`
 * (ADR 0021 §3) and `porte-sintesi` (ADR 0033 §4): calls only the supplier's `applicazione` query API, never a
 * generated `*Queries` type (AC-S52), and maps its answers field by field into Sintesi's own
 * [SegmentoSintesi] — delegates, never re-decides (RC-1). [VociDelTrascritto.segmenti] already returns them ordered
 * by inizio then segmentoId across Voci (its own contract, AC-99): copied over as-is, no re-sorting here (INV-7).
 */
public class LettoreTrascrittoDaTrascrizione(
    private val voci: VociDelTrascritto,
    private val stati: StatiElaborazione,
) : LettoreTrascritto {
    override fun segmenti(r: RegistrazioneId): List<SegmentoSintesi>? =
        voci.segmenti(r)?.map { SegmentoSintesi(it.segmentoId, it.voceId, it.intervallo, it.testo) }

    /**
     * ADR 0033 §4: the supplier's own `statoParte` (open run, then Trascritto, then failed run), mapped case by case:
     * an exhaustive `when`, so a new supplier case fails to compile here instead of throwing at runtime.
     */
    override fun statoParte(r: RegistrazioneId): StatoParteSintesi = when (stati.statoParte(r)) {
        StatoParte.DA_TRASCRIVERE -> StatoParteSintesi.DA_TRASCRIVERE
        StatoParte.IN_TRASCRIZIONE -> StatoParteSintesi.IN_TRASCRIZIONE
        StatoParte.NON_RIUSCITA -> StatoParteSintesi.NON_RIUSCITA
        StatoParte.TRASCRITTA -> StatoParteSintesi.TRASCRITTA
    }
}
