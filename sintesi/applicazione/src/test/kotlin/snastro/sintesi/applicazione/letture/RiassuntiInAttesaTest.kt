package snastro.sintesi.applicazione.letture

import snastro.kernel.RegistrazioneId
import snastro.kernel.atteso
import snastro.kernel.unIncontroDi
import snastro.sintesi.applicazione.porte.RiassuntoRepositoryFinta
import snastro.sintesi.applicazione.porte.conAvvio
import snastro.sintesi.applicazione.porte.conCompletamento
import snastro.sintesi.applicazione.porte.conFallimento
import snastro.sintesi.applicazione.porte.unRiassunto
import snastro.sintesi.applicazione.porte.unaStruttura
import snastro.sintesi.dominio.BozzaRiassunto
import snastro.sintesi.dominio.RiassuntoId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * AC-S110 (boundary `riassunti-in-coda`, consumer-driven: `avvio-sintesi`'s Riassunto source, ADR 0021 §3,
 * ADR 0023 §1): [RiassuntiInAttesa.elenco] carries only what the shared queue needs — `in_attesa` rows, FIFO,
 * primitive ids.
 */
class RiassuntiInAttesaTest {
    private val riassunti = RiassuntoRepositoryFinta()
    private val coda = RiassuntiInAttesa(riassunti)

    @Test
    fun `AC-I30 AC-S110 elenca solo le in_attesa, FIFO, con riassuntoId come stringa e l incontroId`() {
        val prima = RegistrazioneId("registrazione-1")
        val seconda = RegistrazioneId("registrazione-2")
        // salvati fuori ordine: l'elenco deve comunque uscire FIFO
        riassunti.salva(unRiassunto("riassunto-2", seconda, richiestoAlle = t(1))).atteso()
        riassunti.salva(unRiassunto("riassunto-1", prima, richiestoAlle = t(0))).atteso()
        riassunti.salva(unRiassunto("riassunto-corso", RegistrazioneId("registrazione-corso")).conAvvio()).atteso()
        riassunti.salva(
            unRiassunto("riassunto-pronto", RegistrazioneId("registrazione-pronto")).conAvvio()
                .conCompletamento(BOZZA, STRUTTURA),
        ).atteso()
        riassunti.salva(
            unRiassunto("riassunto-fallito", RegistrazioneId("registrazione-fallita")).conAvvio().conFallimento(),
        ).atteso()

        val elenco = coda.elenco()

        assertEquals(
            listOf(
                RiassuntoInCoda("riassunto-1", unIncontroDi(prima), t(0)),
                RiassuntoInCoda("riassunto-2", unIncontroDi(seconda), t(1)),
            ),
            elenco,
            "solo le in_attesa, in ordine FIFO — in_corso/pronto/fallito non appaiono",
        )
    }

    @Test
    fun `AC-S110 a parita di richiestoAlle il pareggio e per id, come inAttesa del repository`() {
        riassunti.salva(unRiassunto("riassunto-b", RegistrazioneId("registrazione-b"), richiestoAlle = t(0))).atteso()
        riassunti.salva(unRiassunto("riassunto-a", RegistrazioneId("registrazione-a"), richiestoAlle = t(0))).atteso()

        assertEquals(listOf("riassunto-a", "riassunto-b"), coda.elenco().map { it.riassuntoId })
    }

    @Test
    fun `AC-S110 una rimozione fa sparire la riga dall elenco`() {
        val registrazione = RegistrazioneId("registrazione-1")
        riassunti.salva(unRiassunto("riassunto-1", registrazione, richiestoAlle = t(0))).atteso()
        assertEquals(1, coda.elenco().size)

        riassunti.rimuovi(RiassuntoId("riassunto-1")).atteso()

        assertEquals(emptyList(), coda.elenco())
    }

    private companion object {
        fun t(secondi: Long): Instant = Instant.parse("2026-09-23T10:00:00Z").plusSeconds(secondi)

        val STRUTTURA = unaStruttura(1 to 1)
        val BOZZA = BozzaRiassunto("Solo un sommario.", emptyList(), emptyList(), emptyList(), emptyList())
    }
}
