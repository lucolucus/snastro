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
    fun `INV-I18 un FORTE che condivide una Parte con A e comunque un rivale e toglie la coppia (D-0057)`() {
        // Voce 1 (P1 stampa A, P3 stampa B) e la Voce 2 (P2 stampa A) sono FORTE tra loro; la Voce 3 parla in P1 come
        // la Voce 1 ed e FORTE con lei (stampa B): A ha due FORTE, quindi nessuna coppia.
        val a = ambiente(fetta(1, P1, A), fetta(1, P3, B), fetta(3, P1, B), fetta(2, P2, A))

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
    fun `D-0057 parteA e parteB sono le Parti da cui suonano gli estratti, non le prime Parti delle Voci`() {
        // Voce 1 parla 1 unita in P1 e 2 in P2 (stampa B): l'estratto e da P2. Voce 2 parla in P3 (stampa B).
        val a = ambiente(fetta(1, P1, A), fetta(1, P2, B), fetta(1, P2, B), fetta(2, P3, B))

        val coppia = a.api.perIncontro(INCONTRO).single()

        assertEquals(VoceId(1), coppia.voceA)
        assertEquals(P2, coppia.estrattoA.registrazioneId)
        assertEquals(2, coppia.parteA)
        assertEquals(P3, coppia.estrattoB.registrazioneId)
        assertEquals(3, coppia.parteB)
    }

    @Test
    fun `D-0057 A resta la Voce della Parte precedente anche se il suo estratto suona da una Parte successiva`() {
        // Voce 1: P1 (1 unita) e P3 (2 unita), estratto da P3; Voce 2: solo P2. Sopravvive la Voce 1 (prima Parte 1).
        val a = ambiente(fetta(1, P1, A), fetta(1, P3, A), fetta(1, P3, A), fetta(2, P2, A))

        val coppia = a.api.perIncontro(INCONTRO).single()

        assertEquals(VoceId(1) to 3, coppia.voceA to coppia.parteA)
        assertEquals(VoceId(2) to 2, coppia.voceB to coppia.parteB)
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
    fun `INV-I18 un Incontro di esattamente due Parti propone la coppia`() {
        val a = ambiente(fetta(1, P1, A), fetta(2, P2, A), parti = listOf(P1, P2))

        val coppia = a.api.perIncontro(INCONTRO).single()

        assertEquals(VoceId(1) to VoceId(2), coppia.voceA to coppia.voceB)
    }

    @Test
    fun `AC-I48 un Incontro di una sola Parte non estrae nessuna impronta`() {
        val a = ambiente(fetta(1, P1, A), fetta(2, P1, A), parti = listOf(P1))

        assertEquals(emptyList(), a.api.perIncontro(INCONTRO))
        assertEquals(0, a.estrazioni)
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
    fun `AC-I49 un calcolo annullato dopo la prima estrazione non lascia voci in cache e il successivo ricalcola`() {
        val a = ambiente(fetta(1, P1, A), fetta(2, P2, A))
        a.annullaAllaEstrazione = 2

        assertFailsWith<InterruptedException> { a.api.perIncontro(INCONTRO) }
        assertEquals(1, a.estrazioni, "la prima fetta era gia estratta")
        val coppie = a.api.perIncontro(INCONTRO)

        assertEquals(1, coppie.size)
        assertEquals(3, a.estrazioni, "ricalcolato da capo, nulla in cache")
    }

    @Test
    fun `AC-I48 un errore dell'estrattore esce da perIncontro e non lascia voci in cache`() {
        val a = ambiente(fetta(1, P1, A), fetta(2, P2, A))
        a.fallisciAllaEstrazione = 2

        assertFailsWith<IllegalStateException> { a.api.perIncontro(INCONTRO) }

        assertEquals(1, a.api.perIncontro(INCONTRO).size)
        assertEquals(3, a.estrazioni)
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
        // Many Incontri written at once from every thread, released together by a start barrier, several rounds: a
        // plain map breaks (ConcurrentModificationException) or loses entries on concurrent resizes.
        repeat(GIRI_CONCORRENTI) { giro -> giroConcorrente(giro) }
    }

    private fun giroConcorrente(giro: Int) {
        val incontri = List(INCONTRI_CONCORRENTI) { IncontroId("incontro-c$it") }
        val a = ambiente(fetta(1, P1, A), fetta(2, P2, A), incontri = incontri)
        val pool = java.util.concurrent.Executors.newFixedThreadPool(THREAD)
        val via = java.util.concurrent.CyclicBarrier(THREAD)
        try {
            val lavori = List(THREAD) { t ->
                pool.submit<List<Int>> {
                    via.await()
                    incontri.shuffled(kotlin.random.Random(giro * THREAD + t)).map { i ->
                        if (t == 0 && i.valore.endsWith("7")) a.api.invalida(i)
                        a.api.perIncontro(i).size
                    }
                }
            }
            val attesi = List(incontri.size) { 1 }
            lavori.forEach { assertEquals(attesi, it.get(30, java.util.concurrent.TimeUnit.SECONDS)) }
        } finally {
            pool.shutdownNow()
        }

        val dopo = a.estrazioni
        incontri.forEach { assertEquals(1, a.api.perIncontro(it).size) }
        assertEquals(dopo, a.estrazioni, "giro $giro: ogni Incontro calcolato e rimasto in cache")
    }

    // --- fixture -----------------------------------------------------------------------------------------------

    private data class Fetta(val voce: Int, val parte: RegistrazioneId, val stampa: Int)

    private fun fetta(voce: Int, parte: RegistrazioneId, stampa: Int) = Fetta(voce, parte, stampa)

    private fun ambiente(
        vararg fette: Fetta,
        debole: Set<Int> = emptySet(),
        parti: List<RegistrazioneId> = listOf(P1, P2, P3),
        incontri: List<IncontroId> = listOf(INCONTRO),
    ) = Ambiente(fette.toList(), debole, parti, incontri)

    /**
     * Each slice is one interval whose start encodes its index; the extractor maps the index to the slice's print.
     * Every Incontro of [incontri] has the same Voci; [INCONTRO]'s Parti are [parti], another's are prefixed by its id.
     */
    private class Ambiente(
        private val fette: List<Fetta>,
        debole: Set<Int>,
        parti: List<RegistrazioneId>,
        incontri: List<IncontroId>,
    ) {
        private val contatore = java.util.concurrent.atomic.AtomicInteger()
        val estrazioni: Int get() = contatore.get()
        var annullaAllaProssima = false

        /** 1-based ordinal of the call to [EstrattoreImpronta.estrai] that throws [InterruptedException]; 0 = none. */
        var annullaAllaEstrazione = 0

        /** 1-based ordinal of the call to [EstrattoreImpronta.estrai] that fails with an [IllegalStateException]. */
        var fallisciAllaEstrazione = 0
        private var chiamate = 0

        @Volatile var durante: () -> Unit = {}

        val attribuzioni = AttribuzioneRepositoryFinta()

        private val impronte = STAMPE.map { Impronta(it) }
        private val indici = fette.mapIndexed { i, f -> f to i }
        private fun parteDi(incontro: IncontroId, parte: RegistrazioneId) =
            if (incontro == INCONTRO) parte else RegistrazioneId("${incontro.valore}/${parte.valore}")
        private fun vociDi(incontro: IncontroId) = fette.groupBy { it.voce }.map { (n, righe) ->
            VoceVista(
                VoceRef(incontro, VoceId(n)),
                righe.groupBy { parteDi(incontro, it.parte) }
                    .mapValues { (_, r) -> r.map { intervallo(indiceDi(it)) } },
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
                val n = synchronized(this@Ambiente) { ++chiamate }
                if (annullaAllaProssima || n == annullaAllaEstrazione) {
                    annullaAllaProssima = false
                    throw InterruptedException()
                }
                check(n != fallisciAllaEstrazione) { "estrazione fallita" }
                contatore.incrementAndGet()
                durante()
                return impronte[indici[c.campioni[0].toInt()].first.stampa]
            }
        }
        private val registrazioni = LettoreRegistrazioneFinta(
            incontri.flatMap { i -> parti.map { parteDi(i, it) to i } }
                .associate { (r, i) -> r to unaRegistrazioneVista(r, PROGETTO).copy(incontroId = i) },
        )
        private val lettoreVoci = LettoreVociFinta(incontri.associateWith { vociDi(it) })

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
        const val INCONTRI_CONCORRENTI = 2_000
        const val GIRI_CONCORRENTI = 5
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
