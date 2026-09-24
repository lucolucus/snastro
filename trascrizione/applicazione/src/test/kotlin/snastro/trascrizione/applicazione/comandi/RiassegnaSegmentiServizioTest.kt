package snastro.trascrizione.applicazione.comandi

import snastro.kernel.DispatcherEventiFinta
import snastro.kernel.Esito
import snastro.kernel.EventoPubblicato
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.VoceId
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.trascrizione.applicazione.eventi.SegmentoRiassegnato
import snastro.trascrizione.applicazione.porte.TrascrittoRepository
import snastro.trascrizione.applicazione.porte.TrascrittoRepositoryFinta
import snastro.trascrizione.dominio.ErroreTrascrizione
import snastro.trascrizione.dominio.SpostamentoSegmento
import snastro.trascrizione.dominio.Trascritto
import snastro.trascrizione.dominio.unTrascritto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RiassegnaSegmentiServizioTest {
    private val finta = TrascrittoRepositoryFinta()
    private val trascritti = SalvataggiContati(finta)
    private val uow = UnitaDiLavoroFinta(finta)
    private val eventi = DispatcherEventiFinta(uow)
    private val servizio = RiassegnaSegmentiServizio(eventi.unitaDiLavoro, trascritti, eventi)
    private val sincroni = mutableListOf<Pair<EventoPubblicato, Boolean>>()

    init {
        eventi.registraSincrono { e ->
            sincroni += e to uow.transazioneAperta
            Esito.Ok(Unit)
        }
    }

    @Test
    fun `AC-518 un solo salva e gli N SegmentoRiassegnato in ordine tutti dentro la transazione`() {
        // V1:S1,S5 V2:S2,S6 V3:S3,S7 V4:S4,S8 — V3 empties; V2 empties and is refilled by S7
        val t = semina(unTrascritto(voci = 4, segmentiPerVoce = 2, registrazioneId = REGISTRAZIONE))
        val piano = listOf(t.sposta(3, 1), t.sposta(2, 1), t.sposta(7, 2), t.sposta(6, 1))

        servizio.esegui(RiassegnaSegmenti(REGISTRAZIONE, piano)).atteso()

        val attesi = listOf(
            riassegnato(3, da = 3, a = 1, daRimossa = false),
            riassegnato(2, da = 2, a = 1, daRimossa = false),
            riassegnato(7, da = 3, a = 2, daRimossa = true),
            riassegnato(6, da = 2, a = 1, daRimossa = false),
        )
        assertEquals(attesi.map { it to true }, sincroni.map { (e, dentro) -> e as SegmentoRiassegnato to dentro })
        assertEquals(attesi, eventi.pubblicati)
        assertEquals(1, trascritti.salvataggi)
        assertEquals(listOf(1, 2, 4), assertNotNull(finta.trova(REGISTRAZIONE)).voci.map { it.id.numero })
    }

    @Test
    fun `AC-519 un Errore del sincrono al k-esimo evento annulla tutto il blocco`() {
        val originale = semina(unTrascritto(voci = 3, segmentiPerVoce = 2, registrazioneId = REGISTRAZIONE))
        val piano = listOf(originale.sposta(2, 1), originale.sposta(3, 1), originale.sposta(5, 3))
        var consegnati = 0
        eventi.registraSincrono {
            consegnati++
            if (consegnati == 2) Esito.Errore(ErroreTrascrizione.VoceNonTrovata(VoceId(99))) else Esito.Ok(Unit)
        }

        servizio.esegui(RiassegnaSegmenti(REGISTRAZIONE, piano)).erroreAtteso<ErroreTrascrizione.VoceNonTrovata>()

        val dopo = assertNotNull(finta.trova(REGISTRAZIONE))
        assertEquals(originale.segmenti, dopo.segmenti)
        assertEquals(originale.prossimaVoce, dopo.prossimaVoce)
        assertEquals(emptyList(), eventi.pubblicati)
    }

    @Test
    fun `AC-520 un piano stantio restituisce TrascrittoCambiato senza scrivere ne pubblicare`() {
        val originale = semina(unTrascritto(voci = 3, segmentiPerVoce = 2, registrazioneId = REGISTRAZIONE))
        val s2 = originale.segmenti.single { it.id == SegmentoId(2) }
        val confermato = unTrascritto(voci = 3, segmentiPerVoce = 2, registrazioneId = REGISTRAZIONE)
        val casi = listOf(
            "Segmento mancante" to SpostamentoSegmento(SegmentoId(99), VoceId(2), VoceId(1), s2.intervallo),
            "non su da" to SpostamentoSegmento(s2.id, VoceId(3), VoceId(1), s2.intervallo),
            "intervallo diverso" to SpostamentoSegmento(s2.id, VoceId(2), VoceId(1), IntervalloMs(1, 2)),
            "a mancante" to SpostamentoSegmento(s2.id, VoceId(2), VoceId(9), s2.intervallo),
        )
        casi.forEach { (caso, stantio) ->
            val errore = servizio.esegui(RiassegnaSegmenti(REGISTRAZIONE, listOf(originale.sposta(1, 3), stantio)))
                .erroreAtteso<ErroreTrascrizione.TrascrittoCambiato>()
            assertEquals(ErroreTrascrizione.TrascrittoCambiato(REGISTRAZIONE), errore, caso)
        }
        confermato.confermaSegmento(SegmentoId(2), true).atteso()
        semina(confermato)
        val salvataggi = trascritti.salvataggi
        servizio.esegui(RiassegnaSegmenti(REGISTRAZIONE, listOf(confermato.sposta(2, 1))))
            .erroreAtteso<ErroreTrascrizione.TrascrittoCambiato>()

        assertEquals(salvataggi, trascritti.salvataggi)
        assertEquals(confermato.segmenti, assertNotNull(finta.trova(REGISTRAZIONE)).segmenti)
        assertEquals(emptyList(), eventi.pubblicati)
        assertEquals(emptyList(), sincroni)
    }

    @Test
    fun `AC-520 senza Trascritto restituisce TrascrittoNonTrovato`() {
        val piano = listOf(SpostamentoSegmento(SegmentoId(1), VoceId(1), VoceId(2), IntervalloMs(0, 1000)))

        val errore = servizio.esegui(RiassegnaSegmenti(REGISTRAZIONE, piano))
            .erroreAtteso<ErroreTrascrizione.TrascrittoNonTrovato>()

        assertEquals(ErroreTrascrizione.TrascrittoNonTrovato(REGISTRAZIONE), errore)
        assertEquals(emptyList(), eventi.pubblicati)
    }

    @Test
    fun `AC-520 una lista vuota restituisce Ok senza scrivere ne pubblicare`() {
        semina(unTrascritto(voci = 2, segmentiPerVoce = 2, registrazioneId = REGISTRAZIONE))
        val salvataggi = trascritti.salvataggi

        assertEquals(Unit, servizio.esegui(RiassegnaSegmenti(REGISTRAZIONE, emptyList())).atteso())

        assertEquals(salvataggi, trascritti.salvataggi)
        assertEquals(emptyList(), eventi.pubblicati)
        assertEquals(emptyList(), sincroni)
    }

    private fun semina(t: Trascritto): Trascritto {
        finta.salva(t)
        return t
    }

    private fun Trascritto.sposta(segmento: Int, verso: Int): SpostamentoSegmento {
        val s = segmenti.single { it.id == SegmentoId(segmento) }
        return SpostamentoSegmento(s.id, s.voceId, VoceId(verso), s.intervallo)
    }

    private fun riassegnato(segmento: Int, da: Int, a: Int, daRimossa: Boolean): SegmentoRiassegnato =
        SegmentoRiassegnato(REGISTRAZIONE, SegmentoId(segmento), VoceId(da), VoceId(a), daRimossa, aNuova = false)

    /** Recording decorator: counts the `salva` calls that reach the repository (AC-518 "ONE salva"). */
    private class SalvataggiContati(private val delegato: TrascrittoRepository) : TrascrittoRepository by delegato {
        var salvataggi = 0
            private set

        override fun salva(t: Trascritto) {
            salvataggi++
            delegato.salva(t)
        }
    }

    private companion object {
        val REGISTRAZIONE = RegistrazioneId("registrazione-1")
    }
}
