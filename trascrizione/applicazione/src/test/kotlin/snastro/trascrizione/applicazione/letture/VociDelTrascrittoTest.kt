package snastro.trascrizione.applicazione.letture

import snastro.kernel.IncontroId
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.SegmentoRef
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.atteso
import snastro.kernel.unIncontroDi
import snastro.trascrizione.applicazione.porte.LettoreRegistrazione
import snastro.trascrizione.applicazione.porte.LettoreRegistrazioneFinta
import snastro.trascrizione.applicazione.porte.ParteDiIncontro
import snastro.trascrizione.applicazione.porte.VociDellIncontroRepository
import snastro.trascrizione.applicazione.porte.VociDellIncontroRepositoryFinta
import snastro.trascrizione.dominio.DURATA_TRASCRITTO_MS
import snastro.trascrizione.dominio.VociDellIncontro
import snastro.trascrizione.dominio.unSegmentoIniziale
import snastro.trascrizione.dominio.unaRadice
import snastro.trascrizione.dominio.unaRadiceDa
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VociDelTrascrittoTest {
    private val trascritti = VociDellIncontroRepositoryFinta()
    private val ordine = mutableMapOf<IncontroId, List<RegistrazioneId>>()
    private val uow = UnitaDiLavoroFinta(trascritti)
    private val api = VociDelTrascritto(trascritti, LettoreRegistrazioneFinta(emptyMap(), ordine), uow)

    /** A one-second turn of diarizer voice [voce] starting at [inizioMs]. */
    private fun turno(voce: Int, inizioMs: Long) = unSegmentoIniziale(voce, inizioMs, inizioMs + 1_000)

    private fun segmentoDi(parte: RegistrazioneId, segmento: Int, voce: Int, ora: Long, confermato: Boolean) =
        SegmentoDiVoceIncontro(
            SegmentoRef(parte, SegmentoId(segmento)),
            VoceId(voce),
            IntervalloMs(ora, ora + 1_000),
            confermato,
        )

    /** An Incontro whose Parti are [parti] in this order (the order Progetto would give), each transcribed. */
    private fun incontroConParti(parti: List<RegistrazioneId>): VociDellIncontro {
        val radice = VociDellIncontro.crea(INCONTRO)
        parti.forEach { r ->
            radice.completaParte(r, listOf(turno(0, 0), turno(1, 1_000)), DURATA_TRASCRITTO_MS).atteso()
        }
        ordine[INCONTRO] = parti
        trascritti.salva(radice)
        return radice
    }

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

    @Test
    fun `AC-I39 una Voce a cavallo di due Parti arriva una volta con gli intervalli di ogni Parte`() {
        val radice = incontroConParti(listOf(PARTE_1, PARTE_2)) // Voci 1,2 in Parte 1 and 3,4 in Parte 2
        radice.unisci(VoceId(1), VoceId(3)).atteso()
        trascritti.salva(radice)

        val voci = api.voci(INCONTRO)!!

        assertEquals(listOf(1, 2, 4), voci.map { it.voceRef.voceId.numero })
        assertEquals(
            VoceIncontroVista(
                VoceRef(INCONTRO, VoceId(1)),
                mapOf(PARTE_1 to listOf(IntervalloMs(0, 1_000)), PARTE_2 to listOf(IntervalloMs(0, 1_000))),
            ),
            voci.first(),
        )
        assertEquals(mapOf(PARTE_1 to listOf(IntervalloMs(1_000, 2_000))), voci[1].intervalliPerParte)
        assertEquals(mapOf(PARTE_2 to listOf(IntervalloMs(1_000, 2_000))), voci[2].intervalliPerParte)
    }

    @Test
    fun `AC-I39 le chiavi per Parte seguono l ordine delle Parti, non quello di trascrizione`() {
        incontroConParti(listOf(PARTE_2, PARTE_1))
        ordine[INCONTRO] = listOf(PARTE_1, PARTE_2)

        assertEquals(listOf(PARTE_1, PARTE_2), api.partiConTrascritto(INCONTRO))
        assertEquals(
            listOf(PARTE_1, PARTE_2),
            api.segmenti(INCONTRO)!!.map { it.segmento.registrazioneId }.distinct(),
        )
    }

    @Test
    fun `AC-I39 segmenti elenca ogni Segmento come SegmentoRef con la Voce dell Incontro e confermato`() {
        val radice = incontroConParti(listOf(PARTE_1, PARTE_2))
        radice.unisci(VoceId(1), VoceId(3)).atteso()
        radice.riassegna(SegmentoRef(PARTE_2, SegmentoId(2)), VoceId(2)).atteso()
        trascritti.salva(radice)

        assertEquals(
            listOf(
                segmentoDi(PARTE_1, 1, 1, 0, false),
                segmentoDi(PARTE_1, 2, 2, 1_000, false),
                segmentoDi(PARTE_2, 1, 1, 0, false),
                segmentoDi(PARTE_2, 2, 2, 1_000, true),
            ),
            api.segmenti(INCONTRO),
        )
    }

    @Test
    fun `AC-I39 senza alcuna Parte trascritta voci e segmenti sono null`() {
        ordine[INCONTRO] = listOf(PARTE_1, PARTE_2)

        assertNull(api.voci(INCONTRO))
        assertNull(api.segmenti(INCONTRO))
        assertNull(api.voci(IncontroId("sconosciuto")))
    }

    @Test
    fun `AC-I40 partiConTrascritto elenca solo le Parti trascritte in ordine di Parte`() {
        val radice = VociDellIncontro.crea(INCONTRO)
        radice.completaParte(PARTE_3, listOf(turno(0, 0)), DURATA_TRASCRITTO_MS).atteso()
        radice.completaParte(PARTE_1, listOf(turno(0, 0)), DURATA_TRASCRITTO_MS).atteso()
        ordine[INCONTRO] = listOf(PARTE_1, PARTE_2, PARTE_3)
        trascritti.salva(radice)

        assertEquals(listOf(PARTE_1, PARTE_3), api.partiConTrascritto(INCONTRO))
        assertEquals(emptyList(), api.partiConTrascritto(IncontroId("sconosciuto")))
    }

    @Test
    fun `AC-I40 trascritto porta l incontroId e il testo della Parte`() {
        incontroConParti(listOf(PARTE_1))

        val trascritto = api.trascritto(PARTE_1)!!

        assertEquals(INCONTRO, trascritto.incontroId)
        assertEquals(PARTE_1, trascritto.registrazioneId)
        assertEquals(2, trascritto.segmenti.size)
        assertNull(api.trascritto(PARTE_2))
    }

    @Test
    fun `AC-I42 ogni lettura per Incontro legge la radice una volta sola`() {
        incontroConParti(listOf(PARTE_1, PARTE_2))
        val contati = ContaTrova(trascritti)
        val api = VociDelTrascritto(contati, LettoreRegistrazioneFinta(emptyMap(), ordine), uow)

        api.voci(INCONTRO)
        api.segmenti(INCONTRO)
        api.partiConTrascritto(INCONTRO)

        assertEquals(3, contati.trova) // one `trova` (one LetturaCoerente snapshot) per call
    }

    @Test
    fun `AC-I42 l ordine delle Parti e la radice sono letti nella stessa lettura coerente`() {
        incontroConParti(listOf(PARTE_1, PARTE_2))
        val dentro = mutableListOf<Boolean>()
        val finta = LettoreRegistrazioneFinta(emptyMap(), ordine)
        val parti = object : LettoreRegistrazione by finta {
            override fun parti(incontroId: IncontroId): List<ParteDiIncontro>? =
                finta.parti(incontroId).also { dentro += uow.letturaAperta }
        }
        val radice = object : VociDellIncontroRepository by trascritti {
            override fun trova(id: IncontroId): VociDellIncontro? =
                trascritti.trova(id).also { dentro += uow.letturaAperta }
        }
        val api = VociDelTrascritto(radice, parti, uow)

        api.voci(INCONTRO)
        api.segmenti(INCONTRO)
        api.partiConTrascritto(INCONTRO)

        assertEquals(List(6) { true }, dentro)
    }

    private class ContaTrova(private val dentro: VociDellIncontroRepository) : VociDellIncontroRepository by dentro {
        var trova = 0
        override fun trova(id: IncontroId): VociDellIncontro? = dentro.trova(id).also { trova++ }
    }

    private companion object {
        val INCONTRO = IncontroId("incontro-1")
        val PARTE_1 = RegistrazioneId("parte-1")
        val PARTE_2 = RegistrazioneId("parte-2")
        val PARTE_3 = RegistrazioneId("parte-3")
        val REGISTRAZIONE = RegistrazioneId("registrazione-1")
        val ALTRA_REGISTRAZIONE = RegistrazioneId("registrazione-2")
    }
}
