package snastro.parlanti.applicazione.letture

import snastro.kernel.IncontroId
import snastro.kernel.IntervalloMs
import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.parlanti.applicazione.porte.AttribuzioneRepositoryFinta
import snastro.parlanti.applicazione.porte.LettoreVociFinta
import snastro.parlanti.applicazione.porte.VoceVista
import snastro.parlanti.dominio.Attribuzione
import kotlin.test.Test
import kotlin.test.assertEquals

/** [IdentificazioneIncontri] against the ports' fakes (D1): AC-I47 (`viste-parlanti-incontro`). */
class IdentificazioneIncontriTest {
    private val attribuzioni = AttribuzioneRepositoryFinta()

    @Test
    fun `AC-I47 conta le Voci dell Incontro su tutte le parti e quelle non attribuite`() {
        // Voce 1 parla nelle parti 1 e 2 (conta una volta), Voce 2 nella parte 1, Voce 5 nella parte 2.
        val voci = LettoreVociFinta(
            mapOf(
                INCONTRO to listOf(
                    unaVoce(1, PARTE_1, PARTE_2),
                    unaVoce(2, PARTE_1),
                    unaVoce(5, PARTE_2),
                ),
            ),
        )
        attribuzioni.salva(Attribuzione.conferma(VoceRef(INCONTRO, VoceId(5)), PROGETTO, ParlanteId("anna")).aggregato)
        val api = IdentificazioneIncontri(voci, attribuzioni)

        assertEquals(mapOf(INCONTRO to IdentificazioneIncontro(3, 2)), api.conteggi(listOf(INCONTRO)))
    }

    @Test
    fun `AC-I47 un Incontro senza Trascritto non compare, ogni Incontro richiesto con Trascritto si`() {
        val voci = LettoreVociFinta(
            mapOf(INCONTRO to listOf(unaVoce(1, PARTE_1)), ALTRO to listOf(unaVoce(1, PARTE_3))),
        )
        attribuzioni.salva(Attribuzione.conferma(VoceRef(ALTRO, VoceId(1)), PROGETTO, ParlanteId("anna")).aggregato)
        val api = IdentificazioneIncontri(voci, attribuzioni)

        val conteggi = api.conteggi(listOf(ALTRO, IncontroId("senza-trascritto"), INCONTRO))

        val attesi = mapOf(ALTRO to IdentificazioneIncontro(1, 0), INCONTRO to IdentificazioneIncontro(1, 1))
        assertEquals(attesi, conteggi)
    }

    private fun unaVoce(n: Int, vararg parti: String): VoceVista {
        val incontro = if (PARTE_3 in parti) ALTRO else INCONTRO
        val intervalli = parti.associate { RegistrazioneId(it) to listOf(IntervalloMs(0, 1_000)) }
        return VoceVista(VoceRef(incontro, VoceId(n)), intervalli)
    }

    private companion object {
        val PROGETTO = ProgettoId("progetto-1")
        val INCONTRO = IncontroId("incontro-1")
        val ALTRO = IncontroId("incontro-2")
        const val PARTE_1 = "parte-1"
        const val PARTE_2 = "parte-2"
        const val PARTE_3 = "parte-3"
    }
}
