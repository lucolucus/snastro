package snastro.trascrizione.applicazione.porte

import org.junit.jupiter.api.Test
import snastro.kernel.RegistrazioneId

/**
 * Consumer-driven contract of [SegnalatoreGuasti] (ADR 0042): any report — repeated, for several Registrazioni,
 * with any cause — is accepted without throwing. Each implementation's own test checks where it sends it.
 */
public abstract class SegnalatoreGuastiContratto {
    /** The reporter under test. */
    protected abstract fun segnalatore(): SegnalatoreGuasti

    @Test
    public fun `ADR 0042 accetta guasti ripetuti per piu Registrazioni senza lanciare`() {
        val s = segnalatore()

        s.guasto(PRIMA, "impossibile leggere l'audio", IllegalStateException("decodifica"))
        s.guasto(SECONDA, "errore nella trascrizione", RuntimeException())
        s.guasto(PRIMA, "impossibile leggere l'audio", IllegalStateException("decodifica"))
    }

    private companion object {
        val PRIMA = RegistrazioneId("registrazione-1")
        val SECONDA = RegistrazioneId("registrazione-2")
    }
}
