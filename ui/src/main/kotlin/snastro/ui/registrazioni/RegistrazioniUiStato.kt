package snastro.ui.registrazioni

import snastro.kernel.ElaborazioneId
import snastro.kernel.RegistrazioneId
import java.time.LocalDate

/**
 * State of S2 · Registrazioni del Progetto (AC-199..206, AC-342..344) — presenter-owned, rendered by
 * [SchermataRegistrazioni].
 */
sealed interface RegistrazioniUiStato {
    /** The read-model(s) are still loading, on first mount (AC-200). */
    data object Caricamento : RegistrazioniUiStato

    /**
     * The known Registrazioni (AC-202: newest [RigaRegistrazione.dataRegistrazione] first — the read
     * model's own order, never re-sorted here) — an empty [righe] is rendered as the AC-199 empty
     * message, not a separate state. [importoInCorso] guards a second import while one is in flight
     * (M3) and drives the indicator (AC-200); [errore], when set, is a dismissible inline message for
     * the last failed import (AC-201) or list refresh (H1: never replaces [righe]).
     */
    data class Dati(
        val righe: List<RigaRegistrazione>,
        val importoInCorso: Boolean = false,
        val errore: String? = null,
    ) : RegistrazioniUiStato

    /**
     * M5: the INITIAL load failed — distinct from [Dati] with an empty [Dati.righe] (AC-199, a real
     * empty catalog): showing the AC-199 empty message here would falsely claim there are no
     * registrazioni. [messaggio] is paired with a retry action (`AzioniRegistrazioni.riprova`). A
     * refresh failing AFTER rows are already known stays in [Dati] (H1/M1): the known rows and every
     * in-flight flag survive, only [Dati.errore] changes.
     */
    data class Errore(val messaggio: String) : RegistrazioniUiStato
}

/**
 * One row (AC-199..206, AC-342..344, AC-448..451/475/476). [elaborazione] is `null` when the
 * Trascrizione sources are not supplied to the presenter (R0 variant, AC-342): no status column, no
 * 'Trascrivi'/'Riprova', a row click does nothing. [operazioneInCorso] guards a second
 * `modificaData`/`avviaElaborazione`/`ritrascrivi`/`annullaElaborazione` on this row while one is in
 * flight (M3); [erroreRiga], when set, is a dismissible inline message for the last failed one (H1,
 * AC-206/AC-344 — "nulla cambia" beyond this). [numeroPersone] is the text of the optional 'Numero di
 * persone' field shown next to 'Trascrivi'/'Riprova'/'Ritrascrivi' (ADR 0014): presenter state only,
 * prefilled on a failed row from its Elaborazione (AC-376) or, with the `ritrascrivi` source supplied,
 * on a Completata row (AC-448), validated when the action fires (AC-375/AC-449).
 *
 * ADR 0018: [trascrittoDisponibile] backs the "a row opens S3 iff a Trascritto exists" rule (replacing
 * "iff COMPLETATA"); [elaborazioneId] is the id `annullaElaborazione` cancels (`null` only for
 * `NonAvviata`, AC-474); [ritrascriviDisponibile] is `true` only on a `Completata` row when the
 * `ritrascrivi` source is supplied (AC-448); [confermaRitrascrivi] shows the inline confirmation in
 * place of the field/button (AC-449, same style as S4's delete confirmation — never an AWT dialog);
 * [ritrascrizioneFallita] is the failed re-run's `motivoFallimento` on a `Completata` row (AC-451,
 * independent of whether `ritrascrivi` is supplied); [annullabile] is `true` only on an `InAttesa` row
 * when the `annullaElaborazione` source is supplied (AC-475).
 */
@Suppress("LongParameterList") // one field per AC-199..206/342..344/448..451/475/476 datum of the row
data class RigaRegistrazione(
    val registrazioneId: RegistrazioneId,
    val titolo: String,
    val dataRegistrazione: LocalDate,
    val durataMs: Long,
    val elaborazione: StatoElaborazioneRiga? = null,
    val riproduzione: StatoRiproduzioneRiga = StatoRiproduzioneRiga.Disponibile,
    val operazioneInCorso: Boolean = false,
    val erroreRiga: String? = null,
    val numeroPersone: String = "",
    val identificazione: IdentificazioneRiga? = null,
    val trascrittoDisponibile: Boolean = false,
    val elaborazioneId: ElaborazioneId? = null,
    val ritrascriviDisponibile: Boolean = false,
    val confermaRitrascrivi: Boolean = false,
    val ritrascrizioneFallita: String? = null,
    val annullabile: Boolean = false,
)

/**
 * AC-204/AC-345 (R2, Parlanti fetta): the identification badge, joining `numVoci`
 * ([StatoElaborazioneRiga] is only ever built from a [snastro.trascrizione.applicazione.letture.StatoRegistrazioneVista]
 * whose `numVoci` is non-null for `COMPLETATA`) with `numVociDaIdentificare` from
 * `identificazione-registrazioni`. `null` — never a provisional or '0' count — when the identification
 * source is not supplied to the presenter (R0/R1), has no entry yet for this row (no Trascritto yet),
 * or failed to load; `numVociDaIdentificare == 0` still renders (just without the "da identificare"
 * clause, AC-345).
 */
data class IdentificazioneRiga(val numVoci: Int, val numVociDaIdentificare: Int)

/** AC-343: this row's playback over the shared [snastro.ui.lettore.LettoreAudio]. */
sealed interface StatoRiproduzioneRiga {
    /** '▶' enabled; nothing is playing for this row right now. */
    data object Disponibile : StatoRiproduzioneRiga

    /** `LettoreAudio.disponibile(id)` is false: '▶' disabled, with a message. */
    data object NonDisponibile : StatoRiproduzioneRiga

    /** `LettoreAudio.stato` reports this row's Registrazione playing: the control shows pause. */
    data object InRiproduzione : StatoRiproduzioneRiga
}

/** AC-203/AC-344 (R1, Trascrizione sources supplied): the row's processing state, joined from `stati-elaborazione`. */
sealed interface StatoElaborazioneRiga {
    /** AC-344: no Elaborazione yet for this Registrazione — shows the 'Numero di persone' field + 'Trascrivi'. */
    data object NonAvviata : StatoElaborazioneRiga

    /** AC-203/AC-450: "In coda ([posizione])", or "Ritrascrizione in coda ([posizione])" when
     * [ritrascrizione] (a Trascritto already exists — a re-run, ADR 0018). */
    data class InAttesa(val posizione: Int, val ritrascrizione: Boolean = false) : StatoElaborazioneRiga

    /** AC-203/AC-450: "In corso · [faseEtichetta] · <mm:ss>" — [trascorsoMs] is measured from
     * `avviataAlle`; "Ritrascrizione in corso · …" when [ritrascrizione]. */
    data class InCorso(
        val faseEtichetta: String,
        val trascorsoMs: Long,
        val ritrascrizione: Boolean = false,
    ) : StatoElaborazioneRiga

    /** AC-203: [motivo] + the 'Numero di persone' field (prefilled, AC-376) + 'Riprova' — only for a
     * FALLITA row WITHOUT a Trascritto (AC-451: one WITH a Trascritto renders as [Completata] instead). */
    data class Fallita(val motivo: String) : StatoElaborazioneRiga

    /** AC-203/AC-448/AC-451: a row click opens S3 (the presenter's injected `apriRegistrazione`, out
     * of this block's scope) — [RigaRegistrazione.ritrascrizioneFallita]/[RigaRegistrazione.ritrascriviDisponibile]
     * carry the ADR 0018 additions (kept on the row, not here, since they are independent of this
     * marker state). */
    data object Completata : StatoElaborazioneRiga
}
