package snastro.trascrizione.dominio

import snastro.kernel.ElaborazioneId
import snastro.kernel.EventoDominio
import snastro.kernel.RegistrazioneId
import java.time.Instant

public data class ElaborazioneAccodata(
    val elaborazioneId: ElaborazioneId,
    val registrazioneId: RegistrazioneId,
    val creataAlle: Instant,
) : EventoDominio

public data class ElaborazioneAvviata(
    val elaborazioneId: ElaborazioneId,
    val registrazioneId: RegistrazioneId,
    val avviataAlle: Instant,
) : EventoDominio

public data class ElaborazioneCompletata(
    val elaborazioneId: ElaborazioneId,
    val registrazioneId: RegistrazioneId,
) : EventoDominio

public data class ElaborazioneFallita(
    val elaborazioneId: ElaborazioneId,
    val registrazioneId: RegistrazioneId,
    val motivo: String,
) : EventoDominio
