package snastro.sintesi.applicazione.comandi

import snastro.kernel.DispatcherEventi
import snastro.kernel.ErroreDominio
import snastro.kernel.Esito
import snastro.kernel.UnitaDiLavoro
import snastro.kernel.mappa
import snastro.kernel.poi
import snastro.sintesi.applicazione.eventi.RiassuntoAvviato
import snastro.sintesi.applicazione.eventi.RiassuntoFallito
import snastro.sintesi.applicazione.eventi.RiassuntoPronto
import snastro.sintesi.applicazione.porte.DisponibilitaModelloLinguistico
import snastro.sintesi.applicazione.porte.ErroreApplicazioneSintesi
import snastro.sintesi.applicazione.porte.LettoreNomi
import snastro.sintesi.applicazione.porte.LettoreTrascritto
import snastro.sintesi.applicazione.porte.ModelloLinguistico
import snastro.sintesi.applicazione.porte.RiassuntoRepository
import snastro.sintesi.applicazione.porte.RichiestaRiassunto
import snastro.sintesi.applicazione.porte.RispostaModello
import snastro.sintesi.applicazione.porte.SegmentoSintesi
import snastro.sintesi.applicazione.porte.StatoModelloLinguistico
import snastro.sintesi.dominio.BozzaElemento
import snastro.sintesi.dominio.BozzaRiassunto
import snastro.sintesi.dominio.IngressoRiassunto
import snastro.sintesi.dominio.MotivoFallimento
import snastro.sintesi.dominio.Riassunto
import snastro.sintesi.dominio.SegmentoIngresso
import snastro.sintesi.dominio.StrutturaTrascritto
import java.time.Clock
import java.time.Instant

/**
 * Use-case `EseguiProssimoRiassunto` (AC-S83..AC-S89, ADR 0023 §3): three phases, with NO transaction
 * around the model (ADR 0012, ADR 0021 §4, AC-S84):
 * 1. **Claim** — one short transaction reads the oldest eligible `in_attesa` Riassunto (FIFO, [esclusi]
 *    and `primaDi` honoured) and moves it `in_corso`, publishing [RiassuntoAvviato].
 * 2. **Run**, OUTSIDE any transaction — checks [DisponibilitaModelloLinguistico] first (AC-S87: the
 *    model is never called when it is not `Installato`), then reads the Segmenti ([LettoreTrascritto])
 *    and the CURRENT names ([LettoreNomi]) — never earlier, so a Revisione committed meanwhile is what
 *    the run sees (AC-S88) — builds the labelled input ([IngressoRiassunto]) with the Riassunto's OWN
 *    cap ([INV-S10], AC-S85), and calls [ModelloLinguistico].
 * 3. **Complete** — the raw answer is verified by the root itself ([Riassunto.completa], INV-S4) against
 *    the structure read in step 2, or the mapped failure is applied ([Riassunto.fallisci]); either way
 *    the result is committed ONLY through [RiassuntoRepository.concludi] (ADR 0022 §4's compare-and-set),
 *    which itself removes any previous `pronto` of the Registrazione — this service never calls
 *    `rimuovi` (D-0003, AC-S86). [RiassuntoRepository.concludi] returning `false` (the row vanished or is
 *    no longer `in_corso` — e.g. a concurrent eliminazione/sostituzione policy, INV-S8) means nothing is
 *    written and NOTHING is published. `Errore(Annullato)` from the model skips this step entirely:
 *    nothing is written, nothing published.
 *
 * [annullato] is this run's own cancellation flag (ADR 0023 §5): a constructor collaborator, not part of
 * the pinned [EseguiProssimoRiassunto] command — the shared queue (`:avvio`, not yet built) can inject a
 * real one later; thread interruption (checked by [ModelloLinguistico] itself) is the cancellation
 * channel this block wires by default.
 */
@Suppress("LongParameterList") // one parameter per collaborator (uow, clock, repo, 2 read ports, model, eventi, …)
public class EseguiProssimoRiassuntoServizio(
    private val uow: UnitaDiLavoro,
    private val orologio: Clock,
    private val riassunti: RiassuntoRepository,
    private val trascritti: LettoreTrascritto,
    private val nomi: LettoreNomi,
    private val modello: ModelloLinguistico,
    private val disponibilita: DisponibilitaModelloLinguistico,
    private val eventi: DispatcherEventi,
    private val annullato: () -> Boolean = { false },
) {
    @Suppress("ReturnCount") // guard clauses (empty queue / bound refused / race) — clearer than nesting
    public fun esegui(comando: EseguiProssimoRiassunto): Esito<RisultatoRiassunto> {
        val esito = uow.inTransazione { avviaIlPiuVecchio(comando.esclusi, comando.primaDi) }
        if (esito is Esito.Errore) return esito
        val riassunto = (esito as Esito.Ok).valore ?: return Esito.Ok(RisultatoRiassunto.Nessuno)
        eseguiEConcludi(riassunto)
        return Esito.Ok(RisultatoRiassunto.Avviato(riassunto.id))
    }

    /** Reads the oldest eligible `in_attesa` and marks it `in_corso` in ONE transaction (AC-S83). */
    @Suppress("ReturnCount") // guard clauses (empty queue / bound refused) — clearer than nesting
    private fun avviaIlPiuVecchio(esclusi: Set<String>, primaDi: Instant?): Esito<Riassunto?> {
        val prossimo = riassunti.inAttesa().firstOrNull { it.id.valore !in esclusi } ?: return Esito.Ok(null)
        if (primaDi != null && prossimo.richiestoAlle >= primaDi) return Esito.Ok(null) // ADR 0023 §2: strict <
        return prossimo.avvia(orologio.instant())
            .poi { riassunti.salva(prossimo) }
            .mappa {
                eventi.pubblica(RiassuntoAvviato(prossimo.registrazioneId))
                prossimo
            }
    }

    /** Phases 2 (run) + 3 (complete): no transaction is open until the final compare-and-set. */
    private fun eseguiEConcludi(riassunto: Riassunto) {
        when (val esecuzione = eseguiSulModello(riassunto)) {
            EsecuzioneModello.Annullata -> Unit // INV-S8: nothing written, nothing published
            is EsecuzioneModello.Fallita -> concludi(riassunto) { riassunto.fallisci(esecuzione.motivo) }
            is EsecuzioneModello.Completata ->
                concludi(riassunto) { riassunto.completa(esecuzione.bozza, esecuzione.struttura) }
        }
    }

    /** AC-S84: no transaction is open here. AC-S87: [ModelloLinguistico] is skipped when not Installato. */
    private fun eseguiSulModello(riassunto: Riassunto): EsecuzioneModello {
        if (disponibilita.stato() !is StatoModelloLinguistico.Installato) {
            return EsecuzioneModello.Fallita(MotivoFallimento.MODELLO_NON_DISPONIBILE)
        }
        val registrazioneId = riassunto.registrazioneId
        val segmenti = checkNotNull(trascritti.segmenti(registrazioneId)) {
            "Trascritto assente per $registrazioneId a Riassunto in_corso"
        }
        val struttura = StrutturaTrascritto.di(segmenti.map { it.segmentoId to it.voceId })
        return when (val risposta = modello.riassumi(richiesta(riassunto, segmenti), annullato)) {
            is Esito.Ok -> EsecuzioneModello.Completata(risposta.valore.inBozza(), struttura)
            is Esito.Errore -> mappaErrore(risposta.errore)
        }
    }

    /** AC-S85: the legend names Voci through [LettoreNomi] read NOW; the cap is the Riassunto's OWN. */
    private fun richiesta(riassunto: Riassunto, segmenti: List<SegmentoSintesi>): RichiestaRiassunto {
        val nomiPerVoce = nomi.nomi(riassunto.registrazioneId).mapKeys { it.key.voceId }
        val ingresso = IngressoRiassunto.costruisci(
            segmenti.map { SegmentoIngresso(it.segmentoId, it.voceId, it.intervallo.inizioMs, it.testo) },
            nomiPerVoce,
        )
        return RichiestaRiassunto(ingresso, riassunto.argomento?.valore, riassunto.lunghezzaMassima.valore)
    }

    /**
     * The completion compare-and-set (ADR 0022 §4, D-0003): [transizione] runs on OUR OWN in-memory
     * [riassunto] (already `in_corso`, so it never actually fails); only [RiassuntoRepository.concludi]
     * writes, and only a write it accepts is published (AC-S86, INV-S8).
     */
    private fun concludi(riassunto: Riassunto, transizione: () -> Esito<*>) {
        val transito = transizione()
        check(transito !is Esito.Errore) { "transizione impossibile su un Riassunto appena avviato: $transito" }
        uow.inTransazione {
            riassunti.concludi(riassunto).poi { scritto ->
                if (scritto) eventi.pubblica(eventoConclusione(riassunto))
                Esito.Ok(Unit)
            }
        }
    }

    private fun eventoConclusione(riassunto: Riassunto) = if (riassunto.pronto) {
        RiassuntoPronto(riassunto.registrazioneId)
    } else {
        RiassuntoFallito(riassunto.registrazioneId, checkNotNull(riassunto.motivoFallimento).codice)
    }
}

/** What [ModelloLinguistico] produced for phase 3, mapped from [ErroreApplicazioneSintesi] (ADR 0021 §4). */
private sealed interface EsecuzioneModello {
    data class Completata(val bozza: BozzaRiassunto, val struttura: StrutturaTrascritto) : EsecuzioneModello
    data class Fallita(val motivo: MotivoFallimento) : EsecuzioneModello
    data object Annullata : EsecuzioneModello
}

/**
 * ADR 0021 §4's failure mapping. `null` (a foreign [ErroreDominio]) never happens by the port's own
 * contract — kept exhaustive rather than an unchecked cast (ADR 0003).
 */
private fun mappaErrore(errore: ErroreDominio): EsecuzioneModello = when (errore as? ErroreApplicazioneSintesi) {
    ErroreApplicazioneSintesi.ModelloNonDisponibile -> fallita(MotivoFallimento.MODELLO_NON_DISPONIBILE)
    is ErroreApplicazioneSintesi.IngressoTroppoLungo -> fallita(MotivoFallimento.TROPPO_LUNGA)
    is ErroreApplicazioneSintesi.ErroreRuntime -> fallita(MotivoFallimento.ERRORE_MODELLO)
    ErroreApplicazioneSintesi.RispostaNonValida -> fallita(MotivoFallimento.ERRORE_MODELLO)
    ErroreApplicazioneSintesi.Annullato -> EsecuzioneModello.Annullata
    null -> error("ModelloLinguistico ha restituito un errore inatteso: $errore")
}

private fun fallita(motivo: MotivoFallimento): EsecuzioneModello = EsecuzioneModello.Fallita(motivo)

/** Raw, unverified: [Riassunto.completa] applies the Verifica delle fonti (INV-S4) to it. */
private fun RispostaModello.inBozza(): BozzaRiassunto = BozzaRiassunto(
    sommario = sommario,
    decisioni = decisioni.map { BozzaElemento(it.testo, it.fonti, voce = null) },
    questioniAperte = questioniAperte.map { BozzaElemento(it.testo, it.fonti, voce = null) },
    azioni = azioni.map { BozzaElemento(it.testo, it.fonti, it.responsabile) },
    puntiChiave = puntiChiave.map { BozzaElemento(it.testo, it.fonti, it.parlante) },
)
