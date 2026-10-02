package snastro.avvio.sbobinatura

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.io.TempDir
import snastro.avvio.progetto.AmbienteProgetto
import snastro.kernel.Esito
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceRef
import snastro.kernel.atteso
import snastro.progetto.applicazione.comandi.Destinazione
import snastro.progetto.applicazione.comandi.EliminaRegistrazione
import snastro.supporto.test.attendiFinche
import snastro.ui.registrazione.ComandoVoce
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/**
 * The wiring of `ModuloSbobinatura` over the real composition (ADR 0035 §7, ADR 0038): the Incontro-keyed events reach
 * EVERY Parte of the Incontro, each with its own Sbobinatura. The proof is observable on disk: the Sbobinatura of the
 * Parte the event is NOT about is removed by hand, and only the Incontro fan-out can write it back.
 */
class SbobinaturaIncontroTest {
    @TempDir
    lateinit var radice: Path

    private class DuePartiTrascritte(val a: RegistrazioneId, val b: RegistrazioneId)

    private fun preparaDueParti(ambiente: AmbienteProgetto): DuePartiTrascritte {
        val a = ambiente.registrazioneTrascritta()
        val incontro = ambiente.incontroDi(a)
        val prima = ambiente.collaboratori.registrazioni().map { r -> r.registrazioneId }.toSet()
        ambiente.importaIn(Destinazione.Incontro(incontro)).atteso()
        val b = ambiente.collaboratori.registrazioni().map { r -> r.registrazioneId }.single { r -> r !in prima }
        ambiente.rendiLeggibile(b)
        ambiente.trascrivi(b)
        return DuePartiTrascritte(a, b)
    }

    private fun attendiSbobinatura(ambiente: AmbienteProgetto, parte: RegistrazioneId): Path {
        attendiFinche(timeout = 30.seconds, messaggio = "Sbobinatura di $parte scritta") {
            ambiente.sbobinatura.percorsoSbobinatura(parte) != null
        }
        return Path.of(checkNotNull(ambiente.sbobinatura.percorsoSbobinatura(parte)))
    }

    private fun togliSbobinatura(ambiente: AmbienteProgetto, parte: RegistrazioneId): Path =
        attendiSbobinatura(ambiente, parte).also(Files::delete)

    @Test
    fun `una Attribuzione sulle Voci dell Incontro rigenera la Sbobinatura di ogni Parte`() {
        AmbienteProgetto(radice).use {
            val s = preparaDueParti(it)
            attendiSbobinatura(it, s.b)
            val fileA = togliSbobinatura(it, s.a)
            val voceDiB = it.porte.trascritti.trascritto(s.b)!!.segmenti.first().voceId

            val esito = runBlocking {
                it.parlanti.comandi.esegui(ComandoVoce.Nuovo(VoceRef(it.incontroDi(s.b), voceDiB), "Berta"))
            }

            assertEquals(Esito.Ok(Unit), esito)
            attendiFinche(timeout = 30.seconds, messaggio = "Sbobinatura della Parte A riscritta dal fan-out") {
                Files.isRegularFile(fileA)
            }
        }
    }

    @Test
    fun `eliminare una Parte rigenera la Sbobinatura delle Parti che restano (TrascrittoEliminato)`() {
        AmbienteProgetto(radice).use {
            val s = preparaDueParti(it)
            attendiSbobinatura(it, s.b)
            val fileA = togliSbobinatura(it, s.a)

            it.collaboratori.eliminaRegistrazione(EliminaRegistrazione(s.b)).atteso()

            attendiFinche(timeout = 30.seconds, messaggio = "Sbobinatura della Parte A riscritta") {
                Files.isRegularFile(fileA)
            }
        }
    }
}
