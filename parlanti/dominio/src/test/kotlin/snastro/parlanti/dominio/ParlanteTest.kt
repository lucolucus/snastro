package snastro.parlanti.dominio

import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ParlanteTest {
    private fun unNome(testo: String): Nome = Nome.di(testo).atteso()

    private fun unParlante(nome: String = "Marco", tipo: TipoParlante = TipoParlante.RICORRENTE): Parlante =
        Parlante.crea(ParlanteId("id-1"), ProgettoId("id-p"), unNome(nome), tipo).aggregato

    private fun unaVoce(n: Int, registrazione: String = "id-r"): VoceRef =
        VoceRef(RegistrazioneId(registrazione), VoceId(n))

    private fun unaImpronta(vararg valori: Float): Impronta = Impronta(valori)

    @Test
    fun `crea un Parlante attivo senza impronte e restituisce ParlanteCreato`() {
        val creato = Parlante.crea(ParlanteId("id-1"), ProgettoId("id-p"), unNome("Marco"), TipoParlante.OCCASIONALE)

        val p = creato.aggregato
        assertTrue(p.attivo)
        assertFalse(p.eliminato)
        assertTrue(p.occasionale)
        assertFalse(p.haImpronte)
        val atteso = ParlanteCreato(ParlanteId("id-1"), ProgettoId("id-p"), "Marco", TipoParlante.OCCASIONALE)
        assertEquals(atteso, creato.evento)
    }

    @Test
    fun `rinomina un Parlante attivo e restituisce ParlanteRinominato`() {
        val p = unParlante()

        val evento = p.rinomina(unNome("Luca")).atteso()

        assertEquals(unNome("Luca"), p.nome)
        assertEquals(ParlanteRinominato(p.id, "Luca"), evento)
    }

    @Test
    fun `INV-13 elimina rimuove tutte le impronte e rende il Parlante eliminato, poi ogni modifica e rifiutata`() {
        val p = unParlante()
        p.registraImpronta(unaVoce(1), unaImpronta(1f, 2f), SORGENTE, MODELLO).atteso()
        p.registraImpronta(unaVoce(2), unaImpronta(3f, 4f), SORGENTE, MODELLO).atteso()

        val evento = p.elimina().atteso()

        assertEquals(ParlanteEliminato(p.id), evento)
        assertTrue(p.eliminato)
        assertFalse(p.attivo)
        assertFalse(p.haImpronte)
        assertEquals(emptyList(), p.impronte)
        assertEquals(unNome("Marco"), p.nome, "il Nome resta come tombstone")
        val rifiutati = listOf(
            p.rinomina(unNome("Luca")),
            p.promuovi(null),
            p.registraImpronta(unaVoce(3), unaImpronta(5f), SORGENTE, MODELLO),
            p.elimina(),
        )
        rifiutati.forEach {
            val errore = it.erroreAtteso<ErroreParlanti.ParlanteEliminatoNonModificabile>()
            assertEquals(ErroreParlanti.ParlanteEliminatoNonModificabile(p.id), errore)
        }
        assertEquals(unNome("Marco"), p.nome)
        assertFalse(p.haImpronte)
    }

    @Test
    fun `INV-13 anche un occasionale eliminato non puo essere promosso`() {
        val p = unParlante(tipo = TipoParlante.OCCASIONALE)
        p.elimina().atteso()

        p.promuovi(unNome("Luca")).erroreAtteso<ErroreParlanti.ParlanteEliminatoNonModificabile>()

        assertTrue(p.occasionale)
        assertEquals(unNome("Marco"), p.nome)
    }

    @Test
    fun `INV-14 registraImpronta sostituisce solo l impronta dello stesso VoceRef e aggiunge le nuove`() {
        val p = unParlante()
        p.registraImpronta(unaVoce(1), unaImpronta(1f, 1f), SORGENTE, MODELLO).atteso()
        p.registraImpronta(unaVoce(2), unaImpronta(2f, 2f), SORGENTE, MODELLO).atteso()

        p.registraImpronta(unaVoce(1), unaImpronta(9f, 9f), SORGENTE, MODELLO).atteso()
        p.registraImpronta(unaVoce(1, registrazione = "id-altra"), unaImpronta(3f, 3f), SORGENTE, MODELLO).atteso()

        assertEquals(
            listOf(
                ImprontaVocale(unaVoce(1), unaImpronta(9f, 9f), SORGENTE, MODELLO),
                ImprontaVocale(unaVoce(2), unaImpronta(2f, 2f), SORGENTE, MODELLO),
                ImprontaVocale(unaVoce(1, registrazione = "id-altra"), unaImpronta(3f, 3f), SORGENTE, MODELLO),
            ),
            p.impronte,
        )
        assertEquals(p.impronte.size, p.impronte.map { it.voceRef }.toSet().size, "al piu una impronta per VoceRef")
    }

    @Test
    fun `rimuoviImpronta toglie solo l impronta di quel VoceRef ed e innocua se assente`() {
        val p = unParlante()
        p.registraImpronta(unaVoce(1), unaImpronta(1f), SORGENTE, MODELLO).atteso()
        p.registraImpronta(unaVoce(2), unaImpronta(2f), SORGENTE, MODELLO).atteso()

        p.rimuoviImpronta(unaVoce(1))
        p.rimuoviImpronta(unaVoce(7))

        assertEquals(listOf(ImprontaVocale(unaVoce(2), unaImpronta(2f), SORGENTE, MODELLO)), p.impronte)
    }

    @Test
    fun `le impronte esposte sono una copia`() {
        val p = unParlante()
        p.registraImpronta(unaVoce(1), unaImpronta(1f), SORGENTE, MODELLO).atteso()

        val copia = p.impronte
        p.rimuoviImpronta(unaVoce(1))

        assertEquals(1, copia.size)
        assertFalse(p.haImpronte)
    }

    @Test
    fun `INV-18 promuovi rifiuta un ricorrente e su un occasionale cambia solo tipo e nome`() {
        val ricorrente = unParlante(tipo = TipoParlante.RICORRENTE)
        assertEquals(
            ErroreParlanti.PromozioneNonAmmessa(ricorrente.id),
            ricorrente.promuovi(null).erroreAtteso<ErroreParlanti.PromozioneNonAmmessa>(),
        )

        val p = unParlante(nome = "Ospite del 12/09/2026", tipo = TipoParlante.OCCASIONALE)
        p.registraImpronta(unaVoce(1), unaImpronta(1f, 2f), SORGENTE, MODELLO).atteso()
        val improntePrima = p.impronte

        val evento = p.promuovi(unNome("Giulia")).atteso()

        assertEquals(ParlantePromosso(p.id, "Giulia", nomeCambiato = true), evento)
        assertFalse(p.occasionale)
        assertEquals(TipoParlante.RICORRENTE, p.tipo)
        assertEquals(unNome("Giulia"), p.nome)
        assertEquals(improntePrima, p.impronte)
        assertTrue(p.attivo)
        assertEquals(
            ErroreParlanti.PromozioneNonAmmessa(p.id),
            p.promuovi(null).erroreAtteso<ErroreParlanti.PromozioneNonAmmessa>(),
            "solo occasionale -> ricorrente, una volta",
        )
    }

    @Test
    fun `INV-18 promuovi senza nome lascia il Nome e segnala nomeCambiato falso`() {
        val p = unParlante(nome = "Ospite del 12/09/2026", tipo = TipoParlante.OCCASIONALE)

        val evento = p.promuovi(null).atteso()

        assertEquals(ParlantePromosso(p.id, "Ospite del 12/09/2026", nomeCambiato = false), evento)
        assertEquals(unNome("Ospite del 12/09/2026"), p.nome)
        assertEquals(TipoParlante.RICORRENTE, p.tipo)
    }

    @Test
    fun `INV-13 su un eliminato trasferisciImpronta non crea impronte`() {
        val p = unParlante()
        p.registraImpronta(unaVoce(2), unaImpronta(1f), SORGENTE, MODELLO).atteso()
        p.elimina().atteso()

        p.trasferisciImpronta(da = unaVoce(2), a = unaVoce(1))

        assertFalse(p.haImpronte)
        assertEquals(emptyList(), p.impronte)
    }

    @Test
    fun `INV-14 trasferisciImpronta ri-chiava su a con stessa Impronta sorgente e modello senza toccare le altre`() {
        val p = unParlante()
        p.registraImpronta(unaVoce(2), unaImpronta(2f, 2f), "3000-9000", "modello-b").atteso()
        p.registraImpronta(unaVoce(5), unaImpronta(5f, 5f), SORGENTE, MODELLO).atteso()

        p.trasferisciImpronta(da = unaVoce(2), a = unaVoce(1))

        assertEquals(
            listOf(
                ImprontaVocale(unaVoce(1), unaImpronta(2f, 2f), "3000-9000", "modello-b"),
                ImprontaVocale(unaVoce(5), unaImpronta(5f, 5f), SORGENTE, MODELLO),
            ),
            p.impronte,
        )
    }

    @Test
    fun `INV-14 trasferisciImpronta senza impronta per da non fa nulla`() {
        val p = unParlante()
        p.registraImpronta(unaVoce(5), unaImpronta(5f), SORGENTE, MODELLO).atteso()

        p.trasferisciImpronta(da = unaVoce(2), a = unaVoce(1))

        assertEquals(listOf(ImprontaVocale(unaVoce(5), unaImpronta(5f), SORGENTE, MODELLO)), p.impronte)
    }

    @Test
    fun `INV-14 trasferisciImpronta su un a che ha gia un impronta e un errore di programmazione`() {
        val p = unParlante()
        p.registraImpronta(unaVoce(1), unaImpronta(1f), SORGENTE, MODELLO).atteso()
        p.registraImpronta(unaVoce(2), unaImpronta(2f), SORGENTE, MODELLO).atteso()

        assertFailsWith<IllegalArgumentException> { p.trasferisciImpronta(da = unaVoce(2), a = unaVoce(1)) }

        assertEquals(2, p.impronte.size)
    }

    @Test
    fun `AC-268 registraImpronta conserva sorgente e modello`() {
        val p = unParlante()

        p.registraImpronta(unaVoce(1), unaImpronta(1f), "1200-5400,8000-15000", "campplus").atteso()

        val impronta = p.impronte.single()
        assertEquals("1200-5400,8000-15000", impronta.sorgente)
        assertEquals("campplus", impronta.modello)
    }

    @Test
    fun `AC-268 obsoleta e vera se sorgente o modello differiscono e falsa se coincidono entrambi`() {
        val impronta = ImprontaVocale(unaVoce(1), unaImpronta(1f), "0-1000", "m1")

        assertFalse(impronta.obsoleta(chiaveCorrente = "0-1000", modelloCorrente = "m1"))
        assertTrue(impronta.obsoleta(chiaveCorrente = "0-2000", modelloCorrente = "m1"))
        assertTrue(impronta.obsoleta(chiaveCorrente = "0-1000", modelloCorrente = "m2"))
        assertTrue(impronta.obsoleta(chiaveCorrente = "0-2000", modelloCorrente = "m2"))
    }

    @Test
    fun `AC-268 la regola di obsolescenza sui soli metadati della riga coincide con quella dell impronta`() {
        val casi = listOf("0-1000" to "m1", "0-2000" to "m1", "0-1000" to "m2", "0-2000" to "m2")
        val impronta = ImprontaVocale(unaVoce(1), unaImpronta(1f), "0-1000", "m1")

        casi.forEach { (chiave, modello) ->
            assertEquals(
                impronta.obsoleta(chiave, modello),
                ImprontaVocale.obsoleta(sorgente = "0-1000", modello = "m1", chiave, modello),
                "chiave $chiave modello $modello",
            )
        }
        assertTrue(ImprontaVocale.obsoleta("0-1000", "m1", "0-1000", "m2"))
        assertFalse(ImprontaVocale.obsoleta("0-1000", "m1", "0-1000", "m1"))
    }

    private companion object {
        const val SORGENTE = "0-1000"
        const val MODELLO = "finto"
    }
}
