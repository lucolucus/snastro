package snastro.progetto.applicazione.porte

import org.junit.jupiter.api.Test
import snastro.kernel.ErroreDiProva
import snastro.kernel.Esito
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.kernel.UnitaDiLavoro
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.progetto.dominio.Registrazione
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Contract of [RegistrazioneRepository] (boundary `repo-progetto`). One subclass per implementation
 * (`RegistrazioneRepositoryFinta` here, `RegistrazioneRepositorySql` in `:progetto:adattatori`).
 */
public abstract class RegistrazioneRepositoryContratto {
    /** A fresh project database that already holds the Progetto [progettoId] and no Registrazione. */
    public interface Ambiente {
        public val registrazioni: RegistrazioneRepository
        public val unitaDiLavoro: UnitaDiLavoro
        public val progettoId: ProgettoId
    }

    protected abstract fun ambiente(): Ambiente

    @Test
    public fun `AC-25 una Registrazione mai salvata non si trova`() {
        assertNull(ambiente().registrazioni.trova(RegistrazioneId("id-9")))
    }

    @Test
    public fun `AC-25 salva poi trova restituisce la stessa Registrazione`() {
        val a = ambiente()
        val r = unaRegistrazione(a.progettoId)
        a.salva(r)
        assertEquals(r.stato(), assertNotNull(a.registrazioni.trova(r.id)).stato())
    }

    @Test
    public fun `AC-25 salvare una Registrazione modificata ne aggiorna lo stato senza duplicarla`() {
        val a = ambiente()
        val r = unaRegistrazione(a.progettoId)
        a.salva(r)
        r.modificaData(LocalDate.of(2026, 3, 1)).atteso()
        a.salva(r)
        assertEquals(LocalDate.of(2026, 3, 1), assertNotNull(a.registrazioni.trova(r.id)).dataRegistrazione)
        assertEquals(listOf(r.id), a.registrazioni.delProgetto(a.progettoId).map { it.id })
    }

    @Test
    public fun `AC-361 salvare una Registrazione rinominata ne persiste il titolo senza toccare il resto`() {
        val a = ambiente()
        val r = unaRegistrazione(a.progettoId)
        a.salva(r)
        r.rinomina("Consiglio di marzo").atteso()
        r.modificaData(LocalDate.of(2026, 3, 1)).atteso()
        a.salva(r)
        assertEquals(r.stato(), assertNotNull(a.registrazioni.trova(r.id)).stato())
        assertEquals(listOf("Consiglio di marzo"), a.registrazioni.titoliDelProgetto(a.progettoId))
    }

    @Test
    public fun `AC-25 una modifica non salvata non cambia lo stato persistito`() {
        val a = ambiente()
        val r = unaRegistrazione(a.progettoId)
        a.salva(r)
        r.modificaData(LocalDate.of(2026, 3, 1)).atteso()
        assertEquals(DATA_DEL_FILE, assertNotNull(a.registrazioni.trova(r.id)).dataRegistrazione)
    }

    @Test
    public fun `AC-25 delProgetto restituisce tutte e sole le Registrazioni del Progetto`() {
        val a = ambiente()
        val prima = unaRegistrazione(a.progettoId, RegistrazioneId("id-2"), "Seduta di marzo")
        val seconda = unaRegistrazione(a.progettoId, RegistrazioneId("id-3"), "Seduta di aprile")
        a.salva(prima)
        a.salva(seconda)
        assertEquals(
            setOf(prima.stato(), seconda.stato()),
            a.registrazioni.delProgetto(a.progettoId).map { it.stato() }.toSet(),
        )
        assertEquals(emptyList(), a.registrazioni.delProgetto(ProgettoId("id-sconosciuto")))
    }

    @Test
    public fun `AC-325 titoliDelProgetto restituisce i titoli di tutte e sole le Registrazioni del Progetto`() {
        val a = ambiente()
        a.salva(unaRegistrazione(a.progettoId, RegistrazioneId("id-2"), "Seduta di marzo"))
        a.salva(unaRegistrazione(a.progettoId, RegistrazioneId("id-3"), "Seduta di aprile"))
        a.salva(unaRegistrazione(a.progettoId, RegistrazioneId("id-4"), "Seduta di marzo (2)"))
        assertEquals(
            listOf("Seduta di aprile", "Seduta di marzo", "Seduta di marzo (2)"),
            a.registrazioni.titoliDelProgetto(a.progettoId).sorted(),
        )
        assertEquals(emptyList(), a.registrazioni.titoliDelProgetto(ProgettoId("id-sconosciuto")))
    }

    @Test
    public fun `AC-325 titoliDelProgetto senza Registrazioni restituisce una lista vuota`() {
        val a = ambiente()
        assertEquals(emptyList(), a.registrazioni.titoliDelProgetto(a.progettoId))
    }

    @Test
    public fun `AC-25 un salva annullato dalla transazione non lascia traccia`() {
        val a = ambiente()
        val r = unaRegistrazione(a.progettoId)
        a.unitaDiLavoro.inTransazione<Unit> {
            a.registrazioni.salva(r)
            Esito.Errore(ErroreDiProva.Fallito("annullato"))
        }.erroreAtteso<ErroreDiProva.Fallito>()
        assertNull(a.registrazioni.trova(r.id))
        assertEquals(emptyList(), a.registrazioni.delProgetto(a.progettoId))
        assertEquals(emptyList(), a.registrazioni.titoliDelProgetto(a.progettoId))
    }

    private fun Ambiente.salva(r: Registrazione) {
        unitaDiLavoro.inTransazione {
            registrazioni.salva(r)
            Esito.Ok(Unit)
        }.atteso()
    }

    private fun unaRegistrazione(
        progettoId: ProgettoId,
        id: RegistrazioneId = RegistrazioneId("id-2"),
        titolo: String = "Seduta di marzo",
    ): Registrazione =
        Registrazione.aggiungi(
            id = id,
            progettoId = progettoId,
            titolo = titolo,
            riferimentoAudio = RiferimentoAudio("audio/${id.valore}.m4a"),
            durataMs = 3_600_000,
            dataRegistrazione = DATA_DEL_FILE,
            aggiuntaAlle = Instant.parse("2026-09-23T10:15:30.123Z"),
        ).aggregato

    /** Every observable field of a Registrazione (the aggregate has no value equality). */
    private fun Registrazione.stato(): List<Any> =
        listOf(id, progettoId, titolo, riferimentoAudio, durataMs, dataRegistrazione, aggiuntaAlle)

    private companion object {
        val DATA_DEL_FILE: LocalDate = LocalDate.of(2026, 2, 12)
    }
}
