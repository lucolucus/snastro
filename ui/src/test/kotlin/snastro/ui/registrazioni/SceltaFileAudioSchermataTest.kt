package snastro.ui.registrazioni

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.junit.jupiter.api.Test
import snastro.kernel.RegistrazioneId
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val UNA = RigaRegistrazione(
    registrazioneId = RegistrazioneId("sola"),
    titolo = "Seduta",
    dataRegistrazione = LocalDate.of(2026, 3, 12),
    durataMs = 125_000L,
)
private val AGGIUNGI_PARTI = arrayOf("registrazioni-altre-azioni-sola", "registrazioni-menu-aggiungi-parti-sola")
private val DUE_FILE = listOf("/audio/a.m4a", "/audio/b.m4a")

/**
 * The S2 pickers: "Importa file audio…" (header and empty state) and "Aggiungi parti…" only ever ask the injected
 * [SceltaFileAudio] (never a `JFileChooser` of their own) and hand every picked path, in one call, to the
 * actions (1 file = the single import, 2+ = the AC-I70 dialog path the drop already uses); a cancel does nothing.
 */
@OptIn(ExperimentalTestApi::class)
class SceltaFileAudioSchermataTest {
    private class Registro {
        val importati = mutableListOf<List<String>>()
        val aggiunti = mutableListOf<List<String>>()
        val azioni = AzioniRegistrazioni(
            importa = { importati += it },
            modificaData = { _, _ -> },
            rinomina = { _, _ -> },
            riproduci = {},
            pausa = {},
            avviaElaborazione = {},
            modificaNumeroPersone = { _, _ -> },
            apriRiga = {},
            chiudiErrore = {},
            chiudiErroreRiga = {},
            riprova = {},
            ritrascrivi = {},
            annullaRitrascrivi = {},
            confermaRitrascrivi = {},
            annullaElaborazione = {},
            elimina = {},
            annullaElimina = {},
            confermaElimina = {},
            chiudiAvviso = {},
            aggiungiParti = { _, _, percorsi -> aggiunti += percorsi },
            scegliImporta = {},
            confermaImporta = {},
            annullaImporta = {},
            espandiIncontro = {},
            modificaOraDiInizio = { _, _ -> },
            modificaNumeroPersoneIncontro = { _, _ -> },
            avviaElaborazioniIncontro = {},
            chiudiErroreIncontro = {},
        )
    }

    private fun clicca(
        stato: RegistrazioniUiStato.Dati,
        scelta: SceltaFileAudio,
        registro: Registro,
        vararg tag: String,
    ) = runDesktopComposeUiTest(width = 1280, height = 800) {
        setContent {
            SchermataRegistrazioni(stato, registro.azioni, riduciMovimento = true, sceltaFileAudio = scelta)
        }
        tag.forEachIndexed { i, t -> onNodeWithTag(t, useUnmergedTree = i == 0 && tag.size > 1).performClick() }
    }

    private val conRighe = RegistrazioniUiStato.Dati(righe = listOf(UNA), incontri = listOf(RigaIncontro.singola(UNA)))

    @Test
    fun `un file scelto con Importa file audio va all'import con quel solo percorso`() {
        val r = Registro()
        clicca(conRighe, SceltaFileAudioFinta(listOf("/audio/a.m4a")), r, "registrazioni-importa")
        assertEquals(listOf(listOf("/audio/a.m4a")), r.importati)
    }

    @Test
    fun `due file scelti arrivano insieme, in una sola chiamata (il percorso multi-file del drop)`() {
        val r = Registro()
        clicca(conRighe, SceltaFileAudioFinta(DUE_FILE), r, "registrazioni-importa")
        assertEquals(listOf(DUE_FILE), r.importati)
    }

    @Test
    fun `lo stato vuoto usa lo stesso selettore multiplo`() {
        val r = Registro()
        val vuoto = RegistrazioniUiStato.Dati(righe = emptyList())
        clicca(vuoto, SceltaFileAudioFinta(DUE_FILE), r, "registrazioni-scegli-file")
        assertEquals(listOf(DUE_FILE), r.importati)
    }

    @Test
    fun `annullare il selettore non importa nulla`() {
        val r = Registro()
        val scelta = SceltaFileAudioFinta(emptyList())
        clicca(conRighe, scelta, r, "registrazioni-importa")
        assertEquals(1, scelta.richieste)
        assertTrue(r.importati.isEmpty())
    }

    @Test
    fun `Aggiungi parti usa il selettore multiplo e annullare non aggiunge nulla`() {
        val r = Registro()
        clicca(conRighe, SceltaFileAudioFinta(DUE_FILE), r, *AGGIUNGI_PARTI)
        assertEquals(listOf(DUE_FILE), r.aggiunti)
        val annullato = Registro()
        clicca(conRighe, SceltaFileAudioFinta(), annullato, *AGGIUNGI_PARTI)
        assertTrue(annullato.aggiunti.isEmpty())
    }
}
