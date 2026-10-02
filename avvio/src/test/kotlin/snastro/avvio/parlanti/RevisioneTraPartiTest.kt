package snastro.avvio.parlanti

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.io.TempDir
import snastro.avvio.progetto.AmbienteProgetto
import snastro.kernel.Esito
import snastro.kernel.IncontroId
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.atteso
import snastro.progetto.applicazione.comandi.Destinazione
import snastro.trascrizione.applicazione.comandi.DividiVoce
import snastro.trascrizione.applicazione.comandi.RiassegnaSegmento
import snastro.trascrizione.applicazione.comandi.UnisciVoci
import snastro.ui.registrazione.ComandoVoce
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * RELEASE CHECK I2 (D-0043, [INV-21], ADR 0034 §2) on the REAL composition over a SQLite project file: an Incontro of
 * two Parti imported through `AggiungiRegistrazione`, one Voce attributed and printed in BOTH Parti; a Revisione in B
 * that empties that Voce's slice there must remove the print (Voce, B) in the same unit, so the deferred FK
 * `impronta_vocale` → `voce` holds and the COMMIT succeeds. The print of Parte A and the Attribuzione stay.
 */
class RevisioneTraPartiTest {
    @TempDir
    lateinit var radice: Path

    @Test
    fun `INV-21 RiassegnaSegmento che svuota la fetta della Voce in B toglie l impronta di B e il COMMIT riesce`() {
        AmbienteProgetto(radice).use {
            val s = preparaVoceInDueParti(it)
            val altra = vociDi(it, s.b).single { v -> v != s.voce }

            val esito = it.trascrizione.revisione.riassegnaSegmento.esegui(
                RiassegnaSegmento(s.b, segmentoDi(it, s.b, s.voce), altra, s.incontro),
            )

            esito.atteso() // a failing deferred FK would throw out of the COMMIT
            assertSoloImprontaDiA(it, s)
        }
    }

    @Test
    fun `INV-21 DividiVoce che svuota la fetta della Voce in B toglie l impronta di B e il COMMIT riesce`() {
        AmbienteProgetto(radice).use {
            val s = preparaVoceInDueParti(it)

            val esito = it.trascrizione.revisione.dividiVoce.esegui(
                DividiVoce(s.b, s.voce, setOf(segmentoDi(it, s.b, s.voce)), s.incontro),
            )

            esito.atteso() // a failing deferred FK would throw out of the COMMIT
            assertSoloImprontaDiA(it, s)
        }
    }

    private class Scenario(val incontro: IncontroId, val a: RegistrazioneId, val b: RegistrazioneId, val voce: VoceId)

    /**
     * Parte A and Parte B (two Voci each, `DUE_VOCI`), both transcribed. A's second Voce is named "Anna", B's second
     * Voce is confirmed as Anna too, then the two are united onto A's: the surviving Voce now speaks in both Parti and
     * Anna holds its print in both (`riassegnaImpronte`, same Parlante).
     */
    private fun preparaVoceInDueParti(ambiente: AmbienteProgetto): Scenario {
        val a = ambiente.registrazioneTrascritta()
        val incontro = ambiente.incontroDi(a)
        val prima = ambiente.collaboratori.registrazioni().map { r -> r.registrazioneId }.toSet()
        ambiente.importaIn(Destinazione.Incontro(incontro)).atteso()
        val b = ambiente.collaboratori.registrazioni().map { r -> r.registrazioneId }.single { r -> r !in prima }
        ambiente.rendiLeggibile(b)
        ambiente.trascrivi(b)
        val voceA = vociDi(ambiente, a).last()
        val voceB = vociDi(ambiente, b).last()
        comando(ambiente, ComandoVoce.Nuovo(VoceRef(incontro, voceA), "Anna"))
        val anna = assertNotNull(ambiente.porte.attribuzioni.trova(VoceRef(incontro, voceA))).parlanteId
        comando(ambiente, ComandoVoce.Conferma(VoceRef(incontro, voceB), anna))
        ambiente.trascrizione.revisione.unisciVoci.esegui(UnisciVoci(b, voceA, voceB, incontro)).atteso()
        val s = Scenario(incontro, a, b, voceA)
        assertEquals(setOf(s.voce to a, s.voce to b), impronte(ambiente), "Anna ha l'impronta nelle due Parti")
        return s
    }

    private fun assertSoloImprontaDiA(ambiente: AmbienteProgetto, s: Scenario) {
        assertEquals(setOf(s.voce to s.a), impronte(ambiente), "l'impronta (Voce, B) e' tolta, quella di A resta")
        assertNotNull(ambiente.porte.attribuzioni.trova(VoceRef(s.incontro, s.voce)), "l'Attribuzione resta")
        assertEquals(listOf(s.voce), vociDi(ambiente, s.a).filter { v -> v == s.voce }, "la Voce parla ancora in A")
    }

    private fun impronte(ambiente: AmbienteProgetto): Set<Pair<VoceId, RegistrazioneId>> =
        ambiente.porte.parlanti.impronteDelProgetto(ambiente.progetto.progettoId)
            .map { r -> r.voceRef.voceId to r.parte }.toSet()

    private fun vociDi(ambiente: AmbienteProgetto, parte: RegistrazioneId): List<VoceId> =
        checkNotNull(ambiente.trascrizione.trascritto(parte)).segmenti.map { sg -> sg.voceId }.distinct()
            .sortedBy { v -> v.numero }

    private fun segmentoDi(ambiente: AmbienteProgetto, parte: RegistrazioneId, voce: VoceId): SegmentoId =
        checkNotNull(ambiente.trascrizione.trascritto(parte)).segmenti.single { sg -> sg.voceId == voce }.segmentoId

    private fun comando(ambiente: AmbienteProgetto, c: ComandoVoce) {
        assertEquals(Esito.Ok(Unit), runBlocking { ambiente.parlanti.comandi.esegui(c) }, "$c")
    }
}
