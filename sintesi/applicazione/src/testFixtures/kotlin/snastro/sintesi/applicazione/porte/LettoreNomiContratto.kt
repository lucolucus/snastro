package snastro.sintesi.applicazione.porte

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import snastro.kernel.IncontroId
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Consumer-driven contract of Sintesi's [LettoreNomi] (boundaries `nomi-per-sintesi`, `porte-sintesi`): one
 * subclass per implementation — [LettoreNomiFinta] (D1) and `LettoreNomiDaParlanti` (D2). The multi-Parte cases are
 * registered only when [AmbienteLettoreNomi.piuPartiPerIncontro] (D-0037), never skipped. Each test takes
 * [AmbienteLettoreNomi.lettore] once, up front, and keeps reading through it after every change: an
 * implementation serving a stale copy fails (names are read at run time, never stored — INV-S5).
 */
public abstract class LettoreNomiContratto {
    /** A fresh supplier: one Progetto, no Registrazione, no Parlante. */
    protected abstract fun ambiente(): AmbienteLettoreNomi

    @Test
    public fun `AC-S8 solo le Voci attribuite hanno una chiave con il Nome del loro Parlante`() {
        val a = ambiente()
        val lettore = a.lettore
        val r = a.aggiungiRegistrazione(voci = 3)
        assertEquals(emptyMap(), lettore.nomi(r.incontroId))

        a.attribuisciANuovo(r.voci[0], "Marco")
        a.attribuisciANuovo(r.voci[2], "Giulia")

        assertEquals(mapOf(r.voci[0] to "Marco", r.voci[2] to "Giulia"), lettore.nomi(r.incontroId))
    }

    @Test
    public fun `AC-S8 dopo RinominaParlante nomi restituisce il Nome nuovo`() {
        val a = ambiente()
        val lettore = a.lettore
        val r = a.aggiungiRegistrazione(voci = 2)
        val marco = a.attribuisciANuovo(r.voci[0], "Marco")
        a.attribuisci(r.voci[1], marco)
        assertEquals(mapOf(r.voci[0] to "Marco", r.voci[1] to "Marco"), lettore.nomi(r.incontroId))

        a.rinomina(marco, "Marco Rossi")

        assertEquals(mapOf(r.voci[0] to "Marco Rossi", r.voci[1] to "Marco Rossi"), lettore.nomi(r.incontroId))
    }

    @Test
    public fun `AC-S8 una Voce ri-attribuita risolve al Parlante attuale, non al primo`() {
        val a = ambiente()
        val lettore = a.lettore
        val r = a.aggiungiRegistrazione(voci = 1)
        a.attribuisciANuovo(r.voci[0], "Marco")
        assertEquals(mapOf(r.voci[0] to "Marco"), lettore.nomi(r.incontroId))

        // Re-attribution of the SAME Voce, to a DIFFERENT (brand new) Parlante: an adapter caching the
        // Voce->Parlante pairing from the FIRST attribution (instead of re-reading it) would still answer "Marco".
        a.attribuisciANuovo(r.voci[0], "Giulia")

        assertEquals(mapOf(r.voci[0] to "Giulia"), lettore.nomi(r.incontroId))
    }

    @Test
    public fun `AC-S8 un rinomina di sole maiuscole minuscole e visibile`() {
        val a = ambiente()
        val lettore = a.lettore
        val r = a.aggiungiRegistrazione(voci = 1)
        val marco = a.attribuisciANuovo(r.voci[0], "marco")
        assertEquals(mapOf(r.voci[0] to "marco"), lettore.nomi(r.incontroId))

        // Case-only rename: a case-folding implementation (comparing "marco" == "Marco" and skipping the
        // write, or normalizing the stored Nome) would still answer "marco" here.
        a.rinomina(marco, "Marco")

        assertEquals(mapOf(r.voci[0] to "Marco"), lettore.nomi(r.incontroId))
    }

    @Test
    public fun `AC-S8 un Parlante eliminato risolve ancora al suo Nome`() {
        val a = ambiente()
        val lettore = a.lettore
        val r = a.aggiungiRegistrazione(voci = 2)
        val ospite = a.attribuisciANuovo(r.voci[0], "Ospite")
        a.rinomina(ospite, "Anna")

        a.elimina(ospite)
        // The Nome of an eliminato is reusable: a new attivo Anna does not steal the Voce.
        a.attribuisciANuovo(r.voci[1], "Anna")

        assertEquals(mapOf(r.voci[0] to "Anna", r.voci[1] to "Anna"), lettore.nomi(r.incontroId))
    }

    @Test
    public fun `AC-S9 ogni chiave appartiene all Incontro richiesto`() {
        val a = ambiente()
        val lettore = a.lettore
        val riunione = a.aggiungiRegistrazione(voci = 2)
        val intervista = a.aggiungiRegistrazione(voci = 2)
        val marco = a.attribuisciANuovo(riunione.voci[0], "Marco")
        a.attribuisci(intervista.voci[1], marco)
        a.attribuisciANuovo(intervista.voci[0], "Giulia")

        for (r in listOf(riunione, intervista)) {
            val nomi = lettore.nomi(r.incontroId)
            assertTrue(nomi.isNotEmpty())
            assertTrue(nomi.keys.all { it.incontroId == r.incontroId }, "chiavi fuori da ${r.incontroId}: ${nomi.keys}")
        }
        assertEquals(mapOf(riunione.voci[0] to "Marco"), lettore.nomi(riunione.incontroId))
        assertEquals(
            mapOf(intervista.voci[0] to "Giulia", intervista.voci[1] to "Marco"),
            lettore.nomi(intervista.incontroId),
        )
    }

    @Test
    public fun `AC-S9 un Incontro sconosciuto da una mappa vuota`() {
        val a = ambiente()
        val r = a.aggiungiRegistrazione(voci = 1)
        a.attribuisciANuovo(r.voci[0], "Marco")

        assertEquals(emptyMap(), a.lettore.nomi(IncontroId("incontro-sconosciuto")))
    }

    /** nomi(incontroId) on an Incontro of several Parti (registered only when the supplier can seed one, D-0037). */
    @TestFactory
    public fun `AC-S8 Incontro con piu Parti`(): List<DynamicTest> =
        if (!ambiente().piuPartiPerIncontro) {
            emptyList()
        } else {
            listOf(
                dynamicTest("AC-S8 nomi ha le Voci attribuite di ogni Parte dell Incontro e di nessun altro") {
                    leVociDiOgniParte()
                },
            )
        }

    private fun leVociDiOgniParte() {
        val a = ambiente()
        val lettore = a.lettore
        val prima = a.aggiungiRegistrazione(voci = 2)
        val seconda = a.aggiungiParte(prima.incontroId, voci = 2)
        val altro = a.aggiungiRegistrazione(voci = 1)
        val marco = a.attribuisciANuovo(prima.voci[0], "Marco")
        a.attribuisciANuovo(seconda.voci[1], "Giulia")
        a.attribuisci(altro.voci[0], marco)

        assertEquals(prima.incontroId, seconda.incontroId)
        assertTrue(seconda.voci.none { it in prima.voci }, "le Voci nuove della seconda Parte: ${seconda.voci}")
        assertEquals(mapOf(prima.voci[0] to "Marco", seconda.voci[1] to "Giulia"), lettore.nomi(prima.incontroId))
        assertEquals(mapOf(altro.voci[0] to "Marco"), lettore.nomi(altro.incontroId))
    }
}
