package snastro.sintesi.applicazione.comandi

import snastro.kernel.DispatcherEventi
import snastro.kernel.Esito
import snastro.kernel.GeneratoreId
import snastro.kernel.IncontroId
import snastro.kernel.ProgettoId
import snastro.kernel.UnitaDiLavoro
import snastro.kernel.poi
import snastro.sintesi.applicazione.eventi.RiassuntoRichiesto
import snastro.sintesi.applicazione.porte.DisponibilitaModelloLinguistico
import snastro.sintesi.applicazione.porte.LettoreIncontro
import snastro.sintesi.applicazione.porte.LettoreTrascritto
import snastro.sintesi.applicazione.porte.LunghezzaMassimaRiassuntoRepository
import snastro.sintesi.applicazione.porte.ParteSintesi
import snastro.sintesi.applicazione.porte.RiassuntoRepository
import snastro.sintesi.applicazione.porte.StatoModelloLinguistico
import snastro.sintesi.applicazione.porte.riassumibilitaInDuePassi
import snastro.sintesi.applicazione.porte.stimaTokenDi
import snastro.sintesi.dominio.Argomento
import snastro.sintesi.dominio.ErroreSintesi
import snastro.sintesi.dominio.Riassumibilita
import snastro.sintesi.dominio.Riassunto
import snastro.sintesi.dominio.RiassuntoId
import snastro.sintesi.dominio.RiassuntoRichiestoDominio
import java.time.Clock

/**
 * Use-case `Riassumi` (AC-S77..S82; INV-S2, INV-S3, INV-S6, INV-S10; ADR 0021 §3). One transaction:
 * - reads the Incontro's Parti and every [Riassumibilita] guard (model `Installato`, EVERY Parte `TRASCRITTA`, no open
 *   Riassunto, the whole Incontro's labelled input within the limit — the same rule `riassunto-vista` shares) —
 *   INV-I9, D-0020; an unknown Incontro is `IncontroNonTrovato`, checked before every other guard;
 * - validates the [Argomento] (AFTER the guards: `What to do`);
 * - reads the Progetto's current lunghezza massima and fixes it on the new Riassunto (INV-S10);
 * - removes a previous `fallito` of the Incontro in this same transaction, leaving a `pronto`
 *   untouched (INV-S3);
 * - creates the Riassunto `in_attesa` and publishes `RiassuntoRichiesto`, delivered after commit only
 *   (ADR 0012).
 *
 * Never starts a download: [disponibilita] is read-only ([DisponibilitaModelloLinguistico.stato] is its only
 * member) — the download is the user's own, from the Riassunto tab (RC-8, ADR 0025 §4).
 */
@Suppress("LongParameterList") // one parameter per collaborator: uow, id/clock/progetto, 4 ports, eventi
public class RiassumiServizio(
    private val uow: UnitaDiLavoro,
    private val generatoreId: GeneratoreId,
    private val clock: Clock,
    private val progettoId: ProgettoId,
    private val riassunti: RiassuntoRepository,
    private val lunghezzeMassime: LunghezzaMassimaRiassuntoRepository,
    private val trascritti: LettoreTrascritto,
    private val incontri: LettoreIncontro,
    private val disponibilita: DisponibilitaModelloLinguistico,
    private val eventi: DispatcherEventi,
) {
    public fun esegui(c: Riassumi): Esito<RiassuntoId> = uow.inTransazione {
        // ADR 0037 §2 / D-0020: every Parte of the Incontro, in INV-I2 order, read through the ports in this
        // transaction; the verdict is Riassumibilita's (the same rule the view shows). The Incontro is looked up
        // FIRST: an unknown (or ceased, or Parte-less) Incontro is IncontroNonTrovato before any Riassumibilita
        // reason, the model's included — there is nothing to evaluate the guards on.
        val parti = incontri.parti(c.incontroId)?.takeIf { it.isNotEmpty() }
        if (parti == null) {
            Esito.Errore(ErroreSintesi.IncontroNonTrovato(c.incontroId))
        } else {
            riassumibile(c.incontroId, parti)
                .poi { Argomento.di(c.argomento) }
                .poi { argomento -> crea(c.incontroId, argomento) }
        }
    }

    /**
     * [Riassumibilita] in two passes ([riassumibilitaInDuePassi]). A `RiassuntoGiaAperto` carries [incontroId], equal
     * to the repository backstop's own (INV-S2).
     */
    private fun riassumibile(incontroId: IncontroId, parti: List<ParteSintesi>): Esito<Unit> {
        val modelloInstallato = disponibilita.stato() is StatoModelloLinguistico.Installato
        val stati = parti.map { it.numero to trascritti.statoParte(it.registrazioneId) }
        val riassuntoAperto = riassunti.trova(incontroId).any { it.aperto }
        val esito = riassumibilitaInDuePassi(modelloInstallato, stati, riassuntoAperto) {
            stimaTokenDi(parti, trascritti::segmenti)
        }
        val errore = (esito as? Esito.Errore)?.errore
        return if (errore is ErroreSintesi.RiassuntoGiaAperto) {
            Esito.Errore(ErroreSintesi.RiassuntoGiaAperto(incontroId))
        } else {
            esito
        }
    }

    /**
     * INV-S3 (previous `fallito` removed — an `Errore` of [RiassuntoRepository.rimuovi] stops here and rolls back,
     * ADR 0003), INV-S10 (cap fixed at request), then `Riassunto.richiedi`.
     */
    private fun crea(incontroId: IncontroId, argomento: Argomento?): Esito<RiassuntoId> {
        val fallito = riassunti.trova(incontroId).singleOrNull { it.fallito }
        val rimosso = fallito?.let { riassunti.rimuovi(it.id) } ?: Esito.Ok(Unit)
        return rimosso.poi {
            val cap = lunghezzeMassime.trova(progettoId).parole
            val id = RiassuntoId(generatoreId.nuovo())
            val creato = Riassunto.richiedi(id, incontroId, argomento, cap, clock.instant())
            riassunti.salva(creato.aggregato).poi {
                eventi.pubblica(creato.evento.pubblicato())
                Esito.Ok(id)
            }
        }
    }
}

private fun RiassuntoRichiestoDominio.pubblicato(): RiassuntoRichiesto = RiassuntoRichiesto(incontroId)
