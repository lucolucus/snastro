// Test builders of Parlanti's own view of a Registrazione (one Parte of its own Incontro).
@file:Suppress("MatchingDeclarationName", "Filename")

package snastro.parlanti.applicazione.porte

import snastro.kernel.IncontroId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.kernel.unIncontroDi
import snastro.kernel.unicaParteDi
import java.time.LocalDate

/** A [RegistrazioneVista] of [id], the one Parte of the Incontro [unIncontroDi] ([id]) — never equal to [id]. */
public fun unaRegistrazioneVista(
    id: RegistrazioneId,
    progettoId: ProgettoId = ProgettoId("progetto-1"),
): RegistrazioneVista =
    RegistrazioneVista(
        registrazioneId = id,
        progettoId = progettoId,
        incontroId = unIncontroDi(id),
        titolo = "Registrazione ${id.valore}",
        riferimentoAudio = RiferimentoAudio("audio/${id.valore}.m4a"),
        dataRegistrazione = LocalDate.of(2026, 9, 21),
        durataMs = 3_600_000L,
    )

/**
 * A [LettoreRegistrazione] that knows EVERY id as [unaRegistrazioneVista], each Incontro with its one Parte
 * ([unicaParteDi]): for tests about something else.
 */
public fun ogniRegistrazioneNota(progettoId: ProgettoId = ProgettoId("progetto-1")): LettoreRegistrazione =
    object : LettoreRegistrazione {
        override fun registrazione(id: RegistrazioneId): RegistrazioneVista = unaRegistrazioneVista(id, progettoId)

        override fun parti(incontroId: IncontroId): List<ParteDiIncontroParlanti> =
            unicaParteDi(incontroId).let { listOf(ParteDiIncontroParlanti(it, 1, unaRegistrazioneVista(it).dataRegistrazione)) }
    }
