package snastro.trascrizione.applicazione.eventi

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import kotlin.test.assertTrue

/** Declarations of the published events of Trascrizione (main source set of `..applicazione.eventi`). */
internal object FormaEventi {
    val classi: List<KoClassDeclaration> =
        Konsist.scopeFromPackage("snastro.trascrizione.applicazione.eventi", "trascrizione/applicazione", "main")
            .classes()

    /** `name: Type` of every primary-constructor parameter, in declaration order (AC-14). */
    fun di(nome: String): List<String> =
        checkNotNull(classi.single { it.name == nome }.primaryConstructor) { "$nome senza costruttore primario" }
            .parameters.map { "${it.name}: ${it.type.text}" }

    /** AC-15 / CR-5: a `data class` of `val`s only, implementing `EventoPubblicato`. */
    fun verificaEventoPubblicato(nome: String) {
        val evento = classi.single { it.name == nome }
        assertTrue(evento.hasDataModifier, "$nome non e' una data class")
        assertTrue(evento.hasParentWithName("EventoPubblicato"), "$nome non implementa EventoPubblicato")
        assertTrue(evento.properties().none { it.isVar }, "$nome ha una proprieta var")
        val parametri = evento.primaryConstructor?.parameters.orEmpty()
        assertTrue(parametri.all { it.isVal }, "$nome ha un parametro non val")
    }
}
