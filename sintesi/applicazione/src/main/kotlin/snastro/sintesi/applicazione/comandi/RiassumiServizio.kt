package snastro.sintesi.applicazione.comandi

import snastro.kernel.DispatcherEventi
import snastro.kernel.Esito
import snastro.kernel.GeneratoreId
import snastro.kernel.IncontroId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.UnitaDiLavoro
import snastro.kernel.poi
import snastro.sintesi.applicazione.eventi.RiassuntoRichiesto
import snastro.sintesi.applicazione.porte.DisponibilitaModelloLinguistico
import snastro.sintesi.applicazione.porte.LettoreIncontro
import snastro.sintesi.applicazione.porte.LettoreTrascritto
import snastro.sintesi.applicazione.porte.LunghezzaMassimaRiassuntoRepository
import snastro.sintesi.applicazione.porte.PRIMA_PARTE
import snastro.sintesi.applicazione.porte.RiassuntoRepository
import snastro.sintesi.applicazione.porte.SegmentoSintesi
import snastro.sintesi.applicazione.porte.StatoModelloLinguistico
import snastro.sintesi.applicazione.porte.inIngresso
import snastro.sintesi.applicazione.porte.parteUnica
import snastro.sintesi.applicazione.porte.statoParte
import snastro.sintesi.dominio.Argomento
import snastro.sintesi.dominio.IngressoRiassunto
import snastro.sintesi.dominio.LimiteIngresso
import snastro.sintesi.dominio.Riassumibilita
import snastro.sintesi.dominio.Riassunto
import snastro.sintesi.dominio.RiassuntoId
import snastro.sintesi.dominio.RiassuntoRichiestoDominio
import java.time.Clock

/**
 * Use-case `Riassumi` (AC-S77..S82; INV-S2, INV-S3, INV-S6, INV-S10; ADR 0021 §3). One transaction:
 * - reads every [Riassumibilita] guard (model `Installato`, the Parte `TRASCRITTA` — Trascritto present, no open
 *   Elaborazione —, no open Riassunto, the labelled input's estimate within the limit — the same rule
 *   `riassunto-vista` shares) — INV-I9;
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
        // ADR 0033 §4.1: the Incontro's Parte, then its per-Parte reads; an unknown Incontro has no Trascritto.
        // TRANSITION (D-0033): the one Parte is Parte 1; multi-Parte reads come with riassumi-incontro.
        val parte = incontri.parteUnica(c.incontroId)
        val segmenti = parte?.let(trascritti::segmenti)
        val stimaToken = if (parte != null && segmenti != null) {
            LimiteIngresso.stimaToken(ingressoDi(parte, segmenti))
        } else {
            null
        }
        Riassumibilita.valuta(
            modelloInstallato = disponibilita.stato() is StatoModelloLinguistico.Installato,
            stati = listOf(PRIMA_PARTE to trascritti.statoParte(parte, segmenti)),
            riassuntoAperto = riassunti.trova(c.incontroId).any { it.aperto },
            stimaToken = stimaToken,
        )
            .poi { Argomento.di(c.argomento) }
            .poi { argomento -> crea(c.incontroId, argomento) }
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

    /** The pure labelled input ([IngressoRiassunto]), without names (ADR 0032), of the one Parte. */
    private fun ingressoDi(parte: RegistrazioneId, segmenti: List<SegmentoSintesi>): String =
        IngressoRiassunto.costruisci(listOf(segmenti.map { it.inIngresso(parte) })).testo
}

private fun RiassuntoRichiestoDominio.pubblicato(): RiassuntoRichiesto = RiassuntoRichiesto(incontroId)
