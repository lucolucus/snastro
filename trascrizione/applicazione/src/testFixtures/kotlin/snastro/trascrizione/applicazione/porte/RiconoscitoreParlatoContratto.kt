package snastro.trascrizione.applicazione.porte

import org.junit.jupiter.api.Test
import snastro.kernel.CampioniAudio
import kotlin.test.assertTrue

/**
 * Consumer-driven contract of [RiconoscitoreParlato] (boundary `tec-riconoscitore`, ADR 0004): tokens, when
 * present, fall within the duration of the given samples (relative to their start); empty samples give a
 * blank text; silence never throws. One subclass per implementation; the real one is `@Tag("modelli")`.
 */
public abstract class RiconoscitoreParlatoContratto {
    /** The recognizer under test. */
    protected abstract fun riconoscitore(): RiconoscitoreParlato

    /** Speech the implementation recognizes: a synthetic tone for the Finta, a `sample/` excerpt for real models. */
    protected open fun parlato(): CampioniAudio = tonoDiProva(DURATA_PARLATO_MS)

    @Test
    public fun `AC-33 il parlato produce testo e i token se presenti cadono dentro la durata`() {
        val campioni = parlato()

        val r = riconoscitore().riconosci(campioni)

        assertTrue(r.testo.isNotBlank(), "il parlato produce testo")
        assertTokenEntro(r, campioni)
    }

    @Test
    public fun `AC-33 i token sono relativi all inizio dei campioni dati`() {
        val tutti = parlato().campioni
        val secondaMeta = CampioniAudio(tutti.copyOfRange(tutti.size / 2, tutti.size))

        assertTokenEntro(riconoscitore().riconosci(secondaMeta), secondaMeta)
    }

    @Test
    public fun `AC-33 campioni vuoti producono testo vuoto senza token`() {
        val r = riconoscitore().riconosci(CampioniAudio(FloatArray(0)))

        assertTrue(r.testo.isBlank(), "testo: '${r.testo}'")
        assertTrue(r.token.isNullOrEmpty(), "token: ${r.token}")
    }

    @Test
    public fun `AC-33 il silenzio non lancia e i token se presenti cadono dentro la durata`() {
        val campioni = silenzio(DURATA_PARLATO_MS)

        assertTokenEntro(riconoscitore().riconosci(campioni), campioni)
    }

    private fun assertTokenEntro(r: Riconoscimento, campioni: CampioniAudio) {
        r.token.orEmpty().forEach {
            assertTrue(it.intervallo.fineMs <= campioni.durataMs(), "$it oltre ${campioni.durataMs()} ms")
        }
    }

    private companion object {
        const val DURATA_PARLATO_MS = 3_000L
    }
}
