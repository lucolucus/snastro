package snastro.sintesi.applicazione.porte

import org.junit.jupiter.api.Test
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Consumer-driven contract of [LettoreTrascritto] (boundary `trascritto-per-sintesi`): one subclass per
 * implementation — [LettoreTrascrittoFinta] (D1) and `lettore-trascritto-da-trascrizione-sintesi`
 * (D2, real-on-real).
 */
public abstract class LettoreTrascrittoContratto {
    /** A fresh supplier: one Progetto, no Registrazione. */
    protected abstract fun ambiente(): AmbienteLettoreTrascritto

    @Test
    public fun `AC-S4 senza Trascritto segmenti restituisce null`() {
        val a = ambiente()
        val mai = a.aggiungiRegistrazione()
        val inAttesa = a.aggiungiRegistrazione().also { a.accodaElaborazione(it) }
        val inCorso = a.aggiungiRegistrazione().also { a.accodaElaborazione(it); a.avviaElaborazione(it) }
        val fallita = a.aggiungiRegistrazione().also {
            a.accodaElaborazione(it)
            a.avviaElaborazione(it)
            a.fallisciElaborazione(it)
        }

        listOf(SCONOSCIUTA, mai, inAttesa, inCorso, fallita).forEach {
            assertNull(a.lettore.segmenti(it), "senza Trascritto: ${it.valore}")
        }
    }

    @Test
    public fun `AC-S4 con Trascritto segmenti restituisce la lista nell ordine del fornitore`() {
        val a = ambiente()
        val id = a.aggiungiRegistrazione()
        val turni = listOf(
            SemeTurno(1, IntervalloMs(12_000, 15_000), "Ok, ci sono."),
            SemeTurno(0, IntervalloMs(0, 4_000), "Buongiorno, iniziamo."),
            SemeTurno(0, IntervalloMs(20_000, 26_000), "Facciamo il deploy on Friday — d'accordo? «ok» àèéìòù"),
            SemeTurno(1, IntervalloMs(4_000, 9_000), "Io ho finito il parser."),
        )
        val c = completa(a, id, turni)

        assertEquals(atteso(turni, c), a.lettore.segmenti(id))
    }

    @Test
    public fun `AC-S4 una Elaborazione completata dopo una fallita da il Trascritto`() {
        val a = ambiente()
        val id = a.aggiungiRegistrazione()
        a.accodaElaborazione(id)
        a.fallisciElaborazione(id)
        assertNull(a.lettore.segmenti(id))

        val turni = listOf(SemeTurno(0, IntervalloMs(0, 1_000), "Riproviamo."))
        val c = completa(a, id, turni)

        assertEquals(atteso(turni, c), a.lettore.segmenti(id))
    }

    @Test
    public fun `AC-S4 ogni Registrazione restituisce i propri segmenti`() {
        val a = ambiente()
        val riunione = a.aggiungiRegistrazione()
        val intervista = a.aggiungiRegistrazione()
        val turniRiunione = listOf(SemeTurno(0, IntervalloMs(1_000, 2_000), "Riunione."))
        val turniIntervista = listOf(SemeTurno(0, IntervalloMs(500, 1_500), "Intervista."))
        val cRiunione = completa(a, riunione, turniRiunione)
        val cIntervista = completa(a, intervista, turniIntervista)

        assertEquals(atteso(turniIntervista, cIntervista), a.lettore.segmenti(intervista))
        assertEquals(atteso(turniRiunione, cRiunione), a.lettore.segmenti(riunione))
    }

    @Test
    public fun `AC-S5 dopo una Revisione ogni segmento porta la Voce attuale con id intervallo e testo invariati`() {
        val a = ambiente()
        val id = a.aggiungiRegistrazione()
        val turni = listOf(
            SemeTurno(0, IntervalloMs(0, 2_000), "Buongiorno a tutti."),
            SemeTurno(0, IntervalloMs(5_000, 8_000), "Partiamo dal budget."),
            SemeTurno(1, IntervalloMs(9_000, 12_000), "Sure, go ahead."),
            SemeTurno(1, IntervalloMs(13_000, 15_000), "Ne parliamo dopo."),
        )
        val c = completa(a, id, turni)
        val nuovaVoce = a.riassegna(id, c[1].segmentoId, destinazione = null)
        val esistente = a.riassegna(id, c[3].segmentoId, destinazione = c[0].voceId)

        val attesi = atteso(turni, c).map {
            when (it.segmentoId) {
                c[1].segmentoId -> it.copy(voceId = nuovaVoce)
                c[3].segmentoId -> it.copy(voceId = esistente)
                else -> it
            }
        }
        assertEquals(c[0].voceId, esistente)
        assertEquals(attesi, a.lettore.segmenti(id))
    }

    @Test
    public fun `AC-S6 elaborazioneAperta e vera solo mentre l ultima Elaborazione e in attesa o in corso`() {
        val a = ambiente()
        val id = a.aggiungiRegistrazione()
        assertFalse(a.lettore.elaborazioneAperta(SCONOSCIUTA), "sconosciuta")
        assertFalse(a.lettore.elaborazioneAperta(id), "mai elaborata")

        a.accodaElaborazione(id)
        assertTrue(a.lettore.elaborazioneAperta(id), "in_attesa")
        a.avviaElaborazione(id)
        assertTrue(a.lettore.elaborazioneAperta(id), "in_corso")
        a.fallisciElaborazione(id)
        assertFalse(a.lettore.elaborazioneAperta(id), "fallita")

        a.accodaElaborazione(id)
        assertTrue(a.lettore.elaborazioneAperta(id), "in_attesa dopo fallita")
        a.annullaElaborazione(id)
        assertFalse(a.lettore.elaborazioneAperta(id), "annullata")

        a.accodaElaborazione(id)
        a.avviaElaborazione(id)
        a.completaElaborazione(id, listOf(SemeTurno(0, IntervalloMs(0, 1_000), "Fatto.")))
        assertFalse(a.lettore.elaborazioneAperta(id), "completata")
    }

    @Test
    public fun `AC-S6 elaborazioneAperta riguarda solo la propria Registrazione`() {
        val a = ambiente()
        val aperta = a.aggiungiRegistrazione()
        val altra = a.aggiungiRegistrazione()
        a.accodaElaborazione(aperta)

        assertTrue(a.lettore.elaborazioneAperta(aperta))
        assertFalse(a.lettore.elaborazioneAperta(altra))
    }

    @Test
    public fun `AC-S6 durante una rielaborazione in coda o in corso segmenti restituisce il VECCHIO Trascritto`() {
        val a = ambiente()
        val id = a.aggiungiRegistrazione()
        val vecchi = listOf(
            SemeTurno(0, IntervalloMs(0, 3_000), "Prima versione."),
            SemeTurno(1, IntervalloMs(3_000, 6_000), "Seconda frase."),
        )
        val vecchio = atteso(vecchi, completa(a, id, vecchi))

        a.accodaElaborazione(id)
        assertTrue(a.lettore.elaborazioneAperta(id))
        assertEquals(vecchio, a.lettore.segmenti(id), "rielaborazione in_attesa")
        a.avviaElaborazione(id)
        assertTrue(a.lettore.elaborazioneAperta(id))
        assertEquals(vecchio, a.lettore.segmenti(id), "rielaborazione in_corso")
        a.fallisciElaborazione(id)
        assertFalse(a.lettore.elaborazioneAperta(id))
        assertEquals(vecchio, a.lettore.segmenti(id), "rielaborazione fallita")

        a.accodaElaborazione(id)
        a.annullaElaborazione(id)
        assertEquals(vecchio, a.lettore.segmenti(id), "rielaborazione annullata")

        val nuovi = listOf(SemeTurno(0, IntervalloMs(10_000, 14_000), "Versione nuova."))
        val nuovo = atteso(nuovi, completa(a, id, nuovi))
        assertFalse(a.lettore.elaborazioneAperta(id))
        assertEquals(nuovo, a.lettore.segmenti(id), "rielaborazione completata")
    }

    private fun completa(a: AmbienteLettoreTrascritto, r: RegistrazioneId, turni: List<SemeTurno>): List<SegmentoConiato> {
        a.accodaElaborazione(r)
        a.avviaElaborazione(r)
        return a.completaElaborazione(r, turni)
    }

    /** The supplier order (INV-7 as `VociDelTrascritto.segmenti` gives it): by segmentoId. */
    private fun atteso(turni: List<SemeTurno>, coniati: List<SegmentoConiato>): List<SegmentoSintesi> =
        turni.zip(coniati) { t, c -> SegmentoSintesi(c.segmentoId, c.voceId, t.intervallo, t.testo) }
            .sortedBy { it.segmentoId.numero }

    private companion object {
        val SCONOSCIUTA = RegistrazioneId("registrazione-sconosciuta")
    }
}
