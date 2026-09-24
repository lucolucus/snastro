package snastro.trascrizione.adattatori.ml

import snastro.kernel.CampioniAudio
import snastro.ml.RiconoscitoreSherpa
import snastro.trascrizione.applicazione.porte.Riconoscimento
import snastro.trascrizione.applicazione.porte.RiconoscitoreParlato
import snastro.trascrizione.applicazione.porte.Token

/**
 * [RiconoscitoreParlato] reale (boundary `tec-riconoscitore`, ADR 0004/0013/0015/0016): a thin
 * translation over [RiconoscitoreSherpa] (sherpa-onnx `OfflineRecognizer`, confined to `:ml-sherpa`,
 * CR-3) — no chunking/merging rule here, that is [AllineatorePerTurno]'s job (this adapter is one of
 * the two ports it is built with).
 *
 * **Lifecycle (ADR 0015 Consequences, AC-388).** [RiconoscitoreSherpa] loads the model lazily on the
 * first [riconosci] and reuses it for every later call — `N` calls cost exactly one load. [close]
 * forwards to [RiconoscitoreSherpa.chiudi] (AC-254) so a composition root can scope one instance to
 * one Elaborazione (`riconoscitoreParlatoSherpa.use { … }`, or close it once the Elaborazione ends):
 * this block does not itself wire that scope — `esegui-elaborazione`/`PortePipeline` hold `Allineatore`
 * (and therefore this adapter) for the app's life with no per-Elaborazione open/close signal today, so
 * the actual scoping is for the composition-root block that wires the real pipeline to decide.
 */
public class RiconoscitoreParlatoSherpa(private val motore: RiconoscitoreSherpa) : RiconoscitoreParlato, AutoCloseable {
    override fun riconosci(c: CampioniAudio): Riconoscimento {
        val r = motore.riconosci(c)
        return Riconoscimento(r.testo, r.token?.map { Token(it.testo, it.intervallo) })
    }

    /** AC-254/388: releases the underlying model; a later [riconosci] reloads it. */
    override fun close() {
        motore.chiudi()
    }
}
