package snastro.trascrizione.applicazione.letture

import snastro.kernel.RegistrazioneId
import snastro.trascrizione.applicazione.porte.FaseElaborazione
import java.time.Instant

/**
 * One row of [StatiElaborazione] (AC-162, the `stati-elaborazione` `view_shape`): the processing state of
 * one Registrazione, as S2 (`RegistrazioniDelProgetto`) joins it. [fase] is only ever non-null for
 * [StatoElaborazioneVista.IN_CORSO] (AC-164); [motivoFallimento] only for `FALLITA`; [posizioneInCoda] only
 * for `IN_ATTESA` (AC-163); [numVoci] only for `COMPLETATA` (AC-165).
 */
public data class StatoRegistrazioneVista(
    val registrazioneId: RegistrazioneId,
    val stato: StatoElaborazioneVista,
    val fase: FaseElaborazione?,
    val avviataAlle: Instant?,
    val motivoFallimento: String?,
    val posizioneInCoda: Int?,
    val numVoci: Int?,
)
