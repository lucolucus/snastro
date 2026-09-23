package snastro.trascrizione.applicazione.porte

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.kernel.atteso
import snastro.trascrizione.dominio.DURATA_TRASCRITTO_MS
import snastro.trascrizione.dominio.Trascritto
import snastro.trascrizione.dominio.unSegmentoIniziale
import snastro.trascrizione.dominio.unTrascritto
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Consumer-driven contract of [TrascrittoRepository] (boundary `repo-trascrizione`, ADR 0006, INV-12): a full
 * round-trip of Voci, Segmenti, `prossimaVoce` and `prossimoSegmento` — also after a Revisione that removed
 * the highest Voce, so a reloaded Trascritto never reuses a `VoceId`; `salva` replaces; no aliasing.
 * One subclass per implementation (the Finta here, `TrascrittoRepositorySql` in `:trascrizione:adattatori`).
 */
public abstract class TrascrittoRepositoryContratto {
    /** A fresh, empty repository. */
    protected abstract fun repository(): TrascrittoRepository

    /** Hook for real stores: create the parent rows of every id the contract uses ([PREDISPOSIZIONE]). */
    protected open fun predisponi(predisposizione: PredisposizioneTrascrizione) {}

    private lateinit var repo: TrascrittoRepository

    @BeforeEach
    public fun preparaRepository() {
        repo = repository()
        predisponi(PREDISPOSIZIONE)
    }

    @Test
    public fun `AC-30 round-trip completo di un Trascritto appena creato`() {
        val t = unTrascritto(voci = 3, segmentiPerVoce = 2, registrazioneId = REGISTRAZIONE)

        repo.salva(t)

        assertStessoStato(t, assertNotNull(repo.trova(REGISTRAZIONE)))
    }

    @Test
    public fun `AC-30 round-trip di Segmenti sovrapposti e con lo stesso inizio`() {
        val t = Trascritto.crea(
            REGISTRAZIONE,
            DURATA_TRASCRITTO_MS,
            listOf(
                unSegmentoIniziale(voceIndice = 4, inizioMs = 0, fineMs = 3_000, testo = "si parla sopra"),
                unSegmentoIniziale(voceIndice = 2, inizioMs = 0, fineMs = 1_500, testo = "insieme"),
                unSegmentoIniziale(voceIndice = 4, inizioMs = 2_000, fineMs = 4_000, testo = "perché \"sì\""),
            ),
        ).atteso().aggregato

        repo.salva(t)

        assertStessoStato(t, assertNotNull(repo.trova(REGISTRAZIONE)))
    }

    @Test
    public fun `AC-30 round-trip dopo una Revisione con Voci nuove`() {
        val t = unTrascritto(voci = 2, segmentiPerVoce = 3, registrazioneId = REGISTRAZIONE)
        t.dividi(VoceId(1), setOf(SegmentoId(3), SegmentoId(5))).atteso()
        t.riassegna(SegmentoId(2), null).atteso()

        repo.salva(t)

        assertStessoStato(t, assertNotNull(repo.trova(REGISTRAZIONE)))
    }

    @Test
    public fun `AC-30 dopo un unione che rimuove la Voce piu alta prossimaVoce resta oltre ogni id usato`() {
        val t = unTrascritto(voci = 3, segmentiPerVoce = 2, registrazioneId = REGISTRAZIONE)
        t.unisci(VoceId(1), VoceId(3)).atteso()
        repo.salva(t)

        val ricaricato = assertNotNull(repo.trova(REGISTRAZIONE))

        assertStessoStato(t, ricaricato)
        assertEquals(4, ricaricato.prossimaVoce)
        val divisa = ricaricato.dividi(VoceId(1), setOf(SegmentoId(1))).atteso()
        assertEquals(VoceId(4), divisa.nuova, "una Voce nuova non riusa l id della Voce rimossa")
    }

    @Test
    public fun `AC-30 salvare di nuovo sostituisce lo stato salvato`() {
        val t = unTrascritto(voci = 2, segmentiPerVoce = 2, registrazioneId = REGISTRAZIONE)
        repo.salva(t)
        val rivisto = assertNotNull(repo.trova(REGISTRAZIONE))
        rivisto.unisci(VoceId(2), VoceId(1)).atteso()
        rivisto.riassegna(SegmentoId(4), null).atteso()

        repo.salva(rivisto)

        val ricaricato = assertNotNull(repo.trova(REGISTRAZIONE))
        assertStessoStato(rivisto, ricaricato)
        assertEquals(listOf(VoceId(2), VoceId(3)), ricaricato.voci.map { it.id })
    }

    @Test
    public fun `AC-30 trova e conTrascritto distinguono le Registrazioni`() {
        assertNull(repo.trova(REGISTRAZIONE))
        assertEquals(emptyList(), repo.conTrascritto())

        repo.salva(unTrascritto(voci = 1, segmentiPerVoce = 1, registrazioneId = REGISTRAZIONE))
        repo.salva(unTrascritto(voci = 2, segmentiPerVoce = 1, registrazioneId = ALTRA_REGISTRAZIONE))
        repo.salva(unTrascritto(voci = 2, segmentiPerVoce = 2, registrazioneId = REGISTRAZIONE))

        assertEquals(setOf(REGISTRAZIONE, ALTRA_REGISTRAZIONE), repo.conTrascritto().toSet())
        assertEquals(2, repo.conTrascritto().size, "ogni Registrazione una volta sola")
        assertEquals(4, repo.trova(REGISTRAZIONE)?.segmenti?.size)
        assertEquals(2, repo.trova(ALTRA_REGISTRAZIONE)?.segmenti?.size)
        assertNull(repo.trova(SENZA_TRASCRITTO))
    }

    @Test
    public fun `AC-30 nessun alias tra il Trascritto del chiamante e quello salvato`() {
        val t = unTrascritto(voci = 2, segmentiPerVoce = 2, registrazioneId = REGISTRAZIONE)
        repo.salva(t)
        val atteso = Istantanea.di(t)

        t.unisci(VoceId(1), VoceId(2)).atteso()
        assertNotNull(repo.trova(REGISTRAZIONE)).riassegna(SegmentoId(1), null).atteso()

        assertEquals(atteso, Istantanea.di(assertNotNull(repo.trova(REGISTRAZIONE))))
    }

    private fun assertStessoStato(atteso: Trascritto, trovato: Trascritto) {
        assertEquals(Istantanea.di(atteso), Istantanea.di(trovato))
    }

    /** The observable state of a [Trascritto] (the aggregate has no value equality). */
    private data class Istantanea(
        val registrazioneId: RegistrazioneId,
        val segmenti: List<Any>,
        val voci: List<Any>,
        val prossimaVoce: Int,
        val prossimoSegmento: Int,
    ) {
        companion object {
            fun di(t: Trascritto): Istantanea =
                Istantanea(t.registrazioneId, t.segmenti, t.voci, t.prossimaVoce, t.prossimoSegmento)
        }
    }

    public companion object {
        public val PROGETTO: ProgettoId = ProgettoId("progetto-1")
        public val REGISTRAZIONE: RegistrazioneId = RegistrazioneId("registrazione-1")
        public val ALTRA_REGISTRAZIONE: RegistrazioneId = RegistrazioneId("registrazione-2")

        /** A Registrazione that exists but never gets a Trascritto. */
        public val SENZA_TRASCRITTO: RegistrazioneId = RegistrazioneId("registrazione-3")

        public val PREDISPOSIZIONE: PredisposizioneTrascrizione = PredisposizioneTrascrizione(
            setOf(PROGETTO),
            listOf(REGISTRAZIONE, ALTRA_REGISTRAZIONE, SENZA_TRASCRITTO).associateWith { PROGETTO },
        )
    }
}
