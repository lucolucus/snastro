package snastro.ui

import org.junit.jupiter.api.Test
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Consumer-driven contract of [SessioneProgetto] (`tec-shell-ui`, in-process): the fake below proves
 * it green on its own (D1); `:avvio`'s real adapter over the project folder subclasses this too (D2).
 */
abstract class SessioneProgettoContratto {
    protected abstract fun con(): SessioneProgetto

    protected open fun cartellaGenitoreProva(): String = "/tmp/snastro-contratto"

    protected open fun nomeProva(): String = "Progetto Prova"

    @Test
    fun `nessun progetto e aperto all inizio`() {
        assertNull(con().corrente.value)
    }

    @Test
    fun `crea apre il progetto e aggiorna corrente`() {
        val sessione = con()
        val progetto = sessione.crea(cartellaGenitoreProva(), nomeProva()).atteso()
        assertEquals(progetto, sessione.corrente.value)
    }

    @Test
    fun `chiudi riporta corrente a null`() {
        val sessione = con()
        sessione.crea(cartellaGenitoreProva(), nomeProva()).atteso()
        sessione.chiudi()
        assertNull(sessione.corrente.value)
    }

    @Test
    fun `apri riapre lo stesso progetto creato in precedenza`() {
        val sessione = con()
        val creato = sessione.crea(cartellaGenitoreProva(), nomeProva()).atteso()
        sessione.chiudi()
        val riaperto = sessione.apri(creato.percorso).atteso()
        assertEquals(creato, riaperto)
        assertEquals(riaperto, sessione.corrente.value)
    }

    @Test
    fun `un nome vuoto restituisce NomeProgettoVuoto`() {
        val errore = con().crea(cartellaGenitoreProva(), "").erroreAtteso<ErroreSessione>()
        assertEquals(ErroreSessione.NomeProgettoVuoto, errore)
    }
}
