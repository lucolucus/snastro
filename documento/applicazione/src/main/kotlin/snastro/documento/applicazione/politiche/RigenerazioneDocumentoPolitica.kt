package snastro.documento.applicazione.politiche

import snastro.documento.applicazione.letture.Documento
import snastro.documento.applicazione.porte.ErroreApplicazioneDocumento
import snastro.documento.applicazione.porte.LettoreNomi
import snastro.documento.applicazione.porte.LettoreTrascritto
import snastro.documento.applicazione.porte.ScrittoreDocumento
import snastro.kernel.Esito
import snastro.kernel.ParlanteId
import snastro.kernel.RegistrazioneId
import snastro.kernel.poi
import java.io.IOException
import java.time.LocalDate
import java.util.Locale

/**
 * Policy `Rigenerazione` (`:documento:applicazione..politiche`, ADR 0012): projects and writes the
 * Documento of one or many Registrazioni through [LettoreTrascritto] / [LettoreNomi] /
 * [ScrittoreDocumento] only.
 *
 * [esegui] and the `perXxx` reactions below never import the published events that trigger them
 * (`RegistrazioneRinominata`, `ParlanteRinominato`, ...): `:documento:applicazione` may not depend
 * on `:progetto:applicazione` / `:parlanti:applicazione` (`architecture.md` edges).
 * `abbonato-documento` (`:documento:adattatori`, wave 6) subscribes to them and translates each
 * into one of the calls below, mirroring the event 1:1 (cf. `ApplicaRevisionePolitica`).
 */
public class RigenerazioneDocumentoPolitica(
    private val trascritti: LettoreTrascritto,
    private val nomi: LettoreNomi,
    private val scrittore: ScrittoreDocumento,
) {
    /**
     * AC-153/154/155/327/157: no Trascritto → [Esito.Ok] without writing anything — the Registrazione
     * has nothing to render yet. Otherwise writes the current projection, then — when
     * [RigeneraDocumento.nomeFilePrecedente] names a DIFFERENT file (compared case-INsensitively: two
     * names differing only by case are the SAME file on a case-insensitive filesystem, e.g. the
     * default on macOS/APFS — removing it would destroy what was just written) — removes it. A
     * write/removal I/O fault becomes [ErroreApplicazioneDocumento.ScritturaFallita] so the
     * after-commit subscriber retries (ADR 0012); nothing is ever removed before the new file is
     * safely in place.
     */
    public fun esegui(c: RigeneraDocumento): Esito<Unit> {
        val trascritto = trascritti.trascritto(c.registrazioneId) ?: return Esito.Ok(Unit)
        val vista = Documento.proietta(trascritto, nomi.nomi(c.registrazioneId))
        return scriviERimuoviSePrecedente(vista.nomeFile, vista.markdown, c.nomeFilePrecedente)
    }

    /** AC-158: every Registrazione with a Trascritto, regenerated unconditionally (ADR 0012 R4). */
    public fun esegui(ignored: RigeneraTuttiIDocumenti): Esito<Unit> =
        rigeneraOgnuna(trascritti.registrazioniConTrascritto())

    /**
     * `DataRegistrazioneModificata` (AC-155, AC-327): the OLD file is computed from [precedente] and
     * the Registrazione's CURRENT titolo (unchanged by a date edit), with the same pure
     * `Documento.nomeFile` used for the new one. No Trascritto (yet) → nothing to do.
     */
    public fun perDataRegistrazioneModificata(registrazioneId: RegistrazioneId, precedente: LocalDate): Esito<Unit> {
        val trascritto = trascritti.trascritto(registrazioneId) ?: return Esito.Ok(Unit)
        return esegui(RigeneraDocumento(registrazioneId, Documento.nomeFile(precedente, trascritto.titolo)))
    }

    /**
     * `RegistrazioneRinominata` (AC-155bis, since fix-batch-11): a rename behaves like a date change
     * — the OLD file is computed from [precedente] and the Registrazione's CURRENT
     * dataRegistrazione (unchanged by a rename). No Trascritto (yet) → nothing to do.
     */
    public fun perRegistrazioneRinominata(registrazioneId: RegistrazioneId, precedente: String): Esito<Unit> {
        val trascritto = trascritti.trascritto(registrazioneId) ?: return Esito.Ok(Unit)
        return esegui(RigeneraDocumento(registrazioneId, Documento.nomeFile(trascritto.dataRegistrazione, precedente)))
    }

    /** `ParlanteRinominato` (AC-156): every Registrazione with an Attribuzione to [parlanteId], and no other. */
    public fun perParlanteRinominato(parlanteId: ParlanteId): Esito<Unit> =
        rigeneraOgnuna(nomi.registrazioniCon(parlanteId))

    /** `ParlantePromosso` (AC-156): only the rendered Nome could change, and only if it actually did. */
    public fun perParlantePromosso(parlanteId: ParlanteId, nomeCambiato: Boolean): Esito<Unit> =
        if (nomeCambiato) rigeneraOgnuna(nomi.registrazioniCon(parlanteId)) else Esito.Ok(Unit)

    /**
     * `ParlanteEliminato` (AC-156): mirrors the event 1:1 for `abbonato-documento` so it can call one
     * policy method per event uniformly — an eliminato Parlante's Nome is still resolved by
     * [LettoreNomi] (INV-24), so the Documento never changes.
     */
    @Suppress("UnusedParameter") // mirrors the ParlanteEliminato event 1:1 for abbonato-documento (AC-156)
    public fun perParlanteEliminato(parlanteId: ParlanteId): Esito<Unit> = Esito.Ok(Unit)

    /**
     * `RegistrazioneEliminata` (AC-623, ADR 0020 §3-§4), REMOVE-only: removes `Documento.nomeFile(data, titolo)` —
     * the name at deletion — then each of [nomiPrecedenti] (names still pending from an unflushed rename or date
     * change) that is a DIFFERENT file (case-insensitively, as in [esegui]). It never writes and never reads the
     * Trascritto (it is gone). An absent file is Ok (idempotent); an I/O fault → `ScritturaFallita`, so the caller
     * retries. Called by `abbonato-documento` on the per-key queue and, at project open, by the derived-files cleanup.
     */
    @Suppress("UnusedParameter") // mirrors the RegistrazioneEliminata event 1:1 for abbonato-documento (AC-623)
    public fun perRegistrazioneEliminata(
        registrazioneId: RegistrazioneId,
        dataRegistrazione: LocalDate,
        titolo: String,
        nomiPrecedenti: Set<String>,
    ): Esito<Unit> {
        val nomeFile = Documento.nomeFile(dataRegistrazione, titolo)
        val daRimuovere = (listOf(nomeFile) + nomiPrecedenti).distinctBy { it.lowercase(Locale.ROOT) }
        for (nome in daRimuovere) {
            try {
                scrittore.rimuovi(nome)
            } catch (e: IOException) {
                val messaggio = e.message ?: "errore di I/O in documenti/"
                return Esito.Errore(ErroreApplicazioneDocumento.ScritturaFallita(nome, messaggio))
            }
        }
        return Esito.Ok(Unit)
    }

    private fun rigeneraOgnuna(registrazioni: List<RegistrazioneId>): Esito<Unit> =
        registrazioni.fold<RegistrazioneId, Esito<Unit>>(Esito.Ok(Unit)) { esito, id ->
            esito.poi { esegui(RigeneraDocumento(id)) }
        }

    private fun scriviERimuoviSePrecedente(
        nomeFile: String,
        markdown: String,
        nomeFilePrecedente: String?,
    ): Esito<Unit> = try {
        scrittore.scrivi(nomeFile, markdown)
        if (nomeFilePrecedente != null && !nomeFilePrecedente.equals(nomeFile, ignoreCase = true)) {
            scrittore.rimuovi(nomeFilePrecedente)
        }
        Esito.Ok(Unit)
    } catch (e: IOException) {
        val messaggio = e.message ?: "errore di I/O in documenti/"
        Esito.Errore(ErroreApplicazioneDocumento.ScritturaFallita(nomeFile, messaggio))
    }
}
