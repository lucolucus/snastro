package snastro.sbobinatura.adattatori.porte

import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.progetto.applicazione.letture.CatalogoRegistrazioni
import snastro.sbobinatura.applicazione.porte.LettoreTrascritto
import snastro.sbobinatura.applicazione.porte.SegmentoVista
import snastro.sbobinatura.applicazione.porte.TrascrittoTesto
import snastro.trascrizione.applicazione.letture.VociDelTrascritto

/**
 * [LettoreTrascritto] over Trascrizione's public read API [VociDelTrascritto] (the Trascritto's
 * Segmenti) and Progetto's [CatalogoRegistrazioni] (titolo + dataRegistrazione of the Registrazione)
 * — boundary `trascritto-per-sbobinatura` (ADR 0002): calls only the suppliers' `applicazione` query
 * API, maps their answers field by field into Sbobinatura's own [TrascrittoTesto] / [SegmentoVista] —
 * delegates, never re-decides. [VociDelTrascritto.segmenti] already returns them ordered by inizio
 * then segmentoId across Voci (its own contract, AC-99): copied over as-is, no re-sorting here.
 *
 * [partiConTrascritto] never orders the Parti (ADR 0033 §4.1): Progetto's [CatalogoRegistrazioni.parti] is unordered
 * until `catalogo-incontro` gives the ordered Parti, so this TRANSITION (ADR 0033 §6, D-0037) answers for a one-Parte
 * Incontro only and fails closed on more.
 */
public class LettoreTrascrittoDaTrascrizione(
    private val trascrizione: VociDelTrascritto,
    private val progetto: CatalogoRegistrazioni,
) : LettoreTrascritto {
    override fun trascritto(id: RegistrazioneId): TrascrittoTesto? =
        trascrizione.segmenti(id)?.let { segmenti ->
            progetto.registrazione(id)?.let { registrazione ->
                TrascrittoTesto(
                    registrazioneId = id,
                    incontroId = registrazione.incontroId,
                    titolo = registrazione.titolo,
                    dataRegistrazione = registrazione.dataRegistrazione,
                    segmenti = segmenti.map { SegmentoVista(it.segmentoId, it.voceId, it.intervallo, it.testo) },
                )
            }
        }

    override fun partiConTrascritto(incontroId: IncontroId): List<RegistrazioneId> {
        val parti = progetto.parti(incontroId) ?: return emptyList()
        check(parti.size == 1) {
            "Incontro $incontroId con ${parti.size} Parti: l'ordine delle Parti e' di catalogo-incontro"
        }
        val conTrascritto = trascrizione.registrazioniConTrascritto().toSet()
        return parti.filter { it in conTrascritto }
    }

    override fun registrazioniConTrascritto(): List<RegistrazioneId> = trascrizione.registrazioniConTrascritto()
}
