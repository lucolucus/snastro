package snastro.parlanti.applicazione.politiche

import snastro.kernel.IntervalloMs
import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.parlanti.applicazione.porte.AttribuzioneRepositoryFinta
import snastro.parlanti.applicazione.porte.DecodificatoreAudioFinta
import snastro.parlanti.applicazione.porte.EstrattoreImprontaFinta
import snastro.parlanti.applicazione.porte.LettoreVociFinta
import snastro.parlanti.applicazione.porte.ParlanteRepositoryFinta
import snastro.parlanti.applicazione.porte.VoceVista
import snastro.parlanti.dominio.Attribuzione
import snastro.parlanti.dominio.ErroreParlanti
import snastro.parlanti.dominio.Impronta
import snastro.parlanti.dominio.Nome
import snastro.parlanti.dominio.Parlante
import snastro.parlanti.dominio.TipoParlante
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApplicaRevisionePoliticaTest {
    private val parlanti = ParlanteRepositoryFinta()
    private val attribuzioni = AttribuzioneRepositoryFinta()
    private val decodificatore = DecodificatoreAudioFinta()
    private val estrattore = EstrattoreImprontaFinta()

    private fun politica(voci: Map<RegistrazioneId, List<VoceVista>> = emptyMap()): ApplicaRevisionePolitica =
        ApplicaRevisionePolitica(parlanti, attribuzioni, LettoreVociFinta(voci), decodificatore, estrattore)

    private fun unNome(testo: String) = Nome.di(testo).atteso()

    private fun unParlante(id: String, nome: String = id, tipo: TipoParlante = TipoParlante.RICORRENTE): Parlante =
        Parlante.crea(ParlanteId(id), PROGETTO, unNome(nome), tipo).aggregato

    private fun unaVoce(n: Int): VoceRef = VoceRef(REGISTRAZIONE, VoceId(n))

    private fun attribuisci(voceRef: VoceRef, parlanteId: ParlanteId): Attribuzione =
        Attribuzione.conferma(voceRef, PROGETTO, parlanteId).aggregato

    private fun improntaAttesa(intervalli: List<IntervalloMs>): Impronta =
        estrattore.estrai(decodificatore.campioni(REGISTRAZIONE, intervalli))

    @Test
    fun `INV-21 unire con A e B attribuiti a Parlanti diversi vince A, B perde Attribuzione e impronta`() {
        val pa = unParlante("id-pa")
        val pb = unParlante("id-pb")
        val voceA = unaVoce(1)
        val voceB = unaVoce(2)
        pa.registraImpronta(voceA, Impronta(floatArrayOf(0f, 0f))).atteso()
        pb.registraImpronta(voceB, Impronta(floatArrayOf(1f, 1f))).atteso()
        parlanti.salva(pa).atteso()
        parlanti.salva(pb).atteso()
        attribuzioni.salva(attribuisci(voceA, pa.id))
        attribuzioni.salva(attribuisci(voceB, pb.id))
        val intervalliMerged = listOf(IntervalloMs(0, 1000), IntervalloMs(1000, 2000))
        val pol = politica(mapOf(REGISTRAZIONE to listOf(VoceVista(voceA, intervalliMerged))))

        pol.applicaVociUnite(REGISTRAZIONE, sopravvissuta = VoceId(1), rimossa = VoceId(2)).atteso()

        assertNull(attribuzioni.trova(voceB), "B perde l'Attribuzione")
        val pbSalvato = assertNotNull(parlanti.trova(pb.id))
        assertEquals(emptyList(), pbSalvato.impronte, "l'impronta derivata da B e cancellata")
        val attribuzioneA = assertNotNull(attribuzioni.trova(voceA), "vince A: la sua Attribuzione resta")
        assertEquals(pa.id, attribuzioneA.parlanteId)
        val paSalvato = assertNotNull(parlanti.trova(pa.id))
        assertEquals(
            improntaAttesa(intervalliMerged),
            paSalvato.impronte.single { it.voceRef == voceA }.impronta,
            "A e sopravvissuta e attribuita: la sua impronta e comunque ri-derivata dai Segmenti correnti",
        )
    }

    @Test
    fun `INV-21 unire con A attribuita e B no ri-deriva l impronta di A dai Segmenti correnti`() {
        val pa = unParlante("id-pa")
        val voceA = unaVoce(1)
        val voceB = unaVoce(2)
        val improntaIniziale = Impronta(floatArrayOf(0f, 0f, 0f))
        pa.registraImpronta(voceA, improntaIniziale).atteso()
        parlanti.salva(pa).atteso()
        attribuzioni.salva(attribuisci(voceA, pa.id))
        val intervalliCorrenti = listOf(IntervalloMs(0, 5000))
        val pol = politica(mapOf(REGISTRAZIONE to listOf(VoceVista(voceA, intervalliCorrenti))))

        pol.applicaVociUnite(REGISTRAZIONE, sopravvissuta = VoceId(1), rimossa = VoceId(2)).atteso()

        assertNull(attribuzioni.trova(voceB))
        val paSalvato = assertNotNull(parlanti.trova(pa.id))
        val improntaSalvata = paSalvato.impronte.single { it.voceRef == voceA }.impronta
        assertEquals(improntaAttesa(intervalliCorrenti), improntaSalvata)
        assertNotEquals(improntaIniziale, improntaSalvata)
    }

    @Test
    fun `INV-21 dividere A' nasce senza Attribuzione, A la mantiene con l impronta ri-derivata`() {
        val pa = unParlante("id-pa")
        val voceOrigine = unaVoce(1)
        val voceNuova = unaVoce(2)
        val improntaIniziale = Impronta(floatArrayOf(2f, 2f))
        pa.registraImpronta(voceOrigine, improntaIniziale).atteso()
        parlanti.salva(pa).atteso()
        attribuzioni.salva(attribuisci(voceOrigine, pa.id))
        val intervalliRidotti = listOf(IntervalloMs(0, 2000))
        val pol = politica(mapOf(REGISTRAZIONE to listOf(VoceVista(voceOrigine, intervalliRidotti))))

        pol.applicaVoceDivisa(REGISTRAZIONE, origine = VoceId(1)).atteso()

        assertNull(attribuzioni.trova(voceNuova), "A' nasce senza Attribuzione")
        val attribuzioneOrigine = assertNotNull(attribuzioni.trova(voceOrigine), "A mantiene la sua Attribuzione")
        assertEquals(pa.id, attribuzioneOrigine.parlanteId)
        val paSalvato = assertNotNull(parlanti.trova(pa.id))
        val improntaSalvata = paSalvato.impronte.single { it.voceRef == voceOrigine }.impronta
        assertEquals(improntaAttesa(intervalliRidotti), improntaSalvata)
        assertNotEquals(improntaIniziale, improntaSalvata)
    }

    @Test
    fun `INV-21 riassegnare svuota la sorgente e la destinazione nuova resta senza Attribuzione`() {
        val pda = unParlante("id-pda", tipo = TipoParlante.RICORRENTE)
        val voceDa = unaVoce(1)
        val voceDestinazione = unaVoce(2)
        pda.registraImpronta(voceDa, Impronta(floatArrayOf(3f, 3f))).atteso()
        parlanti.salva(pda).atteso()
        attribuzioni.salva(attribuisci(voceDa, pda.id))
        val pol = politica()

        pol.applicaSegmentoRiassegnato(
            REGISTRAZIONE,
            da = VoceId(1),
            a = VoceId(2),
            daRimossa = true,
            aNuova = true,
        ).atteso()

        assertNull(attribuzioni.trova(voceDa), "la sorgente svuotata perde l'Attribuzione")
        val pdaSalvato = assertNotNull(parlanti.trova(pda.id))
        assertEquals(emptyList(), pdaSalvato.impronte, "la sorgente svuotata perde l'impronta")
        assertTrue(pdaSalvato.attivo, "e ricorrente: resta (INV-25)")
        assertNull(attribuzioni.trova(voceDestinazione), "la destinazione nuova nasce senza Attribuzione")
    }

    @Test
    fun `INV-25 un occasionale rimasto senza Attribuzioni cessa, un ricorrente resta`() {
        val occasionale = unParlante("id-occ", tipo = TipoParlante.OCCASIONALE)
        val ricorrente = unParlante("id-ric", tipo = TipoParlante.RICORRENTE)
        val voceOcc = unaVoce(1)
        val voceRic = unaVoce(2)
        occasionale.registraImpronta(voceOcc, Impronta(floatArrayOf(4f))).atteso()
        ricorrente.registraImpronta(voceRic, Impronta(floatArrayOf(5f))).atteso()
        parlanti.salva(occasionale).atteso()
        parlanti.salva(ricorrente).atteso()
        attribuzioni.salva(attribuisci(voceOcc, occasionale.id))
        attribuzioni.salva(attribuisci(voceRic, ricorrente.id))
        val pol = politica()

        pol.applicaSegmentoRiassegnato(
            REGISTRAZIONE,
            da = VoceId(1),
            a = VoceId(97),
            daRimossa = true,
            aNuova = true,
        ).atteso()
        pol.applicaSegmentoRiassegnato(
            REGISTRAZIONE,
            da = VoceId(2),
            a = VoceId(98),
            daRimossa = true,
            aNuova = true,
        ).atteso()

        assertNull(parlanti.trova(occasionale.id), "l'occasionale rimasto senza Attribuzioni cessa di esistere")
        val ricorrenteSalvato = assertNotNull(parlanti.trova(ricorrente.id), "il ricorrente resta")
        assertTrue(ricorrenteSalvato.attivo)
        assertEquals(emptyList(), attribuzioni.diParlante(ricorrente.id))
    }

    @Test
    fun `AC-96 un errore della politica restituisce Errore`() {
        val pa = unParlante("id-pa")
        pa.elimina().atteso()
        parlanti.salva(pa).atteso()
        val voceOrigine = unaVoce(1)
        attribuzioni.salva(attribuisci(voceOrigine, pa.id))
        val intervalli = listOf(IntervalloMs(0, 1000))
        val pol = politica(mapOf(REGISTRAZIONE to listOf(VoceVista(voceOrigine, intervalli))))

        val errore = pol.applicaVoceDivisa(REGISTRAZIONE, origine = VoceId(1))
            .erroreAtteso<ErroreParlanti.ParlanteEliminatoNonModificabile>()

        assertEquals(ErroreParlanti.ParlanteEliminatoNonModificabile(pa.id), errore)
        assertNotNull(attribuzioni.trova(voceOrigine), "l'Attribuzione preesistente non viene toccata dal fallimento")
        assertEquals(emptyList(), assertNotNull(parlanti.trova(pa.id)).impronte)
    }

    private companion object {
        val REGISTRAZIONE = RegistrazioneId("registrazione-1")
        val PROGETTO = ProgettoId("progetto-1")
    }
}
