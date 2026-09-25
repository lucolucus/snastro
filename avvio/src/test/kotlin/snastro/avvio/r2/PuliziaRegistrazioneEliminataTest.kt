package snastro.avvio.r2

import org.junit.jupiter.api.io.TempDir
import snastro.kernel.DispatcherEventiInMemoria
import snastro.kernel.ErroreDiProva
import snastro.kernel.Esito
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.RiferimentoAudio
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.atteso
import snastro.progetto.applicazione.eventi.RegistrazioneEliminata
import snastro.progetto.applicazione.porte.ArchivioAudio
import snastro.progetto.applicazione.porte.ArchivioAudioFinta
import snastro.ui.lettore.LettoreAudio
import snastro.ui.lettore.LettoreAudioFinta
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** AC-632: the after-commit Progetto-side cleanup of a deleted Registrazione, its order and its failure rule. */
class PuliziaRegistrazioneEliminataTest {
    @TempDir
    lateinit var cartella: Path

    private val passi = CopyOnWriteArrayList<String>()
    private val dispatcher = DispatcherEventiInMemoria(UnitaDiLavoroFinta())
    private val finta = LettoreAudioFinta()
    private val archivioFinto = ArchivioAudioFinta()

    private val lettore = object : LettoreAudio by finta {
        override fun pausa() {
            passi += "pausa"
            finta.pausa()
        }
    }
    private val archivio = object : ArchivioAudio by archivioFinto {
        override fun scarta(r: RiferimentoAudio) {
            passi += "scarta ${r.percorsoRelativo} (wav presente: ${Files.exists(wav(R))})"
            archivioFinto.scarta(r)
        }
    }

    private fun registra() {
        PuliziaRegistrazioneEliminata(dispatcher, lettore, archivio, cartella).dimenticaPosto =
            { id -> passi += "dimentica ${id.valore} (wav presente: ${Files.exists(wav(R))})" }
    }

    @Test
    fun `AC-632 dopo il commit pausa il lettore, scarta l audio, cancella il WAV e dimentica il posto, in ordine`() {
        registra()
        creaWav(R)
        finta.riproduciDa(R, 0)

        pubblica(eliminata(R)).atteso()

        assertEquals(
            listOf(
                "pausa",
                "scarta audio/r.m4a (wav presente: true)",
                "dimentica r (wav presente: false)",
            ),
            passi,
        )
        assertFalse(finta.stato.value.inRiproduzione)
    }

    @Test
    fun `AC-632 il lettore che tiene un altra Registrazione non e toccato`() {
        registra()
        creaWav(R)
        finta.riproduciDa(ALTRA, 0)

        pubblica(eliminata(R)).atteso()

        assertFalse("pausa" in passi)
        assertTrue(finta.stato.value.inRiproduzione)
    }

    @Test
    fun `AC-632 file gia assenti, nessun errore (idempotente)`() {
        registra()
        pubblica(eliminata(R)).atteso()
        pubblica(eliminata(R)).atteso()

        assertEquals(2, passi.count { it.startsWith("dimentica") })
    }

    @Test
    fun `AC-632 un errore di I-O e registrato e non lancia, il resto prosegue`() {
        registra()
        // A non-empty directory where the WAV should be: deleting it fails with an IOException.
        Files.createDirectories(wav(R).resolve("dentro"))

        pubblica(eliminata(R)).atteso()

        assertTrue(Files.exists(wav(R)), "resta per la prossima apertura (AC-633)")
        assertEquals(1, passi.count { it.startsWith("dimentica") })
    }

    @Test
    fun `AC-632 mai su rollback`() {
        registra()
        creaWav(R)
        finta.riproduciDa(R, 0)

        dispatcher.unitaDiLavoro.inTransazione {
            dispatcher.pubblica(eliminata(R))
            Esito.Errore(ErroreDiProva.Fallito("rollback"))
        }

        assertEquals(emptyList(), passi)
        assertTrue(Files.exists(wav(R)))
        assertTrue(finta.stato.value.inRiproduzione)
    }

    private fun pubblica(evento: RegistrazioneEliminata): Esito<Unit> =
        dispatcher.unitaDiLavoro.inTransazione { Esito.Ok(dispatcher.pubblica(evento)) }

    private fun wav(id: RegistrazioneId): Path = cartella.resolve("cache/audio/${id.valore}.wav")

    private fun creaWav(id: RegistrazioneId) {
        Files.createDirectories(wav(id).parent)
        Files.write(wav(id), byteArrayOf(1, 2, 3))
    }

    private companion object {
        val R = RegistrazioneId("r")
        val ALTRA = RegistrazioneId("altra")

        fun eliminata(id: RegistrazioneId) = RegistrazioneEliminata(
            registrazioneId = id,
            progettoId = ProgettoId("p"),
            titolo = "Riunione",
            dataRegistrazione = LocalDate.parse("2026-09-12"),
            riferimentoAudio = RiferimentoAudio("audio/${id.valore}.m4a"),
        )
    }
}
