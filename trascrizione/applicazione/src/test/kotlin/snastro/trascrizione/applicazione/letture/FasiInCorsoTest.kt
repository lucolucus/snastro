package snastro.trascrizione.applicazione.letture

import snastro.kernel.RegistrazioneId
import snastro.trascrizione.applicazione.porte.FaseElaborazione.ALLINEAMENTO
import snastro.trascrizione.applicazione.porte.FaseElaborazione.DECODIFICA
import snastro.trascrizione.applicazione.porte.FaseElaborazione.DIARIZZAZIONE
import snastro.trascrizione.applicazione.porte.SegnalatoreFase
import snastro.trascrizione.applicazione.porte.SegnalatoreFaseContratto
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** D2: [FasiInCorso] honors `SegnalatoreFaseContratto` (AC-36) plus its own read side, [FasiInCorso.faseDi]. */
class FasiInCorsoTest : SegnalatoreFaseContratto() {
    override fun segnalatore(): SegnalatoreFase = FasiInCorso()

    @Test
    fun `AC-164 faseDi e nullo prima di ogni segnale`() {
        val fasi = FasiInCorso()

        assertNull(fasi.faseDi(REGISTRAZIONE))
    }

    @Test
    fun `AC-164 faseDi riflette l ultima fase segnalata per quella Registrazione`() {
        val fasi = FasiInCorso()
        val altra = RegistrazioneId("registrazione-2")
        fasi.fase(altra, DECODIFICA)

        fasi.fase(REGISTRAZIONE, DECODIFICA)
        fasi.fase(REGISTRAZIONE, DIARIZZAZIONE)

        assertEquals(DIARIZZAZIONE, fasi.faseDi(REGISTRAZIONE))
        assertEquals(DECODIFICA, fasi.faseDi(altra))
    }

    @Test
    fun `AC-164 terminata rimuove la fase in corso per quella Registrazione soltanto`() {
        val fasi = FasiInCorso()
        val altra = RegistrazioneId("registrazione-2")
        fasi.fase(REGISTRAZIONE, ALLINEAMENTO)
        fasi.fase(altra, ALLINEAMENTO)

        fasi.terminata(REGISTRAZIONE)

        assertNull(fasi.faseDi(REGISTRAZIONE))
        assertEquals(ALLINEAMENTO, fasi.faseDi(altra))
    }

    @Test
    fun `terminata senza fasi precedenti non fallisce`() {
        val fasi = FasiInCorso()

        fasi.terminata(REGISTRAZIONE)

        assertNull(fasi.faseDi(REGISTRAZIONE))
    }

    /**
     * Thread-safety: many concurrent writers signalling different Registrazioni never corrupt or lose an
     * entry — the pipeline thread writes, a presenter thread could read concurrently, ConcurrentHashMap
     * guarantees every single-key operation is atomic and visible (ADR 0004, the thread-safety this block
     * owns for the shared [FasiInCorso]).
     */
    @Test
    fun `scritture concorrenti su Registrazioni diverse non si perdono ne si corrompono`() {
        val fasi = FasiInCorso()
        val numero = 200
        val id = { i: Int -> RegistrazioneId("registrazione-$i") }
        val esecutore = Executors.newFixedThreadPool(8)
        val partenza = CountDownLatch(1)
        val pronte = CountDownLatch(numero)
        try {
            repeat(numero) { i ->
                esecutore.submit {
                    partenza.await()
                    fasi.fase(id(i), DIARIZZAZIONE)
                    pronte.countDown()
                }
            }
            partenza.countDown()
            check(pronte.await(10, TimeUnit.SECONDS)) { "le scritture concorrenti non si sono concluse in tempo" }
        } finally {
            esecutore.shutdown()
        }

        repeat(numero) { i -> assertEquals(DIARIZZAZIONE, fasi.faseDi(id(i))) }
    }

    private companion object {
        val REGISTRAZIONE = RegistrazioneId("registrazione-1")
    }
}
