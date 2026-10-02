package snastro.avvio.sintesi

import org.junit.jupiter.api.io.TempDir
import snastro.avvio.progetto.AmbienteProgetto
import snastro.sintesi.applicazione.letture.RiassuntoMostrato
import snastro.sintesi.applicazione.letture.RiassuntoVista
import snastro.supporto.test.attendiFinche
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * INV-I13 / INV-I16 (release blocker of I1) end-to-end on the REAL composition and SQLite: segment ids are never
 * reused, so after a Ritrascrivi of the same length every Segmento a pronto Riassunto cites is gone. The view of the
 * now superato Riassunto renders (never throws), its Fonti marked not present.
 */
class RiassuntoVistaDopoRitrascriviTest {
    @TempDir
    lateinit var radice: Path

    @Test
    fun `una Ritrascrivi della stessa lunghezza lascia la vista superata con le Fonti non piu presenti`() {
        AmbienteProgetto(radice).use {
            val a = it.registrazioneTrascritta()
            it.riassumi(a, "budget")
            it.attendiPronto(a)
            val prima = checkNotNull(it.sintesi.vista(a)?.mostrato)
            assertEquals(false, prima.superato)
            val fontiPrima = prima.fonti()
            assertTrue(fontiPrima.isNotEmpty() && fontiPrima.all { f -> f.segmentoPresente && f.inizioMs != null })

            it.avviaElaborazione(a) // the same two Voci: same length, new segment ids (INV-I16)
            attendiFinche(timeout = 10.seconds, messaggio = "il Riassunto diventa superato") {
                it.sintesi.vista(a)?.mostrato?.superato == true
            }

            val vista: RiassuntoVista = assertNotNull(it.sintesi.vista(a))
            val dopo = checkNotNull(vista.mostrato)
            assertTrue(dopo.superato)
            assertEquals(1, vista.numParti)
            val fontiDopo = dopo.fonti()
            assertEquals(fontiPrima.size, fontiDopo.size, "le Fonti restano, nessuna eccezione")
            assertTrue(fontiDopo.all { f -> !f.segmentoPresente && f.inizioMs == null && f.numeroParte == 1 })
        }
    }

    private fun RiassuntoMostrato.fonti() =
        decisioni.flatMap { e -> e.fonti } +
            questioniAperte.flatMap { e -> e.fonti } +
            azioni.flatMap { x -> x.fonti } +
            puntiChiave.flatMap { x -> x.fonti }
}
