package snastro.avvio.trascrizione

import snastro.avvio.coda.FonteCoda
import snastro.avvio.coda.FonteCodaContratto
import snastro.avvio.coda.RisultatoTentativo
import snastro.kernel.DispatcherEventiFinta
import snastro.kernel.ElaborazioneId
import snastro.kernel.ErroreDiProva
import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.atteso
import snastro.trascrizione.applicazione.comandi.EseguiProssimaElaborazioneServizio
import snastro.trascrizione.applicazione.comandi.PortePipeline
import snastro.trascrizione.applicazione.comandi.RisultatoAvanzamento
import snastro.trascrizione.applicazione.letture.ElaborazioniInAttesa
import snastro.trascrizione.applicazione.porte.AllineatoreFinta
import snastro.trascrizione.applicazione.porte.DecodificatoreAudioFinta
import snastro.trascrizione.applicazione.porte.DiarizzatoreFinta
import snastro.trascrizione.applicazione.porte.ElaborazioneRepositoryFinta
import snastro.trascrizione.applicazione.porte.LettoreRegistrazioneFinta
import snastro.trascrizione.applicazione.porte.SegnalatoreFaseFinta
import snastro.trascrizione.applicazione.porte.TrascrittoRepositoryFinta
import snastro.trascrizione.dominio.Elaborazione
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Carry-over 5: [RisultatoAvanzamento] (Trascrizione) → [RisultatoTentativo] (CodaCondivisa), at the wiring site
 * — plus [FonteCodaContratto] (A122) over the REAL [fonteCodaElaborazione]/[EseguiProssimaElaborazioneServizio].
 */
internal class FonteCodaElaborazioneTest : FonteCodaContratto() {
    @Test
    fun `ogni RisultatoAvanzamento diventa il RisultatoTentativo omonimo con l id primitivo`() {
        val tabella = listOf(
            RisultatoAvanzamento.NessunElemento to RisultatoTentativo.Nessuno,
            RisultatoAvanzamento.Avviata(ElaborazioneId("e-1")) to RisultatoTentativo.Avviata("e-1"),
            RisultatoAvanzamento.AvvioRifiutato(ElaborazioneId("e-2"), ErroreDiProva.Fallito("no"))
                to RisultatoTentativo.Rifiutata("e-2"),
        )

        tabella.forEach { (avanzamento, atteso) ->
            assertEquals(atteso, tentativoDi(Esito.Ok(avanzamento), ultimaTentata = "ignorato"))
        }
    }

    @Test
    fun `un Esito Errore conta come rifiuto della testa tentata, o nessuno se non ne era stata scelta una`() {
        val errore = Esito.Errore(ErroreDiProva.Fallito("guasto"))

        assertEquals(RisultatoTentativo.Rifiutata("e-3"), tentativoDi(errore, ultimaTentata = "e-3"))
        assertEquals(RisultatoTentativo.Nessuno, tentativoDi(errore, ultimaTentata = null))
    }

    /** [FonteCodaContratto] (A122): due Elaborazioni in_attesa, strettamente in ordine — sulla vera fonte. */
    override fun conDue(): FonteCoda {
        val elaborazioni = ElaborazioneRepositoryFinta()
        val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(elaborazioni, TrascrittoRepositoryFinta()))
        val servizio = EseguiProssimaElaborazioneServizio(
            eventi.unitaDiLavoro,
            OROLOGIO,
            elaborazioni,
            TrascrittoRepositoryFinta(),
            PortePipeline(
                LettoreRegistrazioneFinta(),
                DecodificatoreAudioFinta(emptyMap()),
                DiarizzatoreFinta(),
                AllineatoreFinta(),
                SegnalatoreFaseFinta(),
            ),
            eventi,
        )
        elaborazioni.salva(
            Elaborazione.accoda(
                ElaborazioneId("elab-vecchia"),
                RegistrazioneId("registrazione-vecchia"),
                CREATA_ALLE,
                numeroPersone = null,
            ).aggregato,
        ).atteso()
        elaborazioni.salva(
            Elaborazione.accoda(
                ElaborazioneId("elab-nuova"),
                RegistrazioneId("registrazione-nuova"),
                CREATA_ALLE.plusSeconds(60),
                numeroPersone = null,
            ).aggregato,
        ).atteso()
        return fonteCodaElaborazione(
            servizio,
            ElaborazioniInAttesa(elaborazioni),
            recupera = {},
            modelliPronti = { true },
        )
    }

    private companion object {
        val OROLOGIO: Clock = Clock.fixed(Instant.parse("2026-09-23T10:10:00Z"), ZoneOffset.UTC)
        val CREATA_ALLE: Instant = Instant.parse("2026-09-23T10:00:00.000Z")
    }
}
