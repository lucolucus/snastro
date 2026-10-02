package snastro.sintesi.applicazione.politiche

import snastro.kernel.DispatcherEventiFinta
import snastro.kernel.Esito
import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.kernel.atteso
import snastro.kernel.unIncontroDi
import snastro.sintesi.applicazione.eventi.RiassuntoEliminato
import snastro.sintesi.applicazione.porte.RiassuntoRepository
import snastro.sintesi.applicazione.porte.RiassuntoRepositoryFinta
import snastro.sintesi.applicazione.porte.conAvvio
import snastro.sintesi.applicazione.porte.conCompletamento
import snastro.sintesi.applicazione.porte.conFallimento
import snastro.sintesi.applicazione.porte.statoOsservabile
import snastro.sintesi.applicazione.porte.unRiassunto
import snastro.sintesi.applicazione.porte.unaStruttura
import snastro.sintesi.dominio.BozzaElemento
import snastro.sintesi.dominio.BozzaRiassunto
import snastro.sintesi.dominio.ErroreSintesi
import snastro.sintesi.dominio.Riassunto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [ApplicaEliminazioneRegistrazioneSintesiPolitica] against [RiassuntoRepositoryFinta] (D1). AC-S98..S101,
 * [INV-S8]. Every call goes through [eventi]'s own `UnitaDiLavoro`, exactly as the real subscriber runs it
 * inside `EliminaRegistrazione`'s deleting transaction (ADR 0012): `DispatcherEventi.pubblica` requires one.
 */
class ApplicaEliminazioneRegistrazioneSintesiPoliticaTest {
    private val eventi = DispatcherEventiFinta()
    private val riassunti = RiassuntoRepositoryFinta()
    private val politica = ApplicaEliminazioneRegistrazioneSintesiPolitica(riassunti, eventi)

    @Test
    fun `INV-S8 AC-S98 ogni Riassunto di r sparisce con elementi e Fonti, un altra Registrazione resta intatta`() {
        val scenari = listOf<Pair<String, (RegistrazioneId) -> List<Riassunto>>>(
            "pronto + in_attesa" to { r ->
                listOf(unRiassuntoPronto("pronto-$r", r), unRiassunto("attesa-$r", r))
            },
            "pronto + in_corso" to { r ->
                listOf(unRiassuntoPronto("pronto-$r", r), unRiassunto("corso-$r", r).conAvvio())
            },
            "fallito" to { r -> listOf(unRiassunto("fallito-$r", r).conAvvio().conFallimento()) },
        )
        scenari.forEachIndexed { i, (nome, costruisci) ->
            val r = RegistrazioneId("reg-$i")
            val altraRegistrazione = RegistrazioneId("altra-$i")
            val righe = costruisci(r).onEach { riassunti.salva(it).atteso() }
            riassunti.salva(unRiassuntoPronto("altra-pronto-$i", altraRegistrazione)).atteso()
            val altraPrima = riassunti.trova(unIncontroDi(altraRegistrazione)).map { it.statoOsservabile() }
            val eventiPrima = eventi.pubblicati.size

            applica(r).atteso()

            assertEquals(emptyList(), riassunti.trova(unIncontroDi(r)), nome)
            assertEquals(
                altraPrima,
                riassunti.trova(unIncontroDi(altraRegistrazione)).map { it.statoOsservabile() },
                "$nome: un'altra Registrazione resta byte-identica",
            )
            // AC-S99: un solo RiassuntoEliminato anche quando lo scenario rimuove piu' righe (qui: ${righe.size}).
            assertTrue(righe.size >= 1, nome)
            assertEquals(eventiPrima + 1, eventi.pubblicati.size, "$nome: un solo evento anche con piu' righe")
            assertEquals(RiassuntoEliminato(unIncontroDi(r)), eventi.pubblicati.last(), nome)
        }
    }

    @Test
    fun `AC-S99 RiassuntoEliminato e pubblicato solo se qualcosa e stato tolto`() {
        riassunti.salva(unRiassunto("in-attesa", REGISTRAZIONE)).atteso()

        applica(REGISTRAZIONE).atteso()

        assertEquals(listOf(RiassuntoEliminato(unIncontroDi(REGISTRAZIONE))), eventi.pubblicati)
    }

    @Test
    fun `AC-S99 senza Riassunto e Ok e nessun evento e pubblicato`() {
        val esito = applica(REGISTRAZIONE)

        assertEquals(Esito.Ok(Unit), esito)
        assertEquals(emptyList(), eventi.pubblicati)
    }

    @Test
    fun `AC-S100 non veta mai, un Riassunto in corso da solo e Ok e viene tolto`() {
        riassunti.salva(unRiassunto("in-corso", REGISTRAZIONE).conAvvio()).atteso()

        val esito = applica(REGISTRAZIONE)

        assertEquals(Esito.Ok(Unit), esito)
        assertEquals(emptyList(), riassunti.trova(unIncontroDi(REGISTRAZIONE)))
        assertEquals(listOf(RiassuntoEliminato(unIncontroDi(REGISTRAZIONE))), eventi.pubblicati)
    }

    @Test
    fun `INV-I12b incontroCessato false non tocca nulla in nessuno stato e non pubblica`() {
        val scenari = listOf<Pair<String, (RegistrazioneId) -> List<Riassunto>>>(
            "pronto" to { r -> listOf(unRiassuntoPronto("pronto-$r", r)) },
            "pronto + in_attesa" to { r -> listOf(unRiassuntoPronto("pronto-$r", r), unRiassunto("attesa-$r", r)) },
            "in_attesa" to { r -> listOf(unRiassunto("attesa-$r", r)) },
            "fallito" to { r -> listOf(unRiassunto("fallito-$r", r).conAvvio().conFallimento()) },
        )
        scenari.forEachIndexed { i, (nome, costruisci) ->
            val r = RegistrazioneId("reg-parte-$i")
            costruisci(r).forEach { riassunti.salva(it).atteso() }
            val prima = riassunti.trova(unIncontroDi(r)).map { it.statoOsservabile() }

            assertEquals(Esito.Ok(Unit), applica(r, cessato = false), nome)

            assertEquals(prima, riassunti.trova(unIncontroDi(r)).map { it.statoOsservabile() }, nome)
            assertEquals(emptyList(), eventi.pubblicati, nome)
        }
    }

    @Test
    fun `INV-I12b incontroCessato false lascia anche un Riassunto in corso, il suo completamento resta valido`() {
        val r = RegistrazioneId("reg-corso")
        val inCorso = unRiassunto("in-corso", r).conAvvio()
        riassunti.salva(inCorso).atteso()

        assertEquals(Esito.Ok(Unit), applica(r, cessato = false))
        assertEquals(emptyList(), eventi.pubblicati)

        // the run then completes: its compare-and-set still finds the in_corso row and writes the pronto
        inCorso.conCompletamento(BOZZA, unaStruttura(1 to 1))
        val scritto = eventi.unitaDiLavoro.inTransazione { riassunti.concludi(inCorso) }.atteso()

        assertTrue(scritto)
        assertEquals(listOf(true), riassunti.trova(unIncontroDi(r)).map { it.pronto })
    }

    @Test
    fun `AC-S101 un Errore del repository e restituito invariato e nessun evento e pubblicato`() {
        val guasto = Esito.Errore(ErroreSintesi.RiassuntoNonTrovato("x"))
        val politicaGuasta = ApplicaEliminazioneRegistrazioneSintesiPolitica(
            RepositoryConGuasto(riassunti, guasto),
            eventi,
        )

        val esito = eventi.unitaDiLavoro.inTransazione { politicaGuasta.applica(unIncontroDi(REGISTRAZIONE), true) }

        assertEquals(guasto, esito)
        assertEquals(emptyList(), eventi.pubblicati)
    }

    private fun applica(
        r: RegistrazioneId,
        cessato: Boolean = true,
    ): Esito<Unit> = eventi.unitaDiLavoro.inTransazione { politica.applica(unIncontroDi(r), cessato) }

    /** A `pronto` Riassunto with a real Decisione + Fonte, to prove "elements and Fonti" are gone, not just the row. */
    private fun unRiassuntoPronto(id: String, r: RegistrazioneId): Riassunto =
        unRiassunto(id, r).conAvvio().conCompletamento(BOZZA, unaStruttura(1 to 1))

    /** [RiassuntoRepository] whose [rimuoviDiRegistrazione] always answers with [guasto] (AC-S101). */
    private class RepositoryConGuasto(
        private val delegata: RiassuntoRepository,
        private val guasto: Esito.Errore,
    ) : RiassuntoRepository by delegata {
        override fun rimuoviDiIncontro(incontroId: IncontroId): Esito<Int> = guasto
    }

    private companion object {
        val REGISTRAZIONE = RegistrazioneId("reg-1")
        val BOZZA = BozzaRiassunto(
            sommario = null,
            decisioni = listOf(BozzaElemento("una decisione", listOf(1), null)),
            questioniAperte = emptyList(),
            azioni = emptyList(),
            puntiChiave = emptyList(),
        )
    }
}
