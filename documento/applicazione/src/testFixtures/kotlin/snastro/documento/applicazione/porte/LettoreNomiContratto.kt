package snastro.documento.applicazione.porte

import org.junit.jupiter.api.Test
import snastro.kernel.ParlanteId
import snastro.kernel.RegistrazioneId
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Consumer-driven contract of [LettoreNomi] (boundary `nomi-per-documento`): one subclass per
 * implementation — [LettoreNomiFinta] (D1) and `lettore-nomi-da-parlanti` (D2, real-on-real).
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

        assertEquals(emptyMap(), lettore.nomi(SCONOSCIUTA))
        assertEquals(emptyMap(), lettore.nomi(r.id))
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
            lettore.nomi(riunione.id),
        )
        assertEquals(mapOf(intervista.voci[1] to "Marco"), lettore.nomi(intervista.id))
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
            lettore.nomi(r.id),
        )
    }

    @Test
    public fun `AC-51 dopo una rinomina vince il Nome nuovo in ogni Registrazione`() {
        val a = ambiente()
        val lettore = a.lettore
        val riunione = a.aggiungiRegistrazione(voci = 2)
        val intervista = a.aggiungiRegistrazione(voci = 1)
        val marco = a.confermaNuovoParlante(riunione.voci[0], "Marco")
        a.conferma(intervista.voci[0], marco)
        a.confermaNuovoParlante(riunione.voci[1], "Giulia")
        assertEquals(mapOf(intervista.voci[0] to "Marco"), lettore.nomi(intervista.id))

        a.rinomina(marco, "Marco Rossi")

        assertEquals(
            mapOf(riunione.voci[0] to "Marco Rossi", riunione.voci[1] to "Giulia"),
            lettore.nomi(riunione.id),
        )
        assertEquals(mapOf(intervista.voci[0] to "Marco Rossi"), lettore.nomi(intervista.id))
    }

    @Test
    public fun `AC-51 una rinomina che cambia solo le maiuscole conta come cambiamento`() {
        val a = ambiente()
        val lettore = a.lettore
        val r = a.aggiungiRegistrazione(voci = 1)
        val marco = a.confermaNuovoParlante(r.voci[0], "marco")
        assertEquals(mapOf(r.voci[0] to "marco"), lettore.nomi(r.id))

        a.rinomina(marco, "Marco")

        assertEquals(mapOf(r.voci[0] to "Marco"), lettore.nomi(r.id))
    }

    @Test
    public fun `AC-51 un Parlante rinominato e poi eliminato conserva l ultimo Nome`() {
        val a = ambiente()
        val lettore = a.lettore
        val r = a.aggiungiRegistrazione(voci = 1)
        val ospite = a.confermaNuovoParlante(r.voci[0], "Ospite")

        a.rinomina(ospite, "Anna Bianchi")
        a.elimina(ospite)

        assertEquals(mapOf(r.voci[0] to "Anna Bianchi"), lettore.nomi(r.id))
    }

    @Test
    public fun `AC-51 cambiare l Attribuzione di una Voce mostra il nuovo Parlante`() {
        val a = ambiente()
        val lettore = a.lettore
        val r = a.aggiungiRegistrazione(voci = 2)
        a.confermaNuovoParlante(r.voci[0], "Marco")
        val giulia = a.confermaNuovoParlante(r.voci[1], "Giulia")
        assertEquals(mapOf(r.voci[0] to "Marco", r.voci[1] to "Giulia"), lettore.nomi(r.id))

        a.conferma(r.voci[0], giulia)

        assertEquals(mapOf(r.voci[0] to "Giulia", r.voci[1] to "Giulia"), lettore.nomi(r.id))
    }

    @Test
    public fun `AC-52 un Parlante sconosciuto non ha Registrazioni`() {
        val a = ambiente()
        val r = a.aggiungiRegistrazione(voci = 1)
        a.confermaNuovoParlante(r.voci[0], "Marco")

        assertTrue(a.lettore.registrazioniCon(ParlanteId("parlante-sconosciuto")).isEmpty())
    }

    @Test
    public fun `AC-52 registrazioniCon elenca una volta ogni Registrazione con un Attribuzione al Parlante e nessun altra`() {
        val a = ambiente()
        val lettore = a.lettore
        val riunione = a.aggiungiRegistrazione(voci = 3)
        val intervista = a.aggiungiRegistrazione(voci = 1)
        val altra = a.aggiungiRegistrazione(voci = 1)
        a.aggiungiRegistrazione(voci = 1)
        val marco = a.confermaNuovoParlante(riunione.voci[0], "Marco")
        // Two Voci of the same Registrazione on the same Parlante is allowed (INV-22).
        a.conferma(riunione.voci[2], marco)
        a.conferma(intervista.voci[0], marco)
        val giulia = a.confermaNuovoParlante(altra.voci[0], "Giulia")

        assertRegistrazioni(lettore, marco, "Marco", setOf(riunione.id, intervista.id))
        assertRegistrazioni(lettore, giulia, "Giulia", setOf(altra.id))
    }

    @Test
    public fun `AC-52 una Registrazione la cui Voce passa a un altro Parlante non e piu elencata`() {
        val a = ambiente()
        val lettore = a.lettore
        val riunione = a.aggiungiRegistrazione(voci = 1)
        val intervista = a.aggiungiRegistrazione(voci = 1)
        val marco = a.confermaNuovoParlante(riunione.voci[0], "Marco")
        a.conferma(intervista.voci[0], marco)
        assertRegistrazioni(lettore, marco, "Marco", setOf(riunione.id, intervista.id))

        val giulia = a.confermaNuovoParlante(intervista.voci[0], "Giulia")

        assertRegistrazioni(lettore, marco, "Marco", setOf(riunione.id))
        assertRegistrazioni(lettore, giulia, "Giulia", setOf(intervista.id))

        a.conferma(riunione.voci[0], giulia)

        assertTrue(lettore.registrazioniCon(marco).isEmpty())
        assertRegistrazioni(lettore, giulia, "Giulia", setOf(riunione.id, intervista.id))
    }

    @Test
    public fun `AC-52 le Registrazioni di un Parlante eliminato restano elencate`() {
        val a = ambiente()
        val lettore = a.lettore
        val riunione = a.aggiungiRegistrazione(voci = 1)
        val intervista = a.aggiungiRegistrazione(voci = 1)
        val marco = a.confermaNuovoParlante(riunione.voci[0], "Marco")
        a.conferma(intervista.voci[0], marco)

        a.elimina(marco)

        assertRegistrazioni(lettore, marco, "Marco", setOf(riunione.id, intervista.id))
    }

    /** Exactly [attese], each once, and each listed Registrazione really shows [nome] through [LettoreNomi.nomi]. */
    private fun assertRegistrazioni(
        lettore: LettoreNomi,
        parlante: ParlanteId,
        nome: String,
        attese: Set<RegistrazioneId>,
    ) {
        val elencate = lettore.registrazioniCon(parlante)
        assertEquals(attese, elencate.toSet())
        assertEquals(attese.size, elencate.size, "ogni Registrazione una sola volta: $elencate")
        for (r in elencate) {
            assertTrue(nome in lettore.nomi(r).values, "$r elencata ma nomi non mostra $nome: ${lettore.nomi(r)}")
        }
    }

    private companion object {
        val SCONOSCIUTA = RegistrazioneId("registrazione-sconosciuta")
    }
}
