package snastro.progetto.applicazione.letture

import snastro.kernel.ProgettoId
import snastro.progetto.applicazione.porte.RegistroProgettiFinta
import snastro.progetto.applicazione.porte.VoceRegistro
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class ElencoProgettiTest {
    private val registro = RegistroProgettiFinta()
    private val elenco = ElencoProgetti(registro)

    @Test
    fun `AC-159 espone progettoId, nome, percorso, numRegistrazioni e ultimaAttivita di ogni progetto del registro`() {
        registro.registra(unaVoce())

        assertEquals(
            listOf(
                ProgettoVista(
                    progettoId = ProgettoId("id-1"),
                    nome = "Consiglio comunale",
                    percorso = "/progetti/Consiglio comunale.snastro",
                    numRegistrazioni = 3,
                    ultimaAttivita = ORA,
                ),
            ),
            elenco.progetti(),
        )
    }

    @Test
    fun `AC-159 i progetti sono esposti dal piu recente, indipendentemente dall'ordine di registrazione`() {
        // Registration order (mid, new, old) matches neither the expected order (new, mid, old) nor
        // its reverse: an implementation trusting insertion/reversed order would fail this.
        val mid = unaVoce(ProgettoId("id-mid"), "Medio", "/progetti/Medio.snastro", ORA.minusSeconds(60))
        val new = unaVoce(ProgettoId("id-new"), "Nuovo", "/progetti/Nuovo.snastro", ORA)
        val old = unaVoce(ProgettoId("id-old"), "Vecchio", "/progetti/Vecchio.snastro", ORA.minusSeconds(3_600))
        listOf(mid, new, old).forEach(registro::registra)

        assertEquals(
            listOf(ProgettoId("id-new"), ProgettoId("id-mid"), ProgettoId("id-old")),
            elenco.progetti().map { it.progettoId },
        )
    }

    @Test
    fun `AC-160 registro vuoto restituisce lista vuota`() {
        assertEquals(emptyList(), elenco.progetti())
    }

    private fun unaVoce(
        progettoId: ProgettoId = ProgettoId("id-1"),
        nome: String = "Consiglio comunale",
        percorso: String = "/progetti/Consiglio comunale.snastro",
        ultimaAttivita: Instant = ORA,
    ): VoceRegistro = VoceRegistro(progettoId, nome, percorso, numRegistrazioni = 3, ultimaAttivita = ultimaAttivita)

    private companion object {
        val ORA: Instant = Instant.parse("2026-09-23T10:15:30Z")
    }
}
