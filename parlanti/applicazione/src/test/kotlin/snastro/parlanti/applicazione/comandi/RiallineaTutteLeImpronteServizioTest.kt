package snastro.parlanti.applicazione.comandi

import snastro.kernel.CampioniAudio
import snastro.kernel.DispatcherEventiFinta
import snastro.kernel.IntervalloMs
import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.atteso
import snastro.parlanti.applicazione.eventi.ImpronteRiallineate
import snastro.parlanti.applicazione.porte.DecodificatoreAudio
import snastro.parlanti.applicazione.porte.DecodificatoreAudioFinta
import snastro.parlanti.applicazione.porte.EstrattoreImprontaFinta
import snastro.parlanti.applicazione.porte.LettoreVociFinta
import snastro.parlanti.applicazione.porte.ParlanteRepositoryFinta
import snastro.parlanti.applicazione.porte.VoceVista
import snastro.parlanti.dominio.Impronta
import snastro.parlanti.dominio.Nome
import snastro.parlanti.dominio.Parlante
import snastro.parlanti.dominio.SorgenteImpronta
import snastro.parlanti.dominio.TipoParlante
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** [RiallineaTutteLeImpronteServizio] over the real [RiallineaImpronteServizio] and the ports' fakes (D1): AC-300. */
class RiallineaTutteLeImpronteServizioTest {
    private val parlanti = ParlanteRepositoryFinta()
    private val transazioni = UnitaDiLavoroFinta(parlanti)
    private val eventi = DispatcherEventiFinta(transazioni)
    private val voci = LettoreVociFinta(
        listOf(A, B, ALTROVE).associateWith { listOf(VoceVista(VoceRef(it, VoceId(1)), INTERVALLI)) },
    )

    private fun servizio(
        decoder: DecodificatoreAudio = DecodificatoreAudioFinta(transazioni),
    ): RiallineaTutteLeImpronteServizio {
        val riallinea = RiallineaImpronteServizio(
            eventi.unitaDiLavoro,
            voci,
            parlanti,
            decoder,
            EstrattoreImprontaFinta(unitaDiLavoro = transazioni),
            eventi,
        )
        return RiallineaTutteLeImpronteServizio(eventi.unitaDiLavoro, parlanti, riallinea)
    }

    /** A Parlante of [progetto] holding one STALE print on Voce 1 of each of [registrazioni]. */
    private fun unParlanteConImpronteObsolete(
        id: String,
        progetto: ProgettoId,
        registrazioni: List<RegistrazioneId>,
    ): ParlanteId {
        val p = Parlante.crea(ParlanteId(id), progetto, Nome.di(id).atteso(), TipoParlante.RICORRENTE).aggregato
        registrazioni.forEach { p.registraImpronta(VoceRef(it, VoceId(1)), VECCHIA, "0-1000", "altro").atteso() }
        parlanti.salva(p).atteso()
        return p.id
    }

    private fun sorgenteDi(id: ParlanteId, registrazione: RegistrazioneId): String =
        assertNotNull(parlanti.trova(id)).impronte.single { it.voceRef.registrazioneId == registrazione }.sorgente

    @Test
    fun `AC-300 riallinea ogni Registrazione del Progetto con righe d impronta e non quelle di altri Progetti`() {
        val marco = unParlanteConImpronteObsolete("Marco", PROGETTO, listOf(A, B))
        val anna = unParlanteConImpronteObsolete("Anna", PROGETTO, listOf(B))
        val estraneo = unParlanteConImpronteObsolete("Estraneo", ALTRO_PROGETTO, listOf(ALTROVE))

        servizio().esegui(RiallineaTutteLeImpronte(PROGETTO)).atteso()

        assertEquals(CHIAVE, sorgenteDi(marco, A))
        assertEquals(CHIAVE, sorgenteDi(marco, B))
        assertEquals(CHIAVE, sorgenteDi(anna, B))
        assertEquals("0-1000", sorgenteDi(estraneo, ALTROVE))
        assertEquals(
            setOf(ImpronteRiallineate(A), ImpronteRiallineate(B)),
            eventi.pubblicati.toSet(),
        )
        assertEquals(2, eventi.pubblicati.size, "una volta per Registrazione")
    }

    @Test
    fun `AC-300 un Progetto senza impronte non fa nulla`() {
        servizio().esegui(RiallineaTutteLeImpronte(PROGETTO)).atteso()

        assertEquals(emptyList(), eventi.pubblicati)
    }

    @Test
    fun `AC-300 un fallimento su una Registrazione non impedisce le altre ed e riportato`() {
        val marco = unParlanteConImpronteObsolete("Marco", PROGETTO, listOf(A, B))
        val guasto = IllegalStateException("audio di A illeggibile")
        val finta = DecodificatoreAudioFinta(transazioni)
        val fallisceSuA = object : DecodificatoreAudio {
            override fun campioni(id: RegistrazioneId, intervalli: List<IntervalloMs>): CampioniAudio =
                if (id == A) throw guasto else finta.campioni(id, intervalli)
        }

        val riportato = assertFailsWith<IllegalStateException> {
            servizio(fallisceSuA).esegui(RiallineaTutteLeImpronte(PROGETTO))
        }

        assertEquals(guasto, riportato.cause)
        assertTrue(A.valore in assertNotNull(riportato.message), "il messaggio nomina la Registrazione fallita")
        assertEquals("0-1000", sorgenteDi(marco, A))
        assertEquals(CHIAVE, sorgenteDi(marco, B), "B riallineata nonostante A")
        assertEquals(listOf(ImpronteRiallineate(B)), eventi.pubblicati)
    }

    private companion object {
        val PROGETTO = ProgettoId("progetto-1")
        val ALTRO_PROGETTO = ProgettoId("progetto-2")
        val A = RegistrazioneId("registrazione-a")
        val B = RegistrazioneId("registrazione-b")
        val ALTROVE = RegistrazioneId("registrazione-altrove")
        val INTERVALLI = listOf(IntervalloMs(0, 3_000))
        val CHIAVE = SorgenteImpronta.di(INTERVALLI).chiave
        val VECCHIA = Impronta(floatArrayOf(9f, 9f, 9f, 9f, 9f, 9f, 9f, 9f))
    }
}
