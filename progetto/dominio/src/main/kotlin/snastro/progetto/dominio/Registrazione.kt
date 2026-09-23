package snastro.progetto.dominio

import snastro.kernel.Creato
import snastro.kernel.Esito
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RicostituzioneDaPersistenza
import snastro.kernel.RiferimentoAudio
import java.time.Instant
import java.time.LocalDate

/**
 * Aggregate root: an audio recording of one [Progetto].
 * INV-1: [progettoId] is fixed at creation. INV-2: [dataRegistrazione] is always set, changed only by [modificaData].
 * [titolo] starts as the source file name without extension (R6) and is never blank; only [rinomina]
 * changes it (AC-360) — the audio file stored in the project is never renamed with it (AC-362).
 */
public class Registrazione
@Suppress("LongParameterList") // one parameter per field of the root
private constructor(
    public val id: RegistrazioneId,
    public val progettoId: ProgettoId,
    titolo: String,
    public val riferimentoAudio: RiferimentoAudio,
    public val durataMs: Long,
    dataRegistrazione: LocalDate,
    public val aggiuntaAlle: Instant,
) {
    private var _dataRegistrazione: LocalDate = dataRegistrazione
    public val dataRegistrazione: LocalDate get() = _dataRegistrazione

    private var _titolo: String = titolo
    public val titolo: String get() = _titolo

    /**
     * AC-360: renames the Registrazione to [nuovoTitolo], trimmed. Blank → [ErroreProgetto.TitoloVuoto];
     * the same titolo → `Esito.Ok(null)`, a no-op with no event. The uniqueness of the titolo in the
     * Progetto is a set rule, pre-checked by the service (AC-361), not here.
     */
    public fun rinomina(nuovoTitolo: String): Esito<RegistrazioneRinominata?> {
        val nuovo = nuovoTitolo.trim()
        return when {
            nuovo.isEmpty() -> Esito.Errore(ErroreProgetto.TitoloVuoto)
            nuovo == _titolo -> Esito.Ok(null)
            else -> {
                val precedente = _titolo
                _titolo = nuovo
                Esito.Ok(RegistrazioneRinominata(id, precedente, nuovo))
            }
        }
    }

    /** Replaces the DataRegistrazione with a date chosen by the user (INV-2). */
    public fun modificaData(nuova: LocalDate): Esito<DataRegistrazioneModificata> {
        val precedente = _dataRegistrazione
        _dataRegistrazione = nuova
        return Esito.Ok(DataRegistrazioneModificata(id, precedente, nuova))
    }

    public companion object {
        /** [dataRegistrazione] defaults, at the caller, to the source file's date (INV-2). */
        @Suppress("LongParameterList") // the pinned signature of agg-registrazione
        public fun aggiungi(
            id: RegistrazioneId,
            progettoId: ProgettoId,
            titolo: String,
            riferimentoAudio: RiferimentoAudio,
            durataMs: Long,
            dataRegistrazione: LocalDate,
            aggiuntaAlle: Instant,
        ): Creato<Registrazione, RegistrazioneAggiunta> =
            Creato(
                Registrazione(id, progettoId, titolo, riferimentoAudio, durataMs, dataRegistrazione, aggiuntaAlle),
                RegistrazioneAggiunta(id, progettoId),
            )

        /** Rebuilds a persisted Registrazione; the database is trusted, nothing is re-validated (CR-15). */
        @RicostituzioneDaPersistenza
        public fun ricostituisci(
            id: RegistrazioneId,
            progettoId: ProgettoId,
            titolo: String,
            riferimentoAudio: RiferimentoAudio,
            durataMs: Long,
            dataRegistrazione: LocalDate,
            aggiuntaAlle: Instant,
        ): Registrazione =
            Registrazione(id, progettoId, titolo, riferimentoAudio, durataMs, dataRegistrazione, aggiuntaAlle)
    }
}
