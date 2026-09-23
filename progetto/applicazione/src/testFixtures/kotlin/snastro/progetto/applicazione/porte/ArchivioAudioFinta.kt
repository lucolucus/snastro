package snastro.progetto.applicazione.porte

import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import java.util.Locale

/**
 * In-memory [ArchivioAudio]: the copy of a source for which [copiaFallisce] is true fails with
 * CopiaFallita and archives nothing. Not [snastro.kernel.Ripristinabile]: like real files, a copy is
 * not undone by a rollback — only [scarta] removes it.
 */
public class ArchivioAudioFinta(private val copiaFallisce: (String) -> Boolean = { false }) : ArchivioAudio {
    private val copiati = linkedSetOf<RiferimentoAudio>()

    /** The references currently in `audio/`. */
    public val archiviati: Set<RiferimentoAudio> get() = copiati.toSet()

    override fun copia(percorsoSorgente: String, id: RegistrazioneId): Esito<RiferimentoAudio> {
        if (copiaFallisce(percorsoSorgente)) return Esito.Errore(ErroreAudioProgetto.CopiaFallita(percorsoSorgente))
        val nomeFile = percorsoSorgente.substringAfterLast('/').substringAfterLast('\\')
        val estensione = nomeFile.substringAfterLast('.', missingDelimiterValue = "").lowercase(Locale.ROOT)
        val suffisso = if (estensione.isEmpty()) "" else ".$estensione"
        val riferimento = RiferimentoAudio("audio/${id.valore}$suffisso")
        copiati += riferimento
        return Esito.Ok(riferimento)
    }

    override fun scarta(r: RiferimentoAudio) {
        copiati -= r
    }
}
