package snastro.sintesi.applicazione.eventi

import com.lemonappdev.konsist.api.Konsist
import snastro.kernel.EventoPubblicato
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** AC-S71 (boundary `eventi-sintesi`): each published event is a data class of exactly the pinned `val`s. */
class EventiSintesiTest {
    private val classi = Konsist.scopeFromPackage("snastro.sintesi.applicazione.eventi", "sintesi/applicazione", "main")
        .classes()

    private val registrazione = RegistrazioneId("registrazione-1")

    @Test
    fun `AC-S71 gli eventi pubblicati hanno esattamente i campi fissati`() {
        val soloRegistrazione = listOf("registrazioneId: RegistrazioneId")
        assertEquals(
            mapOf(
                "RiassuntoRichiesto" to soloRegistrazione,
                "RiassuntoAvviato" to soloRegistrazione,
                "RiassuntoPronto" to soloRegistrazione,
                "RiassuntoFallito" to listOf("registrazioneId: RegistrazioneId", "motivo: String"),
                "RiassuntoEliminato" to soloRegistrazione,
                "LunghezzaMassimaRiassuntoModificata" to listOf("progettoId: ProgettoId"),
            ),
            classi.associate { c ->
                c.name to checkNotNull(c.primaryConstructor) { c.name }.parameters.map { "${it.name}: ${it.type.text}" }
            },
        )
    }

    @Test
    fun `AC-S71 gli eventi sono data class di soli val che implementano EventoPubblicato`() {
        classi.forEach { evento ->
            assertTrue(evento.hasDataModifier, "${evento.name} non e' una data class")
            assertTrue(evento.hasParentWithName("EventoPubblicato"), "${evento.name} non implementa EventoPubblicato")
            assertTrue(evento.properties().none { it.isVar }, "${evento.name} ha una proprieta var")
            assertTrue(evento.primaryConstructor?.parameters.orEmpty().all { it.isVal }, "${evento.name} non val")
        }
    }

    @Test
    fun `AC-S71 gli eventi sono EventoPubblicato e si confrontano per valore`() {
        val eventi: List<EventoPubblicato> = listOf(
            RiassuntoRichiesto(registrazione),
            RiassuntoAvviato(registrazione),
            RiassuntoPronto(registrazione),
            RiassuntoFallito(registrazione, "errore_modello"),
            RiassuntoEliminato(registrazione),
            LunghezzaMassimaRiassuntoModificata(ProgettoId("progetto-1")),
        )

        assertEquals(RiassuntoFallito(RegistrazioneId("registrazione-1"), "errore_modello"), eventi[3])
        assertEquals(LunghezzaMassimaRiassuntoModificata(ProgettoId("progetto-1")), eventi[5])
        assertEquals(6, eventi.toSet().size)
    }
}
