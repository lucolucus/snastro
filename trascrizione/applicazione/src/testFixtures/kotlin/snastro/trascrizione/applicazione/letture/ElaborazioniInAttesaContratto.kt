package snastro.trascrizione.applicazione.letture

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import snastro.kernel.ElaborazioneId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.atteso
import snastro.trascrizione.applicazione.porte.ElaborazioneRepository
import snastro.trascrizione.applicazione.porte.PredisposizioneTrascrizione
import snastro.trascrizione.dominio.StatoElaborazione.COMPLETATA
import snastro.trascrizione.dominio.StatoElaborazione.FALLITA
import snastro.trascrizione.dominio.StatoElaborazione.IN_ATTESA
import snastro.trascrizione.dominio.StatoElaborazione.IN_CORSO
import snastro.trascrizione.dominio.unaElaborazione
import java.time.Instant
import kotlin.test.assertEquals

/**
 * Consumer-driven contract of [ElaborazioniInAttesa] (A41, boundary `elaborazioni-in-coda`, ADR 0023 §1,
 * consumer `avvio-coda-condivisa`): [ElaborazioniInAttesa.elenco] carries only what the shared queue needs —
 * `in_attesa` rows, FIFO, primitive ids. ONE subclass per implementation (the Finta here,
 * `ElaborazioneRepositorySql` in `:avvio` — so `avvio-coda-condivisa` re-runs this SAME FIFO/id-order proof
 * end to end over the real store, instead of trusting only a Finta-backed test it cannot reach).
 */
public abstract class ElaborazioniInAttesaContratto {
    /** A fresh, empty repository. */
    protected abstract fun repository(): ElaborazioneRepository

    /** Hook for real stores: create the parent rows of every id the contract uses ([PREDISPOSIZIONE]). */
    protected open fun predisponi(predisposizione: PredisposizioneTrascrizione) {}

    private lateinit var elaborazioni: ElaborazioneRepository
    private lateinit var coda: ElaborazioniInAttesa

    @BeforeEach
    public fun preparaCoda() {
        elaborazioni = repository()
        coda = ElaborazioniInAttesa(elaborazioni)
        predisponi(PREDISPOSIZIONE)
    }

    @Test
    public fun `AC-S23 elenca solo le in_attesa, FIFO per creataAlle poi id, con elaborazioneId come stringa`() {
        val prima = RegistrazioneId("registrazione-1")
        val seconda = RegistrazioneId("registrazione-2")
        // salvate fuori ordine: l'elenco deve comunque uscire FIFO
        elaborazioni.salva(unaElaborazione(IN_ATTESA, idDi("el-2"), seconda, creataAlle = t(1))).atteso()
        elaborazioni.salva(unaElaborazione(IN_ATTESA, idDi("el-1"), prima, creataAlle = t(0))).atteso()
        elaborazioni.salva(unaElaborazione(IN_CORSO, idDi("el-corso"), RegistrazioneId("registrazione-corso")))
            .atteso()
        elaborazioni.salva(unaElaborazione(COMPLETATA, idDi("el-completata"), RegistrazioneId("registrazione-c")))
            .atteso()
        elaborazioni.salva(unaElaborazione(FALLITA, idDi("el-fallita"), RegistrazioneId("registrazione-f"))).atteso()

        val elenco = coda.elenco()

        assertEquals(
            listOf(
                ElaborazioneInCoda("el-1", prima, t(0)),
                ElaborazioneInCoda("el-2", seconda, t(1)),
            ),
            elenco,
            "solo le in_attesa, in ordine FIFO — in_corso/completata/fallita non appaiono",
        )
    }

    @Test
    public fun `AC-S23 a parita di creataAlle il pareggio e per id, come inAttesa del repository`() {
        elaborazioni.salva(
            unaElaborazione(IN_ATTESA, idDi("el-b"), RegistrazioneId("registrazione-b"), creataAlle = t(0)),
        ).atteso()
        elaborazioni.salva(
            unaElaborazione(IN_ATTESA, idDi("el-a"), RegistrazioneId("registrazione-a"), creataAlle = t(0)),
        ).atteso()

        assertEquals(listOf("el-a", "el-b"), coda.elenco().map { it.elaborazioneId })
    }

    @Test
    public fun `AC-S23 un annullamento (rimuoviInAttesa) fa sparire la riga dall elenco`() {
        val registrazione = RegistrazioneId("registrazione-1")
        elaborazioni.salva(unaElaborazione(IN_ATTESA, idDi("el-1"), registrazione, creataAlle = t(0))).atteso()
        assertEquals(1, coda.elenco().size)

        elaborazioni.rimuoviInAttesa(idDi("el-1")).atteso()

        assertEquals(emptyList(), coda.elenco())
    }

    public companion object {
        public val PROGETTO: ProgettoId = ProgettoId("progetto-elaborazioni-in-attesa")

        private val REGISTRAZIONI: List<RegistrazioneId> = listOf(
            "registrazione-1", "registrazione-2", "registrazione-corso", "registrazione-c", "registrazione-f",
            "registrazione-a", "registrazione-b",
        ).map(::RegistrazioneId)

        public val PREDISPOSIZIONE: PredisposizioneTrascrizione =
            PredisposizioneTrascrizione(setOf(PROGETTO), REGISTRAZIONI.associateWith { PROGETTO })

        private fun idDi(valore: String) = ElaborazioneId(valore)
        private fun t(secondi: Long): Instant = Instant.parse("2026-09-23T10:00:00Z").plusSeconds(secondi)
    }
}
