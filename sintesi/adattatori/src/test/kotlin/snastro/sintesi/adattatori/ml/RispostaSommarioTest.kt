package snastro.sintesi.adattatori.ml

import org.junit.jupiter.api.Test
import snastro.sintesi.applicazione.porte.RispostaModello
import snastro.sintesi.dominio.TestoConVoci
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** AC-S154 / AC-S180 as of R1-8: the adapter's parsing of the Sommario-only answer on recorded answers, in the gate. */
class RispostaSommarioTest {
    @Test
    fun `AC-S154 una risposta registrata valida diventa RispostaModello col solo sommario e liste vuote`() {
        assertEquals(
            RispostaModello(
                sommario = "{V1} e {V2} scelgono il combattimento a turni.",
                decisioni = emptyList(),
                questioniAperte = emptyList(),
                azioni = emptyList(),
                puntiChiave = emptyList(),
            ),
            RispostaSommario.leggi(RISPOSTA_VALIDA, emptySet()),
        )
    }

    @Test
    fun `AC-S154 gli spazi attorno alla chiave sono accettati e gli escape JSON decodificati`() {
        val r = RispostaSommario.leggi(""" { "sommario" : "A \"turni\"\ncon \u00e8 e \\" } """, emptySet())

        assertEquals("A \"turni\"\ncon è e \\", assertNotNull(r).sommario)
    }

    @Test
    fun `AC-S154 una risposta che non e il solo sommario e rifiutata`() {
        listOf(
            "",
            "non json",
            "{}",
            """{"sommario":null}""",
            """{"sommario":3}""",
            """{"sommario":"x","decisioni":[]}""", // the lists are not asked for any more (R1-8)
            """{"riassunto":"x"}""",
            RISPOSTA_VALIDA.dropLast(1), // truncated
            "$RISPOSTA_VALIDA {}", // trailing content
        ).forEach { assertNull(RispostaSommario.leggi(it, emptySet()), it) }
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
    fun `il sommario della risposta e riscritto in forma canonica secondo la legenda`() {
        val grezza = RISPOSTA_VALIDA.replace("{V1} e {V2}", "V1 e [V2]").replace("combattimento", "Voce 1 {x}")

        val r = RispostaSommario.leggi(grezza, setOf(1, 2))

        assertEquals("{V1} e {V2} scelgono il {V1} {{x}} a turni.", assertNotNull(r).sommario)
    }
}
