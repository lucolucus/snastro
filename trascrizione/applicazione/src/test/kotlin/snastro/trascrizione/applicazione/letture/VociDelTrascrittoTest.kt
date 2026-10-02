package snastro.trascrizione.applicazione.letture

import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.SegmentoRef
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.atteso
import snastro.kernel.unIncontroDi
import snastro.trascrizione.applicazione.porte.VociDellIncontroRepositoryFinta
import snastro.trascrizione.dominio.DURATA_TRASCRITTO_MS
import snastro.trascrizione.dominio.unSegmentoIniziale
import snastro.trascrizione.dominio.unaRadice
import snastro.trascrizione.dominio.unaRadiceDa
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VociDelTrascrittoTest {
    private val trascritti = VociDellIncontroRepositoryFinta()
    private val api = VociDelTrascritto(trascritti)

    @Test
    fun `AC-98 voci restituisce per ogni Voce voceRef e intervalli ordinati per inizio`() {
        trascritti.salva(unaRadice(voci = 2, segmentiPerVoce = 3, registrazioneId = REGISTRAZIONE))

        val voci = api.voci(REGISTRAZIONE)

        assertEquals(
            listOf(
                VoceVista(
                    VoceRef(unIncontroDi(REGISTRAZIONE), VoceId(1)),
                    listOf(IntervalloMs(0, 1_000), IntervalloMs(2_000, 3_000), IntervalloMs(4_000, 5_000)),
                ),
                VoceVista(
                    VoceRef(unIncontroDi(REGISTRAZIONE), VoceId(2)),
                    listOf(IntervalloMs(1_000, 2_000), IntervalloMs(3_000, 4_000), IntervalloMs(5_000, 6_000)),
                ),
            ),
            voci,
        )
    }

    @Test
    fun `AC-98 voci senza Trascritto restituisce null`() {
        assertNull(api.voci(REGISTRAZIONE))
    }

    @Test
    fun `AC-99 segmenti restituisce segmentoId, voceId, intervallo e testo ordinati per inizio e segmentoId`() {
        val radice = unaRadiceDa(
            REGISTRAZIONE,
            unIncontroDi(REGISTRAZIONE),
            DURATA_TRASCRITTO_MS,
            listOf(
                unSegmentoIniziale(voceIndice = 4, inizioMs = 0, fineMs = 3_000, testo = "si parla sopra"),
                unSegmentoIniziale(voceIndice = 2, inizioMs = 0, fineMs = 1_500, testo = "insieme"),
                unSegmentoIniziale(voceIndice = 4, inizioMs = 2_000, fineMs = 4_000, testo = "perché \"sì\""),
            ),
        )
        trascritti.salva(radice)

        val segmenti = api.segmenti(REGISTRAZIONE)

        assertEquals(
            listOf(
                SegmentoVista(SegmentoId(1), VoceId(1), IntervalloMs(0, 1_500), "insieme"),
                SegmentoVista(SegmentoId(2), VoceId(2), IntervalloMs(0, 3_000), "si parla sopra"),
                SegmentoVista(SegmentoId(3), VoceId(2), IntervalloMs(2_000, 4_000), "perché \"sì\""),
            ),
            segmenti,
        )
    }

    @Test
    fun `AC-99 segmenti senza Trascritto restituisce null`() {
        assertNull(api.segmenti(REGISTRAZIONE))
    }

    @Test
    fun `AC-550 segmentiDiVoce restituisce ogni Segmento una volta ordinato con confermato e senza testo`() {
        val radice = unaRadiceDa(
            REGISTRAZIONE,
            unIncontroDi(REGISTRAZIONE),
            DURATA_TRASCRITTO_MS,
            listOf(
                unSegmentoIniziale(voceIndice = 4, inizioMs = 0, fineMs = 3_000),
                unSegmentoIniziale(voceIndice = 2, inizioMs = 0, fineMs = 1_500),
                unSegmentoIniziale(voceIndice = 4, inizioMs = 2_000, fineMs = 4_000),
                unSegmentoIniziale(voceIndice = 2, inizioMs = 5_000, fineMs = 6_000),
            ),
        )
        radice.riassegna(SegmentoRef(REGISTRAZIONE, SegmentoId(3)), VoceId(1)).atteso() // a manual move: confermato
        trascritti.salva(radice)

        assertEquals(
            listOf(
                SegmentoDiVoceVista(SegmentoId(1), VoceId(1), IntervalloMs(0, 1_500), confermato = false),
                SegmentoDiVoceVista(SegmentoId(2), VoceId(2), IntervalloMs(0, 3_000), confermato = false),
                SegmentoDiVoceVista(SegmentoId(3), VoceId(1), IntervalloMs(2_000, 4_000), confermato = true),
                SegmentoDiVoceVista(SegmentoId(4), VoceId(1), IntervalloMs(5_000, 6_000), confermato = false),
            ),
            api.segmentiDiVoce(REGISTRAZIONE),
        )
    }

    @Test
    fun `AC-550 segmentiDiVoce senza Trascritto restituisce null e il tipo non ha testo`() {
        assertNull(api.segmentiDiVoce(REGISTRAZIONE))
        assertEquals(
            setOf("segmentoId", "voceId", "intervallo", "confermato"),
            SegmentoDiVoceVista::class.java.declaredFields.map { it.name }.toSet(),
        )
    }

    @Test
    fun `AC-100 registrazioniConTrascritto elenca solo le Registrazioni con un Trascritto`() {
        trascritti.salva(unaRadice(voci = 1, segmentiPerVoce = 1, registrazioneId = REGISTRAZIONE))
        trascritti.salva(unaRadice(voci = 1, segmentiPerVoce = 1, registrazioneId = ALTRA_REGISTRAZIONE))

        assertEquals(setOf(REGISTRAZIONE, ALTRA_REGISTRAZIONE), api.registrazioniConTrascritto().toSet())
        assertEquals(2, api.registrazioniConTrascritto().size)
    }

    @Test
    fun `AC-100 registrazioniConTrascritto vuoto senza alcun Trascritto`() {
        assertEquals(emptyList(), api.registrazioniConTrascritto())
    }

    private companion object {
        val REGISTRAZIONE = RegistrazioneId("registrazione-1")
        val ALTRA_REGISTRAZIONE = RegistrazioneId("registrazione-2")
    }
}
