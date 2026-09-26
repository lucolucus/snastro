package snastro.sintesi.dominio

import snastro.kernel.ErroreDominio
import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import kotlin.test.Test
import kotlin.test.assertEquals

class RiassumibilitaTest {
    private val id = RegistrazioneId("id-2")

    private data class Condizioni(
        val modello: Boolean = true,
        val trascritto: Boolean = true,
        val elaborazione: Boolean = false,
        val riassunto: Boolean = false,
        val stima: Int? = LimiteIngresso.LIMITE_TOKEN,
    )

    private fun valuta(c: Condizioni): Esito<Unit> =
        Riassumibilita.valuta(id, c.modello, c.trascritto, c.elaborazione, c.riassunto, c.stima)

    private val troppoLunga =
        ErroreSintesi.RegistrazioneTroppoLunga(LimiteIngresso.LIMITE_TOKEN + 1, LimiteIngresso.LIMITE_TOKEN)

    @Test
    fun `INV-S6 ogni precondizione che manca da sola da il suo errore e tutte soddisfatte danno Ok`() {
        val casi = mapOf<Condizioni, ErroreDominio>(
            Condizioni(modello = false) to ErroreSintesi.ModelloNonInstallato,
            Condizioni(trascritto = false) to ErroreSintesi.TrascrittoNonDisponibile(id),
            Condizioni(elaborazione = true) to ErroreSintesi.ElaborazioneGiaAperta(id),
            Condizioni(riassunto = true) to ErroreSintesi.RiassuntoGiaAperto(id),
            Condizioni(stima = LimiteIngresso.LIMITE_TOKEN + 1) to troppoLunga,
        )

        casi.forEach { (c, errore) -> assertEquals(Esito.Errore(errore), valuta(c), "$c") }
        assertEquals(Esito.Ok(Unit), valuta(Condizioni()))
    }

    @Test
    fun `INV-S6 piu precondizioni mancanti danno la prima nell ordine fissato`() {
        val tutte = Condizioni(false, false, true, true, LimiteIngresso.LIMITE_TOKEN + 1)
        val casi = listOf(
            tutte to ErroreSintesi.ModelloNonInstallato,
            tutte.copy(modello = true) to ErroreSintesi.TrascrittoNonDisponibile(id),
            tutte.copy(modello = true, trascritto = true) to ErroreSintesi.ElaborazioneGiaAperta(id),
            tutte.copy(modello = true, trascritto = true, elaborazione = false) to ErroreSintesi.RiassuntoGiaAperto(id),
        )

        casi.forEach { (c, errore) -> assertEquals(Esito.Errore(errore), valuta(c), "$c") }
    }
}
