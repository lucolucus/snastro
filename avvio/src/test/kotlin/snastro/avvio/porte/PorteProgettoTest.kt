package snastro.avvio.porte

import org.junit.jupiter.api.io.TempDir
import snastro.avvio.r3.AmbienteR3
import snastro.kernel.Esito
import snastro.supporto.test.attendiFinche
import snastro.trascrizione.applicazione.letture.StatoElaborazioneVista
import snastro.trascrizione.applicazione.porte.FaseElaborazione
import java.io.File
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * ADR 0030 §1 (block `c1-porte-progetto`): AC-C60..AC-C64 — [PorteProgetto] is the ONE place `avvio/src/main`
 * builds the Progetto/Trascrizione SQL repositories and `CatalogoRegistrazioni`, and the ONE `StatiElaborazione`
 * (over the ONE `FasiInCorso`) S2 (R1) and Sintesi's cross-context read (R3) both read.
 *
 * The static half (AC-C60, AC-C63 structural) mirrors [snastro.avvio.r1.CablaggioR1Test]'s/`GrafoR0Test`'s own
 * style: a fact proven on the source text, so reintroducing a duplicate construction site (or R3's old fake
 * `FasiInCorso()`) fails HERE, not by chance elsewhere. The dynamic half (AC-C62, AC-C63) runs the REAL R3
 * composition ([AmbienteR3]: SQLite project file, real queue, real subscribers).
 */
class PorteProgettoTest {
    @TempDir
    lateinit var radice: Path

    private val sorgenti: List<File> =
        File("src/main/kotlin").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    /** Every `avvio/src/main` line containing [testo], OUTSIDE `snastro/avvio/porte/` (comments excluded). */
    private fun fuoriDaPorteCon(testo: String): List<String> = sorgenti
        .filterNot { it.path.contains("snastro/avvio/porte/") }
        .flatMap { file -> file.readLines().mapIndexed { i, riga -> Triple(file, i + 1, riga) } }
        .filter { (_, _, riga) ->
            val codice = riga.trimStart()
            val eCommento = codice.startsWith("*") || codice.startsWith("//") || codice.startsWith("/*")
            !eCommento && testo in riga
        }
        .map { (file, n, riga) -> "${file.name}:$n: ${riga.trim()}" }

    @Test
    fun `AC-C60 i repository di Progetto Trascrizione e CatalogoRegistrazioni sono costruiti solo in PorteProgetto`() {
        assertTrue(sorgenti.any { it.name == "PorteProgetto.kt" }, "la guardia deve vedere PorteProgetto")
        assertEquals(emptyList(), fuoriDaPorteCon("TrascrittoRepositorySql("))
        assertEquals(emptyList(), fuoriDaPorteCon("ElaborazioneRepositorySql("))
        assertEquals(emptyList(), fuoriDaPorteCon("ProgettoRepositorySql("))
        assertEquals(emptyList(), fuoriDaPorteCon("CatalogoRegistrazioni("))
    }

    @Test
    fun `AC-C63 EstensioneR3 non costruisce piu' un FasiInCorso() proprio, usa porte statiElaborazione`() {
        val righe = File("src/main/kotlin/snastro/avvio/r3/EstensioneR3.kt").readLines()
        val codice = righe.filterNot { riga ->
            val t = riga.trimStart()
            t.startsWith("*") || t.startsWith("//") || t.startsWith("/*")
        }
        assertTrue(codice.none { "FasiInCorso(" in it }, "nessun FasiInCorso() locale — deve venire da porte")
        assertTrue(
            codice.any { "porte.statiElaborazione" in it },
            "il lettore Sintesi deve leggere porte.statiElaborazione",
        )
    }

    @Test
    fun `AC-C62 contesto lettura e il delegato del dispatcher sono la stessa UnitaDiLavoroSql`() {
        AmbienteR3(radice).use { ambiente ->
            val contesto = ambiente.contesto
            // ADR 0029 §2 regola 2: una inLettura annidata in una inTransazione si unisce e vede la scrittura non
            // committata — possibile SOLO se contesto.lettura e il delegato di dispatcher.unitaDiLavoro sono la
            // STESSA istanza (lo stato di nesting/modo e' un ThreadLocal per-istanza, mai condiviso tra due
            // UnitaDiLavoroSql distinte). Con due istanze diverse questa chiamata lancerebbe IllegalStateException
            // ("noEnclosing") invece di restituire Ok.
            val esito = contesto.dispatcher.unitaDiLavoro.inTransazione {
                Esito.Ok(contesto.lettura.inLettura { "raggiunta" })
            }
            assertEquals(Esito.Ok("raggiunta"), esito)
        }
    }

    @Test
    fun `AC-C63 durante un'Elaborazione IN_CORSO porte statiElaborazione riporta la stessa fase di S2`() {
        AmbienteR3(radice).use { ambiente ->
            val barriera = CountDownLatch(1)
            ambiente.diarizzatore.barriera = barriera
            val id = try {
                val id = ambiente.importaEAvvia()
                attendiFinche(timeout = 10.seconds, messaggio = "S2 IN_CORSO (DIARIZZAZIONE)") {
                    ambiente.stato(id) == StatoElaborazioneVista.IN_CORSO
                }
                val rigaS2 = ambiente.r3.r2.r1.statiElaborazione(listOf(id)).single()
                val rigaPorte = ambiente.contesto.porte.statiElaborazione.stati(listOf(id)).single()
                assertEquals(FaseElaborazione.DIARIZZAZIONE, rigaS2.fase)
                assertEquals(rigaS2, rigaPorte, "S2 e porte.statiElaborazione devono riportare la stessa riga")
                assertNotNull(rigaPorte.fase)
                id
            } finally {
                barriera.countDown()
            }
            attendiFinche(timeout = 10.seconds, messaggio = "elaborazione completata dopo il rilascio della barriera") {
                ambiente.stato(id) == StatoElaborazioneVista.COMPLETATA
            }
        }
    }
}
