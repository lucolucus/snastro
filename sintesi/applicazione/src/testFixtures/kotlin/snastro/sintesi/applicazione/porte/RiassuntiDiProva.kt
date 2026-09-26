package snastro.sintesi.applicazione.porte

import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.kernel.atteso
import snastro.sintesi.dominio.Argomento
import snastro.sintesi.dominio.BozzaRiassunto
import snastro.sintesi.dominio.ConclusioneRiassunto
import snastro.sintesi.dominio.LunghezzaMassimaParole
import snastro.sintesi.dominio.MotivoFallimento
import snastro.sintesi.dominio.Riassunto
import snastro.sintesi.dominio.RiassuntoId
import snastro.sintesi.dominio.StrutturaTrascritto
import java.time.Instant
import kotlin.test.assertIs

// Riassunto builders for the Sintesi tests: always through the root's own transitions, never `ricostituisci`
// (reserved to the persistence adapters, CR-15).

/** An `in_attesa` Riassunto, as `Riassunto.richiedi` makes it. */
public fun unRiassunto(
    id: String,
    registrazioneId: RegistrazioneId,
    argomento: String? = null,
    parole: Int = LunghezzaMassimaParole.PREDEFINITA,
    richiestoAlle: Instant = Instant.parse("2026-09-26T10:00:00.123Z"),
): Riassunto = Riassunto.richiedi(
    RiassuntoId(id),
    registrazioneId,
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

/** This `in_corso` Riassunto completed `pronto` from [bozza] verified against [struttura] (never `fallito`). */
public fun Riassunto.conCompletamento(bozza: BozzaRiassunto, struttura: StrutturaTrascritto): Riassunto =
    also { assertIs<ConclusioneRiassunto.Pronto>(completa(bozza, struttura).atteso()) }

/** A structure from `segmentoId to voceId` numbers. */
public fun unaStruttura(vararg coppie: Pair<Int, Int>): StrutturaTrascritto =
    StrutturaTrascritto.di(coppie.map { (s, v) -> SegmentoId(s) to VoceId(v) })

/** Every observable field of a Riassunto (the root has no value equality), to compare stored and expected. */
public fun Riassunto.statoOsservabile(): List<Any?> = listOf(
    id, registrazioneId, argomento, lunghezzaMassima, richiestoAlle, stato, avviatoAlle, motivoFallimento,
    sommario, decisioni.toList(), questioniAperte.toList(), azioni.toList(), puntiChiave.toList(), omessi, struttura,
)
