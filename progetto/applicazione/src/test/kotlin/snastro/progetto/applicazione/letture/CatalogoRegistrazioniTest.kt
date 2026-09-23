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
import kotlin.test.assertNull

class CatalogoRegistrazioniTest {
    private val progettoId = ProgettoId("progetto-1")
    private val registrazioni = RegistrazioneRepositoryFinta()
    private val catalogo = CatalogoRegistrazioni(registrazioni)

    @Test
    fun `AC-97 id noto restituisce la RegistrazioneVista con tutti i campi della Registrazione`() {
        val id = RegistrazioneId("id-1")
        registrazioni.salva(unaRegistrazione(id, "Seduta del 12 marzo"))

        assertEquals(
            RegistrazioneVista(
                registrazioneId = id,
                progettoId = progettoId,
                titolo = "Seduta del 12 marzo",
                riferimentoAudio = RiferimentoAudio("audio/id-1.m4a"),
                dataRegistrazione = LocalDate.of(2026, 3, 12),
                durataMs = 3_600_000L,
            ),
            catalogo.registrazione(id),
        )
    }

    @Test
    fun `AC-97 id sconosciuto restituisce null`() {
        assertNull(catalogo.registrazione(RegistrazioneId("id-sconosciuto")))
    }

    @Test
    fun `AC-97 id sconosciuto tra registrazioni note restituisce null senza toccarle`() {
        val nota = RegistrazioneId("id-1")
        registrazioni.salva(unaRegistrazione(nota, "Seduta del 12 marzo"))

        assertNull(catalogo.registrazione(RegistrazioneId("id-sconosciuto")))
        assertEquals("Seduta del 12 marzo", catalogo.registrazione(nota)?.titolo)
    }

    private fun unaRegistrazione(id: RegistrazioneId, titolo: String): Registrazione =
        Registrazione.aggiungi(
            id = id,
            progettoId = progettoId,
            titolo = titolo,
            riferimentoAudio = RiferimentoAudio("audio/${id.valore}.m4a"),
            durataMs = 3_600_000L,
            dataRegistrazione = LocalDate.of(2026, 3, 12),
            aggiuntaAlle = Instant.parse("2026-09-23T10:00:00Z"),
        ).aggregato
}
