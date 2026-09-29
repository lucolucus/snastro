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
            RispostaV1.leggi(RISPOSTA_VALIDA, emptySet()),
        )
    }

    @Test
    fun `AC-S154 liste vuote spazi e ordine delle chiavi diverso sono accettati`() {
        val r = RispostaV1.leggi(
            """ { "punti_chiave" : [ ], "azioni":[], "questioni_aperte":[],
                "decisioni":[{"fonti":[ 12 , 7 ],"testo":"x"}], "sommario":"" } """,
            emptySet(),
        )

        assertEquals(listOf(ElementoRisposta("x", listOf(12, 7))), assertNotNull(r).decisioni)
        assertEquals("", r.sommario)
    }

    @Test
    fun `una voce ripetuta nella stessa lista e scartata, anche con maiuscole spazi o punto finale diversi`() {
        val r = RispostaV1.leggi(
            """{"sommario":null,"decisioni":[],"questioni_aperte":[],"azioni":[
                {"testo":"Preparare le carte.","fonti":[1],"responsabile":1},
                {"testo":"Verificare la mappa","fonti":[2],"responsabile":2},
                {"testo":"preparare  le carte","fonti":[3],"responsabile":1}],
                "punti_chiave":[{"testo":"Preparare le carte.","fonti":[1],"parlante":null}]}""",
            emptySet(),
        )

        assertEquals(
            listOf(
                AzioneRisposta("Preparare le carte.", listOf(1), 1),
                AzioneRisposta("Verificare la mappa", listOf(2), 2),
            ),
            assertNotNull(r).azioni,
        )
        assertEquals(1, r.puntiChiave.size) // only within the same list
    }

    @Test
    fun `AC-S154 gli escape JSON sono decodificati`() {
        val r = RispostaV1.leggi(
            RISPOSTA_VALIDA.replace("Combattimento a turni.", """A \"turni\"\ncon \u00e8 e \\"""),
            emptySet(),
        )

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
        ).forEach { assertNull(RispostaV1.leggi(it, emptySet()), it) }
    }

    @Test
    fun `AC-S154 sommario null e accettato`() {
        val r = RispostaV1.leggi(
            RISPOSTA_VALIDA.replace("\"{V1} e {V2} scelgono il combattimento a turni.\"", "null"),
            emptySet(),
        )

        assertNull(assertNotNull(r).sommario)
    }

    // --- {V<n>}: the port's canonical form, whatever the model wrote (ADR 0021 §4) ---------------------------

    @Test
    fun `ogni parlante e riscritto nella forma canonica V tra graffe quando il numero e nella legenda`() {
        val legenda = setOf(1, 2, 3, 4, 12)
        mapOf(
            "{V2} prepara" to "{V2} prepara",
            "V2 prepara" to "{V2} prepara",
            "come detto da [V1]" to "come detto da {V1}",
            "Voce 3 propone" to "{V3} propone",
            "{ V4 } e V12" to "{V4} e {V12}",
            "{V02}" to "{V2}",
        ).forEach { (grezzo, atteso) -> assertEquals(atteso, VociNelTesto.canonico(grezzo, legenda), grezzo) }
    }

    // --- a bare V<n> / Voce <n> outside the legend is ordinary text, never a speaker reference ------------------

    @Test
    fun `un V o Voce nudo il cui numero non e nella legenda resta testo, non diventa un riferimento`() {
        val legenda = setOf(1, 2)
        mapOf(
            "il motore V8 si e rotto" to "il motore V8 si e rotto",
            "la Voce 9 del contratto" to "la Voce 9 del contratto",
        ).forEach { (grezzo, atteso) -> assertEquals(atteso, VociNelTesto.canonico(grezzo, legenda), grezzo) }
    }

    @Test
    fun `le forme esplicite V tra graffe e tra quadre contano sempre, anche fuori legenda`() {
        mapOf(
            "{V8} propone" to "{V8} propone",
            "[V9]" to "{V9}",
        ).forEach { (grezzo, atteso) -> assertEquals(atteso, VociNelTesto.canonico(grezzo, emptySet()), grezzo) }
    }

    @Test
    fun `la legenda di IngressoRiassunto e letta dalle righe V n uguale nome`() {
        val ingresso = "[s1 V1] Decidiamo i turni.\n[s2 V2] Preparo il prototipo.\nV1 = Anna\nV2 = Voce 2"

        assertEquals(setOf(1, 2), VociNelTesto.legenda(ingresso))
    }

    @Test
    fun `le graffe letterali sono raddoppiate e le parole simili restano testo`() {
        mapOf(
            "un {blocco} e }{" to "un {{blocco}} e }}{{",
            "Versione 2 del piano" to "Versione 2 del piano",
            "la voce 2 del bilancio" to "la voce 2 del bilancio",
            "V0 e VX" to "V0 e VX",
            "COVID19 e MV2" to "COVID19 e MV2",
        ).forEach { (grezzo, atteso) -> assertEquals(atteso, VociNelTesto.canonico(grezzo, emptySet()), grezzo) }
    }

    @Test
    fun `il testo canonico si decodifica sempre con il codec del Riassunto`() {
        listOf("{V1} e }{ e {x} e V0 e V1234567890", "{", "}", "{{V1}}", "[V3]{V4}").forEach {
            assertNotNull(TestoConVoci.decodifica(VociNelTesto.canonico(it, emptySet())), it)
        }
    }

    @Test
    fun `i testi della risposta sono riscritti in forma canonica secondo la legenda`() {
        val grezza = RISPOSTA_VALIDA.replace("{V1} e {V2}", "V1 e [V2]").replace("Combattimento", "Voce 1 {x}")

        val r = RispostaV1.leggi(grezza, setOf(1, 2))

        assertEquals("{V1} e {V2} scelgono il combattimento a turni.", assertNotNull(r).sommario)
        assertEquals("{V1} {{x}} a turni.", r.decisioni.single().testo)
    }
}
