package snastro.documento.adattatori.porte

import snastro.documento.applicazione.porte.LettoreTrascritto
import snastro.documento.applicazione.porte.SegmentoVista
import snastro.documento.applicazione.porte.TrascrittoTesto
import snastro.kernel.RegistrazioneId
import snastro.progetto.applicazione.letture.CatalogoRegistrazioni
import snastro.trascrizione.applicazione.letture.VociDelTrascritto

/**
 * [LettoreTrascritto] over Trascrizione's public read API [VociDelTrascritto] (the Trascritto's
 * Segmenti) and Progetto's [CatalogoRegistrazioni] (titolo + dataRegistrazione of the Registrazione)
 * — boundary `trascritto-per-documento` (ADR 0002): calls only the suppliers' `applicazione` query
 * API, maps their answers field by field into Documento's own [TrascrittoTesto] / [SegmentoVista] —
 * delegates, never re-decides. [VociDelTrascritto.segmenti] already returns them ordered by inizio
 * then segmentoId across Voci (its own contract, AC-99): copied over as-is, no re-sorting here.
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
                    titolo = registrazione.titolo,
                    dataRegistrazione = registrazione.dataRegistrazione,
                    segmenti = segmenti.map { SegmentoVista(it.segmentoId, it.voceId, it.intervallo, it.testo) },
                )
            }
        }

    override fun registrazioniConTrascritto(): List<RegistrazioneId> = trascrizione.registrazioniConTrascritto()
}
