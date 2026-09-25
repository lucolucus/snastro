package snastro.progetto.applicazione.porte

import snastro.kernel.Esito

/**
 * Port (boundary `tec-pulizia-derivati`, ADR 0020 §4): removes every DERIVED file of a deleted Registrazione — the
 * decoded `cache/audio/<id>.wav` and the Documento `.md` named from [EliminazioneInSospeso]'s titolo and date.
 * Idempotent: absent files are Ok. An [Esito.Errore] leaves the pending row for the next project open.
 * Implemented in `:avvio`.
 */
public interface PuliziaDerivatiRegistrazione {
    public fun pulisci(e: EliminazioneInSospeso): Esito<Unit>
}
