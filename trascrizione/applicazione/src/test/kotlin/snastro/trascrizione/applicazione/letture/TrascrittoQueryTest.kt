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
    fun `AC-167 segmenti in ordine di tempo anche quando le Voci si alternano`() {
        val trascritto = Trascritto.crea(
            REGISTRAZIONE,
            DURATA_TRASCRITTO_MS,
            listOf(
                unSegmentoIniziale(voceIndice = 1, inizioMs = 0, fineMs = 1_000, testo = "v1 turno 1"),
                unSegmentoIniziale(voceIndice = 2, inizioMs = 1_000, fineMs = 2_000, testo = "v2 turno 1"),
                unSegmentoIniziale(voceIndice = 1, inizioMs = 2_000, fineMs = 3_000, testo = "v1 turno 2"),
            ),
        ).atteso().aggregato
        trascritti.salva(trascritto)

        val vista = query.vista(REGISTRAZIONE)

        // Grouping by Voce (V1's segments, then V2's) would yield [1, 3, 2]: this asserts the TIME order.
        assertEquals(
            listOf(
                SegmentoTrascrittoView(SegmentoId(1), VoceId(1), 0, 1_000, "v1 turno 1"),
                SegmentoTrascrittoView(SegmentoId(2), VoceId(2), 1_000, 2_000, "v2 turno 1"),
                SegmentoTrascrittoView(SegmentoId(3), VoceId(1), 2_000, 3_000, "v1 turno 2"),
            ),
            vista?.segmenti,
        )
    }

    @Test
    fun `AC-167 etichetta 'Voce n' usa il VoceId anche con un buco nella sequenza`() {
        val trascritto = Trascritto.crea(
            REGISTRAZIONE,
            DURATA_TRASCRITTO_MS,
            listOf(
                unSegmentoIniziale(voceIndice = 10, inizioMs = 0, fineMs = 1_000, testo = "a"),
                unSegmentoIniziale(voceIndice = 20, inizioMs = 1_000, fineMs = 2_000, testo = "b"),
                unSegmentoIniziale(voceIndice = 30, inizioMs = 2_000, fineMs = 3_000, testo = "c"),
            ),
        ).atteso().aggregato
        // VoceId(1) is absorbed into VoceId(2): only VoceId(2) and VoceId(3) survive — a gap at 1.
        val fuso = trascritto.unisci(sopravvive = VoceId(2), rimossa = VoceId(1)).atteso()
        check(fuso.sopravvissuta == VoceId(2) && fuso.rimossa == VoceId(1))
        trascritti.salva(trascritto)

        val vista = query.vista(REGISTRAZIONE)

        // Labelling by position ("Voce ${i+1}") would yield "Voce 1"/"Voce 2": this asserts the VoceId.
        assertEquals(
            listOf(
                VoceTrascrittoView(VoceId(2), "Voce 2"),
                VoceTrascrittoView(VoceId(3), "Voce 3"),
            ),
            vista?.voci,
        )
    }

    @Test
    fun `AC-167 dopo una Revisione (dividi) i segmenti restano in ordine di tempo e portano il nuovo voceId`() {
        val trascritto = unTrascritto(voci = 2, segmentiPerVoce = 3, registrazioneId = REGISTRAZIONE)
        // Origine VoceId(1) owns segmenti {1, 3, 5} (INV-7 order); split off segmento 3 into a new Voce.
        trascritto.dividi(origine = VoceId(1), segmenti = setOf(SegmentoId(3))).atteso()
        trascritti.salva(trascritto)

        val vista = query.vista(REGISTRAZIONE)

        assertEquals(
            listOf(
                SegmentoTrascrittoView(SegmentoId(1), VoceId(1), 0, 1_000, "testo 0@0"),
                SegmentoTrascrittoView(SegmentoId(2), VoceId(2), 1_000, 2_000, "testo 1@1000"),
                SegmentoTrascrittoView(SegmentoId(3), VoceId(3), 2_000, 3_000, "testo 0@2000"),
                SegmentoTrascrittoView(SegmentoId(4), VoceId(2), 3_000, 4_000, "testo 1@3000"),
                SegmentoTrascrittoView(SegmentoId(5), VoceId(1), 4_000, 5_000, "testo 0@4000"),
                SegmentoTrascrittoView(SegmentoId(6), VoceId(2), 5_000, 6_000, "testo 1@5000"),
            ),
            vista?.segmenti,
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
