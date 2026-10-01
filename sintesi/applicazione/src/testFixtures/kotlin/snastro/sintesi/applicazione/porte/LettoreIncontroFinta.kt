package snastro.sintesi.applicazione.porte

import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.kernel.unicaParteDi

/**
 * In-memory [LettoreIncontro] over Published Language data (passes [LettoreIncontroContratto]): reads [parti] live, so
 * a test may pass a mutable map and change it.
 */
public class LettoreIncontroFinta(
    private val parti: Map<IncontroId, List<RegistrazioneId>> = emptyMap(),
) : LettoreIncontro {
    override fun parti(incontroId: IncontroId): List<RegistrazioneId>? = parti[incontroId]?.toList()?.ifEmpty { null }
}

/**
 * A [LettoreIncontro] where EVERY Incontro is the one-Parte one of the test convention ([unicaParteDi]): for tests
 * about something else.
 */
public fun ogniIncontroConUnaParte(): LettoreIncontro = object : LettoreIncontro {
    override fun parti(incontroId: IncontroId): List<RegistrazioneId> = listOf(unicaParteDi(incontroId))
}
