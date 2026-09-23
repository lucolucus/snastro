package snastro.parlanti.applicazione.comandi

import snastro.kernel.DispatcherEventi
import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.kernel.UnitaDiLavoro
import snastro.kernel.VoceRef
import snastro.kernel.poi
import snastro.parlanti.applicazione.eventi.ImpronteRiallineate
import snastro.parlanti.applicazione.porte.DecodificatoreAudio
import snastro.parlanti.applicazione.porte.EstrattoreImpronta
import snastro.parlanti.applicazione.porte.LettoreVoci
import snastro.parlanti.applicazione.porte.ParlanteRepository
import snastro.parlanti.applicazione.porte.RigaImpronta
import snastro.parlanti.dominio.ImprontaVocale
import snastro.parlanti.dominio.SorgenteImpronta

/**
 * Use-case `RiallineaImpronte` (AC-292..AC-299, AC-301; freshness half of [INV-15], ADR 0012 Amendment (b)
 * point 3, ADR 0009 Amendment (b)), run after commit and at project open:
 * 1. one short read transaction lists the Registrazione's print rows and keeps the STALE ones — decided by
 *    [ImprontaVocale.obsoleta] against [SorgenteImpronta.di] of the Voce's current intervals and
 *    [EstrattoreImpronta.modello] — grouped per Voce; a row whose Voce (or Trascritto) no longer exists is
 *    skipped, its removal belongs to the revisione-policy;
 * 2. per Voce, OUTSIDE any transaction, decode the source and extract the print;
 * 3. per Voce, one short transaction re-reads the Voce and — only if its source is still the extracted one —
 *    compare-and-set UPDATEs each row ([ParlanteRepository.aggiornaImpronta]); it NEVER inserts, so a print
 *    purged meanwhile is not resurrected. A print that is not written is dropped (ADR 0009 (b)).
 *
 * [ImpronteRiallineate] is published (delivered after its commit) iff >= 1 row was updated — also when a
 * later Voce throws: that exception then propagates (ADR 0003, the caller retries), after the event.
 */
@Suppress("LongParameterList") // one parameter per collaborator: uow, reader, repo, 2 technical ports, eventi
public class RiallineaImpronteServizio(
    private val uow: UnitaDiLavoro,
    private val lettoreVoci: LettoreVoci,
    private val parlanti: ParlanteRepository,
    private val decodificatore: DecodificatoreAudio,
    private val estrattore: EstrattoreImpronta,
    private val eventi: DispatcherEventi,
) {
    public fun esegui(c: RiallineaImpronte): Esito<Unit> {
        val registrazioneId = c.registrazioneId
        val modello = estrattore.modello
        return uow.inTransazione { Esito.Ok(obsolete(registrazioneId, modello)) }
            .poi { daRiallineare -> riallinea(registrazioneId, daRiallineare, modello) }
    }

    /** Stale rows per Voce, each with the source it must be re-derived from; absent Voci are skipped (AC-299). */
    private fun obsolete(registrazioneId: RegistrazioneId, modello: String): List<VoceObsoleta> {
        val sorgenti = lettoreVoci.voci(registrazioneId).orEmpty()
            .filter { it.intervalli.isNotEmpty() }
            .associate { it.voceRef to SorgenteImpronta.di(it.intervalli) }
        return parlanti.impronteDiRegistrazione(registrazioneId)
            .filter { riga ->
                val sorgente = sorgenti[riga.voceRef]
                sorgente != null && ImprontaVocale.obsoleta(riga.sorgente, riga.modello, sorgente.chiave, modello)
            }
            .groupBy { it.voceRef }
            .map { (voceRef, righe) -> VoceObsoleta(voceRef, checkNotNull(sorgenti[voceRef]), righe) }
    }

    private fun riallinea(registrazioneId: RegistrazioneId, voci: List<VoceObsoleta>, modello: String): Esito<Unit> {
        var aggiornate = 0
        var esito: Esito<Unit> = Esito.Ok(Unit)
        val fallimento = runCatching {
            for (voce in voci) {
                when (val scritte = riallineaVoce(voce, modello)) {
                    is Esito.Ok -> aggiornate += scritte.valore
                    is Esito.Errore -> {
                        esito = scritte
                        break
                    }
                }
            }
        }.exceptionOrNull()
        if (aggiornate > 0) {
            runCatching { pubblica(registrazioneId) }
                .onSuccess { if (esito is Esito.Ok) esito = it }
                .onFailure { e -> fallimento?.addSuppressed(e) ?: throw e }
        }
        fallimento?.let { throw it }
        return esito
    }

    /** AC-297: decode + extract with no transaction open; then AC-293/AC-295 in one short transaction. */
    private fun riallineaVoce(voce: VoceObsoleta, modello: String): Esito<Int> {
        val campioni = decodificatore.campioni(voce.voceRef.registrazioneId, voce.sorgente.intervalli)
        val impronta = estrattore.estrai(campioni)
        return uow.inTransazione {
            val attuale = lettoreVoci.voci(voce.voceRef.registrazioneId)
                ?.find { it.voceRef == voce.voceRef }
                ?.takeIf { it.intervalli.isNotEmpty() }
            if (attuale == null || SorgenteImpronta.di(attuale.intervalli) != voce.sorgente) {
                Esito.Ok(0) // the Voce changed or vanished during the extraction: a later run converges
            } else {
                Esito.Ok(
                    voce.righe.count { riga ->
                        parlanti.aggiornaImpronta(riga, impronta, voce.sorgente.chiave, modello)
                    },
                )
            }
        }
    }

    private fun pubblica(registrazioneId: RegistrazioneId): Esito<Unit> =
        uow.inTransazione {
            eventi.pubblica(ImpronteRiallineate(registrazioneId))
            Esito.Ok(Unit)
        }
}

private data class VoceObsoleta(val voceRef: VoceRef, val sorgente: SorgenteImpronta, val righe: List<RigaImpronta>)
