package snastro.trascrizione.applicazione.porte

import snastro.kernel.CampioniAudio
import snastro.kernel.IntervalloMs

/**
 * Deterministic [Allineatore]: one [SegmentoGrezzo] per Turno, cut at the duration of the samples (a Turno
 * starting at or after it is dropped), text `voce <voceIndice> <inizioMs>-<fineMs>`. Overlaps are kept as given.
 */
public class AllineatoreFinta : Allineatore {
    override fun allinea(campioni: CampioniAudio, turni: List<Turno>): List<SegmentoGrezzo> {
        val durata = campioni.durataMs()
        return turni
            .filter { it.intervallo.inizioMs < durata }
            .map { t ->
                val intervallo = IntervalloMs(t.intervallo.inizioMs, minOf(t.intervallo.fineMs, durata))
                val testo = "voce ${t.voceIndice} ${intervallo.inizioMs}-${intervallo.fineMs}"
                SegmentoGrezzo(t.voceIndice, intervallo, testo)
            }
    }
}
