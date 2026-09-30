package snastro.avvio.progetto

import snastro.kernel.Esito
import snastro.kernel.poi
import snastro.progetto.applicazione.porte.EliminazioneInSospeso
import snastro.progetto.applicazione.porte.PuliziaDerivatiRegistrazione
import snastro.sbobinatura.applicazione.politiche.RigenerazioneSbobinaturaPolitica
import java.io.IOException
import java.nio.file.Path

/**
 * [PuliziaDerivatiRegistrazione] over the project folder [cartella] (boundary `tec-pulizia-derivati`, ADR 0020 §4,
 * AC-633): deletes `cache/audio/<id>.wav`, then the Sbobinatura `.md` named from the pending row's values, through
 * [sbobinatura]'s `perRegistrazioneEliminata(id, data, titolo, ∅)`. Idempotent (absent files are Ok). A failure is
 * [Esito.Errore], so `CompletaEliminazioniRegistrazioni` keeps the row for the next project open.
 */
internal class PuliziaDerivatiFile(
    private val cartella: Path,
    private val sbobinatura: RigenerazioneSbobinaturaPolitica,
) : PuliziaDerivatiRegistrazione {
    override fun pulisci(e: EliminazioneInSospeso): Esito<Unit> = cancellaWav(e).poi {
        sbobinatura.perRegistrazioneEliminata(e.registrazioneId, e.dataRegistrazione, e.titolo, emptySet())
    }

    private fun cancellaWav(e: EliminazioneInSospeso): Esito<Unit> = try {
        cancellaWavDerivato(cartella, e.registrazioneId)
        Esito.Ok(Unit)
    } catch (ex: IOException) {
        val percorso = "cache/audio/${e.registrazioneId.valore}.wav"
        Esito.Errore(ErroreApplicazioneAvvio.DerivatoNonRimosso(percorso, ex.message.orEmpty()))
    }
}
