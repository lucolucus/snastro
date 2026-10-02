package snastro.sintesi.applicazione.porte

import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.SegmentoRef
import snastro.kernel.VoceId
import snastro.kernel.atteso
import snastro.kernel.unIncontroDi
import snastro.kernel.unicaParteDi
import snastro.sintesi.dominio.Argomento
import snastro.sintesi.dominio.BozzaRiassunto
import snastro.sintesi.dominio.ConclusioneRiassunto
import snastro.sintesi.dominio.LunghezzaMassimaParole
import snastro.sintesi.dominio.MotivoFallimento
import snastro.sintesi.dominio.Riassunto
import snastro.sintesi.dominio.RiassuntoId
import snastro.sintesi.dominio.StrutturaIncontro
import snastro.sintesi.dominio.StrutturaTrascritto
import java.time.Instant
import kotlin.test.assertIs

// Riassunto builders for the Sintesi tests: always through the root's own transitions, never `ricostituisci`
// (reserved to the persistence adapters, CR-15).

/**
 * An `in_attesa` Riassunto of the Incontro of the one-Parte [registrazioneId] ([unIncontroDi]), as `Riassunto.richiedi`
 * makes it.
 */
public fun unRiassunto(
    id: String,
    registrazioneId: RegistrazioneId,
    argomento: String? = null,
    parole: Int = LunghezzaMassimaParole.PREDEFINITA,
    richiestoAlle: Instant = Instant.parse("2026-09-26T10:00:00.123Z"),
): Riassunto = unRiassunto(id, unIncontroDi(registrazioneId), argomento, parole, richiestoAlle)

/** An `in_attesa` Riassunto of the Incontro [incontroId] (e.g. one minted by a real import), as `Riassunto.richiedi`
 * makes it. */
public fun unRiassunto(
    id: String,
    incontroId: IncontroId,
    argomento: String? = null,
    parole: Int = LunghezzaMassimaParole.PREDEFINITA,
    richiestoAlle: Instant = Instant.parse("2026-09-26T10:00:00.123Z"),
): Riassunto = Riassunto.richiedi(
    RiassuntoId(id),
    incontroId,
    Argomento.di(argomento).atteso(),
    LunghezzaMassimaParole.di(parole).atteso(),
    richiestoAlle,
).aggregato

/** This Riassunto moved to `in_corso` at [alle]. */
public fun Riassunto.conAvvio(alle: Instant = Instant.parse("2026-09-26T10:01:00.456Z")): Riassunto =
    also { avvia(alle).atteso() }

/** This `in_corso` Riassunto moved to `fallito` with [motivo]. */
public fun Riassunto.conFallimento(motivo: MotivoFallimento = MotivoFallimento.ERRORE_MODELLO): Riassunto =
    also { fallisci(motivo).atteso() }

/**
 * This `in_corso` Riassunto completed `pronto` from [bozza] verified against [struttura] of the Parte [parte] (by
 * default its Incontro's one Parte under the [unicaParteDi] convention) (never `fallito`). The bozza's `fonti` are
 * segmentoId numbers of [parte]: the label table is the identity (label k = Segmento k of [parte]).
 */
public fun Riassunto.conCompletamento(
    bozza: BozzaRiassunto,
    struttura: StrutturaTrascritto,
    parte: RegistrazioneId = unicaParteDi(incontroId),
): Riassunto = conCompletamento(bozza, StrutturaIncontro(listOf(parte to struttura)), etichetteIdentita(bozza, parte))

/** This `in_corso` Riassunto completed `pronto` from [bozza] against [struttura] through [etichette]. */
public fun Riassunto.conCompletamento(
    bozza: BozzaRiassunto,
    struttura: StrutturaIncontro,
    etichette: List<SegmentoRef>,
): Riassunto = also { assertIs<ConclusioneRiassunto.Pronto>(completa(bozza, struttura, etichette).atteso()) }

/** Label k = Segmento k of [parte], for k up to the highest label [bozza] cites. */
public fun etichetteIdentita(bozza: BozzaRiassunto, parte: RegistrazioneId): List<SegmentoRef> {
    val elementi = bozza.decisioni + bozza.questioniAperte + bozza.azioni + bozza.puntiChiave
    val massima = elementi.flatMap { it.fonti }.maxOrNull() ?: 0
    return (1..massima).map { SegmentoRef(parte, SegmentoId(it)) }
}

/** A structure from `segmentoId to voceId` numbers. */
public fun unaStruttura(vararg coppie: Pair<Int, Int>): StrutturaTrascritto =
    StrutturaTrascritto.di(coppie.map { (s, v) -> SegmentoId(s) to VoceId(v) })

/** Every observable field of a Riassunto (the root has no value equality), to compare stored and expected. */
public fun Riassunto.statoOsservabile(): List<Any?> = listOf(
    id, incontroId, argomento, lunghezzaMassima, richiestoAlle, stato, avviatoAlle, motivoFallimento,
    sommario, decisioni.toList(), questioniAperte.toList(), azioni.toList(), puntiChiave.toList(), omessi,
    strutturaRegistrata,
)
