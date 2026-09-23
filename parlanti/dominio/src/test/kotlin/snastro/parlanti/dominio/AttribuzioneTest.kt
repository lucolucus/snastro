package snastro.parlanti.dominio

import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.atteso
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AttribuzioneTest {
    private val voce = VoceRef(RegistrazioneId("id-r"), VoceId(1))
    private val progetto = ProgettoId("id-p")
    private val marco = ParlanteId("id-1")
    private val luca = ParlanteId("id-2")

    private fun unaAttribuzione(parlanteId: ParlanteId = marco): Attribuzione =
        Attribuzione.conferma(voce, progetto, parlanteId).aggregato

    @Test
    fun `conferma crea l'Attribuzione della Voce e restituisce AttribuzioneConfermata senza precedente`() {
        val creato = Attribuzione.conferma(voce, progetto, marco)

        val a = creato.aggregato
        assertEquals(voce, a.voceRef)
        assertEquals(progetto, a.progettoId)
        assertEquals(marco, a.parlanteId)
        assertEquals(AttribuzioneConfermata(voce, marco, precedente = null), creato.evento)
    }

    @Test
    fun `AC-23 l'identita dell'Attribuzione e il VoceRef, che nessun cambio modifica`() {
        val a = unaAttribuzione()

        a.cambia(luca).atteso()

        assertEquals(voce, a.voceRef, "la chiave resta il VoceRef: un solo Parlante per Voce")
        assertEquals(luca, a.parlanteId)
    }

    @Test
    fun `AC-24 cambia verso un altro Parlante emette AttribuzioneConfermata con precedente`() {
        val a = unaAttribuzione(marco)

        val evento = a.cambia(luca).atteso()

        assertEquals(AttribuzioneConfermata(voce, luca, precedente = marco), evento)
        assertEquals(luca, a.parlanteId)
    }

    @Test
    fun `AC-24 cambia verso lo stesso Parlante e Ok senza evento e non cambia nulla`() {
        val a = unaAttribuzione(marco)

        val evento = a.cambia(marco).atteso()

        assertNull(evento)
        assertEquals(marco, a.parlanteId)
        assertEquals(voce, a.voceRef)
    }

    @Test
    fun `AC-24 dopo due cambi il precedente e l'ultimo Parlante attribuito`() {
        val a = unaAttribuzione(marco)
        a.cambia(luca).atteso()

        val evento = a.cambia(marco).atteso()

        assertEquals(AttribuzioneConfermata(voce, marco, precedente = luca), evento)
    }

    @Test
    fun `AC-269 trasferisci restituisce l Attribuzione con chiave a e stessi parlanteId e progettoId`() {
        val cambiata = unaAttribuzione()
        cambiata.cambia(luca).atteso()
        val sopravvissuta = VoceRef(voce.registrazioneId, VoceId(7))

        val trasferita = cambiata.trasferisci(sopravvissuta)

        assertEquals(sopravvissuta, trasferita.voceRef)
        assertEquals(luca, trasferita.parlanteId)
        assertEquals(progetto, trasferita.progettoId)
        assertEquals(voce, cambiata.voceRef, "l originale non cambia chiave")
    }

    @Test
    fun `AC-269 trasferisci non emette eventi e non consulta lo stato del Parlante`() {
        // The signature returns the aggregate itself (no Esito, no event) and takes no Parlante: the
        // tombstone of an eliminato Parlante is re-keyed exactly like any other Attribuzione.
        val tombstone = unaAttribuzione(parlanteId = ParlanteId("id-eliminato"))

        val trasferita: Attribuzione = tombstone.trasferisci(VoceRef(voce.registrazioneId, VoceId(2)))

        assertEquals(ParlanteId("id-eliminato"), trasferita.parlanteId)
    }

    @Test
    fun `AC-269 trasferisci verso una Voce di un altra Registrazione e rifiutato`() {
        val a = unaAttribuzione()

        assertFailsWith<IllegalArgumentException> { a.trasferisci(VoceRef(RegistrazioneId("id-altra"), VoceId(1))) }
    }
}
