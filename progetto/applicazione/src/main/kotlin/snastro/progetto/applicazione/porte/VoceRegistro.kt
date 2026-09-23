package snastro.progetto.applicazione.porte

import snastro.kernel.ProgettoId
import java.time.Instant

/** One known project in the per-user [RegistroProgetti]; [percorso] is the absolute path of its folder. */
public data class VoceRegistro(
    val progettoId: ProgettoId,
    val nome: String,
    val percorso: String,
    val numRegistrazioni: Int,
    val ultimaAttivita: Instant,
)
