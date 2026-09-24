package snastro.avvio.r1

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.jupiter.api.io.TempDir
import snastro.avvio.GrafoR0
import snastro.avvio.orologioApp
import snastro.kernel.CampioniAudio
import snastro.kernel.ElaborazioneId
import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceId
import snastro.kernel.atteso
import snastro.persistenza.apriDatabaseProgetto
import snastro.progetto.applicazione.letture.ElencoProgetti
import snastro.progetto.applicazione.porte.RegistroProgettiFinta
import snastro.trascrizione.adattatori.persistenza.ElaborazioneRepositorySql
import snastro.trascrizione.applicazione.comandi.AvviaElaborazione
import snastro.trascrizione.applicazione.comandi.UnisciVoci
import snastro.trascrizione.applicazione.letture.StatoElaborazioneVista
import snastro.trascrizione.applicazione.porte.Diarizzatore
import snastro.trascrizione.applicazione.porte.DiarizzatoreFinta
import snastro.trascrizione.applicazione.porte.FaseElaborazione
import snastro.trascrizione.applicazione.porte.Turno
import snastro.trascrizione.dominio.NumeroPersone
import snastro.trascrizione.dominio.StatoElaborazione
import snastro.trascrizione.dominio.unaElaborazione
import snastro.ui.ApriEsternoFinta
import snastro.ui.modelli.ServizioModelliFinta
import snastro.ui.modelli.StatoModelli
import snastro.ui.registrazione.RegistrazioneUiStato
import snastro.ui.registrazioni.RegistrazioniUiStato
import snastro.ui.registrazioni.StatoElaborazioneRiga
import snastro.ui.testi.etichetta
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * End-to-end ACs of the R1 composition, on [AmbienteR1] (real SessioneProgettoImpl + EstensioneR1 over
 * a real project folder; only FFmpeg and the ML models are Finte).
 */
class ComposizioneR1Test {
    @TempDir
    lateinit var radice: Path

    private val dueVoci = DiarizzatoreFinta(
        turni = listOf(
            Turno(IntervalloMs(0, 1_000), voceIndice = 0),
            Turno(IntervalloMs(2_000, 3_000), voceIndice = 1),
        ),
    )

    @Test
    fun `AC-371 dopo un import nessuna Elaborazione esiste, la vista da NON_AVVIATA e S2 mostra Trascrivi`() {
        val ambiente = AmbienteR1(radice)
        val id = ambiente.importa()
        val presenter = costruisciRegistrazioniPresenterR1(grafoR0Di(ambiente), ambiente.collaboratori, ambiente.r1) {}

        assertEquals(StatoElaborazioneVista.NON_AVVIATA, ambiente.r1.statiElaborazione(listOf(id)).single().stato)
        attendiFinche(messaggio = "riga NON_AVVIATA in S2") {
            rigaDi(presenter.stato.value, id) == StatoElaborazioneRiga.NonAvviata
        }
        Thread.sleep(ATTESA_NESSUN_AVVIO_MS) // la coda gira ogni secondo: nulla deve comparire nel frattempo
        assertEquals(StatoElaborazioneVista.NON_AVVIATA, ambiente.r1.statiElaborazione(listOf(id)).single().stato)
        ambiente.close()

        val db = apriDatabaseProgetto(Path.of(ambiente.progetto.percorso).toFile())
        val righe = ElaborazioneRepositorySql(db.database).diRegistrazione(id)
        db.chiudi()
        assertTrue(righe.isEmpty(), "l'import non deve creare alcuna Elaborazione (ADR 0014): $righe")
    }

    @Test
    fun `AC-353 AC-354 una fase della pipeline e visibile in S2 senza polling, poi la riga e completata`() {
        val barriera = CountDownLatch(1)
        val ambiente = AmbienteR1(radice, DiarizzatoreConBarriera(barriera, DiarizzatoreFinta()))
        ambiente.use {
            val id = it.importa()
            val presenter = costruisciRegistrazioniPresenterR1(grafoR0Di(it), it.collaboratori, it.r1) {}

            it.r1.avviaElaborazione(AvviaElaborazione(id)).atteso() // 'Trascrivi'

            attendiFinche(messaggio = "fase di diarizzazione in S2") {
                val riga = rigaDi(presenter.stato.value, id)
                riga is StatoElaborazioneRiga.InCorso && riga.faseEtichetta == etichetta(FaseElaborazione.DIARIZZAZIONE)
            }
            assertEquals(FaseElaborazione.DIARIZZAZIONE, it.r1.statiElaborazione(listOf(id)).single().fase)

            barriera.countDown()
            attendiFinche(messaggio = "riga completata in S2") {
                rigaDi(presenter.stato.value, id) == StatoElaborazioneRiga.Completata
            }
        }
    }

    @Test
    fun `AC-369 Trascrivi con Numero di persone lo passa al Diarizzatore della pipeline`() {
        val diarizzatore = DiarizzatoreFinta()
        AmbienteR1(radice, diarizzatore).use {
            val id = it.importa()

            it.r1.avviaElaborazione(AvviaElaborazione(id, numeroPersone = 3)).atteso()

            attendiFinche { it.r1.statiElaborazione(listOf(id)).single().stato == StatoElaborazioneVista.COMPLETATA }
            assertEquals(listOf(NumeroPersone.di(3).atteso()), diarizzatore.numeroPersoneRicevuti)
        }
    }

    @Test
    fun `AC-356 senza Parlanti il Documento rende ogni Voce come Voce n e una Revisione committata lo rigenera`() {
        AmbienteR1(radice, dueVoci).use {
            val id = it.importa()
            it.r1.avviaElaborazione(AvviaElaborazione(id)).atteso()
            attendiFinche(messaggio = "Documento con due Voci") { documento(it)?.contains("**Voce 2**") == true }
            assertTrue(documento(it).orEmpty().contains("**Voce 1**"))

            it.r1.revisione.unisciVoci.esegui(UnisciVoci(id, sopravvive = VoceId(1), rimossa = VoceId(2))).atteso()

            attendiFinche(messaggio = "Documento rigenerato dopo VociUnite") {
                documento(it)?.contains("**Voce 2**") == false
            }
            assertEquals(2, Regex("""\*\*Voce 1\*\*""").findAll(documento(it).orEmpty()).count())
        }
    }

    @Test
    fun `AC-351 S3 in sola lettura mostra il Trascritto completato con etichette Voce n e il percorso del Documento`() {
        AmbienteR1(radice, dueVoci).use {
            val id = it.importa()
            it.r1.avviaElaborazione(AvviaElaborazione(id)).atteso()
            attendiFinche { it.r1.percorsoDocumento(id) != null }
            val grafo = GrafoR1(grafoR0Di(it), ServizioModelliFinta(StatoModelli.Pronti), ApriEsternoFinta())
            val scopeS3 = CoroutineScope(SupervisorJob() + it.dispatcherUi)

            val presenter = costruisciRegistrazionePresenterR1(grafo, it.collaboratori, it.r1, id, scopeS3)

            attendiFinche(messaggio = "S3 caricato") { presenter.stato.value is RegistrazioneUiStato.Dati }
            val dati = presenter.stato.value as RegistrazioneUiStato.Dati
            assertEquals(listOf("Voce 1", "Voce 2"), dati.segmenti.map { s -> s.etichettaVoce })
            assertNotNull(dati.documentoPercorso)
            scopeS3.cancel()
        }
    }

    @Test
    fun `carry-over 3 il recupero gira prima della coda, un in_corso lasciato da un crash diventa fallita`() {
        val ambiente = AmbienteR1(radice)
        val id = ambiente.importa()
        val percorso = ambiente.progetto.percorso
        ambiente.close()
        val db = apriDatabaseProgetto(Path.of(percorso).toFile())
        ElaborazioneRepositorySql(db.database)
            .salva(unaElaborazione(StatoElaborazione.IN_CORSO, id = ElaborazioneId("e-crash"), registrazioneId = id))
            .atteso()
        db.chiudi()

        val riaperto = AmbienteR1(radice.resolve("bis").also(Files::createDirectories))
        riaperto.use {
            it.sessione.chiudi()
            it.sessione.apri(percorso).atteso()
            attendiFinche(messaggio = "recupero dell'in_corso") {
                it.r1.statiElaborazione(listOf(id)).single().stato == StatoElaborazioneVista.FALLITA
            }
            assertEquals("interrotta", it.r1.statiElaborazione(listOf(id)).single().motivoFallimento)
        }
    }

    @Test
    fun `carry-over 1 chiudi con la pipeline in corso ferma la coda prima di chiudere il database`() {
        val barriera = CountDownLatch(1) // mai rilasciata: la pipeline resta bloccata finche' chiudi la interrompe
        val ambiente = AmbienteR1(radice, DiarizzatoreConBarriera(barriera, DiarizzatoreFinta()))
        val id = ambiente.importa()
        val r1 = ambiente.r1
        r1.avviaElaborazione(AvviaElaborazione(id)).atteso()
        attendiFinche { r1.statiElaborazione(listOf(id)).single().fase == FaseElaborazione.DIARIZZAZIONE }

        ambiente.close()

        assertTrue(r1.coda.lavoro.isCompleted, "la coda deve essere ferma quando chiudi ritorna")
        assertNull(ambiente.sessione.corrente.value)
    }

    private fun rigaDi(stato: RegistrazioniUiStato, id: RegistrazioneId): StatoElaborazioneRiga? =
        (stato as? RegistrazioniUiStato.Dati)?.righe?.find { it.registrazioneId == id }?.elaborazione

    private fun documento(ambiente: AmbienteR1): String? =
        ambiente.cartellaDocumenti().takeIf(Files::isDirectory)
            ?.listDirectoryEntries("*.md")?.singleOrNull()?.readText()

    private fun grafoR0Di(ambiente: AmbienteR1) = GrafoR0(
        scope = ambiente.scope,
        io = ambiente.dispatcherUi,
        clock = orologioApp(),
        sessione = ambiente.sessione,
        elencoProgetti = ElencoProgetti(RegistroProgettiFinta()),
        cartellaProgettiPredefinita = radice.toString(),
    )

    /** A Diarizzatore that holds the pipeline in the DIARIZZAZIONE phase until [barriera] opens (interruptible). */
    private class DiarizzatoreConBarriera(
        private val barriera: CountDownLatch,
        private val delegato: Diarizzatore,
    ) : Diarizzatore {
        override fun diarizza(c: CampioniAudio, numeroPersone: NumeroPersone?): List<Turno> {
            barriera.await()
            return delegato.diarizza(c, numeroPersone)
        }
    }

    private companion object {
        const val ATTESA_NESSUN_AVVIO_MS = 1_500L
    }
}
