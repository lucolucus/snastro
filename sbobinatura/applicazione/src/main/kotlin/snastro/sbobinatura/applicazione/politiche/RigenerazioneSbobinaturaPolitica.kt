package snastro.sbobinatura.applicazione.politiche

import snastro.kernel.Esito
import snastro.kernel.ParlanteId
import snastro.kernel.RegistrazioneId
import snastro.kernel.poi
import snastro.sbobinatura.applicazione.letture.Sbobinatura
import snastro.sbobinatura.applicazione.porte.ErroreApplicazioneSbobinatura
import snastro.sbobinatura.applicazione.porte.LettoreNomi
import snastro.sbobinatura.applicazione.porte.LettoreTrascritto
import snastro.sbobinatura.applicazione.porte.ScrittoreSbobinatura
import java.io.IOException
import java.time.LocalDate
import java.util.Locale

/**
 * Policy `Rigenerazione` (`:sbobinatura:applicazione..politiche`, ADR 0012): projects and writes the
 * Sbobinatura of one or many Registrazioni through [LettoreTrascritto] / [LettoreNomi] /
 * [ScrittoreSbobinatura] only.
 *
 * [esegui] and the `perXxx` reactions below never import the published events that trigger them
 * (`RegistrazioneRinominata`, `ParlanteRinominato`, ...): `:sbobinatura:applicazione` may not depend
 * on `:progetto:applicazione` / `:parlanti:applicazione` (`architecture.md` edges).
 * `abbonato-sbobinatura` (`:sbobinatura:adattatori`, wave 6) subscribes to them and translates each
 * into one of the calls below, mirroring the event 1:1 (cf. `ApplicaRevisionePolitica`).
 */
public class RigenerazioneSbobinaturaPolitica(
    private val trascritti: LettoreTrascritto,
    private val nomi: LettoreNomi,
    private val scrittore: ScrittoreSbobinatura,
) {
    /**
     * AC-153/154/155/327/157: no Trascritto → [Esito.Ok] without writing anything — the Registrazione
     * has nothing to render yet. Otherwise writes the current projection, then — when
     * [RigeneraSbobinatura.nomeFilePrecedente] names a DIFFERENT file (compared case-INsensitively: two
     * names differing only by case are the SAME file on a case-insensitive filesystem, e.g. the
     * default on macOS/APFS — removing it would destroy what was just written) — removes it. A
     * write/removal I/O fault becomes [ErroreApplicazioneSbobinatura.ScritturaFallita] so the
     * after-commit subscriber retries (ADR 0012); nothing is ever removed before the new file is
     * safely in place.
     */
    public fun esegui(c: RigeneraSbobinatura): Esito<Unit> {
        val trascritto = trascritti.trascritto(c.registrazioneId) ?: return Esito.Ok(Unit)
        val vista = Sbobinatura.proietta(trascritto, nomi.nomi(trascritto.incontroId))
        return scriviERimuoviSePrecedente(vista.nomeFile, vista.markdown, c.nomeFilePrecedente)
    }

    /**
     * `DataRegistrazioneModificata` (AC-155, AC-327): the OLD file is computed from [precedente] and
     * the Registrazione's CURRENT titolo (unchanged by a date edit), with the same pure
     * `Sbobinatura.nomeFile` used for the new one. No Trascritto (yet) → nothing to do.
     */
    public fun perDataRegistrazioneModificata(registrazioneId: RegistrazioneId, precedente: LocalDate): Esito<Unit> {
        val trascritto = trascritti.trascritto(registrazioneId) ?: return Esito.Ok(Unit)
        return esegui(RigeneraSbobinatura(registrazioneId, Sbobinatura.nomeFile(precedente, trascritto.titolo)))
    }

    /**
     * `RegistrazioneRinominata` (AC-155bis, since fix-batch-11): a rename behaves like a date change
     * — the OLD file is computed from [precedente] and the Registrazione's CURRENT
     * dataRegistrazione (unchanged by a rename). No Trascritto (yet) → nothing to do.
     */
    public fun perRegistrazioneRinominata(registrazioneId: RegistrazioneId, precedente: String): Esito<Unit> {
        val trascritto = trascritti.trascritto(registrazioneId) ?: return Esito.Ok(Unit)
        return esegui(
            RigeneraSbobinatura(registrazioneId, Sbobinatura.nomeFile(trascritto.dataRegistrazione, precedente)),
        )
    }

    /**
     * `ParlanteRinominato` (AC-156): every transcribed Parte of each Incontro with an Attribuzione to [parlanteId],
     * and no other (ADR 0035 §7).
     */
    public fun perParlanteRinominato(parlanteId: ParlanteId): Esito<Unit> = rigeneraOgnuna(partiCon(parlanteId))

    /** `ParlantePromosso` (AC-156): only the rendered Nome could change, and only if it actually did. */
    public fun perParlantePromosso(parlanteId: ParlanteId, nomeCambiato: Boolean): Esito<Unit> =
        if (nomeCambiato) rigeneraOgnuna(partiCon(parlanteId)) else Esito.Ok(Unit)

    /**
     * `ParlanteEliminato` (AC-156): mirrors the event 1:1 for `abbonato-sbobinatura` so it can call one
     * policy method per event uniformly — an eliminato Parlante's Nome is still resolved by
     * [LettoreNomi] (INV-24), so the Sbobinatura never changes.
     */
    @Suppress("UnusedParameter") // mirrors the ParlanteEliminato event 1:1 for abbonato-sbobinatura (AC-156)
    public fun perParlanteEliminato(parlanteId: ParlanteId): Esito<Unit> = Esito.Ok(Unit)

    /**
     * `RegistrazioneEliminata` (AC-623, ADR 0020 §3-§4), REMOVE-only: removes `Sbobinatura.nomeFile(data, titolo)` —
     * the name at deletion — then each of [nomiPrecedenti] (names still pending from an unflushed rename or date
     * change) that is a DIFFERENT file (case-insensitively, as in [esegui]). It never writes and never reads the
     * Trascritto (it is gone). An absent file is Ok (idempotent); an I/O fault → `ScritturaFallita`, so the caller
     * retries. Called by `abbonato-sbobinatura` on the per-key queue and, at project open, by the derived-files
     * cleanup.
     */
    @Suppress("UnusedParameter") // mirrors the RegistrazioneEliminata event 1:1 for abbonato-sbobinatura (AC-623)
    public fun perRegistrazioneEliminata(
        registrazioneId: RegistrazioneId,
        dataRegistrazione: LocalDate,
        titolo: String,
        nomiPrecedenti: Set<String>,
    ): Esito<Unit> {
        val nomeFile = Sbobinatura.nomeFile(dataRegistrazione, titolo)
        val daRimuovere = (listOf(nomeFile) + nomiPrecedenti).distinctBy { it.lowercase(Locale.ROOT) }
        for (nome in daRimuovere) {
            try {
                scrittore.rimuovi(nome)
            } catch (e: IOException) {
                val messaggio = e.message ?: "errore di I/O in sbobinature/"
                return Esito.Errore(ErroreApplicazioneSbobinatura.ScritturaFallita(nome, messaggio))
            }
        }
        return Esito.Ok(Unit)
    }

    private fun partiCon(parlanteId: ParlanteId): List<RegistrazioneId> =
        nomi.incontriCon(parlanteId).flatMap { trascritti.partiConTrascritto(it) }

    private fun rigeneraOgnuna(registrazioni: List<RegistrazioneId>): Esito<Unit> =
        registrazioni.fold<RegistrazioneId, Esito<Unit>>(Esito.Ok(Unit)) { esito, id ->
            esito.poi { esegui(RigeneraSbobinatura(id)) }
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
        val messaggio = e.message ?: "errore di I/O in sbobinature/"
        Esito.Errore(ErroreApplicazioneSbobinatura.ScritturaFallita(nomeFile, messaggio))
    }
}
