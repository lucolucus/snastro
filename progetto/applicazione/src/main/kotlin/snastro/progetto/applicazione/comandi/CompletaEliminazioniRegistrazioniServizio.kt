package snastro.progetto.applicazione.comandi

import snastro.kernel.Esito
import snastro.kernel.UnitaDiLavoro
import snastro.kernel.ignoraEsito
import snastro.kernel.poi
import snastro.progetto.applicazione.porte.ArchivioAudio
import snastro.progetto.applicazione.porte.EliminazioniInSospeso
import snastro.progetto.applicazione.porte.PuliziaDerivatiRegistrazione

/**
 * Use-case `CompletaEliminazioniRegistrazioni` (ADR 0020 §4): for every pending row, in order, discards the audio
 * copy, then the derived files; ONLY when [PuliziaDerivatiRegistrazione] is Ok is the row concluded, in its own short
 * transaction. The file calls run outside any transaction (ADR 0012). A failed cleanup leaves its row for the next
 * open and never blocks this one: the result is always Ok. Every step is idempotent, so a re-run is safe.
 */
public class CompletaEliminazioniRegistrazioniServizio(
    private val uow: UnitaDiLavoro,
    private val inSospeso: EliminazioniInSospeso,
    private val archivio: ArchivioAudio,
    private val derivati: PuliziaDerivatiRegistrazione,
) {
    public fun esegui(ignored: CompletaEliminazioniRegistrazioni): Esito<Unit> {
        for (e in inSospeso.elenco()) {
            archivio.scarta(e.riferimentoAudio)
            derivati.pulisci(e).poi {
                uow.inTransazione {
                    inSospeso.concludi(e.registrazioneId)
                    Esito.Ok(Unit)
                }
            }.ignoraEsito("un Errore lascia la riga in sospeso: riprovata alla prossima apertura (ADR 0020 §4)")
        }
        return Esito.Ok(Unit)
    }
}
