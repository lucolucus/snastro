package snastro.parlanti.applicazione.letture

import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.unIncontroDi
import snastro.parlanti.applicazione.porte.AttribuzioneRepositoryFinta
import snastro.parlanti.applicazione.porte.LettoreVociFinta
import snastro.parlanti.applicazione.porte.VoceVista
import snastro.parlanti.applicazione.porte.lettoreVociDiUnicheParti
import snastro.parlanti.applicazione.porte.ogniRegistrazioneNota
import snastro.parlanti.applicazione.porte.unaVoceVista
import snastro.parlanti.dominio.Attribuzione
import kotlin.test.Test
import kotlin.test.assertEquals

/** [IdentificazioneRegistrazioni] against the ports' fakes (D1): AC-166. */
class IdentificazioneRegistrazioniTest {
    private val attribuzioni = AttribuzioneRepositoryFinta()

    @Test
    fun `AC-166 una Registrazione senza Trascritto non compare`() {
        val api = IdentificazioneRegistrazioni(LettoreVociFinta(), attribuzioni, ogniRegistrazioneNota())

        assertEquals(emptyList(), api.conteggi(listOf(REGISTRAZIONE)))
    }

    @Test
    fun `AC-166 numVociDaIdentificare e il numero di Voci meno le Voci attribuite`() {
        val voci = lettoreVociDiUnicheParti(mapOf(REGISTRAZIONE to listOf(unaVoce(1), unaVoce(2), unaVoce(3))))
        attribuzioni.salva(unAttribuzione(1, ParlanteId("id-1")))
        val api = IdentificazioneRegistrazioni(voci, attribuzioni, ogniRegistrazioneNota())

        assertEquals(listOf(ConteggioIdentificazione(REGISTRAZIONE, 2)), api.conteggi(listOf(REGISTRAZIONE)))
    }

    @Test
    fun `AC-166 tutte le Voci attribuite da zero`() {
        val voci = lettoreVociDiUnicheParti(mapOf(REGISTRAZIONE to listOf(unaVoce(1), unaVoce(2))))
        attribuzioni.salva(unAttribuzione(1, ParlanteId("id-1")))
        attribuzioni.salva(unAttribuzione(2, ParlanteId("id-1")))
        val api = IdentificazioneRegistrazioni(voci, attribuzioni, ogniRegistrazioneNota())

        assertEquals(listOf(ConteggioIdentificazione(REGISTRAZIONE, 0)), api.conteggi(listOf(REGISTRAZIONE)))
    }

    @Test
    fun `AC-166 elenca solo le Registrazioni richieste che hanno un Trascritto`() {
        val voci =
            lettoreVociDiUnicheParti(
                mapOf(
                    REGISTRAZIONE to listOf(unaVoce(1)),
                    ALTRA_REGISTRAZIONE to listOf(unaVoce(1, ALTRA_REGISTRAZIONE), unaVoce(2, ALTRA_REGISTRAZIONE)),
                ),
            )
        val api = IdentificazioneRegistrazioni(voci, attribuzioni, ogniRegistrazioneNota())

        assertEquals(
            listOf(
                ConteggioIdentificazione(REGISTRAZIONE, 1),
                ConteggioIdentificazione(ALTRA_REGISTRAZIONE, 2),
            ),
            api.conteggi(listOf(REGISTRAZIONE, SENZA_TRASCRITTO, ALTRA_REGISTRAZIONE)),
        )
    }

    private fun unaVoce(n: Int, registrazioneId: RegistrazioneId = REGISTRAZIONE): VoceVista =
        unaVoceVista(VoceRef(unIncontroDi(registrazioneId), VoceId(n)), emptyList())

    private fun unAttribuzione(voceN: Int, parlanteId: ParlanteId): Attribuzione =
        Attribuzione.conferma(VoceRef(unIncontroDi(REGISTRAZIONE), VoceId(voceN)), PROGETTO, parlanteId).aggregato

    private companion object {
        val PROGETTO = ProgettoId("progetto-1")
        val REGISTRAZIONE = RegistrazioneId("registrazione-1")
        val ALTRA_REGISTRAZIONE = RegistrazioneId("registrazione-2")
        val SENZA_TRASCRITTO = RegistrazioneId("registrazione-3")
    }
}
