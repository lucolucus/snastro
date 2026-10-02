package snastro.avvio.progetto

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.io.TempDir
import snastro.avvio.costruisciRegistrazioniPresenter
import snastro.kernel.CampioniAudio
import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.progetto.applicazione.comandi.Destinazione
import snastro.sintesi.applicazione.porte.ElementoRisposta
import snastro.sintesi.applicazione.porte.PuntoChiaveRisposta
import snastro.sintesi.applicazione.porte.RispostaModello
import snastro.supporto.test.attendiFinche
import snastro.trascrizione.applicazione.letture.StatoElaborazioneVista
import snastro.trascrizione.applicazione.porte.Diarizzatore
import snastro.trascrizione.applicazione.porte.Turno
import snastro.trascrizione.dominio.NumeroPersone
import snastro.ui.Cambiamento
import snastro.ui.registrazioni.RegistrazioniPresenter
import snastro.ui.registrazioni.RegistrazioniUiStato
import java.nio.file.Path
import java.time.LocalTime
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * `avvio-incontro-parti` on the REAL single composition (production `apriProgetto`, SQLite file, real queue): S2's
 * presenter built by `costruisciRegistrazioniPresenter` drives the I2 import, 'Trascrivi N parti' and the start-time
 * edit through the collaborators `:avvio` wires (ADR 0033 §2, ADR 0039, D-0009).
 */
class IncontroPartiTest {
    @TempDir
    lateinit var radice: Path

    @Test
    fun `AC-I89 import di 2 file come un incontro in 2 parti, Trascrivi 2 parti con 4 persone, coda in ordine`() {
        val diarizzatore = DiarizzatoreRegistrante()
        AmbienteProgetto(radice, diarizzatore = diarizzatore).use { ambiente ->
            val s2 = presenterS2(ambiente)
            attendiDati(s2, "lista iniziale") { true }

            // The dialog ('Un incontro in 2 parti' is its default), then ONE all-or-nothing import.
            sulThreadUi(ambiente) { s2.importa(ambiente.sorgentiDaImportare) }
            sulThreadUi(ambiente) { s2.confermaImporta() }
            attendiDati(s2, "2 Parti importate") { it.righe.size == 2 }

            val incontro = ambiente.collaboratori.incontri().single()
            assertEquals(2, incontro.numParti)
            // Parte order is the Incontro's (D-0013: the file WITH a time first), not the selection order.
            val (parte1, parte2) = incontro.parti.map { it.registrazioneId }
            assertEquals(LocalTime.of(10, 0), incontro.parti.first().oraDiInizio)
            assertEquals("riunione-ore-10", incontro.parti.first().titolo)
            listOf(parte1, parte2).forEach(ambiente::rendiLeggibile)

            sulThreadUi(ambiente) { s2.modificaNumeroPersoneIncontro(incontro.incontroId, "4") }
            sulThreadUi(ambiente) { s2.avviaElaborazioniIncontro(incontro.incontroId) }
            attendiFinche(timeout = 20.seconds, messaggio = "le 2 Parti trascritte") {
                listOf(parte1, parte2).all { ambiente.stato(it) == StatoElaborazioneVista.COMPLETATA }
            }

            assertEquals(listOf(parte1, parte2), ambiente.decodificate, "la coda fa la Parte 1 prima della Parte 2")
            assertEquals(listOf<Int?>(4, 4), diarizzatore.numeriDiPersone, "il Numero di persone vale per ogni Parte")
            val voci1 = vociDi(ambiente, parte1)
            val voci2 = vociDi(ambiente, parte2)
            assertTrue(voci1.max() < voci2.min(), "le Voci della Parte 1 hanno i numeri piu' bassi: $voci1 < $voci2")
            assertEquals(4, ambiente.trascrizione.numeroPersonePrecompilato(incontro.incontroId))
        }
    }

    @Test
    fun `AC-I89 la modifica dell ora di inizio passa per ModificaOraDiInizio e riordina le Parti`() {
        AmbienteProgetto(radice).use { ambiente ->
            val s2 = presenterS2(ambiente)
            attendiDati(s2, "lista iniziale") { true }
            sulThreadUi(ambiente) { s2.importa(ambiente.sorgentiDaImportare) }
            sulThreadUi(ambiente) { s2.confermaImporta() }
            attendiDati(s2, "2 Parti importate") { it.righe.size == 2 }
            val (conOra, senzaOra) = ambiente.collaboratori.incontri().single().parti.map { it.registrazioneId }

            // 09:00 < 10:00 → the Parte that had no time becomes the first one; the seconds of the VO are 0.
            sulThreadUi(ambiente) { s2.modificaOraDiInizio(senzaOra, LocalTime.of(9, 0)) }

            attendiDati(s2, "ora modificata") { d ->
                d.riga(senzaOra).let { it.oraDiInizio != null && !it.operazioneInCorso }
            }
            val dopo = ambiente.collaboratori.incontri().single().parti
            assertEquals(listOf(senzaOra, conOra), dopo.map { it.registrazioneId })
            assertEquals(listOf(1, 2), dopo.map { it.numero })
            assertEquals(LocalTime.of(9, 0), dopo.first().oraDiInizio)

            // `null` clears it: the Parte without time goes last again.
            sulThreadUi(ambiente) { s2.modificaOraDiInizio(senzaOra, null) }
            attendiDati(s2, "ora azzerata") { d ->
                d.riga(senzaOra).let { it.oraDiInizio == null && !it.operazioneInCorso }
            }
            val ordine = ambiente.collaboratori.incontri().single().parti.map { it.registrazioneId }
            assertEquals(listOf(conOra, senzaOra), ordine)
        }
    }

    @Test
    fun `AC-I89 dopo ModificaOraDiInizio il Riassunto gia' pronto e' superato e l ordine delle Parti e' fresco`() {
        AmbienteProgetto(radice).use { ambiente ->
            val s2 = presenterS2(ambiente)
            attendiDati(s2, "lista iniziale") { true }
            sulThreadUi(ambiente) { s2.importa(ambiente.sorgentiDaImportare) }
            sulThreadUi(ambiente) { s2.confermaImporta() }
            attendiDati(s2, "2 Parti importate") { it.righe.size == 2 }
            val incontro = ambiente.collaboratori.incontri().single()
            val parti = incontro.parti.map { it.registrazioneId }
            parti.forEach(ambiente::rendiLeggibile)
            parti.forEach(ambiente::avviaElaborazione)
            attendiFinche(timeout = 20.seconds, messaggio = "le 2 Parti trascritte") {
                parti.all { ambiente.stato(it) == StatoElaborazioneVista.COMPLETATA }
            }
            ambiente.modello.risposta = Esito.Ok(RISPOSTA_SU_DUE_PARTI)
            ambiente.riassumi(parti.first())
            attendiFinche(timeout = 20.seconds, messaggio = "Riassunto pronto") {
                ambiente.sintesi.vista(parti.first())?.mostrato?.let { !it.superato } == true
            }

            val cambiamenti = ambiente.raccogliCambiamenti()
            sulThreadUi(ambiente) { s2.modificaOraDiInizio(parti.last(), LocalTime.of(9, 0)) }

            // After commit, every Parte's view (the S3 open on the OTHER Parte too) is told to reload its order.
            attendiFinche(messaggio = "un Cambiamento per ogni Parte") {
                parti.all { Cambiamento(it) in cambiamenti }
            }
            attendiFinche(messaggio = "ordine fresco") {
                ambiente.collaboratori.incontri().single().parti.first().registrazioneId == parti.last()
            }
            assertTrue(checkNotNull(ambiente.sintesi.vista(parti.first())?.mostrato).superato, "riordinata: superato")
        }
    }

    // The 'last value used' half of the AC is asserted after a real 'Trascrivi 2 parti' in this class's first test.
    @Test
    fun `AC-I89 senza prefill il campo Numero di persone di un Incontro mai trascritto e' vuoto`() {
        AmbienteProgetto(radice).use { ambiente ->
            ambiente.importaIn(Destinazione.NuovoIncontro)
            val incontro = ambiente.collaboratori.incontri().single().incontroId
            assertNull(ambiente.trascrizione.numeroPersonePrecompilato(incontro))
        }
    }

    private fun presenterS2(ambiente: AmbienteProgetto): RegistrazioniPresenter =
        costruisciRegistrazioniPresenter(ambiente.grafo(), ambiente.collaboratori) {}

    private fun sulThreadUi(ambiente: AmbienteProgetto, azione: () -> Unit) =
        runBlocking(ambiente.dispatcherUi) { azione() }

    private fun RegistrazioniUiStato.Dati.riga(id: RegistrazioneId) = righe.first { it.registrazioneId == id }

    private fun attendiDati(
        s2: RegistrazioniPresenter,
        messaggio: String,
        condizione: (RegistrazioniUiStato.Dati) -> Boolean,
    ) =
        attendiFinche(timeout = 10.seconds, messaggio = messaggio) {
            (s2.stato.value as? RegistrazioniUiStato.Dati)?.let(condizione) == true
        }

    private fun vociDi(ambiente: AmbienteProgetto, parte: RegistrazioneId): List<Int> =
        ambiente.porte.trascritti.trascritto(parte)!!.segmenti.map { it.voceId.numero }.distinct()

    /** The default [DUE_VOCI] script that also records the Numero di persone each run was given. */
    private class DiarizzatoreRegistrante : Diarizzatore {
        val numeriDiPersone: MutableList<Int?> = CopyOnWriteArrayList()

        override fun diarizza(c: CampioniAudio, numeroPersone: NumeroPersone?): List<Turno> {
            numeriDiPersone += numeroPersone?.valore
            return AmbienteProgetto.DUE_VOCI
        }
    }

    private companion object {
        /** Cites Segmento labels 1 (Parte 1) and 3 (Parte 2) of the concatenated input of a 2 x 2 Segmenti Incontro. */
        val RISPOSTA_SU_DUE_PARTI = RispostaModello(
            sommario = "{V1} e {V3} fanno il punto.",
            decisioni = listOf(ElementoRisposta("Si parte dal primo punto.", listOf(1))),
            questioniAperte = listOf(ElementoRisposta("Quando rivedersi.", listOf(3))),
            azioni = emptyList(),
            puntiChiave = listOf(PuntoChiaveRisposta("Il ritmo.", listOf(1, 3), parlante = 1)),
        )
    }
}
