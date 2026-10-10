package snastro.ui.registrazioni

import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.parlanti.applicazione.letture.IdentificazioneIncontro
import snastro.progetto.applicazione.letture.IncontroDelProgettoVista
import snastro.progetto.applicazione.letture.RegistrazioneDelProgettoVista
import snastro.trascrizione.applicazione.letture.StatoElaborazioneVista
import snastro.trascrizione.applicazione.letture.StatoRegistrazioneVista
import snastro.ui.coda.PosizioniCoda
import snastro.ui.nonFataleRegistrato
import snastro.ui.testi.MESSAGGIO_ELIMINA_DISABILITATA_IN_CODA
import snastro.ui.testi.MESSAGGIO_ELIMINA_DISABILITATA_IN_CORSO
import snastro.ui.testi.etichetta
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/**
 * The sources S2's list is read from ([LetturaS2]), built by `:avvio` (presenter-split, "Collaborators of an extracted
 * flow"): the Registrazioni and the Incontri of the project (Progetto), their [statiElaborazione] (AC-342), the
 * Parlanti count per Incontro (AC-204/AC-345), the shared queue's [posizioniNellaCoda] (ADR 0023 §4), the Incontro's
 * latest 'Numero di persone' (AC-I67) and the [clock] of the elapsed time. Every field is MANDATORY (ADR 0030 §1).
 */
data class LettureS2(
    val registrazioni: () -> List<RegistrazioneDelProgettoVista>,
    val incontri: () -> List<IncontroDelProgettoVista>,
    val statiElaborazione: (List<RegistrazioneId>) -> List<StatoRegistrazioneVista>,
    val identificazioniIncontri: (List<IncontroId>) -> Map<IncontroId, IdentificazioneIncontro>,
    val posizioniNellaCoda: () -> PosizioniCoda,
    val numeroPersonePrecompilato: (IncontroId) -> Int?,
    val clock: Clock,
)

/** What S2 shows: the Incontri ([incontri]) and the flat Parte rows ([righe]) they point to. */
internal class ListaS2(val righe: List<RigaRegistrazione>, val incontri: List<RigaIncontro>)

/**
 * Builds S2's list from [letture] (presenter-split): a pure read, called by the presenter's `carica` on `io`; the
 * generation counters, the merge into the screen and the error state stay with the presenter (M1, L485b). A row's
 * `riproduzione` here says only whether the player [disponibile] has its audio: the merge re-derives play/pause from
 * the player's state at merge time (L478a).
 *
 * The Registrazioni are joined with `stati-elaborazione` by [RegistrazioneId] (AC-342), with the queue positions
 * (ADR 0023 §4: one snapshot per [lista] call, absent meaning no position, 0) and with `identificazione-registrazioni`:
 * a badge only once BOTH `numVoci` and the Parlanti count are known (AC-204/AC-345). A failing secondary source (the
 * Incontri, the Parlanti count, the prefill) costs only what it feeds, never the whole read.
 */
internal class LetturaS2(
    private val letture: LettureS2,
    private val disponibile: (RegistrazioneId) -> Boolean,
) {
    /** One Incontro of the list before its rows exist: [parti] are the Registrazioni present in the catalogue. */
    private class Gruppo(
        val incontroId: IncontroId,
        val titolo: String,
        val data: LocalDate,
        val parti: List<Pair<RegistrazioneDelProgettoVista, LocalTime?>>,
        val noto: Boolean = true,
    )

    fun lista(): ListaS2 {
        val gruppi = gruppi(letture.registrazioni())
        val ids = gruppi.flatMap { g -> g.parti.map { it.first.registrazioneId } }
        val stati = letture.statiElaborazione(ids).associateBy { it.registrazioneId }
        val conteggi = conteggiIdentificazione(gruppi.map { it.incontroId })
        val posizioni = letture.posizioniNellaCoda()
        val righe = mutableListOf<RigaRegistrazione>()
        val incontriRighe = gruppi.map { g ->
            val multi = g.parti.size > 1
            val daGruppo = g.parti.mapIndexed { i, (r, ora) ->
                costruisciRiga(
                    r,
                    stati[r.registrazioneId],
                    posizioni,
                    parte = if (multi) ParteDiIncontro(i + 1, g.titolo) else null,
                    ora = if (multi) ora else null,
                    identificazione = if (multi) null else conteggi[g.incontroId],
                )
            }
            righe += daGruppo
            RigaIncontro(
                incontroId = g.incontroId,
                titolo = g.titolo,
                data = g.data,
                durataMs = g.parti.sumOf { it.first.durataMs },
                parti = daGruppo.map { it.registrazioneId },
                stato = statoAggregato(daGruppo),
                identificazione = conteggi[g.incontroId]?.takeIf { multi }
                    ?.let { IdentificazioneRiga(it.numVoci, it.numVociDaIdentificare) },
                numeroPersone = if (multi) precompilato(g.incontroId) else "",
                aggiungiPartiDisponibile = g.noto,
            )
        }
        return ListaS2(righe, incontriRighe)
    }

    /**
     * AC-I69: the Incontri come from [LettureS2.incontri]; a failure of that read (or a Registrazione it does not list)
     * must not fail the whole load — each such Registrazione is then an Incontro of its own, today's S2. Its id is only
     * the Registrazione's (the migration's rule, wrong for a later UUID Incontro, L203): such a group is not
     * [Gruppo.noto], so it offers no 'Aggiungi parti…' that would target an Incontro that may not exist.
     */
    private fun gruppi(progetto: List<RegistrazioneDelProgettoVista>): List<Gruppo> {
        val perId = progetto.associateBy { it.registrazioneId }
        val letti = nonFataleRegistrato("leggi gli incontri") { letture.incontri() }.orEmpty()
        val raggruppati = letti.mapNotNull { i ->
            val parti = i.parti.mapNotNull { p -> perId[p.registrazioneId]?.let { it to p.oraDiInizio } }
            parti.takeIf { it.isNotEmpty() }?.let { Gruppo(i.incontroId, i.titolo, i.data, it) }
        }
        val coperti = raggruppati.flatMap { g -> g.parti.map { it.first.registrazioneId } }.toSet()
        val soli = progetto.filter { it.registrazioneId !in coperti }.map {
            val id = IncontroId(it.registrazioneId.valore)
            Gruppo(id, it.titolo, it.dataRegistrazione, listOf(it to null), noto = false)
        }
        return raggruppati + soli
    }

    /** AC-I67: the latest number used in the Incontro; a failing read only costs the prefill. */
    private fun precompilato(id: IncontroId): String = nonFataleRegistrato("leggi il numero di persone di $id") {
        letture.numeroPersonePrecompilato(id)?.toString()
    }.orEmpty()

    @Suppress("LongParameterList") // the row's sources: catalogue entry, status, queue, Incontro context
    private fun costruisciRiga(
        r: RegistrazioneDelProgettoVista,
        vista: StatoRegistrazioneVista?,
        posizioni: PosizioniCoda,
        parte: ParteDiIncontro?,
        ora: LocalTime?,
        identificazione: IdentificazioneIncontro?,
    ): RigaRegistrazione {
        val elaborazioneRiga = vista?.let { elaborazioneDi(it, posizioni) }
        // AC-451: FALLITA over an existing Trascritto renders as Completata + this notice, whatever
        // the `ritrascrivi` source's presence — it reports a FACT, independent of the action's
        // availability (which `ritrascriviDisponibile` gates on its own).
        val ritrascrizioneFallita = vista
            ?.takeIf { it.stato == StatoElaborazioneVista.FALLITA && it.trascrittoDisponibile }
            ?.motivoFallimento
        return RigaRegistrazione(
            registrazioneId = r.registrazioneId,
            titolo = r.titolo,
            dataRegistrazione = r.dataRegistrazione,
            durataMs = r.durataMs,
            elaborazione = elaborazioneRiga,
            riproduzione = if (disponibile(r.registrazioneId)) {
                StatoRiproduzioneRiga.Disponibile
            } else {
                StatoRiproduzioneRiga.NonDisponibile
            },
            numeroPersone = numeroPersonePrefillDi(vista),
            identificazione = identificazioneDi(vista, identificazione),
            trascrittoDisponibile = vista?.trascrittoDisponibile == true,
            elaborazioneId = vista?.elaborazioneId,
            ritrascriviDisponibile = elaborazioneRiga == StatoElaborazioneRiga.Completata,
            ritrascrizioneFallita = ritrascrizioneFallita,
            annullabile = elaborazioneRiga is StatoElaborazioneRiga.InAttesa,
            eliminazione = eliminazioneDi(elaborazioneRiga),
            parte = parte,
            oraDiInizio = ora,
        )
    }

    /** ADR 0020 §6/AC-625: disabled with its caption on an open Elaborazione (IN_ATTESA/IN_CORSO, plain
     * or re-run) — every other state (no Elaborazione yet, FALLITA, Completata) is
     * [StatoEliminazione.Disponibile]. */
    private fun eliminazioneDi(elaborazione: StatoElaborazioneRiga?): StatoEliminazione = when {
        elaborazione is StatoElaborazioneRiga.InAttesa ->
            StatoEliminazione.NonDisponibile(MESSAGGIO_ELIMINA_DISABILITATA_IN_CODA)
        elaborazione is StatoElaborazioneRiga.InCorso ->
            StatoEliminazione.NonDisponibile(MESSAGGIO_ELIMINA_DISABILITATA_IN_CORSO)
        else -> StatoEliminazione.Disponibile
    }

    /** AC-376 (FALLITA) / AC-448 (Completata, `ritrascrivi` is always wired): the 'Numero di persone'
     * field prefilled from the row's latest Elaborazione — empty otherwise. */
    private fun numeroPersonePrefillDi(v: StatoRegistrazioneVista?): String {
        val fallita = v?.stato == StatoElaborazioneVista.FALLITA
        val completataConRitrascrivi = v?.stato == StatoElaborazioneVista.COMPLETATA
        return if (fallita || completataConRitrascrivi) v?.numeroPersone?.toString().orEmpty() else ""
    }

    /**
     * AC-345: an empty map — never a partial batch — when [LettureS2.identificazioniIncontri]'s read throws; a throw
     * here is logged and contained to this batch (never the failure of [lista]'s OTHER sources), so a failing Parlanti
     * source only costs every row its badge, the rest of each row (title, date, status, playback…) stays built from its
     * own source.
     */
    private fun conteggiIdentificazione(ids: List<IncontroId>): Map<IncontroId, IdentificazioneIncontro> =
        nonFataleRegistrato("leggi l'identificazione degli incontri") { letture.identificazioniIncontri(ids) }.orEmpty()

    /** AC-204/AC-345: a badge on a 1-part row only once BOTH `numVoci` (only known for `COMPLETATA`) and the
     * Parlanti [conteggio] of its Incontro are known — a missing [vista]/`numVoci`/[conteggio] (source
     * absent, not yet loaded for this row, or failed) means no badge, never a provisional one. */
    private fun identificazioneDi(
        vista: StatoRegistrazioneVista?,
        conteggio: IdentificazioneIncontro?,
    ): IdentificazioneRiga? =
        vista?.numVoci?.let { numVoci -> conteggio?.let { IdentificazioneRiga(numVoci, it.numVociDaIdentificare) } }

    // ADR 0018/AC-450: IN_ATTESA/IN_CORSO carry `ritrascrizione` = trascrittoDisponibile — a re-run
    // over an existing Trascritto gets the "Ritrascrizione …" label instead of the plain one.
    // AC-451: FALLITA over an existing Trascritto is NOT `Fallita` — it renders as `Completata`
    // (`RigaRegistrazione.ritrascrizioneFallita` carries the notice); a plain FALLITA keeps 'Riprova'.
    private fun elaborazioneDi(
        v: StatoRegistrazioneVista,
        posizioni: PosizioniCoda,
    ): StatoElaborazioneRiga = when (v.stato) {
        StatoElaborazioneVista.NON_AVVIATA -> StatoElaborazioneRiga.NonAvviata
        StatoElaborazioneVista.IN_ATTESA ->
            StatoElaborazioneRiga.InAttesa(
                posizioni.elaborazioni[v.registrazioneId] ?: 0,
                ritrascrizione = v.trascrittoDisponibile,
            )
        StatoElaborazioneVista.IN_CORSO -> StatoElaborazioneRiga.InCorso(
            faseEtichetta = v.fase?.let(::etichetta).orEmpty(),
            trascorsoMs = v.avviataAlle
                ?.let { Duration.between(it, letture.clock.instant()).toMillis().coerceAtLeast(0) }
                ?: 0,
            ritrascrizione = v.trascrittoDisponibile,
        )
        StatoElaborazioneVista.FALLITA -> if (v.trascrittoDisponibile) {
            StatoElaborazioneRiga.Completata
        } else {
            StatoElaborazioneRiga.Fallita(v.motivoFallimento.orEmpty(), v.elaborazioneId) // L548c
        }
        StatoElaborazioneVista.COMPLETATA -> StatoElaborazioneRiga.Completata
    }
}
