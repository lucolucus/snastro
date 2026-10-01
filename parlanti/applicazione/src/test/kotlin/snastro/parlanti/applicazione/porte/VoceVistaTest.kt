package snastro.parlanti.applicazione.porte

import com.lemonappdev.konsist.api.Konsist
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Boundary `voci-per-parlanti`: [VoceVista] and [SegmentoDiVoce] carry exactly the pinned fields and
 * [LettoreVoci] exactly the pinned methods, keyed by Incontro — never text (AC-48, AC-494, AC-I24); boundary
 * `parti-per-parlanti`: [ParteDiIncontroParlanti] carries exactly the pinned fields (AC-I25).
 */
class VoceVistaTest {
    private val porte = Konsist.scopeFromPackage("snastro.parlanti.applicazione.porte", "parlanti/applicazione", "main")

    @Test
    fun `AC-48 AC-I24 VoceVista ha solo voceRef e intervalliPerParte e nessun testo`() {
        val vista = porte.classes().single { it.name == "VoceVista" }

        assertEquals(
            listOf("voceRef: VoceRef", "intervalliPerParte: Map<RegistrazioneId, List<IntervalloMs>>"),
            checkNotNull(vista.primaryConstructor).parameters.map { "${it.name}: ${it.type.text}" },
        )
        assertEquals(listOf("voceRef", "intervalliPerParte"), vista.properties().map { it.name })
    }

    @Test
    fun `AC-494 AC-I24 SegmentoDiVoce ha solo i campi pinned e nessun testo`() {
        val segmento = porte.classes().single { it.name == "SegmentoDiVoce" }

        assertEquals(
            listOf("segmento: SegmentoRef", "voceId: VoceId", "intervallo: IntervalloMs", "confermato: Boolean"),
            checkNotNull(segmento.primaryConstructor).parameters.map { "${it.name}: ${it.type.text}" },
        )
        assertEquals(listOf("segmento", "voceId", "intervallo", "confermato"), segmento.properties().map { it.name })
    }

    @Test
    fun `AC-48 AC-494 AC-I24 LettoreVoci dichiara solo voci e segmenti e nessun metodo con testo`() {
        val lettore = porte.interfaces().single { it.name == "LettoreVoci" }

        assertEquals(
            listOf(
                "voci(IncontroId): List<VoceVista>?",
                "segmenti(IncontroId): List<SegmentoDiVoce>?",
            ),
            lettore.functions().map { f ->
                "${f.name}(${f.parameters.joinToString { it.type.text }}): ${f.returnType?.text}"
            },
        )
        assertEquals(emptyList(), lettore.properties().map { it.name })
    }

    @Test
    fun `AC-I25 ParteDiIncontroParlanti ha solo registrazioneId numero e dataRegistrazione`() {
        val parte = porte.classes().single { it.name == "ParteDiIncontroParlanti" }

        assertEquals(
            listOf("registrazioneId: RegistrazioneId", "numero: Int", "dataRegistrazione: LocalDate"),
            checkNotNull(parte.primaryConstructor).parameters.map { "${it.name}: ${it.type.text}" },
        )
    }
}
