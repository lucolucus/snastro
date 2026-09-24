package snastro.avvio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.jupiter.api.io.TempDir
import snastro.kernel.Esito
import snastro.kernel.GeneratoreIdFinto
import snastro.kernel.RegistrazioneId
import snastro.kernel.atteso
import snastro.progetto.applicazione.letture.ElencoProgetti
import snastro.progetto.applicazione.letture.RegistrazioneDelProgettoVista
import snastro.progetto.applicazione.porte.RegistroProgettiFinta
import snastro.ui.AggiornamentiVistaFinta
import snastro.ui.DestinazioneShell
import snastro.ui.lettore.LettoreAudioFinta
import snastro.ui.registrazioni.RegistrazioniUiStato
import java.io.File
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * AC-350 (R0's own wiring — no Compose render needed for these facts): the shell has no Parlanti
 * section, and R0 registers no Trascrizione subscriber/source at all.
 *
 * **R1 retargeting (avvio-composizione, carry-over 6).** R1 lives in the same module, so the static
 * guard is now SCOPED TO THE R0 GRAPH: every R1 file lives in package `snastro.avvio.r1`
 * (`avvio/src/main/kotlin/snastro/avvio/r1/`), every R2 file in `snastro.avvio.r2` (avvio-parlanti), and
 * nothing OUTSIDE them may import `snastro.trascrizione`, `snastro.documento`, `snastro.modelli`,
 * `snastro.ml` or `snastro.parlanti` — the R0 graph reaches R1 only through the
 * type-neutral `EstensioneSessione` hook. The behavioral half is kept in R0 MODE: [costruisciGrafoR0]
 * without an extension builds no extension at all, and `RegistrazioniPresenter` built through R0's
 * own [costruisciRegistrazioniPresenter] never carries an `elaborazione` state. The dynamic half —
 * after a REAL `AggiungiRegistrazione` no `elaborazione` row exists — needs real FFmpeg and lives in
 * `AggiungiRegistrazioneR0Test` (`@Tag("modelli")`); its R1 counterpart (AC-371) is gate-level, in
 * `ComposizioneR1Test`.
 */
class GrafoR0Test {
    @Test
    fun `AC-350 la shell R0 non include la sezione Parlanti`() {
        assertEquals(setOf(DestinazioneShell.REGISTRAZIONI), SEZIONI_SHELL_R0)
        assertFalse(DestinazioneShell.PARLANTI in SEZIONI_SHELL_R0)
    }

    @Test
    fun `AC-350 fuori dai pacchetti r1 e r2 avvio src main non importa i contesti delle release successive`() {
        val radice = File("src/main/kotlin")
        val estensioni = listOf(File(radice, "snastro/avvio/r1"), File(radice, "snastro/avvio/r2"))
        val proibiti =
            listOf("snastro.trascrizione", "snastro.documento", "snastro.modelli", "snastro.ml", "snastro.parlanti")
        val fileR0 = radice.walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" && estensioni.none { file.startsWith(it) } }
            .toList()
        val violazioni = fileR0
            .flatMap { file -> file.readLines().map { riga -> file to riga } }
            .filter { (_, riga) -> proibiti.any { riga.trimStart().startsWith("import $it") } }
            .map { (file, riga) -> "$file: $riga" }

        assertTrue(fileR0.any { it.name == "SessioneProgettoImpl.kt" }, "la guardia deve vedere il grafo R0")
        assertTrue(violazioni.isEmpty(), "il grafo R0 non deve importare i contesti di R1: $violazioni")
    }

    @Test
    fun `AC-350 in modo R0 (nessuna estensione) aprire un progetto non costruisce alcuna sorgente di Trascrizione`(
        @TempDir cartella: Path,
    ) {
        val scope = CoroutineScope(SupervisorJob())
        val sessione = SessioneProgettoImpl(
            registro = RegistroProgettiFinta(),
            generatoreId = GeneratoreIdFinto(),
            clock = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC),
            scopeGenitore = scope,
        )

        sessione.crea(cartella.toString(), "Prova").atteso()

        assertNull(sessione.collaboratoriCorrenti()?.estensione)
        sessione.chiudi()
        scope.cancel()
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
