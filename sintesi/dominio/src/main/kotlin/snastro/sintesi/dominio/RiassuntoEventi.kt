package snastro.sintesi.dominio

import snastro.kernel.EventoDominio
import snastro.kernel.IncontroId
import java.time.Instant

public data class RiassuntoRichiestoDominio(
    val riassuntoId: RiassuntoId,
    val incontroId: IncontroId,
    val richiestoAlle: Instant,
) : EventoDominio

public data class RiassuntoAvviatoDominio(
    val riassuntoId: RiassuntoId,
    val incontroId: IncontroId,
    val avviatoAlle: Instant,
) : EventoDominio

public data class RiassuntoFallitoDominio(
    val riassuntoId: RiassuntoId,
    val incontroId: IncontroId,
    val motivo: MotivoFallimento,
) : EventoDominio
