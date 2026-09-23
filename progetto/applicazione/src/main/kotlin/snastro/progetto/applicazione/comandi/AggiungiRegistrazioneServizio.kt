package snastro.progetto.applicazione.comandi

import snastro.kernel.DispatcherEventi
import snastro.kernel.Esito
import snastro.kernel.GeneratoreId
import snastro.kernel.RegistrazioneId
import snastro.kernel.UnitaDiLavoro
import snastro.kernel.poi
import snastro.progetto.applicazione.eventi.RegistrazioneAggiunta
import snastro.progetto.applicazione.porte.ArchivioAudio
import snastro.progetto.applicazione.porte.ProgettoRepository
import snastro.progetto.applicazione.porte.RegistrazioneRepository
import snastro.progetto.applicazione.porte.SondaAudio
import snastro.progetto.dominio.Registrazione
import java.time.Clock

/**
 * Use-case `AggiungiRegistrazione` (AC-56..AC-61, ADR 0010): probes the source, copies it into
 * `audio/` (ADR 0010: verified copy, then commit the row — a failed copy creates nothing), creates
 * the Registrazione (titolo = source file name without extension, durata/data from the probe) and
 * publishes `RegistrazioneAggiunta` — whose SYNC subscriber auto-starts the Elaborazione (ADR 0012
 * R2, out of scope here). If the transaction does not commit after a successful copy — a sync
 * subscriber's `Esito.Errore` (AC-60) or a thrown exception (sync subscriber throw, or any
 * SQLite/IO fault at save/commit) — the copied file is discarded so no file is left behind without
 * its Registrazione; the discard runs from a `finally` so it still happens when the transaction
 * throws instead of returning.
 */
@Suppress("LongParameterList") // one parameter per collaborator: uow, id/clock, 2 repos, 2 technical ports, eventi
public class AggiungiRegistrazioneServizio(
    private val uow: UnitaDiLavoro,
    private val generatoreId: GeneratoreId,
    private val clock: Clock,
    private val progetti: ProgettoRepository,
    private val registrazioni: RegistrazioneRepository,
    private val sonda: SondaAudio,
    private val archivio: ArchivioAudio,
    private val eventi: DispatcherEventi,
) {
    public fun esegui(c: AggiungiRegistrazione): Esito<Unit> {
        val progetto = requireNotNull(progetti.trova()) { "AggiungiRegistrazione richiede un Progetto gia' creato" }
        return sonda.sonda(c.percorsoSorgente).poi { info ->
            val id = RegistrazioneId(generatoreId.nuovo())
            archivio.copia(c.percorsoSorgente, id).poi { riferimento ->
                var confermata = false
                try {
                    uow.inTransazione {
                        val creato = Registrazione.aggiungi(
                            id = id,
                            progettoId = progetto.id,
                            titolo = titoloDa(c.percorsoSorgente),
                            riferimentoAudio = riferimento,
                            durataMs = info.durataMs,
                            dataRegistrazione = info.dataFile,
                            aggiuntaAlle = clock.instant(),
                        )
                        registrazioni.salva(creato.aggregato)
                        eventi.pubblica(creato.evento.pubblicato())
                        Esito.Ok(Unit)
                    }.also { confermata = it is Esito.Ok }
                } finally {
                    // AC-60: no file without its row, whether the transaction returns Errore or throws
                    if (!confermata) archivio.scarta(riferimento)
                }
            }
        }
    }
}

/**
 * The source file's name without its extension (AC-56); no path separator survives in a titolo.
 * Falls back to the full file name when stripping the extension would empty it (a dotfile like
 * `.m4a`, whose "extension" is the whole name), and further to a fixed placeholder when even the
 * file name is empty (a path ending in a separator) — both keep the titolo non-blank without
 * inventing structure the path doesn't have.
 */
private fun titoloDa(percorsoSorgente: String): String {
    val nomeFile = percorsoSorgente.substringAfterLast('/').substringAfterLast('\\')
    val senzaEstensione = nomeFile.substringBeforeLast('.', missingDelimiterValue = nomeFile)
    return senzaEstensione.ifBlank { nomeFile }.ifBlank { "registrazione" }
}

private fun snastro.progetto.dominio.RegistrazioneAggiunta.pubblicato(): RegistrazioneAggiunta =
    RegistrazioneAggiunta(registrazioneId = id, progettoId = progettoId)
