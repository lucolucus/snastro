package snastro.sintesi.applicazione.letture

import snastro.kernel.ErroreDominio
import snastro.kernel.Esito
import snastro.kernel.IncontroId
import snastro.kernel.LetturaCoerente
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoRef
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.poi
import snastro.sintesi.applicazione.porte.DisponibilitaModelloLinguistico
import snastro.sintesi.applicazione.porte.LettoreIncontro
import snastro.sintesi.applicazione.porte.LettoreNomi
import snastro.sintesi.applicazione.porte.LettoreTrascritto
import snastro.sintesi.applicazione.porte.ParteSintesi
import snastro.sintesi.applicazione.porte.RiassuntoRepository
import snastro.sintesi.applicazione.porte.SegmentoSintesi
import snastro.sintesi.applicazione.porte.StatoModelloLinguistico
import snastro.sintesi.applicazione.porte.inIngresso
import snastro.sintesi.dominio.ErroreSintesi
import snastro.sintesi.dominio.IngressoRiassunto
import snastro.sintesi.dominio.LimiteIngresso
import snastro.sintesi.dominio.ParteTesto
import snastro.sintesi.dominio.Riassumibilita
import snastro.sintesi.dominio.Riassunto
import snastro.sintesi.dominio.StrutturaIncontro
import snastro.sintesi.dominio.StrutturaTrascritto
import snastro.sintesi.dominio.TestoConVoci

/**
 * Read-model `vista-riassunto` (AC-S102..S108, ADR 0021 §3, ADR 0023 §4, ADR 0029 §5/AC-C32, ADR 0037 §6): builds
 * [RiassuntoVista] for one Incontro, over ALL its Parti in INV-I2 order, with no side effect, never calling
 * [snastro.sintesi.applicazione.porte.ModelloLinguistico]. `null` when the Incontro is unknown (or read with no
 * Parte), or has one Parte
 * with no Trascritto (the tab is not offered, INV-I3). Names for display are joined from [nomi] at read time
 * (INV-S5), for Voci still present only (INV-I13): a
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
    /** The Riassunto view of the Incontro [i] (ADR 0037 §1, §6), read through all its Parti. */
    public fun di(i: IncontroId): RiassuntoVista? = lettura.inLettura {
        val parti = incontri.parti(i)?.takeIf { it.isNotEmpty() } ?: return@inLettura null
        val segmentiDi = parti.associate { it.registrazioneId to trascritti.segmenti(it.registrazioneId) }
        // INV-I3: a one-Parte Incontro with no Trascritto has no tab, as before (never "Parte 1 non riuscita")
        if (parti.size == 1 && segmentiDi.getValue(parti.single().registrazioneId) == null) return@inLettura null
        val corrente = Corrente(parti, segmentiDi)
        val righe = riassunti.trova(i)
        val pronto = unicoOSseNessuno(righe.filter { it.pronto }, i, "pronto")
        val nonPronto = unicoOSseNessuno(righe.filterNot { it.pronto }, i, "non pronto")

        RiassuntoVista(
            incontroId = i,
            numParti = parti.size,
            modello = statoModelloVista(disponibilitaModello.stato()),
            richiestaAperta = nonPronto?.takeIf { it.aperto }?.let(::richiestaApertaVista),
            ultimoFallimento = nonPronto?.takeIf { it.fallito }?.let(::fallimentoVista),
            disponibilita = disponibilita(corrente),
            argomentoPrecompilato = argomentoPrecompilato(pronto, nonPronto?.takeIf { it.fallito }),
            mostrato = pronto?.let { mostratoVista(it, corrente, nomi.nomi(i), i) },
        )
    }

    /**
     * AC-S107, AC-I50: [Riassumibilita] restricted to the reasons this read-model decides on its own — no write, no
     * LLM call. The estimate is [IngressoRiassunto] built with no names over every Parte in order, exactly like
     * `RiassumiServizio`'s guard, so the button and the command never disagree at the limit. The blocking Parte is
     * the first one in Parte order.
     */
    private fun disponibilita(corrente: Corrente): DisponibilitaVista {
        val stati = corrente.parti.map { it.numero to trascritti.statoParte(it.registrazioneId) }
        // `modello` and `richiestaAperta` surface the other two reasons; the input is built only when no Parte blocks
        val esito = Riassumibilita.valuta(modelloInstallato = true, stati, riassuntoAperto = false, stimaToken = null)
            .poi { Riassumibilita.valuta(modelloInstallato = true, stati, riassuntoAperto = false, stimaDi(corrente)) }
        return when (esito) {
            is Esito.Ok -> DisponibilitaVista.Disponibile
            is Esito.Errore -> DisponibilitaVista.NonDisponibile(motivoDi(esito.errore))
        }
    }

    private fun stimaDi(corrente: Corrente): Int = LimiteIngresso.stimaToken(
        IngressoRiassunto.costruisci(
            corrente.parti.map { p -> corrente.segmentiDi(p).map { it.inIngresso(p.registrazioneId) } },
        ).testo,
    )

    /** Only the four reasons reachable with `modelloInstallato = true` and `riassuntoAperto = false`. */
    private fun motivoDi(errore: ErroreDominio): MotivoNonDisponibile = when (errore) {
        is ErroreSintesi.PartiNonTrascritte -> MotivoNonDisponibile.PartiNonTrascritte(errore.parte)
        is ErroreSintesi.ElaborazioneGiaAperta -> MotivoNonDisponibile.ElaborazioneAperta(errore.parte)
        is ErroreSintesi.PartiFallite -> MotivoNonDisponibile.PartiFallite(errore.parte)
        is ErroreSintesi.IngressoTroppoLungo -> MotivoNonDisponibile.TroppoLunga
        else -> error("Riassumibilita ha rifiutato con $errore a modello installato e nessun Riassunto aperto")
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

/**
 * The Incontro as read in this snapshot: its [parti] in INV-I2 order and, per Parte, its current Segmenti (`null`: no
 * Trascritto). [voci] are the Voci of the CURRENT structure (INV-I13: presence), [parteDi] numbers a Parte still in
 * the Incontro.
 */
private class Corrente(
    val parti: List<ParteSintesi>,
    private val segmenti: Map<RegistrazioneId, List<SegmentoSintesi>?>,
) {
    val voci: Set<VoceId> = segmenti.values.flatMap { it.orEmpty() }.map { it.voceId }.toSet()

    fun segmentiDi(p: ParteSintesi): List<SegmentoSintesi> = segmenti[p.registrazioneId].orEmpty()

    fun parteDi(r: RegistrazioneId): ParteSintesi? = parti.firstOrNull { it.registrazioneId == r }

    private val perRiferimento: Map<SegmentoRef, SegmentoSintesi> = segmenti.entries
        .flatMap { (r, s) -> s.orEmpty().map { SegmentoRef(r, it.segmentoId) to it } }
        .toMap()

    fun segmento(ref: SegmentoRef): SegmentoSintesi? = perRiferimento[ref]

    /** The structure the root's INV-I11 predicate compares: EVERY current Segmento of every Parte (an uncited one
     * moving also supera it), a Parte without Trascritto as `null`. */
    val struttura: StrutturaIncontro = StrutturaIncontro(
        parti.map { p -> p.registrazioneId to segmenti[p.registrazioneId]?.let(::strutturaCorrente) },
    )
}

private fun strutturaCorrente(segmenti: List<SegmentoSintesi>): StrutturaTrascritto =
    StrutturaTrascritto.di(segmenti.map { it.segmentoId to it.voceId })

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
    c: Corrente,
    nomiVoci: Map<VoceRef, String>,
    i: IncontroId,
): RiassuntoMostrato = RiassuntoMostrato(
    argomento = pronto.argomento?.valore,
    lunghezzaMassimaParole = pronto.lunghezzaMassima.valore,
    superato = pronto.superato(c.struttura),
    omessi = checkNotNull(pronto.omessi) { "pronto senza omessi: ${pronto.id}" },
    sommario = pronto.sommario?.testo?.let { testoConVociVista(it, c, nomiVoci, i) },
    decisioni = pronto.decisioni.map { elementoVista(it.testo, it.fonti, c, nomiVoci, i) },
    azioni = pronto.azioni.map { azione ->
        AzioneVista(
            testo = testoConVociVista(azione.testo, c, nomiVoci, i),
            fonti = fontiVista(azione.fonti, c, nomiVoci, i),
            responsabile = azione.responsabile?.let { voceVista(it, c, nomiVoci, i) },
        )
    },
    questioniAperte = pronto.questioniAperte.map { elementoVista(it.testo, it.fonti, c, nomiVoci, i) },
    puntiChiave = pronto.puntiChiave.map { punto ->
        PuntoChiaveVista(
            testo = testoConVociVista(punto.testo, c, nomiVoci, i),
            fonti = fontiVista(punto.fonti, c, nomiVoci, i),
            parlante = punto.parlante?.let { voceVista(it, c, nomiVoci, i) },
        )
    },
)

private fun elementoVista(
    testo: TestoConVoci,
    fonti: Set<SegmentoRef>,
    c: Corrente,
    nomiVoci: Map<VoceRef, String>,
    i: IncontroId,
): ElementoVista = ElementoVista(testoConVociVista(testo, c, nomiVoci, i), fontiVista(fonti, c, nomiVoci, i))

private fun testoConVociVista(
    testo: TestoConVoci,
    c: Corrente,
    nomiVoci: Map<VoceRef, String>,
    i: IncontroId,
): TestoConVociVista = testo.parti.map { parte ->
    when (parte) {
        is ParteTesto.Testo -> ParteTestoVista.Testo(parte.testo)
        is ParteTesto.Voce -> ParteTestoVista.Voce(voceVista(parte.voceId, c, nomiVoci, i))
    }
}

/**
 * AC-S104, INV-I13: every stored Fonte is shown, never thrown at. A Fonte whose Segmento is gone (its Parte was
 * re-transcribed: segment ids are never reused, INV-I16) has no minute and no Voce; one whose Parte left the Incontro
 * has no number. Sorted by Parte number (a missing one last), then the CURRENT start (a missing one last).
 */
private fun fontiVista(
    fonti: Set<SegmentoRef>,
    c: Corrente,
    nomiVoci: Map<VoceRef, String>,
    i: IncontroId,
): List<FonteVista> = fonti.map { ref ->
    val segmento = c.segmento(ref)
    FonteVista(
        registrazioneId = ref.registrazioneId,
        numeroParte = c.parteDi(ref.registrazioneId)?.numero,
        segmentoId = ref.segmentoId.numero,
        voce = segmento?.let { voceVista(it.voceId, c, nomiVoci, i) },
        inizioMs = segmento?.intervallo?.inizioMs,
        segmentoPresente = segmento != null,
    )
}.sortedWith(
    compareBy<FonteVista, Int?>(nullsLast()) { it.numeroParte }
        .thenBy(nullsLast()) { it.inizioMs }
        .thenBy { it.registrazioneId.valore }
        .thenBy { it.segmentoId },
) // a parita' di tutto il resto (Set order altrimenti instabile)

/** INV-I13: a Voce outside the current structure is [VoceVista.presente] `false` and never carries a Nome. */
private fun voceVista(voceId: VoceId, c: Corrente, nomiVoci: Map<VoceRef, String>, i: IncontroId): VoceVista {
    val presente = voceId in c.voci
    return VoceVista(
        voceId.numero,
        "Voce ${voceId.numero}",
        nomiVoci[VoceRef(i, voceId)].takeIf { presente },
        presente,
    )
}
