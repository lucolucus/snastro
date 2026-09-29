package snastro.sintesi.applicazione.porte

import org.junit.jupiter.api.Test
import snastro.kernel.RegistrazioneId
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Consumer-driven contract of Sintesi's [LettoreNomi] (boundary `nomi-per-sintesi`): one subclass
 * per implementation — [LettoreNomiFinta] (D1) and `LettoreNomiDaParlanti` (D2). Each test takes
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
        assertEquals(emptyMap(), lettore.nomi(r.id))

        a.attribuisciANuovo(r.voci[0], "Marco")
        a.attribuisciANuovo(r.voci[2], "Giulia")

        assertEquals(mapOf(r.voci[0] to "Marco", r.voci[2] to "Giulia"), lettore.nomi(r.id))
    }

    @Test
    public fun `AC-S8 dopo RinominaParlante nomi restituisce il Nome nuovo`() {
        val a = ambiente()
        val lettore = a.lettore
        val r = a.aggiungiRegistrazione(voci = 2)
        val marco = a.attribuisciANuovo(r.voci[0], "Marco")
        a.attribuisci(r.voci[1], marco)
        assertEquals(mapOf(r.voci[0] to "Marco", r.voci[1] to "Marco"), lettore.nomi(r.id))

        a.rinomina(marco, "Marco Rossi")

        assertEquals(mapOf(r.voci[0] to "Marco Rossi", r.voci[1] to "Marco Rossi"), lettore.nomi(r.id))
    }

    @Test
    public fun `AC-S8 una Voce ri-attribuita risolve al Parlante attuale, non al primo`() {
        val a = ambiente()
        val lettore = a.lettore
        val r = a.aggiungiRegistrazione(voci = 1)
        a.attribuisciANuovo(r.voci[0], "Marco")
        assertEquals(mapOf(r.voci[0] to "Marco"), lettore.nomi(r.id))

        // Re-attribution of the SAME Voce, to a DIFFERENT (brand new) Parlante: an adapter caching the
        // Voce->Parlante pairing from the FIRST attribution (instead of re-reading it) would still answer "Marco".
        a.attribuisciANuovo(r.voci[0], "Giulia")

        assertEquals(mapOf(r.voci[0] to "Giulia"), lettore.nomi(r.id))
    }

    @Test
    public fun `AC-S8 un rinomina di sole maiuscole minuscole e visibile`() {
        val a = ambiente()
        val lettore = a.lettore
        val r = a.aggiungiRegistrazione(voci = 1)
        val marco = a.attribuisciANuovo(r.voci[0], "marco")
        assertEquals(mapOf(r.voci[0] to "marco"), lettore.nomi(r.id))

        // Case-only rename: a case-folding implementation (comparing "marco" == "Marco" and skipping the
        // write, or normalizing the stored Nome) would still answer "marco" here.
        a.rinomina(marco, "Marco")

        assertEquals(mapOf(r.voci[0] to "Marco"), lettore.nomi(r.id))
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

        assertEquals(mapOf(r.voci[0] to "Anna", r.voci[1] to "Anna"), lettore.nomi(r.id))
    }

    @Test
    public fun `AC-S9 ogni chiave appartiene alla Registrazione richiesta`() {
        val a = ambiente()
        val lettore = a.lettore
        val riunione = a.aggiungiRegistrazione(voci = 2)
        val intervista = a.aggiungiRegistrazione(voci = 2)
        val marco = a.attribuisciANuovo(riunione.voci[0], "Marco")
        a.attribuisci(intervista.voci[1], marco)
        a.attribuisciANuovo(intervista.voci[0], "Giulia")

        for (r in listOf(riunione, intervista)) {
            val nomi = lettore.nomi(r.id)
            assertTrue(nomi.isNotEmpty())
            assertTrue(nomi.keys.all { it.registrazioneId == r.id }, "chiavi fuori da ${r.id}: ${nomi.keys}")
        }
        assertEquals(mapOf(riunione.voci[0] to "Marco"), lettore.nomi(riunione.id))
        assertEquals(mapOf(intervista.voci[0] to "Giulia", intervista.voci[1] to "Marco"), lettore.nomi(intervista.id))
    }

    @Test
    public fun `AC-S9 una Registrazione sconosciuta da una mappa vuota`() {
        val a = ambiente()
        val r = a.aggiungiRegistrazione(voci = 1)
        a.attribuisciANuovo(r.voci[0], "Marco")

        assertEquals(emptyMap(), a.lettore.nomi(RegistrazioneId("registrazione-sconosciuta")))
    }
}
