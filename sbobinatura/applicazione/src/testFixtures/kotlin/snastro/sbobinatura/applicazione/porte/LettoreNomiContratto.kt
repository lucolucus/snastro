package snastro.sbobinatura.applicazione.porte

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import snastro.kernel.IncontroId
import snastro.kernel.ParlanteId
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Consumer-driven contract of [LettoreNomi] (boundary `porte-sbobinatura`): one subclass per
 * implementation — [LettoreNomiFinta] (D1) and `LettoreNomiDaParlanti` (D2, real-on-real).
 * Each test takes [AmbienteLettoreNomi.lettore] once, up front, and keeps reading through it after
 * every change: an implementation that serves a stale copy fails.
 */
public abstract class LettoreNomiContratto {
    /** A fresh supplier: one Progetto, no Registrazione, no Parlante. */
    protected abstract fun ambiente(): AmbienteLettoreNomi

    @Test
    public fun `AC-51 senza Attribuzioni nomi e vuota`() {
        val a = ambiente()
        val lettore = a.lettore
        val r = a.aggiungiRegistrazione(voci = 2)

        assertEquals(emptyMap(), lettore.nomi(SCONOSCIUTO))
        assertEquals(emptyMap(), lettore.nomi(r.incontroId))
    }

    @Test
    public fun `AC-51 solo le Voci attribuite compaiono con il Nome del loro Parlante`() {
        val a = ambiente()
        val lettore = a.lettore
        val riunione = a.aggiungiRegistrazione(voci = 3)
        val intervista = a.aggiungiRegistrazione(voci = 2)
        val marco = a.confermaNuovoParlante(riunione.voci[0], "Marco")
        a.confermaNuovoParlante(riunione.voci[2], "Giulia")
        a.conferma(intervista.voci[1], marco)

        assertEquals(
            mapOf(riunione.voci[0] to "Marco", riunione.voci[2] to "Giulia"),
            lettore.nomi(riunione.incontroId),
        )
        assertEquals(mapOf(intervista.voci[1] to "Marco"), lettore.nomi(intervista.incontroId))
    }

    @Test
    public fun `AC-51 una Voce attribuita a un Parlante eliminato risolve al suo Nome`() {
        val a = ambiente()
        val lettore = a.lettore
        val r = a.aggiungiRegistrazione(voci = 3)
        val marco = a.confermaNuovoParlante(r.voci[0], "Marco")
        a.confermaNuovoParlante(r.voci[1], "Giulia")

        a.elimina(marco)
        // The Nome of an eliminato is reusable (INV-16): a new attivo Marco does not steal the Voce.
        a.confermaNuovoParlante(r.voci[2], "Marco")

        assertEquals(
            mapOf(r.voci[0] to "Marco", r.voci[1] to "Giulia", r.voci[2] to "Marco"),
            lettore.nomi(r.incontroId),
        )
    }

    @Test
    public fun `AC-51 dopo una rinomina vince il Nome nuovo in ogni Incontro`() {
        val a = ambiente()
        val lettore = a.lettore
        val riunione = a.aggiungiRegistrazione(voci = 2)
        val intervista = a.aggiungiRegistrazione(voci = 1)
        val marco = a.confermaNuovoParlante(riunione.voci[0], "Marco")
        a.conferma(intervista.voci[0], marco)
        a.confermaNuovoParlante(riunione.voci[1], "Giulia")
        assertEquals(mapOf(intervista.voci[0] to "Marco"), lettore.nomi(intervista.incontroId))

        a.rinomina(marco, "Marco Rossi")

        assertEquals(
            mapOf(riunione.voci[0] to "Marco Rossi", riunione.voci[1] to "Giulia"),
            lettore.nomi(riunione.incontroId),
        )
        assertEquals(mapOf(intervista.voci[0] to "Marco Rossi"), lettore.nomi(intervista.incontroId))
    }

    @Test
    public fun `AC-51 una rinomina che cambia solo le maiuscole conta come cambiamento`() {
        val a = ambiente()
        val lettore = a.lettore
        val r = a.aggiungiRegistrazione(voci = 1)
        val marco = a.confermaNuovoParlante(r.voci[0], "marco")
        assertEquals(mapOf(r.voci[0] to "marco"), lettore.nomi(r.incontroId))

        a.rinomina(marco, "Marco")

        assertEquals(mapOf(r.voci[0] to "Marco"), lettore.nomi(r.incontroId))
    }

    @Test
    public fun `AC-51 un Parlante rinominato e poi eliminato conserva l ultimo Nome`() {
        val a = ambiente()
        val lettore = a.lettore
        val r = a.aggiungiRegistrazione(voci = 1)
        val ospite = a.confermaNuovoParlante(r.voci[0], "Ospite")

        a.rinomina(ospite, "Anna Bianchi")
        a.elimina(ospite)

        assertEquals(mapOf(r.voci[0] to "Anna Bianchi"), lettore.nomi(r.incontroId))
    }

    @Test
    public fun `AC-51 cambiare l Attribuzione di una Voce mostra il nuovo Parlante`() {
        val a = ambiente()
        val lettore = a.lettore
        val r = a.aggiungiRegistrazione(voci = 2)
        a.confermaNuovoParlante(r.voci[0], "Marco")
        val giulia = a.confermaNuovoParlante(r.voci[1], "Giulia")
        assertEquals(mapOf(r.voci[0] to "Marco", r.voci[1] to "Giulia"), lettore.nomi(r.incontroId))

        a.conferma(r.voci[0], giulia)

        assertEquals(mapOf(r.voci[0] to "Giulia", r.voci[1] to "Giulia"), lettore.nomi(r.incontroId))
    }

    @Test
    public fun `AC-51 quando l unica Voce di un occasionale passa a un altro Parlante mostra il nuovo Nome`() {
        val a = ambiente()
        val lettore = a.lettore
        val r = a.aggiungiRegistrazione(voci = 2)
        val ospite = a.confermaNuovoParlante(r.voci[0], "Ospite", occasionale = true)
        val giulia = a.confermaNuovoParlante(r.voci[1], "Giulia")
        assertEquals(mapOf(r.voci[0] to "Ospite", r.voci[1] to "Giulia"), lettore.nomi(r.incontroId))
        assertIncontri(lettore, ospite, "Ospite", setOf(r.incontroId))

        // Its only Voce leaves: the occasionale is removed (INV-25). The port cannot observe whether the
        // Parlante row is gone; the case bites at D2, where the real Ambiente physically deletes it.
        a.conferma(r.voci[0], giulia)

        assertEquals(mapOf(r.voci[0] to "Giulia", r.voci[1] to "Giulia"), lettore.nomi(r.incontroId))
        assertTrue(lettore.incontriCon(ospite).isEmpty())
    }

    @Test
    public fun `AC-52 un Parlante sconosciuto non ha Incontri`() {
        val a = ambiente()
        val r = a.aggiungiRegistrazione(voci = 1)
        a.confermaNuovoParlante(r.voci[0], "Marco")

        assertTrue(a.lettore.incontriCon(ParlanteId("parlante-sconosciuto")).isEmpty())
    }

    @Test
    public fun `AC-52 incontriCon elenca una volta ogni Incontro con un Attribuzione al Parlante e nessun altro`() {
        val a = ambiente()
        val lettore = a.lettore
        val riunione = a.aggiungiRegistrazione(voci = 3)
        val intervista = a.aggiungiRegistrazione(voci = 1)
        val altra = a.aggiungiRegistrazione(voci = 1)
        a.aggiungiRegistrazione(voci = 1)
        val marco = a.confermaNuovoParlante(riunione.voci[0], "Marco")
        // Two Voci of the same Incontro on the same Parlante is allowed (INV-22).
        a.conferma(riunione.voci[2], marco)
        a.conferma(intervista.voci[0], marco)
        val giulia = a.confermaNuovoParlante(altra.voci[0], "Giulia")

        assertIncontri(lettore, marco, "Marco", setOf(riunione.incontroId, intervista.incontroId))
        assertIncontri(lettore, giulia, "Giulia", setOf(altra.incontroId))
    }

    @Test
    public fun `AC-52 un Incontro la cui Voce passa a un altro Parlante non e piu elencato`() {
        val a = ambiente()
        val lettore = a.lettore
        val riunione = a.aggiungiRegistrazione(voci = 1)
        val intervista = a.aggiungiRegistrazione(voci = 1)
        val marco = a.confermaNuovoParlante(riunione.voci[0], "Marco")
        a.conferma(intervista.voci[0], marco)
        assertIncontri(lettore, marco, "Marco", setOf(riunione.incontroId, intervista.incontroId))

        val giulia = a.confermaNuovoParlante(intervista.voci[0], "Giulia")

        assertIncontri(lettore, marco, "Marco", setOf(riunione.incontroId))
        assertIncontri(lettore, giulia, "Giulia", setOf(intervista.incontroId))

        a.conferma(riunione.voci[0], giulia)

        assertTrue(lettore.incontriCon(marco).isEmpty())
        assertIncontri(lettore, giulia, "Giulia", setOf(riunione.incontroId, intervista.incontroId))
    }

    @Test
    public fun `AC-52 gli Incontri di un Parlante eliminato restano elencati`() {
        val a = ambiente()
        val lettore = a.lettore
        val riunione = a.aggiungiRegistrazione(voci = 1)
        val intervista = a.aggiungiRegistrazione(voci = 1)
        val marco = a.confermaNuovoParlante(riunione.voci[0], "Marco")
        a.conferma(intervista.voci[0], marco)

        a.elimina(marco)

        assertIncontri(lettore, marco, "Marco", setOf(riunione.incontroId, intervista.incontroId))
    }

    @Test
    public fun `AC-I27 nomi di un Incontro ha solo le sue Voci attribuite e incontriCon lo elenca una volta`() {
        val a = ambiente()
        val lettore = a.lettore
        val riunione = a.aggiungiRegistrazione(voci = 3)
        val altra = a.aggiungiRegistrazione(voci = 1)
        val marco = a.confermaNuovoParlante(riunione.voci[0], "Marco")
        a.conferma(riunione.voci[2], marco)
        a.conferma(altra.voci[0], marco)

        assertEquals(mapOf(riunione.voci[0] to "Marco", riunione.voci[2] to "Marco"), lettore.nomi(riunione.incontroId))
        assertTrue(riunione.voci.all { it.incontroId == riunione.incontroId })
        assertTrue(riunione.incontroId != altra.incontroId, "ogni Registrazione importata da sola e un Incontro nuovo")
        assertIncontri(lettore, marco, "Marco", setOf(riunione.incontroId, altra.incontroId))
    }

    /** AC-I27 on an Incontro of several Parti. */
    @TestFactory
    public fun `AC-I27 Incontro con piu Parti`(): List<DynamicTest> =
        listOf(
            dynamicTest("AC-I27 nomi ha un Nome per Voce qualunque sia la Parte") { unNomePerVoceInOgniParte() },
            dynamicTest("AC-I27 incontriCon elenca una volta l Incontro attribuito in piu Parti") {
                unaVoltaLIncontroAttribuitoInPiuParti()
            },
        )

    private fun unNomePerVoceInOgniParte() {
        val a = ambiente()
        val lettore = a.lettore
        val prima = a.aggiungiRegistrazione(voci = 2)
        val seconda = a.aggiungiParte(prima.incontroId, voci = 2)
        val marco = a.confermaNuovoParlante(prima.voci[0], "Marco")
        a.confermaNuovoParlante(seconda.voci[0], "Giulia")
        a.conferma(seconda.voci[1], marco)

        assertEquals(prima.incontroId, seconda.incontroId)
        assertTrue(seconda.voci.none { it in prima.voci }, "le Voci nuove della seconda Parte: ${seconda.voci}")
        assertEquals(
            mapOf(prima.voci[0] to "Marco", seconda.voci[0] to "Giulia", seconda.voci[1] to "Marco"),
            lettore.nomi(prima.incontroId),
        )
    }

    private fun unaVoltaLIncontroAttribuitoInPiuParti() {
        val a = ambiente()
        val lettore = a.lettore
        val prima = a.aggiungiRegistrazione(voci = 1)
        val seconda = a.aggiungiParte(prima.incontroId, voci = 1)
        val altro = a.aggiungiRegistrazione(voci = 1)
        val marco = a.confermaNuovoParlante(prima.voci[0], "Marco")
        a.conferma(seconda.voci[0], marco)
        val giulia = a.confermaNuovoParlante(altro.voci[0], "Giulia")

        assertIncontri(lettore, marco, "Marco", setOf(prima.incontroId))
        assertIncontri(lettore, giulia, "Giulia", setOf(altro.incontroId))
    }

    /** Exactly [attesi], each once, and each listed Incontro really shows [nome] through [LettoreNomi.nomi]. */
    private fun assertIncontri(
        lettore: LettoreNomi,
        parlante: ParlanteId,
        nome: String,
        attesi: Set<IncontroId>,
    ) {
        val elencati = lettore.incontriCon(parlante)
        assertEquals(attesi, elencati.toSet())
        assertEquals(attesi.size, elencati.size, "ogni Incontro una sola volta: $elencati")
        for (i in elencati) {
            assertTrue(nome in lettore.nomi(i).values, "$i elencato ma nomi non mostra $nome: ${lettore.nomi(i)}")
        }
    }

    private companion object {
        val SCONOSCIUTO = IncontroId("incontro-sconosciuto")
    }
}
