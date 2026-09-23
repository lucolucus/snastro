package snastro.progetto.applicazione.porte

import org.junit.jupiter.api.Test
import snastro.kernel.ProgettoId
import java.time.Instant
import kotlin.test.assertEquals

/**
 * Contract of [RegistroProgetti] (boundary `tec-registro-progetti`): the per-user list of known
 * projects, keyed by `percorso`. One subclass per implementation (`RegistroProgettiFinta` here, the
 * app-data file registry in `:progetto:adattatori`).
 */
public abstract class RegistroProgettiContratto {
    /** A fresh, empty registry. */
    protected abstract fun registro(): RegistroProgetti

    @Test
    public fun `AC-28 registra poi elenco contiene la voce`() {
        val registro = registro()
        val voce = unaVoce()
        registro.registra(voce)
        assertEquals(listOf(voce), registro.elenco())
    }

    @Test
    public fun `AC-28 registrare due volte lo stesso percorso non duplica e vale l ultima registrazione`() {
        val registro = registro()
        registro.registra(unaVoce())
        val riaperta = unaVoce(nome = "Consiglio comunale 2026", ultimaAttivita = ORA.plusSeconds(60))
        registro.registra(riaperta)
        assertEquals(listOf(riaperta), registro.elenco())
    }

    @Test
    public fun `AC-28 aggiorna cambia numRegistrazioni e ultimaAttivita solo del Progetto indicato`() {
        val registro = registro()
        val altra = unaVoce(ProgettoId("id-2"), "Assemblea", "/progetti/Assemblea.snastro", ORA.minusSeconds(60))
        registro.registra(unaVoce())
        registro.registra(altra)
        registro.aggiorna(ProgettoId("id-1"), numRegistrazioni = 3, ultimaAttivita = ORA.plusSeconds(120))
        assertEquals(
            listOf(unaVoce().copy(numRegistrazioni = 3, ultimaAttivita = ORA.plusSeconds(120)), altra),
            registro.elenco(),
        )
    }

    @Test
    public fun `AC-28 elenco e ordinato per ultimaAttivita decrescente`() {
        val registro = registro()
        val vecchia = unaVoce(ProgettoId("id-1"), "Vecchio", "/progetti/Vecchio.snastro", ORA.minusSeconds(3_600))
        val recente = unaVoce(ProgettoId("id-2"), "Recente", "/progetti/Recente.snastro", ORA)
        val media = unaVoce(ProgettoId("id-3"), "Medio", "/progetti/Medio.snastro", ORA.minusSeconds(60))
        listOf(vecchia, recente, media).forEach(registro::registra)
        assertEquals(listOf(recente, media, vecchia), registro.elenco())
    }

    @Test
    public fun `AC-28 rimuovi toglie la voce di quel percorso`() {
        val registro = registro()
        val altra = unaVoce(ProgettoId("id-2"), "Assemblea", "/progetti/Assemblea.snastro", ORA.minusSeconds(60))
        registro.registra(unaVoce())
        registro.registra(altra)
        registro.rimuovi(PERCORSO)
        assertEquals(listOf(altra), registro.elenco())
    }

    private fun unaVoce(
        progettoId: ProgettoId = ProgettoId("id-1"),
        nome: String = "Consiglio comunale",
        percorso: String = PERCORSO,
        ultimaAttivita: Instant = ORA,
    ): VoceRegistro = VoceRegistro(progettoId, nome, percorso, numRegistrazioni = 1, ultimaAttivita = ultimaAttivita)

    private companion object {
        const val PERCORSO = "/progetti/Consiglio comunale.snastro"
        val ORA: Instant = Instant.parse("2026-09-23T10:15:30Z")
    }
}
