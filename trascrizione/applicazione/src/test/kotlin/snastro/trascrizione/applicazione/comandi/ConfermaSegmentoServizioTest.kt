package snastro.trascrizione.applicazione.comandi

import snastro.kernel.DispatcherEventiFinta
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.SegmentoRef
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.kernel.unIncontroDi
import snastro.trascrizione.applicazione.eventi.SegmentoConfermato
import snastro.trascrizione.applicazione.porte.VociDellIncontroRepositoryFinta
import snastro.trascrizione.applicazione.porte.ogniRegistrazioneNota
import snastro.trascrizione.dominio.ErroreTrascrizione
import snastro.trascrizione.dominio.unTrascritto
import snastro.trascrizione.dominio.unaRadice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ConfermaSegmentoServizioTest {
    private val trascritti = VociDellIncontroRepositoryFinta()
    private val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(trascritti))
    private val servizio = ConfermaSegmentoServizio(eventi.unitaDiLavoro, trascritti, ogniRegistrazioneNota(), eventi)

    @Test
    fun `AC-517 ConfermaSegmento imposta e revoca il flag e pubblica SegmentoConfermato una volta per cambio`() {
        trascritti.salva(unaRadice(voci = 2, segmentiPerVoce = 2, registrazioneId = REGISTRAZIONE))

        servizio.esegui(ConfermaSegmento(REGISTRAZIONE, SegmentoId(2), confermato = true)).atteso()
        assertEquals(listOf(SegmentoId(2)), confermati())
        servizio.esegui(ConfermaSegmento(REGISTRAZIONE, SegmentoId(2), confermato = false)).atteso()
        assertEquals(emptyList(), confermati())

        assertEquals(
            listOf(
                SegmentoConfermato(INCONTRO, SegmentoRef(REGISTRAZIONE, SegmentoId(2)), confermato = true),
                SegmentoConfermato(INCONTRO, SegmentoRef(REGISTRAZIONE, SegmentoId(2)), confermato = false),
            ),
            eventi.pubblicati,
        )
    }

    @Test
    fun `AC-517 ConfermaSegmento con lo stesso valore non scrive e non pubblica nulla`() {
        val originale = unTrascritto(voci = 2, segmentiPerVoce = 2, registrazioneId = REGISTRAZIONE)
        trascritti.salva(unaRadice(voci = 2, segmentiPerVoce = 2, registrazioneId = REGISTRAZIONE))

        servizio.esegui(ConfermaSegmento(REGISTRAZIONE, SegmentoId(1), confermato = false)).atteso()

        assertEquals(
            originale.segmenti,
            assertNotNull(trascritti.trascritto(REGISTRAZIONE)).segmenti,
        )
        assertEquals(emptyList(), eventi.pubblicati)
    }

    @Test
    fun `AC-517 senza Trascritto restituisce TrascrittoNonTrovato`() {
        val errore = servizio.esegui(ConfermaSegmento(REGISTRAZIONE, SegmentoId(1), confermato = true))
            .erroreAtteso<ErroreTrascrizione.TrascrittoNonTrovato>()

        assertEquals(ErroreTrascrizione.TrascrittoNonTrovato(REGISTRAZIONE), errore)
        assertEquals(emptyList(), eventi.pubblicati)
    }

    @Test
    fun `AC-517 un Segmento sconosciuto restituisce SegmentoNonTrovato e non scrive nulla`() {
        val originale = unTrascritto(voci = 2, segmentiPerVoce = 2, registrazioneId = REGISTRAZIONE)
        trascritti.salva(unaRadice(voci = 2, segmentiPerVoce = 2, registrazioneId = REGISTRAZIONE))

        val errore = servizio.esegui(ConfermaSegmento(REGISTRAZIONE, SegmentoId(99), confermato = true))
            .erroreAtteso<ErroreTrascrizione.SegmentoNonTrovato>()

        assertEquals(ErroreTrascrizione.SegmentoNonTrovato(SegmentoRef(REGISTRAZIONE, SegmentoId(99))), errore)
        assertEquals(
            originale.segmenti,
            assertNotNull(trascritti.trascritto(REGISTRAZIONE)).segmenti,
        )
        assertEquals(emptyList(), eventi.pubblicati)
    }

    private fun confermati(): List<SegmentoId> =
        assertNotNull(
            trascritti.trascritto(REGISTRAZIONE),
        ).segmenti.filter { it.confermato }.map { it.id }

    private companion object {
        val REGISTRAZIONE = RegistrazioneId("registrazione-1")
        val INCONTRO = unIncontroDi(REGISTRAZIONE)
    }
}
