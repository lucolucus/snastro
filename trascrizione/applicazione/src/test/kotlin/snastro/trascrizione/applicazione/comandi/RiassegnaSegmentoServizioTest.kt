package snastro.trascrizione.applicazione.comandi

import snastro.kernel.DispatcherEventiFinta
import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.VoceId
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.trascrizione.applicazione.eventi.SegmentoRiassegnato
import snastro.trascrizione.applicazione.porte.TrascrittoRepositoryFinta
import snastro.trascrizione.dominio.ErroreTrascrizione
import snastro.trascrizione.dominio.unTrascritto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RiassegnaSegmentoServizioTest {
    private val trascritti = TrascrittoRepositoryFinta()
    private val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(trascritti))
    private val servizio = RiassegnaSegmentoServizio(eventi.unitaDiLavoro, trascritti, eventi)

    @Test
    fun `AC-80 RiassegnaSegmento valido pubblica SegmentoRiassegnato con daRimossa e aNuova corretti`() {
        // V1: S1, S3, S5 (unTrascritto, voci=2, segmentiPerVoce=3): S3 moves to a NEW Voce, V1 keeps S1 and S5.
        trascritti.salva(unTrascritto(voci = 2, segmentiPerVoce = 3, registrazioneId = REGISTRAZIONE))

        servizio.esegui(RiassegnaSegmento(REGISTRAZIONE, segmento = SegmentoId(3), destinazione = null)).atteso()

        val trascritto = assertNotNull(trascritti.trova(REGISTRAZIONE))
        val restanti = trascritto.voci.first { it.id == VoceId(1) }.segmenti.map { it.id }
        assertEquals(listOf(SegmentoId(1), SegmentoId(5)), restanti)
        assertEquals(
            listOf(
                SegmentoRiassegnato(
                    REGISTRAZIONE,
                    segmentoId = SegmentoId(3),
                    da = VoceId(1),
                    a = VoceId(3),
                    daRimossa = false,
                    aNuova = true,
                ),
            ),
            eventi.pubblicati,
        )
    }

    @Test
    fun `AC-81 RiassegnaSegmento verso la Voce di cui gia fa parte e rifiutato e non pubblica nulla`() {
        trascritti.salva(unTrascritto(voci = 2, segmentiPerVoce = 3, registrazioneId = REGISTRAZIONE))
        val comando = RiassegnaSegmento(REGISTRAZIONE, segmento = SegmentoId(1), destinazione = VoceId(1))

        val errore = servizio.esegui(comando).erroreAtteso<ErroreTrascrizione.RiassegnazioneNonAmmessa>()

        assertEquals(ErroreTrascrizione.RiassegnazioneNonAmmessa(SegmentoId(1), VoceId(1)), errore)
        assertEquals(emptyList(), eventi.pubblicati)
    }

    @Test
    fun `AC-82 su una Registrazione senza Trascritto restituisce TrascrittoNonTrovato`() {
        val errore = servizio.esegui(RiassegnaSegmento(REGISTRAZIONE, segmento = SegmentoId(1), destinazione = null))
            .erroreAtteso<ErroreTrascrizione.TrascrittoNonTrovato>()

        assertEquals(ErroreTrascrizione.TrascrittoNonTrovato(REGISTRAZIONE), errore)
        assertEquals(emptyList(), eventi.pubblicati)
    }

    @Test
    fun `AC-83 un abbonato sincrono che restituisce Errore annulla la Revisione e il Trascritto resta invariato`() {
        val originale = unTrascritto(voci = 2, segmentiPerVoce = 3, registrazioneId = REGISTRAZIONE)
        trascritti.salva(originale)
        eventi.registraSincrono { Esito.Errore(ErroreTrascrizione.VoceNonTrovata(VoceId(99))) }

        val errore = servizio.esegui(RiassegnaSegmento(REGISTRAZIONE, segmento = SegmentoId(3), destinazione = null))
            .erroreAtteso<ErroreTrascrizione.VoceNonTrovata>()

        assertEquals(VoceId(99), errore.voceId)
        assertEquals(originale.segmenti, assertNotNull(trascritti.trova(REGISTRAZIONE)).segmenti)
        assertEquals(emptyList(), eventi.pubblicati)
    }

    private companion object {
        val REGISTRAZIONE = RegistrazioneId("registrazione-1")
    }
}
