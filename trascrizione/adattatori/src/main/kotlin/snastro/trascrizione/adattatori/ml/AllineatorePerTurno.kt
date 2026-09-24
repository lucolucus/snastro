package snastro.trascrizione.adattatori.ml

import snastro.kernel.CampioniAudio
import snastro.kernel.IntervalloMs
import snastro.trascrizione.applicazione.porte.Allineatore
import snastro.trascrizione.applicazione.porte.RiconoscitoreParlato
import snastro.trascrizione.applicazione.porte.SegmentoGrezzo
import snastro.trascrizione.applicazione.porte.Turno
import snastro.trascrizione.applicazione.porte.Vad

/**
 * [Allineatore] — strategia A, trascrizione per turno unito (ADR 0015, chiude lo spike
 * `allineamento-parole-voci`). Puro Kotlin, costruito su [riconoscitore] e [vad] (ADR 0004):
 *
 * 1. I [Turno] sono ordinati per (`inizio`, `voceIndice`, `fine`) e i turni consecutivi della
 *    STESSA voce a meno di [GAP_UNIONE_TURNI_MS] l'uno dall'altro sono uniti (intervallo unito =
 *    inizio del primo, `max` delle fini); una voce diversa in mezzo impedisce l'unione, e turni di
 *    voci diverse non sono MAI uniti, tagliati o eliminati per una sovrapposizione (INV-7).
 * 2. Un turno unito più corto di [DURATA_MINIMA_TURNO_MS] non produce chiamata ASR né Segmento, e
 *    non è unito a un vicino (il filtro avviene DOPO l'unione, che quindi resta invariata).
 * 3. Un turno unito lungo al più [DURATA_MASSIMA_CHIAMATA_MS] è riconosciuto intero, senza [vad];
 *    un turno più lungo è passato a `vad.parlato` sui suoi soli campioni, e solo gli intervalli
 *    restituiti sono riconosciuti; un intervallo di parlato più lungo di
 *    [DURATA_MASSIMA_CHIAMATA_MS] è tagliato in pezzi consecutivi uguali, ciascuno al più di quella
 *    durata: nessuna chiamata al riconoscitore riceve mai più di [DURATA_MASSIMA_CHIAMATA_MS] di
 *    audio, qualunque cosa restituisca il [vad].
 * 4. Un pezzo (turno intero o intervallo di parlato) più corto di [DURATA_MINIMA_CHIAMATA_MS] non
 *    produce alcuna chiamata al riconoscitore.
 * 5. I campioni passati al riconoscitore sono tagliati esattamente ai limiti del turno o del pezzo,
 *    senza alcun padding.
 * 6. Il testo del turno unito è la concatenazione, con un solo spazio e in ordine di tempo, dei
 *    testi (rifilati) dei pezzi non vuoti; `Riconoscimento.token` è ignorato.
 * 7. Un turno il cui testo unito è vuoto o di soli spazi non produce alcun [SegmentoGrezzo].
 * 8. Il [SegmentoGrezzo] di un turno unito porta il suo `voceIndice` e l'intervallo del TURNO UNITO,
 *    anche quando è stato spezzato dal [vad] per il riconoscimento.
 * 9. Ogni turno sovrapposto è riconosciuto per conto proprio sugli stessi campioni misti: le parole
 *    dell'sovrapposizione possono comparire in più Segmenti, e sono tutte conservate.
 * 10. L'uscita è ordinata per (`inizio`, `voceIndice`, `fine`) ed è deterministica; l'Allineatore non
 *     numera Voci né Segmenti (compito di `Trascritto.crea`).
 *
 * **Arresto cooperativo (fix-batch-16 MED-1).** Prima di ogni turno e di ogni chiamata al
 * riconoscitore controlla il flag di interruzione del thread: se impostato (il worker della pipeline è
 * stato cancellato, es. chiudendo il progetto) lancia [InterruptedException] — che la pipeline
 * rilancia, mai una `fallita` — invece di proseguire con la chiamata nativa successiva. Una pipeline
 * orfana si ferma così entro un pezzo (al più [DURATA_MASSIMA_CHIAMATA_MS] di audio). La singola
 * chiamata nativa in corso, e la diarizzazione intera, non sono interrompibili.
 */
public class AllineatorePerTurno(
    private val riconoscitore: RiconoscitoreParlato,
    private val vad: Vad,
) : Allineatore {

    override fun allinea(campioni: CampioniAudio, turni: List<Turno>): List<SegmentoGrezzo> =
        unisciTurniVicini(turni)
            .filter { it.intervallo.durataMs >= DURATA_MINIMA_TURNO_MS } // regola 2
            .mapNotNull { turno -> segmentoDi(campioni, turno) } // regole 3-8
            .sortedWith(ORDINE_SEGMENTI) // regola 10

    /** Regola 1: unisce ogni turno al turno unito immediatamente precedente, stessa voce,
     * gap < [GAP_UNIONE_TURNI_MS]. */
    private fun unisciTurniVicini(turni: List<Turno>): List<Turno> {
        val uniti = mutableListOf<Turno>()
        for (turno in turni.sortedWith(ORDINE_TURNI)) {
            val precedente = uniti.lastOrNull()
            val siUnisce = precedente != null &&
                precedente.voceIndice == turno.voceIndice &&
                turno.intervallo.inizioMs - precedente.intervallo.fineMs < GAP_UNIONE_TURNI_MS
            // il secondo controllo su precedente serve solo allo smart-cast di Kotlin
            if (precedente != null && siUnisce) {
                uniti[uniti.lastIndex] = precedente.estesoFinoA(turno.intervallo.fineMs)
            } else {
                uniti += turno
            }
        }
        return uniti
    }

    private fun Turno.estesoFinoA(fineMs: Long): Turno =
        Turno(IntervalloMs(intervallo.inizioMs, maxOf(intervallo.fineMs, fineMs)), voceIndice)

    /** Un [SegmentoGrezzo] per turno unito (regola 8), o `null` se il suo testo risulta vuoto (regola 7). */
    private fun segmentoDi(campioni: CampioniAudio, turno: Turno): SegmentoGrezzo? {
        fermatiSeInterrotto()
        val testo = pezziDi(campioni, turno)
            .filter { it.durataMs >= DURATA_MINIMA_CHIAMATA_MS } // regola 4
            .map { pezzo ->
                fermatiSeInterrotto()
                riconoscitore.riconosci(taglio(campioni, pezzo)).testo.trim() // regole 5, 6 (token ignorato)
            }
            .filter { it.isNotBlank() }
            .joinToString(" ") // regola 6: un solo spazio, ordine di tempo
        return testo.takeIf { it.isNotBlank() }?.let { SegmentoGrezzo(turno.voceIndice, turno.intervallo, it) }
    }

    /** Arresto cooperativo: la chiamata nativa successiva non parte se il worker e' stato interrotto. */
    private fun fermatiSeInterrotto() {
        if (Thread.currentThread().isInterrupted) throw InterruptedException("allineamento interrotto")
    }

    /**
     * I pezzi (assoluti, in ordine di tempo) da riconoscere per un turno unito: se stesso quando
     * la sua durata è al più [DURATA_MASSIMA_CHIAMATA_MS] (regola 3, senza [vad]), altrimenti gli
     * intervalli di [vad] su quel turno, ciascuno spezzato a quella durata (regola 3).
     */
    private fun pezziDi(campioni: CampioniAudio, turno: Turno): List<IntervalloMs> =
        if (turno.intervallo.durataMs <= DURATA_MASSIMA_CHIAMATA_MS) {
            listOf(turno.intervallo)
        } else {
            vad.parlato(taglio(campioni, turno.intervallo))
                .map { it.traslato(turno.intervallo.inizioMs) }
                .flatMap { spezzato(it) }
        }

    /** Taglia un intervallo di parlato più lungo di [DURATA_MASSIMA_CHIAMATA_MS] in pezzi consecutivi uguali. */
    private fun spezzato(intervallo: IntervalloMs): List<IntervalloMs> {
        if (intervallo.durataMs <= DURATA_MASSIMA_CHIAMATA_MS) return listOf(intervallo)
        val pezzi = ((intervallo.durataMs + DURATA_MASSIMA_CHIAMATA_MS - 1) / DURATA_MASSIMA_CHIAMATA_MS).toInt()
        return (0 until pezzi).map { i ->
            IntervalloMs(
                intervallo.inizioMs + intervallo.durataMs * i / pezzi,
                intervallo.inizioMs + intervallo.durataMs * (i + 1) / pezzi,
            )
        }
    }

    /** Regola 5: esattamente i campioni tra `[inizioMs, fineMs)` di [intervallo], senza padding. */
    private fun taglio(campioni: CampioniAudio, intervallo: IntervalloMs): CampioniAudio {
        val da = (intervallo.inizioMs * CAMPIONI_PER_MS).toInt().coerceIn(0, campioni.campioni.size)
        val a = (intervallo.fineMs * CAMPIONI_PER_MS).toInt().coerceIn(da, campioni.campioni.size)
        return CampioniAudio(campioni.campioni.copyOfRange(da, a))
    }

    private val IntervalloMs.durataMs: Long get() = fineMs - inizioMs

    private fun IntervalloMs.traslato(offsetMs: Long): IntervalloMs =
        IntervalloMs(inizioMs + offsetMs, fineMs + offsetMs)

    private companion object {
        /** Regola 1: due turni della stessa voce più vicini di questo si uniscono (ADR 0015). */
        const val GAP_UNIONE_TURNI_MS = 1_000L

        /** Regola 2: un turno unito più corto di questo non produce ASR né Segmento ([user], ADR 0015). */
        const val DURATA_MINIMA_TURNO_MS = 500L

        /** Regola 3: nessuna chiamata al riconoscitore riceve più di questa durata di audio (ADR 0015). */
        const val DURATA_MASSIMA_CHIAMATA_MS = 25_000L

        /** Regola 4: un pezzo più corto di questo non produce chiamata ASR (ADR 0015). */
        const val DURATA_MINIMA_CHIAMATA_MS = 200L

        /** 16 kHz mono (ADR 0005): campioni per millisecondo. */
        const val CAMPIONI_PER_MS = 16

        /** Regole 1 e 10: (inizio, voceIndice, fine). */
        val ORDINE_TURNI: Comparator<Turno> =
            compareBy<Turno> { it.intervallo.inizioMs }
                .thenBy { it.voceIndice }
                .thenBy { it.intervallo.fineMs }
        val ORDINE_SEGMENTI: Comparator<SegmentoGrezzo> =
            compareBy<SegmentoGrezzo> { it.intervallo.inizioMs }
                .thenBy { it.voceIndice }
                .thenBy { it.intervallo.fineMs }
    }
}
