package snastro.parlanti.applicazione.porte

import com.lemonappdev.konsist.api.Konsist
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Boundary `voci-per-parlanti`: [VoceVista] carries exactly the pinned fields and [LettoreVoci] exactly
 * the pinned method — never text (AC-48).
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
    fun `AC-48 LettoreVoci dichiara solo voci per RegistrazioneId e nessun metodo con testo`() {
        val lettore = porte.interfaces().single { it.name == "LettoreVoci" }

        assertEquals(
            listOf("voci(RegistrazioneId): List<VoceVista>?"),
            lettore.functions().map { f ->
                "${f.name}(${f.parameters.joinToString { it.type.text }}): ${f.returnType?.text}"
            },
        )
        assertEquals(emptyList(), lettore.properties().map { it.name })
    }
}
