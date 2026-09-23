package snastro.trascrizione.applicazione.porte

import org.junit.jupiter.api.Test
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Consumer-driven contract of [LettoreRegistrazione] (boundary `registrazione-per-trascrizione`):
 * one subclass per implementation — the fake (D1) and `registrazione-da-progetto-tr` (D2).
 * Every read reflects the CURRENT state of the supplier (e.g. a changed `dataRegistrazione`).
 */
public abstract class LettoreRegistrazioneContratto {
    /** A fresh supplier with one Progetto and no Registrazione. */
    protected abstract fun ambiente(): AmbienteLettoreRegistrazione

    @Test
    public fun `AC-43 id sconosciuto senza registrazioni restituisce null`() {
        assertNull(ambiente().lettore.registrazione(SCONOSCIUTA))
    }

    @Test
    public fun `AC-43 id sconosciuto tra registrazioni note restituisce null`() {
        val ambiente = ambiente()
        ambiente.semina(RIUNIONE)

        assertNull(ambiente.lettore.registrazione(SCONOSCIUTA))
    }

    @Test
    public fun `AC-43 id noto restituisce la RegistrazioneVista con tutti i campi fissati`() {
        val ambiente = ambiente()
        val id = ambiente.semina(RIUNIONE)

        assertEquals(
            RegistrazioneVista(
                registrazioneId = id,
                progettoId = ambiente.progettoId,
                titolo = "Riunione di lunedi",
                riferimentoAudio = RiferimentoAudio("audio/${id.valore}.m4a"),
                dataRegistrazione = LocalDate.of(2026, 9, 21),
                durataMs = 3_600_000L,
            ),
            ambiente.lettore.registrazione(id),
        )
    }

    @Test
    public fun `AC-43 ogni id noto restituisce la propria registrazione con estensione minuscola`() {
        val ambiente = ambiente()
        val prima = ambiente.semina(RIUNIONE)
        val seconda = ambiente.semina(INTERVISTA)

        assertEquals(
            RegistrazioneVista(
                registrazioneId = seconda,
                progettoId = ambiente.progettoId,
                titolo = "Intervista",
                riferimentoAudio = RiferimentoAudio("audio/${seconda.valore}.mp3"),
                dataRegistrazione = LocalDate.of(2025, 12, 31),
                durataMs = 1L,
            ),
            ambiente.lettore.registrazione(seconda),
        )
        assertEquals("Riunione di lunedi", ambiente.lettore.registrazione(prima)?.titolo)
    }

    @Test
    public fun `AC-43 dopo una modifica della data restituisce la dataRegistrazione corrente`() {
        val ambiente = ambiente()
        val id = ambiente.semina(RIUNIONE)
        val altra = ambiente.semina(INTERVISTA)
        val lettore = ambiente.lettore
        lettore.registrazione(id)

        ambiente.modificaData(id, LocalDate.of(2026, 1, 5))

        assertEquals(LocalDate.of(2026, 1, 5), lettore.registrazione(id)?.dataRegistrazione)
        assertEquals(LocalDate.of(2026, 1, 5), ambiente.lettore.registrazione(id)?.dataRegistrazione)
        assertEquals(ambiente.progettoId, ambiente.lettore.registrazione(id)?.progettoId)
        assertEquals(LocalDate.of(2025, 12, 31), ambiente.lettore.registrazione(altra)?.dataRegistrazione)
    }

    private companion object {
        val SCONOSCIUTA = RegistrazioneId("registrazione-sconosciuta")
        val RIUNIONE = SemeRegistrazione("Riunione di lunedi", "m4a", LocalDate.of(2026, 9, 21), 3_600_000L)
        val INTERVISTA = SemeRegistrazione("Intervista", "MP3", LocalDate.of(2025, 12, 31), 1L)
    }
}
