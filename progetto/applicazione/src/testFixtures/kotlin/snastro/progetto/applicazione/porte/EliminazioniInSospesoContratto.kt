package snastro.progetto.applicazione.porte

import org.junit.jupiter.api.Test
import snastro.kernel.ErroreDiProva
import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.kernel.UnitaDiLavoro
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import java.time.LocalDate
import kotlin.test.assertEquals

/**
 * Contract of [EliminazioniInSospeso] (boundary `repo-progetto`, ADR 0020 §4). One subclass per implementation
 * (`EliminazioniInSospesoFinta` here, the SQL one in `:progetto:adattatori`). The [Ambiente] of a real store must
 * register each row at a strictly later time than the previous one (e.g. a ticking Clock), so that "by registration
 * time" is observable; ties by id are the store's own concern.
 */
public abstract class EliminazioniInSospesoContratto {
    /** A fresh, empty project database. */
    public interface Ambiente {
        public val inSospeso: EliminazioniInSospeso
        public val unitaDiLavoro: UnitaDiLavoro
    }

    protected abstract fun ambiente(): Ambiente

    @Test
    public fun `AC-615 registra poi elenco contiene la riga con gli stessi quattro valori`() {
        val a = ambiente()
        val e = unaEliminazione("reg-1")

        a.inTransazione { registra(e) }

        assertEquals(listOf(e), a.inSospeso.elenco())
    }

    @Test
    public fun `AC-615 concludi toglie la riga e lascia le altre`() {
        val a = ambiente()
        a.inTransazione { registra(unaEliminazione("reg-1")) }
        a.inTransazione { registra(unaEliminazione("reg-2")) }

        a.inTransazione { concludi(RegistrazioneId("reg-1")) }

        assertEquals(listOf(unaEliminazione("reg-2")), a.inSospeso.elenco())
    }

    @Test
    public fun `AC-615 concludi di un id sconosciuto non fa nulla`() {
        val a = ambiente()
        a.inTransazione { registra(unaEliminazione("reg-1")) }

        a.inTransazione { concludi(RegistrazioneId("sconosciuta")) }

        assertEquals(listOf(unaEliminazione("reg-1")), a.inSospeso.elenco())
    }

    @Test
    public fun `AC-615 elenco e ordinato per momento di registrazione`() {
        val a = ambiente()
        listOf("reg-b", "reg-c", "reg-a").forEach { id -> a.inTransazione { registra(unaEliminazione(id)) } }

        assertEquals(listOf("reg-b", "reg-c", "reg-a"), a.inSospeso.elenco().map { it.registrazioneId.valore })
    }

    @Test
    public fun `AC-615 un registra annullato dalla transazione non lascia traccia`() {
        val a = ambiente()

        a.unitaDiLavoro.inTransazione<Unit> {
            a.inSospeso.registra(unaEliminazione("reg-1"))
            Esito.Errore(ErroreDiProva.Fallito("annullato"))
        }.erroreAtteso<ErroreDiProva.Fallito>()

        assertEquals(emptyList(), a.inSospeso.elenco())
    }

    private fun Ambiente.inTransazione(azione: EliminazioniInSospeso.() -> Unit) {
        unitaDiLavoro.inTransazione {
            inSospeso.azione()
            Esito.Ok(Unit)
        }.atteso()
    }

    private fun unaEliminazione(id: String) = EliminazioneInSospeso(
        registrazioneId = RegistrazioneId(id),
        titolo = "Seduta $id",
        dataRegistrazione = LocalDate.of(2026, 9, 25),
        riferimentoAudio = RiferimentoAudio("audio/$id.m4a"),
    )
}
