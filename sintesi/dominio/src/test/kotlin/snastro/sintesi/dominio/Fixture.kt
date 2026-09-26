package snastro.sintesi.dominio

import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.kernel.atteso
import java.time.Instant

internal val RICHIESTO_ALLE: Instant = Instant.parse("2026-09-26T10:00:00Z")
internal val AVVIATO_ALLE: Instant = Instant.parse("2026-09-26T10:01:00Z")

/** Structure {1→V1, 2→V2, 3→V1} of the tests_nl. */
internal fun unaStruttura(vararg coppie: Pair<Int, Int> = arrayOf(1 to 1, 2 to 2, 3 to 1)): StrutturaTrascritto =
    StrutturaTrascritto.di(coppie.map { (s, v) -> SegmentoId(s) to VoceId(v) })

internal fun unRiassunto(parole: Int = LunghezzaMassimaParole.PREDEFINITA): Riassunto = Riassunto.richiedi(
    RiassuntoId("id-1"),
    RegistrazioneId("id-2"),
    argomento = null,
    lunghezzaMassima = LunghezzaMassimaParole.di(parole).atteso(),
    richiestoAlle = RICHIESTO_ALLE,
).aggregato

internal fun unRiassuntoInCorso(parole: Int = LunghezzaMassimaParole.PREDEFINITA): Riassunto =
    unRiassunto(parole).also { it.avvia(AVVIATO_ALLE).atteso() }

internal fun unaBozza(
    sommario: String? = null,
    decisioni: List<BozzaElemento> = emptyList(),
    questioniAperte: List<BozzaElemento> = emptyList(),
    azioni: List<BozzaElemento> = emptyList(),
    puntiChiave: List<BozzaElemento> = emptyList(),
): BozzaRiassunto = BozzaRiassunto(sommario, decisioni, questioniAperte, azioni, puntiChiave)

internal fun unElemento(testo: String = "si fa", vararg fonti: Int = intArrayOf(1), voce: Int? = null): BozzaElemento =
    BozzaElemento(testo, fonti.toList(), voce)

internal fun testo(s: String): TestoConVoci = TestoConVoci.decodifica(s).atteso()

/** Every observable field of a Riassunto, to prove "nothing changed". */
internal data class Istantanea(
    val stato: StatoRiassunto,
    val avviatoAlle: Instant?,
    val motivo: MotivoFallimento?,
    val sommario: Sommario?,
    val elementi: List<Any>,
    val omessi: Int?,
    val struttura: String?,
    val lunghezza: LunghezzaMassimaParole,
) {
    constructor(r: Riassunto) : this(
        stato = r.stato,
        avviatoAlle = r.avviatoAlle,
        motivo = r.motivoFallimento,
        sommario = r.sommario,
        elementi = r.decisioni + r.questioniAperte + r.azioni + r.puntiChiave,
        omessi = r.omessi,
        struttura = r.struttura,
        lunghezza = r.lunghezzaMassima,
    )
}
