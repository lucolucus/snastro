package snastro.sintesi.dominio

import snastro.kernel.EventoDominio
import snastro.kernel.RegistrazioneId
import java.time.Instant

public data class RiassuntoRichiestoDominio(
    val riassuntoId: RiassuntoId,
    val registrazioneId: RegistrazioneId,
    val richiestoAlle: Instant,
) : EventoDominio

public data class RiassuntoAvviatoDominio(
    val riassuntoId: RiassuntoId,
    val registrazioneId: RegistrazioneId,
    val avviatoAlle: Instant,
) : EventoDominio

public data class RiassuntoFallitoDominio(
    val riassuntoId: RiassuntoId,
    val registrazioneId: RegistrazioneId,
    val motivo: MotivoFallimento,
) : EventoDominio
