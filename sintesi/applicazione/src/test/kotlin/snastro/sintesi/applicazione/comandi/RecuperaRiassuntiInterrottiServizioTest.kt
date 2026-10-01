package snastro.sintesi.applicazione.comandi

import snastro.kernel.DispatcherEventiFinta
import snastro.kernel.RegistrazioneId
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.atteso
import snastro.kernel.unIncontroDi
import snastro.sintesi.applicazione.eventi.RiassuntoFallito
import snastro.sintesi.applicazione.porte.RiassuntoRepositoryFinta
import snastro.sintesi.applicazione.porte.conAvvio
import snastro.sintesi.applicazione.porte.conFallimento
import snastro.sintesi.applicazione.porte.unRiassunto
import snastro.sintesi.dominio.MotivoFallimento
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [RecuperaRiassuntiInterrottiServizio] against the port's fake (D1): AC-S89. */
class RecuperaRiassuntiInterrottiServizioTest {
    private val riassunti = RiassuntoRepositoryFinta()
    private val eventi = DispatcherEventiFinta(UnitaDiLavoroFinta(riassunti))
    private val servizio = RecuperaRiassuntiInterrottiServizio(eventi.unitaDiLavoro, riassunti, eventi)

    @Test
    fun `AC-S89 un in_corso senza esecuzione viva diventa fallito interrotto`() {
        val id = RegistrazioneId("registrazione-1")
        riassunti.salva(unRiassunto("riassunto-1", id).conAvvio()).atteso()

        servizio.esegui(RecuperaRiassuntiInterrotti).atteso()

        val salvato = riassunti.trova(unIncontroDi(id)).single()
        assertTrue(salvato.fallito, "il Riassunto interrotto deve risultare fallito")
        assertEquals(MotivoFallimento.INTERROTTO, salvato.motivoFallimento)
        assertEquals(listOf(RiassuntoFallito(unIncontroDi(id), "interrotto")), eventi.pubblicati)
    }

    @Test
    fun `AC-S89 in_attesa resta intoccato, solo in_corso diventa fallito, ripeterlo non cambia altro`() {
        val inAttesa = RegistrazioneId("registrazione-attesa")
        val inCorso = RegistrazioneId("registrazione-corso")
        val giaFallito = RegistrazioneId("registrazione-fallita")
        riassunti.salva(unRiassunto("r1", inAttesa)).atteso()
        riassunti.salva(unRiassunto("r2", inCorso).conAvvio()).atteso()
        val guastoOriginale = unRiassunto("r3", giaFallito).conAvvio().conFallimento(MotivoFallimento.ERRORE_MODELLO)
        riassunti.salva(guastoOriginale).atteso()

        servizio.esegui(RecuperaRiassuntiInterrotti).atteso()

        assertEquals(listOf("r1"), riassunti.inAttesa().map { it.id.valore })
        val eraInCorso = riassunti.trova(unIncontroDi(inCorso)).single()
        assertTrue(eraInCorso.fallito)
        assertEquals(MotivoFallimento.INTERROTTO, eraInCorso.motivoFallimento)
        val fallitoOriginario = riassunti.trova(unIncontroDi(giaFallito)).single()
        assertEquals(MotivoFallimento.ERRORE_MODELLO, fallitoOriginario.motivoFallimento)
        assertEquals(listOf(RiassuntoFallito(unIncontroDi(inCorso), "interrotto")), eventi.pubblicati)

        // AC-S89: running it twice changes nothing more — the now-fallito row is no longer in_corso.
        servizio.esegui(RecuperaRiassuntiInterrotti).atteso()
        assertEquals(listOf(RiassuntoFallito(unIncontroDi(inCorso), "interrotto")), eventi.pubblicati)
    }

    @Test
    fun `senza in_corso il comando non ha effetti`() {
        servizio.esegui(RecuperaRiassuntiInterrotti).atteso()

        assertEquals(emptyList(), eventi.pubblicati)
    }
}
