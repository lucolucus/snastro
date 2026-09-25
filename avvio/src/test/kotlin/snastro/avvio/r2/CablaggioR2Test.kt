package snastro.avvio.r2

import snastro.avvio.r1.SceltaMl
import snastro.avvio.r1.SelezioneAdattatoriMl
import snastro.ml.MotoreSherpa
import snastro.modelli.CatalogoDiarizzazione
import snastro.modelli.ProvisioningModelli
import snastro.parlanti.adattatori.audio.DecodificatoreAudioFfmpeg
import snastro.parlanti.adattatori.ml.EstrattoreImprontaSherpa
import snastro.parlanti.applicazione.porte.DecodificatoreAudioFinta
import snastro.parlanti.applicazione.porte.EstrattoreImprontaFinta
import snastro.ui.DestinazioneShell
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The static half of the R2 composition's ACs, checked on `avvio/src/main` and on the selection point. */
class CablaggioR2Test {
    private val r2: List<File> = File("src/main/kotlin/snastro/avvio/r2")
        .walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun righeCon(testo: String): List<String> = r2
        .flatMap { file -> file.readLines().mapIndexed { i, riga -> Triple(file, i + 1, riga) } }
        .filter { (_, _, riga) -> testo in riga && !riga.trimStart().let { it.startsWith("*") || it.startsWith("//") } }
        .map { (file, n, riga) -> "${file.name}:$n: ${riga.trim()}" }

    @Test
    fun `AC-359 nessun servizio della composizione R2 e costruito con la UnitaDiLavoroSql grezza`() {
        assertTrue(r2.isNotEmpty())
        assertEquals(emptyList(), righeCon("UnitaDiLavoroSql"))
        assertTrue(righeCon("dispatcher.unitaDiLavoro").isNotEmpty(), "eventi.unitaDiLavoro per tutti")
    }

    @Test
    fun `AC-457 la purga sincrona e registrata prima che R1 costruisca la coda, e S2 riceve Ritrascrivi`() {
        val estensione = File("src/main/kotlin/snastro/avvio/r2/EstensioneR2.kt").readLines()
        val abbonato = estensione.indexOfFirst { "AbbonatoRevisioneParlanti(" in it }
        val politica = estensione.indexOfFirst { "ApplicaSostituzioneTrascrittoPolitica(" in it }
        val coda = estensione.indexOfFirst { "r1.apri(" in it }
        assertTrue(abbonato in 0 until coda, "AbbonatoRevisioneParlanti prima di r1.apri (la coda)")
        assertTrue(politica in abbonato until coda, "con la politica di sostituzione")
        assertTrue(righeCon("ritrascrivi =").isNotEmpty(), "AC-457 'Ritrascrivi' fornito a S2")
    }

    @Test
    fun `AC-630 le due purghe sincrone di RegistrazioneEliminata precedono la coda, e S2 riceve Elimina solo in R2`() {
        val estensione = File("src/main/kotlin/snastro/avvio/r2/EstensioneR2.kt").readLines()
        val coda = estensione.indexOfFirst { "r1.apri(" in it }
        val trascrizione = estensione.indexOfFirst { "AbbonatoEliminazioneRegistrazione(" in it }
        val parlanti = estensione.indexOfFirst { "AbbonatoRevisioneParlanti(" in it }
        assertTrue(trascrizione in 0 until coda, "AbbonatoEliminazioneRegistrazione prima di r1.apri (la coda)")
        assertTrue(parlanti in 0 until coda, "AbbonatoRevisioneParlanti prima di r1.apri (la coda)")
        assertEquals(1, righeCon("eliminaRegistrazione = r2.eliminaRegistrazione").size, "S2 di R2 riceve Elimina")
        val r1 = File("src/main/kotlin/snastro/avvio/r1").walkTopDown().filter { it.isFile && it.extension == "kt" }
        assertEquals(emptyList(), r1.filter { "eliminaRegistrazione" in it.readText() }.map { it.name }.toList())
        assertEquals(
            emptyList(),
            File("src/main/kotlin/snastro/avvio").listFiles().orEmpty()
                .filter { it.isFile && "eliminaRegistrazione" in it.readText() }.map { it.name },
            "R0: nessun Elimina",
        )
    }

    @Test
    fun `AC-633 CompletaEliminazioniRegistrazioni parte dopo che R1 ha accodato RigeneraTuttiIDocumenti`() {
        val estensione = File("src/main/kotlin/snastro/avvio/r2/EstensioneR2.kt").readLines()
        val r1 = estensione.indexOfFirst { "r1.apri(" in it }
        val completa = estensione.indexOfFirst { "completaEliminazioni(" in it }
        assertTrue(completa > r1 && r1 >= 0, "dopo r1.apri: AbbonatoDocumentoEventi accoda RigeneraTuttiIDocumenti")
    }

    @Test
    fun `AC-341 AC-177 la shell R2 include la sezione Parlanti`() {
        assertEquals(setOf(DestinazioneShell.REGISTRAZIONI, DestinazioneShell.PARLANTI), SEZIONI_SHELL_R2)
    }

    @Test
    fun `selezione ML FINTE l'estrattore e il decodificatore finti, e la Proposta confronta davvero`() {
        val adattatori = SelezioneAdattatoriMl.adattatoriParlanti(SceltaMl.FINTE, MotoreSherpa(), modelli())
        assertIs<EstrattoreImprontaFinta>(adattatori.estrattore)
        assertIs<DecodificatoreAudioFinta>(adattatori.decodificatore(Path.of("progetto")))
        assertTrue(adattatori.proposte)
    }

    @Test
    fun `AC-259 AC-310 selezione ML REALI EstrattoreImprontaSherpa su TitaNet-small, FFmpeg, Proposta attiva`() {
        val adattatori = SelezioneAdattatoriMl.adattatoriParlanti(SceltaMl.REALI, MotoreSherpa(), modelli())

        assertIs<EstrattoreImprontaSherpa>(adattatori.estrattore)
        assertIs<DecodificatoreAudioFfmpeg>(adattatori.decodificatore(Path.of("progetto")))
        assertTrue(adattatori.proposte)
        assertEquals(CatalogoDiarizzazione.embeddingTitanetSmall.id, adattatori.estrattore.modello)
        adattatori.rilascia() // never loaded: releasing loads nothing and never throws
    }

    @Test
    fun `AC-259 una sola voce di catalogo TitaNet-small, condivisa da diarizzatore ed estrattore`() {
        val ids = SelezioneAdattatoriMl.catalogo(SceltaMl.REALI).voci.map { it.id }

        assertEquals(1, ids.count { it == CatalogoDiarizzazione.embeddingTitanetSmall.id })
        assertEquals(ids.distinct(), ids)
    }

    @Test
    fun `AC-541 il piano usa il classificatore coseno con SIMILARITA_MINIMA e MARGINE_MINIMO`() {
        assertEquals(
            1,
            righeCon("ClassificatoreSomiglianzaCoseno(SoglieSomiglianza(SIMILARITA_MINIMA, MARGINE_MINIMO))").size,
        )
        assertTrue(righeCon("RiassegnaSegmentiServizio(uow,").isNotEmpty(), "RiassegnaSegmenti, eventi.unitaDiLavoro")
        assertTrue(righeCon("ConfermaSegmentoServizio(uow,").isNotEmpty(), "ConfermaSegmento con eventi.unitaDiLavoro")
    }

    private fun modelli(): ProvisioningModelli =
        ProvisioningModelli(SelezioneAdattatoriMl.catalogo(SceltaMl.REALI), Files.createTempDirectory("modelli"))
}
