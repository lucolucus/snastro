package snastro.avvio.sintesi

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import snastro.kernel.DispatcherEventiFinta
import snastro.kernel.EventoPubblicato
import snastro.kernel.IncontroId
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.VoceId
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
import snastro.sintesi.applicazione.porte.LettoreIncontroFinta
import snastro.sintesi.applicazione.porte.LettoreTrascrittoFinta
import snastro.sintesi.applicazione.porte.RiassuntoRepositoryFinta
import snastro.sintesi.applicazione.porte.SegmentoSintesi
import snastro.sintesi.applicazione.porte.StatoModelloLinguistico
import snastro.sintesi.applicazione.porte.unRiassunto
import snastro.sintesi.dominio.LunghezzaMassimaParole
import snastro.sintesi.dominio.Riassunto
import snastro.sintesi.dominio.RiassuntoId
import snastro.supporto.test.pausaInTempoReale
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * AC-S153 + AC-I36 (opt-in, `./gradlew benchmarkRiassunto -Pcampione=<Sbobinatura .md>[,<.md>...]`, never in `check`):
 * ONE Riassunto of the sample at the default 2000-word cap, through the REAL `EseguiProssimoRiassuntoServizio` and
 * the REAL
 * [ModelloLinguisticoLlama] (llama.cpp natives copied by `copiaNativiLlama`, Qwen3.5 9B), from `RiassuntoAvviato`
 * to `RiassuntoPronto`, model load and release included (ADR 0026 §6). The storage and read ports are the Sintesi
 * fakes: the time is the model's, not SQLite's. Prints load / prefill / generation / release, tokens, peak RSS;
 * fails above 600 s or when the run does not end `pronto`.
 *
 * The sample is a snastro Sbobinatura (`**Nome** (mm:ss): testo` per Segmento, `Sbobinatura.proietta`): its lines
 * become
 * the Segmenti in order, a distinct name a Voce (a `Voce n` name stays unattributed). The model is `-Pmodello`,
 * else the installed catalogue GGUF of the app's model cache.
 *
 * AC-I36: `-Pcampione` is a comma-separated list; the samples are the ordered Parti of ONE Incontro (a real 2-3 Parte
 * ~3 h one), summarized by ONE Riassunto. The limit is 600 s per hour of audio (the audio of a Parte is read from the
 * sample's timestamps: its last Segmento start); one sample keeps the fixed 600 s of AC-S153.
 */
@Tag("benchmark")
class BenchmarkRiassuntoTest {
    private val incontro = IncontroId("benchmark")

    @Test
    fun `AC-S153 AC-I36 un Riassunto del tetto predefinito si completa entro 600 s per ora di audio`() {
        val campioni = checkNotNull(System.getProperty("snastro.benchmark.campione")) { "-Pcampione=<.md>[,<.md>]" }
            .split(",").filter { it.isNotBlank() }.map { Path.of(it.trim()) }
        check(campioni.isNotEmpty()) { "-Pcampione=<.md>[,<.md>]" }
        val parti = campioni.mapIndexed { i, c -> RegistrazioneId("benchmark-${i + 1}") to leggiSbobinatura(c) }
        val segmenti = parti.flatMap { it.second.first }
        val nomi = parti.flatMap { it.second.second.values }
        val audioMs = parti.sumOf { (_, p) -> p.first.maxOf { it.intervallo.inizioMs } }
        val ore = if (parti.size == 1) 1.0 else audioMs / MS_PER_ORA
        val limiteSecondi = LIMITE_SECONDI * ore
        val misure = CopyOnWriteArrayList<MisureRiassunto>()
        val eventi = CopyOnWriteArrayList<Pair<EventoPubblicato, Long>>()
        val riassunti = RiassuntoRepositoryFinta()
        val dispatcher = DispatcherEventiFinta(UnitaDiLavoroFinta(riassunti)).apply {
            registraDopoCommit { eventi += it to System.nanoTime() }
        }
        riassunti.salva(unRiassunto("r1", incontro, parole = LunghezzaMassimaParole.PREDEFINITA)).atteso()
        val servizio = EseguiProssimoRiassuntoServizio(
            dispatcher.unitaDiLavoro,
            Clock.systemUTC(),
            riassunti,
            LettoreTrascrittoFinta(parti.associate { (r, p) -> r to p.first }),
            LettoreIncontroFinta(mapOf(incontro to parti.map { it.first })),
            ModelloLinguisticoLlama({ cartellaNativiLlama() }, { fileModello() }, misure = { misure += it }),
            DisponibilitaModelloLinguisticoFinta(StatoModelloLinguistico.Installato),
            dispatcher,
        )
        val picco = PiccoRss()

        picco.use { servizio.esegui(EseguiProssimoRiassunto(esclusi = emptySet(), primaDi = null)).atteso() }

        val avviato = eventi.single { it.first is RiassuntoAvviato }.second
        val pronto = eventi.singleOrNull { it.first is RiassuntoPronto }?.second
        val riassunto = checkNotNull(riassunti.trova(RiassuntoId("r1")))
        val paroleTrascritto = segmenti.sumOf { parole(it.testo) }
        val paroleRiassunto = parole(testoRiassunto(riassunto))
        val secondi = pronto?.let { (it - avviato) / NANOS_PER_SECONDO.toDouble() }
        val oreTesto = "%.2f".format(ore)
        val tempoTesto = secondi?.let { "%.1f s".format(it) } ?: "-"
        val limiteTesto = "%.0f".format(limiteSecondi)
        println(
            """
            benchmarkRiassunto — ${campioni.map { it.fileName }}: ${parti.size} Parti, $oreTesto h, ${segmenti.size} segmenti, ${nomi.size} nomi (
                ignorati: il modello vede solo Voce n,
                ADR 0032,
            )
              esito: ${riassunto.stato} ${riassunto.motivoFallimento ?: ""} (
                  eventi: ${eventi.map { it.first::class.simpleName }},
              )
              RiassuntoAvviato -> RiassuntoPronto: $tempoTesto (limite $limiteTesto s)
              misure: ${misure.singleOrNull()}
              elementi: decisioni ${riassunto.decisioni.size}, questioni ${riassunto.questioniAperte.size}, azioni ${riassunto.azioni.size}, punti chiave ${riassunto.puntiChiave.size}, omessi ${riassunto.omessi}
              picco RSS: ${picco.massimoKb / KB_PER_MB} MB
              parole: trascritto $paroleTrascritto, riassunto $paroleRiassunto
            """.trimIndent(),
        )
        println("--- riassunto ---\n${testoRiassunto(riassunto)}\n--- fine ---")
        assertTrue(riassunto.pronto, "il Riassunto non e pronto: ${riassunto.stato} ${riassunto.motivoFallimento}")
        val messaggio = "RiassuntoAvviato -> RiassuntoPronto in $tempoTesto > $limiteTesto s"
        assertTrue(checkNotNull(secondi) <= limiteSecondi, messaggio)
    }

    /** The Riassunto's text as the reader sees it (Sommario, then each list), for the word count and the print. */
    private fun testoRiassunto(r: Riassunto): String = buildList {
        r.sommario?.let { add(it.testo.codifica()) }
        r.decisioni.forEach { add("[decisione] ${it.testo.codifica()}") }
        r.questioniAperte.forEach { add("[questione] ${it.testo.codifica()}") }
        r.azioni.forEach { add("[azione] ${it.testo.codifica()}") }
        r.puntiChiave.forEach { add("[punto] ${it.testo.codifica()}") }
    }.joinToString("\n")

    private fun parole(testo: String): Int = testo.split(Regex("\\s+")).count { it.isNotBlank() }

    private fun fileModello(): Path {
        val esplicito = System.getProperty("snastro.benchmark.modello")?.takeIf { it.isNotBlank() }
        val catalogo = CatalogoModelli(listOf(VOCE_CATALOGO_MODELLO_LINGUISTICO))
        return esplicito?.let(Path::of)
            ?: fileModelloLinguistico(ProvisioningModelli(catalogo, CartellaCacheModelli.risolvi()))
    }

    /** Sbobinatura lines -> Segmenti (in order, ids 1..n) and the Nome of each attributed Voce. */
    private fun leggiSbobinatura(md: Path): Pair<List<SegmentoSintesi>, Map<VoceId, String>> {
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
                pausaInTempoReale(250.milliseconds, motivo = "campionatore periodico dell'RSS a orologio reale")
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
        const val MS_PER_ORA = 3_600_000.0
        const val KB_PER_MB = 1_024
    }
}
