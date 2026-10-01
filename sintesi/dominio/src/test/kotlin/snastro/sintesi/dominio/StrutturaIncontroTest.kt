package snastro.sintesi.dominio

import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.kernel.atteso
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StrutturaIncontroTest {
    private val a = RegistrazioneId("A")
    private val b = RegistrazioneId("B")
    private val c = RegistrazioneId("C")

    private fun struttura(vararg parti: Pair<RegistrazioneId, StrutturaTrascritto?>) = StrutturaIncontro(parti.toList())

    private val registrata = struttura(a to unaStruttura(1 to 1, 2 to 2), b to unaStruttura(1 to 3))

    private fun prontoSu(s: StrutturaIncontro): Riassunto = unRiassuntoInCorso().also {
        it.completa(unaBozza(sommario = "{V1} apre"), s, listOf(ref(a, 1))).atteso()
    }

    @Test
    fun `INV-I11 la chiave di A (1-1, 2-2) e B (1-3) unisce le Parti in ordine`() {
        assertEquals("A=1:1,2:2;B=1:3", registrata.chiave)
        assertEquals("A=1:1,2:2;B=", struttura(a to unaStruttura(1 to 1, 2 to 2), b to null).chiave)
    }

    @Test
    fun `INV-I11 superato per ogni cambio di struttura e non piu quando la struttura esatta torna`() {
        val r = prontoSu(registrata)

        assertEquals(registrata.chiave, r.struttura)
        assertFalse(r.superato(registrata), "stessa struttura")
        val cambi = mapOf(
            "Revisione tra Parti" to struttura(a to unaStruttura(1 to 1, 2 to 3), b to unaStruttura(1 to 3)),
            "ritrascrizione di B (nuovi id)" to struttura(a to unaStruttura(1 to 1, 2 to 2), b to unaStruttura(2 to 4)),
            "riordino (B prima di A)" to struttura(b to unaStruttura(1 to 3), a to unaStruttura(1 to 1, 2 to 2)),
            "eliminazione di B" to struttura(a to unaStruttura(1 to 1, 2 to 2)),
            "import di C (C= in coda)" to
                struttura(a to unaStruttura(1 to 1, 2 to 2), b to unaStruttura(1 to 3), c to null),
        )
        cambi.forEach { (caso, corrente) -> assertTrue(r.superato(corrente), caso) }
        assertFalse(
            r.superato(struttura(a to unaStruttura(2 to 2, 1 to 1), b to unaStruttura(1 to 3))),
            "la struttura esatta ripristinata non e superata",
        )
    }

    @Test
    fun `INV-I11 rinominare un Parlante non cambia la struttura, che non contiene nomi`() {
        // A rename changes only Nomi (Parlanti); the Segmento -> Voce assignment read again is the same.
        val r = prontoSu(registrata)
        val rilettaDopoRinomina = struttura(a to unaStruttura(1 to 1, 2 to 2), b to unaStruttura(1 to 3))

        assertFalse(r.superato(rilettaDopoRinomina))
    }

    @Test
    fun `INV-I11 una Parte senza Trascritto alla lettura non e registrata e il Riassunto nasce superato`() {
        val r = prontoSu(struttura(a to unaStruttura(1 to 1), b to null))

        assertEquals("A=1:1", r.struttura)
        assertTrue(r.superato(struttura(a to unaStruttura(1 to 1), b to null)))
    }

    @Test
    fun `AC-I17 un Incontro di una Parte ha chiave id= piu la vecchia codifica e un Trascritto invariato e uguale`() {
        val vecchia = unaStruttura(3 to 1, 1 to 1, 2 to 2)
        val migrata = "parte-1=1:1,2:2,3:1" // what 7.sqm writes: registrazione_id || '=' || the old struttura

        assertEquals(migrata, inUnaParte(vecchia).chiave)
        val contenuto = EsitoVerifica(Sommario(testo("x")), emptyList(), emptyList(), emptyList(), emptyList(), 0)
        val migrato = Riassunto(
            RiassuntoId("id-1"), IncontroId("id-2"), null,
            LunghezzaMassimaParole.di(LunghezzaMassimaParole.PREDEFINITA).atteso(), RICHIESTO_ALLE,
            StatoRiassunto.PRONTO, AVVIATO_ALLE, null, contenuto, migrata,
        )
        assertFalse(migrato.superato(inUnaParte(unaStruttura(1 to 1, 2 to 2, 3 to 1))), "Trascritto invariato")
        assertTrue(migrato.superato(inUnaParte(unaStruttura(1 to 1, 2 to 1, 3 to 1))), "Trascritto cambiato")
    }

    @Test
    fun `INV-I11 una Parte ripetuta e un errore del programmatore`() {
        assertFailsWith<IllegalArgumentException> { struttura(a to null, a to unaStruttura()) }
    }
}
