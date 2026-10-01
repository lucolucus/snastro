package snastro.trascrizione.applicazione.porte

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import snastro.kernel.IncontroId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.SegmentoRef
import snastro.kernel.VoceId
import snastro.kernel.atteso
import snastro.kernel.unIncontroDi
import snastro.trascrizione.dominio.DURATA_TRASCRITTO_MS
import snastro.trascrizione.dominio.SegmentoIniziale
import snastro.trascrizione.dominio.VociDellIncontro
import snastro.trascrizione.dominio.unSegmentoIniziale
import snastro.trascrizione.dominio.unaRadice
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Consumer-driven contract of [VociDellIncontroRepository] (boundary `repo-voci-incontro`, ADR 0035 §1, INV-I4,
 * INV-I16): a full round-trip of the root (Voce counter, each Parte's Segmenti and Segmento counter), the counter
 * surviving the removal of every Trascritto, a Parte read alone, no aliasing. One subclass per implementation (the
 * Finta here, the SQL adapter in `:trascrizione:adattatori`).
 *
 * D-0037: the cases on an Incontro with more than one Parte are built by [casiConPiuParti] only when the environment
 * declares [piuPartiPerIncontro]; otherwise that factory runs the one-Parte guard instead (never skipped).
 */
public abstract class VociDellIncontroRepositoryContratto {
    /** A fresh, empty repository. */
    protected abstract fun repository(): VociDellIncontroRepository

    /** Hook for real stores: create the parent rows of every id the contract uses ([PREDISPOSIZIONE]). */
    protected open fun predisponi(predisposizione: PredisposizioneTrascrizione) {}

    /** Environment capability (D-0037): the store can hold an Incontro with more than one Parte. */
    protected abstract val piuPartiPerIncontro: Boolean

    private lateinit var repo: VociDellIncontroRepository

    @BeforeEach
    public fun preparaRepository() {
        repo = repository()
        predisponi(PREDISPOSIZIONE)
    }

    @Test
    public fun `AC-I21 trova di un Incontro sconosciuto e trascritto di una Parte non trascritta restituiscono null`() {
        repo.salva(unaRadice(registrazioneId = REGISTRAZIONE))

        assertNull(repo.trova(IncontroId("incontro-sconosciuto")))
        assertNull(repo.trova(unIncontroDi(SENZA_TRASCRITTO)))
        assertNull(repo.trascritto(SENZA_TRASCRITTO))
    }

    @Test
    public fun `AC-30 round-trip di una radice con una Parte appena trascritta`() {
        val radice = unaRadice(voci = 3, segmentiPerVoce = 2, registrazioneId = REGISTRAZIONE)

        repo.salva(radice)

        assertStessoStato(radice, assertNotNull(repo.trova(unIncontroDi(REGISTRAZIONE))))
    }

    @Test
    public fun `AC-30 round-trip di Segmenti sovrapposti e con lo stesso inizio`() {
        val radice = VociDellIncontro.crea(unIncontroDi(REGISTRAZIONE))
        val turni = listOf(
            unSegmentoIniziale(voceIndice = 4, inizioMs = 0, fineMs = 3_000, testo = "si parla sopra"),
            unSegmentoIniziale(voceIndice = 2, inizioMs = 0, fineMs = 1_500, testo = "insieme"),
            unSegmentoIniziale(voceIndice = 4, inizioMs = 2_000, fineMs = 4_000, testo = "perché \"sì\""),
        )
        radice.completaParte(REGISTRAZIONE, turni, DURATA_TRASCRITTO_MS).atteso()

        repo.salva(radice)

        assertStessoStato(radice, assertNotNull(repo.trova(unIncontroDi(REGISTRAZIONE))))
    }

    @Test
    public fun `AC-30 round-trip dopo una Revisione con Voci nuove e Segmenti confermati`() {
        val radice = unaRadice(voci = 2, segmentiPerVoce = 3, registrazioneId = REGISTRAZIONE)
        radice.dividi(VoceId(1), setOf(ref(REGISTRAZIONE, 3), ref(REGISTRAZIONE, 5))).atteso()
        radice.riassegna(ref(REGISTRAZIONE, 2), null).atteso()
        radice.confermaSegmento(ref(REGISTRAZIONE, 6), true).atteso()

        repo.salva(radice)

        assertStessoStato(radice, assertNotNull(repo.trova(unIncontroDi(REGISTRAZIONE))))
    }

    @Test
    public fun `AC-30 dopo un'unione che rimuove la Voce piu' alta il contatore resta oltre ogni id usato`() {
        val radice = unaRadice(voci = 3, segmentiPerVoce = 2, registrazioneId = REGISTRAZIONE)
        radice.unisci(VoceId(1), VoceId(3)).atteso()
        repo.salva(radice)

        val ricaricata = assertNotNull(repo.trova(unIncontroDi(REGISTRAZIONE)))

        assertStessoStato(radice, ricaricata)
        val divisa = ricaricata.dividi(VoceId(1), setOf(ref(REGISTRAZIONE, 1))).atteso()
        assertEquals(VoceId(4), divisa.nuova, "una Voce nuova non riusa l'id della Voce rimossa")
    }

    @Test
    public fun `AC-30 salvare di nuovo sostituisce lo stato salvato`() {
        repo.salva(unaRadice(voci = 2, segmentiPerVoce = 2, registrazioneId = REGISTRAZIONE))
        val rivista = assertNotNull(repo.trova(unIncontroDi(REGISTRAZIONE)))
        rivista.unisci(VoceId(2), VoceId(1)).atteso()
        rivista.riassegna(ref(REGISTRAZIONE, 4), null).atteso()

        repo.salva(rivista)

        val ricaricata = assertNotNull(repo.trova(unIncontroDi(REGISTRAZIONE)))
        assertStessoStato(rivista, ricaricata)
        assertEquals(listOf(VoceId(2), VoceId(3)), ricaricata.voci)
    }

    @Test
    public fun `AC-30 trascritto e conTrascritto distinguono le Registrazioni`() {
        assertEquals(emptyList(), repo.conTrascritto())

        repo.salva(unaRadice(voci = 1, segmentiPerVoce = 1, registrazioneId = REGISTRAZIONE))
        repo.salva(unaRadice(voci = 2, segmentiPerVoce = 1, registrazioneId = ALTRA_REGISTRAZIONE))
        repo.salva(unaRadice(voci = 2, segmentiPerVoce = 2, registrazioneId = REGISTRAZIONE))

        assertEquals(setOf(REGISTRAZIONE, ALTRA_REGISTRAZIONE), repo.conTrascritto().toSet())
        assertEquals(2, repo.conTrascritto().size, "ogni Registrazione una volta sola")
        assertEquals(4, repo.trascritto(REGISTRAZIONE)?.segmenti?.size)
        assertEquals(unIncontroDi(REGISTRAZIONE), repo.trascritto(REGISTRAZIONE)?.incontroId)
        assertEquals(2, repo.trascritto(ALTRA_REGISTRAZIONE)?.segmenti?.size)
    }

    @Test
    public fun `AC-30 nessun alias tra la radice del chiamante e quella salvata`() {
        val radice = unaRadice(voci = 2, segmentiPerVoce = 2, registrazioneId = REGISTRAZIONE)
        repo.salva(radice)
        val atteso = Istantanea.di(radice)

        radice.unisci(VoceId(1), VoceId(2)).atteso()
        assertNotNull(repo.trova(unIncontroDi(REGISTRAZIONE))).riassegna(ref(REGISTRAZIONE, 1), null).atteso()

        assertEquals(atteso, Istantanea.di(assertNotNull(repo.trova(unIncontroDi(REGISTRAZIONE)))))
    }

    @Test
    public fun `AC-619 rimuovi toglie la radice con le sue Parti e lascia le altre radici`() {
        repo.salva(unaRadice(voci = 2, segmentiPerVoce = 2, registrazioneId = REGISTRAZIONE))
        val altra = unaRadice(voci = 3, segmentiPerVoce = 1, registrazioneId = ALTRA_REGISTRAZIONE)
        repo.salva(altra)

        repo.rimuovi(unIncontroDi(REGISTRAZIONE))
        repo.rimuovi(unIncontroDi(SENZA_TRASCRITTO)) // no root: a no-op

        assertNull(repo.trova(unIncontroDi(REGISTRAZIONE)))
        assertNull(repo.trascritto(REGISTRAZIONE))
        assertEquals(listOf(ALTRA_REGISTRAZIONE), repo.conTrascritto())
        assertStessoStato(altra, assertNotNull(repo.trova(unIncontroDi(ALTRA_REGISTRAZIONE))))
    }

    @Test
    public fun `AC-I21 il contatore sopravvive alla rimozione dell'unico Trascritto`() {
        val radice = unaRadice(voci = 3, segmentiPerVoce = 1, registrazioneId = REGISTRAZIONE)
        radice.rimuoviParte(REGISTRAZIONE).atteso()

        repo.salva(radice)

        val ricaricata = assertNotNull(repo.trova(unIncontroDi(REGISTRAZIONE)), "the root outlives its last Trascritto")
        assertStessoStato(radice, ricaricata)
        assertNull(repo.trascritto(REGISTRAZIONE))
        assertEquals(emptyList(), repo.conTrascritto())
        val nuove = ricaricata.completaParte(REGISTRAZIONE, turni(0), DURATA_TRASCRITTO_MS).atteso().vociNuove
        assertEquals(setOf(VoceId(4)), nuove, "INV-I4: a new Voce takes the stored counter")
    }

    @Test
    public fun `AC-I21 prossimoSegmento di una Parte non diminuisce attraverso una sostituzione`() {
        repo.salva(unaRadice(voci = 2, segmentiPerVoce = 3, registrazioneId = REGISTRAZIONE)) // Segmenti 1..6
        val ricaricata = assertNotNull(repo.trova(unIncontroDi(REGISTRAZIONE)))

        ricaricata.completaParte(REGISTRAZIONE, turni(0, perVoce = 1), DURATA_TRASCRITTO_MS).atteso()
        repo.salva(ricaricata)

        val sostituito = assertNotNull(repo.trascritto(REGISTRAZIONE))
        assertEquals(listOf(SegmentoId(7)), sostituito.segmenti.map { it.id }, "INV-I16: ids after every one used")
        assertTrue(sostituito.prossimoSegmento >= 8)
        assertEquals(listOf(VoceId(3)), sostituito.voci.map { it.id }, "INV-I4: the counter is never lowered")
    }

    @TestFactory
    public fun `AC-I21 casi su un Incontro con piu' Parti`(): List<DynamicTest> =
        if (piuPartiPerIncontro) casiConPiuParti() else listOf(guardiaUnaParte())

    private fun casiConPiuParti(): List<DynamicTest> = listOf(
        dynamicTest("AC-I21 round-trip di una radice con due Parti e una Voce unita tra le due") {
            val radice = dueParti()
            radice.unisci(VoceId(1), VoceId(3)).atteso() // Voce 1 now speaks in A and B

            repo.salva(radice)

            assertStessoStato(radice, assertNotNull(repo.trova(INCONTRO)))
        },
        dynamicTest("AC-I21 trascritto(r) legge una Parte senza l'altra") {
            repo.salva(dueParti())

            assertEquals(setOf(VoceId(1), VoceId(2)), repo.trascritto(PARTE_A)?.voci?.map { it.id }?.toSet())
            assertEquals(setOf(VoceId(3), VoceId(4), VoceId(5)), repo.trascritto(PARTE_B)?.voci?.map { it.id }?.toSet())
            assertEquals(INCONTRO, repo.trascritto(PARTE_B)?.incontroId)
        },
        dynamicTest("AC-I21 il contatore sopravvive alla rimozione di ogni Trascritto dell'Incontro") {
            val radice = dueParti()
            repo.salva(radice)
            radice.rimuoviParte(PARTE_A).atteso()
            repo.salva(radice)
            assertStessoStato(radice, assertNotNull(repo.trova(INCONTRO)))
            assertNull(repo.trascritto(PARTE_A))

            radice.rimuoviParte(PARTE_B).atteso()
            repo.salva(radice)

            val ricaricata = assertNotNull(repo.trova(INCONTRO))
            assertEquals(emptyList(), ricaricata.voci)
            assertEquals(emptyList(), repo.conTrascritto())
            val nuove = ricaricata.completaParte(PARTE_B, turni(0), DURATA_TRASCRITTO_MS).atteso().vociNuove
            assertEquals(setOf(VoceId(6)), nuove, "INV-I4: never a number used in the Incontro")
        },
        dynamicTest("AC-I21 sostituire la Parte B non cambia la Parte A salvata") {
            repo.salva(dueParti())
            val ricaricata = assertNotNull(repo.trova(INCONTRO))
            val primaA = repo.trascritto(PARTE_A)?.segmenti

            ricaricata.completaParte(PARTE_B, turni(0), DURATA_TRASCRITTO_MS).atteso()
            repo.salva(ricaricata)

            assertEquals(primaA, repo.trascritto(PARTE_A)?.segmenti)
            assertEquals(listOf(SegmentoId(7), SegmentoId(8)), repo.trascritto(PARTE_B)?.segmenti?.map { it.id })
            assertStessoStato(ricaricata, assertNotNull(repo.trova(INCONTRO)))
        },
    )

    /** Without the capability, the one-Parte shape every Incontro of this environment has still round-trips. */
    private fun guardiaUnaParte(): DynamicTest =
        dynamicTest("AC-I21 ambiente con una Parte per Incontro: la radice di una Parte fa round-trip") {
            val radice = unaRadice(registrazioneId = REGISTRAZIONE)
            repo.salva(radice)
            assertStessoStato(radice, assertNotNull(repo.trova(unIncontroDi(REGISTRAZIONE))))
        }

    /** The root of [INCONTRO]: Parte A with Voci 1, 2 (Segmenti 1..4), Parte B with Voci 3, 4, 5 (Segmenti 1..6). */
    private fun dueParti(): VociDellIncontro {
        val radice = VociDellIncontro.crea(INCONTRO)
        radice.completaParte(PARTE_A, turni(0, 1), DURATA_TRASCRITTO_MS).atteso()
        radice.completaParte(PARTE_B, turni(0, 1, 2), DURATA_TRASCRITTO_MS).atteso()
        return radice
    }

    /** [perVoce] rounds in which the diarizer clusters speak in the order [ordine], one 1 s turn each. */
    private fun turni(vararg ordine: Int, perVoce: Int = 2): List<SegmentoIniziale> =
        (0 until perVoce).flatMap { giro ->
            ordine.mapIndexed { i, cluster -> unSegmentoIniziale(cluster, ((giro * ordine.size + i) * 1_000).toLong()) }
        }

    private fun ref(r: RegistrazioneId, numero: Int) = SegmentoRef(r, SegmentoId(numero))

    private fun assertStessoStato(atteso: VociDellIncontro, trovato: VociDellIncontro) {
        assertEquals(Istantanea.di(atteso), Istantanea.di(trovato))
    }

    /**
     * The observable state of a root (it has no value equality). The Parti are compared as a map: the order the
     * store returns them in is the Incontro's (read through `LettoreRegistrazione`), not the completion order.
     */
    private data class Istantanea(
        val incontroId: IncontroId,
        val prossimaVoce: Int,
        val voci: List<VoceId>,
        val parti: Map<RegistrazioneId, Pair<List<Any>, Int>>,
    ) {
        companion object {
            fun di(v: VociDellIncontro): Istantanea = Istantanea(
                v.incontroId,
                v.prossimaVoce,
                v.voci,
                v.trascritti.associate { it.registrazioneId to (it.segmenti to it.prossimoSegmento) },
            )
        }
    }

    public companion object {
        public val PROGETTO: ProgettoId = ProgettoId("progetto-1")
        public val REGISTRAZIONE: RegistrazioneId = RegistrazioneId("registrazione-1")
        public val ALTRA_REGISTRAZIONE: RegistrazioneId = RegistrazioneId("registrazione-2")

        /** A Registrazione that exists but never gets a Trascritto. */
        public val SENZA_TRASCRITTO: RegistrazioneId = RegistrazioneId("registrazione-3")

        /** An Incontro with two Parti, A then B. */
        public val INCONTRO: IncontroId = IncontroId("incontro-a-b")
        public val PARTE_A: RegistrazioneId = RegistrazioneId("parte-a")
        public val PARTE_B: RegistrazioneId = RegistrazioneId("parte-b")

        public val PREDISPOSIZIONE: PredisposizioneTrascrizione = PredisposizioneTrascrizione(
            setOf(PROGETTO),
            listOf(REGISTRAZIONE, ALTRA_REGISTRAZIONE, SENZA_TRASCRITTO, PARTE_A, PARTE_B).associateWith { PROGETTO },
            mapOf(INCONTRO to listOf(PARTE_A, PARTE_B)),
        )
    }
}
