package snastro.ui.registrazioni

import snastro.kernel.RegistrazioneId
import snastro.ui.testi.MESSAGGIO_CONFERMA_RITRASCRIVI
import snastro.ui.testi.MESSAGGIO_CONFERMA_RITRASCRIVI_PARTE
import snastro.ui.testi.titoloConfermaRitrascrivi
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun riga(parte: ParteDiIncontro?) = RigaRegistrazione(
    registrazioneId = RegistrazioneId("parte-2"),
    titolo = "file 2",
    dataRegistrazione = LocalDate.of(2026, 9, 30),
    durataMs = 125_000,
    parte = parte,
)

/** AC-I76 (ADR 0035 §8): the 'Ritrascrivi' confirmation text of a Parte of a multi-part Incontro. */
class RegistrazioniRitrascriviParteTest {
    @Test
    fun `AC-I76 Ritrascrivi sulla parte 2 di 3 nomina la parte e l'Incontro e spiega cosa si perde`() {
        val testi = riga(ParteDiIncontro(2, "Riunione di progetto")).testiConfermaRitrascrivi()

        assertEquals("Ritrascrivere la parte 2 di «Riunione di progetto»?", testi.titolo)
        assertEquals(MESSAGGIO_CONFERMA_RITRASCRIVI_PARTE, testi.messaggio)
        assertTrue(testi.messaggio.contains("Le voci che compaiono solo in questa parte"))
        assertTrue(testi.messaggio.endsWith("Il riassunto dell'incontro diventerà superato."))
    }

    @Test
    fun `INV-I3 su un Incontro di una parte il dialogo e quello di oggi`() {
        val testi = riga(null).testiConfermaRitrascrivi()

        assertEquals(titoloConfermaRitrascrivi("file 2"), testi.titolo)
        assertEquals(MESSAGGIO_CONFERMA_RITRASCRIVI, testi.messaggio)
    }
}
