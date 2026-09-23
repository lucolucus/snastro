package snastro.parlanti.applicazione.eventi

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import snastro.kernel.EventoPubblicato
import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Boundary `eventi-parlanti` (building-blocks.yaml): every published event carries exactly the pinned
 * fields, in the pinned order, with the pinned Published Language types (AC-14), and is a `data class`
 * of `val`s implementing [EventoPubblicato] (AC-15, CR-5).
 */
class EventiParlantiTest {
    private val classi: List<KoClassDeclaration> =
        Konsist.scopeFromPackage("snastro.parlanti.applicazione.eventi", "parlanti/applicazione", "main").classes()

    private val eventi: List<KoClassDeclaration> = classi.filterNot { it.hasEnumModifier }

    private fun formaDi(nome: String): List<String> =
        checkNotNull(eventi.single { it.name == nome }.primaryConstructor) { "$nome senza costruttore primario" }
            .parameters.map { "${it.name}: ${it.type.text}" }

    private val parlante = ParlanteId("id-1")

    @Test
    fun `AC-14 AttribuzioneConfermata ha voceRef, parlanteId e precedente facoltativo`() {
        val voce = VoceRef(RegistrazioneId("id-9"), VoceId(2))
        val prima: EventoPubblicato =
            AttribuzioneConfermata(voceRef = voce, parlanteId = parlante, precedente = null)
        val correzione = AttribuzioneConfermata(voce, ParlanteId("id-2"), precedente = parlante)
        assertNull((prima as AttribuzioneConfermata).precedente)
        assertEquals(parlante, correzione.precedente)
        assertEquals(
            listOf("voceRef: VoceRef", "parlanteId: ParlanteId", "precedente: ParlanteId?"),
            formaDi("AttribuzioneConfermata"),
        )
    }

    @Test
    fun `AC-14 ParlanteCreato ha parlanteId, progettoId, nome e tipo`() {
        val evento: EventoPubblicato = ParlanteCreato(
            parlanteId = parlante,
            progettoId = ProgettoId("id-3"),
            nome = "Marco",
            tipo = TipoParlanteVista.RICORRENTE,
        )
        val atteso = ParlanteCreato(ParlanteId("id-1"), ProgettoId("id-3"), "Marco", TipoParlanteVista.RICORRENTE)
        assertEquals(atteso, evento)
        assertEquals(
            listOf("parlanteId: ParlanteId", "progettoId: ProgettoId", "nome: String", "tipo: TipoParlanteVista"),
            formaDi("ParlanteCreato"),
        )
    }

    @Test
    fun `AC-14 ParlanteRinominato ha parlanteId e nome`() {
        val evento: EventoPubblicato = ParlanteRinominato(parlanteId = parlante, nome = "Marco Rossi")
        assertEquals(ParlanteRinominato(ParlanteId("id-1"), "Marco Rossi"), evento)
        assertEquals(listOf("parlanteId: ParlanteId", "nome: String"), formaDi("ParlanteRinominato"))
    }

    @Test
    fun `AC-14 ParlantePromosso ha parlanteId, nome e nomeCambiato`() {
        val evento: EventoPubblicato = ParlantePromosso(parlanteId = parlante, nome = "Anna", nomeCambiato = true)
        assertEquals(ParlantePromosso(ParlanteId("id-1"), "Anna", true), evento)
        assertEquals(
            listOf("parlanteId: ParlanteId", "nome: String", "nomeCambiato: Boolean"),
            formaDi("ParlantePromosso"),
        )
    }

    @Test
    fun `AC-14 ParlanteEliminato ha solo parlanteId`() {
        val evento: EventoPubblicato = ParlanteEliminato(parlanteId = parlante)
        assertEquals(ParlanteEliminato(ParlanteId("id-1")), evento)
        assertEquals(listOf("parlanteId: ParlanteId"), formaDi("ParlanteEliminato"))
    }

    @Test
    fun `AC-14 ImpronteRiallineate ha solo registrazioneId`() {
        val evento: EventoPubblicato = ImpronteRiallineate(registrazioneId = RegistrazioneId("id-9"))
        assertEquals(ImpronteRiallineate(RegistrazioneId("id-9")), evento)
        assertEquals(listOf("registrazioneId: RegistrazioneId"), formaDi("ImpronteRiallineate"))
    }

    @Test
    fun `TipoParlanteVista ha esattamente RICORRENTE e OCCASIONALE`() {
        assertEquals(listOf("RICORRENTE", "OCCASIONALE"), TipoParlanteVista.entries.map { it.name })
    }

    @Test
    fun `AC-15 gli eventi pubblicati di Parlanti sono data class di soli val che implementano EventoPubblicato`() {
        assertEquals(
            setOf(
                "AttribuzioneConfermata",
                "ParlanteCreato",
                "ParlanteRinominato",
                "ParlantePromosso",
                "ParlanteEliminato",
                "ImpronteRiallineate",
            ),
            eventi.map { it.name }.toSet(),
        )
        assertEquals(listOf("TipoParlanteVista"), classi.filter { it.hasEnumModifier }.map { it.name })
        eventi.forEach { evento ->
            assertTrue(evento.hasDataModifier, "${evento.name} non e' una data class")
            assertTrue(evento.hasParentWithName("EventoPubblicato"), "${evento.name} non implementa EventoPubblicato")
            assertTrue(evento.properties().none { it.isVar }, "${evento.name} ha una proprieta var")
            val parametri = evento.primaryConstructor?.parameters.orEmpty()
            assertTrue(parametri.all { it.isVal }, "${evento.name} ha un parametro non val")
        }
    }
}
