package snastro.parlanti.applicazione.letture

import snastro.kernel.CampioniAudio
import snastro.kernel.IncontroId
import snastro.kernel.IntervalloMs
import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.parlanti.applicazione.porte.AttribuzioneRepositoryFinta
import snastro.parlanti.applicazione.porte.ConfrontoImpronteFinta
import snastro.parlanti.applicazione.porte.DecodificatoreAudio
import snastro.parlanti.applicazione.porte.EstrattoreImpronta
import snastro.parlanti.applicazione.porte.Fascia
import snastro.parlanti.applicazione.porte.LettoreRegistrazioneFinta
import snastro.parlanti.applicazione.porte.LettoreVociFinta
import snastro.parlanti.applicazione.porte.VoceVista
import snastro.parlanti.applicazione.porte.unaRegistrazioneVista
import snastro.parlanti.dominio.Attribuzione
import snastro.parlanti.dominio.Impronta
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** [PropostaTraParti] on fake prints: [INV-I18] (table), AC-I48 (one extraction per slice), AC-I49 (no writes). */
class PropostaTraPartiTest {

    @Test
    fun `INV-I18 due Voci non attribuite di Parti diverse, ciascuna unico FORTE dell'altra, formano una coppia`() {
        val a = ambiente(fetta(1, P1, A), fetta(2, P2, A))

        val coppia = a.api.perIncontro(INCONTRO).single()

        assertEquals(VoceId(1), coppia.voceA)
        assertEquals(1, coppia.parteA)
        assertEquals(P1, coppia.estrattoA.registrazioneId)
        assertEquals(VoceId(2), coppia.voceB)
        assertEquals(2, coppia.parteB)
        assertEquals(P2, coppia.estrattoB.registrazioneId)
    }

    @Test
    fun `INV-I18 la Voce della Parte precedente e sempre A anche se elencata dopo`() {
        val coppia = ambiente(fetta(1, P2, A), fetta(2, P1, A)).api.perIncontro(INCONTRO).single()

        assertEquals(VoceId(2), coppia.voceA)
        assertEquals(1, coppia.parteA)
        assertEquals(VoceId(1), coppia.voceB)
        assertEquals(2, coppia.parteB)
    }

    @Test
    fun `INV-I18 A con due FORTE non ha nulla`() {
        val a = ambiente(fetta(1, P1, A), fetta(2, P2, A), fetta(3, P3, A))

        assertEquals(emptyList(), a.api.perIncontro(INCONTRO))
    }

    @Test
    fun `INV-I18 B e il FORTE di A ma A non e l'unico FORTE di B quindi nulla`() {
        // Voce 1 e Voce 3 parlano nella stessa Parte: entrambe hanno come unico FORTE la Voce 2, che ne ha due.
        val a = ambiente(fetta(1, P1, A), fetta(3, P1, A), fetta(2, P2, A))

        assertEquals(emptyList(), a.api.perIncontro(INCONTRO))
    }

    @Test
    fun `INV-I18 un FORTE gia in un'altra coppia non produce nulla`() {
        // Voce 2 e FORTE con la 1 (stampa A) e con la 3 (stampa B): nessuna delle due la ottiene in esclusiva.
        val a = ambiente(fetta(1, P1, A), fetta(3, P1, B), fetta(2, P2, A), fetta(2, P3, B))

        assertEquals(emptyList(), a.api.perIncontro(INCONTRO))
    }

    @Test
    fun `INV-I18 solo DEBOLE non propone nulla`() {
        val a = ambiente(fetta(1, P1, A), fetta(2, P2, B), debole = setOf(A, B))

        assertEquals(emptyList(), a.api.perIncontro(INCONTRO))
    }

    @Test
    fun `INV-I18 una Voce attribuita esclude la coppia`() {
        val a = ambiente(fetta(1, P1, A), fetta(2, P2, A))
        a.attribuzioni.salva(Attribuzione.conferma(VoceRef(INCONTRO, VoceId(2)), PROGETTO, ParlanteId("p-1")).aggregato)

        assertEquals(emptyList(), a.api.perIncontro(INCONTRO))
    }

    @Test
    fun `INV-I18 Voci che condividono una Parte non si accoppiano`() {
        val a = ambiente(fetta(1, P1, A), fetta(2, P1, A))

        assertEquals(emptyList(), a.api.perIncontro(INCONTRO))
    }

    @Test
    fun `AC-I48 la somiglianza e la migliore sulle fette (Voce, Parte) e si estrae una volta per fetta`() {
        // Voce 1 in Parti 1 e 2 (stampe A e B), Voce 2 in Parte 3 (stampa B): solo la coppia (P2, P3) e FORTE.
        val a = ambiente(fetta(1, P1, A), fetta(1, P2, B), fetta(2, P3, B))

        val coppia = a.api.perIncontro(INCONTRO).single()

        assertEquals(VoceId(1), coppia.voceA)
        assertEquals(1, coppia.parteA)
        assertEquals(3, coppia.parteB)
        assertEquals(3, a.estrazioni)
    }

    @Test
    fun `AC-I48 il risultato e in cache e si ricalcola, con una estrazione per fetta, dopo invalida`() {
        val a = ambiente(fetta(1, P1, A), fetta(2, P2, A))

        a.api.perIncontro(INCONTRO)
        a.api.perIncontro(INCONTRO)
        assertEquals(2, a.estrazioni)

        a.api.invalida(INCONTRO)
        a.api.perIncontro(INCONTRO)
        assertEquals(4, a.estrazioni)
    }

    @Test
    fun `AC-I48 le Voci attribuite non vengono estratte`() {
        val a = ambiente(fetta(1, P1, A), fetta(2, P2, A), fetta(3, P3, B))
        a.attribuzioni.salva(Attribuzione.conferma(VoceRef(INCONTRO, VoceId(3)), PROGETTO, ParlanteId("p-1")).aggregato)

        a.api.perIncontro(INCONTRO)

        assertEquals(2, a.estrazioni)
    }

    @Test
    fun `AC-I49 non scrive nulla`() {
        val a = ambiente(fetta(1, P1, A), fetta(2, P2, A))
        a.attribuzioni.salva(Attribuzione.conferma(VoceRef(INCONTRO, VoceId(9)), PROGETTO, ParlanteId("p-1")).aggregato)
        val prima = a.attribuzioni.diIncontro(INCONTRO).map { it.voceRef to it.parlanteId }

        a.api.perIncontro(INCONTRO)

        assertEquals(prima, a.attribuzioni.diIncontro(INCONTRO).map { it.voceRef to it.parlanteId })
    }

    @Test
    fun `AC-I49 un calcolo annullato non lascia voci in cache e il successivo ricalcola`() {
        val a = ambiente(fetta(1, P1, A), fetta(2, P2, A))
        a.annullaAllaProssima = true

        assertFailsWith<InterruptedException> { a.api.perIncontro(INCONTRO) }
        val coppie = a.api.perIncontro(INCONTRO)

        assertEquals(1, coppie.size)
        assertEquals(2, a.estrazioni)
    }

    @Test
    fun `AC-I49 un invalida durante il calcolo scarta il risultato, non resta in cache e il successivo ricalcola`() {
        val a = ambiente(fetta(1, P1, A), fetta(2, P2, A))
        a.durante = {
            a.api.invalida(INCONTRO)
            a.durante = {}
        }

        a.api.perIncontro(INCONTRO) // computed from pre-invalida data: returned, but never stored
        assertEquals(2, a.estrazioni)
        a.api.perIncontro(INCONTRO)

        assertEquals(4, a.estrazioni, "il risultato calcolato prima dell'invalida non e stato memorizzato")
        a.api.perIncontro(INCONTRO)
        assertEquals(4, a.estrazioni, "il calcolo successivo e in cache")
    }

    @Test
    fun `AC-I49 accessi concorrenti a perIncontro e invalida non corrompono la cache`() {
        val a = ambiente(fetta(1, P1, A), fetta(2, P2, A))
        val pool = java.util.concurrent.Executors.newFixedThreadPool(THREAD)
        try {
            val lavori = List(THREAD * 20) { i ->
                pool.submit<Int> {
                    if (i % 3 == 0) a.api.invalida(INCONTRO)
                    a.api.perIncontro(INCONTRO).size
                }
            }
            lavori.forEach { assertEquals(1, it.get(10, java.util.concurrent.TimeUnit.SECONDS)) }
        } finally {
            pool.shutdownNow()
        }
    }

    // --- fixture -----------------------------------------------------------------------------------------------

    private data class Fetta(val voce: Int, val parte: RegistrazioneId, val stampa: Int)

    private fun fetta(voce: Int, parte: RegistrazioneId, stampa: Int) = Fetta(voce, parte, stampa)

    private fun ambiente(vararg fette: Fetta, debole: Set<Int> = emptySet()) = Ambiente(fette.toList(), debole)

    /** Each slice is one interval whose start encodes its index; the extractor maps the index to the slice's print. */
    private class Ambiente(fette: List<Fetta>, debole: Set<Int>) {
        var estrazioni = 0
        var annullaAllaProssima = false

        @Volatile var durante: () -> Unit = {}

        val attribuzioni = AttribuzioneRepositoryFinta()

        private val impronte = STAMPE.map { Impronta(it) }
        private val indici = fette.mapIndexed { i, f -> f to i }
        private val voci = fette.groupBy { it.voce }.map { (n, righe) ->
            VoceVista(
                VoceRef(INCONTRO, VoceId(n)),
                righe.groupBy { it.parte }.mapValues { (_, r) -> r.map { intervallo(indiceDi(it)) } },
            )
        }
        private fun indiceDi(f: Fetta) = indici.first { it.first === f }.second

        private val decodificatore = object : DecodificatoreAudio {
            override fun campioni(id: RegistrazioneId, intervalli: List<IntervalloMs>) =
                CampioniAudio(floatArrayOf((intervalli.first().inizioMs / UNITA).toFloat()))
        }
        private val estrattore = object : EstrattoreImpronta {
            override val modello = "finto"

            override fun estrai(c: CampioniAudio): Impronta {
                if (annullaAllaProssima) {
                    annullaAllaProssima = false
                    throw InterruptedException()
                }
                synchronized(this@Ambiente) { estrazioni++ }
                durante()
                return impronte[indici[c.campioni[0].toInt()].first.stampa]
            }
        }
        private val registrazioni = LettoreRegistrazioneFinta(
            listOf(P1, P2, P3).associateWith { unaRegistrazioneVista(it, PROGETTO).copy(incontroId = INCONTRO) },
        )
        private val lettoreVoci = LettoreVociFinta(mapOf(INCONTRO to voci))

        val api = PropostaTraParti(
            lettoreVoci,
            registrazioni,
            attribuzioni,
            decodificatore,
            estrattore,
            ConfrontoImpronteFinta(debole.associate { impronte[it] to Fascia.DEBOLE }),
            EstrattoAudio(lettoreVoci, registrazioni),
        )

        private fun intervallo(i: Int) = IntervalloMs(i * UNITA, i * UNITA + UNITA)
    }

    private companion object {
        const val UNITA = 1_000L
        const val THREAD = 8
        const val A = 0
        const val B = 1
        val STAMPE = listOf(floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 1f, 0f))
        val PROGETTO = ProgettoId("progetto-1")
        val INCONTRO = IncontroId("incontro-1")
        val P1 = RegistrazioneId("parte-1")
        val P2 = RegistrazioneId("parte-2")
        val P3 = RegistrazioneId("parte-3")
    }
}
