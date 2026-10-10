package snastro.ui.registrazioni

import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.progetto.applicazione.comandi.ModificaDataRegistrazione
import snastro.progetto.applicazione.comandi.RinominaRegistrazione
import snastro.trascrizione.applicazione.comandi.AnnullaElaborazione
import snastro.trascrizione.applicazione.comandi.AvviaElaborazione
import snastro.trascrizione.dominio.ErroreTrascrizione
import snastro.ui.testi.MESSAGGIO_NUMERO_PERSONE_NON_VALIDO
import snastro.ui.testi.messaggioPer
import java.time.LocalDate
import java.time.LocalTime

/**
 * The port calls behind S2's plain row commands ([FlussoComandiRiga]), built by `:avvio` (presenter-split,
 * "Collaborators of an extracted flow"). [ritrascrivi] backs the confirmed 'Ritrascrivi' (ADR 0018, AC-448/449): the
 * same underlying command as [avviaElaborazione], supplied on its own. [modificaOraDiInizio] takes the plain time:
 * `:avvio` builds the `OraDiInizio` VO (CR-1). Every field is MANDATORY (ADR 0030 §1).
 */
data class ComandiRigaS2(
    val modificaData: (ModificaDataRegistrazione) -> Esito<Unit>,
    val rinomina: (RinominaRegistrazione) -> Esito<Unit>,
    val avviaElaborazione: (AvviaElaborazione) -> Esito<Unit>,
    val ritrascrivi: (AvviaElaborazione) -> Esito<Unit>,
    val annullaElaborazione: (AnnullaElaborazione) -> Esito<Unit>,
    val modificaOraDiInizio: (RegistrazioneId, LocalTime?) -> Esito<Unit>,
)

/**
 * The plain row commands of S2 · Registrazioni (presenter-split): the inline date, title and start time, 'Trascrivi'/
 * 'Riprova', 'Ritrascrivi' with its inline confirmation, 'Annulla' on a queued row and the row's 'Numero di persone'
 * field. Every command runs on S2's one row runner ([StatoS2.comandoDiRiga]: M3 guard, `io` hop,
 * `comandoConfermato`, logged fault, L269 reset); a success refreshes the list through [StatoS2.ricarica] (M1), a
 * refusal shows inline and changes nothing else (H1).
 */
internal class FlussoComandiRiga(
    private val s2: StatoS2,
    private val comandi: ComandiRigaS2,
) {
    /** AC-206: replaces the DataRegistrazione shown/edited inline on the row. */
    fun modificaData(id: RegistrazioneId, nuovaData: LocalDate) = s2.comandoDiRiga(
        id,
        "modifica la data di $id",
        comando = { comandi.modificaData(ModificaDataRegistrazione(id, nuovaData)) },
        suEsito = { concludiRiga(id, it) },
    )

    /** AC-363: renames the Registrazione from the row's inline titolo field — same row guard (M3) and inline error as
     * [modificaData]; the list refreshes only after a success. */
    fun rinomina(id: RegistrazioneId, nuovoTitolo: String) = s2.comandoDiRiga(
        id,
        "rinomina $id",
        comando = { comandi.rinomina(RinominaRegistrazione(id, nuovoTitolo)) },
        suEsito = { concludiRiga(id, it) },
    )

    /**
     * AC-344/AC-203: 'Trascrivi' (NON_AVVIATA) and 'Riprova' (FALLITA) both land here, carrying the row's
     * 'Numero di persone' field (AC-375): empty → `null` (automatic), an integer 1..10 → that number, anything
     * else → the inline [MESSAGGIO_NUMERO_PERSONE_NON_VALIDO] and NO command.
     */
    fun avviaElaborazione(id: RegistrazioneId) {
        val riga = s2.rigaLibera(id) ?: return
        when (val campo = numeroPersoneCampo(riga.numeroPersone)) {
            NumeroPersoneCampo.NonValido ->
                s2.aggiornaRiga(id) { it.copy(erroreRiga = MESSAGGIO_NUMERO_PERSONE_NON_VALIDO) }
            is NumeroPersoneCampo.Valido -> s2.comandoDiRiga(
                id,
                "avvia l'elaborazione di $id",
                comando = { comandi.avviaElaborazione(AvviaElaborazione(id, campo.numero)) },
                suEsito = { concludiRiga(id, it) },
            )
        }
    }

    /**
     * AC-449: 'Ritrascrivi' validates the field exactly like [avviaElaborazione] (invalid → the same
     * inline message, no dialog); a valid field opens the inline confirmation instead of sending the
     * command right away.
     */
    fun ritrascrivi(id: RegistrazioneId) {
        val riga = s2.rigaLibera(id) ?: return
        when (numeroPersoneCampo(riga.numeroPersone)) {
            NumeroPersoneCampo.NonValido ->
                s2.aggiornaRiga(id) { it.copy(erroreRiga = MESSAGGIO_NUMERO_PERSONE_NON_VALIDO) }
            is NumeroPersoneCampo.Valido ->
                s2.aggiornaRiga(id) { it.copy(confermaRitrascrivi = true, erroreRiga = null) }
        }
    }

    /** AC-449: 'Annulla' on the confirmation — no command is sent, the field keeps its value. */
    fun annullaRitrascrivi(id: RegistrazioneId) = s2.aggiornaRiga(id) { it.copy(confermaRitrascrivi = false) }

    /** AC-449: the confirmed 'Ritrascrivi' — exactly ONE `AvviaElaborazione` with the value [ritrascrivi] validated. */
    fun confermaRitrascrivi(id: RegistrazioneId) {
        val riga = s2.rigaLibera(id)?.takeIf { it.confermaRitrascrivi } ?: return
        val numero = (numeroPersoneCampo(riga.numeroPersone) as? NumeroPersoneCampo.Valido)?.numero
        s2.comandoDiRiga(
            id,
            "ritrascrivi $id",
            comando = { comandi.ritrascrivi(AvviaElaborazione(id, numero)) },
            suEsito = { concludiRiga(id, it) },
        )
    }

    /**
     * ADR 0018 Amendment (b) §3: 'Annulla' on an `InAttesa` row (AC-475), no dialog — nothing is lost.
     * AC-476: BOTH outcomes reload the list, unlike every other row action here (H1 "nulla cambia"
     * does not hold for this one): `ElaborazioneGiaAvviata` means the dispatcher's claim raced ahead
     * and the row's real state already changed elsewhere (ADR 0018 Amendment (b) §3's race, now shown
     * inline too — "La trascrizione è già partita…"), and `ElaborazioneNonTrovata` means it is already
     * gone — the reload alone shows the row's accurate current state, with NO inline message (AC-476:
     * "it just reloads" — a message would refer to an Elaborazione the row no longer shows at all).
     */
    fun annullaElaborazione(id: RegistrazioneId) {
        val elaborazioneId = s2.rigaLibera(id)?.elaborazioneId ?: return
        s2.comandoDiRiga(
            id,
            "annulla $elaborazioneId",
            comando = { comandi.annullaElaborazione(AnnullaElaborazione(elaborazioneId)) },
            suEsito = { esito ->
                s2.ricarica() // AC-476: reload on both Ok and Errore (see the kdoc above)
                val messaggio = (esito as? Esito.Errore)?.errore
                    ?.let { it as? ErroreTrascrizione.ElaborazioneGiaAvviata }
                    ?.let(::messaggioPer)
                s2.aggiornaRiga(id) { it.copy(erroreRiga = messaggio) }
            },
        )
    }

    /**
     * AC-I68: replaces a Parte's start time ([ora] `null` clears it). The list is read again on success, so the
     * sub-rows come back in their new order; a refusal stays on the Parte's row ([RigaRegistrazione.erroreRiga]).
     */
    fun modificaOraDiInizio(id: RegistrazioneId, ora: LocalTime?) = s2.comandoDiRiga(
        id,
        "modifica l'ora di inizio di $id",
        comando = { comandi.modificaOraDiInizio(id, ora) },
        suEsito = { concludiRiga(id, it) },
    )

    /** ADR 0014: the text of [id]'s 'Numero di persone' field, as typed (validated only when an action fires). */
    fun modificaNumeroPersone(id: RegistrazioneId, testo: String) =
        s2.aggiornaRiga(id) { it.copy(numeroPersone = testo) }

    /**
     * The outcome of a plain row command: Ok reloads (M1: the merge keeps this row's flags until the runner resets
     * them) and closes the Ritrascrivi confirmation; a refusal shows its message inline (H1). Committed with only its
     * after-commit follow-up failed is an Ok (`comandoConfermato`, D-0062, L270).
     */
    private suspend fun concludiRiga(id: RegistrazioneId, esito: Esito<Unit>) = when (esito) {
        is Esito.Ok -> {
            s2.ricarica()
            s2.aggiornaRiga(id) { it.copy(erroreRiga = null, confermaRitrascrivi = false) }
        }
        is Esito.Errore -> s2.aggiornaRiga(id) { it.copy(erroreRiga = messaggioPer(esito.errore)) }
    }
}

/** ADR 0014/AC-375: the field's text → `null` (empty, automatic), 1..10, or [NonValido]. */
internal sealed interface NumeroPersoneCampo {
    data class Valido(val numero: Int?) : NumeroPersoneCampo
    data object NonValido : NumeroPersoneCampo
}

// L548b: a strict `^(10|[1-9])$` match — never `String.toIntOrNull()` on the trimmed text, which
// also accepts a leading sign ("+4".toIntOrNull() == 4) and leading zeros ("04".toIntOrNull() == 4),
// neither a sane representation of a person count.
private val REGEX_NUMERO_PERSONE = Regex("^($NUMERO_PERSONE_MAX|[1-9])$")

/** The 'Numero di persone' field of a row ([FlussoComandiRiga]) or of an Incontro ([FlussoIncontro]), validated. */
internal fun numeroPersoneCampo(testo: String): NumeroPersoneCampo {
    val t = testo.trim()
    val numero = t.toIntOrNull()?.takeIf {
        REGEX_NUMERO_PERSONE.matches(t) && it in NUMERO_PERSONE_MIN..NUMERO_PERSONE_MAX
    }
    return when {
        t.isEmpty() -> NumeroPersoneCampo.Valido(null)
        numero != null -> NumeroPersoneCampo.Valido(numero)
        else -> NumeroPersoneCampo.NonValido
    }
}

/**
 * ADR 0014: the range the field accepts before sending the command (AC-375, 'no command' on anything else).
 * `:ui` cannot see the `NumeroPersone` VO (CR-1); `AvviaElaborazione` re-validates it (`NumeroPersone.di`).
 * L548a: `internal` (not `private`) so this boundary is checkable from elsewhere in `:ui`'s own tests; the
 * cross-module parity with the domain's actual range lives in `:avvio`'s own
 * `NumeroPersoneRegistrazioniParitaTest` (`:ui` cannot import `NumeroPersone` itself to share it directly).
 */
internal const val NUMERO_PERSONE_MIN = 1
internal const val NUMERO_PERSONE_MAX = 10
