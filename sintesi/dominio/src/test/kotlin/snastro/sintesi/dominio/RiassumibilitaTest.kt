package snastro.sintesi.dominio

import snastro.kernel.ErroreDominio
import snastro.kernel.Esito
import snastro.sintesi.dominio.StatoParte.DA_TRASCRIVERE
import snastro.sintesi.dominio.StatoParte.IN_TRASCRIZIONE
import snastro.sintesi.dominio.StatoParte.NON_RIUSCITA
import snastro.sintesi.dominio.StatoParte.TRASCRITTA
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RiassumibilitaTest {
    private data class Condizioni(
        val modello: Boolean = true,
        val parti: List<StatoParte> = listOf(TRASCRITTA),
        val riassunto: Boolean = false,
        val stima: Int? = LimiteIngresso.LIMITE_TOKEN,
    )

    private fun valuta(c: Condizioni): Esito<Unit> = Riassumibilita.valuta(
        c.modello,
        c.parti.mapIndexed { i, stato -> i + 1 to stato },
        c.riassunto,
        c.stima,
    )

    private val troppoLungo =
        ErroreSintesi.IngressoTroppoLungo(LimiteIngresso.LIMITE_TOKEN + 1, LimiteIngresso.LIMITE_TOKEN)

    @Test
    fun `INV-I9 tabella degli stati delle Parti`() {
        val casi = mapOf<Condizioni, ErroreDominio>(
            Condizioni(parti = listOf(TRASCRITTA, DA_TRASCRIVERE)) to ErroreSintesi.PartiNonTrascritte(2),
            Condizioni(parti = listOf(IN_TRASCRIZIONE, TRASCRITTA)) to ErroreSintesi.ElaborazioneGiaAperta(1),
            Condizioni(parti = listOf(NON_RIUSCITA)) to ErroreSintesi.PartiFallite(1),
            Condizioni(parti = listOf(TRASCRITTA, NON_RIUSCITA, DA_TRASCRIVERE)) to ErroreSintesi.PartiFallite(2),
            Condizioni(parti = listOf(TRASCRITTA, TRASCRITTA, IN_TRASCRIZIONE, NON_RIUSCITA)) to
                ErroreSintesi.ElaborazioneGiaAperta(3),
            Condizioni(modello = false, parti = listOf(DA_TRASCRIVERE)) to ErroreSintesi.ModelloNonInstallato,
            Condizioni(riassunto = true) to ErroreSintesi.RiassuntoGiaAperto(),
            Condizioni(stima = LimiteIngresso.LIMITE_TOKEN + 1) to troppoLungo,
        )

        casi.forEach { (c, errore) -> assertEquals(Esito.Errore(errore), valuta(c), "$c") }
        assertEquals(Esito.Ok(Unit), valuta(Condizioni(parti = listOf(TRASCRITTA, TRASCRITTA))))
        assertEquals(Esito.Ok(Unit), valuta(Condizioni(stima = null)))
    }

    @Test
    fun `INV-I9 la prima Parte bloccante e quella di numero minore anche se le coppie arrivano in disordine`() {
        val esito = Riassumibilita.valuta(true, listOf(3 to DA_TRASCRIVERE, 2 to NON_RIUSCITA), false, null)

        assertEquals(Esito.Errore(ErroreSintesi.PartiFallite(2)), esito)
    }

    @Test
    fun `INV-I9 piu precondizioni mancanti danno la prima nell ordine fissato`() {
        val tutte = Condizioni(false, listOf(DA_TRASCRIVERE), true, LimiteIngresso.LIMITE_TOKEN + 1)
        val casi = listOf(
            tutte to ErroreSintesi.ModelloNonInstallato,
            tutte.copy(modello = true) to ErroreSintesi.PartiNonTrascritte(1),
            tutte.copy(modello = true, parti = listOf(TRASCRITTA)) to ErroreSintesi.RiassuntoGiaAperto(),
            tutte.copy(modello = true, parti = listOf(TRASCRITTA), riassunto = false) to troppoLungo,
        )

        casi.forEach { (c, errore) -> assertEquals(Esito.Errore(errore), valuta(c), "$c") }
    }

    @Test
    fun `INV-I9 un Incontro senza Parti e un errore del programmatore`() {
        assertFailsWith<IllegalArgumentException> { Riassumibilita.valuta(true, emptyList(), false, null) }
    }

    @Test
    fun `INV-I9 un numero di Parte ripetuto e un errore del programmatore`() {
        assertFailsWith<IllegalArgumentException> {
            Riassumibilita.valuta(true, listOf(1 to TRASCRITTA, 1 to DA_TRASCRIVERE), false, null)
        }
    }
}
