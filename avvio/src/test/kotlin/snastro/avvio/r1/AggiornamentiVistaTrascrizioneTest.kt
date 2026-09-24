package snastro.avvio.r1

import snastro.kernel.DispatcherEventiInMemoria
import snastro.kernel.ErroreDiProva
import snastro.kernel.Esito
import snastro.kernel.EventoPubblicato
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.VoceId
import snastro.trascrizione.applicazione.eventi.ElaborazioneAvviata
import snastro.trascrizione.applicazione.eventi.ElaborazioneCompletata
import snastro.trascrizione.applicazione.eventi.ElaborazioneFallita
import snastro.trascrizione.applicazione.eventi.SegmentoRiassegnato
import snastro.trascrizione.applicazione.eventi.VoceDivisa
import snastro.trascrizione.applicazione.eventi.VociUnite
import snastro.trascrizione.applicazione.letture.FasiInCorso
import snastro.trascrizione.applicazione.porte.FaseElaborazione
import snastro.ui.Cambiamento
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** AC-354: every Elaborazione/Revisione event and every phase change of FasiInCorso is a Cambiamento. */
class AggiornamentiVistaTrascrizioneTest {
    private val id = RegistrazioneId("rec-1")

    @Test
    fun `AC-354 ogni evento di Elaborazione e di Revisione produce un Cambiamento dopo il commit`() {
        val eventi: List<EventoPubblicato> = listOf(
            ElaborazioneAvviata(id, Instant.parse("2026-01-01T10:00:00Z")),
            ElaborazioneCompletata(id),
            ElaborazioneFallita(id, "interrotta"),
            VociUnite(id, VoceId(1), VoceId(2)),
            VoceDivisa(id, VoceId(1), VoceId(3), listOf(SegmentoId(1))),
            SegmentoRiassegnato(id, SegmentoId(1), VoceId(1), VoceId(2), daRimossa = false, aNuova = false),
        )

        eventi.forEach { evento ->
            val dispatcher = DispatcherEventiInMemoria(UnitaDiLavoroFinta())
            val aggiornamenti = AggiornamentiVistaTrascrizione(dispatcher)

            dispatcher.unitaDiLavoro.inTransazione { dispatcher.pubblica(evento).let { Esito.Ok(Unit) } }

            assertEquals(listOf(Cambiamento(id)), aggiornamenti.cambiamenti.replayCache, "Cambiamento per $evento")
        }
    }

    @Test
    fun `AC-354 nessun Cambiamento se il comando e annullato`() {
        val dispatcher = DispatcherEventiInMemoria(UnitaDiLavoroFinta())
        val aggiornamenti = AggiornamentiVistaTrascrizione(dispatcher)

        dispatcher.unitaDiLavoro.inTransazione {
            dispatcher.pubblica(ElaborazioneCompletata(id))
            Esito.Errore(ErroreDiProva.Fallito("rollback"))
        }

        assertEquals(emptyList(), aggiornamenti.cambiamenti.replayCache)
    }

    @Test
    fun `AC-353 AC-354 un cambio di fase scrive nella FasiInCorso condivisa e produce un Cambiamento`() {
        val fasi = FasiInCorso()
        val aggiornamenti = AggiornamentiVistaTrascrizione(DispatcherEventiInMemoria(UnitaDiLavoroFinta()))
        val segnalatore = SegnalatoreFaseConCambiamenti(fasi, aggiornamenti::cambiata)

        segnalatore.fase(id, FaseElaborazione.DIARIZZAZIONE)
        assertEquals(FaseElaborazione.DIARIZZAZIONE, fasi.faseDi(id))
        assertEquals(listOf(Cambiamento(id)), aggiornamenti.cambiamenti.replayCache)

        segnalatore.terminata(RegistrazioneId("rec-2").also { fasi.fase(it, FaseElaborazione.DECODIFICA) })
        assertNull(fasi.faseDi(RegistrazioneId("rec-2")))
        assertEquals(listOf(Cambiamento(RegistrazioneId("rec-2"))), aggiornamenti.cambiamenti.replayCache)
    }
}
