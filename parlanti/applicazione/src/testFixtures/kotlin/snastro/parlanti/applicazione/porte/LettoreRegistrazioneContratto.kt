package snastro.parlanti.applicazione.porte

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
 * Consumer-driven contract of Parlanti's own [LettoreRegistrazione] (boundary
 * `registrazione-per-parlanti`): one subclass per implementation — the fake (D1) and
 * `registrazione-da-progetto-pa` (D2). Parlanti relies on `progettoId` (INV-17 scope) and on the
 * CURRENT `dataRegistrazione` ('Ospite del dd/MM/yyyy', INV-19), and on [LettoreRegistrazione.parti] for the
 * Incontro's Parti in order, numbered, with their dates (AC-I205, AC-I25).
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

        assertEquals(setOf(id), ambiente.lettore.parti(ambiente.incontroDi(id))?.map { it.registrazioneId }?.toSet())
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

        assertEquals(listOf(prima), ambiente.lettore.parti(ambiente.incontroDi(prima))?.map { it.registrazioneId })
        assertEquals(listOf(seconda), ambiente.lettore.parti(ambiente.incontroDi(seconda))?.map { it.registrazioneId })
    }

    @Test
    public fun `AC-I25 l'unica Parte di un Incontro e la numero 1 con la sua data corrente`() {
        val ambiente = ambiente()
        val id = ambiente.semina(RIUNIONE)
        val lettore = ambiente.lettore
        assertEquals(listOf(ParteDiIncontroParlanti(id, 1, RIUNIONE.dataRegistrazione)), lettore.parti(ambiente.incontroDi(id)))

        ambiente.modificaData(id, LocalDate.of(2026, 1, 5))

        assertEquals(listOf(ParteDiIncontroParlanti(id, 1, LocalDate.of(2026, 1, 5))), lettore.parti(ambiente.incontroDi(id)))
    }

    /**
     * AC-I25 on an Incontro with several Parti: registered only where [AmbienteLettoreRegistrazione.piuPartiPerIncontro]
     * holds (D-0037: the fake now, the real adapter once the I2 import lands) — dynamic tests, never a skipped one.
     */
    @TestFactory
    public fun `AC-I25 casi con piu Parti`(): List<DynamicTest> {
        if (!ambiente().piuPartiPerIncontro) return emptyList()
        return listOf(
            dynamicTest("AC-I25 parti ordinate per data e import, numerate 1..N con la data di ciascuna") {
                val ambiente = ambiente()
                val tarda = ambiente.semina(RIUNIONE) // 12/09/2026, imported first
                val incontro = ambiente.incontroDi(tarda)
                val presto = ambiente.aggiungiParte(incontro, INTERVISTA) // 31/12/2025
                val pari = ambiente.aggiungiParte(incontro, INTERVISTA.copy(titolo = "Seguito")) // same date, later import
                val altra = ambiente.semina(RIUNIONE)

                val parti = ambiente.lettore.parti(incontro)

                assertEquals(
                    listOf(
                        ParteDiIncontroParlanti(presto, 1, INTERVISTA.dataRegistrazione),
                        ParteDiIncontroParlanti(pari, 2, INTERVISTA.dataRegistrazione),
                        ParteDiIncontroParlanti(tarda, 3, RIUNIONE.dataRegistrazione),
                    ),
                    parti,
                )
                assertEquals(parti?.minOf { it.dataRegistrazione }, parti?.first()?.dataRegistrazione, "data dell'Incontro")
                assertEquals(listOf(altra), ambiente.lettore.parti(ambiente.incontroDi(altra))?.map { it.registrazioneId })
            },
            dynamicTest("AC-I25 dopo una modifica della data ordine e numeri seguono la data corrente") {
                val ambiente = ambiente()
                val prima = ambiente.semina(INTERVISTA)
                val incontro = ambiente.incontroDi(prima)
                val seconda = ambiente.aggiungiParte(incontro, RIUNIONE)
                val lettore = ambiente.lettore
                assertEquals(listOf(prima, seconda), lettore.parti(incontro)?.map { it.registrazioneId })

                ambiente.modificaData(prima, LocalDate.of(2026, 12, 1))

                assertEquals(
                    listOf(
                        ParteDiIncontroParlanti(seconda, 1, RIUNIONE.dataRegistrazione),
                        ParteDiIncontroParlanti(prima, 2, LocalDate.of(2026, 12, 1)),
                    ),
                    lettore.parti(incontro),
                )
            },
            dynamicTest("AC-I25 eliminata una Parte le altre restano numerate 1..N e l'Incontro non cessa") {
                val ambiente = ambiente()
                val prima = ambiente.semina(INTERVISTA)
                val incontro = ambiente.incontroDi(prima)
                val seconda = ambiente.aggiungiParte(incontro, INTERVISTA.copy(titolo = "Seguito"))
                val terza = ambiente.aggiungiParte(incontro, RIUNIONE)

                ambiente.elimina(seconda)

                assertEquals(
                    listOf(
                        ParteDiIncontroParlanti(prima, 1, INTERVISTA.dataRegistrazione),
                        ParteDiIncontroParlanti(terza, 2, RIUNIONE.dataRegistrazione),
                    ),
                    ambiente.lettore.parti(incontro),
                )
                assertEquals(incontro, ambiente.lettore.registrazione(terza)?.incontroId)
            },
        )
    }

    private companion object {
        val SCONOSCIUTA = RegistrazioneId("registrazione-sconosciuta")
        val RIUNIONE = SemeRegistrazione("Riunione di lunedi", "m4a", LocalDate.of(2026, 9, 12), 3_600_000L)
        val INTERVISTA = SemeRegistrazione("Intervista", "MP3", LocalDate.of(2025, 12, 31), 1L)
    }
}
