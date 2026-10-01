package snastro.parlanti.applicazione.porte

import org.junit.jupiter.api.Test
import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * Consumer-driven contract of Parlanti's own [LettoreRegistrazione] (boundary
 * `registrazione-per-parlanti`): one subclass per implementation — the fake (D1) and
 * `registrazione-da-progetto-pa` (D2). Parlanti relies on `progettoId` (INV-17 scope) and on the
 * CURRENT `dataRegistrazione` ('Ospite del dd/MM/yyyy', INV-19).
 */
public abstract class LettoreRegistrazioneContratto {
    /** A fresh supplier with one Progetto and no Registrazione. */
    protected abstract fun ambiente(): AmbienteLettoreRegistrazione

    @Test
    public fun `AC-44 id sconosciuto senza registrazioni restituisce null`() {
        assertNull(ambiente().lettore.registrazione(SCONOSCIUTA))
    }

    @Test
    public fun `AC-44 id sconosciuto tra registrazioni note restituisce null`() {
        val ambiente = ambiente()
        ambiente.semina(RIUNIONE)

        assertNull(ambiente.lettore.registrazione(SCONOSCIUTA))
    }

    @Test
    public fun `AC-44 id noto restituisce la RegistrazioneVista con progettoId e dataRegistrazione`() {
        val ambiente = ambiente()
        val id = ambiente.semina(RIUNIONE)

        assertEquals(
            RegistrazioneVista(
                registrazioneId = id,
                progettoId = ambiente.progettoId,
                incontroId = ambiente.incontroDi(id),
                titolo = "Riunione di lunedi",
                riferimentoAudio = RiferimentoAudio("audio/${id.valore}.m4a"),
                dataRegistrazione = LocalDate.of(2026, 9, 12),
                durataMs = 3_600_000L,
            ),
            ambiente.lettore.registrazione(id),
        )
    }

    @Test
    public fun `AC-44 ogni id noto restituisce la propria registrazione con estensione minuscola`() {
        val ambiente = ambiente()
        val prima = ambiente.semina(RIUNIONE)
        val seconda = ambiente.semina(INTERVISTA)

        assertEquals(
            RegistrazioneVista(
                registrazioneId = seconda,
                progettoId = ambiente.progettoId,
                incontroId = ambiente.incontroDi(seconda),
                titolo = "Intervista",
                riferimentoAudio = RiferimentoAudio("audio/${seconda.valore}.mp3"),
                dataRegistrazione = LocalDate.of(2025, 12, 31),
                durataMs = 1L,
            ),
            ambiente.lettore.registrazione(seconda),
        )
        assertEquals(LocalDate.of(2026, 9, 12), ambiente.lettore.registrazione(prima)?.dataRegistrazione)
    }

    @Test
    public fun `AC-44 dopo una modifica della data restituisce la dataRegistrazione corrente`() {
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

    @Test
    public fun `AC-I205 registrazione restituisce l'incontroId fissato all'import, uno per Registrazione importata`() {
        val ambiente = ambiente()
        val prima = ambiente.semina(RIUNIONE)
        val seconda = ambiente.semina(INTERVISTA)

        assertEquals(ambiente.incontroDi(prima), ambiente.lettore.registrazione(prima)?.incontroId)
        assertEquals(ambiente.incontroDi(seconda), ambiente.lettore.registrazione(seconda)?.incontroId)
        assertNotEquals(ambiente.incontroDi(prima), ambiente.incontroDi(seconda))
    }

    @Test
    public fun `AC-I205 parti restituisce l'insieme delle Registrazioni dell'Incontro, in nessun ordine garantito`() {
        val ambiente = ambiente()
        val id = ambiente.semina(RIUNIONE)

        assertEquals(setOf(id), ambiente.lettore.parti(ambiente.incontroDi(id))?.toSet())
    }

    @Test
    public fun `AC-I205 parti di un Incontro sconosciuto restituisce null`() {
        val ambiente = ambiente()
        ambiente.semina(RIUNIONE)

        assertNull(ambiente.lettore.parti(IncontroId("incontro-sconosciuto")))
    }

    @Test
    public fun `AC-I205 parti dopo l'eliminazione dell'unica Parte restituisce null`() {
        val ambiente = ambiente()
        val id = ambiente.semina(RIUNIONE)
        val incontro = ambiente.incontroDi(id)

        ambiente.elimina(id)

        assertNull(ambiente.lettore.parti(incontro))
    }

    @Test
    public fun `AC-I205 parti non elenca mai una Registrazione di un altro Incontro`() {
        val ambiente = ambiente()
        val prima = ambiente.semina(RIUNIONE)
        val seconda = ambiente.semina(INTERVISTA)

        assertEquals(listOf(prima), ambiente.lettore.parti(ambiente.incontroDi(prima)))
        assertEquals(listOf(seconda), ambiente.lettore.parti(ambiente.incontroDi(seconda)))
    }

    private companion object {
        val SCONOSCIUTA = RegistrazioneId("registrazione-sconosciuta")
        val RIUNIONE = SemeRegistrazione("Riunione di lunedi", "m4a", LocalDate.of(2026, 9, 12), 3_600_000L)
        val INTERVISTA = SemeRegistrazione("Intervista", "MP3", LocalDate.of(2025, 12, 31), 1L)
    }
}
