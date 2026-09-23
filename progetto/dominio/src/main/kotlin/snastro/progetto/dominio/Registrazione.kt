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
 * [titolo] (source file name without extension) is immutable (R6).
 */
public class Registrazione
@Suppress("LongParameterList") // one parameter per field of the root
private constructor(
    public val id: RegistrazioneId,
    public val progettoId: ProgettoId,
    public val titolo: String,
    public val riferimentoAudio: RiferimentoAudio,
    public val durataMs: Long,
    dataRegistrazione: LocalDate,
    public val aggiuntaAlle: Instant,
) {
    private var _dataRegistrazione: LocalDate = dataRegistrazione
    public val dataRegistrazione: LocalDate get() = _dataRegistrazione

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
        @Suppress("LongParameterList") // one parameter per persisted column
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
