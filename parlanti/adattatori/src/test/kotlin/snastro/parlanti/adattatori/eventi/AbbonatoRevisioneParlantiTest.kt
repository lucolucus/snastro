package snastro.parlanti.adattatori.eventi

import io.mockk.spyk
import io.mockk.verify
import snastro.kernel.DispatcherEventiInMemoria
import snastro.kernel.Esito
import snastro.kernel.EventoPubblicato
import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.atteso
import snastro.parlanti.applicazione.politiche.ApplicaRevisionePolitica
import snastro.parlanti.applicazione.porte.AttribuzioneRepositoryFinta
import snastro.parlanti.applicazione.porte.ParlanteRepository
import snastro.parlanti.applicazione.porte.ParlanteRepositoryFinta
import snastro.parlanti.dominio.Attribuzione
import snastro.parlanti.dominio.ErroreParlanti
import snastro.parlanti.dominio.Impronta
import snastro.parlanti.dominio.Nome
import snastro.parlanti.dominio.Parlante
import snastro.parlanti.dominio.TipoParlante
import snastro.trascrizione.applicazione.eventi.SegmentoRiassegnato
import snastro.trascrizione.applicazione.eventi.VoceDivisa
import snastro.trascrizione.applicazione.eventi.VociUnite
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [AbbonatoRevisioneParlanti] end-to-end on the in-memory "database" (AC-142/AC-143): a REAL
 * [DispatcherEventiInMemoria] over a REAL [UnitaDiLavoroFinta] whose participants are the REAL
 * [ParlanteRepositoryFinta]/[AttribuzioneRepositoryFinta] (`Ripristinabile`, so a doomed transaction
 * is genuinely rolled back to its pre-transaction snapshot — not just asserted by bookkeeping), and a
 * REAL [ApplicaRevisionePolitica] (whose own rule coverage is [ApplicaRevisionePoliticaTest]'s job:
 * this test only proves the WIRING — routing + transaction placement + rollback).
 */
class AbbonatoRevisioneParlantiTest {
    private val parlanti = ParlanteRepositoryFinta()
    private val attribuzioni = AttribuzioneRepositoryFinta()
    private val transazioni = UnitaDiLavoroFinta(parlanti, attribuzioni)

    private fun dispatcherCon(politica: ApplicaRevisionePolitica): DispatcherEventiInMemoria {
        val dispatcher = DispatcherEventiInMemoria(transazioni)
        AbbonatoRevisioneParlanti(dispatcher, politica)
        return dispatcher
    }

    private fun commit(dispatcher: DispatcherEventiInMemoria, evento: EventoPubblicato): Esito<Unit> =
        dispatcher.unitaDiLavoro.inTransazione {
            dispatcher.pubblica(evento)
            Esito.Ok(Unit)
        }

    private fun unParlante(id: String): Parlante =
        Parlante.crea(ParlanteId(id), PROGETTO, Nome.di(id).atteso(), TipoParlante.RICORRENTE).aggregato

    /** Attributes [voceRef] to [parlante] with one print, as a command would. */
    private fun attribuisci(voceRef: VoceRef, parlante: Parlante) {
        parlante.registraImpronta(voceRef, Impronta(floatArrayOf(1f)), "0-1000", "finto").atteso()
        parlanti.salva(parlante).atteso()
        attribuzioni.salva(Attribuzione.conferma(voceRef, PROGETTO, parlante.id).aggregato)
    }

    @Test
    fun `AC-142 VociUnite invoca applicaVociUnite dentro la transazione della Revisione`() {
        val politica = spyk(ApplicaRevisionePolitica(parlanti, attribuzioni))
        val dispatcher = dispatcherCon(politica)
        val pa = unParlante("id-pa")
        val pb = unParlante("id-pb")
        attribuisci(VoceRef(REG, VoceId(1)), pa)
        attribuisci(VoceRef(REG, VoceId(2)), pb)

        commit(dispatcher, VociUnite(REG, sopravvissuta = VoceId(1), rimossa = VoceId(2))).atteso()

        verify(exactly = 1) { politica.applicaVociUnite(REG, VoceId(1), VoceId(2)) }
        val messaggio = "l'effetto della policy e davvero applicato: B perde l'Attribuzione"
        assertNull(attribuzioni.trova(VoceRef(REG, VoceId(2))), messaggio)
    }

    @Test
    fun `AC-142 VoceDivisa invoca applicaVoceDivisa dentro la transazione della Revisione`() {
        val politica = spyk(ApplicaRevisionePolitica(parlanti, attribuzioni))
        val dispatcher = dispatcherCon(politica)
        val pa = unParlante("id-pa")
        attribuisci(VoceRef(REG, VoceId(1)), pa)

        val evento = VoceDivisa(REG, origine = VoceId(1), nuova = VoceId(2), segmentiSpostati = listOf(SegmentoId(2)))
        commit(dispatcher, evento).atteso()

        verify(exactly = 1) { politica.applicaVoceDivisa(REG, VoceId(1)) }
        assertNull(attribuzioni.trova(VoceRef(REG, VoceId(2))), "A' nasce senza Attribuzione")
    }

    @Test
    fun `AC-142 SegmentoRiassegnato invoca applicaSegmentoRiassegnato dentro la transazione della Revisione`() {
        val politica = spyk(ApplicaRevisionePolitica(parlanti, attribuzioni))
        val dispatcher = dispatcherCon(politica)
        val pda = unParlante("id-pda")
        attribuisci(VoceRef(REG, VoceId(1)), pda)

        val evento = SegmentoRiassegnato(
            REG,
            SegmentoId(1),
            da = VoceId(1),
            a = VoceId(2),
            daRimossa = true,
            aNuova = true,
        )
        commit(dispatcher, evento).atteso()

        verify(exactly = 1) { politica.applicaSegmentoRiassegnato(REG, VoceId(1), VoceId(2), true, true) }
        assertNull(attribuzioni.trova(VoceRef(REG, VoceId(1))), "la sorgente svuotata perde l'Attribuzione")
    }

    @Test
    fun `AC-143 un Errore della policy annulla la Revisione (end-to-end sul database in memoria)`() {
        val pa = unParlante("id-pa")
        val pb = unParlante("id-pb")
        attribuisci(VoceRef(REG, VoceId(1)), pa)
        attribuisci(VoceRef(REG, VoceId(2)), pb)
        val guasto = ApplicaRevisionePolitica(ParlanteRepositorySalvaFallisce(parlanti), attribuzioni)
        val dispatcher = dispatcherCon(guasto)

        val esito = commit(dispatcher, VociUnite(REG, sopravvissuta = VoceId(1), rimossa = VoceId(2)))

        assertTrue(esito is Esito.Errore, "la Revisione deve essere annullata")
        val trovata = attribuzioni.trova(VoceRef(REG, VoceId(2)))
        val attribuzioneB = assertNotNull(trovata, "il rollback ripristina l'Attribuzione di B")
        assertEquals(pb.id, attribuzioneB.parlanteId)
        assertEquals(1, assertNotNull(parlanti.trova(pb.id)).impronte.size, "la riga d'impronta di B e ripristinata")
    }

    /** [ParlanteRepository] whose [salva] always fails, like ADR 0007's unique index (mirrors AC-96). */
    private class ParlanteRepositorySalvaFallisce(
        private val delegato: ParlanteRepository,
    ) : ParlanteRepository by delegato {
        override fun salva(p: Parlante): Esito<Unit> = Esito.Errore(ErroreParlanti.NomeGiaInUso(p.nome.valore))
    }

    private companion object {
        val PROGETTO = ProgettoId("progetto-1")
        val REG = RegistrazioneId("registrazione-1")
    }
}
