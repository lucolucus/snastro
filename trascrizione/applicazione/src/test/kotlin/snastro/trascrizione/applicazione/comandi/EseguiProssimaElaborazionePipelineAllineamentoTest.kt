package snastro.trascrizione.applicazione.comandi

import snastro.kernel.CampioniAudio
import snastro.kernel.DispatcherEventiFinta
import snastro.kernel.ElaborazioneId
import snastro.kernel.IntervalloMs
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.atteso
import snastro.trascrizione.applicazione.porte.Allineatore
import snastro.trascrizione.applicazione.porte.DecodificatoreAudioFinta
import snastro.trascrizione.applicazione.porte.DiarizzatoreFinta
import snastro.trascrizione.applicazione.porte.ElaborazioneRepositoryFinta
import snastro.trascrizione.applicazione.porte.LettoreRegistrazioneFinta
import snastro.trascrizione.applicazione.porte.RegistrazioneVista
import snastro.trascrizione.applicazione.porte.SegmentoGrezzo
import snastro.trascrizione.applicazione.porte.SegnalatoreFaseFinta
import snastro.trascrizione.applicazione.porte.TrascrittoRepositoryFinta
import snastro.trascrizione.applicazione.porte.Turno
import snastro.trascrizione.dominio.Elaborazione
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * REWORK ADR 0015: what the pipeline does with the [Allineatore]'s output at its two edges — an empty
 * result (regola 2/7) and preserved overlaps across Voci (regola 9, INV-7). Split from
 * [EseguiProssimaElaborazioneServizioTest] (`LargeClass`), same fixture shape as
 * [EseguiProssimaElaborazioneNumeroPersoneTest].
 */
class EseguiProssimaElaborazionePipelineAllineamentoTest {
    @Test
    fun `AC-386 Allineatore che restituisce lista vuota diventa fallita nessun parlato rilevato`() {
        val id = RegistrazioneId("registrazione-1")
        val riferimento = RiferimentoAudio("audio/registrazione-1.m4a")
        val elaborazioni = ElaborazioneRepositoryFinta()
        val trascritti = TrascrittoRepositoryFinta()
        val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(elaborazioni, trascritti))
        val registrazioni = LettoreRegistrazioneFinta(mapOf(id to unaVista(id, riferimento)))
        val decodificatore = DecodificatoreAudioFinta(mapOf(riferimento to DURATA))
        // il diarizzatore trova un turno; l'Allineatore lo scarta comunque (ogni turno corto/senza testo).
        val diarizzatore = DiarizzatoreFinta(listOf(Turno(IntervalloMs(0, 500), voceIndice = 0)))
        val pipeline = PortePipeline(
            registrazioni,
            decodificatore,
            diarizzatore,
            AllineatoreSenzaSegmenti(),
            SegnalatoreFaseFinta(),
        )
        val servizio = EseguiProssimaElaborazioneServizio(
            eventi.unitaDiLavoro,
            OROLOGIO,
            elaborazioni,
            trascritti,
            pipeline,
            eventi,
        )
        elaborazioni.salva(unaInAttesa(id)).atteso()

        servizio.esegui(EseguiProssimaElaborazione()).atteso()

        val salvata = elaborazioni.diRegistrazione(id).single()
        assertTrue(salvata.fallita)
        assertEquals("nessun parlato rilevato", salvata.motivoFallimento)
        assertNull(trascritti.trova(id), "AC-386: nessun Trascritto esiste")
        assertEquals(emptyList(), trascritti.conTrascritto())
    }

    @Test
    fun `AC-387 due Segmenti grezzi sovrapposti di Voci diverse arrivano entrambi nel Trascritto senza tagli`() {
        val id = RegistrazioneId("registrazione-2")
        val riferimento = RiferimentoAudio("audio/registrazione-2.m4a")
        val elaborazioni = ElaborazioneRepositoryFinta()
        val trascritti = TrascrittoRepositoryFinta()
        val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(elaborazioni, trascritti))
        val registrazioni = LettoreRegistrazioneFinta(mapOf(id to unaVista(id, riferimento)))
        val decodificatore = DecodificatoreAudioFinta(mapOf(riferimento to DURATA))
        val pipeline = PortePipeline(
            registrazioni,
            decodificatore,
            DiarizzatoreFinta(),
            AllineatoreSegmentiSovrapposti(),
            SegnalatoreFaseFinta(),
        )
        val servizio = EseguiProssimaElaborazioneServizio(
            eventi.unitaDiLavoro,
            OROLOGIO,
            elaborazioni,
            trascritti,
            pipeline,
            eventi,
        )
        elaborazioni.salva(unaInAttesa(id)).atteso()

        servizio.esegui(EseguiProssimaElaborazione()).atteso()

        assertTrue(elaborazioni.diRegistrazione(id).single().completata)
        val trascritto = checkNotNull(trascritti.trova(id))
        assertEquals(2, trascritto.voci.size, "AC-387: Voci diverse restano distinte, nessuna fusione")
        assertEquals(
            listOf(IntervalloMs(0, 1_000), IntervalloMs(500, 1_500)),
            trascritto.segmenti.sortedBy { it.intervallo.inizioMs }.map { it.intervallo },
            "AC-387: nessun Segmento e' tagliato, unito o scartato dalla pipeline",
        )
    }

    private fun unaInAttesa(id: RegistrazioneId): Elaborazione =
        Elaborazione.accoda(ElaborazioneId("elab-${id.valore}"), id, CREATA, numeroPersone = null).aggregato

    private fun unaVista(id: RegistrazioneId, riferimento: RiferimentoAudio): RegistrazioneVista = RegistrazioneVista(
        registrazioneId = id,
        progettoId = ProgettoId("progetto-1"),
        titolo = "Riunione",
        riferimentoAudio = riferimento,
        dataRegistrazione = LocalDate.of(2026, 9, 20),
        durataMs = DURATA,
    )

    private companion object {
        const val DURATA = 2_000L
        val OROLOGIO: Clock = Clock.fixed(Instant.parse("2026-09-23T10:10:00Z"), ZoneOffset.UTC)
        val CREATA: Instant = Instant.parse("2026-09-23T09:00:00Z")
    }
}

/** [Allineatore] that never produces a Segmento, whatever the Turni (AC-386, ADR 0015 regola 2/7). */
private class AllineatoreSenzaSegmenti : Allineatore {
    override fun allinea(campioni: CampioniAudio, turni: List<Turno>): List<SegmentoGrezzo> = emptyList()
}

/** [Allineatore] returning two OVERLAPPING SegmentoGrezzo of different Voci, untouched (AC-387, INV-7). */
private class AllineatoreSegmentiSovrapposti : Allineatore {
    override fun allinea(campioni: CampioniAudio, turni: List<Turno>): List<SegmentoGrezzo> = listOf(
        SegmentoGrezzo(0, IntervalloMs(0, 1_000), "voce 0 0-1000"),
        SegmentoGrezzo(1, IntervalloMs(500, 1_500), "voce 1 500-1500"),
    )
}
