package snastro.parlanti.applicazione.porte

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import snastro.kernel.IncontroId
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoRef
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Consumer-driven contract of [LettoreVoci] (boundary `voci-per-parlanti`, keyed by Incontro — ADR 0033 §4): one
 * subclass per implementation — [LettoreVociFinta] (D1) and `LettoreVociDaTrascrizione` (D2, real-on-real).
 * Each test takes [AmbienteLettoreVoci.lettore] once, up front, and keeps reading through it after
 * every change: an implementation that serves a stale copy fails. Expected lists are written in the
 * pinned order (Voci by voceId, intervalli by inizio then segmentoId) and compared whole, so every
 * listed [VoceRef] must be one the supplier minted for that Incontro.
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

        assertNull(lettore.voci(SCONOSCIUTO))
        assertNull(lettore.voci(a.incontroDi(mai)))
        assertNull(lettore.voci(a.incontroDi(fallita)))
    }

    @Test
    public fun `AC-45 dopo una Elaborazione fallita una completata restituisce le Voci del suo Trascritto`() {
        val a = ambiente()
        val lettore = a.lettore
        val id = a.aggiungiRegistrazione()
        a.fallisciElaborazione(id)
        assertNull(lettore.voci(a.incontroDi(id)))

        val turni = listOf(SemeTurno(0, IntervalloMs(0, 2_000)), SemeTurno(1, IntervalloMs(2_000, 5_000)))
        val c = a.completaElaborazione(id, turni)

        assertEquals(
            listOf(
                a.vista(id, c[0].voceId, listOf(turni[0].intervallo)),
                a.vista(id, c[1].voceId, listOf(turni[1].intervallo)),
            ),
            lettore.voci(a.incontroDi(id)),
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
                a.vista(id, c[1].voceId, listOf(1, 5, 2).map { turni[it].intervallo }),
                a.vista(id, c[3].voceId, listOf(3, 0).map { turni[it].intervallo }),
                a.vista(id, c[4].voceId, listOf(turni[4].intervallo)),
            ).sortedBy { it.voceRef.voceId.numero },
            lettore.voci(a.incontroDi(id)),
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
        assertTrue(
            c[2].segmentoId.numero < c[1].segmentoId.numero,
            "a parita di inizio la Voce minore ha il Segmento minore: $c",
        )

        // Both 5 000 ms Segmenti end up in one Voce: the longer one has the smaller segmentoId.
        a.unisci(a.incontroDi(id), sopravvive = c[0].voceId, rimossa = c[1].voceId)

        assertEquals(
            listOf(a.vista(id, c[0].voceId, listOf(0, 2, 1).map { turni[it].intervallo })),
            lettore.voci(a.incontroDi(id)),
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

        val voci = lettore.voci(a.incontroDi(id))

        assertEquals(
            listOf(
                a.vista(id, c[0].voceId, listOf(3, 1, 0, 4).map { turni[it].intervallo }),
                a.vista(id, c[2].voceId, listOf(turni[2].intervallo)),
            ).sortedBy { it.voceRef.voceId.numero },
            voci,
        )
        assertEquals(turni.size, voci?.sumOf { it.intervalliPerParte.values.sumOf { i -> i.size } }, "uno per Segmento: $voci")
    }

    @Test
    public fun `AC-46 ogni Incontro restituisce le proprie Voci`() {
        val a = ambiente()
        val lettore = a.lettore
        val riunione = a.aggiungiRegistrazione()
        val intervista = a.aggiungiRegistrazione()
        val turnoRiunione = SemeTurno(0, IntervalloMs(1_000, 2_000))
        val turnoIntervista = SemeTurno(0, IntervalloMs(500, 1_500))
        val cRiunione = a.completaElaborazione(riunione, listOf(turnoRiunione))
        val cIntervista = a.completaElaborazione(intervista, listOf(turnoIntervista))

        assertEquals(
            listOf(a.vista(intervista, cIntervista[0].voceId, listOf(turnoIntervista.intervallo))),
            lettore.voci(a.incontroDi(intervista)),
        )
        assertEquals(
            listOf(a.vista(riunione, cRiunione[0].voceId, listOf(turnoRiunione.intervallo))),
            lettore.voci(a.incontroDi(riunione)),
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
        val prima = lettore.voci(a.incontroDi(id))
        assertEquals(3, prima?.size, "tre Voci prima dell'unione: $prima")

        a.unisci(a.incontroDi(id), sopravvive = c[2].voceId, rimossa = c[0].voceId)

        assertEquals(
            listOf(
                a.vista(id, c[1].voceId, listOf(1, 3).map { turni[it].intervallo }),
                a.vista(id, c[2].voceId, listOf(0, 2, 4).map { turni[it].intervallo }),
            ).sortedBy { it.voceRef.voceId.numero },
            lettore.voci(a.incontroDi(id)),
        )
        assertEquals(
            listOf(a.vista(altra, cAltra[0].voceId, listOf(IntervalloMs(0, 1_000)))),
            lettore.voci(a.incontroDi(altra)),
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
        assertEquals(2, lettore.voci(a.incontroDi(id))?.size)

        val nuova = a.dividi(a.incontroDi(id), origine = c[0].voceId, segmenti = setOf(ref(id, c[2]), ref(id, c[3])))

        assertTrue(nuova != c[0].voceId && nuova != c[1].voceId, "la nuova Voce ha un voceId nuovo: $nuova")
        assertEquals(
            listOf(
                a.vista(id, c[0].voceId, listOf(turni[0].intervallo)),
                a.vista(id, c[1].voceId, listOf(turni[1].intervallo)),
                a.vista(id, nuova, listOf(2, 3).map { turni[it].intervallo }),
            ).sortedBy { it.voceRef.voceId.numero },
            lettore.voci(a.incontroDi(id)),
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
        assertEquals(2, lettore.voci(a.incontroDi(id))?.size)

        // To a new Voce: its Voce keeps the other Segmento.
        val nuova = a.riassegna(ref(id, c[2]), destinazione = null)
        assertTrue(nuova != c[0].voceId && nuova != c[1].voceId, "la nuova Voce ha un voceId nuovo: $nuova")
        assertEquals(
            listOf(
                a.vista(id, c[0].voceId, listOf(turni[0].intervallo)),
                a.vista(id, c[1].voceId, listOf(turni[1].intervallo)),
                a.vista(id, nuova, listOf(turni[2].intervallo)),
            ).sortedBy { it.voceRef.voceId.numero },
            lettore.voci(a.incontroDi(id)),
        )

        // To an existing Voce: the only Segmento of its Voce leaves, so that Voce ceases to exist.
        assertEquals(c[0].voceId, a.riassegna(ref(id, c[1]), destinazione = c[0].voceId))
        assertEquals(
            listOf(
                a.vista(id, c[0].voceId, listOf(0, 1).map { turni[it].intervallo }),
                a.vista(id, nuova, listOf(turni[2].intervallo)),
            ).sortedBy { it.voceRef.voceId.numero },
            lettore.voci(a.incontroDi(id)),
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
        a.unisci(a.incontroDi(id), sopravvive = c[0].voceId, rimossa = c[2].voceId)

        val nuova = a.dividi(a.incontroDi(id), origine = c[0].voceId, segmenti = setOf(ref(id, c[3])))

        assertTrue(nuova !in c.map { it.voceId }, "voceId mai riusato: $nuova tra ${c.map { it.voceId }}")
        assertEquals(
            listOf(
                a.vista(id, c[0].voceId, listOf(0, 2).map { turni[it].intervallo }),
                a.vista(id, c[1].voceId, listOf(turni[1].intervallo)),
                a.vista(id, nuova, listOf(turni[3].intervallo)),
            ).sortedBy { it.voceRef.voceId.numero },
            lettore.voci(a.incontroDi(id)),
        )
    }

    @Test
    public fun `AC-494 senza Trascritto segmenti restituisce null`() {
        val a = ambiente()
        val lettore = a.lettore
        val mai = a.aggiungiRegistrazione()
        val fallita = a.aggiungiRegistrazione()
        a.fallisciElaborazione(fallita)

        assertNull(lettore.segmenti(SCONOSCIUTO))
        assertNull(lettore.segmenti(a.incontroDi(mai)))
        assertNull(lettore.segmenti(a.incontroDi(fallita)))
    }

    @Test
    public fun `AC-494 ogni Segmento corrente compare una volta ordinato per inizio poi segmentoId con confermato come memorizzato`() {
        val a = ambiente()
        val lettore = a.lettore
        val id = a.aggiungiRegistrazione()
        val turni = listOf(
            SemeTurno(0, IntervalloMs(8_000, 10_000)),
            SemeTurno(1, IntervalloMs(1_000, 3_000)),
            SemeTurno(0, IntervalloMs(1_000, 3_000)), // stesso intervallo del precedente: l'ordine segue il segmentoId
        )
        val c = a.completaElaborazione(id, turni)
        val (prima, seconda) = if (c[1].segmentoId.numero < c[2].segmentoId.numero) c[1] to c[2] else c[2] to c[1]
        a.conferma(ref(id, prima))

        assertEquals(
            listOf(
                SegmentoDiVoce(ref(id, prima), prima.voceId, IntervalloMs(1_000, 3_000), confermato = true),
                SegmentoDiVoce(ref(id, seconda), seconda.voceId, IntervalloMs(1_000, 3_000), confermato = false),
                SegmentoDiVoce(ref(id, c[0]), c[0].voceId, turni[0].intervallo, confermato = false),
            ),
            lettore.segmenti(a.incontroDi(id)),
        )
    }

    @Test
    public fun `AC-494 dopo DividiVoce i segmenti riflettono la Voce corrente e il sottoinsieme spostato e confermato`() {
        val a = ambiente()
        val lettore = a.lettore
        val id = a.aggiungiRegistrazione()
        val turni = listOf(
            SemeTurno(0, IntervalloMs(0, 1_000)),
            SemeTurno(0, IntervalloMs(1_000, 2_000)),
            SemeTurno(1, IntervalloMs(2_000, 3_000)),
        )
        val c = a.completaElaborazione(id, turni)

        val nuova = a.dividi(a.incontroDi(id), origine = c[0].voceId, segmenti = setOf(ref(id, c[1])))

        // [INV-26]: DividiVoce conferma il sottoinsieme spostato (c[1]); l'origine e il resto restano com'erano.
        assertEquals(
            listOf(
                SegmentoDiVoce(ref(id, c[0]), c[0].voceId, turni[0].intervallo, confermato = false),
                SegmentoDiVoce(ref(id, c[1]), nuova, turni[1].intervallo, confermato = true),
                SegmentoDiVoce(ref(id, c[2]), c[2].voceId, turni[2].intervallo, confermato = false),
            ),
            lettore.segmenti(a.incontroDi(id)),
        )
    }

    @Test
    public fun `AC-I24 i VoceRef portano l'incontroId della Parte e nessuno di un altro Incontro`() {
        val a = ambiente()
        val lettore = a.lettore
        val id = a.aggiungiRegistrazione()
        val altra = a.aggiungiRegistrazione()
        a.completaElaborazione(id, listOf(SemeTurno(0, IntervalloMs(0, 1_000)), SemeTurno(1, IntervalloMs(1_000, 2_000))))
        a.completaElaborazione(altra, listOf(SemeTurno(0, IntervalloMs(0, 1_000))))

        val voci = lettore.voci(a.incontroDi(id)).orEmpty()

        assertEquals(2, voci.size, "$voci")
        assertTrue(voci.all { it.voceRef.incontroId == a.incontroDi(id) && it.intervalliPerParte.keys == setOf(id) }, "$voci")
    }

    /** AC-I24 on an Incontro with several Parti. */
    @TestFactory
    public fun `AC-I24 casi con piu Parti`(): List<DynamicTest> {
        return listOf(
            dynamicTest("AC-I24 null finche nessuna Parte e trascritta, poi solo le Voci delle Parti trascritte") {
                val a = ambiente()
                val lettore = a.lettore
                val prima = a.aggiungiRegistrazione()
                val incontro = a.incontroDi(prima)
                val seconda = a.aggiungiParte(incontro)
                a.fallisciElaborazione(prima)
                assertNull(lettore.voci(incontro))
                assertNull(lettore.segmenti(incontro))

                val c = a.completaElaborazione(seconda, listOf(SemeTurno(0, IntervalloMs(0, 1_000))))

                assertEquals(listOf(a.vista(seconda, c[0].voceId, listOf(IntervalloMs(0, 1_000)))), lettore.voci(incontro))
                assertEquals(
                    listOf(SegmentoDiVoce(ref(seconda, c[0]), c[0].voceId, IntervalloMs(0, 1_000), confermato = false)),
                    lettore.segmenti(incontro),
                )
            },
            dynamicTest("AC-I24 una Voce che parla in due Parti compare una volta con gli intervalli di ogni Parte") {
                val a = ambiente()
                val lettore = a.lettore
                val prima = a.aggiungiRegistrazione()
                val incontro = a.incontroDi(prima)
                val seconda = a.aggiungiParte(incontro)
                val c1 = a.completaElaborazione(prima, listOf(SemeTurno(0, IntervalloMs(0, 2_000))))
                val c2 = a.completaElaborazione(
                    seconda,
                    listOf(SemeTurno(0, IntervalloMs(0, 1_000)), SemeTurno(0, IntervalloMs(5_000, 6_000))),
                )
                assertTrue(c2[0].voceId != c1[0].voceId, "una Voce nuova prende il contatore dell'Incontro: $c1 $c2")

                a.riassegna(ref(seconda, c2[1]), destinazione = c1[0].voceId)

                assertEquals(
                    listOf(
                        VoceVista(
                            VoceRef(incontro, c1[0].voceId),
                            mapOf(prima to listOf(IntervalloMs(0, 2_000)), seconda to listOf(IntervalloMs(5_000, 6_000))),
                        ),
                        a.vista(seconda, c2[0].voceId, listOf(IntervalloMs(0, 1_000))),
                    ).sortedBy { it.voceRef.voceId.numero },
                    lettore.voci(incontro),
                )
                assertPerParte(
                    mapOf(
                        prima to listOf(SegmentoDiVoce(ref(prima, c1[0]), c1[0].voceId, IntervalloMs(0, 2_000), false)),
                        seconda to listOf(
                            SegmentoDiVoce(ref(seconda, c2[0]), c2[0].voceId, IntervalloMs(0, 1_000), false),
                            SegmentoDiVoce(ref(seconda, c2[1]), c1[0].voceId, IntervalloMs(5_000, 6_000), true),
                        ),
                    ),
                    lettore.segmenti(incontro),
                )
            },
            dynamicTest("AC-I24 dopo UnisciVoci tra due Parti la sopravvissuta ha gli intervalli di entrambe") {
                val a = ambiente()
                val lettore = a.lettore
                val prima = a.aggiungiRegistrazione()
                val incontro = a.incontroDi(prima)
                val seconda = a.aggiungiParte(incontro)
                val c1 = a.completaElaborazione(
                    prima,
                    listOf(SemeTurno(0, IntervalloMs(0, 2_000)), SemeTurno(1, IntervalloMs(2_000, 3_000))),
                )
                val c2 = a.completaElaborazione(seconda, listOf(SemeTurno(0, IntervalloMs(1_000, 4_000))))

                a.unisci(incontro, sopravvive = c1[0].voceId, rimossa = c2[0].voceId)

                assertEquals(
                    listOf(
                        VoceVista(
                            VoceRef(incontro, c1[0].voceId),
                            mapOf(prima to listOf(IntervalloMs(0, 2_000)), seconda to listOf(IntervalloMs(1_000, 4_000))),
                        ),
                        a.vista(prima, c1[1].voceId, listOf(IntervalloMs(2_000, 3_000))),
                    ).sortedBy { it.voceRef.voceId.numero },
                    lettore.voci(incontro),
                )
            },
        )
    }

    /** The Segmenti of each Parte together, each Parte's in the pinned order; the order between Parti is free. */
    private fun assertPerParte(atteso: Map<RegistrazioneId, List<SegmentoDiVoce>>, segmenti: List<SegmentoDiVoce>?) {
        val parti = segmenti.orEmpty().map { it.segmento.registrazioneId }
        assertEquals(parti.distinct().size, parti.zipWithNext().count { (x, y) -> x != y } + 1, "Parti contigue: $parti")
        assertEquals(atteso, segmenti?.groupBy { it.segmento.registrazioneId })
    }

    private fun AmbienteLettoreVoci.vista(parte: RegistrazioneId, voce: VoceId, intervalli: List<IntervalloMs>): VoceVista =
        VoceVista(VoceRef(incontroDi(parte), voce), mapOf(parte to intervalli))

    private fun ref(parte: RegistrazioneId, coniato: SegmentoConiato): SegmentoRef = SegmentoRef(parte, coniato.segmentoId)

    private companion object {
        val SCONOSCIUTO = IncontroId("incontro-sconosciuto")
    }
}
