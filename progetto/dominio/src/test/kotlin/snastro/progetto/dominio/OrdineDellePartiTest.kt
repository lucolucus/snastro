package snastro.progetto.dominio

import snastro.kernel.RegistrazioneId
import snastro.kernel.atteso
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class OrdineDellePartiTest {
    private val giorno = LocalDate.of(2026, 9, 12)

    private fun unaParte(
        id: String,
        data: LocalDate = giorno,
        ora: String? = null,
        aggiuntaAlle: Long = 0,
    ) = ParteDaOrdinare(
        registrazioneId = RegistrazioneId(id),
        dataRegistrazione = data,
        oraDiInizio = ora?.let { OraDiInizio.di(LocalTime.parse(it)).atteso() },
        aggiuntaAlle = Instant.ofEpochMilli(aggiuntaAlle),
    )

    private fun ordine(vararg parti: ParteDaOrdinare): List<String> =
        OrdineDelleParti.ordina(parti.toList()).map { it.registrazioneId.valore }

    @Test
    fun `INV-I2 date diverse si ordinano per data`() {
        val dopo = unaParte("a", data = giorno.plusDays(1), ora = "08:00:00")
        val prima = unaParte("b", data = giorno, ora = "18:00:00")

        assertEquals(listOf("b", "a"), ordine(dopo, prima))
    }

    @Test
    fun `INV-I2 stessa data, le 09 00 prima delle 10 00`() {
        val dieci = unaParte("dieci", ora = "10:00:00")
        val nove = unaParte("nove", ora = "09:00:00")

        assertEquals(listOf("nove", "dieci"), ordine(dieci, nove))
    }

    @Test
    fun `INV-I2 stessa data con un ora vuota, prima quella con l ora e la vuota in fondo`() {
        val vuota = unaParte("vuota", aggiuntaAlle = 1)
        val conOra = unaParte("conOra", ora = "23:00:00", aggiuntaAlle = 2)

        assertEquals(listOf("conOra", "vuota"), ordine(vuota, conOra))
    }

    @Test
    fun `INV-I2 A 09 00 importata terza, B vuota seconda, C 10 00 prima danno A C B su ogni permutazione`() {
        val a = unaParte("A", ora = "09:00:00", aggiuntaAlle = 3)
        val b = unaParte("B", aggiuntaAlle = 2)
        val c = unaParte("C", ora = "10:00:00", aggiuntaAlle = 1)

        permutazioni(listOf(a, b, c)).forEach { input ->
            assertEquals(listOf("A", "C", "B"), ordine(*input.toTypedArray()), "input $input")
        }
    }

    @Test
    fun `INV-I2 a parita piena decide aggiuntaAlle poi registrazioneId`() {
        val tardi = unaParte("a", ora = "09:00:00", aggiuntaAlle = 2)
        val presto = unaParte("z", ora = "09:00:00", aggiuntaAlle = 1)
        assertEquals(listOf("z", "a"), ordine(tardi, presto))

        val y = unaParte("y", aggiuntaAlle = 5)
        val x = unaParte("x", aggiuntaAlle = 5)
        assertEquals(listOf("x", "y"), ordine(y, x))
    }

    @Test
    fun `INV-I2 il numero della parte e il rango da 1 a N`() {
        val ordinate = OrdineDelleParti.ordina(
            listOf(unaParte("c", ora = "11:00:00"), unaParte("a", ora = "09:00:00"), unaParte("b", ora = "10:00:00")),
        )

        assertEquals(
            listOf(
                ParteOrdinata(RegistrazioneId("a"), 1),
                ParteOrdinata(RegistrazioneId("b"), 2),
                ParteOrdinata(RegistrazioneId("c"), 3),
            ),
            ordinate,
        )
    }

    private fun <T> permutazioni(lista: List<T>): List<List<T>> =
        if (lista.size <= 1) {
            listOf(lista)
        } else {
            lista.flatMap { primo -> permutazioni(lista - primo).map { listOf(primo) + it } }
        }
}
