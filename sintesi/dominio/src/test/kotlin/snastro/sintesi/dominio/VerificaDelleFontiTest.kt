package snastro.sintesi.dominio

import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.kernel.atteso
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** INV-S4 through [Riassunto.completa], against the structure {1→V1, 2→V2, 3→V1}. */
class VerificaDelleFontiTest {
    private fun completa(bozza: BozzaRiassunto): Riassunto =
        unRiassuntoInCorso().also { it.completa(bozza, PARTE, unaStruttura()).atteso() }

    private fun segmenti(vararg n: Int) = n.map(::SegmentoId).toSet()

    @Test
    fun `INV-S4 Fonti non valide scartate e duplicate collassate, elemento senza Fonti valide scartato e contato`() {
        val r = completa(
            unaBozza(
                decisioni = listOf(
                    unElemento("tiene", 2, 9, 2),
                    unElemento("solo nove", 9),
                    BozzaElemento("nessuna", fonti = emptyList(), voce = null),
                ),
            ),
        )

        assertEquals(listOf(Decisione(testo("tiene"), segmenti(2))), r.decisioni)
        assertEquals(2, r.omessi)
    }

    @Test
    fun `INV-S4 una Decisione con Fonti valide non cambia omessi`() {
        val r = completa(unaBozza(decisioni = listOf(unElemento("tiene", 2, 9, 2))))

        assertEquals(0, r.omessi)
    }

    @Test
    fun `INV-S4 Responsabile non valido rimosso con elemento tenuto, valido legato alla Voce`() {
        val r = completa(unaBozza(azioni = listOf(unElemento("fa", 1, voce = 7), unElemento("fa", 1, voce = 2))))

        assertEquals(
            listOf(Azione(testo("fa"), segmenti(1), null), Azione(testo("fa"), segmenti(1), VoceId(2))),
            r.azioni,
        )
        assertEquals(0, r.omessi)
    }

    @Test
    fun `INV-S4 parlante del PuntoChiave legato solo se Voce di una sua Fonte valida`() {
        val r = completa(
            unaBozza(puntiChiave = listOf(unElemento("punto", 1, 3, voce = 2), unElemento("punto", 1, 3, voce = 1))),
        )

        assertEquals(
            listOf(
                PuntoChiave(testo("punto"), segmenti(1, 3), null),
                PuntoChiave(testo("punto"), segmenti(1, 3), VoceId(1)),
            ),
            r.puntiChiave,
        )
        assertEquals(0, r.omessi)
    }

    @Test
    fun `INV-S4 token di Voce assente scarta elemento o Sommario e conta, gli altri restano`() {
        val r = completa(
            unaBozza(
                sommario = "{V5} apre",
                decisioni = listOf(unElemento("{V2} propone il budget", 1), unElemento("{V5} propone", 1)),
            ),
        )

        assertNull(r.sommario)
        assertEquals(listOf(Decisione(testo("{V2} propone il budget"), segmenti(1))), r.decisioni)
        assertEquals(2, r.omessi)
    }

    @Test
    fun `INV-S4 senza Sommario ne elementi verificabili completa finisce fallito senza contenuto`() {
        val r = unRiassuntoInCorso()

        val conclusione = r.completa(
            unaBozza(
                sommario = "{V5}",
                decisioni = listOf(unElemento("x", 9)),
                azioni = listOf(unElemento("{V9} y", 1)),
            ),
            PARTE,
            unaStruttura(),
        ).atteso()

        assertEquals(ConclusioneRiassunto.Fallito(MotivoFallimento.NESSUN_CONTENUTO_VERIFICABILE), conclusione)
        assertTrue(r.fallito)
        assertNull(r.sommario)
        assertNull(r.omessi)
        assertNull(r.struttura)
        assertTrue(r.decisioni.isEmpty() && r.azioni.isEmpty())
    }

    @Test
    fun `INV-S4 solo un Sommario valido e zero elementi da pronto`() {
        val r = unRiassuntoInCorso()

        val conclusione = r.completa(unaBozza(sommario = "{V1} riassume"), PARTE, unaStruttura()).atteso()

        assertIs<ConclusioneRiassunto.Pronto>(conclusione)
        assertEquals(testo("{V1} riassume"), r.sommario?.testo)
    }

    @Test
    fun `INV-S4 omessi conta esattamente elementi e Sommario scartati e nessun testo scartato e esposto`() {
        val r = unRiassuntoInCorso()
        val bozza = unaBozza(
            sommario = "SCARTATO-S {V8}",
            decisioni = listOf(unElemento("tenuta-d", 1), unElemento("SCARTATO-1", 9)),
            questioniAperte = listOf(unElemento("tenuta-q", 2)),
            azioni = listOf(unElemento("SCARTATO-2 {V}", 1)),
            puntiChiave = listOf(unElemento("tenuto-p", 3)),
        )

        val conclusione = r.completa(bozza, PARTE, unaStruttura()).atteso()

        assertEquals(ConclusioneRiassunto.Pronto(omessi = 3), conclusione)
        assertEquals(3, r.omessi)
        val testi = listOfNotNull(r.sommario?.testo) + r.decisioni.map { it.testo } +
            r.questioniAperte.map { it.testo } + r.azioni.map { it.testo } + r.puntiChiave.map { it.testo }
        val esposti = testi.map { it.codifica() }
        assertEquals(listOf("tenuta-d", "tenuta-q", "tenuto-p"), esposti)
        assertTrue(esposti.none { "SCARTATO" in it })
    }

    @Test
    fun `A29 un elemento con Fonti valide ma testo vuoto e scartato come un Sommario vuoto, non tenuto`() {
        val r = completa(
            unaBozza(
                sommario = "   ",
                decisioni = listOf(BozzaElemento("  ", fonti = listOf(1), voce = null), unElemento("tenuta", 1)),
            ),
        )

        assertNull(r.sommario, "un Sommario vuoto resta assente")
        assertEquals(listOf(Decisione(testo("tenuta"), segmenti(1))), r.decisioni, "l elemento vuoto non e tenuto")
        assertEquals(0, r.omessi, "come il Sommario vuoto, il testo vuoto non e contato: non e stato scartato nulla")
    }

    @Test
    fun `INV-S5 un token malformato scarta l elemento che lo porta e lo conta`() {
        listOf("{ solo", "solo }", "{V} x", "{V0} x", "{Vx} x").forEach { malformato ->
            val r = completa(unaBozza(decisioni = listOf(unElemento(malformato, 1), unElemento("tenuta", 1))))

            assertEquals(listOf(Decisione(testo("tenuta"), segmenti(1))), r.decisioni, malformato)
            assertEquals(1, r.omessi, malformato)
        }
    }
}
