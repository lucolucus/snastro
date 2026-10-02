package snastro.trascrizione.applicazione.letture

import snastro.kernel.ElaborazioneId
import snastro.kernel.IncontroId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.kernel.atteso
import snastro.trascrizione.applicazione.porte.ElaborazioneRepositoryFinta
import snastro.trascrizione.applicazione.porte.LettoreRegistrazioneFinta
import snastro.trascrizione.applicazione.porte.RegistrazioneVista
import snastro.trascrizione.dominio.NumeroPersone
import snastro.trascrizione.dominio.StatoElaborazione
import snastro.trascrizione.dominio.unaElaborazione
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NumeroPersoneDellIncontroTest {
    private val elaborazioni = ElaborazioneRepositoryFinta()
    private val lettura = NumeroPersoneDellIncontro(
        LettoreRegistrazioneFinta(listOf(A, B).associateWith { vista(it) }),
        elaborazioni,
    )

    private fun salva(id: String, r: RegistrazioneId, alle: Int, n: Int?) {
        elaborazioni.salva(
            unaElaborazione(
                StatoElaborazione.COMPLETATA,
                ElaborazioneId(id),
                r,
                creataAlle = T.plusSeconds(alle.toLong()),
                numeroPersone = n?.let { NumeroPersone.di(it).atteso() },
            ),
        ).atteso()
    }

    @Test
    fun `AC-I34 e il numeroPersone dell Elaborazione piu recente su tutte le Parti`() {
        salva("e1", A, 1, 2)
        salva("e2", B, 3, 4)
        salva("e3", A, 2, 3)

        assertEquals(4, lettura.numeroPersonePrecompilato(INCONTRO))
    }

    @Test
    fun `AC-I34 a parita di creataAlle decide l id`() {
        salva("e1", A, 1, 2)
        salva("e2", B, 1, 5)

        assertEquals(5, lettura.numeroPersonePrecompilato(INCONTRO))
    }

    @Test
    fun `AC-I34 e null senza Elaborazioni o quando l ultima non aveva numero`() {
        assertNull(lettura.numeroPersonePrecompilato(INCONTRO))
        assertNull(lettura.numeroPersonePrecompilato(IncontroId("ignoto")))

        salva("e1", A, 1, 3)
        salva("e2", B, 2, null)

        assertNull(lettura.numeroPersonePrecompilato(INCONTRO))
    }

    private companion object {
        val A = RegistrazioneId("parte-A")
        val B = RegistrazioneId("parte-B")
        val INCONTRO = IncontroId("incontro-1")
        val T: Instant = Instant.parse("2026-10-02T10:00:00Z")

        fun vista(id: RegistrazioneId) = RegistrazioneVista(
            registrazioneId = id,
            progettoId = ProgettoId("p"),
            incontroId = INCONTRO,
            titolo = "Riunione",
            riferimentoAudio = RiferimentoAudio("a/${id.valore}.m4a"),
            dataRegistrazione = LocalDate.of(2026, 10, 2),
            durataMs = 10_000,
        )
    }
}
