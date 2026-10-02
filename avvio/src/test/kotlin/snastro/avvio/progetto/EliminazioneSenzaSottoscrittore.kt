package snastro.avvio.progetto

import snastro.avvio.coda.Campanello
import snastro.avvio.parlanti.ModuloParlanti
import snastro.avvio.sintesi.ModuloSintesi
import snastro.avvio.trascrizione.ModuloTrascrizione
import snastro.kernel.AbbonatoSincrono
import snastro.kernel.Esito
import snastro.kernel.EventoPubblicato
import snastro.kernel.GeneratoreIdFinto
import snastro.kernel.RegistrazioneId
import snastro.progetto.applicazione.comandi.EliminaRegistrazione
import snastro.progetto.applicazione.comandi.EliminaRegistrazioneServizio
import snastro.progetto.applicazione.porte.SondaAudioFinta
import java.nio.file.Path

/**
 * The real deleting service over a SECOND, self-consistent composition of the same database (its own unit of work,
 * dispatcher and modules), holding every synchronous subscriber BUT the one of [senza] (a subscriber class name).
 * Never the production dispatcher: that holds them all, and a half-wired one over another unit of work would fail
 * for the wrong reason.
 */
internal fun eliminaSenza(ambiente: AmbienteProgetto, senza: String, id: RegistrazioneId): Esito<Unit> {
    val cartella = Path.of(ambiente.progetto.percorso)
    val porte = PorteProgetto(ambiente.porte.database, ambiente.clock, cartella.toFile())
    val apertura = AperturaProgetto(
        ambiente.progetto.progettoId,
        cartella,
        ambiente.scope,
        ambiente.collaboratori.lettoreAudio,
        GeneratoreIdFinto(),
        ambiente.clock,
        SondaAudioFinta(emptyMap()),
    )
    val trascrizione = ModuloTrascrizione(porte, apertura, ambiente.app, Campanello())
    val moduli = listOf(
        ModuloSintesi(porte, apertura, ambiente.app, Campanello()),
        ModuloParlanti(porte, apertura, ambiente.app, trascrizione.collaboratori),
        trascrizione,
    )
    moduli.flatMap { m -> m.abbonatiSincroni() }
        .filter { a -> a.abbonato::class.simpleName != senza }
        .forEach { a ->
            porte.dispatcher.registraSincrono(
                object : AbbonatoSincrono {
                    override fun ricevi(evento: EventoPubblicato): Esito<Unit> =
                        if (a.evento.isInstance(evento)) a.abbonato.ricevi(evento) else Esito.Ok(Unit)
                },
            )
        }
    return EliminaRegistrazioneServizio(
        porte.unitaDiLavoro,
        porte.registrazioni,
        porte.incontri,
        porte.eliminazioniInSospeso,
        porte.dispatcher,
    ).esegui(EliminaRegistrazione(id))
}

internal fun causaRadice(e: Throwable): Throwable = generateSequence(e) { it.cause }.last()

/** True when the failure came out of the transaction's COMMIT (a deferred FK), not of a statement before it. */
internal fun fallitaAlCommit(e: Throwable): Boolean = generateSequence(e) { it.cause }
    .flatMap { it.stackTrace.asSequence() }
    .any { f -> f.methodName == "endTransaction" } // the driver runs COMMIT there
