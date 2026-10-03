package snastro.avvio.progetto

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.io.TempDir
import snastro.kernel.Esito
import snastro.kernel.atteso
import snastro.parlanti.adattatori.persistenza.AttribuzioneRepositorySql
import snastro.parlanti.adattatori.persistenza.ParlanteRepositorySql
import snastro.progetto.applicazione.comandi.EliminaRegistrazione
import snastro.ui.registrazione.ComandoVoce
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * AC-I12 (incontro-chiavi, ADR 0033 §4.1/§6) end-to-end on the REAL composition and SQLite: transcribe, name a Voce,
 * Riassumi and Elimina on a one-Parte Incontro whose id differs from the Registrazione's. Every row is keyed by the
 * Registrazione's incontroId, read back through the repositories alone (no join), and the views are unchanged.
 */
class ChiaviIncontroTest {
    @TempDir
    lateinit var radice: Path

    @Test
    fun `AC-I12 trascrivi, nomina, riassumi ed elimina scrivono e leggono le righe con l incontroId della Parte`() {
        AmbienteProgetto(radice).use {
            val r = it.registrazioneTrascritta()
            val incontro = it.incontroDi(r)
            assertNotEquals(r.valore, incontro.valore, "l'import conia un Incontro con un suo id")
            val trascritto = assertNotNull(it.trascrizione.trascritto(r))
            assertEquals(incontro, trascritto.incontroId)

            assertEquals(
                Esito.Ok(Unit),
                runBlocking { it.parlanti.comandi.esegui(ComandoVoce.Nuovo(voce(r, 1), "Anna")) },
            )
            val attribuzione = AttribuzioneRepositorySql(it.porte.database).diIncontro(incontro).single()
            assertEquals(incontro, attribuzione.voceRef.incontroId)
            val impronta = ParlanteRepositorySql(
                it.porte.database,
                it.porte.lettura,
            ).impronteDiRegistrazione(r).single()
            assertEquals(r, impronta.parte, "l'impronta registra la Parte da cui e estratta")
            assertEquals("Anna", it.parlanti.letture.identificazione(r).single { v -> v.voceId.numero == 1 }.nome)

            it.riassumi(r)
            it.attendiPronto(r)
            val riassunto = it.porte.riassunti.trova(incontro).single()
            assertEquals(incontro, riassunto.incontroId)
            val struttura = assertNotNull(riassunto.strutturaRegistrata)
            assertTrue(
                struttura.startsWith("${r.valore}=") && ';' !in struttura,
                "il Riassunto registra la struttura della sua unica Parte: $struttura",
            )
            assertEquals(false, assertNotNull(it.sintesi.vista(r)?.mostrato).superato)

            it.collaboratori.eliminaRegistrazione(EliminaRegistrazione(r)).atteso()

            assertEquals(emptyList(), it.porte.riassunti.trova(incontro))
            assertEquals(emptyList(), AttribuzioneRepositorySql(it.porte.database).diIncontro(incontro))
            assertEquals(null, it.porte.catalogo.parti(incontro), "l'Incontro cessa con la sua unica Parte")
        }
    }
}
