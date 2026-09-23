package snastro.parlanti.applicazione.porte

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.parlanti.dominio.ErroreParlanti
import snastro.parlanti.dominio.Impronta
import snastro.parlanti.dominio.ImprontaVocale
import snastro.parlanti.dominio.Nome
import snastro.parlanti.dominio.Parlante
import snastro.parlanti.dominio.TipoParlante
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Consumer-driven contract of [ParlanteRepository] (boundary `repo-parlanti`, ADR 0007/0009): INV-16
 * (unique active normalized [Nome] per Progetto) refused like the partial unique index, `salva` as an
 * upsert that replaces the owned prints, no aliasing between the caller's aggregate and the stored state.
 * One subclass per implementation (the Finta here, `ParlanteRepositorySql` in `:parlanti:adattatori`).
 */
public abstract class ParlanteRepositoryContratto {
    /** A fresh, empty repository. */
    protected abstract fun repository(): ParlanteRepository

    /** Hook for real stores: create the parent rows of every id the contract uses ([PREDISPOSIZIONE]). */
    protected open fun predisponi(predisposizione: PredisposizioneParlanti) {}

    /**
     * Physical `impronta_vocale` rows stored for [id] (ADR 0009), read beside the port so an adapter that
     * merely hides print rows fails. Mandatory: every implementation must count them, so a subclass can
     * never silently skip the purge checks.
     */
    protected abstract fun righeImpronte(id: ParlanteId): Int

    private lateinit var repo: ParlanteRepository

    @BeforeEach
    public fun preparaRepository() {
        repo = repository()
        predisponi(PREDISPOSIZIONE)
    }

    @Test
    public fun `AC-37 un secondo attivo con lo stesso nome normalizzato nello stesso Progetto e NomeGiaInUso`() {
        repo.salva(unParlante("id-1", "Marco")).atteso()

        val errore = repo.salva(unParlante("id-2", "  MARCO ")).erroreAtteso<ErroreParlanti.NomeGiaInUso>()

        assertEquals("MARCO", errore.nome)
        assertNull(repo.trova(ParlanteId("id-2")))
        assertEquals(listOf("id-1"), repo.delProgetto(PROGETTO).map { it.id.valore })
    }

    @Test
    public fun `AC-37 lo stesso nome in un altro Progetto e accettato`() {
        repo.salva(unParlante("id-1", "Marco")).atteso()

        repo.salva(unParlante("id-2", "marco", progettoId = ALTRO_PROGETTO)).atteso()

        assertNotNull(repo.trova(ParlanteId("id-2")))
    }

    @Test
    public fun `AC-37 il nome di un eliminato e accettato`() {
        val eliminato = unParlante("id-1", "Marco")
        eliminato.elimina().atteso()
        repo.salva(eliminato).atteso()

        repo.salva(unParlante("id-2", "marco")).atteso()

        assertEquals(setOf("id-1", "id-2"), repo.delProgetto(PROGETTO).map { it.id.valore }.toSet())
    }

    @Test
    public fun `AC-37 nomeAttivoInUso confronta il nome normalizzato dei soli attivi dello stesso Progetto`() {
        repo.salva(unParlante("id-1", "Marco")).atteso()
        val eliminato = unParlante("id-2", "Anna")
        eliminato.elimina().atteso()
        repo.salva(eliminato).atteso()

        assertTrue(repo.nomeAttivoInUso(PROGETTO, nome(" marco "), escluso = null))
        assertFalse(repo.nomeAttivoInUso(PROGETTO, nome("Anna"), escluso = null))
        assertFalse(repo.nomeAttivoInUso(ALTRO_PROGETTO, nome("Marco"), escluso = null))
        assertFalse(repo.nomeAttivoInUso(PROGETTO, nome("Luca"), escluso = null))
    }

    @Test
    public fun `AC-37 nomeAttivoInUso esclude il Parlante indicato`() {
        repo.salva(unParlante("id-1", "Marco")).atteso()

        assertFalse(repo.nomeAttivoInUso(PROGETTO, nome("MARCO"), escluso = ParlanteId("id-1")))
        assertTrue(repo.nomeAttivoInUso(PROGETTO, nome("MARCO"), escluso = ParlanteId("id-9")))
    }

    @Test
    public fun `AC-37 rinominare se stesso cambiando solo le maiuscole non e NomeGiaInUso`() {
        repo.salva(unParlante("id-1", "Marco")).atteso()
        val p = assertNotNull(repo.trova(ParlanteId("id-1")))
        p.rinomina(nome("MARCO")).atteso()

        repo.salva(p).atteso()

        assertEquals("MARCO", assertNotNull(repo.trova(ParlanteId("id-1"))).nome.valore)
    }

    @Test
    public fun `AC-37 round-trip con impronte`() {
        val p = unParlante("id-1", "Marco", tipo = TipoParlante.OCCASIONALE)
        p.registraImpronta(VOCE_1, Impronta(floatArrayOf(0.1f, 0.2f, 0.3f))).atteso()
        p.registraImpronta(VOCE_2, Impronta(floatArrayOf(-1.5f, 0f, 2.25f))).atteso()

        repo.salva(p).atteso()

        assertStessoStato(p, assertNotNull(repo.trova(p.id)))
    }

    @Test
    public fun `AC-37 round-trip di un eliminato conserva il nome e nessuna impronta`() {
        val p = unParlante("id-1", "Marco")
        p.registraImpronta(VOCE_1, Impronta(floatArrayOf(1f, 2f))).atteso()
        repo.salva(p).atteso()
        p.elimina().atteso()

        repo.salva(p).atteso()

        val trovato = assertNotNull(repo.trova(p.id))
        assertStessoStato(p, trovato)
        assertTrue(trovato.eliminato)
        assertEquals(emptyList(), trovato.impronte)
        assertRigheImpronte(0, p.id)
    }

    @Test
    public fun `AC-37 salva sostituisce le impronte possedute senza duplicare il Parlante`() {
        val p = unParlante("id-1", "Marco")
        p.registraImpronta(VOCE_1, Impronta(floatArrayOf(1f))).atteso()
        p.registraImpronta(VOCE_2, Impronta(floatArrayOf(2f))).atteso()
        repo.salva(p).atteso()
        p.rimuoviImpronta(VOCE_1)
        p.registraImpronta(VOCE_2, Impronta(floatArrayOf(3f))).atteso()

        repo.salva(p).atteso()

        val impronte = assertNotNull(repo.trova(p.id)).impronte
        assertEquals(listOf(ImprontaVocale(VOCE_2, Impronta(floatArrayOf(3f)))), impronte)
        assertEquals(1, repo.delProgetto(PROGETTO).size)
        assertRigheImpronte(1, p.id)
    }

    @Test
    public fun `AC-37 rinominare un Parlante esistente con impronte cambiate in un nome in uso non salva nulla`() {
        val marco = unParlante("id-1", "Marco")
        repo.salva(marco).atteso()
        val anna = unParlante("id-2", "Anna")
        anna.registraImpronta(VOCE_1, Impronta(floatArrayOf(1f, 1f))).atteso()
        repo.salva(anna).atteso()
        val modificata = assertNotNull(repo.trova(anna.id))
        modificata.rimuoviImpronta(VOCE_1)
        modificata.registraImpronta(VOCE_2, Impronta(floatArrayOf(2f, 2f))).atteso()
        modificata.registraImpronta(VOCE_3, Impronta(floatArrayOf(3f, 3f))).atteso()
        modificata.rinomina(nome(" MARCO")).atteso()

        repo.salva(modificata).erroreAtteso<ErroreParlanti.NomeGiaInUso>()

        assertStessoStato(anna, assertNotNull(repo.trova(anna.id)))
        assertRigheImpronte(1, anna.id)
        assertEquals("Marco", assertNotNull(repo.trova(marco.id)).nome.valore)
    }

    @Test
    public fun `AC-37 lo stato salvato non cambia con modifiche non salvate all aggregato o ai suoi array`() {
        val valori = floatArrayOf(1f, 2f)
        val p = unParlante("id-1", "Marco")
        p.registraImpronta(VOCE_1, Impronta(valori)).atteso()
        repo.salva(p).atteso()

        p.rinomina(nome("Luca")).atteso()
        valori[0] = 9f
        val letto = assertNotNull(repo.trova(p.id))
        letto.rinomina(nome("Anna")).atteso()

        val riletto = assertNotNull(repo.trova(p.id))
        assertEquals("Marco", riletto.nome.valore)
        assertEquals(listOf(ImprontaVocale(VOCE_1, Impronta(floatArrayOf(1f, 2f)))), riletto.impronte)
    }

    @Test
    public fun `AC-37 salva non modifica gli array delle impronte ricevute`() {
        val valori = floatArrayOf(0.5f, -0.5f)
        val p = unParlante("id-1", "Marco")
        p.registraImpronta(VOCE_1, Impronta(valori)).atteso()

        repo.salva(p).atteso()

        assertEquals(listOf(0.5f, -0.5f), valori.toList())
    }

    @Test
    public fun `AC-37 trova di un id sconosciuto restituisce null`() {
        assertNull(repo.trova(ParlanteId("id-9")))
    }

    @Test
    public fun `AC-37 delProgetto restituisce attivi ed eliminati del solo Progetto indicato`() {
        repo.salva(unParlante("id-1", "Marco")).atteso()
        val eliminato = unParlante("id-2", "Anna")
        eliminato.elimina().atteso()
        repo.salva(eliminato).atteso()
        repo.salva(unParlante("id-3", "Luca", progettoId = ALTRO_PROGETTO)).atteso()

        assertEquals(setOf("id-1", "id-2"), repo.delProgetto(PROGETTO).map { it.id.valore }.toSet())
        assertEquals(listOf("id-3"), repo.delProgetto(ALTRO_PROGETTO).map { it.id.valore })
        assertEquals(emptyList(), repo.delProgetto(ProgettoId("progetto-vuoto")))
    }

    @Test
    public fun `AC-37 rimuovi cancella il Parlante con le sue impronte e ne libera il nome`() {
        val p = unParlante("id-1", "Marco", tipo = TipoParlante.OCCASIONALE)
        p.registraImpronta(VOCE_1, Impronta(floatArrayOf(1f))).atteso()
        repo.salva(p).atteso()

        repo.rimuovi(p.id)

        assertNull(repo.trova(p.id))
        assertRigheImpronte(0, p.id)
        assertEquals(emptyList(), repo.delProgetto(PROGETTO))
        assertFalse(repo.nomeAttivoInUso(PROGETTO, nome("Marco"), escluso = null))
        repo.salva(unParlante("id-2", "Marco")).atteso()
    }

    private fun assertStessoStato(atteso: Parlante, trovato: Parlante) {
        assertEquals(atteso.id, trovato.id)
        assertEquals(atteso.progettoId, trovato.progettoId)
        assertEquals(atteso.nome, trovato.nome)
        assertEquals(atteso.tipo, trovato.tipo)
        assertEquals(atteso.attivo, trovato.attivo)
        assertEquals(atteso.eliminato, trovato.eliminato)
        assertEquals(atteso.impronte.toSet(), trovato.impronte.toSet())
        assertEquals(atteso.impronte.size, trovato.impronte.size)
    }

    private fun assertRigheImpronte(attese: Int, id: ParlanteId) {
        assertEquals(attese, righeImpronte(id), "righe impronta_vocale di ${id.valore}")
    }

    private fun nome(testo: String): Nome = Nome.di(testo).atteso()

    private fun unParlante(
        id: String,
        nome: String,
        progettoId: ProgettoId = PROGETTO,
        tipo: TipoParlante = TipoParlante.RICORRENTE,
    ): Parlante = Parlante.crea(ParlanteId(id), progettoId, nome(nome), tipo).aggregato

    protected companion object {
        public val PROGETTO: ProgettoId = ProgettoId("progetto-1")
        public val ALTRO_PROGETTO: ProgettoId = ProgettoId("progetto-2")
        public val REGISTRAZIONE: RegistrazioneId = RegistrazioneId("registrazione-1")
        public val VOCE_1: VoceRef = VoceRef(REGISTRAZIONE, VoceId(1))
        public val VOCE_2: VoceRef = VoceRef(REGISTRAZIONE, VoceId(2))
        public val VOCE_3: VoceRef = VoceRef(REGISTRAZIONE, VoceId(3))

        /** Every id this contract uses (prints only on Parlanti of [PROGETTO]). */
        public val PREDISPOSIZIONE: PredisposizioneParlanti = PredisposizioneParlanti(
            progetti = setOf(PROGETTO, ALTRO_PROGETTO),
            registrazioni = mapOf(REGISTRAZIONE to PROGETTO),
            voci = setOf(VOCE_1, VOCE_2, VOCE_3),
            parlanti = emptyMap(),
        )
    }
}
