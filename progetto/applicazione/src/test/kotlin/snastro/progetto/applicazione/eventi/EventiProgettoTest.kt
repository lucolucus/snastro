package snastro.progetto.applicazione.eventi

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import snastro.kernel.DispatcherEventiInMemoria
import snastro.kernel.ErroreDiProva
import snastro.kernel.Esito
import snastro.kernel.EventoPubblicato
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Boundary `eventi-progetto` (building-blocks.yaml): every published event carries exactly the pinned
 * fields, in the pinned order, with the pinned Published Language types (AC-14), and is a `data class`
 * of `val`s implementing [EventoPubblicato] (AC-15, CR-5).
 */
class EventiProgettoTest {
    private val eventi: List<KoClassDeclaration> =
        Konsist.scopeFromPackage("snastro.progetto.applicazione.eventi", "progetto/applicazione", "main").classes()

    private fun formaDi(nome: String): List<String> =
        checkNotNull(eventi.single { it.name == nome }.primaryConstructor) { "$nome senza costruttore primario" }
            .parameters.map { "${it.name}: ${it.type.text}" }

    @Test
    fun `AC-14 ProgettoCreato ha progettoId e nome`() {
        val evento: EventoPubblicato = ProgettoCreato(progettoId = ProgettoId("id-1"), nome = "Consiglio comunale")
        assertEquals(ProgettoCreato(ProgettoId("id-1"), "Consiglio comunale"), evento)
        assertEquals(listOf("progettoId: ProgettoId", "nome: String"), formaDi("ProgettoCreato"))
    }

    @Test
    fun `AC-14 RegistrazioneAggiunta ha registrazioneId e progettoId`() {
        val evento: EventoPubblicato =
            RegistrazioneAggiunta(registrazioneId = RegistrazioneId("id-2"), progettoId = ProgettoId("id-1"))
        assertEquals(RegistrazioneAggiunta(RegistrazioneId("id-2"), ProgettoId("id-1")), evento)
        assertEquals(
            listOf("registrazioneId: RegistrazioneId", "progettoId: ProgettoId"),
            formaDi("RegistrazioneAggiunta"),
        )
    }

    @Test
    fun `AC-14 DataRegistrazioneModificata ha registrazioneId, precedente e nuova`() {
        val evento: EventoPubblicato = DataRegistrazioneModificata(
            registrazioneId = RegistrazioneId("id-2"),
            precedente = LocalDate.of(2026, 9, 12),
            nuova = LocalDate.of(2026, 9, 13),
        )
        assertEquals(
            DataRegistrazioneModificata(RegistrazioneId("id-2"), LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 13)),
            evento,
        )
        assertEquals(
            listOf("registrazioneId: RegistrazioneId", "precedente: LocalDate", "nuova: LocalDate"),
            formaDi("DataRegistrazioneModificata"),
        )
    }

    @Test
    fun `AC-360 RegistrazioneRinominata ha registrazioneId, precedente e nuovo`() {
        val evento: EventoPubblicato =
            RegistrazioneRinominata(registrazioneId = RegistrazioneId("id-2"), precedente = "Vecchio", nuovo = "Nuovo")
        assertEquals(RegistrazioneRinominata(RegistrazioneId("id-2"), "Vecchio", "Nuovo"), evento)
        assertEquals(
            listOf("registrazioneId: RegistrazioneId", "precedente: String", "nuovo: String"),
            formaDi("RegistrazioneRinominata"),
        )
    }

    @Test
    fun `AC-618 RegistrazioneEliminata ha registrazioneId, progettoId, titolo, dataRegistrazione e riferimentoAudio`() {
        val evento: EventoPubblicato = eliminata()
        assertEquals(
            RegistrazioneEliminata(
                RegistrazioneId("id-2"),
                ProgettoId("id-1"),
                "Seduta",
                LocalDate.of(2026, 9, 12),
                RiferimentoAudio("audio/id-2.m4a"),
            ),
            evento,
        )
        assertEquals(
            listOf(
                "registrazioneId: RegistrazioneId",
                "progettoId: ProgettoId",
                "titolo: String",
                "dataRegistrazione: LocalDate",
                "riferimentoAudio: RiferimentoAudio",
            ),
            formaDi("RegistrazioneEliminata"),
        )
    }

    @Test
    fun `AC-618 RegistrazioneEliminata arriva al sincrono dentro la transazione e al dopo-commit solo dopo il COMMIT`() {
        val dispatcher = DispatcherEventiInMemoria(UnitaDiLavoroFinta())
        var dentro = false
        val sincroni = mutableListOf<Pair<EventoPubblicato, Boolean>>()
        val dopoCommit = mutableListOf<EventoPubblicato>()
        dispatcher.registraSincrono { e ->
            sincroni += e to dentro
            Esito.Ok(Unit)
        }
        dispatcher.registraDopoCommit { e -> dopoCommit += e }
        fun pubblica(conferma: Boolean): Esito<Unit> = dispatcher.unitaDiLavoro.inTransazione {
            dentro = true
            dispatcher.pubblica(eliminata())
            assertEquals(emptyList(), dopoCommit, "mai prima del COMMIT")
            dentro = false
            if (conferma) Esito.Ok(Unit) else Esito.Errore(ErroreDiProva.Fallito("rollback"))
        }

        pubblica(conferma = false).erroreAtteso<ErroreDiProva.Fallito>()
        assertEquals(listOf<Pair<EventoPubblicato, Boolean>>(eliminata() to true), sincroni)
        assertEquals(emptyList(), dopoCommit, "mai dopo un rollback")

        pubblica(conferma = true).atteso()
        assertEquals(listOf(true, true), sincroni.map { it.second })
        assertEquals(listOf<EventoPubblicato>(eliminata()), dopoCommit)
    }

    private fun eliminata() = RegistrazioneEliminata(
        registrazioneId = RegistrazioneId("id-2"),
        progettoId = ProgettoId("id-1"),
        titolo = "Seduta",
        dataRegistrazione = LocalDate.of(2026, 9, 12),
        riferimentoAudio = RiferimentoAudio("audio/id-2.m4a"),
    )

    @Test
    fun `AC-15 gli eventi pubblicati di Progetto sono data class di soli val che implementano EventoPubblicato`() {
        assertEquals(
            setOf(
                "ProgettoCreato",
                "RegistrazioneAggiunta",
                "DataRegistrazioneModificata",
                "RegistrazioneRinominata",
                "RegistrazioneEliminata",
            ),
            eventi.map { it.name }.toSet(),
        )
        eventi.forEach { evento ->
            assertTrue(evento.hasDataModifier, "${evento.name} non e' una data class")
            assertTrue(evento.hasParentWithName("EventoPubblicato"), "${evento.name} non implementa EventoPubblicato")
            assertTrue(evento.properties().none { it.isVar }, "${evento.name} ha una proprieta var")
            val parametri = evento.primaryConstructor?.parameters.orEmpty()
            assertTrue(parametri.all { it.isVal }, "${evento.name} ha un parametro non val")
        }
    }
}
