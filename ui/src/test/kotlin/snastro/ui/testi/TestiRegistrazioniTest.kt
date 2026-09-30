package snastro.ui.testi

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * AC-S141: the "with a Trascritto" Elimina confirmation body is exactly this Italian sentence,
 * naming the riassunto among what's deleted — a literal fixture, not the constant read back at
 * itself, so a future rewording that keeps compiling still fails this test.
 */
class TestiRegistrazioniTest {
    @Test
    fun `AC-S141 il testo di conferma elimina con trascritto nomina il riassunto`() {
        assertEquals(
            "Verranno cancellati il file audio copiato nel progetto, la trascrizione con le correzioni delle voci, " +
                "i nomi dati alle voci, la sbobinatura, il riassunto e le impronte vocali ricavate da questa " +
                "registrazione. Non si può annullare.",
            MESSAGGIO_CONFERMA_ELIMINA_CON_TRASCRITTO,
        )
    }
}
