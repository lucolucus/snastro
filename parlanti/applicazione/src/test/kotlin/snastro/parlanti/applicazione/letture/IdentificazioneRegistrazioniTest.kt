package snastro.parlanti.applicazione.letture

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

/** [IdentificazioneRegistrazioni] against the ports' fakes (D1): AC-166. */
class IdentificazioneRegistrazioniTest {
    private val attribuzioni = AttribuzioneRepositoryFinta()

    @Test
    fun `AC-166 una Registrazione senza Trascritto non compare`() {
        val api = IdentificazioneRegistrazioni(LettoreVociFinta(emptyMap()), attribuzioni)

        assertEquals(emptyList(), api.conteggi(listOf(REGISTRAZIONE)))
    }

    @Test
    fun `AC-166 numVociDaIdentificare e il numero di Voci meno le Voci attribuite`() {
        val voci = LettoreVociFinta(mapOf(REGISTRAZIONE to listOf(unaVoce(1), unaVoce(2), unaVoce(3))))
        attribuzioni.salva(unAttribuzione(1, ParlanteId("id-1")))
        val api = IdentificazioneRegistrazioni(voci, attribuzioni)

        assertEquals(listOf(ConteggioIdentificazione(REGISTRAZIONE, 2)), api.conteggi(listOf(REGISTRAZIONE)))
    }

    @Test
    fun `AC-166 tutte le Voci attribuite da zero`() {
        val voci = LettoreVociFinta(mapOf(REGISTRAZIONE to listOf(unaVoce(1), unaVoce(2))))
        attribuzioni.salva(unAttribuzione(1, ParlanteId("id-1")))
        attribuzioni.salva(unAttribuzione(2, ParlanteId("id-1")))
        val api = IdentificazioneRegistrazioni(voci, attribuzioni)

        assertEquals(listOf(ConteggioIdentificazione(REGISTRAZIONE, 0)), api.conteggi(listOf(REGISTRAZIONE)))
    }

    @Test
    fun `AC-166 elenca solo le Registrazioni richieste che hanno un Trascritto`() {
        val voci =
            LettoreVociFinta(
                mapOf(
                    REGISTRAZIONE to listOf(unaVoce(1)),
                    ALTRA_REGISTRAZIONE to listOf(unaVoce(1, ALTRA_REGISTRAZIONE), unaVoce(2, ALTRA_REGISTRAZIONE)),
                ),
            )
        val api = IdentificazioneRegistrazioni(voci, attribuzioni)

        assertEquals(
            listOf(
                ConteggioIdentificazione(REGISTRAZIONE, 1),
                ConteggioIdentificazione(ALTRA_REGISTRAZIONE, 2),
            ),
            api.conteggi(listOf(REGISTRAZIONE, SENZA_TRASCRITTO, ALTRA_REGISTRAZIONE)),
        )
    }

    private fun unaVoce(n: Int, registrazioneId: RegistrazioneId = REGISTRAZIONE): VoceVista =
        VoceVista(VoceRef(registrazioneId, VoceId(n)), emptyList())

    private fun unAttribuzione(voceN: Int, parlanteId: ParlanteId): Attribuzione =
        Attribuzione.conferma(VoceRef(REGISTRAZIONE, VoceId(voceN)), PROGETTO, parlanteId).aggregato

    private companion object {
        val PROGETTO = ProgettoId("progetto-1")
        val REGISTRAZIONE = RegistrazioneId("registrazione-1")
        val ALTRA_REGISTRAZIONE = RegistrazioneId("registrazione-2")
        val SENZA_TRASCRITTO = RegistrazioneId("registrazione-3")
    }
}
