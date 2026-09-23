package snastro.progetto.applicazione.letture

import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.progetto.applicazione.porte.RegistrazioneRepositoryFinta
import snastro.progetto.dominio.Registrazione
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class RegistrazioniDelProgettoTest {
    private val progettoId = ProgettoId("progetto-1")
    private val registrazioni = RegistrazioneRepositoryFinta()
    private val vista = RegistrazioniDelProgetto(registrazioni)

    @Test
    fun `AC-161 espone registrazioneId, titolo, dataRegistrazione e durataMs`() {
        val id = RegistrazioneId("id-1")
        registrazioni.salva(
            unaRegistrazione(
                id = id,
                titolo = "Seduta del 12 marzo",
                dataRegistrazione = LocalDate.of(2026, 3, 12),
            ),
        )

        assertEquals(
            listOf(
                RegistrazioneDelProgettoVista(
                    registrazioneId = id,
                    titolo = "Seduta del 12 marzo",
                    dataRegistrazione = LocalDate.of(2026, 3, 12),
                    durataMs = 3_600_000L,
                ),
            ),
            vista.delProgetto(progettoId),
        )
    }

    @Test
    fun `AC-161 ordinate per dataRegistrazione dalla piu recente, indipendentemente dall'ordine di inserimento`() {
        // Insertion order (mid, new, old) matches neither the correct order (new, mid, old) nor its
        // reverse (old, new, mid): a `delProgetto(id).reversed()` implementation would fail this.
        val mid = unaRegistrazione(RegistrazioneId("id-mid"), dataRegistrazione = LocalDate.of(2026, 2, 1))
        val new = unaRegistrazione(RegistrazioneId("id-new"), dataRegistrazione = LocalDate.of(2026, 3, 1))
        val old = unaRegistrazione(RegistrazioneId("id-old"), dataRegistrazione = LocalDate.of(2026, 1, 1))
        registrazioni.salva(mid)
        registrazioni.salva(new)
        registrazioni.salva(old)

        assertEquals(
            listOf(RegistrazioneId("id-new"), RegistrazioneId("id-mid"), RegistrazioneId("id-old")),
            vista.delProgetto(progettoId).map { it.registrazioneId },
        )
    }

    @Test
    fun `AC-161 a parita di data vince l'ultima aggiunta`() {
        val primaAggiunta = unaRegistrazione(
            RegistrazioneId("id-1"),
            dataRegistrazione = LocalDate.of(2026, 3, 1),
            aggiuntaAlle = Instant.parse("2026-03-01T10:00:00Z"),
        )
        val ultimaAggiunta = unaRegistrazione(
            RegistrazioneId("id-2"),
            dataRegistrazione = LocalDate.of(2026, 3, 1),
            aggiuntaAlle = Instant.parse("2026-03-01T11:00:00Z"),
        )
        registrazioni.salva(primaAggiunta)
        registrazioni.salva(ultimaAggiunta)

        assertEquals(
            listOf(RegistrazioneId("id-2"), RegistrazioneId("id-1")),
            vista.delProgetto(progettoId).map { it.registrazioneId },
        )
    }

    @Test
    fun `AC-161 dataRegistrazione batte aggiuntaAlle anche quando la piu vecchia e' stata aggiunta dopo`() {
        // "new" has the more recent dataRegistrazione but was added FIRST (earlier aggiuntaAlle).
        // "old" has an older dataRegistrazione but was added LATER (later aggiuntaAlle): it must
        // still sort below "new" — date wins over aggiuntaAlle. Insertion order equals the expected
        // order here, so a naive `.reversed()` implementation would (wrongly) flip it.
        val new = unaRegistrazione(
            RegistrazioneId("id-new"),
            dataRegistrazione = LocalDate.of(2026, 3, 1),
            aggiuntaAlle = Instant.parse("2026-03-01T08:00:00Z"),
        )
        val old = unaRegistrazione(
            RegistrazioneId("id-old"),
            dataRegistrazione = LocalDate.of(2026, 1, 1),
            aggiuntaAlle = Instant.parse("2026-03-01T23:00:00Z"),
        )
        registrazioni.salva(new)
        registrazioni.salva(old)

        assertEquals(
            listOf(RegistrazioneId("id-new"), RegistrazioneId("id-old")),
            vista.delProgetto(progettoId).map { it.registrazioneId },
        )
    }

    @Test
    fun `AC-161 a parita di data e aggiuntaAlle il tie-break finale e' l'id, stabile tra i refresh`() {
        // Both dataRegistrazione and aggiuntaAlle tie: only the id tie-break decides. Inserted in
        // the OPPOSITE order of the expected id order, so a stable sort without the id tie-break
        // would (wrongly) preserve insertion order and fail this assertion.
        val stessaData = LocalDate.of(2026, 3, 1)
        val stessoAggiunta = Instant.parse("2026-03-01T10:00:00Z")
        val secondoPerId = unaRegistrazione(
            RegistrazioneId("id-2"),
            dataRegistrazione = stessaData,
            aggiuntaAlle = stessoAggiunta,
        )
        val primoPerId = unaRegistrazione(
            RegistrazioneId("id-1"),
            dataRegistrazione = stessaData,
            aggiuntaAlle = stessoAggiunta,
        )
        registrazioni.salva(secondoPerId)
        registrazioni.salva(primoPerId)

        assertEquals(
            listOf(RegistrazioneId("id-1"), RegistrazioneId("id-2")),
            vista.delProgetto(progettoId).map { it.registrazioneId },
        )
    }

    @Test
    fun `AC-161 progetto senza registrazioni restituisce lista vuota`() {
        assertEquals(emptyList(), vista.delProgetto(progettoId))
    }

    @Test
    fun `AC-161 non include registrazioni di un altro progetto`() {
        registrazioni.salva(unaRegistrazione(RegistrazioneId("id-1"), progettoId = ProgettoId("progetto-2")))

        assertEquals(emptyList(), vista.delProgetto(progettoId))
    }

    private fun unaRegistrazione(
        id: RegistrazioneId,
        titolo: String = "Seduta",
        dataRegistrazione: LocalDate = LocalDate.of(2026, 3, 12),
        aggiuntaAlle: Instant = Instant.parse("2026-09-23T10:00:00Z"),
        progettoId: ProgettoId = this.progettoId,
    ): Registrazione =
        Registrazione.aggiungi(
            id = id,
            progettoId = progettoId,
            titolo = titolo,
            riferimentoAudio = RiferimentoAudio("audio/${id.valore}.m4a"),
            durataMs = 3_600_000L,
            dataRegistrazione = dataRegistrazione,
            aggiuntaAlle = aggiuntaAlle,
        ).aggregato
}
