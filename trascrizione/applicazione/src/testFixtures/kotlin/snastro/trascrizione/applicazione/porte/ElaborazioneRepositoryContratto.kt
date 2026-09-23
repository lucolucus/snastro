package snastro.trascrizione.applicazione.porte

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import snastro.kernel.ElaborazioneId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.trascrizione.dominio.Elaborazione
import snastro.trascrizione.dominio.ErroreTrascrizione.ElaborazioneGiaAperta
import snastro.trascrizione.dominio.ErroreTrascrizione.ElaborazioneGiaCompletata
import snastro.trascrizione.dominio.StatoElaborazione
import snastro.trascrizione.dominio.StatoElaborazione.COMPLETATA
import snastro.trascrizione.dominio.StatoElaborazione.FALLITA
import snastro.trascrizione.dominio.StatoElaborazione.IN_ATTESA
import snastro.trascrizione.dominio.StatoElaborazione.IN_CORSO
import snastro.trascrizione.dominio.unaElaborazione
import java.time.Instant
import kotlin.test.assertEquals

/**
 * Consumer-driven contract of [ElaborazioneRepository] (boundary `repo-trascrizione`, ADR 0006/0007): INV-4
 * refused like the partial unique indexes (a second open → [ElaborazioneGiaAperta], a second completata →
 * [ElaborazioneGiaCompletata], store unchanged), `inAttesa` FIFO by creation (ties by id), `salva` as an
 * upsert whose transitions round-trip every field, no aliasing between callers and the store.
 * One subclass per implementation (the Finta here, `ElaborazioneRepositorySql` in `:trascrizione:adattatori`).
 */
public abstract class ElaborazioneRepositoryContratto {
    /** A fresh, empty repository. */
    protected abstract fun repository(): ElaborazioneRepository

    /** Hook for real stores: create the parent rows of every id the contract uses ([PREDISPOSIZIONE]). */
    protected open fun predisponi(predisposizione: PredisposizioneTrascrizione) {}

    private lateinit var repo: ElaborazioneRepository

    @BeforeEach
    public fun preparaRepository() {
        repo = repository()
        predisponi(PREDISPOSIZIONE)
    }

    @Test
    public fun `AC-29 una seconda Elaborazione in attesa per la stessa Registrazione e ElaborazioneGiaAperta`() {
        repo.salva(una(IN_ATTESA, "elaborazione-1")).atteso()

        val errore = repo.salva(una(IN_ATTESA, "elaborazione-2")).erroreAtteso<ElaborazioneGiaAperta>()

        assertEquals(ElaborazioneGiaAperta(REGISTRAZIONE), errore)
        assertEquals(listOf("elaborazione-1"), repo.diRegistrazione(REGISTRAZIONE).map { it.id.valore })
    }

    @Test
    public fun `AC-29 una nuova Elaborazione mentre un altra e in corso e ElaborazioneGiaAperta`() {
        repo.salva(una(IN_CORSO, "elaborazione-1")).atteso()

        repo.salva(una(IN_ATTESA, "elaborazione-2")).erroreAtteso<ElaborazioneGiaAperta>()

        assertEquals(listOf(IN_CORSO), repo.diRegistrazione(REGISTRAZIONE).map { it.stato })
    }

    @Test
    public fun `AC-29 una seconda completata per la stessa Registrazione e ElaborazioneGiaCompletata`() {
        repo.salva(una(COMPLETATA, "elaborazione-1")).atteso()
        val seconda = una(IN_ATTESA, "elaborazione-2", creataAlle = DOPO)
        repo.salva(seconda).atteso()
        seconda.avvia(DOPO).atteso()
        repo.salva(seconda).atteso()
        seconda.completa().atteso()

        val errore = repo.salva(seconda).erroreAtteso<ElaborazioneGiaCompletata>()

        assertEquals(ElaborazioneGiaCompletata(REGISTRAZIONE), errore)
        assertEquals(
            mapOf("elaborazione-1" to COMPLETATA, "elaborazione-2" to IN_CORSO),
            repo.diRegistrazione(REGISTRAZIONE).associate { it.id.valore to it.stato },
        )
    }

    @Test
    public fun `AC-29 dopo una fallita e accettata una nuova Elaborazione e anche una completata`() {
        repo.salva(una(FALLITA, "elaborazione-1")).atteso()
        repo.salva(una(FALLITA, "elaborazione-2", creataAlle = DOPO)).atteso()

        repo.salva(una(COMPLETATA, "elaborazione-3", creataAlle = ANCORA_DOPO)).atteso()

        assertEquals(3, repo.diRegistrazione(REGISTRAZIONE).size)
    }

    @Test
    public fun `AC-29 Registrazioni diverse non si escludono`() {
        repo.salva(una(IN_ATTESA, "elaborazione-1")).atteso()
        repo.salva(una(COMPLETATA, "elaborazione-2", registrazioneId = ALTRA_REGISTRAZIONE)).atteso()

        repo.salva(una(IN_ATTESA, "elaborazione-3", registrazioneId = ALTRA_REGISTRAZIONE)).atteso()

        assertEquals(listOf("elaborazione-1"), repo.diRegistrazione(REGISTRAZIONE).map { it.id.valore })
        assertEquals(
            setOf("elaborazione-2", "elaborazione-3"),
            repo.diRegistrazione(ALTRA_REGISTRAZIONE).map { it.id.valore }.toSet(),
        )
    }

    @Test
    public fun `AC-29 inAttesa in ordine FIFO di creazione e a parita per id`() {
        repo.salva(una(IN_ATTESA, "elaborazione-c", registrazioneId = TERZA_REGISTRAZIONE, creataAlle = DOPO)).atteso()
        repo.salva(una(IN_ATTESA, "elaborazione-b", registrazioneId = ALTRA_REGISTRAZIONE)).atteso()
        repo.salva(una(IN_CORSO, "elaborazione-x", registrazioneId = QUARTA_REGISTRAZIONE, creataAlle = PRIMA)).atteso()
        repo.salva(una(IN_ATTESA, "elaborazione-a")).atteso()

        assertEquals(listOf("elaborazione-a", "elaborazione-b", "elaborazione-c"), repo.inAttesa().map { it.id.valore })
        assertEquals(listOf("elaborazione-x"), repo.inCorso().map { it.id.valore })
    }

    @Test
    public fun `AC-29 salvare una transizione aggiorna la riga esistente con tutti i campi`() {
        val e = una(IN_ATTESA, "elaborazione-1")
        repo.salva(e).atteso()

        e.avvia(DOPO).atteso()
        repo.salva(e).atteso()
        assertEquals(listOf(righe(e)), repo.inCorso().map(::righe))
        assertEquals(emptyList(), repo.inAttesa())

        e.fallisci("Il file audio non si puo leggere").atteso()
        repo.salva(e).atteso()
        assertEquals(listOf(righe(e)), repo.diRegistrazione(REGISTRAZIONE).map(::righe))
        assertEquals(emptyList(), repo.inCorso())
    }

    @Test
    public fun `AC-29 il round-trip conserva ogni campo in ogni stato`() {
        val tutte = StatoElaborazione.entries.mapIndexed { i, stato ->
            una(stato, "elaborazione-$i", registrazioneId = REGISTRAZIONI[i])
        }
        tutte.forEach { repo.salva(it).atteso() }

        tutte.forEach { assertEquals(listOf(righe(it)), repo.diRegistrazione(it.registrazioneId).map(::righe)) }
    }

    @Test
    public fun `AC-29 nessun alias tra l Elaborazione del chiamante e quella salvata`() {
        val e = una(IN_ATTESA, "elaborazione-1")
        repo.salva(e).atteso()

        e.avvia(DOPO).atteso()
        repo.inAttesa().single().avvia(DOPO).atteso()

        assertEquals(listOf(IN_ATTESA), repo.diRegistrazione(REGISTRAZIONE).map { it.stato })
    }

    @Test
    public fun `AC-29 una Registrazione senza Elaborazioni restituisce una lista vuota`() {
        assertEquals(emptyList(), repo.diRegistrazione(REGISTRAZIONE))
        assertEquals(emptyList(), repo.inAttesa())
        assertEquals(emptyList(), repo.inCorso())
    }

    /** The observable state of an [Elaborazione] (the aggregate has no value equality). */
    private fun righe(e: Elaborazione): List<Any?> =
        listOf(e.id, e.registrazioneId, e.creataAlle, e.stato, e.avviataAlle, e.motivoFallimento)

    private fun una(
        stato: StatoElaborazione,
        id: String,
        registrazioneId: RegistrazioneId = REGISTRAZIONE,
        creataAlle: Instant = CREATA,
    ): Elaborazione =
        unaElaborazione(stato, ElaborazioneId(id), registrazioneId, creataAlle, avviataAlle = AVVIATA, motivo = MOTIVO)

    public companion object {
        public val PROGETTO: ProgettoId = ProgettoId("progetto-1")
        public val REGISTRAZIONE: RegistrazioneId = RegistrazioneId("registrazione-1")
        public val ALTRA_REGISTRAZIONE: RegistrazioneId = RegistrazioneId("registrazione-2")
        public val TERZA_REGISTRAZIONE: RegistrazioneId = RegistrazioneId("registrazione-3")
        public val QUARTA_REGISTRAZIONE: RegistrazioneId = RegistrazioneId("registrazione-4")

        /** Every Registrazione the contract uses, all in [PROGETTO]. */
        public val REGISTRAZIONI: List<RegistrazioneId> =
            listOf(REGISTRAZIONE, ALTRA_REGISTRAZIONE, TERZA_REGISTRAZIONE, QUARTA_REGISTRAZIONE)

        public val PREDISPOSIZIONE: PredisposizioneTrascrizione =
            PredisposizioneTrascrizione(setOf(PROGETTO), REGISTRAZIONI.associateWith { PROGETTO })

        // Millisecond precision: what a store keeps of an Instant.
        private val PRIMA = Instant.parse("2026-09-23T09:00:00Z")
        private val CREATA = Instant.parse("2026-09-23T10:00:00.123Z")
        private val AVVIATA = Instant.parse("2026-09-23T10:05:00.456Z")
        private val DOPO = Instant.parse("2026-09-23T11:00:00Z")
        private val ANCORA_DOPO = Instant.parse("2026-09-23T12:00:00Z")
        private const val MOTIVO = "Il file audio non si puo leggere"
    }
}
