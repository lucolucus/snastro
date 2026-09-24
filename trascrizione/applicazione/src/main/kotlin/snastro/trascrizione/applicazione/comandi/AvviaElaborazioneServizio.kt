package snastro.trascrizione.applicazione.comandi

import snastro.kernel.ElaborazioneId
import snastro.kernel.Esito
import snastro.kernel.GeneratoreId
import snastro.kernel.RegistrazioneId
import snastro.kernel.UnitaDiLavoro
import snastro.kernel.poi
import snastro.trascrizione.applicazione.porte.ElaborazioneRepository
import snastro.trascrizione.applicazione.porte.LettoreRegistrazione
import snastro.trascrizione.dominio.Elaborazione
import snastro.trascrizione.dominio.ErroreTrascrizione.ElaborazioneGiaAperta
import snastro.trascrizione.dominio.ErroreTrascrizione.RegistrazioneNonTrovata
import snastro.trascrizione.dominio.NumeroPersone
import java.time.Clock

/**
 * AC-64..AC-67: enqueues a new `Elaborazione` `in_attesa` for [AvviaElaborazione.registrazioneId].
 * INV-4 is owned by [Elaborazione] / [ElaborazioneRepository] (RC-1): this service only PRE-CHECKS it
 * from [ElaborazioneRepository.diRegistrazione] (ADR 0007) — the partial unique indexes are the
 * backstop, surfaced by [ElaborazioneRepository.salva] as the same `ErroreTrascrizione`, returned
 * unchanged (AC-66). [AvviaElaborazione.numeroPersone] is validated by [NumeroPersone.di] before any write
 * and fixed on the new Elaborazione (AC-369, ADR 0014).
 */
public class AvviaElaborazioneServizio(
    private val uow: UnitaDiLavoro,
    private val generatoreId: GeneratoreId,
    private val orologio: Clock,
    private val registrazioni: LettoreRegistrazione,
    private val elaborazioni: ElaborazioneRepository,
) {
    public fun esegui(comando: AvviaElaborazione): Esito<Unit> = uow.inTransazione {
        val registrazioneId = comando.registrazioneId
        if (registrazioni.registrazione(registrazioneId) == null) {
            return@inTransazione Esito.Errore(RegistrazioneNonTrovata(registrazioneId))
        }
        numeroPersone(comando.numeroPersone).poi { creaSeAssente(registrazioneId, it) }
    }

    private fun numeroPersone(n: Int?): Esito<NumeroPersone?> = n?.let(NumeroPersone::di) ?: Esito.Ok(null)

    private fun creaSeAssente(registrazioneId: RegistrazioneId, numeroPersone: NumeroPersone?): Esito<Unit> {
        val esistenti = elaborazioni.diRegistrazione(registrazioneId)
        return when {
            esistenti.any { it.aperta } -> Esito.Errore(ElaborazioneGiaAperta(registrazioneId))
            else -> {
                val id = ElaborazioneId(generatoreId.nuovo())
                val creata = Elaborazione.accoda(id, registrazioneId, orologio.instant(), numeroPersone)
                elaborazioni.salva(creata.aggregato)
            }
        }
    }
}
