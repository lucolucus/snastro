package snastro.parlanti.applicazione.politiche

import snastro.kernel.Esito
import snastro.kernel.IntervalloMs
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
import snastro.parlanti.dominio.ImprontaVocale
import snastro.parlanti.dominio.Nome
import snastro.parlanti.dominio.Parlante
import snastro.parlanti.dominio.SorgenteImpronta
import snastro.parlanti.dominio.TipoParlante
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [ApplicaRevisionePolitica] against the repository fakes (D1). STRUCTURAL part of [INV-21]/[INV-25]
 * only (ADR 0012 Amendment (b)): no ML port is even wired (AC-291) — surviving rows are KEPT as they
 * were (stale by `sorgente`, refreshed after commit by RiallineaImpronte), `unire` inheritance RE-KEYS.
 */
class ApplicaRevisionePoliticaTest {
    private val parlanti = ParlanteRepositoryFinta()
    private val attribuzioni = AttribuzioneRepositoryFinta()
    private val politica = ApplicaRevisionePolitica(parlanti, attribuzioni)

    private fun unParlante(id: String, tipo: TipoParlante = TipoParlante.RICORRENTE): Parlante =
        Parlante.crea(ParlanteId(id), PROGETTO, Nome.di(id).atteso(), tipo).aggregato

    private fun unaVoce(n: Int): VoceRef = VoceRef(REGISTRAZIONE, VoceId(n))

    /** Attributes [voceRef] to [parlante] with a print extracted from [SORGENTE_INIZIALE], as a command would. */
    private fun attribuisci(voceRef: VoceRef, parlante: Parlante, valore: Float = voceRef.voceId.numero.toFloat()) {
        if (parlante.attivo) {
            val impronta = Impronta(floatArrayOf(valore))
            parlante.registraImpronta(voceRef, impronta, SORGENTE_INIZIALE, MODELLO).atteso()
        }
        parlanti.salva(parlante).atteso()
        attribuzioni.salva(Attribuzione.conferma(voceRef, PROGETTO, parlante.id).aggregato)
    }

    private fun impronteDi(parlante: Parlante): List<ImprontaVocale> =
        assertNotNull(parlanti.trova(parlante.id)).impronte

    @Test
    fun `INV-21 unire con A e B attribuiti a Parlanti diversi vince A, B perde Attribuzione e impronta`() {
        val pa = unParlante("id-pa")
        val pb = unParlante("id-pb")
        attribuisci(unaVoce(1), pa)
        attribuisci(unaVoce(2), pb)
        val rigaA = impronteDi(pa).single()

        politica.applicaVociUnite(REGISTRAZIONE, sopravvissuta = VoceId(1), rimossa = VoceId(2)).atteso()

        assertNull(attribuzioni.trova(unaVoce(2)), "B perde l'Attribuzione")
        assertEquals(emptyList(), impronteDi(pb), "l'impronta derivata da B e cancellata")
        assertEquals(pa.id, assertNotNull(attribuzioni.trova(unaVoce(1))).parlanteId, "vince A")
        assertEquals(listOf(rigaA), impronteDi(pa), "la riga di A resta com'era (obsoleta), nessuna estrazione")
    }

    @Test
    fun `INV-21 unire con A attribuita e B no, A mantiene la sua riga obsoleta aggiornata dopo il commit`() {
        val pa = unParlante("id-pa")
        attribuisci(unaVoce(1), pa)
        val rigaPrima = impronteDi(pa).single()
        val intervalliUniti = listOf(IntervalloMs(0, 1_000), IntervalloMs(2_000, 5_000))

        politica.applicaVociUnite(REGISTRAZIONE, sopravvissuta = VoceId(1), rimossa = VoceId(2)).atteso()

        assertNull(attribuzioni.trova(unaVoce(2)))
        assertEquals(pa.id, assertNotNull(attribuzioni.trova(unaVoce(1))).parlanteId)
        val rigaDopo = impronteDi(pa).single()
        assertEquals(rigaPrima, rigaDopo, "la riga resta identica: nessuna estrazione nella transazione")
        assertTrue(
            rigaDopo.obsoleta(SorgenteImpronta.di(intervalliUniti).chiave, MODELLO),
            "la sorgente non corrisponde piu ai Segmenti uniti: RiallineaImpronte la aggiornera",
        )
    }

    @Test
    fun `INV-21 unire con B attribuita a P attivo e A no, Attribuzione e riga di B sono ri-chiavate su A`() {
        val p = unParlante("id-p", tipo = TipoParlante.OCCASIONALE)
        attribuisci(unaVoce(2), p, valore = 8f)
        val rigaB = impronteDi(p).single()

        politica.applicaVociUnite(REGISTRAZIONE, sopravvissuta = VoceId(1), rimossa = VoceId(2)).atteso()

        assertNull(attribuzioni.trova(unaVoce(2)), "nessuna Attribuzione per B")
        assertEquals(p.id, assertNotNull(attribuzioni.trova(unaVoce(1))).parlanteId, "Attribuzione(A) = P")
        assertEquals(
            listOf(ImprontaVocale(unaVoce(1), rigaB.impronta, rigaB.sorgente, rigaB.modello)),
            impronteDi(p),
            "la riga di B e ri-chiavata su A conservando impronta, sorgente e modello (quindi obsoleta)",
        )
        assertTrue(assertNotNull(parlanti.trova(p.id)).attivo, "P occasionale NON cessa: INV-25 non scatta")
    }

    @Test
    fun `INV-21 unire con B attribuita a P eliminato e A no, solo l Attribuzione tombstone e ri-chiavata`() {
        val p = unParlante("id-p")
        attribuisci(unaVoce(2), p)
        p.elimina().atteso()
        parlanti.salva(p).atteso()

        politica.applicaVociUnite(REGISTRAZIONE, sopravvissuta = VoceId(1), rimossa = VoceId(2)).atteso()

        assertNull(attribuzioni.trova(unaVoce(2)), "B perde l'Attribuzione")
        assertEquals(p.id, assertNotNull(attribuzioni.trova(unaVoce(1))).parlanteId, "A eredita il tombstone")
        assertEquals(emptyList(), impronteDi(p), "nessuna riga d'impronta creata: P resta a zero impronte (INV-13)")
        assertTrue(assertNotNull(parlanti.trova(p.id)).eliminato)
    }

    @Test
    fun `INV-21 unire con A e B attribuiti allo stesso Parlante, A mantiene Attribuzione e riga, B le perde`() {
        val p = unParlante("id-p", tipo = TipoParlante.OCCASIONALE)
        attribuisci(unaVoce(1), p)
        attribuisci(unaVoce(2), p)
        val rigaA = impronteDi(p).single { it.voceRef == unaVoce(1) }

        politica.applicaVociUnite(REGISTRAZIONE, sopravvissuta = VoceId(1), rimossa = VoceId(2)).atteso()

        assertNull(attribuzioni.trova(unaVoce(2)), "B perde l'Attribuzione")
        assertEquals(p.id, assertNotNull(attribuzioni.trova(unaVoce(1))).parlanteId)
        assertEquals(listOf(rigaA), impronteDi(p), "A tiene la propria riga (obsoleta), quella di B e cancellata")
        assertTrue(assertNotNull(parlanti.trova(p.id)).attivo, "e ancora attribuito da A: non cessa")
    }

    @Test
    fun `INV-21 dividere A' nasce senza Attribuzione, A mantiene Attribuzione e riga obsoleta`() {
        val pa = unParlante("id-pa")
        attribuisci(unaVoce(1), pa)
        val rigaPrima = impronteDi(pa).single()

        politica.applicaVoceDivisa(REGISTRAZIONE, origine = VoceId(1)).atteso()

        assertNull(attribuzioni.trova(unaVoce(2)), "A' nasce senza Attribuzione")
        assertEquals(pa.id, assertNotNull(attribuzioni.trova(unaVoce(1))).parlanteId)
        assertEquals(listOf(rigaPrima), impronteDi(pa), "riga invariata, aggiornata dopo il commit")
    }

    @Test
    fun `INV-21 riassegnare svuota la sorgente e la destinazione nuova resta senza Attribuzione`() {
        val pda = unParlante("id-pda")
        attribuisci(unaVoce(1), pda)

        politica.applicaSegmentoRiassegnato(
            REGISTRAZIONE,
            da = VoceId(1),
            a = VoceId(2),
            daRimossa = true,
            aNuova = true,
        ).atteso()

        assertNull(attribuzioni.trova(unaVoce(1)), "la sorgente svuotata perde l'Attribuzione")
        assertEquals(emptyList(), impronteDi(pda), "la sorgente svuotata perde l'impronta")
        assertTrue(assertNotNull(parlanti.trova(pda.id)).attivo, "e ricorrente: resta (INV-25)")
        assertNull(attribuzioni.trova(unaVoce(2)), "la destinazione nuova nasce senza Attribuzione")
    }

    @Test
    fun `INV-21 riassegnare tra Voci che sopravvivono lascia entrambe le righe com erano`() {
        val pda = unParlante("id-pda")
        val pa = unParlante("id-pa")
        attribuisci(unaVoce(1), pda)
        attribuisci(unaVoce(2), pa)
        val righePrima = impronteDi(pda) + impronteDi(pa)

        politica.applicaSegmentoRiassegnato(
            REGISTRAZIONE,
            da = VoceId(1),
            a = VoceId(2),
            daRimossa = false,
            aNuova = false,
        ).atteso()

        assertEquals(pda.id, assertNotNull(attribuzioni.trova(unaVoce(1))).parlanteId)
        assertEquals(pa.id, assertNotNull(attribuzioni.trova(unaVoce(2))).parlanteId)
        assertEquals(righePrima, impronteDi(pda) + impronteDi(pa), "righe invariate (obsolete), nessuna estrazione")
    }

    @Test
    fun `INV-25 un occasionale rimasto senza Attribuzioni cessa, un ricorrente resta`() {
        val occasionale = unParlante("id-occ", tipo = TipoParlante.OCCASIONALE)
        val ricorrente = unParlante("id-ric")
        attribuisci(unaVoce(1), occasionale)
        attribuisci(unaVoce(2), ricorrente)

        politica.applicaSegmentoRiassegnato(REGISTRAZIONE, VoceId(1), VoceId(97), daRimossa = true, aNuova = true)
            .atteso()
        politica.applicaSegmentoRiassegnato(REGISTRAZIONE, VoceId(2), VoceId(98), daRimossa = true, aNuova = true)
            .atteso()

        assertNull(parlanti.trova(occasionale.id), "l'occasionale rimasto senza Attribuzioni cessa di esistere")
        assertTrue(assertNotNull(parlanti.trova(ricorrente.id), "il ricorrente resta").attivo)
        assertEquals(emptyList(), attribuzioni.diParlante(ricorrente.id))
    }

    @Test
    fun `INV-25 unire con A e B attribuiti a Parlanti diversi fa cessare l occasionale di B`() {
        val pa = unParlante("id-pa")
        val pb = unParlante("id-pb", tipo = TipoParlante.OCCASIONALE)
        attribuisci(unaVoce(1), pa)
        attribuisci(unaVoce(2), pb)

        politica.applicaVociUnite(REGISTRAZIONE, sopravvissuta = VoceId(1), rimossa = VoceId(2)).atteso()

        assertNull(parlanti.trova(pb.id), "l'occasionale di B, senza piu Attribuzioni, cessa")
    }

    @Test
    fun `INV-25 un occasionale con altre Attribuzioni non cessa`() {
        val occasionale = unParlante("id-occ", tipo = TipoParlante.OCCASIONALE)
        val voceAltrove = VoceRef(RegistrazioneId("registrazione-2"), VoceId(1))
        attribuisci(unaVoce(1), occasionale)
        attribuzioni.salva(Attribuzione.conferma(voceAltrove, PROGETTO, occasionale.id).aggregato)

        politica.applicaSegmentoRiassegnato(REGISTRAZIONE, VoceId(1), VoceId(2), daRimossa = true, aNuova = true)
            .atteso()

        assertNull(attribuzioni.trova(unaVoce(1)), "la sorgente svuotata perde questa Attribuzione")
        assertEquals(emptyList(), impronteDi(occasionale), "l'unica impronta era quella della Voce svuotata")
        assertEquals(listOf(voceAltrove), attribuzioni.diParlante(occasionale.id).map { it.voceRef })
    }

    @Test
    fun `AC-96 il ParlanteRepository che fallisce salva fa restituire alla policy il suo Errore`() {
        val p = unParlante("id-p")
        attribuisci(unaVoce(1), p)
        val politicaConGuasto = ApplicaRevisionePolitica(ParlanteRepositorySalvaFallisce(parlanti), attribuzioni)

        val errore = politicaConGuasto
            .applicaSegmentoRiassegnato(REGISTRAZIONE, VoceId(1), VoceId(2), daRimossa = true, aNuova = true)
            .erroreAtteso<ErroreParlanti.NomeGiaInUso>()

        assertEquals(ErroreParlanti.NomeGiaInUso(p.nome.valore), errore)
    }

    @Test
    fun `AC-96 anche l eredita di unire propaga l Errore del ParlanteRepository`() {
        val p = unParlante("id-p")
        attribuisci(unaVoce(2), p)
        val politicaConGuasto = ApplicaRevisionePolitica(ParlanteRepositorySalvaFallisce(parlanti), attribuzioni)

        politicaConGuasto.applicaVociUnite(REGISTRAZIONE, sopravvissuta = VoceId(1), rimossa = VoceId(2))
            .erroreAtteso<ErroreParlanti.NomeGiaInUso>()
    }

    @Test
    fun `AC-291 nessuna riga d impronta e creata o ri-estratta dalla policy`() {
        val p = unParlante("id-p")
        val q = unParlante("id-q")
        attribuisci(unaVoce(1), p)
        attribuisci(unaVoce(3), q)
        attribuisci(unaVoce(4), q)
        val impronteIniziali = (impronteDi(p) + impronteDi(q)).map { it.impronta }.toSet()

        politica.applicaVoceDivisa(REGISTRAZIONE, origine = VoceId(1)).atteso()
        politica.applicaSegmentoRiassegnato(REGISTRAZIONE, VoceId(3), VoceId(4), daRimossa = false, aNuova = false)
            .atteso()
        politica.applicaVociUnite(REGISTRAZIONE, sopravvissuta = VoceId(5), rimossa = VoceId(1)).atteso()

        val righe = impronteDi(p) + impronteDi(q)
        assertEquals(3, righe.size, "nessuna riga creata")
        assertTrue(
            righe.all { it.impronta in impronteIniziali && it.sorgente == SORGENTE_INIZIALE },
            "nulla ri-estratto",
        )
    }

    /** [ParlanteRepository] whose [salva] always fails, like ADR 0007's unique index (AC-96). */
    private class ParlanteRepositorySalvaFallisce(delegato: ParlanteRepository) : ParlanteRepository by delegato {
        override fun salva(p: Parlante): Esito<Unit> = Esito.Errore(ErroreParlanti.NomeGiaInUso(p.nome.valore))
    }

    private companion object {
        val REGISTRAZIONE = RegistrazioneId("registrazione-1")
        val PROGETTO = ProgettoId("progetto-1")
        const val SORGENTE_INIZIALE = "0-1000"
        const val MODELLO = "finto"
    }
}
