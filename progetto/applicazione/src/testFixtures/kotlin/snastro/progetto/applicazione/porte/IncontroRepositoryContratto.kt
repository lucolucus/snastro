package snastro.progetto.applicazione.porte

import org.junit.jupiter.api.Test
import snastro.kernel.ErroreDiProva
import snastro.kernel.Esito
import snastro.kernel.IncontroId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.kernel.UnitaDiLavoro
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.progetto.dominio.Incontro
import snastro.progetto.dominio.Registrazione
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Contract of [IncontroRepository] (boundary `repo-incontro`, AC-I18). One subclass per implementation
 * (`IncontroRepositoryFinta` here, the SQL adapter in `:progetto:adattatori`). The Parti are seeded through
 * [RegistrazioneRepository] on the same state.
 */
public abstract class IncontroRepositoryContratto {
    /** A fresh project database that already holds the Progetto [progettoId], with no Incontro and no Registrazione. */
    public interface Ambiente {
        public val incontri: IncontroRepository
        public val registrazioni: RegistrazioneRepository
        public val unitaDiLavoro: UnitaDiLavoro
        public val progettoId: ProgettoId
    }

    protected abstract fun ambiente(): Ambiente

    @Test
    public fun `AC-I18 un Incontro mai salvato non si trova`() {
        assertNull(ambiente().incontri.trova(IncontroId("incontro-sconosciuto")))
    }

    @Test
    public fun `AC-I18 salva poi trova restituisce lo stesso Incontro`() {
        val a = ambiente()
        val incontro = Incontro.nuovo(IncontroId("incontro-a"), a.progettoId)
        a.inTransazione { incontri.salva(incontro) }
        val trovato = assertNotNull(a.incontri.trova(incontro.id))
        assertEquals(incontro.id, trovato.id)
        assertEquals(a.progettoId, trovato.progettoId)
        assertNull(a.incontri.trova(IncontroId("incontro-b")))
    }

    @Test
    public fun `AC-I18 salvare di nuovo lo stesso Incontro non cambia nulla`() {
        val a = ambiente()
        val incontro = Incontro.nuovo(IncontroId("incontro-a"), a.progettoId)
        a.inTransazione { incontri.salva(incontro) }
        a.parte(incontro.id, "id-2")
        a.inTransazione { incontri.salva(incontro) }
        assertEquals(a.progettoId, assertNotNull(a.incontri.trova(incontro.id)).progettoId)
        assertEquals(listOf(RegistrazioneId("id-2")), a.incontri.partiDi(incontro.id))
    }

    @Test
    public fun `AC-I18 partiDi elenca tutte e sole le Registrazioni dell'Incontro`() {
        val a = ambiente()
        val incontro = a.nuovoIncontro("incontro-a")
        val altro = a.nuovoIncontro("incontro-b")
        val parti = PARTI.map { a.parte(incontro, it) }
        val parteDellAltro = a.parte(altro, "id-9")

        assertEquals(parti.toSet(), a.incontri.partiDi(incontro).toSet())
        assertEquals(parti.size, a.incontri.partiDi(incontro).size)
        assertEquals(listOf(parteDellAltro), a.incontri.partiDi(altro))
        assertEquals(emptyList(), a.incontri.partiDi(IncontroId("incontro-sconosciuto")))
    }

    @Test
    public fun `AC-I18 partiDi non elenca piu una Parte rimossa`() {
        val a = ambiente()
        val incontro = a.nuovoIncontro("incontro-a")
        val parti = PARTI.map { a.parte(incontro, it) }
        a.inTransazione { registrazioni.rimuovi(parti.first()) }
        assertEquals(parti.drop(1).toSet(), a.incontri.partiDi(incontro).toSet())
    }

    @Test
    public fun `AC-I18 rimuovi di un Incontro che ha ancora una Parte e rifiutato e l'Incontro resta`() {
        val a = ambiente()
        val incontro = a.nuovoIncontro("incontro-a")
        val parti = PARTI.map { a.parte(incontro, it) }
        // One of the two Parti is removed first: the refusal holds while ANY Parte is left, not only the last.
        a.inTransazione { registrazioni.rimuovi(parti.first()) }

        val rifiuto = assertFails { a.inTransazione { incontri.rimuovi(incontro) } }

        // The FK refusal itself, not any failure (the Finta raises it like SQLite does).
        val messaggi = generateSequence(rifiuto) { it.cause }.mapNotNull { it.message }.toList()
        assertTrue(messaggi.any { "FOREIGN KEY constraint failed" in it }, "$messaggi")

        assertNotNull(a.incontri.trova(incontro))
        assertEquals(listOf(parti.last()), a.incontri.partiDi(incontro))
    }

    @Test
    public fun `AC-I18 rimuovi dopo l'ultima Parte toglie l'Incontro e lascia gli altri`() {
        val a = ambiente()
        val incontro = a.nuovoIncontro("incontro-a")
        val altro = a.nuovoIncontro("incontro-b")
        val parte = a.parte(incontro, "id-2")
        a.parte(altro, "id-3")

        a.inTransazione {
            registrazioni.rimuovi(parte)
            incontri.rimuovi(incontro)
        }

        assertNull(a.incontri.trova(incontro))
        assertEquals(emptyList(), a.incontri.partiDi(incontro))
        assertNotNull(a.incontri.trova(altro))
    }

    @Test
    public fun `AC-I18 rimuovi di un id sconosciuto non fa nulla`() {
        val a = ambiente()
        val incontro = a.nuovoIncontro("incontro-a")
        a.inTransazione { incontri.rimuovi(IncontroId("incontro-sconosciuto")) }
        assertNotNull(a.incontri.trova(incontro))
    }

    @Test
    public fun `AC-I18 un salva annullato dalla transazione non lascia traccia`() {
        val a = ambiente()
        val incontro = Incontro.nuovo(IncontroId("incontro-a"), a.progettoId)
        a.unitaDiLavoro.inTransazione<Unit> {
            a.incontri.salva(incontro)
            Esito.Errore(ErroreDiProva.Fallito("annullato"))
        }.erroreAtteso<ErroreDiProva.Fallito>()
        assertNull(a.incontri.trova(incontro.id))
    }

    private companion object {
        /** Two Parti of one Incontro. */
        val PARTI = listOf("id-2", "id-3")
    }

    private fun Ambiente.nuovoIncontro(id: String): IncontroId {
        val incontro = Incontro.nuovo(IncontroId(id), progettoId)
        inTransazione { incontri.salva(incontro) }
        return incontro.id
    }

    private fun Ambiente.parte(incontroId: IncontroId, id: String): RegistrazioneId {
        val r = Registrazione.aggiungi(
            id = RegistrazioneId(id),
            progettoId = progettoId,
            incontroId = incontroId,
            titolo = "Parte $id",
            riferimentoAudio = RiferimentoAudio("audio/$id.m4a"),
            durataMs = 3_600_000,
            dataRegistrazione = LocalDate.of(2026, 2, 12),
            aggiuntaAlle = Instant.parse("2026-09-23T10:15:30.123Z"),
        ).aggregato
        inTransazione { registrazioni.salva(r) }
        return r.id
    }

    private fun Ambiente.inTransazione(blocco: Ambiente.() -> Unit) {
        unitaDiLavoro.inTransazione {
            blocco()
            Esito.Ok(Unit)
        }.atteso()
    }
}
