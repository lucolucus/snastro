package snastro.trascrizione.applicazione.eventi

import snastro.kernel.EventoPubblicato
import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.SegmentoRef
import snastro.kernel.VoceId
import kotlin.test.Test
import kotlin.test.assertEquals

/** Boundary `eventi-trascrizione-incontro` (ADR 0035 §5): AC-I23 shape of each Revisione event, AC-15 CR-5. */
class EventiRevisioneTest {
    private val incontro = IncontroId("incontro-1")
    private val segmento = SegmentoRef(RegistrazioneId("parte-b"), SegmentoId(5))

    @Test
    fun `AC-I23 VociUnite ha incontroId, sopravvissuta e rimossa`() {
        val evento: EventoPubblicato = VociUnite(incontroId = incontro, sopravvissuta = VoceId(1), rimossa = VoceId(2))
        assertEquals(VociUnite(IncontroId("incontro-1"), VoceId(1), VoceId(2)), evento)
        assertEquals(
            listOf("incontroId: IncontroId", "sopravvissuta: VoceId", "rimossa: VoceId"),
            FormaEventi.di("VociUnite"),
        )
    }

    @Test
    fun `AC-I23 VoceDivisa ha incontroId, origine, nuova e spostati come SegmentoRef`() {
        val spostati = listOf(SegmentoRef(RegistrazioneId("parte-a"), SegmentoId(4)), segmento)
        val evento: EventoPubblicato =
            VoceDivisa(incontroId = incontro, origine = VoceId(1), nuova = VoceId(3), spostati = spostati)
        assertEquals(VoceDivisa(IncontroId("incontro-1"), VoceId(1), VoceId(3), spostati), evento)
        assertEquals(
            listOf("incontroId: IncontroId", "origine: VoceId", "nuova: VoceId", "spostati: List<SegmentoRef>"),
            FormaEventi.di("VoceDivisa"),
        )
    }

    @Test
    fun `AC-I23 SegmentoRiassegnato ha incontroId, segmento come SegmentoRef, da, a, daRimossa e aNuova`() {
        val evento: EventoPubblicato = SegmentoRiassegnato(
            incontroId = incontro,
            segmento = segmento,
            da = VoceId(2),
            a = VoceId(4),
            daRimossa = true,
            aNuova = true,
        )
        assertEquals(SegmentoRiassegnato(IncontroId("incontro-1"), segmento, VoceId(2), VoceId(4), true, true), evento)
        assertEquals(
            listOf(
                "incontroId: IncontroId",
                "segmento: SegmentoRef",
                "da: VoceId",
                "a: VoceId",
                "daRimossa: Boolean",
                "aNuova: Boolean",
            ),
            FormaEventi.di("SegmentoRiassegnato"),
        )
    }

    @Test
    fun `AC-I23 SegmentoConfermato ha incontroId, segmento come SegmentoRef e confermato`() {
        val evento: EventoPubblicato = SegmentoConfermato(incontroId = incontro, segmento = segmento, confermato = true)
        assertEquals(SegmentoConfermato(IncontroId("incontro-1"), segmento, true), evento)
        assertEquals(
            listOf("incontroId: IncontroId", "segmento: SegmentoRef", "confermato: Boolean"),
            FormaEventi.di("SegmentoConfermato"),
        )
    }

    @Test
    fun `AC-525 SegmentoConfermato arriva al dopo-commit solo dopo il COMMIT e mai dopo un rollback`() {
        val consegna = Consegna()
        val evento = SegmentoConfermato(incontro, segmento, confermato = false)

        consegna.inTransazione(evento, conferma = false)
        assertEquals(emptyList(), consegna.dopoCommit)

        consegna.inTransazione(evento, conferma = true)
        assertEquals(listOf<EventoPubblicato>(evento), consegna.dopoCommit)
    }

    @Test
    fun `AC-15 gli eventi di Revisione sono data class di soli val che implementano EventoPubblicato`() {
        listOf("VociUnite", "VoceDivisa", "SegmentoRiassegnato", "SegmentoConfermato")
            .forEach(FormaEventi::verificaEventoPubblicato)
    }

    @Test
    fun `AC-15 il pacchetto eventi di Trascrizione contiene solo i dieci eventi fissati`() {
        assertEquals(
            setOf(
                "ElaborazioneAvviata",
                "ElaborazioneCompletata",
                "ElaborazioneFallita",
                "TrascrittoSostituito",
                "TrascrittoEliminato",
                "ElaborazioneAnnullata",
                "VociUnite",
                "VoceDivisa",
                "SegmentoRiassegnato",
                "SegmentoConfermato",
            ),
            FormaEventi.classi.map { it.name }.toSet(),
        )
    }
}
