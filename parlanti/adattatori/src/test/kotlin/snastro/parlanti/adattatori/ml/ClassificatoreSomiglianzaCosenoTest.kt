package snastro.parlanti.adattatori.ml

import snastro.kernel.ParlanteId
import snastro.parlanti.applicazione.porte.Classificazione
import snastro.parlanti.applicazione.porte.ClassificatoreSomiglianza
import snastro.parlanti.applicazione.porte.ClassificatoreSomiglianzaContratto
import snastro.parlanti.applicazione.porte.SoglieSomiglianza
import snastro.parlanti.dominio.Impronta
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [ClassificatoreSomiglianzaCoseno] against the consumer-driven contract (AC-496: it passes
 * [ClassificatoreSomiglianzaContratto] in the gate, no model involved — pure Kotlin) plus AC-497..500,
 * which pin the exact decision at the provisional thresholds.
 */
class ClassificatoreSomiglianzaCosenoTest : ClassificatoreSomiglianzaContratto() {
    override fun classificatore(): ClassificatoreSomiglianza = ClassificatoreSomiglianzaCoseno()

    @Test
    fun `AC-497 con soglie 0_30 e 0_05 e riferimenti unitari ortogonali A e B la decisione segue best e margine`() {
        val classificatore = ClassificatoreSomiglianzaCoseno(SoglieSomiglianza(minima = 0.30, margine = 0.05))
        val a = Impronta(floatArrayOf(1f, 0f))
        val b = Impronta(floatArrayOf(0f, 1f))

        // frase = A esattamente -> Sicura(A).
        assertEquals(
            listOf(Classificazione.Sicura(PARLANTE_A)),
            classificatore.classifica(mapOf(PARLANTE_A to listOf(a), PARLANTE_B to listOf(b)), listOf(a)),
        )

        // frase a 45 gradi (cos 0.707 / 0.707) -> Incerta (margine 0).
        val a45Gradi = Impronta(floatArrayOf((1.0 / sqrt(2.0)).toFloat(), (1.0 / sqrt(2.0)).toFloat()))
        assertEquals(
            listOf(Classificazione.Incerta),
            classificatore.classifica(mapOf(PARLANTE_A to listOf(a), PARLANTE_B to listOf(b)), listOf(a45Gradi)),
        )

        // cos(A) = 0.25, cos(B) = 0 -> Incerta (sotto la minima).
        val sottoMinima = vettore3D(cosA = 0.25, cosB = 0.0)
        assertEquals(
            listOf(Classificazione.Incerta),
            classificatore.classifica(mapOf(PARLANTE_A to listOf(a3D), PARLANTE_B to listOf(b3D)), listOf(sottoMinima)),
        )

        // best/second = 0.80 / 0.76 -> Incerta (margine 0.04 < 0.05); 0.80 / 0.70 -> Sicura(A) (margine 0.10).
        val fraseFissa = Impronta(floatArrayOf(1f, 0f))
        assertEquals(
            listOf(Classificazione.Incerta),
            classificatore.classifica(
                mapOf(PARLANTE_A to listOf(aDistanza(0.80)), PARLANTE_B to listOf(aDistanza(0.76))),
                listOf(fraseFissa),
            ),
        )
        assertEquals(
            listOf(Classificazione.Sicura(PARLANTE_A)),
            classificatore.classifica(
                mapOf(PARLANTE_A to listOf(aDistanza(0.80)), PARLANTE_B to listOf(aDistanza(0.70))),
                listOf(fraseFissa),
            ),
        )

        // tre riferimenti A, B, C con il migliore (C) avanti di 0.1 -> Sicura(C).
        assertEquals(
            listOf(Classificazione.Sicura(PARLANTE_C)),
            classificatore.classifica(
                mapOf(
                    PARLANTE_A to listOf(aDistanza(0.50)),
                    PARLANTE_B to listOf(aDistanza(0.55)),
                    PARLANTE_C to listOf(aDistanza(0.65)),
                ),
                listOf(fraseFissa),
            ),
        )
    }

    @Test
    fun `AC-498 il centroide e la media normalizzata dei riferimenti non il massimo di un singolo riferimento`() {
        val classificatore = ClassificatoreSomiglianzaCoseno(SoglieSomiglianza(minima = 0.30, margine = 0.05))
        // Due riferimenti di A gia unitari: (1,0) e (0.6,0.8) -> centroide proporzionale a (1.6, 0.8).
        val aRif1 = Impronta(floatArrayOf(1f, 0f))
        val aRif2 = Impronta(floatArrayOf(0.6f, 0.8f))
        val angoloCentroideA = atan2(0.8, 1.6)
        val frase = unitario(angoloCentroideA) // esattamente la direzione del centroide di A: cos(frase, centroideA) = 1.0

        // bRif e a coseno 0.90 dalla frase — piu vicino alla frase di quanto lo sia OGNUNO dei due
        // riferimenti singoli di A (la loro similarita con la frase e ~0.894, provata sotto per costruzione:
        // un centroide-direzione e equidistante in coseno dai due riferimenti che lo compongono).
        val bRif = unitario(angoloCentroideA + acos(0.90))

        val risultato = classificatore.classifica(
            riferimenti = mapOf(PARLANTE_A to listOf(aRif1, aRif2), PARLANTE_B to listOf(bRif)),
            frasi = listOf(frase),
        )

        // Sicura(A): il centroide di A (cos 1.0) batte l'unico riferimento di B (cos 0.90, margine 0.10),
        // anche se bRif e piu vicino alla frase dei singoli riferimenti di A presi uno a uno.
        assertEquals(listOf(Classificazione.Sicura(PARLANTE_A)), risultato)
    }

    @Test
    fun `AC-499 una frase non confrontabile e sempre Incerta senza eccezioni`() {
        val classificatore = ClassificatoreSomiglianzaCoseno()
        val riferimenti = mapOf(
            PARLANTE_A to listOf(Impronta(floatArrayOf(1f, 0f))),
            PARLANTE_B to listOf(Impronta(floatArrayOf(0f, 1f))),
        )
        val nonConfrontabili = listOf(
            Impronta(floatArrayOf(0f, 0f)),
            Impronta(floatArrayOf(Float.NaN, 0f)),
            Impronta(floatArrayOf(Float.POSITIVE_INFINITY, 0f)),
            Impronta(floatArrayOf(1f, 0f, 0f)), // dimensione diversa da quella dei riferimenti
        )

        assertEquals(
            List(nonConfrontabili.size) { Classificazione.Incerta },
            classificatore.classifica(riferimenti, nonConfrontabili),
        )
    }

    @Test
    fun `AC-499 un riferimento non confrontabile e ignorato nel centroide del suo Parlante`() {
        val classificatore = ClassificatoreSomiglianzaCoseno(SoglieSomiglianza(minima = 0.30, margine = 0.05))
        val riferimenti = mapOf(
            PARLANTE_A to listOf(Impronta(floatArrayOf(0f, 0f)), Impronta(floatArrayOf(1f, 0f))), // lo zero e ignorato
            PARLANTE_B to listOf(Impronta(floatArrayOf(0f, 1f))),
        )

        assertEquals(
            listOf(Classificazione.Sicura(PARLANTE_A)),
            classificatore.classifica(riferimenti, listOf(Impronta(floatArrayOf(1f, 0f)))),
        )
    }

    @Test
    fun `AC-499 un Parlante senza alcun riferimento confrontabile e escluso dalla classifica`() {
        val classificatore = ClassificatoreSomiglianzaCoseno(SoglieSomiglianza(minima = 0.30, margine = 0.05))
        val riferimenti = mapOf(
            PARLANTE_A to listOf(Impronta(floatArrayOf(1f, 0f))),
            PARLANTE_B to listOf(Impronta(floatArrayOf(0f, 0f)), Impronta(floatArrayOf(Float.NaN, 0f))),
        )

        // B e escluso: A resta l'unico candidato, il "secondo" manca e il margine e vacuamente soddisfatto.
        assertEquals(
            listOf(Classificazione.Sicura(PARLANTE_A)),
            classificatore.classifica(riferimenti, listOf(Impronta(floatArrayOf(1f, 0f)))),
        )
    }

    @Test
    fun `AC-500 le soglie provvisorie di default sono le costanti nominate 0_30 e 0_05`() {
        assertEquals(0.30, ClassificatoreSomiglianzaCoseno.SIMILARITA_MINIMA)
        assertEquals(0.05, ClassificatoreSomiglianzaCoseno.MARGINE_MINIMO)
    }

    private fun unitario(angoloRadianti: Double): Impronta =
        Impronta(floatArrayOf(cos(angoloRadianti).toFloat(), sin(angoloRadianti).toFloat()))

    /** A 2D reference at cosine [distanza] from the fixed frase (1, 0) — not required orthogonal to any other. */
    private fun aDistanza(distanza: Double): Impronta = unitario(acos(distanza))

    /** A 3D frase with EXACT cosine [cosA] to A = (1,0,0) and [cosB] to B = (0,1,0) (orthogonal unit references). */
    private fun vettore3D(cosA: Double, cosB: Double): Impronta {
        val resto = sqrt(maxOf(0.0, 1 - cosA * cosA - cosB * cosB))
        return Impronta(floatArrayOf(cosA.toFloat(), cosB.toFloat(), resto.toFloat()))
    }

    private companion object {
        val PARLANTE_A = ParlanteId("parlante-a")
        val PARLANTE_B = ParlanteId("parlante-b")
        val PARLANTE_C = ParlanteId("parlante-c")
        val a3D = Impronta(floatArrayOf(1f, 0f, 0f))
        val b3D = Impronta(floatArrayOf(0f, 1f, 0f))
    }
}
