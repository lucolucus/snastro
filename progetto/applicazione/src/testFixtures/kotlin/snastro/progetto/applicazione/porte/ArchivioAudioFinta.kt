package snastro.progetto.applicazione.porte

import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import java.util.Locale

/**
 * In-memory [ArchivioAudio] over an in-memory project folder. Sources are declared with [conSorgente]
 * (copyable) or [conSorgenteCheFallisce]; copying an undeclared source fails with CopiaFallita.
 * Not [snastro.kernel.Ripristinabile]: like real files, a copy is not undone by a rollback — only
 * [scarta] removes it.
 */
public class ArchivioAudioFinta : ArchivioAudio {
    private val sorgenti = mutableMapOf<String, ByteArray>()
    private val guaste = mutableSetOf<String>()
    private val progetto = linkedMapOf<String, ByteArray>()

    /** The references currently in `audio/`. */
    public val archiviati: Set<RiferimentoAudio>
        get() = progetto.keys.filter { it.startsWith(AUDIO) }.map(::RiferimentoAudio).toSet()

    public fun conSorgente(percorso: String, contenuto: ByteArray = byteArrayOf(1)): ArchivioAudioFinta = apply {
        sorgenti[percorso] = contenuto.copyOf()
    }

    public fun conSorgenteCheFallisce(percorso: String): ArchivioAudioFinta = apply { guaste += percorso }

    /** Puts a file at [percorsoRelativo] in the project folder (e.g. `documenti/…`). */
    public fun conFileNelProgetto(percorsoRelativo: String, contenuto: ByteArray): ArchivioAudioFinta = apply {
        progetto[percorsoRelativo] = contenuto.copyOf()
    }

    /** The content of the project file at [percorsoRelativo], or null if absent. */
    public fun contenuto(percorsoRelativo: String): ByteArray? = progetto[percorsoRelativo]?.copyOf()

    override fun copia(percorsoSorgente: String, id: RegistrazioneId): Esito<RiferimentoAudio> {
        val contenuto = sorgenti[percorsoSorgente]
        if (percorsoSorgente in guaste || contenuto == null) {
            return Esito.Errore(ErroreApplicazioneProgetto.CopiaFallita(percorsoSorgente))
        }
        val nomeFile = percorsoSorgente.substringAfterLast('/').substringAfterLast('\\')
        val estensione = nomeFile.substringAfterLast('.', missingDelimiterValue = "").lowercase(Locale.ROOT)
        val suffisso = if (estensione.isEmpty()) "" else ".$estensione"
        val riferimento = RiferimentoAudio("$AUDIO${id.valore}$suffisso")
        progetto[riferimento.percorsoRelativo] = contenuto.copyOf()
        return Esito.Ok(riferimento)
    }

    override fun scarta(r: RiferimentoAudio) {
        val percorso = r.percorsoRelativo
        val inAudio = percorso.startsWith(AUDIO) && percorso.split('/', '\\').none { it == ".." }
        if (inAudio) progetto.remove(percorso)
    }

    private companion object {
        const val AUDIO = "audio/"
    }
}
