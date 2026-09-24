package snastro.parlanti.applicazione.politiche

import io.mockk.spyk
import io.mockk.verify
import snastro.kernel.Esito
import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.parlanti.applicazione.porte.AttribuzioneRepositoryFinta
import snastro.parlanti.applicazione.porte.ParlanteRepository
import snastro.parlanti.applicazione.porte.ParlanteRepositoryFinta
import snastro.parlanti.dominio.Attribuzione
import snastro.parlanti.dominio.ErroreParlanti
import snastro.parlanti.dominio.Impronta
import snastro.parlanti.dominio.Nome
import snastro.parlanti.dominio.Parlante
import snastro.parlanti.dominio.TipoParlante
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [ApplicaSostituzioneTrascrittoPolitica] against the repository fakes (D1, ADR 0018 §3 + Amendment
 * 2026-09-24 (b) §1). No `EstrattoreImpronta`/`DecodificatoreAudio` port is even wired into the
 * policy's constructor — structurally it cannot decode or extract (ADR 0012 (b) `enforced_by`), so
 * AC-430's "never invoked" is covered by that gate, not a runtime call.
 */
class ApplicaSostituzioneTrascrittoPoliticaTest {
    private val parlanti = ParlanteRepositoryFinta()
    private val attribuzioni = AttribuzioneRepositoryFinta()
    private val politica = ApplicaSostituzioneTrascrittoPolitica(parlanti, attribuzioni)

    private fun unParlante(id: String, tipo: TipoParlante = TipoParlante.RICORRENTE): Parlante =
        Parlante.crea(ParlanteId(id), PROGETTO, Nome.di(id).atteso(), tipo).aggregato

    private fun unaVoce(registrazioneId: RegistrazioneId, n: Int): VoceRef = VoceRef(registrazioneId, VoceId(n))

    /** Attributes [voceRef] to [parlante] with one print, as a command would (skips a not-attivo Parlante). */
    private fun attribuisci(voceRef: VoceRef, parlante: Parlante, valore: Float = voceRef.voceId.numero.toFloat()) {
        if (parlante.attivo) {
            parlante.registraImpronta(voceRef, Impronta(floatArrayOf(valore)), "0-1000", "finto").atteso()
        }
        parlanti.salva(parlante).atteso()
        attribuzioni.salva(Attribuzione.conferma(voceRef, PROGETTO, parlante.id).aggregato)
    }

    /** A print row with NO Attribuzione (the defensive case: [INV-15] says it cannot exist). */
    private fun improntaOrfana(voceRef: VoceRef, parlante: Parlante, valore: Float) {
        parlante.registraImpronta(voceRef, Impronta(floatArrayOf(valore)), "0-1000", "finto").atteso()
        parlanti.salva(parlante).atteso()
    }

    private fun impronteDi(id: ParlanteId): List<VoceRef> =
        assertNotNull(parlanti.trova(id)).impronte.map { it.voceRef }

    @Test
    fun `AC-428 purga Attribuzioni e impronte di r, incluse quelle orfane, e lascia intatta l altra Registrazione`() {
        val pa = unParlante("id-pa")
        val pb = unParlante("id-pb")
        val pc = unParlante("id-pc")
        attribuisci(unaVoce(R, 1), pa)
        attribuisci(unaVoce(ALTRA, 1), pa, valore = 100f)
        attribuisci(unaVoce(R, 2), pb)
        improntaOrfana(unaVoce(R, 5), pb, valore = 55f) // riga senza Attribuzione, difensivo
        attribuisci(unaVoce(R, 3), pc)
        pc.elimina().atteso()
        parlanti.salva(pc).atteso()

        politica.applica(R).atteso()

        assertEquals(emptyList(), attribuzioni.diRegistrazione(R), "zero Attribuzioni di r")
        val messaggioImpronte = "zero righe d'impronta di r, incluse le orfane"
        assertEquals(emptyList(), parlanti.impronteDiRegistrazione(R), messaggioImpronte)
        assertEquals(1, attribuzioni.diRegistrazione(ALTRA).size, "l'Attribuzione dell'altra Registrazione resta")
        assertEquals(pa.id, attribuzioni.diRegistrazione(ALTRA).single().parlanteId)
        val righeAltra = parlanti.impronteDiRegistrazione(ALTRA)
        assertEquals(1, righeAltra.size, "la riga d'impronta dell'altra Registrazione resta")
        assertEquals(unaVoce(ALTRA, 1), righeAltra.single().voceRef)
    }

    @Test
    fun `AC-429 un occasionale rimasto solo in r cessa, uno con Attribuzione altrove resta con la sua impronta`() {
        val soloInR = unParlante("id-solo-r", tipo = TipoParlante.OCCASIONALE)
        val ancheAltrove = unParlante("id-anche-altrove", tipo = TipoParlante.OCCASIONALE)
        attribuisci(unaVoce(R, 1), soloInR)
        attribuisci(unaVoce(R, 2), ancheAltrove)
        attribuisci(unaVoce(ALTRA, 1), ancheAltrove, valore = 200f)

        politica.applica(R).atteso()

        assertNull(parlanti.trova(soloInR.id), "l'occasionale rimasto senza Attribuzioni cessa")
        val trovato = assertNotNull(parlanti.trova(ancheAltrove.id), "l'occasionale con Attribuzione altrove resta")
        assertTrue(trovato.attivo)
        val messaggioImpronta = "tiene solo la riga dell'altra Registrazione"
        assertEquals(listOf(unaVoce(ALTRA, 1)), impronteDi(ancheAltrove.id), messaggioImpronta)
    }

    @Test
    fun `AC-429 un ricorrente resta anche a zero Attribuzioni e zero impronte, un eliminato resta tombstone`() {
        val ricorrente = unParlante("id-ricorrente")
        val eliminato = unParlante("id-eliminato")
        attribuisci(unaVoce(R, 1), ricorrente)
        attribuisci(unaVoce(R, 2), eliminato)
        eliminato.elimina().atteso()
        parlanti.salva(eliminato).atteso()

        politica.applica(R).atteso()

        val ricorrenteDopo = assertNotNull(parlanti.trova(ricorrente.id), "il ricorrente resta")
        assertTrue(ricorrenteDopo.attivo)
        assertEquals(ricorrente.nome, ricorrenteDopo.nome)
        assertEquals(TipoParlante.RICORRENTE, ricorrenteDopo.tipo)
        assertEquals(emptyList(), ricorrenteDopo.impronte, "zero impronte dopo la purga")
        assertEquals(emptyList(), attribuzioni.diParlante(ricorrente.id), "zero Attribuzioni dopo la purga")

        val eliminatoDopo = assertNotNull(parlanti.trova(eliminato.id), "il tombstone resta")
        assertTrue(eliminatoDopo.eliminato)
        assertEquals(eliminato.nome, eliminatoDopo.nome)
        assertNull(attribuzioni.trova(unaVoce(R, 2)), "la sua Attribuzione in r e andata")
    }

    @Test
    fun `AC-430 applica due volte da lo stesso stato di applica una volta`() {
        val occasionale = unParlante("id-occ", tipo = TipoParlante.OCCASIONALE)
        val ricorrente = unParlante("id-ric")
        attribuisci(unaVoce(R, 1), occasionale)
        attribuisci(unaVoce(R, 2), ricorrente)

        politica.applica(R).atteso()
        val occasionaleDopoUna = parlanti.trova(occasionale.id)
        val ricorrenteDopoUna = parlanti.trova(ricorrente.id)

        politica.applica(R).atteso()

        assertEquals(occasionaleDopoUna, parlanti.trova(occasionale.id))
        assertEquals(ricorrenteDopoUna?.impronte, parlanti.trova(ricorrente.id)?.impronte)
        assertEquals(emptyList(), attribuzioni.diRegistrazione(R))
    }

    @Test
    fun `AC-430 su una Registrazione senza Attribuzione ne impronta restituisce Ok senza alcun salva o rimuovi`() {
        val parlantiContati = spyk(parlanti)
        val attribuzioniContate = spyk(attribuzioni)
        val politicaContata = ApplicaSostituzioneTrascrittoPolitica(parlantiContati, attribuzioniContate)

        politicaContata.applica(RegistrazioneId("registrazione-vuota")).atteso()

        verify(exactly = 0) { parlantiContati.salva(any()) }
        verify(exactly = 0) { parlantiContati.rimuovi(any()) }
        verify(exactly = 0) { attribuzioniContate.rimuovi(any()) }
    }

    @Test
    fun `AC-431 un Errore da ParlanteRepository salva e restituito invariato`() {
        val p = unParlante("id-p")
        attribuisci(unaVoce(R, 1), p)
        val parlantiGuasti = ParlanteRepositorySalvaFallisce(parlanti)
        val politicaConGuasto = ApplicaSostituzioneTrascrittoPolitica(parlantiGuasti, attribuzioni)

        val errore = politicaConGuasto.applica(R).erroreAtteso<ErroreParlanti.NomeGiaInUso>()

        assertEquals(ErroreParlanti.NomeGiaInUso(p.nome.valore), errore)
    }

    /** [ParlanteRepository] whose [salva] always fails, like ADR 0007's unique index (mirrors AC-96). */
    private class ParlanteRepositorySalvaFallisce(
        private val delegato: ParlanteRepository,
    ) : ParlanteRepository by delegato {
        override fun salva(p: Parlante): Esito<Unit> = Esito.Errore(ErroreParlanti.NomeGiaInUso(p.nome.valore))
    }

    private companion object {
        val PROGETTO = ProgettoId("progetto-1")
        val R = RegistrazioneId("registrazione-r")
        val ALTRA = RegistrazioneId("registrazione-altra")
    }
}
