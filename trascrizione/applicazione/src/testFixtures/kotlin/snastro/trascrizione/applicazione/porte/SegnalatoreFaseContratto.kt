package snastro.trascrizione.applicazione.porte

import org.junit.jupiter.api.Test
import snastro.kernel.RegistrazioneId

/**
 * Consumer-driven contract of [SegnalatoreFase] (boundary `tec-segnalatore-fase`, ADR 0004): progress
 * signals are fire-and-forget and never fail the pipeline — any sequence, interleaving, repetition or a
 * [SegnalatoreFase.terminata] without phases is accepted without throwing. Each implementation's own test
 * checks what it does with them (the Finta records them, AC-36).
 */
public abstract class SegnalatoreFaseContratto {
    /** The signaller under test. */
    protected abstract fun segnalatore(): SegnalatoreFase

    @Test
    public fun `AC-36 accetta tutte le fasi in ordine e la terminazione per piu Registrazioni intercalate`() {
        val s = segnalatore()

        FaseElaborazione.entries.forEach { f ->
            s.fase(PRIMA, f)
            s.fase(SECONDA, f)
        }
        s.terminata(PRIMA)
        s.terminata(SECONDA)
    }

    @Test
    public fun `AC-36 accetta fasi ripetute e una terminazione senza fasi`() {
        val s = segnalatore()

        s.terminata(PRIMA)
        s.fase(SECONDA, FaseElaborazione.DECODIFICA)
        s.fase(SECONDA, FaseElaborazione.DECODIFICA)
        s.terminata(SECONDA)
        s.terminata(SECONDA)
    }

    private companion object {
        val PRIMA = RegistrazioneId("registrazione-1")
        val SECONDA = RegistrazioneId("registrazione-2")
    }
}
