package snastro.avvio.progetto

import org.junit.jupiter.api.io.TempDir
import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceId
import snastro.kernel.atteso
import snastro.persistenza.scriviDatabaseV7
import snastro.progetto.applicazione.comandi.EliminaRegistrazione
import snastro.supporto.test.attendiFinche
import snastro.trascrizione.applicazione.comandi.UnisciVoci
import snastro.trascrizione.applicazione.letture.StatoElaborazioneVista
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * AC-I12 (incontro-chiavi, ADR 0033 §4.1/§6) on a project written by the PREVIOUS release (schema 7, no Revisione
 * step, ids of the Incontro = ids of the Registrazione after `7.sqm`), opened through the real `apri`: the migrated
 * `pronto` Riassunto is not `superato`, the Nomi resolve, `UnisciVoci` on an attributed Voce, the queue position and
 * `Elimina` all work on the migrated rows.
 */
class ChiaviIncontroMigrazioneTest {
    @TempDir
    lateinit var radice: Path

    private val reg1 = RegistrazioneId("reg-1")
    private val reg2 = RegistrazioneId("reg-2")

    @Test
    fun `AC-I12 su un progetto v7 migrato pronto non superato, nomi, UnisciVoci, coda ed Elimina funzionano`() {
        AmbienteProgetto(radice).use {
            it.sessione.chiudi()
            val percorso = Path.of(it.progetto.percorso)
            scriviDatabaseV7(percorso.toFile(), RIGHE_V7)
            it.rendiLeggibile(reg1)
            it.rendiLeggibile(reg2)
            it.apri(percorso.absolutePathString())

            // The migrated Incontro of a Registrazione is keyed by the Registrazione's own id (a migration fact).
            val incontro = it.incontroDi(reg1)
            assertEquals(IncontroId("reg-1"), incontro)
            // pronto, NOT superato: the key recomputed from the migrated Trascritto equals the stored one.
            val mostrato = assertNotNull(it.sintesi.vista(reg1)?.mostrato)
            assertEquals(false, mostrato.superato)
            // The Nomi resolve through the migrated Attribuzione.
            assertEquals("Anna", it.parlanti.letture.identificazione(reg1).single { v -> v.voceId.numero == 1 }.nome)

            // UnisciVoci on an attributed Voce (Voce 1 = Anna survives, Voce 2 merged into it).
            it.trascrizione.revisione.unisciVoci.esegui(UnisciVoci(reg1, VoceId(1), VoceId(2))).atteso()
            assertEquals(
                setOf(VoceId(1)),
                it.porte.trascritti.trascritto(reg1)!!.segmenti.map { s -> s.voceId }.toSet(),
            )
            assertEquals(listOf(VoceId(1)), it.porte.attribuzioni.diIncontro(incontro).map { a -> a.voceRef.voceId })
            assertTrue(assertNotNull(it.sintesi.vista(reg1)?.mostrato).superato, "una Revisione rende superato")

            // Queue position: reg-2 runs (held), reg-1 waits behind it.
            val barriera = CountDownLatch(1)
            it.diarizzatoreScriptato.barriera = barriera
            try {
                it.avviaElaborazione(reg2)
                attendiFinche(timeout = 10.seconds, messaggio = "reg-2 in corso") {
                    it.stato(reg2) == StatoElaborazioneVista.IN_CORSO
                }
                it.avviaElaborazione(reg1)
                attendiFinche(timeout = 10.seconds, messaggio = "reg-1 in coda in posizione 1") {
                    it.collaboratori.posizioniNellaCoda.istantanea().elaborazioni[reg1] == 1
                }
            } finally {
                barriera.countDown()
            }
            attendiFinche(timeout = 30.seconds, messaggio = "la coda esegue reg-2 e poi reg-1") {
                listOf(reg2, reg1).all { r -> it.stato(r) == StatoElaborazioneVista.COMPLETATA }
            }

            // Elimina of the migrated Parte: the Incontro and its Voci cease with it.
            it.collaboratori.eliminaRegistrazione(EliminaRegistrazione(reg1)).atteso()
            assertNull(it.porte.catalogo.parti(incontro), "l'Incontro cessa con la sua unica Parte")
            assertNull(it.porte.trascritti.trova(incontro))
            assertEquals(emptyList(), it.porte.riassunti.trova(incontro))
            assertEquals(emptyList(), it.porte.attribuzioni.diIncontro(incontro))
            assertEquals(listOf(reg2), it.collaboratori.registrazioni().map { r -> r.registrazioneId })
        }
    }

    private companion object {
        /** reg-1: transcribed (Voci 1..2), Voce 1 = Anna, a pronto Riassunto with Fonti. reg-2: bare. */
        val RIGHE_V7 = listOf(
            "INSERT INTO progetto(id, nome) VALUES ('progetto-1', 'Progetto')",
            "INSERT INTO registrazione(id, progetto_id, titolo, riferimento_audio, durata_ms, data_registrazione, " +
                "aggiunta_alle) VALUES ('reg-1', 'progetto-1', 't', 'audio/reg-1.wav', 3000, '2026-09-25', 0)",
            "INSERT INTO registrazione(id, progetto_id, titolo, riferimento_audio, durata_ms, data_registrazione, " +
                "aggiunta_alle) VALUES ('reg-2', 'progetto-1', 'u', 'audio/reg-2.wav', 3000, '2026-09-26', 1)",
            "INSERT INTO parlante(id, progetto_id, nome, nome_normalizzato, tipo, stato) " +
                "VALUES ('parlante-1', 'progetto-1', 'Anna', 'anna', 'ricorrente', 'attivo')",
            "INSERT INTO trascritto(registrazione_id, prossima_voce, prossimo_segmento) VALUES ('reg-1', 3, 3)",
            "INSERT INTO voce(registrazione_id, numero) VALUES ('reg-1', 1), ('reg-1', 2)",
            "INSERT INTO segmento(registrazione_id, numero, voce_numero, inizio_ms, fine_ms, testo, confermato) " +
                "VALUES ('reg-1', 1, 1, 0, 1000, 'a', 0), ('reg-1', 2, 2, 2000, 3000, 'b', 0)",
            "INSERT INTO elaborazione(id, registrazione_id, stato, creata_alle, avviata_alle, motivo_fallimento, " +
                "numero_persone) VALUES ('elab-1', 'reg-1', 'completata', 0, 1, NULL, 2)",
            "INSERT INTO attribuzione(registrazione_id, voce_id, progetto_id, parlante_id) " +
                "VALUES ('reg-1', 1, 'progetto-1', 'parlante-1')",
            "INSERT INTO impronta_vocale(parlante_id, registrazione_id, voce_id, impronta, sorgente_impronta, " +
                "modello_impronta) VALUES ('parlante-1', 'reg-1', 1, X'010203', '0-1000', 'modello-1')",
            "INSERT INTO riassunto(id, registrazione_id, stato, argomento, lunghezza_massima_parole, richiesto_alle, " +
                "avviato_alle, motivo_fallimento, sommario, omessi, struttura) VALUES " +
                "('r-pronto', 'reg-1', 'pronto', NULL, 2000, 0, 1, NULL, '{V1} e {V2} fanno il punto.', 0, '1:1,2:2')",
            "INSERT INTO riassunto_elemento(riassunto_id, tipo, posizione, testo, voce_id) VALUES " +
                "('r-pronto', 'decisione', 0, 'si parte', NULL)",
            "INSERT INTO riassunto_fonte(riassunto_id, tipo, posizione, segmento_id) VALUES " +
                "('r-pronto', 'decisione', 0, 1)",
        )
    }
}
