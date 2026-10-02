package snastro.avvio.spike

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Timeout
import snastro.avvio.costruisciGrafo
import snastro.avvio.progetto.CollaboratoriProgetto
import snastro.avvio.trascrizione.SceltaMl
import snastro.kernel.ProgettoId
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.atteso
import snastro.progetto.applicazione.comandi.AggiungiRegistrazione
import snastro.progetto.applicazione.comandi.Destinazione
import snastro.supporto.test.attendiFinche
import snastro.trascrizione.applicazione.comandi.AvviaElaborazione
import snastro.trascrizione.applicazione.letture.StatoElaborazioneVista
import snastro.trascrizione.applicazione.letture.TrascrittoView
import snastro.ui.registrazione.ComandoVoce
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.copyTo
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Spike `voci-tra-parti` + input of `contesto-lungo` (feature `incontro`). Opt-in: runs only with
 * `SNASTRO_SPIKE_INCONTRO_DIR` set to a folder holding `audio/parte-1 *.m4a`, `audio/parte-2 *.m4a` (one real meeting
 * recorded in two consecutive files). Over the app's own graph and the REAL models already in the user's cache:
 * 1. imports and transcribes both parts;
 * 2. names every Voce of part 1 "P1-Voce n" (a new ricorrente Parlante each, so each yields an ImprontaVocale);
 * 3. asks the app's existing Proposta for every Voce of part 2 — option (a) of the spike, with no new ML code.
 * Writes `rapporto.md` (per Voce: speaking time, sample lines, the ranked Candidati with their Fascia) and copies both
 * Sbobinature, whose concatenation is the long input for `contesto-lungo`. The user judges which matches are right.
 * The user's own project registry is never touched (a throwaway one under the spike folder).
 */
@Tag("modelli")
class SpikeIncontroTest {
    @Test
    @Timeout(value = 2, unit = TimeUnit.HOURS) // ~95 min of real audio through the real pipeline
    fun `spike voci-tra-parti e input di contesto-lungo su un incontro reale in due parti`() {
        val cartella = System.getenv(VARIABILE)?.let(Path::of)
        assumeTrue(cartella != null && Files.isDirectory(cartella), "$VARIABILE non impostata: spike saltato")
        checkNotNull(cartella)
        val parti = cartella.resolve("audio").listDirectoryEntries("parte-*").sortedBy { it.name }
        check(parti.size == 2) { "servono audio/parte-1 e audio/parte-2: $parti" }
        // Optional Numero di persone for both parts (the user's own knowledge of the meeting); null = automatic.
        val persone = System.getenv(VARIABILE_PERSONE)?.toInt()
        val esito = Files.createDirectories(cartella.resolve(persone?.let { "esito-persone-$it" } ?: "esito"))
        val sessione = costruisciGrafo(Files.createTempDirectory("spike-incontro-registro"), SceltaMl.REALI).sessione
        val cronometro = TimeSource.Monotonic.markNow()
        try {
            val progetto = sessione.crea(esito.toString(), "Spike Incontro").atteso()
            val c = checkNotNull(sessione.collaboratoriCorrenti())
            val ids = parti.map { parte ->
                importa(c, progetto.progettoId, parte)
                val titolo = parte.fileName.toString().substringBeforeLast('.')
                c.registrazioni().single { it.titolo == titolo }.registrazioneId
            }
            val tempi = ids.map { id ->
                val inizio = TimeSource.Monotonic.markNow()
                c.avviaElaborazione(AvviaElaborazione(id, persone)).atteso()
                attendiFinche(timeout = 90.minutes, messaggio = "trascrizione di $id") {
                    c.trascrizione.statiElaborazione(listOf(id)).single().stato in FINALI
                }
                val stato = c.trascrizione.statiElaborazione(listOf(id)).single()
                check(stato.stato == StatoElaborazioneVista.COMPLETATA) { "fallita: ${stato.motivoFallimento}" }
                inizio.elapsedNow()
            }
            val trascritti = ids.map { checkNotNull(c.trascrizione.trascritto(it)) }

            // 2. every Voce of part 1 becomes its own Parlante "P1-Voce n": each attribution extracts a print.
            val voci1 = trascritti[0].voci.map { VoceRef(trascritti[0].incontroId, it.voceId) }
            runBlocking {
                voci1.forEach { v -> c.parlanti.comandi.esegui(ComandoVoce.Nuovo(v, "P1-Voce ${v.voceId.numero}")) }
            }
            val composto = checkNotNull(sessione.progettoCorrente())
            attendiFinche(timeout = 10.minutes, messaggio = "impronte della parte 1") {
                composto.porte.parlanti.impronteDelProgetto(progetto.progettoId).size >= voci1.size
            }

            // 3. the app's own Proposta for every Voce of part 2.
            val proposte = trascritti[1].voci.associate { v ->
                v.voceId to c.parlanti.letture.proposta(VoceRef(trascritti[1].incontroId, v.voceId))
            }

            val sbobinature = Path.of(progetto.percorso).resolve("sbobinature")
            attendiFinche(timeout = 60.seconds, messaggio = "due sbobinature") {
                sbobinature.listDirectoryEntries("*.md").size == 2
            }
            sbobinature.listDirectoryEntries("*.md").forEach { it.copyTo(esito.resolve(it.name), overwrite = true) }
            esito.resolve("rapporto.md").writeText(
                rapporto(
                    parti,
                    trascritti,
                    tempi.map { it.inWholeSeconds },
                    proposte,
                    cronometro.elapsedNow().inWholeSeconds,
                ),
            )
            println(esito.resolve("rapporto.md").toFile().readText())
        } finally {
            sessione.chiudi()
        }
    }

    private fun rapporto(
        parti: List<Path>,
        trascritti: List<TrascrittoView>,
        secondiTrascrizione: List<Long>,
        proposte: Map<VoceId, snastro.parlanti.applicazione.letture.PropostaVista?>,
        secondiTotali: Long,
    ): String = buildString {
        appendLine("# Spike incontro — voci-tra-parti\n")
        trascritti.forEachIndexed { i, t ->
            val minuti = t.durataMs / MS_MIN
            appendLine("## Parte ${i + 1}: ${parti[i].name} — $minuti min, trascritta in ${secondiTrascrizione[i]} s")
            t.voci.forEach { v ->
                val suoi = t.segmenti.filter { it.voceId == v.voceId }
                val parlato = suoi.sumOf { it.fineMs - it.inizioMs } / MS_S
                appendLine("\n### ${v.etichetta} — ${suoi.size} segmenti, $parlato s di parlato")
                suoi.sortedByDescending { it.testo.length }.take(ESEMPI).sortedBy { it.inizioMs }.forEach { s ->
                    appendLine("- (${minuto(s.inizioMs)}) ${s.testo.take(TESTO_MAX)}")
                }
                if (i == 1) {
                    val candidati = proposte[v.voceId]?.candidati.orEmpty()
                    appendLine(
                        "- **Proposta:** " +
                            candidati.joinToString { "${it.nome} (${it.fascia})" }.ifEmpty { "nessun Candidato" },
                    )
                }
            }
            appendLine()
        }
        appendLine("Tempo totale dello spike: $secondiTotali s")
    }

    private fun importa(c: CollaboratoriProgetto, progettoId: ProgettoId, parte: Path) {
        val comando = AggiungiRegistrazione(progettoId, listOf(parte.toString()), Destinazione.NuovoIncontro)
        c.aggiungiRegistrazione(comando).atteso()
    }

    private fun minuto(ms: Long): String = "%d:%02d".format(ms / MS_MIN, ms / MS_S % SECONDI_MIN)

    private companion object {
        const val VARIABILE = "SNASTRO_SPIKE_INCONTRO_DIR"
        const val VARIABILE_PERSONE = "SNASTRO_SPIKE_PERSONE"
        val FINALI = setOf(StatoElaborazioneVista.COMPLETATA, StatoElaborazioneVista.FALLITA)
        const val MS_S = 1_000L
        const val MS_MIN = 60_000L
        const val SECONDI_MIN = 60
        const val ESEMPI = 3
        const val TESTO_MAX = 160
    }
}
