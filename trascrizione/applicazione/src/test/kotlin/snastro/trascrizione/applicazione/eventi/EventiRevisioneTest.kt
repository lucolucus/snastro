package snastro.trascrizione.applicazione.eventi

import snastro.kernel.EventoPubblicato
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import kotlin.test.Test
import kotlin.test.assertEquals

/** Boundary `eventi-revisione` (building-blocks.yaml): AC-14 shape of each event, AC-15 CR-5. */
class EventiRevisioneTest {
    private val registrazione = RegistrazioneId("id-1")

    @Test
    fun `AC-14 VociUnite ha registrazioneId, sopravvissuta e rimossa`() {
        val evento: EventoPubblicato =
            VociUnite(registrazioneId = registrazione, sopravvissuta = VoceId(1), rimossa = VoceId(2))
        assertEquals(VociUnite(RegistrazioneId("id-1"), VoceId(1), VoceId(2)), evento)
        assertEquals(
            listOf("registrazioneId: RegistrazioneId", "sopravvissuta: VoceId", "rimossa: VoceId"),
            FormaEventi.di("VociUnite"),
        )
    }

    @Test
    fun `AC-14 VoceDivisa ha registrazioneId, origine, nuova e segmentiSpostati`() {
        val evento: EventoPubblicato = VoceDivisa(
            registrazioneId = registrazione,
            origine = VoceId(1),
            nuova = VoceId(3),
            segmentiSpostati = listOf(SegmentoId(4), SegmentoId(7)),
        )
        val atteso = VoceDivisa(RegistrazioneId("id-1"), VoceId(1), VoceId(3), listOf(SegmentoId(4), SegmentoId(7)))
        assertEquals(atteso, evento)
        assertEquals(
            listOf(
                "registrazioneId: RegistrazioneId",
                "origine: VoceId",
                "nuova: VoceId",
                "segmentiSpostati: List<SegmentoId>",
            ),
            FormaEventi.di("VoceDivisa"),
        )
    }

    @Test
    fun `AC-14 SegmentoRiassegnato ha registrazioneId, segmentoId, da, a, daRimossa e aNuova`() {
        val evento: EventoPubblicato = SegmentoRiassegnato(
            registrazioneId = registrazione,
            segmentoId = SegmentoId(5),
            da = VoceId(2),
            a = VoceId(4),
            daRimossa = true,
            aNuova = true,
        )
        val atteso = SegmentoRiassegnato(RegistrazioneId("id-1"), SegmentoId(5), VoceId(2), VoceId(4), true, true)
        assertEquals(atteso, evento)
        assertEquals(
            listOf(
                "registrazioneId: RegistrazioneId",
                "segmentoId: SegmentoId",
                "da: VoceId",
                "a: VoceId",
                "daRimossa: Boolean",
                "aNuova: Boolean",
            ),
            FormaEventi.di("SegmentoRiassegnato"),
        )
    }

    @Test
    fun `AC-15 gli eventi di Revisione sono data class di soli val che implementano EventoPubblicato`() {
        listOf("VociUnite", "VoceDivisa", "SegmentoRiassegnato").forEach(FormaEventi::verificaEventoPubblicato)
    }

    @Test
    fun `AC-15 il pacchetto eventi di Trascrizione contiene solo gli otto eventi fissati`() {
        assertEquals(
            setOf(
                "ElaborazioneAvviata",
                "ElaborazioneCompletata",
                "ElaborazioneFallita",
                "TrascrittoSostituito",
                "ElaborazioneAnnullata",
                "VociUnite",
                "VoceDivisa",
                "SegmentoRiassegnato",
            ),
            FormaEventi.classi.map { it.name }.toSet(),
        )
    }
}
