package snastro.trascrizione.adattatori.ml

import snastro.kernel.CampioniAudio
import snastro.kernel.atteso
import snastro.ml.ConfigDiarizzazione
import snastro.ml.ConfigSessione
import snastro.ml.ModelloEmbeddingFinto
import snastro.ml.MotoreSherpa
import snastro.ml.SegmentoDiarizzazione
import snastro.ml.motoreSherpaSenzaNativi
import snastro.ml.sessioni
import snastro.trascrizione.applicazione.porte.Turno
import snastro.trascrizione.dominio.NumeroPersone
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [DiarizzatoreSherpa]'s orchestration of ADR 0019 §1.2 on the REAL Mutex and sessions of a native-free
 * [MotoreSherpa] (gate): a fake step 1 and a fake piece model. The audio encodes the speaker as a constant
 * sample value per region; the fake model turns that value into a one-hot embedding.
 */
class DiarizzatoreSherpaPipelineTest {
    private val modello = ModelloEmbeddingFinto { campioni ->
        val voce = campioni.average().roundToInt()
        FloatArray(DIMENSIONE) { if (it == voce) 1f else 0f }
    }

    /** Speakers 1 and 2, alternating every [TURNO_S] s over [DURATA_S] s; step 1 = one segment per turn. */
    private val audio = CampioniAudio(FloatArray(DURATA_S * HZ) { i -> if ((i / HZ / TURNO_S) % 2 == 0) 1f else 2f })
    private val segmenti = (0 until DURATA_S / TURNO_S).map { t ->
        SegmentoDiarizzazione((t * TURNO_S).toFloat(), ((t + 1) * TURNO_S).toFloat(), speaker = t % 3)
    }

    private fun diarizzatore(
        motore: MotoreSherpa = motoreSherpaSenzaNativi(),
        passo1: (ConfigDiarizzazione) -> List<SegmentoDiarizzazione> = { segmenti },
        raggruppa: (List<Pezzo>, List<DoubleArray>, Int?) -> IntArray = RaggruppamentoVoci::voci,
    ) = DiarizzatoreSherpa(
        motore,
        ConfigSessione(emptyList(), threadIntraOp = 1),
        modello.su(motore),
        passo1 = { _, impostazioni -> passo1(impostazioni) },
        raggruppa = raggruppa,
    )

    @Test
    fun `AC-373 il passo 1 e configurato numClusters -1 soglia 0,2 wsr 0,5 on 0,3 off 0,5`() {
        var ricevute: ConfigDiarizzazione? = null

        diarizzatore(
            passo1 = {
                ricevute = it
                segmenti
            },
        ).diarizza(audio, null)

        assertEquals(ConfigDiarizzazione(-1, 0.2f, 0.5f, 0.3f, 0.5f), ricevute)
    }

    @Test
    fun `AC-373 le etichette del passo 1 non sono mai usate`() {
        val altreEtichette = segmenti.map { it.copy(speaker = 0) }

        val con = diarizzatore().diarizza(audio, NumeroPersone.di(2).atteso())
        val senza = diarizzatore(passo1 = { altreEtichette }).diarizza(audio, NumeroPersone.di(2).atteso())

        assertEquals(con, senza)
    }

    @Test
    fun `AC-373 k 2 da 2 voci, un k oltre le voci reali non fallisce e non supera k, senza k taglio automatico`() {
        val k2 = diarizzatore().diarizza(audio, NumeroPersone.di(2).atteso())
        val k10 = diarizzatore().diarizza(audio, NumeroPersone.di(10).atteso())
        val auto = diarizzatore().diarizza(audio, null)

        assertEquals(2, voci(k2))
        assertTrue(voci(k10) in 1..10)
        assertEquals(2, voci(auto))
        assertEquals(voceDi(k2, 0), voceDi(k2, 2 * TURNO_S * 1000L), "turni 1 e 3: stessa persona")
    }

    @Test
    fun `AC-484 due istanze danno gli stessi Turni`() {
        assertEquals(diarizzatore().diarizza(audio, null), diarizzatore().diarizza(audio, null))
    }

    @Test
    fun `AC-485 INV-7 due segmenti sovrapposti danno entrambi i loro Turni, ognuno con inizio minore di fine`() {
        val sovrapposti = listOf(SegmentoDiarizzazione(0f, 4f, 0), SegmentoDiarizzazione(2f, 6f, 1))

        val turni = diarizzatore(passo1 = { sovrapposti }).diarizza(audio, null)

        assertEquals(
            listOf(0L to 2000L, 2000L to 4000L, 2000L to 4000L, 4000L to 6000L),
            turni.map { it.intervallo.inizioMs to it.intervallo.fineMs },
        )
        assertTrue(turni.all { it.intervallo.inizioMs < it.intervallo.fineMs })
    }

    @Test
    fun `AC-482 nessun segmento dal passo 1 da nessun Turno`() {
        assertEquals(emptyList(), diarizzatore(passo1 = { emptyList() }).diarizza(audio, null))
    }

    @Test
    fun `AC-486 il passo 1 e UNA conSessione e ogni pezzo UNA conSessione, il raggruppamento a Mutex libero`() {
        val motore = motoreSherpaSenzaNativi()
        var chiamatePasso1 = 0
        var sondaLibera = false
        val altro = Executors.newSingleThreadExecutor()
        try {
            val d = diarizzatore(
                motore = motore,
                passo1 = {
                    chiamatePasso1++
                    segmenti
                },
                raggruppa = { pezzi, embedding, k ->
                    val sonda = altro.submit<Unit> { motore.conSessione(ConfigSessione(emptyList(), 1)) { } }
                    sonda.get(ATTESA_S, TimeUnit.SECONDS) // a TimeoutException fails the test
                    sondaLibera = true
                    RaggruppamentoVoci.voci(pezzi, embedding, k)
                },
            )

            val turni = d.diarizza(audio, null)

            assertEquals(1, chiamatePasso1)
            assertEquals(turni.size, modello.calcoli)
            assertEquals(1 + turni.size + 1, motore.sessioni, "passo 1 + un pezzo per sessione + la sonda")
            assertTrue(sondaLibera, "la sonda ottiene subito il Mutex durante il raggruppamento")
        } finally {
            altro.shutdownNow()
        }
    }

    @Test
    fun `AC-491 N diarizza caricano il modello dei pezzi una volta e close lo rilascia`() {
        val d = diarizzatore()

        d.diarizza(audio, null)
        d.diarizza(audio, NumeroPersone.di(2).atteso())
        d.close()

        assertEquals(1, modello.caricamenti)
        assertEquals(1, modello.rilasci)
    }

    private fun voci(turni: List<Turno>): Int = turni.map { it.voceIndice }.toSet().size

    private fun voceDi(turni: List<Turno>, inizioMs: Long): Int =
        turni.first { it.intervallo.inizioMs == inizioMs }.voceIndice

    private companion object {
        const val HZ = 16_000
        const val DURATA_S = 400
        const val TURNO_S = 100
        const val DIMENSIONE = 4
        const val ATTESA_S = 5L
    }
}
