package snastro.parlanti.applicazione.letture

import snastro.kernel.RegistrazioneId
import snastro.parlanti.applicazione.porte.AttribuzioneRepository
import snastro.parlanti.applicazione.porte.LettoreRegistrazione
import snastro.parlanti.applicazione.porte.LettoreVoci
import snastro.parlanti.applicazione.porte.vociDellaParte

/**
 * Parlanti slice of the S2 badge (`identificazione-registrazioni`, ux-proposal S2 + R1): for each
 * requested Registrazione, how many of its Voci still need identification. Read-only: no rule lives
 * here (RC-1) — [LettoreVoci] decides whether a Trascritto exists (INV-5), [AttribuzioneRepository]
 * the attributed count.
 */
public class IdentificazioneRegistrazioni(
    private val voci: LettoreVoci,
    private val attribuzioni: AttribuzioneRepository,
    private val registrazioni: LettoreRegistrazione,
) {
    /** AC-166: one entry per Registrazione of [registrazioneIds] with a Trascritto, in the given order. */
    public fun conteggi(registrazioneIds: List<RegistrazioneId>): List<ConteggioIdentificazione> =
        registrazioneIds.mapNotNull { id ->
            val vociDellaParte = voci.vociDellaParte(id, registrazioni) ?: return@mapNotNull null
            // ADR 0033 §4.1: an Attribuzione is per Voce of the Incontro: count those of this Parte's Voci.
            val numAttribuite = vociDellaParte.count { attribuzioni.trova(it.voceRef) != null }
            ConteggioIdentificazione(id, vociDellaParte.size - numAttribuite)
        }
}

/** One row of [IdentificazioneRegistrazioni.conteggi]: AC-166. */
public data class ConteggioIdentificazione(
    val registrazioneId: RegistrazioneId,
    val numVociDaIdentificare: Int,
)
