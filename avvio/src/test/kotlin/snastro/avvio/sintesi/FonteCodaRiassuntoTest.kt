package snastro.avvio.sintesi

import snastro.avvio.coda.ElementoInCoda
import snastro.avvio.coda.RisultatoTentativo
import snastro.avvio.coda.TipoElementoCoda
import snastro.kernel.DispatcherEventiFinta
import snastro.kernel.Esito
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.VoceId
import snastro.sintesi.applicazione.comandi.EseguiProssimoRiassuntoServizio
import snastro.sintesi.applicazione.letture.RiassuntiInAttesa
import snastro.sintesi.applicazione.porte.DisponibilitaModelloLinguisticoFinta
import snastro.sintesi.applicazione.porte.LettoreNomiFinta
import snastro.sintesi.applicazione.porte.LettoreTrascrittoFinta
import snastro.sintesi.applicazione.porte.ModelloLinguistico
import snastro.sintesi.applicazione.porte.ModelloLinguisticoFinto
import snastro.sintesi.applicazione.porte.RiassuntoRepositoryFinta
import snastro.sintesi.applicazione.porte.RichiestaRiassunto
import snastro.sintesi.applicazione.porte.RispostaModello
import snastro.sintesi.applicazione.porte.SegmentoSintesi
import snastro.sintesi.applicazione.porte.StatoModelloLinguistico
import snastro.sintesi.applicazione.porte.unRiassunto
import snastro.sintesi.dominio.RiassuntoId
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Riassunto source of the shared queue ([fonteCodaRiassunto]) over the REAL `EseguiProssimoRiassuntoServizio`
 * and the Sintesi fakes: carry-over 2 (`teste` honours `esclusi` and agrees with `prossima`, the `primaDi` bound),
 * and the per-run flag of AC-S161/AC-S162 (D-0004/D-0006) at the source's own seam — with AC-S63's match (only the
 * RUNNING Riassunto of the deleted Registrazione), which ADR 0030 moved from the queue into this per-run state.
 */
class FonteCodaRiassuntoTest {
    private val repo = RiassuntoRepositoryFinta()
    private val uowFinta = UnitaDiLavoroFinta(repo)
    private val eventi = DispatcherEventiFinta(uowFinta)
    private val esecuzioni = EsecuzioniRiassunto(repo)

    /** What the model does while it runs (the flag is read through `annullato`); the answer is the fake's default. */
    private var durante: (annullato: () -> Boolean) -> Unit = {}
    private val letture = mutableListOf<Boolean>()

    private val modello = object : ModelloLinguistico {
        private val finto = ModelloLinguisticoFinto(uowFinta)

        override fun riassumi(richiesta: RichiestaRiassunto, annullato: () -> Boolean): Esito<RispostaModello> {
            letture += annullato()
            durante(annullato)
            return finto.riassumi(richiesta, { false })
        }
    }

    private val servizio = EseguiProssimoRiassuntoServizio(
        eventi.unitaDiLavoro,
        Clock.fixed(Instant.parse("2026-09-26T11:00:00Z"), ZoneOffset.UTC),
        RiassuntoRepositoryConReclamo(repo, esecuzioni),
        LettoreTrascrittoFinta(listOf(R1, R2, R3).associateWith { listOf(SEGMENTO) }),
        LettoreNomiFinta(),
        modello,
        DisponibilitaModelloLinguisticoFinta(StatoModelloLinguistico.Installato),
        eventi,
        esecuzioni.annullato,
    )
    private val fonte = fonteCodaRiassunto(RiassuntiInAttesa(repo), servizio::esegui, {}, esecuzioni)

    private fun semina() {
        repo.salva(unRiassunto("A", R1, richiestoAlle = T1))
        repo.salva(unRiassunto("B", R2, richiestoAlle = T2))
        repo.salva(unRiassunto("C", R3, richiestoAlle = T3))
    }

    @Test
    fun `teste onora esclusi nell'ordine FIFO, e una fonte Riassunto non trattiene mai la coda`() {
        semina()
        assertEquals(TipoElementoCoda.RIASSUNTO, fonte.tipo)
        assertEquals(ElementoInCoda("A", R1.valore, T1), fonte.teste(emptySet()))
        assertEquals("B", fonte.teste(setOf("A"))?.id)
        assertEquals("C", fonte.teste(setOf("A", "B"))?.id)
        assertNull(fonte.teste(setOf("A", "B", "C")))
        assertFalse(fonte.trattenuta())
    }

    @Test
    fun `teste e prossima concordano, esclusi compresi, e ultimaTentata e la testa reclamata`() {
        semina()
        val testa = checkNotNull(fonte.teste(setOf("A")))

        assertEquals(RisultatoTentativo.Avviata(testa.id), fonte.prossima(setOf("A"), null))
        assertEquals("B", fonte.ultimaTentata())
        assertEquals(RisultatoTentativo.Avviata("A"), fonte.prossima(emptySet(), null))
        assertEquals(RisultatoTentativo.Avviata("C"), fonte.prossima(emptySet(), null))
        assertEquals(RisultatoTentativo.Nessuno, fonte.prossima(emptySet(), null))
    }

    @Test
    fun `il limite primaDi e stretto - un'Elaborazione allo stesso millisecondo passa prima`() {
        semina()
        assertEquals(RisultatoTentativo.Nessuno, fonte.prossima(emptySet(), T1))
        assertNull(fonte.ultimaTentata())
        assertEquals(RisultatoTentativo.Avviata("A"), fonte.prossima(emptySet(), T1.plusMillis(1)))
    }

    @Test
    fun `AC-S63 la cancellazione agisce solo sul Riassunto in corso di quella Registrazione, mai a vuoto`() {
        semina()
        esecuzioni.annulla(R1) // nothing running (an Elaborazione runs, or the queue is idle): no effect
        val durante = mutableListOf<Boolean>()
        this.durante = { annullato ->
            repo.rimuovi(RiassuntoId("A")) // deleted with R1 (ADR 0024 §1)
            esecuzioni.annulla(R2) // another Registrazione: never the running one
            durante += annullato()
            esecuzioni.annulla(R1) // the running Riassunto's own Registrazione, its row gone: cancelled
            durante += annullato()
        }
        fonte.prossima(emptySet(), null)
        assertEquals(listOf(false, true), durante)
    }

    @Test
    fun `AC-S161 annulla gira il flag solo se la riga del Riassunto in corso non esiste piu`() {
        semina()
        val durante = mutableListOf<Boolean>()
        this.durante = { annullato ->
            esecuzioni.annulla(R1) // late/duplicate Eliminato: A still exists
            durante += annullato()
            repo.rimuovi(RiassuntoId("A")) // now it is really gone
            esecuzioni.annulla(R1)
            durante += annullato()
        }
        fonte.prossima(emptySet(), null)
        assertEquals(listOf(false, true), durante)

        this.durante = {}
        fonte.prossima(emptySet(), null) // B: a fresh flag for a fresh claim
        assertEquals(false, letture.last(), "il flag di un nuovo reclamo parte falso")
    }

    @Test
    fun `AC-S162 interrompi gira il flag del run corrente senza condizioni, e il reclamo successivo riparte falso`() {
        semina()
        val durante = mutableListOf<Boolean>()
        this.durante = { annullato ->
            fonte.interrompi()
            durante += annullato()
        }
        fonte.prossima(emptySet(), null)
        assertEquals(listOf(true), durante, "la riga esiste ancora, ma lo STOP annulla comunque")

        this.durante = {}
        fonte.prossima(emptySet(), null)
        assertEquals(false, letture.last())
        fonte.interrompi() // nothing running: no effect on anything
        esecuzioni.annulla(R3)
        assertTrue(fonte.teste(emptySet())?.id == "C")
    }

    private companion object {
        val R1 = RegistrazioneId("r-1")
        val R2 = RegistrazioneId("r-2")
        val R3 = RegistrazioneId("r-3")
        val T1: Instant = Instant.parse("2026-09-26T10:00:00.100Z")
        val T2: Instant = Instant.parse("2026-09-26T10:00:00.200Z")
        val T3: Instant = Instant.parse("2026-09-26T10:00:00.300Z")
        val SEGMENTO = SegmentoSintesi(SegmentoId(1), VoceId(1), IntervalloMs(0, 1_000), "Si resta a turni.")
    }
}
