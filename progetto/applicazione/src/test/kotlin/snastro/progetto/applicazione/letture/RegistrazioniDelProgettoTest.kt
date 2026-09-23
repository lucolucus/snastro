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
                RegistrazioneVista(
                    registrazioneId = id,
                    progettoId = progettoId,
                    titolo = "Seduta del 12 marzo",
                    riferimentoAudio = RiferimentoAudio("audio/id-1.m4a"),
                    dataRegistrazione = LocalDate.of(2026, 3, 12),
                    durataMs = 3_600_000L,
                ),
            ),
            vista.delProgetto(progettoId),
        )
    }

    @Test
    fun `AC-161 ordinate per dataRegistrazione dalla piu recente`() {
        val vecchia = unaRegistrazione(RegistrazioneId("id-1"), dataRegistrazione = LocalDate.of(2026, 1, 1))
        val recente = unaRegistrazione(RegistrazioneId("id-2"), dataRegistrazione = LocalDate.of(2026, 3, 1))
        registrazioni.salva(vecchia)
        registrazioni.salva(recente)

        assertEquals(
            listOf(RegistrazioneId("id-2"), RegistrazioneId("id-1")),
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
