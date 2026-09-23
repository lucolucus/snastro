package snastro.documento.applicazione.letture

import org.junit.jupiter.api.Test
import snastro.documento.applicazione.porte.SegmentoVista
import snastro.documento.applicazione.porte.TrascrittoTesto
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import java.time.LocalDate
import java.util.Locale
import kotlin.random.Random
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Tests of [Documento]: pure projection, no ports, no I/O — [INV-23]/[INV-24]/AC-320/AC-321. */
class DocumentoTest {

    @Test
    fun `INV-23 stessi input danno due DocumentoVista uguali (markdown e nomeFile byte-identici)`() {
        val trascritto = unTrascritto(
            segmenti = listOf(
                unSegmento(1, 1, 0, 3_000, "Frase uno."),
                unSegmento(2, 2, 3_000, 6_000, "Frase due."),
            ),
        )
        val nomi = mapOf(VoceRef(REGISTRAZIONE, VoceId(1)) to "Marco")

        val prima = Documento.proietta(trascritto, nomi)
        val seconda = Documento.proietta(trascritto.copy(), nomi.toMap())

        assertEquals(prima, seconda)
        assertContentEquals(
            prima.markdown.toByteArray(Charsets.UTF_8),
            seconda.markdown.toByteArray(Charsets.UTF_8),
        )
    }

    @Test
    fun `INV-23 un ordine diverso degli stessi Segmenti in ingresso produce markdown byte-identico`() {
        val segmenti = listOf(
            unSegmento(1, 1, 0, 3_000, "Primo."),
            unSegmento(2, 2, 3_000, 6_000, "Secondo."),
            unSegmento(3, 1, 6_000, 9_000, "Terzo."),
            unSegmento(4, 3, 9_000, 12_000, "Quarto."),
            unSegmento(5, 2, 12_000, 15_000, "Quinto."),
        )
        val nomi = mapOf(
            VoceRef(REGISTRAZIONE, VoceId(1)) to "Marco",
            VoceRef(REGISTRAZIONE, VoceId(2)) to "Anna",
        )
        val canonico = Documento.proietta(unTrascritto(segmenti = segmenti), nomi)

        val rimescolato = Documento.proietta(unTrascritto(segmenti = segmenti.shuffled(Random(42))), nomi)

        assertContentEquals(
            canonico.markdown.toByteArray(Charsets.UTF_8),
            rimescolato.markdown.toByteArray(Charsets.UTF_8),
        )
    }

    @Test
    fun `INV-24 una Voce senza Attribuzione rende come Voce n`() {
        val trascritto = unTrascritto(segmenti = listOf(unSegmento(1, 3, 0, 1_000, "Ciao.")))

        val vista = Documento.proietta(trascritto, nomi = emptyMap())

        assertTrue(vista.markdown.contains("**Voce 3** (0:00): Ciao."))
    }

    @Test
    fun `INV-24 una Voce attribuita rende con il Nome che nomi le assegna, anche quello di un eliminato`() {
        // LettoreNomi risolve gia' il Nome di un Parlante eliminato (INV-24 sul suo lato): Documento
        // si limita a usare la stringa ricevuta, senza sapere se il Parlante e' attivo o eliminato.
        val trascritto = unTrascritto(segmenti = listOf(unSegmento(1, 5, 0, 1_000, "Ciao.")))
        val nomi = mapOf(VoceRef(REGISTRAZIONE, VoceId(5)) to "Anna Bianchi")

        val vista = Documento.proietta(trascritto, nomi)

        assertTrue(vista.markdown.contains("**Anna Bianchi** (0:00): Ciao."))
    }

    @Test
    fun `INV-24 un Nome mappato per un'altra registrazioneId non si applica, la Voce resta Voce n`() {
        val altraRegistrazione = RegistrazioneId("altra-registrazione")
        val trascritto = unTrascritto(segmenti = listOf(unSegmento(1, 5, 0, 1_000, "Ciao.")))
        val nomi = mapOf(VoceRef(altraRegistrazione, VoceId(5)) to "Anna Bianchi")

        val vista = Documento.proietta(trascritto, nomi)

        assertTrue(vista.markdown.contains("**Voce 5** (0:00): Ciao."))
        assertFalse(vista.markdown.contains("Anna Bianchi"))
    }

    @Test
    fun `INV-24 i Segmenti rendono in ordine di inizio, a parita per segmentoId, con input in ordine inverso`() {
        // I due Segmenti a pari inizioMs (4_000) arrivano qui in ordine di segmentoId INVERSO
        // (4 prima di 2): un ordinamento stabile che ignorasse il tie-break per segmentoId
        // manterrebbe questo stesso ordine in uscita e il test fallirebbe.
        val fuoriOrdine = listOf(
            unSegmento(3, 1, 12_000, 15_000, "Terzo."),
            unSegmento(4, 1, 4_000, 9_000, "Secondo pari (segmentoId 4)."),
            unSegmento(1, 1, 0, 4_000, "Primo."),
            unSegmento(2, 2, 4_000, 9_000, "Secondo pari (segmentoId 2)."),
        )
        val trascritto = unTrascritto(segmenti = fuoriOrdine)

        val vista = Documento.proietta(trascritto, nomi = emptyMap())

        val testiInOrdine = listOf("Primo.", "Secondo pari (segmentoId 2).", "Secondo pari (segmentoId 4).", "Terzo.")
        val posizioni = testiInOrdine.map { vista.markdown.indexOf(it) }
        assertTrue(posizioni.all { it >= 0 })
        assertEquals(posizioni.sorted(), posizioni)
    }

    @Test
    fun `INV-24 il testo resta verbatim con italiano e inglese misti`() {
        val testo = "Yes, let's ship it: pero' prima la code review... «ok» (aeiou) 100%!"
        val trascritto = unTrascritto(segmenti = listOf(unSegmento(1, 1, 0, 1_000, testo)))

        val vista = Documento.proietta(trascritto, nomi = emptyMap())

        assertTrue(vista.markdown.contains(testo))
    }

    @Test
    fun `AC-103 il markdown ha titolo, intestazione data, una riga per Segmento e righe vuote di separazione`() {
        val trascritto = unTrascritto(
            titolo = "Riunione di lavoro",
            data = LocalDate.of(2026, 9, 12),
            segmenti = listOf(
                unSegmento(1, 1, 0, 3_000, "Frase uno."),
                unSegmento(2, 1, 3_000, 6_000, "Frase due."),
                unSegmento(3, 2, 6_000, 9_000, "Frase tre."),
            ),
        )
        val nomi = mapOf(VoceRef(REGISTRAZIONE, VoceId(1)) to "Marco")

        val vista = Documento.proietta(trascritto, nomi)

        assertEquals(
            "# Riunione di lavoro\n\n" +
                "Registrata il 12/09/2026\n\n" +
                "**Marco** (0:00): Frase uno.\n\n" +
                "**Marco** (0:03): Frase due.\n\n" +
                "**Voce 2** (0:06): Frase tre.\n\n",
            vista.markdown,
        )
    }

    @Test
    fun `AC-103 mm non si azzera a 60 minuti, ss resta a due cifre`() {
        val trascritto = unTrascritto(
            segmenti = listOf(
                unSegmento(1, 1, 5_000, 10_000, "Cinque secondi."),
                unSegmento(2, 1, 3_900_000, 3_905_000, "65 minuti."),
            ),
        )

        val vista = Documento.proietta(trascritto, nomi = emptyMap())

        assertTrue(vista.markdown.contains("(0:05): Cinque secondi."))
        assertTrue(vista.markdown.contains("(65:00): 65 minuti."))
    }

    @Test
    fun `AC-103 59 min 59 s non arrotonda a 60_00 e i millisecondi troncano per difetto`() {
        val trascritto = unTrascritto(
            segmenti = listOf(
                unSegmento(1, 1, 3_599_999, 3_600_500, "Al limite dei 60 minuti."),
                unSegmento(2, 2, 3_600_000, 3_601_000, "Esattamente 60 minuti."),
                unSegmento(3, 3, 1_999, 2_500, "Un secondo e novecentonovantanove millisecondi."),
            ),
        )

        val vista = Documento.proietta(trascritto, nomi = emptyMap())

        assertTrue(vista.markdown.contains("(59:59): Al limite dei 60 minuti."))
        assertTrue(vista.markdown.contains("(60:00): Esattamente 60 minuti."))
        assertTrue(vista.markdown.contains("(0:01): Un secondo e novecentonovantanove millisecondi."))
    }

    @Test
    fun `AC-103 e AC-320 la formattazione non dipende dal Locale di default della JVM`() {
        val defaultOriginale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-SA")) // usa cifre non ASCII se non fissato Locale.ROOT
            val trascritto = unTrascritto(data = LocalDate.of(2026, 9, 12), segmenti = emptyList())

            val vista = Documento.proietta(trascritto, nomi = emptyMap())

            assertTrue(vista.markdown.contains("Registrata il 12/09/2026"))
            assertEquals("2026-09-12 Riunione.md", vista.nomeFile)
        } finally {
            Locale.setDefault(defaultOriginale)
        }
    }

    @Test
    fun `AC-104 nomeFile compone data e titolo come AAAA-MM-DD titolo md`() {
        assertEquals("2026-09-12 Riunione.md", Documento.nomeFile(LocalDate.of(2026, 9, 12), "Riunione"))
    }

    @Test
    fun `AC-320 pulisci sostituisce i caratteri non validi, rifila, gestisce riservati e vuoto (tabella)`() {
        val casi = listOf(
            "Riunione 3/10: budget?" to "Riunione 3_10_ budget_",
            "  Nota finale.. " to "Nota finale",
            "a<b>c|d*e\"f" to "a_b_c_d_e_f",
            "prima\u0007dopo" to "prima_dopo",
            "con" to "con_",
            "CON" to "CON_",
            "com1" to "com1_",
            "..." to "registrazione",
            "" to "registrazione",
            "é" to "é", // 'e' + accento combinante (NFD) -> 'é' precomposto (NFC)
            "Titolo pulito" to "Titolo pulito",
        )

        for ((titolo, atteso) in casi) {
            assertEquals(atteso, Documento.pulisci(titolo), "titolo='$titolo'")
        }
    }

    @Test
    fun `AC-320 nomeFile applica pulisci al titolo (stessa tabella, con prefisso data e suffisso md)`() {
        val data = LocalDate.of(2026, 9, 12)

        assertEquals("2026-09-12 Riunione 3_10_ budget_.md", Documento.nomeFile(data, "Riunione 3/10: budget?"))
        assertEquals("2026-09-12 Nota finale.md", Documento.nomeFile(data, "  Nota finale.. "))
        assertEquals("2026-09-12 a_b_c_d_e_f.md", Documento.nomeFile(data, "a<b>c|d*e\"f"))
        assertEquals("2026-09-12 con_.md", Documento.nomeFile(data, "con"))
        assertEquals("2026-09-12 registrazione.md", Documento.nomeFile(data, "..."))
        assertEquals("2026-09-12 registrazione.md", Documento.nomeFile(data, ""))
    }

    @Test
    fun `AC-320 pulisci e' idempotente, nomeFile(d, pulisci(t)) uguale a nomeFile(d, t)`() {
        val data = LocalDate.of(2026, 9, 12)
        val titoli = listOf("Riunione 3/10: budget?", "  Nota finale.. ", "con", "...", "", "Titolo pulito")

        for (titolo in titoli) {
            assertEquals(Documento.pulisci(titolo), Documento.pulisci(Documento.pulisci(titolo)))
            assertEquals(Documento.nomeFile(data, titolo), Documento.nomeFile(data, Documento.pulisci(titolo)))
        }
    }

    @Test
    fun `AC-321 un titolo di 300 caratteri a 2 byte resta entro il limite e non spezza un code point`() {
        val carattereDueByte = 'è' // 'è', U+00E8, 2 byte in UTF-8
        val titolo = carattereDueByte.toString().repeat(300)

        val pulito = Documento.pulisci(titolo)
        val nomeFile = Documento.nomeFile(LocalDate.of(2026, 9, 12), titolo)

        assertTrue(
            nomeFile.toByteArray(Charsets.UTF_8).size <= 251,
            "nomeFile e' lungo ${nomeFile.toByteArray(Charsets.UTF_8).size} byte",
        )
        assertTrue(pulito.toByteArray(Charsets.UTF_8).size <= 237)
        assertTrue(pulito.isNotEmpty())
        assertTrue(pulito.all { it == carattereDueByte }, "nessun code point spezzato: '$pulito'")
        assertFalse(pulito.endsWith(" "))
        assertFalse(pulito.endsWith("."))
    }

    @Test
    fun `AC-321 un titolo di 100 emoji (coppie surrogate) non spezza mai una coppia surrogata`() {
        val emoji = "😀" // U+1F600, 4 byte UTF-8, coppia surrogata in UTF-16
        val titolo = emoji.repeat(100)

        val pulito = Documento.pulisci(titolo)
        val nomeFile = Documento.nomeFile(LocalDate.of(2026, 9, 12), titolo)

        assertTrue(
            nomeFile.toByteArray(Charsets.UTF_8).size <= 251,
            "nomeFile e' lungo ${nomeFile.toByteArray(Charsets.UTF_8).size} byte",
        )
        assertTrue(pulito.toByteArray(Charsets.UTF_8).size <= 237)
        assertEquals(0, pulito.length % 2, "una coppia surrogata non deve restare spezzata a meta'")
        assertTrue(
            pulito.codePoints().toArray().all { Character.charCount(it) == 2 },
            "nessuna surrogata solitaria: '$pulito'",
        )
        assertFalse(pulito.endsWith(" "))
        assertFalse(pulito.endsWith("."))
    }

    private companion object {
        val REGISTRAZIONE = RegistrazioneId("registrazione-1")

        fun unTrascritto(
            titolo: String = "Riunione",
            data: LocalDate = LocalDate.of(2026, 1, 1),
            segmenti: List<SegmentoVista>,
        ) = TrascrittoTesto(REGISTRAZIONE, titolo, data, segmenti)

        fun unSegmento(segmentoId: Int, voceId: Int, inizioMs: Long, fineMs: Long, testo: String) =
            SegmentoVista(SegmentoId(segmentoId), VoceId(voceId), IntervalloMs(inizioMs, fineMs), testo)
    }
}
