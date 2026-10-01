package snastro.sbobinatura.applicazione.porte

import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId

/**
 * In-memory [LettoreTrascritto] over fixed Published Language data (passes [LettoreTrascrittoContratto]).
 * It returns the segmenti in the pinned order whatever order they were given in. [parti] lists the Parti of each
 * Incontro already in Parte order, as Progetto gives them (transcribed or not); by default each Incontro has the
 * Parti of [trascritti] that carry it, in the map's order.
 */
public class LettoreTrascrittoFinta(
    private val trascritti: Map<RegistrazioneId, TrascrittoTesto> = emptyMap(),
    private val parti: Map<IncontroId, List<RegistrazioneId>> =
        trascritti.values.groupBy({ it.incontroId }, { it.registrazioneId }),
) : LettoreTrascritto {
    override fun trascritto(id: RegistrazioneId): TrascrittoTesto? =
        trascritti[id]?.let { it.copy(segmenti = it.segmenti.sortedWith(ORDINE)) }

    override fun partiConTrascritto(incontroId: IncontroId): List<RegistrazioneId> =
        parti[incontroId].orEmpty().filter { it in trascritti }

    override fun registrazioniConTrascritto(): List<RegistrazioneId> = trascritti.keys.toList()

    private companion object {
        val ORDINE = compareBy<SegmentoVista>({ it.intervallo.inizioMs }, { it.segmentoId.numero })
    }
}
