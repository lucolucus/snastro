package snastro.ui.registrazioni

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import snastro.kernel.Esito
import snastro.kernel.IncontroId
import snastro.trascrizione.applicazione.comandi.AvviaElaborazioniDellIncontro
import snastro.ui.comandoConfermato
import snastro.ui.eccezioneRegistrata
import snastro.ui.testi.MESSAGGIO_ERRORE_GENERICO
import snastro.ui.testi.MESSAGGIO_NUMERO_PERSONE_NON_VALIDO
import snastro.ui.testi.messaggioPer

/**
 * The Incontro flow of S2 · Registrazioni (AC-I67, AC-I69): the chevron ([espandiIncontro], whose set this holder
 * alone owns, read back by the refresh through [espanso]), the Incontro's ONE 'Numero di persone' field and
 * 'Trascrivi N parti' — ONE [avviaElaborazioniDellIncontro] run on [io], the list refreshed through [s2] only after a
 * success. M3: one command per Incontro ([RigaIncontro.operazioneInCorso]); L269: the flag is reset in `finally`.
 */
internal class FlussoIncontro(
    private val s2: StatoS2,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val avviaElaborazioniDellIncontro: (AvviaElaborazioniDellIncontro) -> Esito<Unit>,
) {
    // AC-I69: the expanded Incontri — presenter state, so the chevron survives a round trip to S3 and every refresh.
    private val espansi = mutableSetOf<IncontroId>()

    /** AC-I69: whether Incontro [id]'s chevron is open — the refresh's merge reads it here, never a copy. */
    fun espanso(id: IncontroId): Boolean = id in espansi

    /** AC-I69: the chevron of [id]; the set lives here so it outlives S2's composition and every refresh. */
    fun espandiIncontro(id: IncontroId) {
        if (!espansi.add(id)) espansi.remove(id)
        aggiornaIncontro(id) { it.copy(espanso = id in espansi) }
    }

    /** AC-I67: the text of an Incontro's ONE 'Numero di persone' field, as typed (validated when 'Trascrivi' fires). */
    fun modificaNumeroPersoneIncontro(id: IncontroId, testo: String) =
        aggiornaIncontro(id) { it.copy(numeroPersone = testo) }

    /**
     * AC-I67: 'Trascrivi N parti' — the field validated like a row's (invalid → the same inline message, nothing
     * sent), then ONE `AvviaElaborazioniDellIncontro(id, n)` over the Incontro. A committing command (ADR 0041 §3): a
     * thrown `Exception` is logged and shows the generic message, an `Error` propagates (D-0065); L269: the Incontro
     * is released in `finally`.
     */
    fun avviaElaborazioniIncontro(id: IncontroId) {
        val incontro = incontroLibero(id) ?: return
        when (val campo = numeroPersoneCampo(incontro.numeroPersone)) {
            NumeroPersoneCampo.NonValido ->
                aggiornaIncontro(id) { it.copy(errore = MESSAGGIO_NUMERO_PERSONE_NON_VALIDO) }
            is NumeroPersoneCampo.Valido -> {
                aggiornaIncontro(id) { it.copy(operazioneInCorso = true, errore = null) }
                s2.azzeraAvviso()
                scope.launch {
                    try {
                        val avvia = AvviaElaborazioniDellIncontro(id, campo.numero)
                        val esito = eccezioneRegistrata("trascrivi $id") {
                            withContext(io) {
                                comandoConfermato("trascrivi $id") { avviaElaborazioniDellIncontro(avvia) }
                            }
                        }
                        val errore = when (esito) {
                            is Esito.Ok -> null
                            is Esito.Errore -> messaggioPer(esito.errore)
                            null -> MESSAGGIO_ERRORE_GENERICO
                        }
                        if (errore == null) s2.ricarica()
                        aggiornaIncontro(id) { it.copy(errore = errore) }
                    } finally {
                        aggiornaIncontro(id) { it.copy(operazioneInCorso = false) } // L269
                    }
                }
            }
        }
    }

    /** AC-I67: dismisses the inline message of Incontro [id]. */
    fun chiudiErroreIncontro(id: IncontroId) = aggiornaIncontro(id) { it.copy(errore = null) }

    private fun incontroLibero(id: IncontroId): RigaIncontro? =
        s2.dati?.incontri?.find { it.incontroId == id }
            ?.takeUnless { it.operazioneInCorso }

    private fun aggiornaIncontro(id: IncontroId, f: (RigaIncontro) -> RigaIncontro) =
        s2.aggiornaDati { d -> d.copy(incontri = d.incontri.map { if (it.incontroId == id) f(it) else it }) }
}
