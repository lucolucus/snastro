package snastro.avvio.r3

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import snastro.kernel.DispatcherEventiFinta
import snastro.kernel.EventoPubblicato
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.atteso
import snastro.modelli.CartellaCacheModelli
import snastro.modelli.CatalogoModelli
import snastro.modelli.ProvisioningModelli
import snastro.modelli.VOCE_CATALOGO_MODELLO_LINGUISTICO
import snastro.sintesi.adattatori.ml.MisureRiassunto
import snastro.sintesi.adattatori.ml.ModelloLinguisticoLlama
import snastro.sintesi.applicazione.comandi.EseguiProssimoRiassunto
import snastro.sintesi.applicazione.comandi.EseguiProssimoRiassuntoServizio
import snastro.sintesi.applicazione.eventi.RiassuntoAvviato
import snastro.sintesi.applicazione.eventi.RiassuntoPronto
import snastro.sintesi.applicazione.porte.DisponibilitaModelloLinguisticoFinta
import snastro.sintesi.applicazione.porte.LettoreNomiFinta
import snastro.sintesi.applicazione.porte.LettoreTrascrittoFinta
import snastro.sintesi.applicazione.porte.RiassuntoRepositoryFinta
import snastro.sintesi.applicazione.porte.SegmentoSintesi
import snastro.sintesi.applicazione.porte.StatoModelloLinguistico
import snastro.sintesi.applicazione.porte.unRiassunto
import snastro.sintesi.dominio.LunghezzaMassimaParole
import snastro.sintesi.dominio.RiassuntoId
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.test.assertTrue

/**
 * AC-S153 (opt-in, `./gradlew benchmarkRiassunto -Pcampione=<Documento .md>`, never in `check`): ONE Riassunto of
 * the sample at the default 2000-word cap, through the REAL `EseguiProssimoRiassuntoServizio` and the REAL
 * [ModelloLinguisticoLlama] (llama.cpp natives copied by `copiaNativiLlama`, Qwen3.5 9B), from `RiassuntoAvviato`
 * to `RiassuntoPronto`, model load and release included (ADR 0026 §6). The storage and read ports are the Sintesi
 * fakes: the time is the model's, not SQLite's. Prints load / prefill / generation / release, tokens, peak RSS;
 * fails above 600 s or when the run does not end `pronto`.
 *
 * The sample is a snastro Documento (`**Nome** (mm:ss): testo` per Segmento, `Documento.proietta`): its lines become
 * the Segmenti in order, a distinct name a Voce (a `Voce n` name stays unattributed). The model is `-Pmodello`,
 * else the installed catalogue GGUF of the app's model cache.
 */
@Tag("benchmark")
class BenchmarkRiassuntoTest {
    private val registrazione = RegistrazioneId("benchmark")

    @Test
    fun `AC-S153 un Riassunto di un'ora al tetto predefinito si completa entro 600 s`() {
        val campione = Path.of(checkNotNull(System.getProperty("snastro.benchmark.campione")) { "-Pcampione=<.md>" })
        val (segmenti, nomi) = leggiDocumento(campione)
        val misure = CopyOnWriteArrayList<MisureRiassunto>()
        val eventi = CopyOnWriteArrayList<Pair<EventoPubblicato, Long>>()
        val riassunti = RiassuntoRepositoryFinta()
        val dispatcher = DispatcherEventiFinta(UnitaDiLavoroFinta(riassunti)).apply {
            registraDopoCommit { eventi += it to System.nanoTime() }
        }
        riassunti.salva(unRiassunto("r1", registrazione, parole = LunghezzaMassimaParole.PREDEFINITA)).atteso()
        val servizio = EseguiProssimoRiassuntoServizio(
            dispatcher.unitaDiLavoro,
            Clock.systemUTC(),
            riassunti,
            LettoreTrascrittoFinta(mapOf(registrazione to segmenti)),
            LettoreNomiFinta(
                nomi.entries.associate { (voce, nome) -> VoceRef(registrazione, voce) to nome },
                nomi.values.associateWith { it },
            ),
            ModelloLinguisticoLlama({ cartellaNativiLlama() }, { fileModello() }, misure = { misure += it }),
            DisponibilitaModelloLinguisticoFinta(StatoModelloLinguistico.Installato),
            dispatcher,
        )
        val picco = PiccoRss()

        picco.use { servizio.esegui(EseguiProssimoRiassunto(esclusi = emptySet(), primaDi = null)).atteso() }

        val avviato = eventi.single { it.first is RiassuntoAvviato }.second
        val pronto = eventi.singleOrNull { it.first is RiassuntoPronto }?.second
        val riassunto = checkNotNull(riassunti.trova(RiassuntoId("r1")))
        val secondi = pronto?.let { (it - avviato) / NANOS_PER_SECONDO.toDouble() }
        println(
            """
            benchmarkRiassunto — ${campione.fileName}: ${segmenti.size} segmenti, ${nomi.size} nomi
              esito: ${riassunto.stato} ${riassunto.motivoFallimento ?: ""} (eventi: ${eventi.map { it.first::class.simpleName }})
              RiassuntoAvviato -> RiassuntoPronto: ${secondi?.let { "%.1f s".format(it) } ?: "-"} (limite 600 s)
              misure: ${misure.singleOrNull()}
              elementi: decisioni ${riassunto.decisioni.size}, questioni ${riassunto.questioniAperte.size}, azioni ${riassunto.azioni.size}, punti chiave ${riassunto.puntiChiave.size}, omessi ${riassunto.omessi}
              picco RSS: ${picco.massimoKb / KB_PER_MB} MB
            """.trimIndent(),
        )
        assertTrue(riassunto.pronto, "il Riassunto non e pronto: ${riassunto.stato} ${riassunto.motivoFallimento}")
        assertTrue(checkNotNull(secondi) <= LIMITE_SECONDI, "RiassuntoAvviato -> RiassuntoPronto in $secondi s > 600 s")
    }

    private fun fileModello(): Path {
        val esplicito = System.getProperty("snastro.benchmark.modello")?.takeIf { it.isNotBlank() }
        val catalogo = CatalogoModelli(listOf(VOCE_CATALOGO_MODELLO_LINGUISTICO))
        return esplicito?.let(Path::of)
            ?: fileModelloLinguistico(ProvisioningModelli(catalogo, CartellaCacheModelli.risolvi()))
    }

    /** Documento lines -> Segmenti (in order, ids 1..n) and the Nome of each attributed Voce. */
    private fun leggiDocumento(md: Path): Pair<List<SegmentoSintesi>, Map<VoceId, String>> {
        val voci = LinkedHashMap<String, VoceId>()
        val segmenti = Files.readAllLines(md).mapNotNull { RIGA.matchEntire(it.trim()) }.mapIndexed { i, m ->
            val (nome, minuti, secondi) = m.destructured
            val testo = m.groupValues[GRUPPO_TESTO]
            val inizio = (minuti.toLong() * 60 + secondi.toLong()) * 1_000
            val voce = voci.getOrPut(nome) { VoceId(voci.size + 1) }
            SegmentoSintesi(SegmentoId(i + 1), voce, IntervalloMs(inizio, inizio + 1_000), testo)
        }
        check(segmenti.isNotEmpty()) { "$md: nessuna riga '**Nome** (mm:ss): testo'" }
        return segmenti to voci.filterKeys { !VOCE_SENZA_NOME.matches(it) }.entries.associate { (nome, v) -> v to nome }
    }

    /** Samples this JVM's resident set size (`ps -o rss=`, KB) every 250 ms while open. */
    private class PiccoRss : AutoCloseable {
        private val massimo = AtomicLong(0)

        @Volatile private var attivo = true
        private val campionatore = thread(isDaemon = true, name = "picco-rss") {
            while (attivo) {
                massimo.accumulateAndGet(rss(), ::maxOf)
                Thread.sleep(250) // real time is the subject: a periodic real-clock RSS sampler.
            }
        }
        val massimoKb: Long get() = massimo.get()

        override fun close() {
            attivo = false
            campionatore.join()
        }

        private fun rss(): Long = ProcessBuilder("ps", "-o", "rss=", "-p", ProcessHandle.current().pid().toString())
            .start().inputStream.bufferedReader().readText().trim().toLongOrNull() ?: 0
    }

    private companion object {
        val RIGA = Regex("""\*\*(.+?)\*\* \((\d+):(\d{2})\): (.*)""")
        val VOCE_SENZA_NOME = Regex("""Voce \d+""")
        const val GRUPPO_TESTO = 4
        const val LIMITE_SECONDI = 600.0
        const val NANOS_PER_SECONDO = 1_000_000_000L
        const val KB_PER_MB = 1_024
    }
}
