package snastro.sintesi.applicazione.comandi

import snastro.kernel.DispatcherEventi
import snastro.kernel.Esito
import snastro.kernel.GeneratoreId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.UnitaDiLavoro
import snastro.kernel.poi
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
import snastro.sintesi.dominio.SegmentoIngresso
import java.time.Clock

/**
 * Use-case `Riassumi` (AC-S77..S82; INV-S2, INV-S3, INV-S6, INV-S10; ADR 0021 §3). One transaction:
 * - reads every [Riassumibilita] guard (model `Installato`, Trascritto present, no open Elaborazione,
 *   no open Riassunto, the labelled input's estimate within the limit — the same rule `riassunto-vista`
 *   shares) — INV-S6;
 * - validates the [Argomento] (AFTER the guards: `What to do`);
 * - reads the Progetto's current lunghezza massima and fixes it on the new Riassunto (INV-S10);
 * - removes a previous `fallito` of the Registrazione in this same transaction, leaving a `pronto`
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
    private val disponibilita: DisponibilitaModelloLinguistico,
    private val eventi: DispatcherEventi,
) {
    public fun esegui(c: Riassumi): Esito<RiassuntoId> = uow.inTransazione {
        val segmenti = trascritti.segmenti(c.registrazioneId)
        val stimaToken = segmenti?.let { LimiteIngresso.stimaToken(ingressoDi(it)) }
        Riassumibilita.valuta(
            registrazioneId = c.registrazioneId,
            modelloInstallato = disponibilita.stato() is StatoModelloLinguistico.Installato,
            trascrittoPresente = segmenti != null,
            elaborazioneAperta = trascritti.elaborazioneAperta(c.registrazioneId),
            riassuntoAperto = riassunti.diRegistrazione(c.registrazioneId).any { it.aperto },
            stimaToken = stimaToken,
        )
            .poi { Argomento.di(c.argomento) }
            .poi { argomento -> crea(c.registrazioneId, argomento) }
    }

    /** INV-S3 (previous `fallito` removed), INV-S10 (cap fixed at request), then `Riassunto.richiedi`. */
    private fun crea(registrazioneId: RegistrazioneId, argomento: Argomento?): Esito<RiassuntoId> {
        riassunti.diRegistrazione(registrazioneId).singleOrNull { it.fallito }?.let { riassunti.rimuovi(it.id) }
        val cap = lunghezzeMassime.trova(progettoId).parole
        val id = RiassuntoId(generatoreId.nuovo())
        val creato = Riassunto.richiedi(id, registrazioneId, argomento, cap, clock.instant())
        return riassunti.salva(creato.aggregato).poi {
            eventi.pubblica(RiassuntoRichiesto(registrazioneId))
            Esito.Ok(id)
        }
    }

    /** The pure labelled input ([IngressoRiassunto]), without names: [riassumi] has no `LettoreNomi` (not in its
     * consumes — the legend falls back to "Voce n", which does not change the token estimate's purpose: a guard). */
    private fun ingressoDi(segmenti: List<SegmentoSintesi>): String = IngressoRiassunto.costruisci(
        segmenti.map { SegmentoIngresso(it.segmentoId, it.voceId, it.intervallo.inizioMs, it.testo) },
        nomi = emptyMap(),
    )
}
