package snastro.trascrizione.applicazione.letture

import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.kernel.atteso
import snastro.trascrizione.applicazione.porte.LettoreRegistrazioneFinta
import snastro.trascrizione.applicazione.porte.RegistrazioneVista
import snastro.trascrizione.applicazione.porte.TrascrittoRepositoryFinta
import snastro.trascrizione.dominio.DURATA_TRASCRITTO_MS
import snastro.trascrizione.dominio.Trascritto
import snastro.trascrizione.dominio.unSegmentoIniziale
import snastro.trascrizione.dominio.unTrascritto
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TrascrittoQueryTest {
    private val trascritti = TrascrittoRepositoryFinta()
    private val registrazioni = LettoreRegistrazioneFinta(mapOf(REGISTRAZIONE to UNA_REGISTRAZIONE))
    private val query = TrascrittoQuery(trascritti, registrazioni)

    @Test
    fun `AC-167 vista espone i campi della Registrazione, i segmenti in ordine di tempo e le voci etichettate`() {
        val trascritto = Trascritto.crea(
            REGISTRAZIONE,
            DURATA_TRASCRITTO_MS,
            listOf(
                unSegmentoIniziale(voceIndice = 4, inizioMs = 2_000, fineMs = 4_000, testo = "seconda battuta"),
                unSegmentoIniziale(voceIndice = 2, inizioMs = 0, fineMs = 1_500, testo = "prima battuta"),
                unSegmentoIniziale(voceIndice = 4, inizioMs = 4_500, fineMs = 5_000, testo = "terza battuta"),
            ),
        ).atteso().aggregato
        trascritti.salva(trascritto)

        val vista = query.vista(REGISTRAZIONE)

        assertEquals(
            TrascrittoView(
                registrazioneId = REGISTRAZIONE,
                titolo = "Intervista",
                dataRegistrazione = LocalDate.of(2026, 9, 1),
                durataMs = 120_000,
                segmenti = listOf(
                    SegmentoTrascrittoView(SegmentoId(1), VoceId(1), 0, 1_500, "prima battuta"),
                    SegmentoTrascrittoView(SegmentoId(2), VoceId(2), 2_000, 4_000, "seconda battuta"),
                    SegmentoTrascrittoView(SegmentoId(3), VoceId(2), 4_500, 5_000, "terza battuta"),
                ),
                voci = listOf(
                    VoceTrascrittoView(VoceId(1), "Voce 1"),
                    VoceTrascrittoView(VoceId(2), "Voce 2"),
                ),
            ),
            vista,
        )
    }

    @Test
    fun `AC-168 vista senza Trascritto restituisce null`() {
        assertNull(query.vista(REGISTRAZIONE))
    }

    @Test
    fun `AC-168 vista con Trascritto ma senza Registrazione nota restituisce null`() {
        trascritti.salva(unTrascritto(voci = 1, segmentiPerVoce = 1, registrazioneId = ALTRA_REGISTRAZIONE))

        assertNull(query.vista(ALTRA_REGISTRAZIONE))
    }

    private companion object {
        val REGISTRAZIONE = RegistrazioneId("registrazione-1")
        val ALTRA_REGISTRAZIONE = RegistrazioneId("registrazione-2")
        val UNA_REGISTRAZIONE = RegistrazioneVista(
            registrazioneId = REGISTRAZIONE,
            progettoId = ProgettoId("progetto-1"),
            titolo = "Intervista",
            riferimentoAudio = RiferimentoAudio("audio/registrazione-1.wav"),
            dataRegistrazione = LocalDate.of(2026, 9, 1),
            durataMs = 120_000,
        )
    }
}
