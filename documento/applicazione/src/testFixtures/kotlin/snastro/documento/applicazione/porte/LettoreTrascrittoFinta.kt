package snastro.documento.applicazione.porte

import snastro.kernel.RegistrazioneId

/**
 * In-memory [LettoreTrascritto] over fixed Published Language data (passes [LettoreTrascrittoContratto]).
 * It returns the segmenti in the pinned order whatever order they were given in.
 */
public class LettoreTrascrittoFinta(
    private val trascritti: Map<RegistrazioneId, TrascrittoTesto> = emptyMap(),
) : LettoreTrascritto {
    override fun trascritto(id: RegistrazioneId): TrascrittoTesto? =
        trascritti[id]?.let { it.copy(segmenti = it.segmenti.sortedWith(ORDINE)) }

    override fun registrazioniConTrascritto(): List<RegistrazioneId> = trascritti.keys.toList()

    private companion object {
        val ORDINE = compareBy<SegmentoVista>({ it.intervallo.inizioMs }, { it.segmentoId.numero })
    }
}
