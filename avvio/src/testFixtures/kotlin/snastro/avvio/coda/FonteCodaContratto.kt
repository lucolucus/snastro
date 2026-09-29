package snastro.avvio.coda

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Consumer-driven contract of [FonteCoda] (A122, ADR 0023 §1/§2): every REAL source (`fonteCodaRiassunto`,
 * `fonteCodaElaborazione`) must keep [FonteCoda.teste]'s "oldest eligible, excluding `esclusi`" promise —
 * agreeing with [FonteCoda.prossima] on which id that is, honouring `esclusi` when it grows, and refusing a
 * head past `limite` — the exact contract [CodaCondivisa] leans on for its total order (raccogliPicco) and for
 * [FonteCoda.tutti]'s bounded fallback ([enumeraViaTeste]) not to spin forever on a source that gets this wrong.
 *
 * [conDue] seeds (through the subclass's OWN context: real repository fake + real command service, never a
 * hand-rolled [FonteCoda]) at least two `in_attesa` items, the FIRST one strictly older than the second.
 */
internal abstract class FonteCodaContratto {
    protected abstract fun conDue(): FonteCoda

    @Test
    fun `teste onora esclusi - una volta esclusa, la testa non e piu offerta`() {
        val fonte = conDue()
        val prima = checkNotNull(fonte.teste(emptySet())) { "conDue() deve seminare almeno due elementi in_attesa" }
        val seconda = checkNotNull(fonte.teste(setOf(prima.id))) { "conDue() deve seminare almeno due elementi" }
        assertEquals(seconda.id, fonte.teste(setOf(prima.id))?.id, "esclusa la prima, la stessa seconda torna")
        assertNull(fonte.teste(setOf(prima.id, seconda.id)), "escluse entrambe, nessuna testa resta")
    }

    @Test
    fun `teste e prossima concordano sulla stessa testa`() {
        val fonte = conDue()
        val testa = checkNotNull(fonte.teste(emptySet())) { "conDue() deve seminare almeno un elemento in_attesa" }

        val risultato = fonte.prossima(emptySet(), null)

        assertEquals(
            RisultatoTentativo.Avviata(testa.id),
            risultato,
            "prossima deve rivendicare la STESSA testa che teste ha appena offerto (carry-over 2)",
        )
    }

    @Test
    fun `il limite e onorato - una testa oltre il limite non e rivendicata`() {
        val fonte = conDue()
        val testa = checkNotNull(fonte.teste(emptySet())) { "conDue() deve seminare almeno un elemento in_attesa" }

        val risultato = fonte.prossima(emptySet(), testa.istante.minusMillis(1))

        assertEquals(RisultatoTentativo.Nessuno, risultato, "un limite prima della testa la rifiuta, mai la rivendica")
    }
}
