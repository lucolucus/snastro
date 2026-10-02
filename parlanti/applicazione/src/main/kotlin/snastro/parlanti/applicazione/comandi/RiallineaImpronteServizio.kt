package snastro.parlanti.applicazione.comandi

import snastro.kernel.DispatcherEventi
import snastro.kernel.Esito
import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.kernel.UnitaDiLavoro
import snastro.kernel.VoceRef
import snastro.kernel.poi
import snastro.parlanti.applicazione.eventi.ImpronteRiallineate
import snastro.parlanti.applicazione.porte.DecodificatoreAudio
import snastro.parlanti.applicazione.porte.EstrattoreImpronta
import snastro.parlanti.applicazione.porte.LettoreRegistrazione
import snastro.parlanti.applicazione.porte.LettoreVoci
import snastro.parlanti.applicazione.porte.ParlanteRepository
import snastro.parlanti.applicazione.porte.RigaImpronta
import snastro.parlanti.dominio.ImprontaVocale
import snastro.parlanti.dominio.SorgenteImpronta

/**
 * Use-case `RiallineaImpronte` (AC-292..AC-299, AC-301, ADR 0035 §6 — keyed by the Incontro; freshness half of
 * [INV-15], ADR 0012 Amendment (b)
 * point 3, ADR 0009 Amendment (b)), run after commit and at project open:
 * 1. one short read transaction lists the print rows of every Parte of the Incontro and keeps the STALE ones —
 *    decided by
 *    [ImprontaVocale.obsoleta] against [SorgenteImpronta.di] of the Voce's current intervals and
 *    [EstrattoreImpronta.modello] — grouped per (Voce, Parte); a row whose Voce (or Trascritto) no longer exists is
 *    skipped, its removal belongs to the revisione-policy;
 * 2. per (Voce, Parte), OUTSIDE any transaction, decode the source and extract the print;
 * 3. per (Voce, Parte), one short transaction re-reads the Voce and — only if its source is still the extracted one —
 *    compare-and-set UPDATEs each row ([ParlanteRepository.aggiornaImpronta]); it NEVER inserts, so a print
 *    purged meanwhile is not resurrected. A print that is not written is dropped (ADR 0009 (b)).
 *
 * [ImpronteRiallineate] is published (delivered after its commit) iff >= 1 row was updated — also when a
 * later Voce throws: that exception then propagates (ADR 0003, the caller retries), after the event.
 */
@Suppress("LongParameterList") // one parameter per collaborator: uow, 2 readers, repo, 2 technical ports, eventi
public class RiallineaImpronteServizio(
    private val uow: UnitaDiLavoro,
    private val lettoreVoci: LettoreVoci,
    private val registrazioni: LettoreRegistrazione,
    private val parlanti: ParlanteRepository,
    private val decodificatore: DecodificatoreAudio,
    private val estrattore: EstrattoreImpronta,
    private val eventi: DispatcherEventi,
) {
    public fun esegui(c: RiallineaImpronte): Esito<Unit> {
        val incontroId = c.incontroId
        val modello = estrattore.modello
        return uow.inTransazione { Esito.Ok(obsolete(incontroId, modello)) }
            .poi { daRiallineare -> riallinea(incontroId, daRiallineare, modello) }
    }

    /**
     * Stale rows per (Voce, Parte), each with the source it must be re-derived from; absent Voci are skipped
     * (AC-299). Every print of the Incontro is sourced from one of its Parti ([INV-I8b] purges the others).
     */
    private fun obsolete(incontroId: IncontroId, modello: String): List<VoceObsoleta> {
        val sorgenti = sorgentiDelleVoci(incontroId)
        return registrazioni.parti(incontroId).orEmpty().flatMap { parte ->
            parlanti.impronteDiRegistrazione(parte.registrazioneId)
                .filter { riga ->
                    val sorgente = sorgenti[riga.voceRef to riga.parte]
                    sorgente != null && ImprontaVocale.obsoleta(riga.sorgente, riga.modello, sorgente.chiave, modello)
                }
                .groupBy { it.voceRef }
                .map { (voceRef, righe) ->
                    val sorgente = checkNotNull(sorgenti[voceRef to parte.registrazioneId])
                    VoceObsoleta(voceRef, parte.registrazioneId, sorgente, righe)
                }
        }
    }

    /** The current source of each (Voce, Parte) where the Voce speaks. */
    private fun sorgentiDelleVoci(incontroId: IncontroId): Map<Pair<VoceRef, RegistrazioneId>, SorgenteImpronta> =
        lettoreVoci.voci(incontroId).orEmpty().flatMap { v ->
            v.intervalliPerParte.filterValues { it.isNotEmpty() }
                .map { (parte, intervalli) -> (v.voceRef to parte) to SorgenteImpronta.di(intervalli) }
        }.toMap()

    private fun riallinea(incontroId: IncontroId, voci: List<VoceObsoleta>, modello: String): Esito<Unit> {
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
            runCatching { pubblica(incontroId) }
                .onSuccess { if (esito is Esito.Ok) esito = it }
                .onFailure { e -> fallimento?.addSuppressed(e) ?: throw e }
        }
        fallimento?.let { throw it }
        return esito
    }

    /** AC-297: decode + extract with no transaction open; then AC-293/AC-295 in one short transaction. */
    private fun riallineaVoce(voce: VoceObsoleta, modello: String): Esito<Int> {
        val campioni = decodificatore.campioni(voce.parte, voce.sorgente.intervalli)
        val impronta = estrattore.estrai(campioni)
        return uow.inTransazione {
            val attuale = sorgentiDelleVoci(voce.voceRef.incontroId)[voce.voceRef to voce.parte]
            if (attuale != voce.sorgente) {
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

    private fun pubblica(incontroId: IncontroId): Esito<Unit> =
        uow.inTransazione {
            eventi.pubblica(ImpronteRiallineate(incontroId))
            Esito.Ok(Unit)
        }
}

/** The stale print rows of [voceRef] extracted from the Parte [parte] (the rows read by `impronteDiRegistrazione`). */
private data class VoceObsoleta(
    val voceRef: VoceRef,
    val parte: RegistrazioneId,
    val sorgente: SorgenteImpronta,
    val righe: List<RigaImpronta>,
)
