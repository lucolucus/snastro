// Test builders of Trascrizione's own view of a Registrazione (one Parte of its own Incontro).
@file:Suppress("MatchingDeclarationName", "Filename")

package snastro.trascrizione.applicazione.porte

import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.kernel.unIncontroDi
import java.time.LocalDate

/** A [RegistrazioneVista] of [id], the one Parte of the Incontro [unIncontroDi] ([id]) — never equal to [id]. */
public fun unaRegistrazioneVista(
    id: RegistrazioneId,
    durataMs: Long = 3_600_000L,
    riferimentoAudio: RiferimentoAudio = RiferimentoAudio("audio/${id.valore}.m4a"),
): RegistrazioneVista = RegistrazioneVista(
    registrazioneId = id,
    progettoId = ProgettoId("progetto-1"),
    incontroId = unIncontroDi(id),
    titolo = "Registrazione ${id.valore}",
    riferimentoAudio = riferimentoAudio,
    dataRegistrazione = LocalDate.of(2026, 9, 21),
    durataMs = durataMs,
)

/** A [LettoreRegistrazione] that knows EVERY id, each as [unaRegistrazioneVista]: for tests about something else. */
public fun ogniRegistrazioneNota(): LettoreRegistrazione = object : LettoreRegistrazione {
    override fun registrazione(id: RegistrazioneId): RegistrazioneVista = unaRegistrazioneVista(id)
}
