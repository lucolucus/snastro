package snastro.progetto.dominio

import snastro.kernel.Creato
import snastro.kernel.Esito
import snastro.kernel.IncontroId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RicostituzioneDaPersistenza
import snastro.kernel.RiferimentoAudio
import java.time.Instant
import java.time.LocalDate

/**
 * Aggregate root: an audio recording of one [Progetto].
 * INV-1: [progettoId] is fixed at creation. INV-I1: [incontroId], the `Incontro` this Registrazione is a Parte of, is
 * set at creation and never changes (ADR 0033 §1). INV-2: [dataRegistrazione] is always set, changed only by
 * [modificaData].
 * [titolo] starts as the source file name without extension (R6) and is never blank; only [rinomina]
 * changes it (AC-360) — the audio file stored in the project is never renamed with it (AC-362).
 */
public class Registrazione
@Suppress("LongParameterList") // one parameter per field of the root
private constructor(
    public val id: RegistrazioneId,
    public val progettoId: ProgettoId,
    public val incontroId: IncontroId,
    titolo: String,
    public val riferimentoAudio: RiferimentoAudio,
    public val durataMs: Long,
    dataRegistrazione: LocalDate,
    public val aggiuntaAlle: Instant,
    oraDiInizio: OraDiInizio?,
) {
    private var _dataRegistrazione: LocalDate = dataRegistrazione
    public val dataRegistrazione: LocalDate get() = _dataRegistrazione

    private var _titolo: String = titolo
    public val titolo: String get() = _titolo

    private var _oraDiInizio: OraDiInizio? = oraDiInizio

    /** INV-I14: the time of day this Parte starts, `null` when unknown; only [modificaOraDiInizio] changes it. */
    public val oraDiInizio: OraDiInizio? get() = _oraDiInizio

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

    /**
     * AC-I14: replaces the OraDiInizio with [ora] chosen by the user; `null` clears it (INV-I14, empty is legal).
     * The same value, also empty on empty, → `Esito.Ok(null)`, a no-op with no event.
     */
    public fun modificaOraDiInizio(ora: OraDiInizio?): Esito<OraDiInizioModificataDominio?> {
        val precedente = _oraDiInizio
        if (ora == precedente) return Esito.Ok(null)
        _oraDiInizio = ora
        return Esito.Ok(OraDiInizioModificataDominio(id, incontroId, precedente, ora))
    }

    /**
     * ADR 0020: a pure check returning the event with the CURRENT titolo and date — no state change, no guard
     * (INV-28's "no open Elaborazione" is Trascrizione's, checked by its synchronous subscriber). The physical
     * deletion is `RegistrazioneRepository.rimuovi`.
     */
    public fun elimina(): RegistrazioneEliminata =
        RegistrazioneEliminata(id, progettoId, _titolo, _dataRegistrazione, riferimentoAudio)

    public companion object {
        /** [dataRegistrazione] defaults, at the caller, to the source file's date (INV-2). */
        @Suppress("LongParameterList") // the pinned signature of agg-registrazione
        public fun aggiungi(
            id: RegistrazioneId,
            progettoId: ProgettoId,
            incontroId: IncontroId,
            titolo: String,
            riferimentoAudio: RiferimentoAudio,
            durataMs: Long,
            dataRegistrazione: LocalDate,
            aggiuntaAlle: Instant,
            oraDiInizio: OraDiInizio? = null,
        ): Creato<Registrazione, RegistrazioneAggiunta> =
            Creato(
                Registrazione(
                    id,
                    progettoId,
                    incontroId,
                    titolo,
                    riferimentoAudio,
                    durataMs,
                    dataRegistrazione,
                    aggiuntaAlle,
                    oraDiInizio,
                ),
                RegistrazioneAggiunta(id, progettoId),
            )

        /** Rebuilds a persisted Registrazione; the database is trusted, nothing is re-validated (CR-15). */
        @RicostituzioneDaPersistenza
        @Suppress("LongParameterList") // one parameter per persisted field
        public fun ricostituisci(
            id: RegistrazioneId,
            progettoId: ProgettoId,
            incontroId: IncontroId,
            titolo: String,
            riferimentoAudio: RiferimentoAudio,
            durataMs: Long,
            dataRegistrazione: LocalDate,
            aggiuntaAlle: Instant,
            oraDiInizio: OraDiInizio? = null,
        ): Registrazione =
            Registrazione(
                id,
                progettoId,
                incontroId,
                titolo,
                riferimentoAudio,
                durataMs,
                dataRegistrazione,
                aggiuntaAlle,
                oraDiInizio,
            )
    }
}
