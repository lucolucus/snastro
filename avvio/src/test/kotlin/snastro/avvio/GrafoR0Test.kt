package snastro.avvio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import snastro.kernel.Esito
import snastro.kernel.GeneratoreIdFinto
import snastro.kernel.RegistrazioneId
import snastro.progetto.applicazione.letture.ElencoProgetti
import snastro.progetto.applicazione.letture.RegistrazioneDelProgettoVista
import snastro.progetto.applicazione.porte.RegistroProgettiFinta
import snastro.ui.AggiornamentiVistaFinta
import snastro.ui.DestinazioneShell
import snastro.ui.lettore.LettoreAudioFinta
import snastro.ui.registrazioni.RegistrazioniUiStato
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * AC-350 (R0's own wiring — no Compose render needed for these two facts): the shell has no Parlanti
 * section, and R0 registers no Trascrizione subscriber/source at all — statically (nothing under
 * `avvio/src/main` even imports `snastro.trascrizione`, the one dependency `:avvio` has on
 * `:trascrizione:applicazione` is only for `RegistrazioniPresenter`'s full constructor signature to
 * resolve, see `avvio/build.gradle.kts`) and behaviorally (`RegistrazioniPresenter` built through R0's
 * own [costruisciRegistrazioniPresenter] never carries an `elaborazione` state). The dynamic half of
 * AC-350 — after a REAL `AggiungiRegistrazione` no `elaborazione` row exists in the database — needs
 * real FFmpeg to probe a source and lives in `AggiungiRegistrazioneR0Test` (`@Tag("modelli")`).
 */
class GrafoR0Test {
    @Test
    fun `AC-350 la shell R0 non include la sezione Parlanti`() {
        assertEquals(setOf(DestinazioneShell.REGISTRAZIONI), SEZIONI_SHELL_R0)
        assertFalse(DestinazioneShell.PARLANTI in SEZIONI_SHELL_R0)
    }

    @Test
    fun `AC-350 avvio src main non importa mai snastro trascrizione (nessun abbonato di Trascrizione)`() {
        val radice = File("src/main/kotlin")
        val violazioni = radice.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file -> file.readLines().map { riga -> file to riga } }
            .filter { (_, riga) -> riga.trimStart().startsWith("import snastro.trascrizione") }
            .map { (file, riga) -> "$file: $riga" }
            .toList()

        assertTrue(violazioni.isEmpty(), "avvio/src/main non deve importare snastro.trascrizione: $violazioni")
    }

    @Test
    fun `AC-350 RegistrazioniPresenter costruito da R0 non porta mai uno stato di elaborazione (Trascrizione)`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val orologio = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC)
        val grafo = GrafoR0(
            scope = scope,
            io = Dispatchers.Unconfined,
            clock = orologio,
            sessione = SessioneProgettoImpl(
                registro = RegistroProgettiFinta(),
                generatoreId = GeneratoreIdFinto(),
                clock = orologio,
                scopeGenitore = scope,
            ),
            elencoProgetti = ElencoProgetti(RegistroProgettiFinta()),
            cartellaProgettiPredefinita = "/tmp/snastro",
        )
        val collaboratori = CollaboratoriProgettoAperto(
            registrazioni = {
                listOf(
                    RegistrazioneDelProgettoVista(
                        registrazioneId = RegistrazioneId("rec-1"),
                        titolo = "Riunione",
                        dataRegistrazione = LocalDate.parse("2026-01-01"),
                        durataMs = 60_000,
                    ),
                )
            },
            aggiungiRegistrazione = { Esito.Ok(Unit) },
            modificaDataRegistrazione = { Esito.Ok(Unit) },
            rinominaRegistrazione = { Esito.Ok(Unit) },
            lettoreAudio = LettoreAudioFinta(),
            aggiornamentiVista = AggiornamentiVistaFinta(),
            scope = scope,
        )

        val presenter = costruisciRegistrazioniPresenter(grafo, collaboratori)

        val stato = presenter.stato.value as RegistrazioniUiStato.Dati
        assertTrue(stato.righe.isNotEmpty())
        assertTrue(stato.righe.all { it.elaborazione == null }, "R0 non deve mai popolare elaborazione (Trascrizione)")
    }
}
