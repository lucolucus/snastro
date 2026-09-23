package snastro.progetto.dominio

import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import kotlin.test.Test
import kotlin.test.assertEquals

class NomeProgettoTest {
    @Test
    fun `AC-16 con testo vuoto restituisce NomeProgettoVuoto`() {
        NomeProgetto.di("").erroreAtteso<ErroreProgetto.NomeProgettoVuoto>()
    }

    @Test
    fun `AC-16 con testo di soli spazi restituisce NomeProgettoVuoto`() {
        NomeProgetto.di(" \t\n ").erroreAtteso<ErroreProgetto.NomeProgettoVuoto>()
    }

    @Test
    fun `AC-16 con spazi ai lati il nome e trimmato`() {
        assertEquals("Riunioni di redazione", NomeProgetto.di("  Riunioni di redazione \t").atteso().valore)
    }

    @Test
    fun `AC-16 gli spazi interni sono conservati`() {
        assertEquals("Consiglio  comunale", NomeProgetto.di("Consiglio  comunale").atteso().valore)
    }
}
