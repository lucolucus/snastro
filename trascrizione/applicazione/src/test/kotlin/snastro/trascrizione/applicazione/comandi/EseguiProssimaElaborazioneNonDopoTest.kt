package snastro.trascrizione.applicazione.comandi

import snastro.kernel.DispatcherEventiFinta
import snastro.kernel.ElaborazioneId
import snastro.kernel.RegistrazioneId
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.atteso
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
import snastro.trascrizione.applicazione.porte.SegnalatoreFase
import snastro.trascrizione.applicazione.porte.SegnalatoreFaseFinta
import snastro.trascrizione.applicazione.porte.TrascrittoRepositoryFinta
import snastro.trascrizione.dominio.Elaborazione
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * ADR 0023 §2 (AC-S20..AC-S22, rework item 2): [EseguiProssimaElaborazione.nonDopo] — the bound the
 * shared queue (`:avvio`) passes when arbitrating this source against a `Riassunto` source. Split out
 * of [EseguiProssimaElaborazioneServizioTest] (same fakes/helper shapes), purely to keep that class
 * under detekt's `LargeClass` threshold, like [EseguiProssimaElaborazioneEsclusioneTest].
 */
class EseguiProssimaElaborazioneNonDopoTest {
    @Test
    fun `AC-S20 con nonDopo nullo il comportamento e invariato, la piu vecchia in_attesa parte`() {
        val elaborazioni = ElaborazioneRepositoryFinta()
        val servizio = servizio(elaborazioni)
        elaborazioni.salva(unaInAttesa(REGISTRAZIONE, CREATA_ALLE)).atteso()

        val risultato = servizio.esegui(EseguiProssimaElaborazione(nonDopo = null)).atteso()

        assertEquals(RisultatoAvanzamento.Avviata(ID), risultato)
    }

    @Test
    fun `AC-S21 nonDopo uguale a creataAlle rivendica la testa, il confronto e inclusivo`() {
        val elaborazioni = ElaborazioneRepositoryFinta()
        val servizio = servizio(elaborazioni)
        elaborazioni.salva(unaInAttesa(REGISTRAZIONE, CREATA_ALLE)).atteso()

        val risultato = servizio.esegui(EseguiProssimaElaborazione(nonDopo = CREATA_ALLE)).atteso()

        assertEquals(RisultatoAvanzamento.Avviata(ID), risultato)
        assertFalse(elaborazioni.diRegistrazione(REGISTRAZIONE).single().inAttesa, "la testa e stata avviata")
    }

    @Test
    fun `AC-S21 nonDopo di un millisecondo prima non rivendica nulla, la testa resta in_attesa`() {
        val elaborazioni = ElaborazioneRepositoryFinta()
        val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(elaborazioni, TrascrittoRepositoryFinta()))
        val servizio = servizio(elaborazioni, eventi)
        elaborazioni.salva(unaInAttesa(REGISTRAZIONE, CREATA_ALLE)).atteso()

        val risultato = servizio.esegui(EseguiProssimaElaborazione(nonDopo = CREATA_ALLE.minusMillis(1))).atteso()

        assertEquals(RisultatoAvanzamento.NessunElemento, risultato)
        val rimasta = elaborazioni.diRegistrazione(REGISTRAZIONE).single()
        assertTrue(rimasta.inAttesa, "nulla scritto: la testa resta in_attesa")
        assertEquals(emptyList(), eventi.pubblicati, "nessun evento pubblicato")
    }

    @Test
    fun `AC-S22 il vincolo e onorato dentro la transazione, sulla testa appena letta, non su quella annullata`() {
        val elaborazioni = ElaborazioneRepositoryFinta()
        val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(elaborazioni, TrascrittoRepositoryFinta()))
        val servizio = servizio(elaborazioni, eventi)
        val annullaServizio = AnnullaElaborazioneServizio(eventi.unitaDiLavoro, elaborazioni, eventi)
        val vecchia = RegistrazioneId("registrazione-vecchia")
        val nuova = RegistrazioneId("registrazione-nuova")
        val idVecchia = ElaborazioneId("elab-vecchia")
        val idNuova = ElaborazioneId("elab-nuova")
        val creataVecchia = CREATA_ALLE
        val creataNuova = CREATA_ALLE.plusSeconds(60)
        elaborazioni.salva(Elaborazione.accoda(idVecchia, vecchia, creataVecchia, numeroPersone = null).aggregato)
            .atteso()
        elaborazioni.salva(Elaborazione.accoda(idNuova, nuova, creataNuova, numeroPersone = null).aggregato).atteso()
        // la testa letta dal coordinatore (idVecchia, creataVecchia) viene annullata prima della rivendicazione:
        annullaServizio.esegui(AnnullaElaborazione(idVecchia)).atteso()
        val pubblicatiDopoAnnullamento = eventi.pubblicati.size

        // un vincolo pensato per la testa annullata (tra creataVecchia e creataNuova): la nuova testa (idNuova) lo
        // supera — non deve essere rivendicata al posto di quella scomparsa.
        val vincolo = creataVecchia.plusSeconds(30)
        val risultato = servizio.esegui(EseguiProssimaElaborazione(nonDopo = vincolo)).atteso()

        assertEquals(RisultatoAvanzamento.NessunElemento, risultato)
        val rimasta = elaborazioni.diRegistrazione(nuova).single()
        assertTrue(rimasta.inAttesa, "la nuova testa non e stata rivendicata")
        assertEquals(pubblicatiDopoAnnullamento, eventi.pubblicati.size, "nessun nuovo evento dalla rivendicazione")
    }

    @Test
    fun `A37 il vincolo nonDopo e verificato mentre la transazione e gia aperta, sulla testa letta ora`() {
        val elaborazioni = ElaborazioneRepositoryFinta()
        val uowFinta = UnitaDiLavoroFinta(elaborazioni, TrascrittoRepositoryFinta())
        val eventi = DispatcherEventiFinta(uowFinta)
        val servizio = servizio(elaborazioni, eventi)
        elaborazioni.salva(unaInAttesa(REGISTRAZIONE, CREATA_ALLE)).atteso()
        var transazioneApertaAllaLettura: Boolean? = null
        val elaborazioniOsservate = object : ElaborazioneRepository by elaborazioni {
            override fun inAttesa(): List<Elaborazione> {
                transazioneApertaAllaLettura = uowFinta.transazioneAperta // A37: leggo ORA, non prima/fuori
                return elaborazioni.inAttesa()
            }
        }
        val servizioOsservato = EseguiProssimaElaborazioneServizio(
            uowFinta,
            OROLOGIO,
            elaborazioniOsservate,
            TrascrittoRepositoryFinta(),
            pipeline(),
            eventi,
        )

        val risultato =
            servizioOsservato.esegui(EseguiProssimaElaborazione(nonDopo = CREATA_ALLE.minusMillis(1))).atteso()

        assertEquals(RisultatoAvanzamento.NessunElemento, risultato, "il vincolo rifiuta la testa appena letta")
        assertEquals(true, transazioneApertaAllaLettura, "la lettura che il vincolo usa avviene DENTRO la transazione")
        assertTrue(servizio.esegui(EseguiProssimaElaborazione(nonDopo = null)).atteso() is RisultatoAvanzamento.Avviata)
    }

    @Test
    fun `A38 esclusi e nonDopo combinati - il vincolo si applica dopo l esclusione, non alla testa saltata`() {
        val elaborazioni = ElaborazioneRepositoryFinta()
        val servizio = servizio(elaborazioni)
        val idEsclusa = ElaborazioneId("elab-esclusa")
        val registrazioneEsclusa = RegistrazioneId("registrazione-esclusa")
        elaborazioni.salva(
            Elaborazione.accoda(idEsclusa, registrazioneEsclusa, CREATA_ALLE, numeroPersone = null).aggregato,
        ).atteso()
        val creataCandidata = CREATA_ALLE.plusSeconds(10) // dopo la esclusa: sarebbe la vera testa FIFO grezza
        elaborazioni.salva(unaInAttesa(REGISTRAZIONE, creataCandidata)).atteso()

        // esclusi salta idEsclusa (la testa FIFO grezza, la piu' vecchia): il vincolo nonDopo — inclusivo, AC-S21 —
        // si applica alla testa SUCCESSIVA (ID), non a quella saltata.
        val risultato = servizio.esegui(
            EseguiProssimaElaborazione(esclusi = setOf(idEsclusa), nonDopo = creataCandidata),
        ).atteso()

        assertEquals(RisultatoAvanzamento.Avviata(ID), risultato, "la testa dopo l'esclusione e' rivendicata")
        assertTrue(checkNotNull(elaborazioni.trova(idEsclusa)).inAttesa, "la testa esclusa non e' toccata")
    }

    private fun servizio(
        elaborazioni: ElaborazioneRepositoryFinta,
        eventi: DispatcherEventiFinta = DispatcherEventiFinta(
            UnitaDiLavoroFinta(elaborazioni, TrascrittoRepositoryFinta()),
        ),
    ): EseguiProssimaElaborazioneServizio = EseguiProssimaElaborazioneServizio(
        eventi.unitaDiLavoro,
        OROLOGIO,
        elaborazioni,
        TrascrittoRepositoryFinta(),
        pipeline(),
        eventi,
    )

    private fun pipeline(
        registrazioni: LettoreRegistrazione = LettoreRegistrazioneFinta(),
        decodificatore: DecodificatoreAudio = DecodificatoreAudioFinta(emptyMap()),
        diarizzatore: Diarizzatore = DiarizzatoreFinta(),
        allineatore: Allineatore = AllineatoreFinta(),
        segnalatore: SegnalatoreFase = SegnalatoreFaseFinta(),
    ): PortePipeline = PortePipeline(registrazioni, decodificatore, diarizzatore, allineatore, segnalatore)

    private fun unaInAttesa(id: RegistrazioneId, creataAlle: Instant): Elaborazione =
        Elaborazione.accoda(ID, id, creataAlle, numeroPersone = null).aggregato

    private companion object {
        val OROLOGIO: Clock = Clock.fixed(Instant.parse("2026-09-23T10:10:00Z"), ZoneOffset.UTC)
        val CREATA_ALLE: Instant = Instant.parse("2026-09-23T10:00:00.000Z")
        val REGISTRAZIONE = RegistrazioneId("registrazione-1")
        val ID = ElaborazioneId("elab-${REGISTRAZIONE.valore}")
    }
}
