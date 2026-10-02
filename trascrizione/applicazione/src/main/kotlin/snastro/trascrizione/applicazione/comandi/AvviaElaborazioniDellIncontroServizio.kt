package snastro.trascrizione.applicazione.comandi

import snastro.kernel.DispatcherEventi
import snastro.kernel.ElaborazioneId
import snastro.kernel.Esito
import snastro.kernel.GeneratoreId
import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.kernel.UnitaDiLavoro
import snastro.kernel.poi
import snastro.trascrizione.applicazione.eventi.ElaborazioneAvviata
import snastro.trascrizione.applicazione.porte.ElaborazioneRepository
import snastro.trascrizione.applicazione.porte.LettoreRegistrazione
import snastro.trascrizione.applicazione.porte.ParteDiIncontro
import snastro.trascrizione.applicazione.porte.VociDellIncontroRepository
import snastro.trascrizione.dominio.Elaborazione
import snastro.trascrizione.dominio.ErroreTrascrizione.IncontroNonTrovato
import snastro.trascrizione.dominio.ErroreTrascrizione.NessunaParteDaTrascrivere
import snastro.trascrizione.dominio.NumeroPersone
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * ADR 0039 (AC-I33): in ONE transaction, reads the ordered Parti ([LettoreRegistrazione.parti]) and, for each Parte
 * with neither a Trascritto nor an open Elaborazione (INV-4 per Registrazione, unchanged), accoda one
 * `Elaborazione` `in_attesa` carrying the same [NumeroPersone] and publishes one [ElaborazioneAvviata]
 * (after-commit subscribers). One clock read `t`, then `creataAlle` = `t, t + 1 ms, t + 2 ms, …` in Parte order, so
 * the shared FIFO (`creata_alle`, id) claims the Parti in order even when the clock does not move. An unknown
 * Incontro is `IncontroNonTrovato`, no eligible Parte is `NessunaParteDaTrascrivere`, an invalid number is
 * `NumeroPersoneFuoriIntervallo`; each leaves the store unchanged. No rule lives here: eligibility reads the
 * Trascritto via the repository and the open Elaborazioni via [ElaborazioneRepository].
 */
@Suppress("LongParameterList") // one port per collaborator: clock, ids, Parti, Elaborazioni, Trascritti, events
public class AvviaElaborazioniDellIncontroServizio(
    private val uow: UnitaDiLavoro,
    private val generatoreId: GeneratoreId,
    private val orologio: Clock,
    private val registrazioni: LettoreRegistrazione,
    private val elaborazioni: ElaborazioneRepository,
    private val trascritti: VociDellIncontroRepository,
    private val eventi: DispatcherEventi,
) {
    public fun esegui(comando: AvviaElaborazioniDellIncontro): Esito<Unit> = uow.inTransazione {
        val incontroId = comando.incontroId
        val parti = registrazioni.parti(incontroId)
            ?: return@inTransazione Esito.Errore(IncontroNonTrovato(incontroId))
        numeroPersone(comando.numeroPersone).poi { accodaLeParti(incontroId, parti, it) }
    }

    private fun numeroPersone(n: Int?): Esito<NumeroPersone?> = n?.let(NumeroPersone::di) ?: Esito.Ok(null)

    private fun accodaLeParti(
        incontroId: IncontroId,
        parti: List<ParteDiIncontro>,
        numero: NumeroPersone?,
    ): Esito<Unit> {
        val daTrascrivere = parti.map { it.registrazioneId }.filter { r ->
            trascritti.trascritto(r) == null && elaborazioni.diRegistrazione(r).none { it.aperta }
        }
        return if (daTrascrivere.isEmpty()) {
            Esito.Errore(NessunaParteDaTrascrivere(incontroId))
        } else {
            accoda(daTrascrivere, numero)
        }
    }

    private fun accoda(parti: List<RegistrazioneId>, numero: NumeroPersone?): Esito<Unit> {
        val t = orologio.instant()
        return parti.foldIndexed<RegistrazioneId, Esito<Unit>>(Esito.Ok(Unit)) { i, esito, registrazioneId ->
            esito.poi { accodaUna(registrazioneId, t.plus(i.toLong(), ChronoUnit.MILLIS), numero) }
        }
    }

    private fun accodaUna(registrazioneId: RegistrazioneId, alle: Instant, numero: NumeroPersone?): Esito<Unit> {
        val creata = Elaborazione.accoda(ElaborazioneId(generatoreId.nuovo()), registrazioneId, alle, numero)
        return elaborazioni.salva(creata.aggregato).poi {
            eventi.pubblica(ElaborazioneAvviata(registrazioneId, alle))
            Esito.Ok(Unit)
        }
    }
}
