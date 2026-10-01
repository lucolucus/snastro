// Test builders of the Voci of one-Parte Incontri, for tests about something else than LettoreVoci.
@file:Suppress("MatchingDeclarationName", "Filename")

package snastro.parlanti.applicazione.porte

import snastro.kernel.IncontroId
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceRef
import snastro.kernel.unIncontroDi
import snastro.kernel.unicaParteDi

/** A [VoceVista] of [voceRef] speaking only in the one Parte of its test Incontro ([unicaParteDi]). */
public fun unaVoceVista(voceRef: VoceRef, intervalli: List<IntervalloMs>): VoceVista =
    VoceVista(voceRef, mapOf(unicaParteDi(voceRef) to intervalli))

/**
 * A [LettoreVoci] over per-Registrazione data, each Registrazione the one Parte of its test Incontro [unIncontroDi]:
 * a [LettoreVociFinta] that reads [voci] and [segmenti] LIVE (a test may change them afterwards).
 */
public fun lettoreVociDiUnicheParti(
    voci: Map<RegistrazioneId, List<VoceVista>> = emptyMap(),
    segmenti: Map<RegistrazioneId, List<SegmentoDiVoce>> = emptyMap(),
): LettoreVoci =
    object : LettoreVoci {
        override fun voci(incontroId: IncontroId): List<VoceVista>? = finta().voci(incontroId)

        override fun segmenti(incontroId: IncontroId): List<SegmentoDiVoce>? = finta().segmenti(incontroId)

        private fun finta() = LettoreVociFinta(voci.mapKeys { unIncontroDi(it.key) }, segmenti.mapKeys { unIncontroDi(it.key) })
    }
