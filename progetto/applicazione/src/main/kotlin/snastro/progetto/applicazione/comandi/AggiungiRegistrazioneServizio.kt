package snastro.progetto.applicazione.comandi

import snastro.kernel.DispatcherEventi
import snastro.kernel.Esito
import snastro.kernel.GeneratoreId
import snastro.kernel.IncontroId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.kernel.UnitaDiLavoro
import snastro.kernel.mappa
import snastro.kernel.poi
import snastro.progetto.applicazione.eventi.RegistrazioneAggiunta
import snastro.progetto.applicazione.porte.ArchivioAudio
import snastro.progetto.applicazione.porte.ErroreApplicazioneProgetto
import snastro.progetto.applicazione.porte.IncontroRepository
import snastro.progetto.applicazione.porte.InfoAudio
import snastro.progetto.applicazione.porte.ProgettoRepository
import snastro.progetto.applicazione.porte.RegistrazioneRepository
import snastro.progetto.applicazione.porte.SondaAudio
import snastro.progetto.dominio.Incontro
import snastro.progetto.dominio.OraDiInizio
import snastro.progetto.dominio.Registrazione
import java.text.Normalizer
import java.time.Clock
import java.time.temporal.ChronoUnit

/**
 * Use-case `AggiungiRegistrazione` (AC-56..AC-61, AC-322..324, ADR 0010): probes the source, copies it into
 * `audio/` (ADR 0010: verified copy, then commit the row — a failed copy creates nothing), creates
 * the Registrazione (titolo = source file name without extension, made unique in the Progetto with
 * ` (2)`, ` (3)`… by [TitoloRegistrazione] — AC-322..324; durata/data from the probe) and
 * publishes `RegistrazioneAggiunta` (after-commit consumers only; no composition registers a sync
 * subscriber and importing starts no Elaborazione, ADR 0014 — the dispatcher's sync mechanism stays,
 * ADR 0012). If the transaction does not commit after a successful copy — ANY sync subscriber's
 * `Esito.Errore` (AC-60) or a thrown exception (sync subscriber throw, or any
 * SQLite/IO fault at save/commit) — the copied file is discarded so no file is left behind without
 * its Registrazione; the discard runs from a `use` (a `finally`) so it still happens when the transaction
 * throws instead of returning. If that check of the stored rows fails too, every copy is kept (an orphan file
 * rather than lost audio) and the original exception propagates. An after-commit subscriber's throw comes AFTER the
 * commit: once the transaction's block has returned Ok, a copy whose Registrazione is stored is kept (never lose
 * the audio of a committed Registrazione).
 */
@Suppress("LongParameterList") // one parameter per collaborator: uow, id/clock, 3 repos, 2 technical ports, eventi
public class AggiungiRegistrazioneServizio(
    private val uow: UnitaDiLavoro,
    private val generatoreId: GeneratoreId,
    private val clock: Clock,
    private val progetti: ProgettoRepository,
    private val registrazioni: RegistrazioneRepository,
    private val incontri: IncontroRepository,
    private val sonda: SondaAudio,
    private val archivio: ArchivioAudio,
    private val eventi: DispatcherEventi,
) {
    public fun esegui(c: AggiungiRegistrazione): Esito<Unit> {
        val progetto = requireNotNull(progetti.trova()) { "AggiungiRegistrazione richiede un Progetto gia' creato" }
        require(c.progettoId == progetto.id) { "AggiungiRegistrazione e' di un altro Progetto" }
        require(c.file.isNotEmpty()) { "AggiungiRegistrazione richiede almeno un file" }
        val copie = mutableListOf<FileCopiato>()
        var confermata = false
        var importata = false
        // AC-60: no file without its row, whether the transaction returns Errore or throws; after an Ok block the
        // throw may come after the commit (an after-commit subscriber): the rows say which copies are committed.
        // `use` runs this like a `finally`, but a failure of the cleanup itself (the row check failing on the same DB
        // fault) is added to the original exception as suppressed instead of masking it, and no copy is discarded.
        val scartaNonConfermate = AutoCloseable {
            if (!confermata) {
                val salvate = if (importata) copie.filter { registrazioni.trova(it.id) != null } else emptyList()
                (copie - salvate.toSet()).forEach { archivio.scarta(it.riferimento) }
            }
        }
        scartaNonConfermate.use {
            // Probe and copy every file OUTSIDE the transaction (ADR 0012); the first failure stops the import.
            for (percorso in c.file) {
                val copiato = sonda.sonda(percorso).poi { info ->
                    oraDi(info).poi { ora ->
                        val id = RegistrazioneId(generatoreId.nuovo())
                        archivio.copia(percorso, id).mappa { FileCopiato(id, percorso, info, ora, it) }
                    }
                }
                when (copiato) {
                    is Esito.Ok -> copie += copiato.valore
                    is Esito.Errore -> return copiato
                }
            }
            return uow.inTransazione { importa(progetto.id, c.destinazione, copie).also { importata = it is Esito.Ok } }
                .also { confermata = it is Esito.Ok }
        }
    }

    /** The ONE transaction: the target Incontro (or the new ones), then one Registrazione per file, then the events. */
    private fun importa(progettoId: ProgettoId, destinazione: Destinazione, copie: List<FileCopiato>): Esito<Unit> {
        val incontroEsistente = (destinazione as? Destinazione.Incontro)?.let { d ->
            // INV-I1: re-read inside the transaction; another Progetto's or a ceased Incontro is unknown
            incontri.trova(d.incontroId)?.takeIf { it.progettoId == progettoId }
                ?: return Esito.Errore(ErroreApplicazioneProgetto.IncontroNonTrovato(d.incontroId))
        }
        val titoli = registrazioni.titoliDelProgetto(progettoId).toMutableList()
        var incontroComune = incontroEsistente?.id
        val eventiDaPubblicare = mutableListOf<RegistrazioneAggiunta>()
        // INV-I2 (ADR 0033 §2): ONE instant per import, +1 ms per file in the user's selection order, after every
        // Registrazione already in the Progetto, so Parti with the same data and no OraDiInizio follow the selection
        // and then the import order (a per-file clock.instant() ties at the stored ms).
        val giaNelProgetto = registrazioni.delProgetto(progettoId)
        val aggiunte = Registrazione.istantiDiAggiunta(clock.instant(), copie.size, giaNelProgetto)
        for ((indice, copia) in copie.withIndex()) {
            val incontroId = incontroComune ?: IncontroId(generatoreId.nuovo()).also { nuovo ->
                incontri.salva(Incontro.nuovo(nuovo, progettoId)) // saved before its Parte (AC-I55)
                if (destinazione is Destinazione.NuovoIncontro) incontroComune = nuovo
            }
            val titolo = TitoloRegistrazione.unico(titoloDa(copia.percorso), titoli) // AC-322: also among this import
            titoli += titolo
            val creato = Registrazione.aggiungi(
                id = copia.id,
                progettoId = progettoId,
                incontroId = incontroId,
                titolo = titolo,
                riferimentoAudio = copia.riferimento,
                durataMs = copia.info.durataMs,
                dataRegistrazione = copia.info.dataFile,
                aggiuntaAlle = aggiunte[indice],
                oraDiInizio = copia.ora,
            )
            registrazioni.salva(creato.aggregato)
            eventiDaPubblicare += creato.evento.pubblicato(incontroId)
        }
        eventiDaPubblicare.forEach(eventi::pubblica)
        return Esito.Ok(Unit)
    }
}

/** AC-I31: the OraDiInizio of the probe, to the second; empty when the probe has none. */
private fun oraDi(info: InfoAudio): Esito<OraDiInizio?> =
    info.oraDiInizio?.let { OraDiInizio.di(it.truncatedTo(ChronoUnit.SECONDS)) } ?: Esito.Ok(null)

private data class FileCopiato(
    val id: RegistrazioneId,
    val percorso: String,
    val info: InfoAudio,
    val ora: OraDiInizio?,
    val riferimento: RiferimentoAudio,
)

/**
 * The base titolo (AC-56, AC-322): the source file's name without its extension, NFC-normalized and
 * trimmed; no path separator survives in a titolo. Falls back to the full file name when stripping
 * the extension would empty it (a dotfile like `.m4a`, whose "extension" is the whole name), and
 * further to a fixed placeholder when even the file name is empty (a path ending in a separator) —
 * both keep the titolo non-blank without inventing structure the path doesn't have.
 */
private fun titoloDa(percorsoSorgente: String): String {
    val nomeFile = percorsoSorgente.substringAfterLast('/').substringAfterLast('\\')
    val senzaEstensione = nomeFile.substringBeforeLast('.', missingDelimiterValue = nomeFile)
    return Normalizer.normalize(senzaEstensione.ifBlank { nomeFile }, Normalizer.Form.NFC)
        .trim()
        .ifEmpty { "registrazione" }
}

private fun snastro.progetto.dominio.RegistrazioneAggiunta.pubblicato(incontroId: IncontroId): RegistrazioneAggiunta =
    RegistrazioneAggiunta(registrazioneId = id, progettoId = progettoId, incontroId = incontroId)
