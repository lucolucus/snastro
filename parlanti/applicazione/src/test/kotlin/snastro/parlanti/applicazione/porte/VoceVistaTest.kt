package snastro.parlanti.applicazione.porte

import com.lemonappdev.konsist.api.Konsist
import kotlin.test.Test
import kotlin.test.assertEquals

/** Boundary `voci-per-parlanti`: [VoceVista] carries exactly the pinned fields — never text (AC-48). */
class VoceVistaTest {
    @Test
    fun `AC-48 VoceVista ha solo voceRef e intervalli e nessun testo`() {
        val vista = Konsist.scopeFromPackage("snastro.parlanti.applicazione.porte", "parlanti/applicazione", "main")
            .classes()
            .single { it.name == "VoceVista" }

        assertEquals(
            listOf("voceRef: VoceRef", "intervalli: List<IntervalloMs>"),
            checkNotNull(vista.primaryConstructor).parameters.map { "${it.name}: ${it.type.text}" },
        )
        assertEquals(listOf("voceRef", "intervalli"), vista.properties().map { it.name })
    }
}
