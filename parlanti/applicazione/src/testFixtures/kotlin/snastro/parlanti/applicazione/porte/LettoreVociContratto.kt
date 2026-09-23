package snastro.parlanti.applicazione.porte

import org.junit.jupiter.api.Test
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceRef
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Consumer-driven contract of [LettoreVoci] (boundary `voci-per-parlanti`): one subclass per
 * implementation — [LettoreVociFinta] (D1) and `lettore-voci-da-trascrizione` (D2, real-on-real).
 * Each test takes [AmbienteLettoreVoci.lettore] once, up front, and keeps reading through it after
 * every change: an implementation that serves a stale copy fails. Expected lists are written in the
 * pinned order (Voci by voceId, intervalli by inizio then segmentoId) and compared whole, so every
 * listed [VoceRef] must be one the supplier minted for that Registrazione.
 */
public abstract class LettoreVociContratto {
    /** A fresh supplier: one Progetto, no Registrazione. */
    protected abstract fun ambiente(): AmbienteLettoreVoci

    @Test
    public fun `AC-45 senza Trascritto restituisce null`() {
        val a = ambiente()
        val lettore = a.lettore
        val mai = a.aggiungiRegistrazione()
        val fallita = a.aggiungiRegistrazione()
        a.fallisciElaborazione(fallita)
        a.completaElaborazione(a.aggiungiRegistrazione(), listOf(SemeTurno(0, IntervalloMs(0, 1_000))))

        assertNull(lettore.voci(SCONOSCIUTA))
        assertNull(lettore.voci(mai))
        assertNull(lettore.voci(fallita))
    }

    @Test
    public fun `AC-45 dopo una Elaborazione fallita una completata restituisce le Voci del suo Trascritto`() {
        val a = ambiente()
        val lettore = a.lettore
        val id = a.aggiungiRegistrazione()
        a.fallisciElaborazione(id)
        assertNull(lettore.voci(id))

        val turni = listOf(SemeTurno(0, IntervalloMs(0, 2_000)), SemeTurno(1, IntervalloMs(2_000, 5_000)))
        val c = a.completaElaborazione(id, turni)

        assertEquals(
            listOf(
                VoceVista(VoceRef(id, c[0].voceId), listOf(turni[0].intervallo)),
                VoceVista(VoceRef(id, c[1].voceId), listOf(turni[1].intervallo)),
            ),
            lettore.voci(id),
        )
    }

    @Test
    public fun `AC-46 le Voci sono ordinate per voceId e gli intervalli di ogni Voce per inizio`() {
        val a = ambiente()
        val lettore = a.lettore
        val id = a.aggiungiRegistrazione()
        val turni = listOf(
            SemeTurno(1, IntervalloMs(12_000, 15_000)),
            SemeTurno(0, IntervalloMs(0, 4_000)),
            SemeTurno(0, IntervalloMs(20_000, 26_000)),
            SemeTurno(1, IntervalloMs(4_000, 9_000)),
            SemeTurno(2, IntervalloMs(30_000, 31_000)),
            SemeTurno(0, IntervalloMs(9_000, 11_000)),
        )
        val c = a.completaElaborazione(id, turni)

        assertEquals(
            listOf(
                VoceVista(VoceRef(id, c[1].voceId), listOf(1, 5, 2).map { turni[it].intervallo }),
                VoceVista(VoceRef(id, c[3].voceId), listOf(3, 0).map { turni[it].intervallo }),
                VoceVista(VoceRef(id, c[4].voceId), listOf(turni[4].intervallo)),
            ).sortedBy { it.voceRef.voceId.numero },
            lettore.voci(id),
        )
    }

    @Test
    public fun `AC-46 a parita di inizio gli intervalli seguono il segmentoId e non la fine`() {
        val a = ambiente()
        val lettore = a.lettore
        val id = a.aggiungiRegistrazione()
        val turni = listOf(
            SemeTurno(0, IntervalloMs(0, 3_000)),
            SemeTurno(1, IntervalloMs(5_000, 6_000)),
            SemeTurno(0, IntervalloMs(5_000, 9_000)),
        )
        val c = a.completaElaborazione(id, turni)
        assertTrue(c[2].segmentoId.numero < c[1].segmentoId.numero, "a parita di inizio la Voce minore ha il Segmento minore: $c")

        // Both 5 000 ms Segmenti end up in one Voce: the longer one has the smaller segmentoId.
        a.unisci(id, sopravvive = c[0].voceId, rimossa = c[1].voceId)

        assertEquals(
            listOf(VoceVista(VoceRef(id, c[0].voceId), listOf(0, 2, 1).map { turni[it].intervallo })),
            lettore.voci(id),
        )
    }

    @Test
    public fun `AC-46 Segmenti sovrapposti o con lo stesso intervallo danno un intervallo ciascuno senza fusioni`() {
        val a = ambiente()
        val lettore = a.lettore
        val id = a.aggiungiRegistrazione()
        // INV-7 allows overlap: two overlapping and two identical Segmenti in the same Voce.
        val turni = listOf(
            SemeTurno(0, IntervalloMs(8_000, 10_000)),
            SemeTurno(0, IntervalloMs(2_000, 6_000)),
            SemeTurno(1, IntervalloMs(3_000, 5_000)),
            SemeTurno(0, IntervalloMs(0, 4_000)),
            SemeTurno(0, IntervalloMs(8_000, 10_000)),
        )
        val c = a.completaElaborazione(id, turni)

        val voci = lettore.voci(id)

        assertEquals(
            listOf(
                VoceVista(VoceRef(id, c[0].voceId), listOf(3, 1, 0, 4).map { turni[it].intervallo }),
                VoceVista(VoceRef(id, c[2].voceId), listOf(turni[2].intervallo)),
            ).sortedBy { it.voceRef.voceId.numero },
            voci,
        )
        assertEquals(turni.size, voci?.sumOf { it.intervalli.size }, "un intervallo per Segmento: $voci")
    }

    @Test
    public fun `AC-46 ogni Registrazione restituisce le proprie Voci`() {
        val a = ambiente()
        val lettore = a.lettore
        val riunione = a.aggiungiRegistrazione()
        val intervista = a.aggiungiRegistrazione()
        val turnoRiunione = SemeTurno(0, IntervalloMs(1_000, 2_000))
        val turnoIntervista = SemeTurno(0, IntervalloMs(500, 1_500))
        val cRiunione = a.completaElaborazione(riunione, listOf(turnoRiunione))
        val cIntervista = a.completaElaborazione(intervista, listOf(turnoIntervista))

        assertEquals(
            listOf(VoceVista(VoceRef(intervista, cIntervista[0].voceId), listOf(turnoIntervista.intervallo))),
            lettore.voci(intervista),
        )
        assertEquals(
            listOf(VoceVista(VoceRef(riunione, cRiunione[0].voceId), listOf(turnoRiunione.intervallo))),
            lettore.voci(riunione),
        )
    }

    @Test
    public fun `AC-47 dopo UnisciVoci la Voce rimossa sparisce e la sopravvissuta ha tutti gli intervalli`() {
        val a = ambiente()
        val lettore = a.lettore
        val id = a.aggiungiRegistrazione()
        val altra = a.aggiungiRegistrazione()
        val turni = listOf(
            SemeTurno(0, IntervalloMs(0, 2_000)),
            SemeTurno(1, IntervalloMs(2_000, 4_000)),
            SemeTurno(2, IntervalloMs(4_000, 6_000)),
            SemeTurno(1, IntervalloMs(6_000, 8_000)),
            SemeTurno(0, IntervalloMs(8_000, 9_000)),
        )
        val c = a.completaElaborazione(id, turni)
        val cAltra = a.completaElaborazione(altra, listOf(SemeTurno(0, IntervalloMs(0, 1_000))))
        val prima = lettore.voci(id)
        assertEquals(3, prima?.size, "tre Voci prima dell'unione: $prima")

        a.unisci(id, sopravvive = c[2].voceId, rimossa = c[0].voceId)

        assertEquals(
            listOf(
                VoceVista(VoceRef(id, c[1].voceId), listOf(1, 3).map { turni[it].intervallo }),
                VoceVista(VoceRef(id, c[2].voceId), listOf(0, 2, 4).map { turni[it].intervallo }),
            ).sortedBy { it.voceRef.voceId.numero },
            lettore.voci(id),
        )
        assertEquals(
            listOf(VoceVista(VoceRef(altra, cAltra[0].voceId), listOf(IntervalloMs(0, 1_000)))),
            lettore.voci(altra),
        )
    }

    @Test
    public fun `AC-47 dopo DividiVoce la nuova Voce ha gli intervalli spostati e l origine il resto`() {
        val a = ambiente()
        val lettore = a.lettore
        val id = a.aggiungiRegistrazione()
        val turni = listOf(
            SemeTurno(0, IntervalloMs(0, 1_000)),
            SemeTurno(1, IntervalloMs(1_000, 3_000)),
            SemeTurno(0, IntervalloMs(3_000, 4_000)),
            SemeTurno(0, IntervalloMs(6_000, 7_000)),
        )
        val c = a.completaElaborazione(id, turni)
        assertEquals(2, lettore.voci(id)?.size)

        val nuova = a.dividi(id, origine = c[0].voceId, segmenti = setOf(c[2].segmentoId, c[3].segmentoId))

        assertTrue(nuova != c[0].voceId && nuova != c[1].voceId, "la nuova Voce ha un voceId nuovo: $nuova")
        assertEquals(
            listOf(
                VoceVista(VoceRef(id, c[0].voceId), listOf(turni[0].intervallo)),
                VoceVista(VoceRef(id, c[1].voceId), listOf(turni[1].intervallo)),
                VoceVista(VoceRef(id, nuova), listOf(2, 3).map { turni[it].intervallo }),
            ).sortedBy { it.voceRef.voceId.numero },
            lettore.voci(id),
        )
    }

    @Test
    public fun `AC-47 dopo RiassegnaSegmento le Voci riflettono lo spostamento e l origine svuotata sparisce`() {
        val a = ambiente()
        val lettore = a.lettore
        val id = a.aggiungiRegistrazione()
        val turni = listOf(
            SemeTurno(0, IntervalloMs(0, 1_000)),
            SemeTurno(1, IntervalloMs(1_000, 2_000)),
            SemeTurno(0, IntervalloMs(2_000, 3_000)),
        )
        val c = a.completaElaborazione(id, turni)
        assertEquals(2, lettore.voci(id)?.size)

        // To a new Voce: its Voce keeps the other Segmento.
        val nuova = a.riassegna(id, c[2].segmentoId, destinazione = null)
        assertTrue(nuova != c[0].voceId && nuova != c[1].voceId, "la nuova Voce ha un voceId nuovo: $nuova")
        assertEquals(
            listOf(
                VoceVista(VoceRef(id, c[0].voceId), listOf(turni[0].intervallo)),
                VoceVista(VoceRef(id, c[1].voceId), listOf(turni[1].intervallo)),
                VoceVista(VoceRef(id, nuova), listOf(turni[2].intervallo)),
            ).sortedBy { it.voceRef.voceId.numero },
            lettore.voci(id),
        )

        // To an existing Voce: the only Segmento of its Voce leaves, so that Voce ceases to exist.
        assertEquals(c[0].voceId, a.riassegna(id, c[1].segmentoId, destinazione = c[0].voceId))
        assertEquals(
            listOf(
                VoceVista(VoceRef(id, c[0].voceId), listOf(0, 1).map { turni[it].intervallo }),
                VoceVista(VoceRef(id, nuova), listOf(turni[2].intervallo)),
            ).sortedBy { it.voceRef.voceId.numero },
            lettore.voci(id),
        )
    }

    @Test
    public fun `AC-47 una Voce rimossa da un unione non cede il suo voceId alla Voce nata da una divisione`() {
        val a = ambiente()
        val lettore = a.lettore
        val id = a.aggiungiRegistrazione()
        val turni = listOf(
            SemeTurno(0, IntervalloMs(0, 1_000)),
            SemeTurno(1, IntervalloMs(1_000, 2_000)),
            SemeTurno(2, IntervalloMs(2_000, 3_000)),
            SemeTurno(0, IntervalloMs(3_000, 4_000)),
        )
        val c = a.completaElaborazione(id, turni)
        a.unisci(id, sopravvive = c[0].voceId, rimossa = c[2].voceId)

        val nuova = a.dividi(id, origine = c[0].voceId, segmenti = setOf(c[3].segmentoId))

        assertTrue(nuova !in c.map { it.voceId }, "voceId mai riusato: $nuova tra ${c.map { it.voceId }}")
        assertEquals(
            listOf(
                VoceVista(VoceRef(id, c[0].voceId), listOf(0, 2).map { turni[it].intervallo }),
                VoceVista(VoceRef(id, c[1].voceId), listOf(turni[1].intervallo)),
                VoceVista(VoceRef(id, nuova), listOf(turni[3].intervallo)),
            ).sortedBy { it.voceRef.voceId.numero },
            lettore.voci(id),
        )
    }

    private companion object {
        val SCONOSCIUTA = RegistrazioneId("registrazione-sconosciuta")
    }
}
