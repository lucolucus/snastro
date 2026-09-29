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
import kotlin.jvm.internal.CallableReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * ADR 0030 §1 (block `c1-porte-progetto`): AC-C60..AC-C64 — [PorteProgetto] (with the per-context parts it owns,
 * `PorteProgettoParlanti`/`PorteProgettoSintesi`) is the ONE place `avvio/src/main` builds an SQL repository and
 * `CatalogoRegistrazioni`, each ONCE per open project, and every R1/R2/R3 consumer receives that same instance.
 *
 * - Static half: the source text (AC-C60's grep over every `RepositorySql(`, AC-C63's no local `FasiInCorso(`).
 * - Counting half: [SondaCostruzioni] in [AmbienteR3] observes every constructor the REAL R3 composition runs while
 *   the project is created (and re-opened): how many instances, and which instance each consumer received.
 * - Behavioural half: AC-C62 (one UnitaDiLavoroSql) and AC-C63 (one live phase) on the real composition.
 */
class PorteProgettoTest {
    @TempDir
    lateinit var radice: Path

    private val sorgenti: List<File> =
        File("src/main/kotlin").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    /** Every code line (comments excluded) of the [sorgenti] files matching [regola], as `file:line: text`. */
    private fun righe(regola: Regex, dove: List<File>): List<String> = dove
        .flatMap { file -> file.readLines().mapIndexed { i, riga -> Triple(file, i + 1, riga) } }
        .filter { (_, _, riga) ->
            val codice = riga.trimStart()
            val eCommento = codice.startsWith("*") || codice.startsWith("//") || codice.startsWith("/*")
            !eCommento && regola.containsMatchIn(riga)
        }
        .map { (file, n, riga) -> "${file.name}:$n: ${riga.trim()}" }

    /** `PorteProgetto.kt` and the per-context parts it owns (`PorteProgettoParlanti.kt`, `PorteProgettoSintesi.kt`). */
    private fun diPorteProgetto(file: File) = file.name.startsWith("PorteProgetto")

    @Test
    fun `AC-C60 RepositorySql( compare in avvio src main solo in PorteProgetto e nelle sue parti per contesto`() {
        val fuori = sorgenti.filterNot(::diPorteProgetto)
        assertTrue(sorgenti.any { it.name == "PorteProgetto.kt" }, "la guardia deve vedere PorteProgetto")
        assertTrue(fuori.any { it.name == "EstensioneR3.kt" }, "la guardia deve vedere le estensioni")
        // A constructor call, a constructor reference and any SQL adapter of a context's persistence package.
        assertEquals(emptyList(), righe(Regex("""RepositorySql\s*\("""), fuori))
        assertEquals(emptyList(), righe(Regex("""::\s*\w*RepositorySql\b"""), fuori))
        assertEquals(emptyList(), righe(Regex("""\.adattatori\.persistenza\."""), fuori))
        assertEquals(emptyList(), righe(Regex("""\bCatalogoRegistrazioni\s*\("""), fuori))
    }

    @Test
    fun `AC-C60 ogni costruttore di repository e CatalogoRegistrazioni gira UNA volta per apertura del progetto`() {
        AmbienteR3(radice).use { ambiente ->
            val crea = ambiente.costruzioniApertura
            val apri = ambiente.riapri()
            for ((percorso, costruzioni) in listOf("crea" to crea, "apri" to apri)) {
                assertEquals(
                    UNA_PER_APERTURA.associateWith { 1 },
                    UNA_PER_APERTURA.associateWith { costruzioni.istanze(it).size },
                    "$percorso: costruzioni per classe",
                )
            }
        }
    }

    @Test
    fun `AC-C61 i consumatori di R1 R2 e R3 ricevono la stessa istanza di ogni repository catalogo e politica`() {
        AmbienteR3(radice).use { ambiente ->
            val costruzioni = ambiente.costruzioniApertura
            for (classe in UNA_PER_APERTURA) {
                val unica = costruzioni.istanze(classe).single()
                val ricevute = costruzioni.argomentiOvunque(classe)
                assertTrue(ricevute.all { it === unica }, "$classe: un consumatore ha ricevuto un'altra istanza")
            }
            // Not vacuous: named consumers of each level really received it (R1 / R2 / R3).
            for ((consumatore, classe) in CONSUMATORI) {
                val ricevute = costruzioni.argomenti(consumatore, classe)
                assertTrue(ricevute.isNotEmpty(), "$consumatore non ha ricevuto $classe")
                assertTrue(ricevute.all { it === costruzioni.istanze(classe).single() }, "$consumatore: $classe")
            }
            // R1's Documento worker and R2's PuliziaDerivatiFile: the same RigenerazioneDocumentoPolitica.
            assertSame(
                costruzioni.argomenti(ABBONATO_DOCUMENTO, POLITICA_DOCUMENTO).single(),
                costruzioni.argomenti(PULIZIA_DERIVATI, POLITICA_DOCUMENTO).single(),
            )
            // S2's StatiElaborazione (R1, a bound reference) and Sintesi's LettoreTrascrittoDaTrascrizione (R3).
            val s2 = (ambiente.r3.r2.r1.statiElaborazione as CallableReference).boundReceiver
            assertSame(s2, costruzioni.argomenti(LETTORE_SINTESI, STATI).single())
            assertSame(ambiente.contesto.porte.statiElaborazione, s2)
        }
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

    private companion object {
        const val POLITICA_DOCUMENTO = "snastro.documento.applicazione.politiche.RigenerazioneDocumentoPolitica"
        const val STATI = "snastro.trascrizione.applicazione.letture.StatiElaborazione"
        const val ABBONATO_DOCUMENTO = "snastro.documento.adattatori.eventi.AbbonatoDocumentoEventi"
        const val PULIZIA_DERIVATI = "snastro.avvio.r2.PuliziaDerivatiFile"
        const val LETTORE_SINTESI = "snastro.sintesi.adattatori.porte.LettoreTrascrittoDaTrascrizione"
        const val PROGETTO_SQL = "snastro.progetto.adattatori.persistenza.ProgettoRepositorySql"
        const val REGISTRAZIONE_SQL = "snastro.progetto.adattatori.persistenza.RegistrazioneRepositorySql"
        const val IN_SOSPESO_SQL = "snastro.progetto.adattatori.persistenza.EliminazioniInSospesoSql"
        const val TRASCRITTO_SQL = "snastro.trascrizione.adattatori.persistenza.TrascrittoRepositorySql"
        const val ELABORAZIONE_SQL = "snastro.trascrizione.adattatori.persistenza.ElaborazioneRepositorySql"
        const val PARLANTE_SQL = "snastro.parlanti.adattatori.persistenza.ParlanteRepositorySql"
        const val ATTRIBUZIONE_SQL = "snastro.parlanti.adattatori.persistenza.AttribuzioneRepositorySql"
        const val RIASSUNTO_SQL = "snastro.sintesi.adattatori.persistenza.RiassuntoRepositorySql"
        const val LUNGHEZZA_SQL = "snastro.sintesi.adattatori.persistenza.LunghezzaMassimaRiassuntoRepositorySql"
        const val CATALOGO = "snastro.progetto.applicazione.letture.CatalogoRegistrazioni"

        /** AC-C60: every SQL repository, CatalogoRegistrazioni, the Documento policy and the project's ports. */
        val UNA_PER_APERTURA = listOf(
            PROGETTO_SQL,
            REGISTRAZIONE_SQL,
            IN_SOSPESO_SQL,
            TRASCRITTO_SQL,
            ELABORAZIONE_SQL,
            PARLANTE_SQL,
            ATTRIBUZIONE_SQL,
            RIASSUNTO_SQL,
            LUNGHEZZA_SQL,
            CATALOGO,
            POLITICA_DOCUMENTO,
            STATI,
            "snastro.trascrizione.applicazione.letture.FasiInCorso",
            "snastro.persistenza.UnitaDiLavoroSql",
            "snastro.kernel.DispatcherEventiInMemoria",
        )

        /** AC-C61: (consumer, what it must receive) across R0/R1 (Trascrizione, Documento), R2 (Parlanti) and R3. */
        val CONSUMATORI = listOf(
            "snastro.progetto.applicazione.comandi.CreaProgettoServizio" to PROGETTO_SQL,
            CATALOGO to REGISTRAZIONE_SQL,
            "snastro.progetto.applicazione.comandi.EliminaRegistrazioneServizio" to REGISTRAZIONE_SQL,
            "snastro.progetto.applicazione.comandi.EliminaRegistrazioneServizio" to IN_SOSPESO_SQL,
            "snastro.progetto.applicazione.comandi.CompletaEliminazioniRegistrazioniServizio" to IN_SOSPESO_SQL,
            "snastro.trascrizione.applicazione.comandi.EseguiProssimaElaborazioneServizio" to TRASCRITTO_SQL,
            "snastro.trascrizione.applicazione.comandi.ConfermaSegmentoServizio" to TRASCRITTO_SQL,
            "snastro.trascrizione.applicazione.politiche.ApplicaEliminazioneRegistrazionePolitica" to TRASCRITTO_SQL,
            "snastro.trascrizione.applicazione.comandi.AvviaElaborazioneServizio" to ELABORAZIONE_SQL,
            "snastro.trascrizione.applicazione.politiche.ApplicaEliminazioneRegistrazionePolitica" to ELABORAZIONE_SQL,
            "snastro.trascrizione.adattatori.porte.LettoreRegistrazioneDaProgetto" to CATALOGO,
            "snastro.parlanti.adattatori.porte.LettoreRegistrazioneDaProgetto" to CATALOGO,
            "snastro.documento.adattatori.porte.LettoreTrascrittoDaTrascrizione" to CATALOGO,
            "snastro.parlanti.applicazione.letture.NomiDelleVoci" to PARLANTE_SQL,
            "snastro.parlanti.applicazione.letture.NomiDelleVoci" to ATTRIBUZIONE_SQL,
            "snastro.parlanti.applicazione.comandi.RinominaParlanteServizio" to PARLANTE_SQL,
            "snastro.parlanti.applicazione.politiche.ApplicaRevisionePolitica" to ATTRIBUZIONE_SQL,
            "snastro.sintesi.applicazione.comandi.RiassumiServizio" to RIASSUNTO_SQL,
            "snastro.sintesi.applicazione.politiche.ApplicaEliminazioneRegistrazioneSintesiPolitica" to RIASSUNTO_SQL,
            "snastro.sintesi.applicazione.comandi.ModificaLunghezzaMassimaRiassuntoServizio" to LUNGHEZZA_SQL,
            ABBONATO_DOCUMENTO to POLITICA_DOCUMENTO,
            PULIZIA_DERIVATI to POLITICA_DOCUMENTO,
            LETTORE_SINTESI to STATI,
            "snastro.kernel.DispatcherEventiInMemoria" to "snastro.persistenza.UnitaDiLavoroSql",
        )
    }
}
