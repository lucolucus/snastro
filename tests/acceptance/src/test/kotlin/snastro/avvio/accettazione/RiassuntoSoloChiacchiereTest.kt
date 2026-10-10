package snastro.avvio.accettazione

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * R1 · punti cardine (EX-24): a meeting of chit-chat alone, with no decision and no task, still gives a Riassunto
 * `pronto` with the Sommario alone — the app adds no list and no section of its own. Bench and substituted boundaries:
 * [BancoRiassunto], here hearing [CHIACCHIERE]; that the real model's Sommario of such a meeting says nothing was
 * decided or assigned is measured with `benchmarkRiassunto` on `.mismagent/spikes/campioni/chiacchiere-meteo-pranzo.md`
 * (`.mismagent/spikes/h1.md`).
 */
class RiassuntoSoloChiacchiereTest : BancoRiassunto(RiconoscitoreCheDice(CHIACCHIERE)) {
    @Test
    fun `EX-24 una riunione di sole chiacchiere da un riassunto pronto col solo sommario`() {
        libreria.sommario = SOMMARIO_DELLE_CHIACCHIERE

        val contenuto = schedaRiassunto().riassumi(argomento = null)

        val riunione = riunioneNel(libreria.prompt.single())
        CHIACCHIERE.forEach { assertTrue(it in riunione, "le chiacchiere arrivano al modello: $it") }
        val sommario = assertNotNull(contenuto.sommario)
        assertTrue("meteo" in sommario && "pranzo" in sommario, sommario)
        assertEquals(emptyList(), contenuto.decisioni, "decisioni")
        assertEquals(emptyList(), contenuto.questioniAperte, "questioni aperte")
        assertEquals(emptyList(), contenuto.azioni, "azioni")
        assertEquals(emptyList(), contenuto.puntiChiave, "punti chiave")
    }

    private companion object {
        val CHIACCHIERE = listOf(
            "Qui piove da stamattina, non ha mai smesso.",
            "Pensavo di provare quel posto nuovo del ramen, magari a pranzo.",
        )

        const val SOMMARIO_DELLE_CHIACCHIERE = "{V1} e {V2} chiacchierano del meteo e di dove andare a pranzo."
    }
}
