package snastro.sintesi.adattatori.ml

import org.junit.jupiter.api.Test
import snastro.sintesi.applicazione.porte.AzioneRisposta
import snastro.sintesi.applicazione.porte.ElementoRisposta
import snastro.sintesi.applicazione.porte.PuntoChiaveRisposta
import snastro.sintesi.applicazione.porte.RispostaModello
import snastro.sintesi.dominio.TestoConVoci
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** AC-S154 / AC-S180: the adapter's parsing of answer schema v1 on recorded answers, inside the gate. */
class RispostaV1Test {
    @Test
    fun `AC-S154 una risposta registrata valida diventa RispostaModello`() {
        assertEquals(
            RispostaModello(
                sommario = "{V1} e {V2} scelgono il combattimento a turni.",
                decisioni = listOf(ElementoRisposta("Combattimento a turni.", listOf(1))),
                questioniAperte = listOf(ElementoRisposta("Quanti nemici per stanza.", listOf(3))),
                azioni = listOf(AzioneRisposta("Preparare il prototipo entro venerdi.", listOf(2), responsabile = 2)),
                puntiChiave = listOf(PuntoChiaveRisposta("Il ritmo resta lento di proposito.", listOf(1, 3), null)),
            ),
            RispostaV1.leggi(RISPOSTA_VALIDA),
        )
    }

    @Test
    fun `AC-S154 liste vuote spazi e ordine delle chiavi diverso sono accettati`() {
        val r = RispostaV1.leggi(
            """ { "punti_chiave" : [ ], "azioni":[], "questioni_aperte":[],
                "decisioni":[{"fonti":[ 12 , 7 ],"testo":"x"}], "sommario":"" } """,
        )

        assertEquals(listOf(ElementoRisposta("x", listOf(12, 7))), assertNotNull(r).decisioni)
        assertEquals("", r.sommario)
    }

    @Test
    fun `AC-S154 gli escape JSON sono decodificati`() {
        val r = RispostaV1.leggi(RISPOSTA_VALIDA.replace("Combattimento a turni.", """A \"turni\"\ncon \u00e8 e \\"""))

        assertEquals("A \"turni\"\ncon è e \\", assertNotNull(r).decisioni.single().testo)
    }

    @Test
    fun `AC-S154 una risposta che non rispetta lo schema v1 e rifiutata`() {
        listOf(
            "",
            "non json",
            """{"sommario":"x"}""", // keys missing
            RISPOSTA_VALIDA.replace("\"questioni_aperte\"", "\"questioni\""), // unknown key, one missing
            RISPOSTA_VALIDA.dropLast(1), // truncated
            "$RISPOSTA_VALIDA {}", // trailing content
            RISPOSTA_VALIDA.replace("\"fonti\":[1]", "\"fonti\":[\"s1\"]"), // fonti not integers
            RISPOSTA_VALIDA.replace("\"fonti\":[1]", "\"fonti\":[1.5]"),
            RISPOSTA_VALIDA.replace("\"fonti\":[1]", "\"fonti\":[99999999999]"), // outside Int
            RISPOSTA_VALIDA.replace("\"responsabile\":2", "\"responsabile\":\"Anna\""),
            RISPOSTA_VALIDA.replace("\"parlante\":null", "\"parlante\":true"),
            RISPOSTA_VALIDA.replace("\"testo\":\"Combattimento a turni.\",", ""), // element without testo
            RISPOSTA_VALIDA.replace("\"{V1} e {V2} scelgono il combattimento a turni.\"", "3"), // sommario not a string
            RISPOSTA_VALIDA.replace("\"decisioni\":[", "\"decisioni\":{\"a\":[")
                .replace("],\"questioni_aperte", "]},\"questioni_aperte"), // a list that is an object
        ).forEach { assertNull(RispostaV1.leggi(it), it) }
    }

    @Test
    fun `AC-S154 sommario null e accettato`() {
        val r = RispostaV1.leggi(RISPOSTA_VALIDA.replace("\"{V1} e {V2} scelgono il combattimento a turni.\"", "null"))

        assertNull(assertNotNull(r).sommario)
    }

    // --- {V<n>}: the port's canonical form, whatever the model wrote (ADR 0021 §4) ---------------------------

    @Test
    fun `ogni parlante e riscritto nella forma canonica V tra graffe`() {
        mapOf(
            "{V2} prepara" to "{V2} prepara",
            "V2 prepara" to "{V2} prepara",
            "come detto da [V1]" to "come detto da {V1}",
            "Voce 3 propone" to "{V3} propone",
            "{ V4 } e V12" to "{V4} e {V12}",
            "{V02}" to "{V2}",
        ).forEach { (grezzo, atteso) -> assertEquals(atteso, VociNelTesto.canonico(grezzo), grezzo) }
    }

    @Test
    fun `le graffe letterali sono raddoppiate e le parole simili restano testo`() {
        mapOf(
            "un {blocco} e }{" to "un {{blocco}} e }}{{",
            "Versione 2 del piano" to "Versione 2 del piano",
            "la voce 2 del bilancio" to "la voce 2 del bilancio",
            "V0 e VX" to "V0 e VX",
            "COVID19 e MV2" to "COVID19 e MV2",
        ).forEach { (grezzo, atteso) -> assertEquals(atteso, VociNelTesto.canonico(grezzo), grezzo) }
    }

    @Test
    fun `il testo canonico si decodifica sempre con il codec del Riassunto`() {
        listOf("{V1} e }{ e {x} e V0 e V1234567890", "{", "}", "{{V1}}", "[V3]{V4}").forEach {
            assertNotNull(TestoConVoci.decodifica(VociNelTesto.canonico(it)), it)
        }
    }

    @Test
    fun `i testi della risposta sono riscritti in forma canonica`() {
        val grezza = RISPOSTA_VALIDA.replace("{V1} e {V2}", "V1 e [V2]").replace("Combattimento", "Voce 1 {x}")

        val r = RispostaV1.leggi(grezza)

        assertEquals("{V1} e {V2} scelgono il combattimento a turni.", assertNotNull(r).sommario)
        assertEquals("{V1} {{x}} a turni.", r.decisioni.single().testo)
    }
}
