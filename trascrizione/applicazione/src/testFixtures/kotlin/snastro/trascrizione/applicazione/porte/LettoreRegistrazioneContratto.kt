package snastro.trascrizione.applicazione.porte

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * Consumer-driven contract of [LettoreRegistrazione] (boundary `registrazione-per-trascrizione`):
 * one subclass per implementation — the fake (D1) and `registrazione-da-progetto-tr` (D2).
 * Every read reflects the CURRENT state of the supplier (e.g. a changed `dataRegistrazione`).
 * D-0037: the cases on an Incontro with more than one Parte are built only when the environment declares
 * [AmbienteLettoreRegistrazione.piuPartiPerIncontro]; otherwise that factory runs the one-Parte guard (never skipped).
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
                incontroId = ambiente.incontroDi(id),
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
                incontroId = ambiente.incontroDi(seconda),
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
    public fun `AC-I22 parti di un Incontro sconosciuto restituisce null`() {
        val ambiente = ambiente()
        ambiente.semina(RIUNIONE)

        assertNull(ambiente.lettore.parti(IncontroId("incontro-sconosciuto")))
    }

    @Test
    public fun `AC-I22 l'Incontro di una Registrazione importata ha quella sola Parte, numero 1`() {
        val ambiente = ambiente()
        val id = ambiente.semina(RIUNIONE)
        ambiente.semina(INTERVISTA)

        assertEquals(listOf(ParteDiIncontro(id, 1)), ambiente.lettore.parti(ambiente.incontroDi(id)))
    }

    @TestFactory
    public fun `AC-I22 parti di un Incontro con piu' Parti`(): List<DynamicTest> {
        val ambiente = ambiente()
        return if (ambiente.piuPartiPerIncontro) {
            listOf(
                dynamicTest("AC-I22 parti restituisce le Parti nell'ordine del fornitore, numerate 1..N") {
                    val incontro = ambiente.seminaIncontro(listOf(RIUNIONE, INTERVISTA, RIPRESA))
                    val ordine = ambiente.ordineDelleParti(incontro)

                    assertEquals(3, ordine.size)
                    val attese = ordine.mapIndexed { i, r -> ParteDiIncontro(r, i + 1) }
                    assertEquals(attese, ambiente.lettore.parti(incontro))
                    ordine.forEach { assertEquals(incontro, ambiente.lettore.registrazione(it)?.incontroId) }
                },
            )
        } else {
            listOf(
                dynamicTest("AC-I22 ambiente con una Parte per Incontro: ogni import ha il suo Incontro di una Parte") {
                    val prima = ambiente.semina(RIUNIONE)
                    val seconda = ambiente.semina(INTERVISTA)

                    assertEquals(listOf(ParteDiIncontro(prima, 1)), ambiente.lettore.parti(ambiente.incontroDi(prima)))
                    assertEquals(
                        listOf(ParteDiIncontro(seconda, 1)),
                        ambiente.lettore.parti(ambiente.incontroDi(seconda)),
                    )
                },
            )
        }
    }

    private companion object {
        val RIPRESA = SemeRegistrazione("Ripresa", "wav", LocalDate.of(2026, 9, 21), 600_000L)
        val SCONOSCIUTA = RegistrazioneId("registrazione-sconosciuta")
        val RIUNIONE = SemeRegistrazione("Riunione di lunedi", "m4a", LocalDate.of(2026, 9, 21), 3_600_000L)
        val INTERVISTA = SemeRegistrazione("Intervista", "MP3", LocalDate.of(2025, 12, 31), 1L)
    }
}
