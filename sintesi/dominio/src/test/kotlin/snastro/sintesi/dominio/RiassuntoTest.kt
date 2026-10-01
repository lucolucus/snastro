package snastro.sintesi.dominio

import snastro.kernel.Esito
import snastro.kernel.IncontroId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RiassuntoTest {
    private val bozzaValida = unaBozza(sommario = "{V1} apre", decisioni = listOf(unElemento("budget", 1)))

    private fun inStato(stato: StatoRiassunto): Riassunto = when (stato) {
        StatoRiassunto.IN_ATTESA -> unRiassunto()
        StatoRiassunto.IN_CORSO -> unRiassuntoInCorso()
        StatoRiassunto.PRONTO -> unRiassuntoInCorso().also { it.completa(bozzaValida, PARTE, unaStruttura()).atteso() }
        StatoRiassunto.FALLITO -> unRiassuntoInCorso().also { it.fallisci(MotivoFallimento.ERRORE_MODELLO).atteso() }
    }

    @Test
    fun `INV-S1 richiedi crea un Riassunto in_attesa aperto e restituisce RiassuntoRichiestoDominio`() {
        val creato = Riassunto.richiedi(
            RiassuntoId("id-1"),
            IncontroId("id-2"),
            Argomento.di("budget").atteso(),
            LunghezzaMassimaParole.di(LunghezzaMassimaParole.PREDEFINITA).atteso(),
            RICHIESTO_ALLE,
        )

        val r = creato.aggregato
        val atteso = RiassuntoRichiestoDominio(RiassuntoId("id-1"), IncontroId("id-2"), RICHIESTO_ALLE)
        assertEquals(atteso, creato.evento)
        assertEquals(StatoRiassunto.IN_ATTESA, r.stato)
        assertEquals("budget", r.argomento?.valore)
        assertEquals(RICHIESTO_ALLE, r.richiestoAlle)
        assertTrue(r.aperto && r.inAttesa && !r.inCorso && !r.pronto && !r.fallito)
    }

    @Test
    fun `INV-S1 da in_attesa solo avvia porta in_corso con avviatoAlle e RiassuntoAvviatoDominio`() {
        val r = unRiassunto()

        val evento = r.avvia(AVVIATO_ALLE).atteso()

        assertEquals(RiassuntoAvviatoDominio(r.id, r.incontroId, AVVIATO_ALLE), evento)
        assertEquals(AVVIATO_ALLE, r.avviatoAlle)
        assertTrue(r.aperto && r.inCorso && !r.inAttesa)
    }

    @Test
    fun `INV-S1 da in_corso completa porta a pronto terminale`() {
        val r = unRiassuntoInCorso()

        val conclusione = r.completa(bozzaValida, PARTE, unaStruttura()).atteso()

        assertEquals(ConclusioneRiassunto.Pronto(omessi = 0), conclusione)
        assertTrue(r.pronto && !r.aperto && !r.fallito)
    }

    @Test
    fun `INV-S1 da in_corso completa senza contenuto verificabile porta a fallito`() {
        val r = unRiassuntoInCorso()

        val conclusione = r.completa(unaBozza(), PARTE, unaStruttura()).atteso()

        assertEquals(ConclusioneRiassunto.Fallito(MotivoFallimento.NESSUN_CONTENUTO_VERIFICABILE), conclusione)
        assertTrue(r.fallito && !r.aperto)
        assertEquals(MotivoFallimento.NESSUN_CONTENUTO_VERIFICABILE, r.motivoFallimento)
    }

    @Test
    fun `INV-S1 da in_corso fallisci porta a fallito terminale e restituisce RiassuntoFallitoDominio`() {
        val r = unRiassuntoInCorso()

        val evento = r.fallisci(MotivoFallimento.INTERROTTO).atteso()

        assertEquals(RiassuntoFallitoDominio(r.id, r.incontroId, MotivoFallimento.INTERROTTO), evento)
        assertTrue(r.fallito)
        assertEquals(MotivoFallimento.INTERROTTO, r.motivoFallimento)
    }

    private data class Mossa(val da: StatoRiassunto, val verso: StatoRiassunto, val esegui: (Riassunto) -> Esito<*>)

    @Test
    fun `INV-S1 ogni altra mossa e TransizioneNonAmmessa e lascia stato e campi invariati`() {
        val avvia = { r: Riassunto -> r.avvia(AVVIATO_ALLE) }
        val completa = { r: Riassunto -> r.completa(bozzaValida, PARTE, unaStruttura()) }
        val fallisci = { r: Riassunto -> r.fallisci(MotivoFallimento.ERRORE_MODELLO) }
        val mosse = listOf(
            Mossa(StatoRiassunto.IN_ATTESA, StatoRiassunto.PRONTO, completa),
            Mossa(StatoRiassunto.IN_ATTESA, StatoRiassunto.FALLITO, fallisci),
            Mossa(StatoRiassunto.IN_CORSO, StatoRiassunto.IN_CORSO, avvia),
        ) + listOf(StatoRiassunto.PRONTO, StatoRiassunto.FALLITO).flatMap { terminale ->
            listOf(
                Mossa(terminale, StatoRiassunto.IN_CORSO, avvia),
                Mossa(terminale, StatoRiassunto.PRONTO, completa),
                Mossa(terminale, StatoRiassunto.FALLITO, fallisci),
            )
        }

        mosse.forEach { (da, verso, esegui) ->
            val r = inStato(da)
            val prima = Istantanea(r)

            val errore = esegui(r).erroreAtteso<ErroreSintesi.TransizioneNonAmmessa>()

            assertEquals(ErroreSintesi.TransizioneNonAmmessa(da.codice, verso.codice), errore, "$da -> $verso")
            assertEquals(prima, Istantanea(r), "$da -> $verso")
        }
    }

    @Test
    fun `INV-S1 il contenuto esiste solo in pronto e il motivo solo in fallito`() {
        StatoRiassunto.entries.forEach { stato ->
            val r = inStato(stato)
            val pronto = stato == StatoRiassunto.PRONTO

            assertEquals(pronto, r.sommario != null, "$stato sommario")
            assertEquals(pronto, r.decisioni.isNotEmpty(), "$stato decisioni")
            assertTrue(r.questioniAperte.isEmpty() && r.azioni.isEmpty() && r.puntiChiave.isEmpty(), "$stato")
            assertEquals(pronto, r.omessi != null, "$stato omessi")
            assertEquals(pronto, r.struttura != null, "$stato struttura")
            assertEquals(stato == StatoRiassunto.FALLITO, r.motivoFallimento != null, "$stato motivo")
        }
    }

    @Test
    fun `INV-S1 una ricostituzione con combinazione incoerente fallisce il require`() {
        val contenuto = EsitoVerifica(null, emptyList(), emptyList(), emptyList(), emptyList(), omessi = 0)
        val incoerenti = listOf<() -> Riassunto>(
            { ricostruisci(StatoRiassunto.FALLITO, motivo = null) },
            { ricostruisci(StatoRiassunto.IN_CORSO, motivo = MotivoFallimento.INTERROTTO) },
            { ricostruisci(StatoRiassunto.PRONTO, contenuto = null, struttura = "1:1") },
            { ricostruisci(StatoRiassunto.PRONTO, contenuto = contenuto, struttura = null) },
            { ricostruisci(StatoRiassunto.IN_CORSO, contenuto = contenuto, struttura = "1:1") },
            { ricostruisci(StatoRiassunto.FALLITO, MotivoFallimento.INTERROTTO, contenuto, "1:1") },
            { ricostruisci(StatoRiassunto.IN_CORSO, avviatoAlle = null) },
        )

        incoerenti.forEachIndexed { i, costruisci ->
            assertFailsWith<IllegalArgumentException>("caso $i") { costruisci() }
        }
        ricostruisci(StatoRiassunto.PRONTO, contenuto = contenuto, struttura = "1:1") // coherent: accepted
    }

    private fun ricostruisci(
        stato: StatoRiassunto,
        motivo: MotivoFallimento? = null,
        contenuto: EsitoVerifica? = null,
        struttura: String? = null,
        avviatoAlle: java.time.Instant? = AVVIATO_ALLE,
    ): Riassunto = Riassunto(
        RiassuntoId("id-1"), IncontroId("id-2"), null,
        LunghezzaMassimaParole.di(LunghezzaMassimaParole.PREDEFINITA).atteso(), RICHIESTO_ALLE,
        stato, avviatoAlle, motivo, contenuto, struttura,
    )

    @Test
    fun `INV-S10 la lunghezza massima richiesta si legge in ogni stato e nessun metodo la cambia`() {
        StatoRiassunto.entries.forEach { stato ->
            val r = inStato(stato)
            assertEquals(LunghezzaMassimaParole.PREDEFINITA, r.lunghezzaMassima.valore, "$stato")
        }
    }

    @Test
    fun `INV-S10 una risposta di 3000 parole con tetto 2000 resta pronto e intera`() {
        val r = unRiassuntoInCorso(parole = 2000)
        val lungo = List(3000) { "parola" }.joinToString(" ")

        val conclusione = r.completa(unaBozza(sommario = lungo), PARTE, unaStruttura()).atteso()

        assertEquals(ConclusioneRiassunto.Pronto(omessi = 0), conclusione)
        assertEquals(lungo, r.sommario?.testo?.codifica())
        assertEquals(2000, r.lunghezzaMassima.valore)
    }

    @Test
    fun `INV-S7 superato confronta la struttura corrente con quella memorizzata`() {
        val r = unRiassuntoInCorso()
        assertFalse(r.superato(PARTE, unaStruttura()), "non pronto: mai superato")
        r.completa(bozzaValida, PARTE, unaStruttura(1 to 1, 2 to 2, 3 to 1)).atteso()

        assertEquals("parte-1=1:1,2:2,3:1", r.struttura)
        assertEquals(PARTE, r.parte)
        assertFalse(r.superato(PARTE, unaStruttura(3 to 1, 1 to 1, 2 to 2)), "stessa assegnazione")
        assertTrue(r.superato(PARTE, unaStruttura(1 to 1, 2 to 1, 3 to 1)), "segmento 2 passa da V2 a V1")
        assertFalse(r.superato(PARTE, unaStruttura(1 to 1, 2 to 2, 3 to 1)), "segmento 2 torna a V2")
    }

    @Test
    fun `INV-S7 un Riassunto fallito non e mai superato`() {
        val r = unRiassuntoInCorso().also { it.fallisci(MotivoFallimento.ERRORE_MODELLO).atteso() }

        assertFalse(r.superato(PARTE, StrutturaTrascritto.di(listOf(SegmentoId(9) to VoceId(9)))))
        assertNull(r.struttura)
    }

    @Test
    fun `A26 gli accessor delle liste restituiscono una copia, non la collezione interna`() {
        val decisioni = mutableListOf(Decisione(testo("tiene"), setOf(SegmentoId(1))))
        val contenuto = EsitoVerifica(null, decisioni, emptyList(), emptyList(), emptyList(), omessi = 0)
        val r = Riassunto(
            RiassuntoId("id-1"), IncontroId("id-2"), null,
            LunghezzaMassimaParole.di(LunghezzaMassimaParole.PREDEFINITA).atteso(), RICHIESTO_ALLE,
            StatoRiassunto.PRONTO, AVVIATO_ALLE, null, contenuto, "1:1",
        )

        val letta = r.decisioni
        decisioni.add(Decisione(testo("aggiunta dopo la lettura"), setOf(SegmentoId(2))))

        assertEquals(1, letta.size, "la lista gia letta non deve vedere una mutazione esterna successiva")
    }
}
