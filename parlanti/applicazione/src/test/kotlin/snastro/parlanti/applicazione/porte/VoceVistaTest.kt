package snastro.parlanti.applicazione.porte

import com.lemonappdev.konsist.api.Konsist
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Boundary `voci-per-parlanti`: [VoceVista] and [SegmentoDiVoce] carry exactly the pinned fields and
 * [LettoreVoci] exactly the pinned methods — never text (AC-48, AC-494).
 */
class VoceVistaTest {
    private val porte = Konsist.scopeFromPackage("snastro.parlanti.applicazione.porte", "parlanti/applicazione", "main")

    @Test
    fun `AC-48 VoceVista ha solo voceRef e intervalli e nessun testo`() {
        val vista = porte.classes().single { it.name == "VoceVista" }

        assertEquals(
            listOf("voceRef: VoceRef", "intervalli: List<IntervalloMs>"),
            checkNotNull(vista.primaryConstructor).parameters.map { "${it.name}: ${it.type.text}" },
        )
        assertEquals(listOf("voceRef", "intervalli"), vista.properties().map { it.name })
    }

    @Test
    fun `AC-494 SegmentoDiVoce ha solo i campi pinned e nessun testo`() {
        val segmento = porte.classes().single { it.name == "SegmentoDiVoce" }

        assertEquals(
            listOf("segmentoId: SegmentoId", "voceId: VoceId", "intervallo: IntervalloMs", "confermato: Boolean"),
            checkNotNull(segmento.primaryConstructor).parameters.map { "${it.name}: ${it.type.text}" },
        )
        assertEquals(listOf("segmentoId", "voceId", "intervallo", "confermato"), segmento.properties().map { it.name })
    }

    @Test
    fun `AC-48 AC-494 LettoreVoci dichiara solo voci e segmenti e nessun metodo con testo`() {
        val lettore = porte.interfaces().single { it.name == "LettoreVoci" }

        assertEquals(
            listOf(
                "voci(RegistrazioneId): List<VoceVista>?",
                "segmenti(RegistrazioneId): List<SegmentoDiVoce>?",
            ),
            lettore.functions().map { f ->
                "${f.name}(${f.parameters.joinToString { it.type.text }}): ${f.returnType?.text}"
            },
        )
        assertEquals(emptyList(), lettore.properties().map { it.name })
    }
}
