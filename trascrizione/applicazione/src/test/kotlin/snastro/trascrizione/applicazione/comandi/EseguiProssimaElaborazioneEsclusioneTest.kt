package snastro.trascrizione.applicazione.comandi

import snastro.kernel.DispatcherEventiFinta
import snastro.kernel.ElaborazioneId
import snastro.kernel.Esito
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.kernel.UnitaDiLavoro
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.atteso
import snastro.trascrizione.applicazione.eventi.ElaborazioneAvviata
import snastro.trascrizione.applicazione.porte.Allineatore
import snastro.trascrizione.applicazione.porte.AllineatoreFinta
import snastro.trascrizione.applicazione.porte.DecodificatoreAudio
import snastro.trascrizione.applicazione.porte.DecodificatoreAudioFinta
import snastro.trascrizione.applicazione.porte.Diarizzatore
import snastro.trascrizione.applicazione.porte.DiarizzatoreFinta
import snastro.trascrizione.applicazione.porte.ElaborazioneRepository
import snastro.trascrizione.applicazione.porte.ElaborazioneRepositoryFinta
import snastro.trascrizione.applicazione.porte.LettoreRegistrazione
import snastro.trascrizione.applicazione.porte.LettoreRegistrazioneFinta
import snastro.trascrizione.applicazione.porte.RegistrazioneVista
import snastro.trascrizione.applicazione.porte.SegnalatoreFase
import snastro.trascrizione.applicazione.porte.SegnalatoreFaseFinta
import snastro.trascrizione.applicazione.porte.TrascrittoRepository
import snastro.trascrizione.applicazione.porte.TrascrittoRepositoryFinta
import snastro.trascrizione.dominio.Elaborazione
import snastro.trascrizione.dominio.ErroreTrascrizione
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * AC-313 (F-G, rework item 1): [EseguiProssimaElaborazione.esclusi] skips the excluded FIFO heads,
 * and [RisultatoAvanzamento] always carries the attempted id — split out of
 * [EseguiProssimaElaborazioneServizioTest] (same fakes, same helper shapes) purely to keep that
 * class under detekt's `LargeClass` threshold.
 */
class EseguiProssimaElaborazioneEsclusioneTest {
    @Test
    fun `AC-313 esclude gli id passati e parte sul prossimo in_attesa idoneo`() {
        val elaborazioni = ElaborazioneRepositoryFinta()
        val trascritti = TrascrittoRepositoryFinta()
        val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(elaborazioni, trascritti))
        val vecchia = RegistrazioneId("registrazione-vecchia")
        val recente = RegistrazioneId("registrazione-recente")
        val riferimentoVecchia = RiferimentoAudio("audio/vecchia.m4a")
        val riferimentoRecente = RiferimentoAudio("audio/recente.m4a")
        val registrazioni = LettoreRegistrazioneFinta(
            mapOf(
                vecchia to unaVista(vecchia, riferimentoVecchia, DURATA),
                recente to unaVista(recente, riferimentoRecente, DURATA),
            ),
        )
        val decodificatore = DecodificatoreAudioFinta(mapOf(riferimentoVecchia to DURATA, riferimentoRecente to DURATA))
        val servizio = servizio(
            eventi.unitaDiLavoro,
            eventi,
            elaborazioni,
            trascritti,
            pipeline(registrazioni = registrazioni, decodificatore = decodificatore),
        )
        val idVecchia = ElaborazioneId("elab-${vecchia.valore}")
        val idRecente = ElaborazioneId("elab-${recente.valore}")
        elaborazioni.salva(unaInAttesa(recente, CREATA_RECENTE)).atteso()
        elaborazioni.salva(unaInAttesa(vecchia, CREATA_VECCHIA)).atteso()

        val risultato = servizio.esegui(EseguiProssimaElaborazione(esclusi = setOf(idVecchia))).atteso()

        assertEquals(RisultatoAvanzamento.Avviata(idRecente), risultato)
        assertEquals(listOf(vecchia), elaborazioni.inAttesa().map { it.registrazioneId }, "l'escluso resta in_attesa")
        assertTrue(elaborazioni.diRegistrazione(recente).single().completata)
    }

    @Test
    fun `AC-313 un abbonato sincrono che rifiuta sempre restituisce AvvioRifiutato con l id, resta in_attesa`() {
        val elaborazioni = ElaborazioneRepositoryFinta()
        val trascritti = TrascrittoRepositoryFinta()
        val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(elaborazioni, trascritti))
        val causa = ErroreTrascrizione.ElaborazioneGiaAperta(REGISTRAZIONE_GUASTO)
        eventi.registraSincrono { evento -> if (evento is ElaborazioneAvviata) Esito.Errore(causa) else Esito.Ok(Unit) }
        val servizio = servizio(eventi.unitaDiLavoro, eventi, elaborazioni, trascritti, pipeline())
        val id = ElaborazioneId("elab-${REGISTRAZIONE_GUASTO.valore}")
        elaborazioni.salva(unaInAttesa(REGISTRAZIONE_GUASTO)).atteso()

        val risultato = servizio.esegui(EseguiProssimaElaborazione()).atteso()

        assertEquals(RisultatoAvanzamento.AvvioRifiutato(id, causa), risultato)
        assertEquals(listOf(id), elaborazioni.inAttesa().map { it.id }, "la transazione di avvio e' stata annullata")
        assertEquals(emptyList(), eventi.pubblicati, "nessun evento sopravvive al rollback")
    }

    private fun servizio(
        uow: UnitaDiLavoro,
        eventi: DispatcherEventiFinta,
        elaborazioni: ElaborazioneRepository,
        trascritti: TrascrittoRepository,
        pipeline: PortePipeline,
    ): EseguiProssimaElaborazioneServizio =
        EseguiProssimaElaborazioneServizio(uow, OROLOGIO, elaborazioni, trascritti, pipeline, eventi)

    private fun pipeline(
        registrazioni: LettoreRegistrazione = LettoreRegistrazioneFinta(),
        decodificatore: DecodificatoreAudio = DecodificatoreAudioFinta(emptyMap()),
        diarizzatore: Diarizzatore = DiarizzatoreFinta(),
        allineatore: Allineatore = AllineatoreFinta(),
        segnalatore: SegnalatoreFase = SegnalatoreFaseFinta(),
    ): PortePipeline = PortePipeline(registrazioni, decodificatore, diarizzatore, allineatore, segnalatore)

    private fun unaVista(id: RegistrazioneId, riferimento: RiferimentoAudio, durataMs: Long): RegistrazioneVista =
        RegistrazioneVista(
            registrazioneId = id,
            progettoId = PROGETTO,
            titolo = "Riunione",
            riferimentoAudio = riferimento,
            dataRegistrazione = LocalDate.of(2026, 9, 20),
            durataMs = durataMs,
        )

    private fun unaInAttesa(id: RegistrazioneId, creataAlle: Instant = CREATA_VECCHIA): Elaborazione =
        Elaborazione.accoda(ElaborazioneId("elab-${id.valore}"), id, creataAlle, numeroPersone = null).aggregato

    private companion object {
        const val DURATA = 2_000L
        val OROLOGIO: Clock = Clock.fixed(Instant.parse("2026-09-23T10:10:00Z"), ZoneOffset.UTC)
        val CREATA_VECCHIA: Instant = Instant.parse("2026-09-23T09:00:00Z")
        val CREATA_RECENTE: Instant = Instant.parse("2026-09-23T09:05:00Z")
        val PROGETTO = ProgettoId("progetto-1")
        val REGISTRAZIONE_GUASTO = RegistrazioneId("registrazione-1")
    }
}
