package snastro.sintesi.adattatori.ml

import io.github.lucolucus.llamajni.LlamaError
import io.github.lucolucus.llamajni.LlamaResult
import org.junit.jupiter.api.io.TempDir
import snastro.sintesi.applicazione.porte.ModelloLinguistico
import snastro.sintesi.applicazione.porte.ModelloLinguisticoContratto
import snastro.supporto.test.pausaInTempoReale
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The port's contract on the REAL adapter over a fake of the library (inside the gate): the fake honours the
 * library's cancel semantics (polls `cancel` while it "generates", answers `Cancelled` once it is true), so the
 * adapter's cancel wiring (annullato OR the caller's interrupt, D-0009) is exercised with no native.
 */
class ModelloLinguisticoLlamaSuLibreriaFintaTest : ModelloLinguisticoContratto() {
    @TempDir
    lateinit var cartella: Path

    override val limiteAnnullamento: Duration = 2.seconds

    override fun modello(): ModelloLinguistico {
        val file = cartella.resolve("modello.gguf").also { if (Files.notExists(it)) Files.createFile(it) }
        val backend = BackendFinto(genera = { _, cancel ->
            var passi = 0
            while (passi < PASSI_DI_GENERAZIONE && !cancel()) {
                pausaInTempoReale(
                    PASSO_MS.milliseconds,
                    motivo = "generazione lenta: l'annullamento reale arriva a meta",
                )
                passi++
            }
            if (cancel()) LlamaResult.Err(LlamaError.Cancelled) else LlamaResult.Ok(unaGenerazione(RISPOSTA_VALIDA))
        })
        return ModelloLinguisticoLlama({ cartella }, { file }, { LlamaResult.Ok(backend) })
    }

    private companion object {
        const val PASSI_DI_GENERAZIONE = 20
        const val PASSO_MS = 10L
    }
}
