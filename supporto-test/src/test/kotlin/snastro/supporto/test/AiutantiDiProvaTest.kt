package snastro.supporto.test

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** AC-C12: [attendiFinche], [OrologioFinto], [conScopeDiProva]; CR-19a: [restaVeroPer], [pausaInTempoReale]. */
class AiutantiDiProvaTest {
    private val inizio = Instant.parse("2026-09-27T10:00:00Z")

    @Test
    fun `AC-C12 attendiFinche ritorna appena la condizione diventa vera`() {
        var controlli = 0
        val partenza = TimeSource.Monotonic.markNow()

        attendiFinche(timeout = 5.seconds, messaggio = "mai vera") { ++controlli >= 3 }

        assertEquals(3, controlli)
        assertTrue(partenza.elapsedNow() < 1.seconds)
    }

    @Test
    fun `AC-C12 attendiFinche fallisce dopo il timeout con un AssertionError che contiene il messaggio`() {
        val partenza = TimeSource.Monotonic.markNow()

        val errore = assertFailsWith<AssertionError> {
            attendiFinche(timeout = 50.milliseconds, messaggio = "il lavoro non e finito") { false }
        }

        assertTrue("il lavoro non e finito" in errore.message.orEmpty(), errore.message)
        assertTrue(partenza.elapsedNow() >= 50.milliseconds)
    }

    @Test
    fun `AC-C12 OrologioFinto parte da inizio, avanza, si imposta ed e in UTC`() {
        val orologio = OrologioFinto(inizio)
        assertEquals(inizio, orologio.instant())
        assertEquals(ZoneOffset.UTC, orologio.zone)

        orologio.avanza(5.seconds)
        assertEquals(inizio.plusSeconds(5), orologio.instant())

        val altro = Instant.parse("2030-01-01T00:00:00Z")
        orologio.imposta(altro)
        assertEquals(altro, orologio.instant())
    }

    @Test
    fun `AC-C12 OrologioFinto in un altra zona segue lo stesso istante`() {
        val orologio = OrologioFinto(inizio)
        val roma = orologio.withZone(ZoneId.of("Europe/Rome"))

        orologio.avanza(1.seconds)

        assertEquals(ZoneId.of("Europe/Rome"), roma.zone)
        assertEquals(inizio.plusSeconds(1), roma.instant())
    }

    @Test
    fun `AC-C12 conScopeDiProva cancella lo scope anche quando il blocco fallisce un asserzione`() {
        var visto: CoroutineScope? = null

        assertFailsWith<AssertionError> {
            conScopeDiProva { scope ->
                visto = scope
                throw AssertionError("fallita")
            }
        }

        assertFalse(checkNotNull(visto).isActive)
    }

    @Test
    fun `AC-C12 conScopeDiProva restituisce il valore del blocco e cancella lo scope`() {
        var visto: CoroutineScope? = null

        val valore = conScopeDiProva { scope ->
            visto = scope
            assertTrue(scope.isActive)
            42
        }

        assertEquals(42, valore)
        assertFalse(checkNotNull(visto).isActive)
    }

    @Test
    fun `CR-19a restaVeroPer dura tutta la finestra e controlla piu volte`() {
        var controlli = 0
        val partenza = TimeSource.Monotonic.markNow()

        restaVeroPer(durata = 100.milliseconds, messaggio = "cambiata") { ++controlli > 0 }

        assertTrue(partenza.elapsedNow() >= 100.milliseconds)
        assertTrue(controlli > 2, "controllata solo $controlli volte")
    }

    @Test
    fun `CR-19a restaVeroPer fallisce appena la condizione diventa falsa, senza aspettare la fine`() {
        var controlli = 0
        val partenza = TimeSource.Monotonic.markNow()

        val errore = assertFailsWith<AssertionError> {
            restaVeroPer(durata = 5.seconds, messaggio = "qualcosa e successo") { ++controlli < 3 }
        }

        assertTrue("qualcosa e successo" in errore.message.orEmpty(), errore.message)
        assertTrue(partenza.elapsedNow() < 1.seconds)
    }

    @Test
    fun `CR-19a pausaInTempoReale aspetta la durata`() {
        val partenza = TimeSource.Monotonic.markNow()

        pausaInTempoReale(30.milliseconds, motivo = "il tempo reale e il soggetto")

        assertTrue(partenza.elapsedNow() >= 30.milliseconds)
    }

    @Test
    fun `CR-19a pausaInTempoReale rifiuta un motivo vuoto`() {
        assertFailsWith<IllegalArgumentException> { pausaInTempoReale(1.milliseconds, motivo = " ") }
    }

    @Test
    fun `CR-19a pausaInTempoReale rifiuta una durata negativa`() {
        assertFailsWith<IllegalArgumentException> { pausaInTempoReale((-1).milliseconds, motivo = "prova") }
    }
}
