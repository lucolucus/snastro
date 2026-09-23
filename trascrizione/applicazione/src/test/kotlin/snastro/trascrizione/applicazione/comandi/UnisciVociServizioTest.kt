package snastro.trascrizione.applicazione.comandi

import snastro.kernel.DispatcherEventiFinta
import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.VoceId
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.trascrizione.applicazione.eventi.VociUnite
import snastro.trascrizione.applicazione.porte.TrascrittoRepositoryFinta
import snastro.trascrizione.dominio.ErroreTrascrizione
import snastro.trascrizione.dominio.unTrascritto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class UnisciVociServizioTest {
    private val trascritti = TrascrittoRepositoryFinta()
    private val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(trascritti))
    private val servizio = UnisciVociServizio(eventi.unitaDiLavoro, trascritti, eventi)

    @Test
    fun `AC-76 UnisciVoci valido sposta i Segmenti su sopravvive e pubblica VociUnite esattamente una volta`() {
        trascritti.salva(unTrascritto(voci = 2, segmentiPerVoce = 3, registrazioneId = REGISTRAZIONE))

        servizio.esegui(UnisciVoci(REGISTRAZIONE, sopravvive = VoceId(1), rimossa = VoceId(2))).atteso()

        val trascritto = assertNotNull(trascritti.trova(REGISTRAZIONE))
        assertEquals(listOf(VoceId(1)), trascritto.voci.map { it.id })
        assertEquals(
            listOf(VociUnite(REGISTRAZIONE, sopravvissuta = VoceId(1), rimossa = VoceId(2))),
            eventi.pubblicati,
        )
    }

    @Test
    fun `AC-77 UnisciVoci(A, A) restituisce UnioneNonAmmessa e non pubblica nulla`() {
        trascritti.salva(unTrascritto(voci = 2, segmentiPerVoce = 3, registrazioneId = REGISTRAZIONE))

        val errore = servizio.esegui(UnisciVoci(REGISTRAZIONE, sopravvive = VoceId(1), rimossa = VoceId(1)))
            .erroreAtteso<ErroreTrascrizione.UnioneNonAmmessa>()

        assertEquals(ErroreTrascrizione.UnioneNonAmmessa(VoceId(1), VoceId(1)), errore)
        assertEquals(emptyList(), eventi.pubblicati)
    }

    @Test
    fun `AC-82 su una Registrazione senza Trascritto restituisce TrascrittoNonTrovato`() {
        val errore = servizio.esegui(UnisciVoci(REGISTRAZIONE, sopravvive = VoceId(1), rimossa = VoceId(2)))
            .erroreAtteso<ErroreTrascrizione.TrascrittoNonTrovato>()

        assertEquals(ErroreTrascrizione.TrascrittoNonTrovato(REGISTRAZIONE), errore)
        assertEquals(emptyList(), eventi.pubblicati)
    }

    @Test
    fun `AC-83 un abbonato sincrono che restituisce Errore annulla la Revisione e il Trascritto resta invariato`() {
        val originale = unTrascritto(voci = 2, segmentiPerVoce = 3, registrazioneId = REGISTRAZIONE)
        trascritti.salva(originale)
        eventi.registraSincrono { Esito.Errore(ErroreTrascrizione.VoceNonTrovata(VoceId(99))) }

        val errore = servizio.esegui(UnisciVoci(REGISTRAZIONE, sopravvive = VoceId(1), rimossa = VoceId(2)))
            .erroreAtteso<ErroreTrascrizione.VoceNonTrovata>()

        assertEquals(VoceId(99), errore.voceId)
        assertEquals(originale.segmenti, assertNotNull(trascritti.trova(REGISTRAZIONE)).segmenti)
        assertEquals(emptyList(), eventi.pubblicati)
    }

    private companion object {
        val REGISTRAZIONE = RegistrazioneId("registrazione-1")
    }
}
