package snastro.sintesi.applicazione.letture

import snastro.kernel.Esito
import snastro.kernel.IncontroId
import snastro.kernel.LetturaCoerente
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.sintesi.applicazione.porte.DisponibilitaModelloLinguistico
import snastro.sintesi.applicazione.porte.LettoreIncontro
import snastro.sintesi.applicazione.porte.LettoreNomi
import snastro.sintesi.applicazione.porte.LettoreTrascritto
import snastro.sintesi.applicazione.porte.RiassuntoRepository
import snastro.sintesi.applicazione.porte.SegmentoSintesi
import snastro.sintesi.applicazione.porte.StatoModelloLinguistico
import snastro.sintesi.applicazione.porte.parteUnica
import snastro.sintesi.dominio.ErroreSintesi
import snastro.sintesi.dominio.IngressoRiassunto
import snastro.sintesi.dominio.LimiteIngresso
import snastro.sintesi.dominio.ParteTesto
import snastro.sintesi.dominio.Riassumibilita
import snastro.sintesi.dominio.Riassunto
import snastro.sintesi.dominio.SegmentoIngresso
import snastro.sintesi.dominio.StrutturaTrascritto
import snastro.sintesi.dominio.TestoConVoci

/**
 * Read-model `vista-riassunto` (AC-S102..S108, ADR 0021 §3, ADR 0023 §4, ADR 0029 §5/AC-C32): builds
 * [RiassuntoVista] for one Registrazione with no side effect, never calling
 * [snastro.sintesi.applicazione.porte.ModelloLinguistico]. `null` when [trascritti] has no Trascritto
 * for it (the tab is not offered). Names for display are joined from [nomi] at read time (INV-S5): a
 * rename never invalidates a stored Riassunto. [di] reads the Segmenti (through [trascritti]) and the
 * Riassunto rows (through [riassunti]) from ONE [lettura] snapshot — never a row read before a
 * concurrent completion commits and its elements after (a stale reconstitution would throw): this
 * read-model owns the snapshot, no composition root wraps it (dev-architecture-app.md#repository).
 */
public class RiassuntoVisteLettura(
    private val lettura: LetturaCoerente,
    private val riassunti: RiassuntoRepository,
    private val trascritti: LettoreTrascritto,
    private val incontri: LettoreIncontro,
    private val nomi: LettoreNomi,
    private val disponibilitaModello: DisponibilitaModelloLinguistico,
) {
    /** The Riassunto view of the Incontro [i] (ADR 0037 §1), read through its Parte (ADR 0033 §4.1). */
    public fun di(i: IncontroId): RiassuntoVista? = lettura.inLettura {
        val parte = incontri.parteUnica(i) ?: return@inLettura null
        val segmenti = trascritti.segmenti(parte) ?: return@inLettura null
        val correnti = segmenti.associateBy { it.segmentoId }
        val nomiVoci = nomi.nomi(parte)
        val righe = riassunti.trova(i)
        val pronto = unicoOSseNessuno(righe.filter { it.pronto }, i, "pronto")
        val nonPronto = unicoOSseNessuno(righe.filterNot { it.pronto }, i, "non pronto")

        RiassuntoVista(
            incontroId = i,
            modello = statoModelloVista(disponibilitaModello.stato()),
            richiestaAperta = nonPronto?.takeIf { it.aperto }?.let(::richiestaApertaVista),
            ultimoFallimento = nonPronto?.takeIf { it.fallito }?.let(::fallimentoVista),
            disponibilita = disponibilita(i, parte, segmenti),
            argomentoPrecompilato = argomentoPrecompilato(pronto, nonPronto?.takeIf { it.fallito }),
            mostrato = pronto?.let { mostratoVista(it, parte, correnti, nomiVoci, i) },
        )
    }

    /**
     * AC-S107: [Riassumibilita] restricted to the two reasons this read-model decides on its own — no
     * write, no LLM call. The estimate is [IngressoRiassunto] built with no names, exactly like
     * `RiassumiServizio`'s guard, so the button and the command never disagree at the limit.
     */
    private fun disponibilita(
        i: IncontroId,
        parte: RegistrazioneId,
        segmenti: List<SegmentoSintesi>,
    ): DisponibilitaVista {
        val ingresso = IngressoRiassunto.costruisci(
            segmenti.map { SegmentoIngresso(it.segmentoId, it.voceId, it.intervallo.inizioMs, it.testo) },
        )
        val esito = Riassumibilita.valuta(
            incontroId = i,
            modelloInstallato = true, // surfaced by `modello`, not `disponibilita`
            trascrittoPresente = true, // already known: [di] returned above otherwise
            elaborazioneAperta = trascritti.elaborazioneAperta(parte),
            riassuntoAperto = false, // surfaced by `richiestaAperta`, not `disponibilita`
            stimaToken = LimiteIngresso.stimaToken(ingresso),
        )
        return when (esito) {
            is Esito.Ok -> DisponibilitaVista.Disponibile
            is Esito.Errore -> when (esito.errore) {
                is ErroreSintesi.ElaborazioneGiaAperta ->
                    DisponibilitaVista.NonDisponibile(MotivoNonDisponibile.ElaborazioneAperta)
                is ErroreSintesi.RegistrazioneTroppoLunga ->
                    DisponibilitaVista.NonDisponibile(MotivoNonDisponibile.TroppoLunga)
                else -> error(
                    "Riassumibilita ha rifiutato con ${esito.errore} a modello installato e nessun Riassunto aperto",
                )
            }
        }
    }

    /** INV-S2/INV-S3 (partial unique indexes): at most one row of [righe] per state group; a second one
     * is a persistence bug, not a case this read-model interprets (ADR 0003: programmer error). */
    private fun unicoOSseNessuno(righe: List<Riassunto>, r: IncontroId, gruppo: String): Riassunto? {
        check(righe.size <= 1) { "piu' di un Riassunto $gruppo per $r: ${righe.map { it.id }}" }
        return righe.singleOrNull()
    }

    private fun statoModelloVista(stato: StatoModelloLinguistico): StatoModelloVista = when (stato) {
        is StatoModelloLinguistico.NonInstallato -> StatoModelloVista.NonInstallato(stato.dimensioneByte)
        is StatoModelloLinguistico.InDownload -> StatoModelloVista.InDownload(stato.scaricatiByte, stato.totaliByte)
        is StatoModelloLinguistico.DownloadFallito -> StatoModelloVista.DownloadFallito(stato.motivo)
        is StatoModelloLinguistico.Installato -> StatoModelloVista.Installato
    }
}

private fun richiestaApertaVista(riassunto: Riassunto): RichiestaApertaVista = if (riassunto.inAttesa) {
    RichiestaApertaVista.InAttesa(riassunto.richiestoAlle)
} else {
    val avviatoAlle = checkNotNull(riassunto.avviatoAlle) { "in_corso senza avviatoAlle: ${riassunto.id}" }
    RichiestaApertaVista.InCorso(avviatoAlle)
}

private fun fallimentoVista(riassunto: Riassunto): FallimentoVista {
    val motivo = checkNotNull(riassunto.motivoFallimento) { "fallito senza motivo: ${riassunto.id}" }
    return FallimentoVista(motivo)
}

/** AC-S108: the fallito's Argomento when it is newer (by richiestoAlle) than the pronto's, else the pronto's. */
private fun argomentoPrecompilato(pronto: Riassunto?, fallito: Riassunto?): String? = when {
    fallito != null && (pronto == null || fallito.richiestoAlle > pronto.richiestoAlle) -> fallito.argomento?.valore
    else -> pronto?.argomento?.valore
}

private fun mostratoVista(
    pronto: Riassunto,
    parte: RegistrazioneId,
    correnti: Map<SegmentoId, SegmentoSintesi>,
    nomiVoci: Map<VoceRef, String>,
    r: IncontroId,
): RiassuntoMostrato = RiassuntoMostrato(
    argomento = pronto.argomento?.valore,
    lunghezzaMassimaParole = pronto.lunghezzaMassima.valore,
    superato = pronto.superato(parte, strutturaCorrente(correnti)),
    omessi = checkNotNull(pronto.omessi) { "pronto senza omessi: ${pronto.id}" },
    sommario = pronto.sommario?.testo?.let { testoConVociVista(it, nomiVoci, r) },
    decisioni = pronto.decisioni.map { elementoVista(it.testo, it.fonti, correnti, nomiVoci, r) },
    azioni = pronto.azioni.map { azione ->
        AzioneVista(
            testo = testoConVociVista(azione.testo, nomiVoci, r),
            fonti = fontiVista(azione.fonti, correnti, nomiVoci, r),
            responsabile = azione.responsabile?.let { voceVista(it, nomiVoci, r) },
        )
    },
    questioniAperte = pronto.questioniAperte.map { elementoVista(it.testo, it.fonti, correnti, nomiVoci, r) },
    puntiChiave = pronto.puntiChiave.map { punto ->
        PuntoChiaveVista(
            testo = testoConVociVista(punto.testo, nomiVoci, r),
            fonti = fontiVista(punto.fonti, correnti, nomiVoci, r),
            parlante = punto.parlante?.let { voceVista(it, nomiVoci, r) },
        )
    },
)

/** INV-S7's own comparison, over EVERY current Segmento (an uncited one moving also supera it). */
private fun strutturaCorrente(correnti: Map<SegmentoId, SegmentoSintesi>): StrutturaTrascritto =
    StrutturaTrascritto.di(correnti.values.map { it.segmentoId to it.voceId })

private fun elementoVista(
    testo: TestoConVoci,
    fonti: Set<SegmentoId>,
    correnti: Map<SegmentoId, SegmentoSintesi>,
    nomiVoci: Map<VoceRef, String>,
    r: IncontroId,
): ElementoVista = ElementoVista(testoConVociVista(testo, nomiVoci, r), fontiVista(fonti, correnti, nomiVoci, r))

private fun testoConVociVista(
    testo: TestoConVoci,
    nomiVoci: Map<VoceRef, String>,
    r: IncontroId,
): TestoConVociVista = testo.parti.map { parte ->
    when (parte) {
        is ParteTesto.Testo -> ParteTestoVista.Testo(parte.testo)
        is ParteTesto.Voce -> ParteTestoVista.Voce(voceVista(parte.voceId, nomiVoci, r))
    }
}

/** AC-S104: sorted by the CURRENT Segmento's inizio; every stored Fonte is still in the same
 * Trascritto generation (INV-S8: a Riassunto is deleted with its generation). */
private fun fontiVista(
    fonti: Set<SegmentoId>,
    correnti: Map<SegmentoId, SegmentoSintesi>,
    nomiVoci: Map<VoceRef, String>,
    r: IncontroId,
): List<FonteVista> = fonti.map { id ->
    val segmento = checkNotNull(correnti[id]) { "Fonte $id assente dal Trascritto corrente di $r" }
    FonteVista(id.numero, voceVista(segmento.voceId, nomiVoci, r), segmento.intervallo.inizioMs)
}.sortedWith(compareBy({ it.inizioMs }, { it.segmentoId })) // a parita' di inizioMs (Set order altrimenti instabile)

private fun voceVista(voceId: VoceId, nomiVoci: Map<VoceRef, String>, r: IncontroId): VoceVista =
    VoceVista(voceId.numero, "Voce ${voceId.numero}", nomiVoci[VoceRef(r, voceId)])
