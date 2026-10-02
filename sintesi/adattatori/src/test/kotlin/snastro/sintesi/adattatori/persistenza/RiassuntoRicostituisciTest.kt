package snastro.sintesi.adattatori.persistenza

import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RicostituzioneDaPersistenza
import snastro.kernel.SegmentoId
import snastro.kernel.SegmentoRef
import snastro.kernel.atteso
import snastro.sintesi.dominio.Azione
import snastro.sintesi.dominio.Decisione
import snastro.sintesi.dominio.LunghezzaMassimaParole
import snastro.sintesi.dominio.PuntoChiave
import snastro.sintesi.dominio.QuestioneAperta
import snastro.sintesi.dominio.Riassunto
import snastro.sintesi.dominio.RiassuntoId
import snastro.sintesi.dominio.Sommario
import snastro.sintesi.dominio.StatoRiassunto
import snastro.sintesi.dominio.TestoConVoci
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * [Riassunto.ricostituisci] is reserved to persistence adapters (CR-15, dev-architecture-app.md#aggregato):
 * its own guard, the elements -> content mapping and the defensive copy of the caller's lists
 * (dev-architecture-app.md#valori-id "state captive, collections as copies") can only be exercised from here.
 */
@OptIn(RicostituzioneDaPersistenza::class)
class RiassuntoRicostituisciTest {
    private val id = RiassuntoId("riassunto-1")
    private val incontroId = IncontroId("incontro-1")
    private val parole = LunghezzaMassimaParole.di(LunghezzaMassimaParole.PREDEFINITA).atteso()
    private val richiestoAlle: Instant = Instant.parse("2026-09-26T10:00:00Z")

    private fun fonte(segmento: Int) = SegmentoRef(RegistrazioneId("parte-1"), SegmentoId(segmento))

    private fun testo(s: String): TestoConVoci = checkNotNull(TestoConVoci.decodifica(s)) { s }

    @Suppress("LongParameterList") // one parameter per Riassunto.ricostituisci argument it forwards (see Riassunto.kt)
    private fun ricostituisci(
        sommario: Sommario? = null,
        decisioni: List<Decisione> = emptyList(),
        questioniAperte: List<QuestioneAperta> = emptyList(),
        azioni: List<Azione> = emptyList(),
        puntiChiave: List<PuntoChiave> = emptyList(),
        omessi: Int? = 0,
    ): Riassunto = Riassunto.ricostituisci(
        id, incontroId, argomento = null, lunghezzaMassima = parole, richiestoAlle = richiestoAlle,
        stato = StatoRiassunto.PRONTO, avviatoAlle = richiestoAlle, motivoFallimento = null,
        sommario = sommario, decisioni = decisioni, questioniAperte = questioniAperte,
        azioni = azioni, puntiChiave = puntiChiave, omessi = omessi, strutturaRegistrata = "1:1",
    )

    @Test
    fun `A27 contenuto senza omessi fallisce il require proprio di ricostituisci`() {
        assertFailsWith<IllegalArgumentException> {
            ricostituisci(decisioni = listOf(Decisione(testo("tiene"), setOf(fonte(1)))), omessi = null)
        }
    }

    @Test
    fun `A27 ricostituisci mappa ogni lista di elementi sul contenuto esposto dagli accessor`() {
        val r = ricostituisci(
            sommario = Sommario(testo("apre")),
            decisioni = listOf(Decisione(testo("d"), setOf(fonte(1)))),
            questioniAperte = listOf(QuestioneAperta(testo("q"), setOf(fonte(2)))),
            azioni = listOf(Azione(testo("a"), setOf(fonte(3)), responsabile = null)),
            puntiChiave = listOf(PuntoChiave(testo("p"), setOf(fonte(4)), parlante = null)),
            omessi = 2,
        )

        assertEquals(testo("apre"), r.sommario?.testo)
        assertEquals(listOf(Decisione(testo("d"), setOf(fonte(1)))), r.decisioni)
        assertEquals(listOf(QuestioneAperta(testo("q"), setOf(fonte(2)))), r.questioniAperte)
        assertEquals(listOf(Azione(testo("a"), setOf(fonte(3)), null)), r.azioni)
        assertEquals(listOf(PuntoChiave(testo("p"), setOf(fonte(4)), null)), r.puntiChiave)
        assertEquals(2, r.omessi)
    }

    @Test
    fun `A26 ricostituisci copia le liste del chiamante, mutarle dopo la chiamata non cambia lo stato`() {
        val decisioni = mutableListOf(Decisione(testo("tiene"), setOf(fonte(1))))

        val r = ricostituisci(decisioni = decisioni, omessi = 0)
        decisioni.add(Decisione(testo("aggiunta dopo ricostituisci"), setOf(fonte(2))))

        assertEquals(1, r.decisioni.size, "la lista del chiamante non deve restare aliasata dal Riassunto")
    }
}
