package snastro.sintesi.applicazione.politiche

import snastro.kernel.DispatcherEventi
import snastro.kernel.Esito
import snastro.kernel.GeneratoreId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.poi
import snastro.sintesi.applicazione.eventi.RiassuntoEliminato
import snastro.sintesi.applicazione.eventi.RiassuntoRichiesto
import snastro.sintesi.applicazione.porte.DisponibilitaModelloLinguistico
import snastro.sintesi.applicazione.porte.LettoreTrascritto
import snastro.sintesi.applicazione.porte.LunghezzaMassimaRiassuntoRepository
import snastro.sintesi.applicazione.porte.RiassuntoRepository
import snastro.sintesi.applicazione.porte.SegmentoSintesi
import snastro.sintesi.applicazione.porte.StatoModelloLinguistico
import snastro.sintesi.dominio.Argomento
import snastro.sintesi.dominio.IngressoRiassunto
import snastro.sintesi.dominio.LimiteIngresso
import snastro.sintesi.dominio.Riassumibilita
import snastro.sintesi.dominio.Riassunto
import snastro.sintesi.dominio.RiassuntoId
import snastro.sintesi.dominio.RiassuntoRichiestoDominio
import snastro.sintesi.dominio.SegmentoIngresso
import java.time.Clock

/**
 * Policy `ApplicaSostituzioneTrascrittoSintesi` (INV-S8, INV-S10; ADR 0018 §5, ADR 0021 §6): reacts to
 * `TrascrittoSostituito`, run INSIDE the re-run's completion transaction, AFTER ADR 0018 §2 already saved the
 * new Trascritto — which [applica] reads through [trascritti] (AC-S96), never the old generation.
 *
 * Removes every Riassunto of the Registrazione, whatever its state (INV-S8: AC-S93 an `in_attesa`/`in_corso`
 * one goes too, so its eventual completion — `esegui-riassunto` — writes nothing). Only when at least one
 * existed (AC-S94) does it evaluate the NEW Trascritto against [Riassumibilita], RESTRICTED to {model
 * `Installato`, input within the limit}: the other three preconditions cannot fail at this point in the
 * transaction — the Trascritto just read exists by construction (ADR 0018 §2 order), no Elaborazione of this
 * Registrazione is open (the one that just completed was the only one `elaborazione_aperta_unica` allowed),
 * and every Riassunto was just removed above — so passing their known value is not a shortcut, it is what the
 * transaction already guarantees; `elaborazioneAperta` is never even read through [trascritti] (AC-S96 only
 * exercises [LettoreTrascritto.segmenti]).
 *
 * `abbonato-trascrizione-sintesi` (`:sintesi:adattatori`, a later block) is the synchronous subscriber that
 * translates `TrascrittoSostituito(registrazioneId)` into [applica]; this policy never imports that
 * Trascrizione event (ADR 0002 edges) — translating it is the subscriber's job, not this one's.
 *
 * STRUCTURAL only (ADR 0012 Amendment (b), ADR 0021 §6): `ModelloLinguistico` is never a collaborator
 * (AC-S97, constructor test) — [disponibilita] exposes only [DisponibilitaModelloLinguistico.stato], so there
 * is no member through which this policy could call the model.
 */
@Suppress("LongParameterList") // one parameter per collaborator: id/clock/progetto, 4 ports, eventi
public class ApplicaSostituzioneTrascrittoSintesiPolitica(
    private val generatoreId: GeneratoreId,
    private val clock: Clock,
    private val progettoId: ProgettoId,
    private val riassunti: RiassuntoRepository,
    private val lunghezzeMassime: LunghezzaMassimaRiassuntoRepository,
    private val trascritti: LettoreTrascritto,
    private val disponibilita: DisponibilitaModelloLinguistico,
    private val eventi: DispatcherEventi,
) {
    public fun applica(registrazioneId: RegistrazioneId): Esito<Unit> {
        val rimossi = riassunti.diRegistrazione(registrazioneId)
        if (rimossi.isEmpty()) return Esito.Ok(Unit) // AC-S94: nothing existed, nothing created, nothing published
        return riassunti.rimuoviDiRegistrazione(registrazioneId).poi {
            eventi.pubblica(RiassuntoEliminato(registrazioneId))
            riaccoda(registrazioneId, rimossi)
        }
    }

    /**
     * INV-S10 (the Progetto's CURRENT cap, read now — never the removed one's), the Argomento of the most
     * recently removed Riassunto (by `richiestoAlle`, AC-S92); never `Errore` for a refused re-summary (AC-S95).
     */
    private fun riaccoda(registrazioneId: RegistrazioneId, rimossi: List<Riassunto>): Esito<Unit> {
        // Unlike `esegui-riassunto`'s phase 2 (outside any transaction, a real race), this runs INSIDE the same
        // completion transaction that just wrote the new Trascritto (ADR 0018 §2 order): a null read here is not
        // a race but a broken structural precondition — fail loud (programmer error, ADR 0003) rather than
        // silently skipping the re-summary as if it had been legitimately refused.
        val segmenti = checkNotNull(trascritti.segmenti(registrazioneId)) {
            "Trascritto assente per $registrazioneId nel riaccodo: violazione di ADR 0018 §2 " +
                "(dovrebbe esistere per costruzione)"
        }
        val idoneo = Riassumibilita.valuta(
            registrazioneId = registrazioneId,
            modelloInstallato = disponibilita.stato() is StatoModelloLinguistico.Installato,
            trascrittoPresente = true, // garantito dal checkNotNull sopra
            // elaborazioneAperta/riassuntoAperto: known false by the calling contract this class's own KDoc
            // documents (the only open run just completed; every Riassunto of r was just removed above) — reading
            // either through a port here would itself violate AC-S96 (this policy calls no such lookup).
            elaborazioneAperta = false,
            riassuntoAperto = false,
            stimaToken = LimiteIngresso.stimaToken(ingressoDi(segmenti)),
        )
        if (idoneo !is Esito.Ok) return Esito.Ok(Unit) // AC-S95: refused re-summary, nothing created
        val argomento: Argomento? = rimossi.maxWith(compareBy({ it.richiestoAlle }, { it.id.valore })).argomento
        val cap = lunghezzeMassime.trova(progettoId).parole
        val creato =
            Riassunto.richiedi(RiassuntoId(generatoreId.nuovo()), registrazioneId, argomento, cap, clock.instant())
        return riassunti.salva(creato.aggregato).poi {
            eventi.pubblica(creato.evento.pubblicato())
            Esito.Ok(Unit)
        }
    }

    /** The pure labelled input ([IngressoRiassunto]), name-free like `riassumi`: no `LettoreNomi` collaborator —
     * it only feeds [LimiteIngresso]'s guard estimate, never the model (this policy calls no `ModelloLinguistico`). */
    private fun ingressoDi(segmenti: List<SegmentoSintesi>): String = IngressoRiassunto.costruisci(
        segmenti.map { SegmentoIngresso(it.segmentoId, it.voceId, it.intervallo.inizioMs, it.testo) },
        nomi = emptyMap(),
    )
}

private fun RiassuntoRichiestoDominio.pubblicato(): RiassuntoRichiesto = RiassuntoRichiesto(registrazioneId)
