package snastro.sbobinatura.applicazione.porte

import java.io.IOException

/**
 * In-memory [ScrittoreSbobinatura] for the consumers' tests: [sbobinature] shows what is present,
 * [operazioni] the completed operations in call order, and [fallisciAllaProssimaScrittura] /
 * [fallisciAllaProssimaRimozione] make the next call throw without changing anything.
 */
public class ScrittoreSbobinaturaFinta : ScrittoreSbobinatura {
    /** One completed operation, recorded in [operazioni]. */
    public sealed interface Operazione {
        public data class Scritto(val nomeFile: String) : Operazione

        public data class Rimosso(val nomeFile: String) : Operazione
    }

    private val scritti = mutableMapOf<String, String>()
    private val registro = mutableListOf<Operazione>()
    private var guastoScrittura: IOException? = null
    private var guastoRimozione: IOException? = null

    /** Snapshot of the documents currently present, name → markdown. */
    public val sbobinature: Map<String, String> get() = scritti.toMap()

    /** The operations that succeeded, in call order. A failed call is not recorded. */
    public val operazioni: List<Operazione> get() = registro.toList()

    /** The next [scrivi] throws [guasto] and leaves everything unchanged (one shot). */
    public fun fallisciAllaProssimaScrittura(guasto: IOException = IOException("scrittura simulata fallita")) {
        guastoScrittura = guasto
    }

    /** The next [rimuovi] throws [guasto] and leaves everything unchanged (one shot). */
    public fun fallisciAllaProssimaRimozione(guasto: IOException = IOException("rimozione simulata fallita")) {
        guastoRimozione = guasto
    }

    override fun scrivi(nomeFile: String, markdown: String) {
        richiediNomeFileSbobinatura(nomeFile)
        guastoScrittura?.let {
            guastoScrittura = null
            throw it
        }
        scritti[nomeFile] = markdown
        registro += Operazione.Scritto(nomeFile)
    }

    override fun rimuovi(nomeFile: String) {
        richiediNomeFileSbobinatura(nomeFile)
        guastoRimozione?.let {
            guastoRimozione = null
            throw it
        }
        scritti.remove(nomeFile)
        registro += Operazione.Rimosso(nomeFile)
    }
}
