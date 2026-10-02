package snastro.sintesi.applicazione.porte

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Consumer-driven contract of [LettoreIncontro] (boundary `porte-sintesi`, ADR 0033 §4; AC-I204, AC-I28): one subclass
 * per implementation — [LettoreIncontroFinta] (D1) and `LettoreIncontroDaProgetto` (D2).
 */
public abstract class LettoreIncontroContratto {
    /** A fresh supplier: one Progetto, no Registrazione. */
    protected abstract fun ambiente(): AmbienteLettoreIncontro

    @Test
    public fun `AC-I28 l'unica Parte di un Incontro e la Parte 1`() {
        val a = ambiente()
        val lettore = a.lettore
        val r = a.importa()

        assertEquals(listOf(ParteSintesi(r, 1)), lettore.parti(a.incontroDi(r)))
    }

    @Test
    public fun `AC-I28 una modifica della data lascia l'unica Parte come Parte 1`() {
        val a = ambiente()
        val lettore = a.lettore
        val r = a.importa(data = LocalDate.of(2026, 10, 2))

        a.modificaData(r, LocalDate.of(2026, 9, 1))

        assertEquals(listOf(ParteSintesi(r, 1)), lettore.parti(a.incontroDi(r)))
    }

    @Test
    public fun `AC-I204 parti di un Incontro sconosciuto restituisce null`() {
        val a = ambiente()
        a.importa()

        assertNull(a.lettore.parti(IncontroId("incontro-sconosciuto")))
    }

    @Test
    public fun `AC-I204 parti dopo l'eliminazione dell'unica Parte restituisce null`() {
        val a = ambiente()
        val lettore = a.lettore
        val r = a.importa()
        val incontro = a.incontroDi(r)

        a.elimina(r)

        assertNull(lettore.parti(incontro))
    }

    @Test
    public fun `AC-I204 parti non elenca mai una Registrazione di un altro Incontro`() {
        val a = ambiente()
        val prima = a.importa()
        val seconda = a.importa()

        assertEquals(listOf(ParteSintesi(prima, 1)), a.lettore.parti(a.incontroDi(prima)))
        assertEquals(listOf(ParteSintesi(seconda, 1)), a.lettore.parti(a.incontroDi(seconda)))
    }

    /** AC-I28 on an Incontro of several Parti. */
    @TestFactory
    public fun `AC-I28 Incontro con piu Parti`(): List<DynamicTest> =
        listOf(
            dynamicTest("AC-I28 parti in ordine per data, ora di inizio (vuota in fondo), import, numerate 1..N") {
                ordinateENumerate()
            },
            dynamicTest("AC-I28 una modifica della data o dell'ora di inizio riordina le Parti") { riordinate() },
            dynamicTest("AC-I28 una Parte eliminata sparisce e le altre si rinumerano") { eliminataSparisce() },
            dynamicTest("AC-I204 un Incontro di piu Parti non elenca le Parti di un altro") { soloLeSue() },
        )

    private fun ordinateENumerate() {
        val a = ambiente()
        val lettore = a.lettore
        val senzaOra = a.importa(data = GIORNO_2)
        val incontro = a.incontroDi(senzaOra)
        val giornoPrima = a.aggiungiParte(incontro, data = GIORNO_1)
        val alleNove = a.aggiungiParte(incontro, data = GIORNO_2, ora = LocalTime.of(9, 0))
        val alleOtto = a.aggiungiParte(incontro, data = GIORNO_2, ora = LocalTime.of(8, 0))
        val ancoraSenzaOra = a.aggiungiParte(incontro, data = GIORNO_2)

        assertEquals(
            numerate(listOf(giornoPrima, alleOtto, alleNove, senzaOra, ancoraSenzaOra)),
            lettore.parti(incontro),
        )
    }

    private fun riordinate() {
        val a = ambiente()
        val lettore = a.lettore
        val prima = a.importa(data = GIORNO_1)
        val incontro = a.incontroDi(prima)
        val seconda = a.aggiungiParte(incontro, data = GIORNO_1)
        assertEquals(numerate(listOf(prima, seconda)), lettore.parti(incontro), "a parita di data, l'ordine di import")

        a.modificaOraDiInizio(seconda, LocalTime.of(10, 0))
        assertEquals(numerate(listOf(seconda, prima)), lettore.parti(incontro), "l'ora vuota va in fondo")

        a.modificaOraDiInizio(prima, LocalTime.of(9, 30))
        assertEquals(numerate(listOf(prima, seconda)), lettore.parti(incontro), "l'ora piu presto prima")

        a.modificaData(prima, GIORNO_2)
        assertEquals(numerate(listOf(seconda, prima)), lettore.parti(incontro), "la data prima dell'ora")
    }

    private fun eliminataSparisce() {
        val a = ambiente()
        val lettore = a.lettore
        val prima = a.importa(data = GIORNO_1)
        val incontro = a.incontroDi(prima)
        val seconda = a.aggiungiParte(incontro, data = GIORNO_2)
        val terza = a.aggiungiParte(incontro, data = GIORNO_3)

        a.elimina(seconda)
        assertEquals(numerate(listOf(prima, terza)), lettore.parti(incontro))

        a.elimina(prima)
        assertEquals(numerate(listOf(terza)), lettore.parti(incontro))
    }

    private fun soloLeSue() {
        val a = ambiente()
        val prima = a.importa()
        val incontro = a.incontroDi(prima)
        val altra = a.importa()
        val seconda = a.aggiungiParte(incontro)

        assertEquals(numerate(listOf(prima, seconda)), a.lettore.parti(incontro))
        assertEquals(numerate(listOf(altra)), a.lettore.parti(a.incontroDi(altra)))
    }

    private fun numerate(parti: List<RegistrazioneId>): List<ParteSintesi> =
        parti.mapIndexed { i, r -> ParteSintesi(r, i + 1) }

    private companion object {
        val GIORNO_1: LocalDate = LocalDate.of(2026, 9, 30)
        val GIORNO_2: LocalDate = LocalDate.of(2026, 10, 1)
        val GIORNO_3: LocalDate = LocalDate.of(2026, 10, 2)
    }
}
