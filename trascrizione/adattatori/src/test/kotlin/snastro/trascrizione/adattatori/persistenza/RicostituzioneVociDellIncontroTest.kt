package snastro.trascrizione.adattatori.persistenza

import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RicostituzioneDaPersistenza
import snastro.kernel.atteso
import snastro.trascrizione.dominio.DURATA_TRASCRITTO_MS
import snastro.trascrizione.dominio.Trascritto
import snastro.trascrizione.dominio.VociDellIncontro
import snastro.trascrizione.dominio.unSegmentoIniziale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The `require` guards of [VociDellIncontro.ricostituisci] (INV-I4), testable only here (CR-15): what the adapter reads
 * back must never let the root mint an existing `VoceId`, or `VoceId(0)`.
 */
@OptIn(RicostituzioneDaPersistenza::class)
class RicostituzioneVociDellIncontroTest {
    private val incontro = IncontroId("incontro-1")
    private val a = RegistrazioneId("parte-a")

    /** Parte [a] of [incontroId] with Voce 1 and Voce 2: the stored counter must be at least 3. */
    private fun parteA(incontroId: IncontroId = incontro): Trascritto = VociDellIncontro.crea(incontroId).run {
        completaParte(a, listOf(unSegmentoIniziale(0, 0), unSegmentoIniziale(1, 1_000)), DURATA_TRASCRITTO_MS).atteso()
        trascritti.single()
    }

    @Test
    fun `INV-I4 un contatore sotto 1 e rifiutato anche senza Trascritti`() {
        assertFailsWith<IllegalArgumentException> { VociDellIncontro.ricostituisci(incontro, emptyList(), 0) }
        assertEquals(1, VociDellIncontro.ricostituisci(incontro, emptyList(), 1).prossimaVoce)
    }

    @Test
    fun `INV-I4 un contatore non oltre ogni Voce salvata e rifiutato`() {
        assertFailsWith<IllegalArgumentException> { VociDellIncontro.ricostituisci(incontro, listOf(parteA()), 2) }
        assertEquals(3, VociDellIncontro.ricostituisci(incontro, listOf(parteA()), 3).prossimaVoce)
    }

    @Test
    fun `un Trascritto di un altro Incontro o una Parte ripetuta sono rifiutati`() {
        val altro = parteA(IncontroId("incontro-2"))

        assertFailsWith<IllegalArgumentException> { VociDellIncontro.ricostituisci(incontro, listOf(altro), 3) }
        assertFailsWith<IllegalArgumentException> {
            VociDellIncontro.ricostituisci(incontro, listOf(parteA(), parteA()), 3)
        }
    }
}
