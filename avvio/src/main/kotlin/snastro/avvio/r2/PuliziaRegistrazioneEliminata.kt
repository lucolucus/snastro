package snastro.avvio.r2

import snastro.kernel.DispatcherEventiInMemoria
import snastro.kernel.EventoPubblicato
import snastro.kernel.RegistrazioneId
import snastro.progetto.applicazione.eventi.RegistrazioneEliminata
import snastro.progetto.applicazione.porte.ArchivioAudio
import snastro.ui.lettore.LettoreAudio
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Level
import java.util.logging.Logger

/**
 * The `AbbonatoDopoCommit` of [RegistrazioneEliminata] that removes the Progetto-side files of the deleted
 * Registrazione and forgets its place (ADR 0020 §3 and §5, AC-632). It runs after commit only, never on rollback,
 * in this order:
 * 1. if the [lettore] holds that Registrazione, it is paused;
 * 2. [archivio] discards the audio copy (`audio/<id>.<ext>`);
 * 3. `cache/audio/<id>.wav` is deleted ([cancellaWavDerivato]);
 * 4. [dimenticaPosto] runs: the UI's `NavigazioneProgetto.dimentica`, attached by `ContenutoAppR2` while the
 *    project is shown.
 *
 * Every step is idempotent. An I/O failure is logged and never thrown (the command has already committed). The
 * `eliminazione_in_sospeso` row is not touched: `CompletaEliminazioniRegistrazioni` concludes it at the next open
 * (AC-633). The Documento `.md` is not handled here: `abbonato-documento` removes it on its per-key queue.
 */
internal class PuliziaRegistrazioneEliminata(
    dispatcher: DispatcherEventiInMemoria,
    private val lettore: LettoreAudio,
    private val archivio: ArchivioAudio,
    private val cartella: Path,
) {
    @Volatile
    var dimenticaPosto: (RegistrazioneId) -> Unit = {}

    init {
        dispatcher.registraDopoCommit { evento -> ricevi(evento) }
    }

    private fun ricevi(evento: EventoPubblicato) {
        if (evento !is RegistrazioneEliminata) return
        val id = evento.registrazioneId
        if (lettore.stato.value.registrazioneId == id) lettore.pausa()
        archivio.scarta(evento.riferimentoAudio)
        try {
            cancellaWavDerivato(cartella, id)
        } catch (e: IOException) {
            log.log(Level.WARNING, "WAV derivato di ${id.valore} non rimosso: resta per la prossima apertura", e)
        }
        dimenticaPosto(id)
    }

    private companion object {
        val log: Logger = Logger.getLogger(PuliziaRegistrazioneEliminata::class.java.name)
    }
}

/** Deletes `cache/audio/<id>.wav` under [cartella] (absent: a no-op). The project-folder layout is `:avvio`'s. */
internal fun cancellaWavDerivato(cartella: Path, id: RegistrazioneId) {
    Files.deleteIfExists(cartella.resolve("cache/audio/${id.valore}.wav"))
}
