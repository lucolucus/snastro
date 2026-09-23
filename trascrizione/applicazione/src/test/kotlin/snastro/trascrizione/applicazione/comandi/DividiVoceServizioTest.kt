package snastro.trascrizione.applicazione.comandi

import snastro.kernel.DispatcherEventiFinta
import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.VoceId
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.trascrizione.applicazione.eventi.VoceDivisa
import snastro.trascrizione.applicazione.porte.TrascrittoRepositoryFinta
import snastro.trascrizione.dominio.ErroreTrascrizione
import snastro.trascrizione.dominio.unTrascritto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DividiVoceServizioTest {
    private val trascritti = TrascrittoRepositoryFinta()
    private val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(trascritti))
    private val servizio = DividiVoceServizio(eventi.unitaDiLavoro, trascritti, eventi)

    @Test
    fun `AC-78 DividiVoce valido pubblica VoceDivisa con i segmenti spostati in ordine deterministico`() {
        // V1: S1, S3, S5 (unTrascritto, voci=2, segmentiPerVoce=3)
        trascritti.salva(unTrascritto(voci = 2, segmentiPerVoce = 3, registrazioneId = REGISTRAZIONE))

        // The input Set is handed in reverse of INV-7 order (inizio, id): the event must still list S3 before S5.
        val comando = DividiVoce(REGISTRAZIONE, origine = VoceId(1), segmenti = setOf(SegmentoId(5), SegmentoId(3)))
        servizio.esegui(comando).atteso()

        val trascritto = assertNotNull(trascritti.trova(REGISTRAZIONE))
        assertEquals(listOf(SegmentoId(1)), trascritto.voci.first { it.id == VoceId(1) }.segmenti.map { it.id })
        val spostati = listOf(SegmentoId(3), SegmentoId(5))
        val atteso = VoceDivisa(REGISTRAZIONE, origine = VoceId(1), nuova = VoceId(3), spostati)
        assertEquals(listOf(atteso), eventi.pubblicati)
    }

    @Test
    fun `AC-79 DividiVoce con l intera Voce restituisce DivisioneNonAmmessa e non pubblica nulla`() {
        trascritti.salva(unTrascritto(voci = 2, segmentiPerVoce = 3, registrazioneId = REGISTRAZIONE))
        val tutti = setOf(SegmentoId(1), SegmentoId(3), SegmentoId(5))

        val errore = servizio.esegui(DividiVoce(REGISTRAZIONE, origine = VoceId(1), segmenti = tutti))
            .erroreAtteso<ErroreTrascrizione.DivisioneNonAmmessa>()

        assertEquals(
            ErroreTrascrizione.DivisioneNonAmmessa(VoceId(1), setOf(SegmentoId(1), SegmentoId(3), SegmentoId(5))),
            errore,
        )
        assertEquals(emptyList(), eventi.pubblicati)
    }

    @Test
    fun `AC-82 su una Registrazione senza Trascritto restituisce TrascrittoNonTrovato`() {
        val errore = servizio.esegui(DividiVoce(REGISTRAZIONE, origine = VoceId(1), segmenti = setOf(SegmentoId(1))))
            .erroreAtteso<ErroreTrascrizione.TrascrittoNonTrovato>()

        assertEquals(ErroreTrascrizione.TrascrittoNonTrovato(REGISTRAZIONE), errore)
        assertEquals(emptyList(), eventi.pubblicati)
    }

    @Test
    fun `AC-83 un abbonato sincrono che restituisce Errore annulla la Revisione e il Trascritto resta invariato`() {
        val originale = unTrascritto(voci = 2, segmentiPerVoce = 3, registrazioneId = REGISTRAZIONE)
        trascritti.salva(originale)
        eventi.registraSincrono { Esito.Errore(ErroreTrascrizione.VoceNonTrovata(VoceId(99))) }

        val errore = servizio.esegui(DividiVoce(REGISTRAZIONE, origine = VoceId(1), segmenti = setOf(SegmentoId(3))))
            .erroreAtteso<ErroreTrascrizione.VoceNonTrovata>()

        assertEquals(VoceId(99), errore.voceId)
        assertEquals(originale.segmenti, assertNotNull(trascritti.trova(REGISTRAZIONE)).segmenti)
        assertEquals(emptyList(), eventi.pubblicati)
    }

    private companion object {
        val REGISTRAZIONE = RegistrazioneId("registrazione-1")
    }
}
