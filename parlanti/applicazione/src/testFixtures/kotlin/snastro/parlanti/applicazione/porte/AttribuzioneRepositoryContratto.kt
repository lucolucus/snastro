package snastro.parlanti.applicazione.porte

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.atteso
import snastro.parlanti.dominio.Attribuzione
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Consumer-driven contract of [AttribuzioneRepository] (boundary `repo-parlanti`, ADR 0007): keyed by
 * [VoceRef] — `salva` on an already-attributed Voce replaces its row (upsert), never a second one;
 * queries by Registrazione and by Parlante; `rimuovi`. One subclass per implementation.
 */
public abstract class AttribuzioneRepositoryContratto {
    /** A fresh, empty repository. */
    protected abstract fun repository(): AttribuzioneRepository

    /** Hook for real stores: create the parent rows of every id the contract uses ([PREDISPOSIZIONE]). */
    protected open fun predisponi(predisposizione: PredisposizioneParlanti) {}

    private lateinit var repo: AttribuzioneRepository

    @BeforeEach
    public fun preparaRepository() {
        repo = repository()
        predisponi(PREDISPOSIZIONE)
    }

    @Test
    public fun `AC-38 salva e trova per VoceRef`() {
        repo.salva(unaAttribuzione(voce(REGISTRAZIONE_1, 1), MARCO))

        val trovata = assertNotNull(repo.trova(voce(REGISTRAZIONE_1, 1)))

        assertEquals(voce(REGISTRAZIONE_1, 1), trovata.voceRef)
        assertEquals(PROGETTO, trovata.progettoId)
        assertEquals(MARCO, trovata.parlanteId)
        assertNull(repo.trova(voce(REGISTRAZIONE_1, 2)))
        assertNull(repo.trova(voce(REGISTRAZIONE_2, 1)))
    }

    @Test
    public fun `AC-38 salva su una VoceRef gia attribuita sostituisce la riga senza crearne una seconda`() {
        repo.salva(unaAttribuzione(voce(REGISTRAZIONE_1, 1), MARCO))
        val a = assertNotNull(repo.trova(voce(REGISTRAZIONE_1, 1)))
        a.cambia(ANNA).atteso()

        repo.salva(a)

        assertEquals(ANNA, assertNotNull(repo.trova(voce(REGISTRAZIONE_1, 1))).parlanteId)
        assertEquals(listOf(ANNA), repo.diRegistrazione(REGISTRAZIONE_1).map { it.parlanteId })
        assertEquals(emptyList(), repo.diParlante(MARCO))
    }

    @Test
    public fun `AC-38 salva di una nuova Attribuzione sulla stessa VoceRef sostituisce la riga`() {
        repo.salva(unaAttribuzione(voce(REGISTRAZIONE_1, 1), MARCO))

        repo.salva(unaAttribuzione(voce(REGISTRAZIONE_1, 1), ANNA))

        assertEquals(listOf(ANNA), repo.diRegistrazione(REGISTRAZIONE_1).map { it.parlanteId })
    }

    @Test
    public fun `AC-38 diRegistrazione restituisce le sole Attribuzioni di quella Registrazione`() {
        repo.salva(unaAttribuzione(voce(REGISTRAZIONE_1, 1), MARCO))
        repo.salva(unaAttribuzione(voce(REGISTRAZIONE_1, 2), ANNA))
        repo.salva(unaAttribuzione(voce(REGISTRAZIONE_2, 1), MARCO))

        assertEquals(
            setOf(voce(REGISTRAZIONE_1, 1) to MARCO, voce(REGISTRAZIONE_1, 2) to ANNA),
            repo.diRegistrazione(REGISTRAZIONE_1).map { it.voceRef to it.parlanteId }.toSet(),
        )
        assertEquals(listOf(voce(REGISTRAZIONE_2, 1)), repo.diRegistrazione(REGISTRAZIONE_2).map { it.voceRef })
        assertEquals(emptyList(), repo.diRegistrazione(RegistrazioneId("registrazione-vuota")))
    }

    @Test
    public fun `AC-38 diParlante restituisce le sole Attribuzioni di quel Parlante su tutte le Registrazioni`() {
        repo.salva(unaAttribuzione(voce(REGISTRAZIONE_1, 1), MARCO))
        repo.salva(unaAttribuzione(voce(REGISTRAZIONE_1, 2), ANNA))
        repo.salva(unaAttribuzione(voce(REGISTRAZIONE_2, 1), MARCO))

        assertEquals(
            setOf(voce(REGISTRAZIONE_1, 1), voce(REGISTRAZIONE_2, 1)),
            repo.diParlante(MARCO).map { it.voceRef }.toSet(),
        )
        assertEquals(listOf(voce(REGISTRAZIONE_1, 2)), repo.diParlante(ANNA).map { it.voceRef })
        assertEquals(emptyList(), repo.diParlante(ParlanteId("parlante-senza-voci")))
    }

    @Test
    public fun `AC-38 rimuovi cancella la sola Attribuzione di quella VoceRef`() {
        repo.salva(unaAttribuzione(voce(REGISTRAZIONE_1, 1), MARCO))
        repo.salva(unaAttribuzione(voce(REGISTRAZIONE_1, 2), MARCO))

        repo.rimuovi(voce(REGISTRAZIONE_1, 1))

        assertNull(repo.trova(voce(REGISTRAZIONE_1, 1)))
        assertEquals(listOf(voce(REGISTRAZIONE_1, 2)), repo.diRegistrazione(REGISTRAZIONE_1).map { it.voceRef })
        assertEquals(listOf(voce(REGISTRAZIONE_1, 2)), repo.diParlante(MARCO).map { it.voceRef })
    }

    @Test
    public fun `AC-38 rimuovi di una VoceRef non attribuita non cambia nulla`() {
        repo.salva(unaAttribuzione(voce(REGISTRAZIONE_1, 1), MARCO))

        repo.rimuovi(voce(REGISTRAZIONE_1, 2))

        assertEquals(listOf(voce(REGISTRAZIONE_1, 1)), repo.diRegistrazione(REGISTRAZIONE_1).map { it.voceRef })
    }

    @Test
    public fun `AC-38 lo stato salvato non cambia con modifiche non salvate all aggregato`() {
        val a = unaAttribuzione(voce(REGISTRAZIONE_1, 1), MARCO)
        repo.salva(a)

        a.cambia(ANNA).atteso()
        assertNotNull(repo.trova(voce(REGISTRAZIONE_1, 1))).cambia(ANNA).atteso()

        assertEquals(MARCO, assertNotNull(repo.trova(voce(REGISTRAZIONE_1, 1))).parlanteId)
        assertEquals(listOf(MARCO), repo.diRegistrazione(REGISTRAZIONE_1).map { it.parlanteId })
    }

    private fun voce(registrazioneId: RegistrazioneId, n: Int): VoceRef = VoceRef(registrazioneId, VoceId(n))

    private fun unaAttribuzione(voceRef: VoceRef, parlanteId: ParlanteId): Attribuzione =
        Attribuzione.conferma(voceRef, PROGETTO, parlanteId).aggregato

    protected companion object {
        public val PROGETTO: ProgettoId = ProgettoId("progetto-1")
        public val REGISTRAZIONE_1: RegistrazioneId = RegistrazioneId("registrazione-1")
        public val REGISTRAZIONE_2: RegistrazioneId = RegistrazioneId("registrazione-2")
        public val MARCO: ParlanteId = ParlanteId("parlante-marco")
        public val ANNA: ParlanteId = ParlanteId("parlante-anna")

        /** Every id this contract uses; the queries on "registrazione-vuota" / "parlante-senza-voci" need no row. */
        public val PREDISPOSIZIONE: PredisposizioneParlanti = PredisposizioneParlanti(
            progetti = setOf(PROGETTO),
            registrazioni = mapOf(REGISTRAZIONE_1 to PROGETTO, REGISTRAZIONE_2 to PROGETTO),
            voci = setOf(
                VoceRef(REGISTRAZIONE_1, VoceId(1)),
                VoceRef(REGISTRAZIONE_1, VoceId(2)),
                VoceRef(REGISTRAZIONE_2, VoceId(1)),
            ),
            parlanti = mapOf(MARCO to PROGETTO, ANNA to PROGETTO),
        )
    }
}
