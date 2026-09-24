package snastro.avvio.r2

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.io.TempDir
import snastro.avvio.r1.attendiFinche
import snastro.kernel.AbbonatoSincrono
import snastro.kernel.CampioniAudio
import snastro.kernel.Esito
import snastro.kernel.EventoPubblicato
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.parlanti.adattatori.persistenza.AttribuzioneRepositorySql
import snastro.parlanti.adattatori.persistenza.ParlanteRepositorySql
import snastro.parlanti.applicazione.eventi.ImpronteRiallineate
import snastro.parlanti.applicazione.porte.DecodificatoreAudio
import snastro.parlanti.applicazione.porte.EstrattoreImpronta
import snastro.parlanti.dominio.ErroreParlanti
import snastro.parlanti.dominio.Impronta
import snastro.trascrizione.applicazione.comandi.AvviaElaborazione
import snastro.trascrizione.applicazione.comandi.ConfermaSegmento
import snastro.trascrizione.applicazione.comandi.RiassegnaSegmento
import snastro.trascrizione.applicazione.eventi.SegmentoRiassegnato
import snastro.trascrizione.applicazione.letture.SegmentoTrascrittoView
import snastro.trascrizione.applicazione.porte.Diarizzatore
import snastro.trascrizione.applicazione.porte.Turno
import snastro.trascrizione.dominio.NumeroPersone
import snastro.ui.Cambiamento
import snastro.ui.registrazione.ComandoVoce
import snastro.ui.registrazione.ErroreSomiglianzaUi
import snastro.ui.registrazione.GruppoSpostamenti
import snastro.ui.registrazione.ObiettivoNome
import snastro.ui.registrazione.PassiNominaFrase
import snastro.ui.registrazione.StatoSomiglianza
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val ANNA = floatArrayOf(1f, 0f, 0f)
private val MARCO = floatArrayOf(0f, 1f, 0f)
private val LUCA = floatArrayOf(0f, 0f, 1f)
private val AMBIGUA = floatArrayOf(1f, 1f, 0f)

/** One 2 s Turno per row: (voceIndice, the voice it "sounds like"). Consecutive rows alternate voices. */
private val COPIONE: List<Pair<Int, FloatArray>> = listOf(
    0 to ANNA, // Segmento 1 → Voce 1 (Anna's confirmed sentence)
    1 to MARCO, // 2 → Voce 2 (Marco's confirmed sentence)
    2 to ANNA, // 3 → Voce 3
    3 to LUCA, // 4 → Voce 4 (Luca, "intera Voce")
    2 to ANNA, // 5 → Voce 3
    3 to LUCA, // 6 → Voce 4
    2 to MARCO, // 7 → Voce 3
    3 to ANNA, // 8 → Voce 4 (Anna-like inside Luca's Voce)
    2 to AMBIGUA, // 9 → Voce 3 (ambiguous: stays, incerta)
)
private const val DURATA_TURNO_MS = 2_000L

private fun intervallo(i: Int) = IntervalloMs(i * DURATA_TURNO_MS, (i + 1) * DURATA_TURNO_MS)

/** The diarizer of [COPIONE] (ignores the audio and the stated count). */
private class DiarizzatoreCopione : Diarizzatore {
    override fun diarizza(c: CampioniAudio, numeroPersone: NumeroPersone?): List<Turno> =
        COPIONE.mapIndexed { i, (voce, _) -> Turno(intervallo(i), voce) }
}

/** A decoder that encodes the first interval's start as the only sample: the extractor reads it back. */
private class DecodificatoreTabella : DecodificatoreAudio {
    val chiamate = AtomicInteger()

    override fun campioni(id: RegistrazioneId, intervalli: List<IntervalloMs>): CampioniAudio {
        chiamate.incrementAndGet()
        return CampioniAudio(floatArrayOf(intervalli.first().inizioMs.toFloat()))
    }
}

/** "The extractor vector per interval from a table" (AC-538): the voice of the Turno the audio starts in. */
private class EstrattoreTabella : EstrattoreImpronta {
    override val modello: String = "tabella"
    val chiamate = AtomicInteger()

    override fun estrai(c: CampioniAudio): Impronta {
        chiamate.incrementAndGet()
        val turno = (c.campioni.first().toLong() / DURATA_TURNO_MS).toInt()
        return Impronta(COPIONE.getOrNull(turno)?.second ?: floatArrayOf(1f, 1f, 1f))
    }
}

/**
 * ADR 0019 §4-§6 + Amendment (b) end-to-end on the REAL R2 composition ([AmbienteR2]: SQLite project
 * folder, real queue/pipeline, real Parlanti and Trascrizione services and subscribers, the real
 * `PianoRiassegnazioneQuery` and cosine classifier) with table-driven fake ML: AC-537..AC-540, AC-549, the
 * fallback references, SegmentoConfermato → Cambiamento (AC-541).
 *
 * AC-538's setup ([prepara]): Voce 1 → Anna with one confirmed 2 s Segmento; Voce 2 → Marco with one
 * confirmed Segmento (both named through "Dai un nome a questa frase", case (b)); Voce 3 unattributed with 4
 * Segmenti (2 Anna-like, 1 Marco-like, 1 ambiguous); Voce 4 → Luca with NO confirmed Segmento and 3 Segmenti
 * (2 Luca-like, 1 Anna-like: "intera Voce").
 */
class SomiglianzaR2Test {
    @TempDir
    lateinit var radice: Path

    private val decodificatore = DecodificatoreTabella()
    private val estrattore = EstrattoreTabella()

    private fun ambiente() = AmbienteR2(
        radice,
        DiarizzatoreCopione(),
        estrattore,
        decodificatoreParlanti = decodificatore,
        durataMs = COPIONE.size * DURATA_TURNO_MS,
    )

    private fun frase(a: AmbienteR2, id: RegistrazioneId, n: Int, passi: PassiNominaFrase): Esito<Unit>? =
        runBlocking { a.r2.comandi.nominaFrase(id, SegmentoId(n), passi) }

    private fun segmenti(a: AmbienteR2, id: RegistrazioneId): List<SegmentoTrascrittoView> =
        checkNotNull(a.r2.r1.trascritto(id)).segmenti

    private fun voceDi(a: AmbienteR2, id: RegistrazioneId, n: Int): VoceId =
        segmenti(a, id).single { it.segmentoId == SegmentoId(n) }.voceId

    private fun prepara(a: AmbienteR2): RegistrazioneId {
        val id = a.importa()
        a.trascrivi(id)
        assertEquals(COPIONE.size, segmenti(a, id).size, "un Segmento per Turno")
        assertEquals((1..4).map(::VoceId), checkNotNull(a.r2.r1.trascritto(id)).voci.map { it.voceId })
        val anna = ObiettivoNome.Nuovo("Anna", ricorrente = true)
        assertEquals(Esito.Ok(Unit), frase(a, id, 1, PassiNominaFrase.AttribuisciVoce(VoceId(1), anna)))
        val marco = ObiettivoNome.Nuovo("Marco", ricorrente = true)
        assertEquals(Esito.Ok(Unit), frase(a, id, 2, PassiNominaFrase.AttribuisciVoce(VoceId(2), marco)))
        assertEquals(Esito.Ok(Unit), runBlocking { a.r2.comandi.esegui(ComandoVoce.Nuovo(voce(id, 4), "Luca")) })
        assertEquals(listOf(1, 2), segmenti(a, id).filter { it.confermato }.map { it.segmentoId.numero })
        return id
    }

    private fun calcola(a: AmbienteR2, id: RegistrazioneId): StatoSomiglianza {
        a.r2.somiglianza.calcola(id)
        attendiFinche(messaggio = "fine del confronto") {
            val s = a.r2.somiglianza.stato.value[id]
            s != null && s !is StatoSomiglianza.InCorso
        }
        return checkNotNull(a.r2.somiglianza.stato.value[id])
    }

    private fun applica(a: AmbienteR2, id: RegistrazioneId): StatoSomiglianza {
        a.r2.somiglianza.applica(id)
        attendiFinche(messaggio = "fine dell'applicazione") {
            val s = a.r2.somiglianza.stato.value[id]
            s is StatoSomiglianza.Esito || s is StatoSomiglianza.Errore
        }
        return checkNotNull(a.r2.somiglianza.stato.value[id])
    }

    /** Segmenti, attribuzioni and print rows of [id] — what "nothing written" compares. */
    private fun righe(a: AmbienteR2, id: RegistrazioneId): Triple<Any, Any, Any> = Triple(
        segmenti(a, id),
        AttribuzioneRepositorySql(a.contesto.database).diRegistrazione(id).map { it.voceRef to it.parlanteId },
        ParlanteRepositorySql(a.contesto.database).impronteDiRegistrazione(id).size,
    )

    private fun documento(a: AmbienteR2, id: RegistrazioneId): String? =
        a.r2.r1.percorsoDocumento(id)?.let { p -> Path.of(p).readText() }

    @Test
    fun `AC-538 anteprima senza scritture, Applica in una transazione, confermate intatte, Documento rigenerato`() {
        ambiente().use { a ->
            val id = prepara(a)
            val riallineate = CopyOnWriteArrayList<EventoPubblicato>()
            a.contesto.dispatcher.registraDopoCommit { e -> if (e is ImpronteRiallineate) riallineate += e }
            val prima = righe(a, id)

            val anteprima = calcola(a, id)
            val attesi = listOf(
                GruppoSpostamenti(VoceId(3), VoceId(1), 2),
                GruppoSpostamenti(VoceId(4), VoceId(1), 1),
                GruppoSpostamenti(VoceId(3), VoceId(2), 1),
            )
            assertEquals(StatoSomiglianza.Anteprima(attesi, 1), anteprima)
            assertEquals(prima, righe(a, id), "l'anteprima non scrive nulla")

            // AC-549: nothing decoded nor extracted between Applica and the batch transaction.
            val prima549 = estrattore.chiamate.get() to decodificatore.chiamate.get()
            val sonda = SondaBatch(a)
            riallineate.clear()

            assertEquals(StatoSomiglianza.Esito(4, 1), applica(a, id))
            assertEquals(prima549, sonda.chiamateAllInizio)
            assertEquals(listOf(3, 5, 7, 8), sonda.eventi.map { it.segmentoId.numero }, "ONE batch, in plan order")

            assertEquals(listOf(1, 3, 5, 8).map { VoceId(1) }, listOf(1, 3, 5, 8).map { voceDi(a, id, it) })
            assertEquals(listOf(VoceId(2), VoceId(2)), listOf(2, 7).map { voceDi(a, id, it) })
            assertEquals(VoceId(3), voceDi(a, id, 9), "l'incerta resta dov'era")
            assertEquals(listOf(VoceId(4), VoceId(4)), listOf(4, 6).map { voceDi(a, id, it) })
            assertEquals(listOf(1, 2), segmenti(a, id).filter { it.confermato }.map { it.segmentoId.numero })
            val attribuzioni = AttribuzioneRepositorySql(a.contesto.database).diRegistrazione(id)
            assertEquals(listOf(1, 2, 4), attribuzioni.map { it.voceRef.voceId.numero }.sorted(), "Luca tiene Voce 4")

            attendiFinche(messaggio = "Documento rigenerato") {
                documento(a, id)?.let { d -> d.split("**Anna**").size - 1 == 4 && "**Voce 3**" in d } == true
            }
            attendiFinche(messaggio = "RiallineaImpronte dopo il commit") { riallineate.isNotEmpty() }
            Thread.sleep(ATTESA_COALESCENZA_MS)
            assertEquals(1, riallineate.size, "un solo riallineamento per il batch")
            confermaLucaERicalcola(a, id)
        }
    }

    /** AC-538 end: confirm one Luca Segmento → every reference is "frasi confermate": N = 0, 'Chiudi' sends nothing. */
    private fun confermaLucaERicalcola(a: AmbienteR2, id: RegistrazioneId) {
        a.r2.confermaSegmento(ConfermaSegmento(id, SegmentoId(4), confermato = true)).atteso()
        a.r2.somiglianza.annulla(id)
        val seconda = calcola(a, id)
        assertEquals(StatoSomiglianza.Anteprima(emptyList(), 1), seconda)
        val dopo = righe(a, id)
        a.r2.somiglianza.applica(id)
        a.r2.somiglianza.annulla(id) // 'Chiudi'
        assertNull(a.r2.somiglianza.stato.value[id])
        assertEquals(dopo, righe(a, id))
    }

    @Test
    fun `AC-539 piano scaduto - TrascrittoCambiato, nulla scritto, piano scartato, nuovo calcolo dal nuovo stato`() {
        ambiente().use { a ->
            val id = prepara(a)
            assertTrue(calcola(a, id) is StatoSomiglianza.Anteprima)
            a.r2.r1.revisione.riassegnaSegmento.esegui(RiassegnaSegmento(id, SegmentoId(3), VoceId(2))).atteso()
            val prima = righe(a, id)

            assertEquals(StatoSomiglianza.Errore(ErroreSomiglianzaUi.TrascrittoCambiato), applica(a, id))
            assertEquals(prima, righe(a, id))
            a.r2.somiglianza.applica(id)
            val cambiato = StatoSomiglianza.Errore(ErroreSomiglianzaUi.TrascrittoCambiato)
            assertEquals(cambiato, a.r2.somiglianza.stato.value[id])

            // 'Ricalcola': Segmento 3 is now CONFIRMED on Voce 2 (a manual move confirms) → it no longer moves.
            val nuova = calcola(a, id)
            val gruppi = (nuova as StatoSomiglianza.Anteprima).gruppi
            assertEquals(
                listOf(GruppoSpostamenti(VoceId(3), VoceId(1), 1), GruppoSpostamenti(VoceId(4), VoceId(1), 1)),
                gruppi.filter { it.a == VoceId(1) },
            )
        }
    }

    @Test
    fun `AC-537 AC-549 Annulla sull anteprima non scrive nulla e Ritrascrivi accodato la scarta`() {
        ambiente().use { a ->
            val id = prepara(a)
            val prima = righe(a, id)
            assertTrue(calcola(a, id) is StatoSomiglianza.Anteprima)
            a.r2.somiglianza.annulla(id)
            a.r2.somiglianza.applica(id)
            Thread.sleep(ATTESA_COALESCENZA_MS)
            assertNull(a.r2.somiglianza.stato.value[id])
            assertEquals(prima, righe(a, id))

            assertTrue(calcola(a, id) is StatoSomiglianza.Anteprima)
            a.r2.avviaElaborazione(AvviaElaborazione(id)).atteso() // 'Ritrascrivi' queued
            assertNull(a.r2.somiglianza.stato.value[id])
        }
    }

    @Test
    fun `fallback - senza frasi confermate si usa tutta la Voce, con meno di 2 riferimenti nessuna estrazione`() {
        ambiente().use { a ->
            val id = a.importa()
            a.trascrivi(id)
            assertEquals(Esito.Ok(Unit), runBlocking { a.r2.comandi.esegui(ComandoVoce.Nuovo(voce(id, 4), "Luca")) })
            val estrazioni = estrattore.chiamate.get()
            val decodifiche = decodificatore.chiamate.get()
            assertEquals(StatoSomiglianza.Errore(ErroreSomiglianzaUi.RiferimentiInsufficienti), calcola(a, id))
            assertEquals(estrazioni, estrattore.chiamate.get())
            assertEquals(decodifiche, decodificatore.chiamate.get())

            // Anna named on Voce 1 WITHOUT confirming anything: both persons are "intera Voce" references.
            assertEquals(Esito.Ok(Unit), runBlocking { a.r2.comandi.esegui(ComandoVoce.Nuovo(voce(id, 1), "Anna")) })
            assertTrue(segmenti(a, id).none { it.confermato })
            a.r2.somiglianza.annulla(id)
            val anteprima = calcola(a, id) as StatoSomiglianza.Anteprima
            assertEquals(
                // Anna's centroid is her whole Voce: the ambiguous Segmento 9 is now closer to her than to Luca's.
                listOf(GruppoSpostamenti(VoceId(3), VoceId(1), 3), GruppoSpostamenti(VoceId(4), VoceId(1), 1)),
                anteprima.gruppi.filter { it.a == VoceId(1) },
            )
            assertFalse(anteprima.gruppi.any { it.da == VoceId(1) }, "Anna's only Segmento is her last: never moved")
        }
    }

    @Test
    fun `AC-540 caso d - nuova Voce confermata attribuita a Dario, poi NomeGiaInUso la lascia senza nome`() {
        ambiente().use { a ->
            val id = a.importa()
            a.trascrivi(id)
            val dario = ObiettivoNome.Nuovo("Dario", ricorrente = true)
            // Segmento 4 of Voce 4 (3 Segmenti, unattributed).
            assertEquals(Esito.Ok(Unit), frase(a, id, 4, PassiNominaFrase.NuovaVoce(dario)))
            val nuova = voceDi(a, id, 4)
            assertEquals(VoceId(5), nuova)
            assertTrue(segmenti(a, id).single { it.segmentoId == SegmentoId(4) }.confermato)
            val attribuzione = AttribuzioneRepositorySql(a.contesto.database).diRegistrazione(id).single()
            assertEquals(nuova, attribuzione.voceRef.voceId)
            val dariano = a.r2.letture.parlantiDelProgetto().single { it.nome == "Dario" }
            assertEquals(attribuzione.parlanteId, dariano.parlanteId)
            assertEquals(1, ParlanteRepositorySql(a.contesto.database).impronteDiRegistrazione(id).size)
            attendiFinche(messaggio = "Documento con Dario") { documento(a, id)?.contains("**Dario**") == true }

            val prima = AttribuzioneRepositorySql(a.contesto.database).diRegistrazione(id).size
            val parlanti = a.r2.letture.parlantiDelProgetto().size
            val esito = frase(a, id, 6, PassiNominaFrase.NuovaVoce(dario))
            checkNotNull(esito).erroreAtteso<ErroreParlanti.NomeGiaInUso>()
            assertEquals(VoceId(6), voceDi(a, id, 6), "la nuova Voce esiste, senza nome")
            assertTrue(segmenti(a, id).single { it.segmentoId == SegmentoId(6) }.confermato)
            assertEquals(prima, AttribuzioneRepositorySql(a.contesto.database).diRegistrazione(id).size)
            assertEquals(parlanti, a.r2.letture.parlantiDelProgetto().size)
        }
    }

    @Test
    fun `AC-541 SegmentoConfermato produce un Cambiamento della sua Registrazione dopo il commit`() {
        ambiente().use { a ->
            val id = a.importa()
            a.trascrivi(id)
            val cambiamenti = CopyOnWriteArrayList<Cambiamento>()
            a.scope.launch { a.collaboratori.aggiornamentiVista.cambiamenti.collect(cambiamenti::add) }
            Thread.sleep(ATTESA_COALESCENZA_MS)
            cambiamenti.clear()
            a.r2.confermaSegmento(ConfermaSegmento(id, SegmentoId(1), confermato = true)).atteso()
            attendiFinche(messaggio = "Cambiamento") { Cambiamento(id) in cambiamenti }
        }
    }

    /**
     * A synchronous subscriber registered after the composition's: inside the batch transaction it records the
     * [SegmentoRiassegnato] events and, at the first one, the extractor/decoder call counts.
     */
    private inner class SondaBatch(a: AmbienteR2) {
        val eventi = CopyOnWriteArrayList<SegmentoRiassegnato>()

        @Volatile var chiamateAllInizio: Pair<Int, Int>? = null

        init {
            a.contesto.dispatcher.registraSincrono(
                AbbonatoSincrono { e: EventoPubblicato ->
                    if (e is SegmentoRiassegnato) {
                        if (eventi.isEmpty()) {
                            chiamateAllInizio = estrattore.chiamate.get() to decodificatore.chiamate.get()
                        }
                        eventi += e
                    }
                    Esito.Ok(Unit)
                },
            )
        }
    }

    private companion object {
        const val ATTESA_COALESCENZA_MS = 300L
    }
}
