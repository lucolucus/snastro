package snastro.sintesi.applicazione.porte

import org.junit.jupiter.api.Test
import snastro.kernel.Esito
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** D1: the contract on the fake, plus the fake's own guarantees (AC-S14, AC-S15). */
class ModelloLinguisticoFintoTest : ModelloLinguisticoContratto() {
    private val uow = UnitaDiLavoroFinta()

    override fun modello(): ModelloLinguistico = ModelloLinguisticoFinto(uow)

    // The fake cancels immediately (no work done); the bound only absorbs class loading and scheduling
    // noise under a loaded parallel gate — the Annullato outcome is what discriminates here.
    override val limiteAnnullamento: Duration = 1.seconds

    private val richiesta =
        RichiestaRiassunto(ingresso = "[s1 V1 0:00] ciao", argomento = "combattimento", lunghezzaMassimaParole = 150)

    @Test
    fun `AC-S14 invocato dentro una transazione lancia IllegalStateException`() {
        val finto = ModelloLinguisticoFinto(uow)

        assertFailsWith<IllegalStateException> {
            uow.inTransazione { finto.riassumi(richiesta) { false } }
        }
    }

    @Test
    fun `AC-S14 invocato fuori da una transazione risponde`() {
        val finto = ModelloLinguisticoFinto(uow)
        uow.inTransazione { Esito.Ok(Unit) }

        assertEquals(ModelloLinguisticoFinto.RISPOSTA_PREDEFINITA, finto.riassumi(richiesta) { false }.atteso())
    }

    @Test
    fun `AC-S15 registra l ultima richiesta ricevuta`() {
        val finto = ModelloLinguisticoFinto(uow)
        finto.riassumi(RichiestaRiassunto("prima", null, 100)) { false }

        finto.riassumi(richiesta) { false }

        assertEquals(richiesta, finto.ultimaRichiesta)
    }

    @Test
    fun `AC-S15 puo essere istruito a restituire ogni ErroreApplicazioneSintesi`() {
        val errori = listOf(
            ErroreApplicazioneSintesi.ModelloNonDisponibile,
            ErroreApplicazioneSintesi.IngressoTroppoLungo(token = 31_000),
            ErroreApplicazioneSintesi.ErroreRuntime("metal non disponibile"),
            ErroreApplicazioneSintesi.RispostaNonValida,
            ErroreApplicazioneSintesi.Annullato,
        )
        val finto = ModelloLinguisticoFinto(uow)

        errori.forEach { errore ->
            finto.fallisci(errore)
            assertEquals(errore, finto.riassumi(richiesta) { false }.erroreAtteso<ErroreApplicazioneSintesi>())
        }
    }

    @Test
    fun `AC-S16 puo restituire di proposito Fonti non valide`() {
        val conFontiInvalide = ModelloLinguisticoFinto.RISPOSTA_PREDEFINITA.copy(
            decisioni = listOf(ElementoRisposta("Una decisione inventata.", listOf(999))),
            azioni = listOf(AzioneRisposta("Un'azione.", listOf(-1), responsabile = 42)),
        )
        val finto = ModelloLinguisticoFinto(uow).apply { rispondi(conFontiInvalide) }

        assertEquals(conFontiInvalide, finto.riassumi(richiesta) { false }.atteso())
    }

    @Test
    fun `AC-S12 il controllo dei parlanti rifiuta ogni forma diversa da V tra graffe`() {
        listOf("V1 dice di si", "come detto da [V2]", "Voce 3 propone", "{V1} e V2").forEach {
            assertTrue(parlanteFuoriForma(it), it)
        }
        listOf("{V1} e {V12} concordano", "Versione 2 del piano", "nessun parlante").forEach {
            assertFalse(parlanteFuoriForma(it), it)
        }
    }

    @Test
    fun `AC-S12 il contratto fallisce su un modello che scrive un parlante fuori forma`() {
        val finto = ModelloLinguisticoFinto(uow).apply {
            rispondi(ModelloLinguisticoFinto.RISPOSTA_PREDEFINITA.copy(sommario = "Voce 2 prepara il prototipo."))
        }
        val contratto = object : ModelloLinguisticoContratto() {
            override fun modello(): ModelloLinguistico = finto
            override val limiteAnnullamento: Duration = 1.seconds
        }

        assertFailsWith<AssertionError> {
            contratto.`AC-S12 ogni parlante nei testi della risposta e scritto solo come V tra graffe`()
        }
    }
}
