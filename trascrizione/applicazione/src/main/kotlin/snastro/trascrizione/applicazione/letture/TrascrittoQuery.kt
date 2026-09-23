package snastro.trascrizione.applicazione.letture

import snastro.kernel.RegistrazioneId
import snastro.trascrizione.applicazione.porte.LettoreRegistrazione
import snastro.trascrizione.applicazione.porte.TrascrittoRepository

/**
 * Read-model `trascritto-view`: builds [TrascrittoView], the Trascrizione slice of `schermata-registrazione`
 * (S3), joining [TrascrittoRepository] (segmenti/voci) with [LettoreRegistrazione] (titolo/dataRegistrazione/
 * durataMs). Read-only, no rule: order and labels come straight from the Trascritto (INV-7/INV-8).
 */
public class TrascrittoQuery(
    private val trascritti: TrascrittoRepository,
    private val registrazioni: LettoreRegistrazione,
) {
    /** AC-167: the full view of [registrazioneId]'s Trascritto; AC-168: `null` without one. */
    public fun vista(registrazioneId: RegistrazioneId): TrascrittoView? =
        trascritti.trova(registrazioneId)?.let { trascritto ->
            registrazioni.registrazione(registrazioneId)?.let { registrazione ->
                TrascrittoView(
                    registrazioneId = registrazioneId,
                    titolo = registrazione.titolo,
                    dataRegistrazione = registrazione.dataRegistrazione,
                    durataMs = registrazione.durataMs,
                    segmenti = trascritto.segmenti.map { s ->
                        SegmentoTrascrittoView(s.id, s.voceId, s.intervallo.inizioMs, s.intervallo.fineMs, s.testo)
                    },
                    voci = trascritto.voci.map { v -> VoceTrascrittoView(v.id, "Voce ${v.id.numero}") },
                )
            }
        }
}
