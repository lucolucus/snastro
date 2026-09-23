package snastro.trascrizione.applicazione.comandi

import snastro.kernel.DispatcherEventiFinta
import snastro.kernel.ElaborazioneId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.atteso
import snastro.trascrizione.applicazione.porte.AllineatoreFinta
import snastro.trascrizione.applicazione.porte.DecodificatoreAudioFinta
import snastro.trascrizione.applicazione.porte.DiarizzatoreFinta
import snastro.trascrizione.applicazione.porte.ElaborazioneRepositoryFinta
import snastro.trascrizione.applicazione.porte.LettoreRegistrazioneFinta
import snastro.trascrizione.applicazione.porte.RegistrazioneVista
import snastro.trascrizione.applicazione.porte.SegnalatoreFaseFinta
import snastro.trascrizione.applicazione.porte.TrascrittoRepositoryFinta
import snastro.trascrizione.dominio.Elaborazione
import snastro.trascrizione.dominio.NumeroPersone
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** AC-370 (ADR 0014): the pipeline hands the Diarizzatore the Numero di persone of the Elaborazione it runs. */
class EseguiProssimaElaborazioneNumeroPersoneTest {
    @Test
    fun `AC-370 la pipeline passa al Diarizzatore il numeroPersone dell Elaborazione riletta, assente compreso`() {
        val elaborazioni = ElaborazioneRepositoryFinta()
        val trascritti = TrascrittoRepositoryFinta()
        val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(elaborazioni, trascritti))
        val quattro = NumeroPersone.di(4).atteso()
        // Queued before the "restart": the new service only sees what it re-reads from the repository.
        elaborazioni.salva(unaInAttesa(CON_4, Instant.parse("2026-09-23T09:00:00Z"), quattro)).atteso()
        elaborazioni.salva(unaInAttesa(SENZA, Instant.parse("2026-09-23T09:05:00Z"), numeroPersone = null)).atteso()
        val diarizzatore = DiarizzatoreFinta()
        val pipeline = PortePipeline(
            LettoreRegistrazioneFinta(RIFERIMENTI.mapValues { (id, riferimento) -> unaVista(id, riferimento) }),
            DecodificatoreAudioFinta(RIFERIMENTI.values.associateWith { DURATA }),
            diarizzatore,
            AllineatoreFinta(),
            SegnalatoreFaseFinta(),
        )
        val uow = eventi.unitaDiLavoro
        val servizio = EseguiProssimaElaborazioneServizio(uow, OROLOGIO, elaborazioni, trascritti, pipeline, eventi)

        servizio.esegui(EseguiProssimaElaborazione).atteso()
        servizio.esegui(EseguiProssimaElaborazione).atteso()

        assertEquals(listOf(quattro, null), diarizzatore.numeroPersoneRicevuti)
        val eseguita = elaborazioni.diRegistrazione(CON_4).single()
        assertTrue(eseguita.completata)
        assertEquals(quattro, eseguita.numeroPersone)
    }

    private fun unaInAttesa(id: RegistrazioneId, creataAlle: Instant, numeroPersone: NumeroPersone?): Elaborazione =
        Elaborazione.accoda(ElaborazioneId("elab-${id.valore}"), id, creataAlle, numeroPersone).aggregato

    private fun unaVista(id: RegistrazioneId, riferimento: RiferimentoAudio) = RegistrazioneVista(
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
        val CON_4 = RegistrazioneId("registrazione-con-4")
        val SENZA = RegistrazioneId("registrazione-senza")
        val RIFERIMENTI = mapOf(
            CON_4 to RiferimentoAudio("audio/con-4.m4a"),
            SENZA to RiferimentoAudio("audio/senza.m4a"),
        )
    }
}
