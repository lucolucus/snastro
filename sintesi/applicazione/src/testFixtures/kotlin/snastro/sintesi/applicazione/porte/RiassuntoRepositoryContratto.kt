package snastro.sintesi.applicazione.porte

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.sintesi.dominio.BozzaElemento
import snastro.sintesi.dominio.BozzaRiassunto
import snastro.sintesi.dominio.ErroreSintesi.RiassuntoGiaAperto
import snastro.sintesi.dominio.MotivoFallimento
import snastro.sintesi.dominio.Riassunto
import snastro.sintesi.dominio.RiassuntoId
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Consumer-driven contract of [RiassuntoRepository] (boundary `repo-sintesi`, ADR 0022): the round-trip of every
 * state (AC-S65), the partial unique indexes → `Esito` (AC-S66), the queue reads (AC-S67), the completion
 * compare-and-set (AC-S68) and the removals with their children (AC-S69). One subclass per implementation (the
 * Finta here, `RiassuntoRepositorySql` in `:sintesi:adattatori`). Stored and expected Riassunti are compared on
 * [statoOsservabile].
 */
public abstract class RiassuntoRepositoryContratto {
    /** A fresh, empty repository. */
    protected abstract fun repository(): RiassuntoRepository

    /** Hook for real stores: create the parent rows of every id the contract uses ([PREDISPOSIZIONE]). */
    protected open fun predisponi(predisposizione: PredisposizioneSintesi) {}

    /**
     * A64: the child rows (elementi + Fonti) STILL physically stored for [id] — `null` where there is no
     * separate physical child storage to check (the Finta: an in-memory map has no orphan rows by
     * construction). A re-save through [salva] REPLACES children, which would mask an orphan left behind
     * by [RiassuntoRepository.rimuovi]/[RiassuntoRepository.rimuoviDiRegistrazione] — checking straight
     * after removal, before any re-save, is what actually discriminates.
     */
    protected open fun figliOrfaniDi(id: RiassuntoId): Int? = null

    private lateinit var repo: RiassuntoRepository

    @BeforeEach
    public fun preparaRepository() {
        repo = repository()
        predisponi(PREDISPOSIZIONE)
    }

    @Test
    public fun `AC-S65 un pronto con ogni tipo di elemento Fonti voci e graffe letterali torna identico`() {
        val conArgomento = unPronto("riassunto-1", argomento = "il combattimento", parole = 1500)
        val senzaArgomento = unPronto("riassunto-2", registrazioneId = ALTRA_REGISTRAZIONE, argomento = null)
        repo.salva(conArgomento).atteso()
        repo.salva(senzaArgomento).atteso()

        val letto = checkNotNull(repo.trova(conArgomento.id))
        assertEquals(conArgomento.statoOsservabile(), letto.statoOsservabile())
        assertEquals(senzaArgomento.statoOsservabile(), checkNotNull(repo.trova(senzaArgomento.id)).statoOsservabile())
        // The fixture really covers what AC-S65 lists, so a lossy store cannot pass by accident.
        val elementi = listOf(letto.decisioni, letto.questioniAperte, letto.azioni, letto.puntiChiave)
        assertEquals(listOf(1, 1, 1, 1), elementi.map { it.size })
        assertEquals(2, letto.omessi)
        assertEquals("1:1,2:2,3:1", letto.struttura)
        assertEquals(setOf(1, 3), letto.puntiChiave.single().fonti.map { it.numero }.toSet())
        assertEquals(BOZZA.sommario, letto.sommario?.testo?.codifica())
        assertEquals(BOZZA.azioni.first().testo, letto.azioni.single().testo.codifica())
        assertEquals(1500, letto.lunghezzaMassima.valore)
        assertEquals("il combattimento", letto.argomento?.valore)
        assertNull(repo.trova(senzaArgomento.id)?.argomento)
    }

    @Test
    public fun `AC-S65 in_attesa in_corso e fallito con ogni motivo tornano identici`() {
        val attesa = unRiassunto("riassunto-a", REGISTRAZIONI[0], argomento = "tema", parole = 300)
        val corso = unRiassunto("riassunto-c", REGISTRAZIONI[1], parole = 2500).conAvvio()
        val falliti = MotivoFallimento.entries.mapIndexed { i, motivo ->
            unRiassunto("riassunto-f$i", REGISTRAZIONI[2 + i], argomento = "tema $i").conAvvio().conFallimento(motivo)
        }
        val tutti = listOf(attesa, corso) + falliti
        tutti.forEach { repo.salva(it).atteso() }

        tutti.forEach { assertEquals(it.statoOsservabile(), checkNotNull(repo.trova(it.id)).statoOsservabile()) }
    }

    @Test
    public fun `AC-S65 salva e un upsert che sostituisce i figli senza duplicarli`() {
        val r = unRiassunto("riassunto-1", REGISTRAZIONE)
        repo.salva(r).atteso()
        r.conAvvio()
        repo.salva(r).atteso()
        assertEquals(listOf(r.statoOsservabile()), repo.diRegistrazione(REGISTRAZIONE).map { it.statoOsservabile() })

        r.conCompletamento(BOZZA, STRUTTURA)
        repo.salva(r).atteso()
        repo.salva(r).atteso()

        assertEquals(listOf(r.statoOsservabile()), repo.diRegistrazione(REGISTRAZIONE).map { it.statoOsservabile() })
    }

    @Test
    public fun `AC-S65 nessun alias tra il Riassunto del chiamante e quello salvato`() {
        val r = unRiassunto("riassunto-1", REGISTRAZIONE)
        repo.salva(r).atteso()

        r.conAvvio()
        checkNotNull(repo.trova(r.id)).conAvvio()

        assertTrue(checkNotNull(repo.trova(r.id)).inAttesa)
    }

    @Test
    public fun `AC-S66 un secondo in_attesa in_corso o fallito della stessa Registrazione e RiassuntoGiaAperto`() {
        // (existing, second) pairs, one Registrazione each: every non-pronto state collides with every other.
        val casi = listOf(
            unRiassunto("esistente-0", REGISTRAZIONI[0]) to unRiassunto("secondo-0", REGISTRAZIONI[0]),
            unRiassunto("esistente-1", REGISTRAZIONI[1]) to unRiassunto("secondo-1", REGISTRAZIONI[1]).conAvvio(),
            unRiassunto("esistente-2", REGISTRAZIONI[2]).conAvvio() to
                unRiassunto("secondo-2", REGISTRAZIONI[2]).conAvvio().conFallimento(),
            unRiassunto("esistente-3", REGISTRAZIONI[3]).conAvvio().conFallimento() to
                unRiassunto("secondo-3", REGISTRAZIONI[3]),
        )
        casi.forEach { (esistente, _) -> repo.salva(esistente).atteso() }

        casi.forEach { (esistente, secondo) ->
            val errore = repo.salva(secondo).erroreAtteso<RiassuntoGiaAperto>()
            assertEquals(RiassuntoGiaAperto(secondo.registrazioneId), errore)
            assertEquals(
                listOf(esistente.statoOsservabile()),
                repo.diRegistrazione(secondo.registrazioneId).map { it.statoOsservabile() },
                "nulla scritto per ${secondo.id}",
            )
            assertNull(repo.trova(secondo.id))
        }
    }

    @Test
    public fun `AC-S66 un secondo pronto della stessa Registrazione e RiassuntoGiaAperto e nulla e scritto`() {
        val primo = unPronto("riassunto-1")
        repo.salva(primo).atteso()

        val errore = repo.salva(unPronto("riassunto-2")).erroreAtteso<RiassuntoGiaAperto>()

        assertEquals(RiassuntoGiaAperto(REGISTRAZIONE), errore)

        assertEquals(listOf(primo.statoOsservabile()), repo.diRegistrazione(REGISTRAZIONE).map { it.statoOsservabile() })
        assertNull(repo.trova(RiassuntoId("riassunto-2")))
    }

    @Test
    public fun `AC-S66 un pronto non esclude un aperto ne un fallito della stessa Registrazione`() {
        repo.salva(unPronto("riassunto-1")).atteso()
        repo.salva(unRiassunto("riassunto-2", REGISTRAZIONE)).atteso()
        repo.salva(unPronto("riassunto-3", registrazioneId = ALTRA_REGISTRAZIONE)).atteso()
        repo.salva(unRiassunto("riassunto-4", ALTRA_REGISTRAZIONE).conAvvio().conFallimento()).atteso()

        assertEquals(setOf("riassunto-1", "riassunto-2"), idDi(repo.diRegistrazione(REGISTRAZIONE)).toSet())
        assertEquals(setOf("riassunto-3", "riassunto-4"), idDi(repo.diRegistrazione(ALTRA_REGISTRAZIONE)).toSet())
    }

    @Test
    public fun `AC-S67 inAttesa e FIFO per richiestoAlle e a parita per id e inCorso elenca solo gli in_corso`() {
        repo.salva(unRiassunto("riassunto-c", REGISTRAZIONI[0], richiestoAlle = DOPO)).atteso()
        repo.salva(unRiassunto("riassunto-b", REGISTRAZIONI[1], richiestoAlle = PRIMA)).atteso()
        repo.salva(unRiassunto("riassunto-x", REGISTRAZIONI[2], richiestoAlle = MOLTO_PRIMA).conAvvio()).atteso()
        repo.salva(unRiassunto("riassunto-a", REGISTRAZIONI[3], richiestoAlle = PRIMA)).atteso()
        repo.salva(unRiassunto("riassunto-y", REGISTRAZIONI[4], richiestoAlle = MOLTO_PRIMA).conAvvio().conFallimento())
            .atteso()
        repo.salva(unPronto("riassunto-z", registrazioneId = REGISTRAZIONI[5], richiestoAlle = MOLTO_PRIMA)).atteso()

        assertEquals(listOf("riassunto-a", "riassunto-b", "riassunto-c"), idDi(repo.inAttesa()))
        assertEquals(listOf("riassunto-x"), idDi(repo.inCorso()))
    }

    @Test
    public fun `AC-S67 diRegistrazione elenca ogni stato della Registrazione e solo quella`() {
        repo.salva(unPronto("riassunto-1")).atteso()
        repo.salva(unRiassunto("riassunto-2", REGISTRAZIONE).conAvvio()).atteso()
        repo.salva(unRiassunto("riassunto-3", ALTRA_REGISTRAZIONE)).atteso()

        assertEquals(setOf("riassunto-1", "riassunto-2"), idDi(repo.diRegistrazione(REGISTRAZIONE)).toSet())
        assertEquals(listOf("riassunto-3"), idDi(repo.diRegistrazione(ALTRA_REGISTRAZIONE)))
        assertEquals(emptyList(), repo.diRegistrazione(TERZA_REGISTRAZIONE))
    }

    @Test
    public fun `AC-S67 un repository vuoto restituisce liste vuote e trova null`() {
        assertEquals(emptyList(), repo.inAttesa())
        assertEquals(emptyList(), repo.inCorso())
        assertEquals(emptyList(), repo.diRegistrazione(REGISTRAZIONE))
        assertNull(repo.trova(RiassuntoId("riassunto-1")))
    }

    @Test
    public fun `AC-S68 concludi pronto di una riga assente e Ok false e il pronto precedente resta`() {
        val precedente = unPronto("precedente")
        repo.salva(precedente).atteso()
        val r = unRiassunto("riassunto-1", REGISTRAZIONE).conAvvio().conCompletamento(BOZZA, STRUTTURA)

        assertEquals(false, repo.concludi(r).atteso())

        assertNull(repo.trova(r.id))
        // D-0003: the previous pronto goes only after the in_corso check succeeds.
        assertEquals(listOf(precedente.statoOsservabile()), repo.diRegistrazione(REGISTRAZIONE).map { it.statoOsservabile() })
    }

    @Test
    public fun `AC-S68 concludi di una riga in_attesa pronto o fallito e Ok false e nulla cambia`() {
        val salvate = listOf(
            unRiassunto("riassunto-0", REGISTRAZIONI[0]),
            unPronto("riassunto-1", registrazioneId = REGISTRAZIONI[1]),
            unRiassunto("riassunto-2", REGISTRAZIONI[2]).conAvvio().conFallimento(MotivoFallimento.INTERROTTO),
        )
        // A previous pronto next to the in_attesa and the fallito row: a losing concludi(pronto) must not remove it.
        val precedenti = listOf(
            unPronto("precedente-0", registrazioneId = REGISTRAZIONI[0]),
            unPronto("precedente-2", registrazioneId = REGISTRAZIONI[2]),
        )
        (salvate + precedenti).forEach { repo.salva(it).atteso() }

        salvate.forEach { salvata ->
            val prima = repo.diRegistrazione(salvata.registrazioneId).map { it.statoOsservabile() }.toSet()
            // The caller's copy of the same row, concluded the other way (pronto over fallito and vice versa).
            val copia = unRiassunto(salvata.id.valore, salvata.registrazioneId).conAvvio()
            if (salvata.pronto) copia.conFallimento() else copia.conCompletamento(BOZZA, STRUTTURA)

            assertEquals(false, repo.concludi(copia).atteso(), "${salvata.id}")
            assertEquals(salvata.statoOsservabile(), checkNotNull(repo.trova(salvata.id)).statoOsservabile())
            assertEquals(
                prima,
                repo.diRegistrazione(salvata.registrazioneId).map { it.statoOsservabile() }.toSet(),
                "nulla cambia per ${salvata.id}",
            )
        }
        precedenti.forEach { assertEquals(it.statoOsservabile(), checkNotNull(repo.trova(it.id)).statoOsservabile()) }
    }

    @Test
    public fun `AC-S68 concludi di una riga in_corso e Ok true e scrive il pronto con i figli`() {
        val r = unRiassunto("riassunto-1", REGISTRAZIONE, argomento = "tema").conAvvio()
        repo.salva(r).atteso()

        r.conCompletamento(BOZZA, STRUTTURA)
        assertEquals(true, repo.concludi(r).atteso())

        assertEquals(r.statoOsservabile(), checkNotNull(repo.trova(r.id)).statoOsservabile())
        assertEquals(emptyList(), repo.inCorso())
    }

    @Test
    public fun `AC-S68 concludi di una riga in_corso e Ok true e scrive il fallito lasciando intatto il pronto`() {
        val pronto = unPronto("riassunto-1")
        val r = unRiassunto("riassunto-2", REGISTRAZIONE).conAvvio()
        repo.salva(pronto).atteso()
        repo.salva(r).atteso()

        r.conFallimento(MotivoFallimento.TROPPO_LUNGA)
        assertEquals(true, repo.concludi(r).atteso())

        assertEquals(r.statoOsservabile(), checkNotNull(repo.trova(r.id)).statoOsservabile())
        assertEquals(pronto.statoOsservabile(), checkNotNull(repo.trova(pronto.id)).statoOsservabile())
    }

    @Test
    public fun `AC-S68 concludi pronto sostituisce il pronto precedente della Registrazione e nessun altro`() {
        val precedente = unPronto("riassunto-1")
        val altro = unPronto("riassunto-3", registrazioneId = ALTRA_REGISTRAZIONE)
        val r = unRiassunto("riassunto-2", REGISTRAZIONE, parole = 800).conAvvio()
        listOf(precedente, altro, r).forEach { repo.salva(it).atteso() }

        r.conCompletamento(BOZZA_BREVE, STRUTTURA)
        assertEquals(true, repo.concludi(r).atteso())

        assertEquals(listOf(r.statoOsservabile()), repo.diRegistrazione(REGISTRAZIONE).map { it.statoOsservabile() })
        assertEquals(altro.statoOsservabile(), checkNotNull(repo.trova(altro.id)).statoOsservabile())
    }

    @Test
    public fun `AC-S69 rimuoviDiRegistrazione restituisce quanti ne toglie con elementi e Fonti`() {
        repo.salva(unPronto("riassunto-1")).atteso()
        repo.salva(unRiassunto("riassunto-2", REGISTRAZIONE)).atteso()
        val altro = unPronto("riassunto-3", registrazioneId = ALTRA_REGISTRAZIONE)
        repo.salva(altro).atteso()

        assertEquals(2, repo.rimuoviDiRegistrazione(REGISTRAZIONE).atteso())

        assertEquals(emptyList(), repo.diRegistrazione(REGISTRAZIONE))
        assertEquals(emptyList(), repo.inAttesa())
        assertEquals(altro.statoOsservabile(), checkNotNull(repo.trova(altro.id)).statoOsservabile())
        // A64: checked BEFORE any re-save, which would REPLACE (and so mask) an orphan left behind.
        listOf(RiassuntoId("riassunto-1"), RiassuntoId("riassunto-2")).forEach { id ->
            figliOrfaniDi(id)?.let { assertEquals(0, it, "figli orfani di $id dopo rimuoviDiRegistrazione") }
        }
        // No element or Fonte left behind: the same id saved again carries only its new children.
        val rifatto = unPronto("riassunto-1", bozza = BOZZA_BREVE)
        repo.salva(rifatto).atteso()
        assertEquals(rifatto.statoOsservabile(), checkNotNull(repo.trova(rifatto.id)).statoOsservabile())
    }

    @Test
    public fun `AC-S69 rimuoviDiRegistrazione senza Riassunti restituisce 0`() {
        repo.salva(unRiassunto("riassunto-1", ALTRA_REGISTRAZIONE)).atteso()

        assertEquals(0, repo.rimuoviDiRegistrazione(REGISTRAZIONE).atteso())

        assertEquals(listOf("riassunto-1"), idDi(repo.inAttesa()))
    }

    @Test
    public fun `AC-S69 rimuovi toglie il Riassunto con i figli e su un id assente non fa nulla`() {
        repo.salva(unPronto("riassunto-1")).atteso()
        val resta = unRiassunto("riassunto-2", REGISTRAZIONE)
        repo.salva(resta).atteso()

        repo.rimuovi(RiassuntoId("sconosciuto")).atteso()
        assertEquals(setOf("riassunto-1", "riassunto-2"), idDi(repo.diRegistrazione(REGISTRAZIONE)).toSet())

        repo.rimuovi(RiassuntoId("riassunto-1")).atteso()
        assertNull(repo.trova(RiassuntoId("riassunto-1")))
        // A64: checked BEFORE any re-save, which would REPLACE (and so mask) an orphan left behind.
        figliOrfaniDi(RiassuntoId("riassunto-1"))?.let { assertEquals(0, it, "figli orfani di riassunto-1 dopo rimuovi") }
        assertEquals(listOf(resta.statoOsservabile()), repo.diRegistrazione(REGISTRAZIONE).map { it.statoOsservabile() })
        val rifatto = unPronto("riassunto-1", bozza = BOZZA_BREVE)
        repo.salva(rifatto).atteso()
        assertEquals(rifatto.statoOsservabile(), checkNotNull(repo.trova(rifatto.id)).statoOsservabile())
    }

    private fun idDi(riassunti: List<Riassunto>): List<String> = riassunti.map { it.id.valore }

    private fun unPronto(
        id: String,
        registrazioneId: RegistrazioneId = REGISTRAZIONE,
        argomento: String? = null,
        parole: Int = 2000,
        richiestoAlle: Instant = PRIMA,
        bozza: BozzaRiassunto = BOZZA,
    ): Riassunto =
        unRiassunto(id, registrazioneId, argomento, parole, richiestoAlle).conAvvio().conCompletamento(bozza, STRUTTURA)

    public companion object {
        public val PROGETTO: ProgettoId = ProgettoId("progetto-1")

        /** Every Registrazione the contract uses, all in [PROGETTO]. */
        public val REGISTRAZIONI: List<RegistrazioneId> = (1..8).map { RegistrazioneId("registrazione-$it") }
        public val REGISTRAZIONE: RegistrazioneId = REGISTRAZIONI[0]
        public val ALTRA_REGISTRAZIONE: RegistrazioneId = REGISTRAZIONI[1]
        public val TERZA_REGISTRAZIONE: RegistrazioneId = REGISTRAZIONI[2]

        public val PREDISPOSIZIONE: PredisposizioneSintesi =
            PredisposizioneSintesi(setOf(PROGETTO), REGISTRAZIONI.associateWith { PROGETTO })

        /** {s1→V1, s2→V2, s3→V1}. */
        private val STRUTTURA = unaStruttura(1 to 1, 2 to 2, 3 to 1)

        /**
         * One element of each type, a multi-Fonte set (a duplicate collapsed), `{V<n>}` tokens and literal braces;
         * two elements with no valid Fonte are dropped by the Verifica → omessi = 2.
         */
        private val BOZZA = BozzaRiassunto(
            sommario = "{V1} e {V2} fissano il combattimento a turni {{bozza}}.",
            decisioni = listOf(
                BozzaElemento("Il combattimento resta a turni, come vuole {V2}.", listOf(1, 1), null),
                BozzaElemento("inventata", listOf(99), null),
            ),
            questioniAperte = listOf(BozzaElemento("Quanti nemici per stanza? {{n}} da decidere", listOf(3), null)),
            azioni = listOf(
                BozzaElemento("{V2} prepara il prototipo entro venerdi.", listOf(2), 2),
                BozzaElemento("senza fonte", emptyList(), 1),
            ),
            puntiChiave = listOf(BozzaElemento("Il ritmo del combattimento.", listOf(1, 3), 1)),
        )

        /** A different, shorter content (Sommario only, no omessi). */
        private val BOZZA_BREVE =
            BozzaRiassunto("Solo un sommario.", emptyList(), emptyList(), emptyList(), emptyList())

        // Millisecond precision: what a store keeps of an Instant.
        private val MOLTO_PRIMA = Instant.parse("2026-09-26T09:00:00Z")
        private val PRIMA = Instant.parse("2026-09-26T10:00:00.123Z")
        private val DOPO = Instant.parse("2026-09-26T11:00:00.456Z")
    }
}
