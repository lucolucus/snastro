package snastro.ui.registrazione

import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.progetto.applicazione.letture.IncontroDelProgettoVista
import snastro.trascrizione.applicazione.letture.ParteRef
import java.time.LocalTime

/**
 * The two things S3 needs to be one Parte of a multi-part Incontro (ux-proposal "Screen S3"): [incontro]
 * reads the Incontro (its first Parte's title, this Parte's start time), [vaiAllaParte] opens S3 of another
 * Parte of it (`:avvio` binds the window's navigation; the per-window tab holder [SelezioneSchedaS3] keeps
 * the selected tab across the switch, AC-I74). [incontroDi] reads the Incontro a Parte belongs to by the Parte's own
 * id — the only handle S3 has on a Parte with no Trascritto yet ([RegistrazioneUiStato.ParteInAttesa], D-0051).
 * MANDATORY collaborator (ADR 0030 §1).
 */
class SorgentiParti(
    val incontro: (IncontroId) -> IncontroDelProgettoVista?,
    val incontroDi: (RegistrazioneId) -> IncontroDelProgettoVista?,
    val vaiAllaParte: (RegistrazioneId) -> Unit,
)

/**
 * AC-I74 header of a Parte of an Incontro with two or more Parti (`null` on a 1-Parte Incontro, INV-I3):
 * breadcrumb '… › [titoloIncontro] · [totale] parti', subtitle 'Parte [numero] di [totale] · data ora …',
 * and the switcher over [parti].
 */
data class IntestazioneParte(
    val numero: Int,
    val totale: Int,
    val titoloIncontro: String,
    val ora: LocalTime?,
    val parti: List<ParteRef>,
)
