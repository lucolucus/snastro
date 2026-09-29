package snastro.avvio.progetto

import org.junit.jupiter.api.io.TempDir
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
 * ADR 0030 §1 (blocks `c1-porte-progetto`, `c3-composizione-piatta`): AC-C60..AC-C64 — [PorteProgetto] is the ONE place
 * `avvio/src/main` builds an SQL repository, `CatalogoRegistrazioni` and a cross-context reader, each ONCE per open
 * project, and every module's consumer receives that same instance.
 *
 * - Static half: the source text (AC-C60's grep over every `RepositorySql(`, AC-C63's no local `FasiInCorso(`).
 * - Counting half: [SondaCostruzioni] in [AmbienteProgetto] observes every constructor the REAL composition runs while
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

    /** `PorteProgetto.kt`, the ONE holder (c3 folded the per-context parts `PorteProgettoParlanti`/`Sintesi` in). */
    private fun diPorteProgetto(file: File) = file.name == "PorteProgetto.kt"

    @Test
    fun `AC-C60 RepositorySql( compare in avvio src main solo in PorteProgetto`() {
        val fuori = sorgenti.filterNot(::diPorteProgetto)
        assertTrue(sorgenti.any { it.name == "PorteProgetto.kt" }, "la guardia deve vedere PorteProgetto")
        assertTrue(fuori.any { it.name == "ModuloSintesi.kt" }, "la guardia deve vedere i moduli")
        // A constructor call, a constructor reference and any SQL adapter of a context's persistence package.
        assertEquals(emptyList(), righe(Regex("""RepositorySql\s*\("""), fuori))
        assertEquals(emptyList(), righe(Regex("""::\s*\w*RepositorySql\b"""), fuori))
        assertEquals(emptyList(), righe(Regex("""\.adattatori\.persistenza\."""), fuori))
        assertEquals(emptyList(), righe(Regex("""\bCatalogoRegistrazioni\s*\("""), fuori))
        // ADR 0030 §1: the cross-context readers are PorteProgetto's too (a module never builds one).
        assertEquals(emptyList(), righe(Regex("""\bLettore\w+Da\w+\s*\("""), fuori))
    }

    @Test
    fun `AC-C60 ogni costruttore di repository e CatalogoRegistrazioni gira UNA volta per apertura del progetto`() {
        AmbienteProgetto(radice).use { ambiente ->
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
    fun `AC-C61 i consumatori di ogni modulo ricevono la stessa istanza di ogni repository catalogo e politica`() {
        AmbienteProgetto(radice).use { ambiente ->
            val costruzioni = ambiente.costruzioniApertura
            for (classe in UNA_PER_APERTURA) {
                val unica = costruzioni.istanze(classe).single()
                val ricevute = costruzioni.argomentiOvunque(classe)
                assertTrue(ricevute.all { it === unica }, "$classe: un consumatore ha ricevuto un'altra istanza")
            }
            // Not vacuous: named consumers of each module really received it.
            for ((consumatore, classe) in CONSUMATORI) {
                val ricevute = costruzioni.argomenti(consumatore, classe)
                assertTrue(ricevute.isNotEmpty(), "$consumatore non ha ricevuto $classe")
                assertTrue(ricevute.all { it === costruzioni.istanze(classe).single() }, "$consumatore: $classe")
            }
            // Documento's worker and Progetto's PuliziaDerivatiFile: the same RigenerazioneDocumentoPolitica.
            assertSame(
                costruzioni.argomenti(ABBONATO_DOCUMENTO, POLITICA_DOCUMENTO).single(),
                costruzioni.argomenti(PULIZIA_DERIVATI, POLITICA_DOCUMENTO).single(),
            )
            // S2's StatiElaborazione (Trascrizione, a bound reference) and Sintesi's LettoreTrascrittoDaTrascrizione.
            val s2 = (ambiente.trascrizione.statiElaborazione as CallableReference).boundReceiver
            assertSame(s2, costruzioni.argomenti(LETTORE_SINTESI, STATI).single())
            assertSame(ambiente.porte.statiElaborazione, s2)
        }
    }

    @Test
    fun `AC-C63 nessun FasiInCorso() fuori da PorteProgetto, e il lettore Sintesi legge la sua statiElaborazione`() {
        assertEquals(emptyList(), righe(Regex("""\bFasiInCorso\s*\("""), sorgenti.filterNot(::diPorteProgetto)))
        val porte = sorgenti.single(::diPorteProgetto).readLines()
        assertTrue(
            porte.any { "LettoreTrascrittoSintesi(vociDelTrascritto, statiElaborazione)" in it },
            "il lettore Sintesi deve leggere la statiElaborazione di PorteProgetto",
        )
    }

    @Test
    fun `AC-C62 porte lettura e il delegato del dispatcher sono la stessa UnitaDiLavoroSql`() {
        AmbienteProgetto(radice).use { ambiente ->
            val contesto = ambiente.porte
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
        AmbienteProgetto(radice).use { ambiente ->
            val barriera = CountDownLatch(1)
            ambiente.diarizzatoreScriptato.barriera = barriera
            val id = try {
                val id = ambiente.importaEAvvia()
                attendiFinche(timeout = 10.seconds, messaggio = "S2 IN_CORSO (DIARIZZAZIONE)") {
                    ambiente.stato(id) == StatoElaborazioneVista.IN_CORSO
                }
                val rigaS2 = ambiente.vistaDi(id)
                val rigaPorte = ambiente.porte.statiElaborazione.stati(listOf(id)).single()
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
        const val PULIZIA_DERIVATI = "snastro.avvio.progetto.PuliziaDerivatiFile"
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
            // c3 (ADR 0030 §1): the cross-context readers and the public queries they share, the ports holder itself.
            "snastro.trascrizione.applicazione.letture.VociDelTrascritto",
            "snastro.parlanti.applicazione.letture.NomiDelleVoci",
            "snastro.parlanti.adattatori.porte.LettoreVociDaTrascrizione",
            "snastro.parlanti.adattatori.porte.LettoreRegistrazioneDaProgetto",
            "snastro.trascrizione.adattatori.porte.LettoreRegistrazioneDaProgetto",
            "snastro.documento.adattatori.porte.LettoreTrascrittoDaTrascrizione",
            "snastro.documento.adattatori.porte.LettoreNomiDaParlanti",
            LETTORE_SINTESI,
            "snastro.sintesi.adattatori.porte.LettoreNomiDaParlanti",
            "snastro.avvio.progetto.PorteProgetto",
        )

        /** AC-C61: (consumer, what it must receive) across the Progetto, Trascrizione, Documento, Parlanti, Sintesi. */
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
