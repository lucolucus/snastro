package snastro.avvio.parlanti

import org.junit.jupiter.api.io.TempDir
import snastro.avvio.SEZIONI_SHELL
import snastro.avvio.coda.CodaCondivisa
import snastro.avvio.progetto.AmbienteProgetto
import snastro.avvio.progetto.SondaCostruzioni
import snastro.avvio.trascrizione.SceltaMl
import snastro.avvio.trascrizione.SelezioneAdattatoriMl
import snastro.ml.MotoreSherpa
import snastro.modelli.CatalogoDiarizzazione
import snastro.modelli.ProvisioningModelli
import snastro.parlanti.adattatori.audio.DecodificatoreAudioFfmpeg
import snastro.parlanti.adattatori.eventi.AbbonatoRevisioneParlanti
import snastro.parlanti.adattatori.ml.EstrattoreImprontaSherpa
import snastro.parlanti.applicazione.porte.DecodificatoreAudioFinta
import snastro.parlanti.applicazione.porte.EstrattoreImprontaFinta
import snastro.progetto.applicazione.eventi.RegistrazioneEliminata
import snastro.trascrizione.applicazione.eventi.TrascrittoSostituito
import snastro.ui.DestinazioneShell
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The Parlanti wiring of the single composition (ADR 0030 §3, retargeted from the retired release composition): the
 * declared subscriber lists and the start order of the REAL `apriProgetto` ([AmbienteProgetto]), the constructions
 * it ran ([snastro.avvio.progetto.SondaCostruzioni]), `avvio/src/main` where the fact is textual, and the selection
 * point.
 */
class CablaggioParlantiTest {
    @TempDir
    lateinit var radice: Path

    private val sorgenti: List<File> = File("src/main/kotlin/snastro/avvio")
        .walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun righeCon(testo: String, dove: List<File> = sorgenti): List<String> = dove
        .flatMap { file -> file.readLines().mapIndexed { i, riga -> Triple(file, i + 1, riga) } }
        .filter { (_, _, riga) -> testo in riga && !riga.trimStart().let { it.startsWith("*") || it.startsWith("//") } }
        .map { (file, n, riga) -> "${file.name}:$n: ${riga.trim()}" }

    private fun del(pacchetto: String): List<File> = sorgenti.filter { it.path.contains("snastro/avvio/$pacchetto/") }

    @Test
    fun `AC-359 nessun servizio dei Parlanti e costruito con la UnitaDiLavoroSql grezza`() {
        assertTrue(del("parlanti").isNotEmpty())
        assertEquals(emptyList(), righeCon("UnitaDiLavoroSql", del("parlanti")))
        assertTrue(righeCon("porte.unitaDiLavoro", del("parlanti")).isNotEmpty(), "eventi.unitaDiLavoro per tutti")
    }

    @Test
    fun `AC-457 la purga sincrona e registrata prima della coda, e S2 riceve Ritrascrivi`() {
        AmbienteProgetto(radice).use {
            val parlanti = it.composto.ordineSincroni.single { m -> m is ModuloParlanti }
            val purga = parlanti.abbonatiSincroni().single { a -> a.evento == TrascrittoSostituito::class }
            assertIs<AbbonatoRevisioneParlanti>(purga.abbonato)
            assertTrue(registrazioniPrimaDellaCoda(it.costruzioniApertura), "registrata prima della coda")
        }
        assertEquals(1, righeCon("ritrascrivi = collaboratori.avviaElaborazione").size, "AC-457 'Ritrascrivi' a S2")
    }

    @Test
    fun `AC-630 le purghe sincrone di RegistrazioneEliminata precedono la coda, e S2 riceve Elimina`() {
        AmbienteProgetto(radice).use {
            val purghe = it.composto.ordineSincroni.flatMap { m -> m.abbonatiSincroni() }
                .filter { a -> a.evento == RegistrazioneEliminata::class }
                .map { a -> a.abbonato::class.simpleName }
            assertEquals(
                listOf("AbbonatoProgettoSintesi", "AbbonatoRevisioneParlanti", "AbbonatoEliminazioneRegistrazione"),
                purghe,
            )
            assertTrue(registrazioniPrimaDellaCoda(it.costruzioniApertura), "registrate prima della coda")
        }
        assertEquals(1, righeCon("eliminaRegistrazione = collaboratori.eliminaRegistrazione").size, "S2 riceve Elimina")
    }

    @Test
    fun `AC-633 CompletaEliminazioniRegistrazioni parte dopo il lavoro della Sbobinatura, col suo giro accodato`() {
        AmbienteProgetto(radice).use {
            val avvio = it.composto.ordineAvvio.map { a -> a::class.simpleName }
            assertTrue(
                avvio.indexOf("ModuloSbobinatura") in 0 until avvio.indexOf("ModuloProgetto"),
                "la Sbobinatura parte prima del completamento delle eliminazioni: $avvio",
            )
        }
    }

    /** Every declared registration (`Iscrizione*`) was constructed before the queue, in the REAL apriProgetto. */
    private fun registrazioniPrimaDellaCoda(costruzioni: SondaCostruzioni.Costruzioni): Boolean {
        val ordine = costruzioni.di { true }.map { c -> c.istanza.javaClass.name }
        val coda = ordine.indexOf(CodaCondivisa::class.java.name)
        val ultimaIscrizione = ordine.indexOfLast { n -> n.contains(".Iscrizione") }
        return coda > 0 && ultimaIscrizione in 0 until coda
    }

    @Test
    fun `AC-341 AC-177 la shell include la sezione Parlanti`() {
        assertEquals(setOf(DestinazioneShell.REGISTRAZIONI, DestinazioneShell.PARLANTI), SEZIONI_SHELL)
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
        val trascrizione = del("trascrizione")
        assertEquals(1, righeCon("RiassegnaSegmentiServizio(uow,", trascrizione).size, "RiassegnaSegmenti")
        assertEquals(1, righeCon("ConfermaSegmentoServizio(uow,", trascrizione).size, "ConfermaSegmento")
        assertEquals(1, righeCon("val uow = porte.unitaDiLavoro", trascrizione).size)
    }

    private fun modelli(): ProvisioningModelli =
        ProvisioningModelli(SelezioneAdattatoriMl.catalogo(SceltaMl.REALI), Files.createTempDirectory("modelli"))
}
