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
import kotlin.test.assertEquals

/** Tests of [Documento]: pure projection, no ports, no I/O — [INV-23]/[INV-24]. */
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
        assertEquals(
            prima.markdown.toByteArray(Charsets.UTF_8).toList(),
            seconda.markdown.toByteArray(Charsets.UTF_8).toList(),
        )
    }

    @Test
    fun `INV-24 una Voce senza Attribuzione rende come Voce n`() {
        val trascritto = unTrascritto(segmenti = listOf(unSegmento(1, 3, 0, 1_000, "Ciao.")))

        val vista = Documento.proietta(trascritto, nomi = emptyMap())

        assertEquals(true, vista.markdown.contains("**Voce 3** (0:00): Ciao."))
    }

    @Test
    fun `INV-24 una Voce attribuita rende con il Nome che nomi le assegna, anche quello di un eliminato`() {
        // LettoreNomi risolve gia' il Nome di un Parlante eliminato (INV-24 sul suo lato): Documento
        // si limita a usare la stringa ricevuta, senza sapere se il Parlante e' attivo o eliminato.
        val trascritto = unTrascritto(segmenti = listOf(unSegmento(1, 5, 0, 1_000, "Ciao.")))
        val nomi = mapOf(VoceRef(REGISTRAZIONE, VoceId(5)) to "Anna Bianchi")

        val vista = Documento.proietta(trascritto, nomi)

        assertEquals(true, vista.markdown.contains("**Anna Bianchi** (0:00): Ciao."))
    }

    @Test
    fun `INV-24 i Segmenti rendono in ordine di inizio tra le Voci, a parita per segmentoId, con input scomposto`() {
        val fuoriOrdine = listOf(
            unSegmento(3, 1, 12_000, 15_000, "Terzo."),
            unSegmento(1, 1, 0, 4_000, "Primo."),
            unSegmento(2, 2, 4_000, 9_000, "Secondo pari (segmentoId 2)."),
            unSegmento(4, 1, 4_000, 9_000, "Secondo pari (segmentoId 4)."),
        )
        val trascritto = unTrascritto(segmenti = fuoriOrdine)

        val vista = Documento.proietta(trascritto, nomi = emptyMap())

        val testiInOrdine = listOf("Primo.", "Secondo pari (segmentoId 2).", "Secondo pari (segmentoId 4).", "Terzo.")
        val posizioni = testiInOrdine.map { vista.markdown.indexOf(it) }
        assertEquals(true, posizioni.all { it >= 0 })
        assertEquals(posizioni.sorted(), posizioni)
    }

    @Test
    fun `INV-24 il testo resta verbatim con italiano e inglese misti`() {
        val testo = "Yes, let's ship it: pero' prima la code review... «ok» (aeiou) 100%!"
        val trascritto = unTrascritto(segmenti = listOf(unSegmento(1, 1, 0, 1_000, testo)))

        val vista = Documento.proietta(trascritto, nomi = emptyMap())

        assertEquals(true, vista.markdown.contains(testo))
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

        assertEquals(true, vista.markdown.contains("(0:05): Cinque secondi."))
        assertEquals(true, vista.markdown.contains("(65:00): 65 minuti."))
    }

    @Test
    fun `AC-104 nomeFile compone data e titolo come AAAA-MM-DD titolo md`() {
        assertEquals("2026-09-12 Riunione.md", Documento.nomeFile(LocalDate.of(2026, 9, 12), "Riunione"))
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
