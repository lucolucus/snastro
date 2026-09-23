package snastro.parlanti.applicazione.politiche

import io.mockk.mockk
import io.mockk.verify
import snastro.kernel.CampioniAudio
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
import snastro.parlanti.applicazione.porte.DecodificatoreAudio
import snastro.parlanti.applicazione.porte.DecodificatoreAudioFinta
import snastro.parlanti.applicazione.porte.EstrattoreImpronta
import snastro.parlanti.applicazione.porte.EstrattoreImprontaFinta
import snastro.parlanti.applicazione.porte.LettoreVociFinta
import snastro.parlanti.applicazione.porte.ParlanteRepository
import snastro.parlanti.applicazione.porte.ParlanteRepositoryFinta
import snastro.parlanti.applicazione.porte.RigaImpronta
import snastro.parlanti.applicazione.porte.VoceVista
import snastro.parlanti.dominio.Attribuzione
import snastro.parlanti.dominio.ErroreParlanti
import snastro.parlanti.dominio.Impronta
import snastro.parlanti.dominio.Nome
import snastro.parlanti.dominio.Parlante
import snastro.parlanti.dominio.TipoParlante
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApplicaRevisionePoliticaTest {
    private val parlanti = ParlanteRepositoryFinta()
    private val attribuzioni = AttribuzioneRepositoryFinta()

    // TODO(option-c follow-up): ML Finte built WITHOUT the UnitaDiLavoroFinta (no in-transaction guard, AC-272)
    // because this service still extracts inside its transaction; the option-(c) rework passes the uow.
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
        pa.registraImpronta(voceA, Impronta(floatArrayOf(0f, 0f)), "0-1000", "finto").atteso()
        pb.registraImpronta(voceB, Impronta(floatArrayOf(1f, 1f)), "0-1000", "finto").atteso()
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
        pa.registraImpronta(voceA, improntaIniziale, "0-1000", "finto").atteso()
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
        pa.registraImpronta(voceOrigine, improntaIniziale, "0-1000", "finto").atteso()
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
        pda.registraImpronta(voceDa, Impronta(floatArrayOf(3f, 3f)), "0-1000", "finto").atteso()
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
        occasionale.registraImpronta(voceOcc, Impronta(floatArrayOf(4f)), "0-1000", "finto").atteso()
        ricorrente.registraImpronta(voceRic, Impronta(floatArrayOf(5f)), "0-1000", "finto").atteso()
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

    // --- F1 (code-review HIGH): una Voce attribuita a un Parlante ELIMINATO non deve mai far fallire
    // la Revisione. Per [INV-15] un'impronta esiste solo mentre il Parlante e `attivo`: past il
    // tombstone ([INV-13]/[INV-24]) l'Attribuzione resta, ma niente decodifica/estrazione/scrittura
    // deve avvenire — la ri-derivazione va semplicemente saltata, `Ok`. --------------------------------

    @Test
    fun `INV-21 dividere con l origine attribuita a un Parlante eliminato non ri-deriva`() {
        val pa = unParlante("id-pa")
        pa.elimina().atteso()
        parlanti.salva(pa).atteso()
        val voceOrigine = unaVoce(1)
        attribuzioni.salva(attribuisci(voceOrigine, pa.id))
        val intervalli = listOf(IntervalloMs(0, 1000))
        val decodificatoreMock = mockk<DecodificatoreAudio>()
        val estrattoreMock = mockk<EstrattoreImpronta>()
        val pol = ApplicaRevisionePolitica(
            parlanti,
            attribuzioni,
            LettoreVociFinta(mapOf(REGISTRAZIONE to listOf(VoceVista(voceOrigine, intervalli)))),
            decodificatoreMock,
            estrattoreMock,
        )

        pol.applicaVoceDivisa(REGISTRAZIONE, origine = VoceId(1)).atteso()

        assertNotNull(
            attribuzioni.trova(voceOrigine),
            "il Parlante eliminato mantiene l'Attribuzione tombstone (INV-13)",
        )
        assertEquals(
            emptyList(),
            assertNotNull(parlanti.trova(pa.id)).impronte,
            "nessuna impronta per un eliminato (INV-15)",
        )
        verify(exactly = 0) { decodificatoreMock.campioni(any(), any()) }
        verify(exactly = 0) { estrattoreMock.estrai(any()) }
    }

    @Test
    fun `INV-21 unire con la sopravvissuta attribuita a un Parlante eliminato non ri-deriva`() {
        val pa = unParlante("id-pa")
        pa.elimina().atteso()
        parlanti.salva(pa).atteso()
        val voceA = unaVoce(1)
        attribuzioni.salva(attribuisci(voceA, pa.id))
        val intervalliMerged = listOf(IntervalloMs(0, 1000), IntervalloMs(1000, 2000))
        val decodificatoreMock = mockk<DecodificatoreAudio>()
        val estrattoreMock = mockk<EstrattoreImpronta>()
        val pol = ApplicaRevisionePolitica(
            parlanti,
            attribuzioni,
            LettoreVociFinta(mapOf(REGISTRAZIONE to listOf(VoceVista(voceA, intervalliMerged)))),
            decodificatoreMock,
            estrattoreMock,
        )

        pol.applicaVociUnite(REGISTRAZIONE, sopravvissuta = VoceId(1), rimossa = VoceId(2)).atteso()

        assertNotNull(attribuzioni.trova(voceA), "il Parlante eliminato mantiene l'Attribuzione tombstone")
        assertEquals(emptyList(), assertNotNull(parlanti.trova(pa.id)).impronte)
        verify(exactly = 0) { decodificatoreMock.campioni(any(), any()) }
        verify(exactly = 0) { estrattoreMock.estrai(any()) }
    }

    @Test
    fun `INV-21 riassegnare su una destinazione esistente attribuita a un Parlante eliminato non ri-deriva`() {
        val pa = unParlante("id-pa")
        pa.elimina().atteso()
        parlanti.salva(pa).atteso()
        val voceDestinazione = unaVoce(2)
        attribuzioni.salva(attribuisci(voceDestinazione, pa.id))
        val intervalli = listOf(IntervalloMs(0, 1000))
        val decodificatoreMock = mockk<DecodificatoreAudio>()
        val estrattoreMock = mockk<EstrattoreImpronta>()
        val pol = ApplicaRevisionePolitica(
            parlanti,
            attribuzioni,
            LettoreVociFinta(mapOf(REGISTRAZIONE to listOf(VoceVista(voceDestinazione, intervalli)))),
            decodificatoreMock,
            estrattoreMock,
        )

        pol.applicaSegmentoRiassegnato(
            REGISTRAZIONE,
            da = VoceId(1),
            a = VoceId(2),
            daRimossa = false,
            aNuova = false,
        ).atteso()

        assertNotNull(attribuzioni.trova(voceDestinazione), "il Parlante eliminato mantiene l'Attribuzione tombstone")
        assertEquals(emptyList(), assertNotNull(parlanti.trova(pa.id)).impronte)
        verify(exactly = 0) { decodificatoreMock.campioni(any(), any()) }
        verify(exactly = 0) { estrattoreMock.estrai(any()) }
    }

    // --- F2 (code-review MED): AC-96 riscritto su un fallimento REALE. Il caso "Parlante eliminato"
    // non e piu un errore (F1 sopra): qui la policy fallisce per una ragione di infrastruttura vera.
    // L'annullamento END-TO-END della Revisione (rollback del Trascritto) e provato altrove, da
    // abbonato-revisione-parlanti's AC-143 — qui la policy non possiede la transazione. -------------

    @Test
    fun `AC-96 il ParlanteRepository che fallisce salva restituisce il suo Errore`() {
        val pa = unParlante("id-pa")
        parlanti.salva(pa).atteso()
        val voceOrigine = unaVoce(1)
        attribuzioni.salva(attribuisci(voceOrigine, pa.id))
        val intervalli = listOf(IntervalloMs(0, 1000))
        val repositorioCheFallisce = ParlanteRepositorySalvaFallisce(parlanti)
        val pol = ApplicaRevisionePolitica(
            repositorioCheFallisce,
            attribuzioni,
            LettoreVociFinta(mapOf(REGISTRAZIONE to listOf(VoceVista(voceOrigine, intervalli)))),
            decodificatore,
            estrattore,
        )

        val errore = pol.applicaVoceDivisa(REGISTRAZIONE, origine = VoceId(1))
            .erroreAtteso<ErroreParlanti.NomeGiaInUso>()

        assertEquals(ErroreParlanti.NomeGiaInUso(pa.nome.valore), errore)
    }

    @Test
    fun `AC-96 un EstrattoreImpronta che lancia una eccezione la propaga senza intercettarla`() {
        val pa = unParlante("id-pa")
        parlanti.salva(pa).atteso()
        val voceOrigine = unaVoce(1)
        attribuzioni.salva(attribuisci(voceOrigine, pa.id))
        val intervalli = listOf(IntervalloMs(0, 1000))
        val estrattoreCheLancia = EstrattoreImprontaCheLancia
        val pol = ApplicaRevisionePolitica(
            parlanti,
            attribuzioni,
            LettoreVociFinta(mapOf(REGISTRAZIONE to listOf(VoceVista(voceOrigine, intervalli)))),
            decodificatore,
            estrattoreCheLancia,
        )

        assertFailsWith<IllegalStateException> { pol.applicaVoceDivisa(REGISTRAZIONE, origine = VoceId(1)) }
    }

    // --- F3 (code-review SHOULD, rami mancanti su comportamento gia implementato): niente da
    // correggere nel codice, solo test che coprono i rami esistenti. ---------------------------------

    @Test
    fun `INV-21 riassegnare su una destinazione esistente attribuita ri-deriva la sua impronta`() {
        val pDest = unParlante("id-dest")
        val voceDestinazione = unaVoce(2)
        val improntaIniziale = Impronta(floatArrayOf(6f, 6f))
        pDest.registraImpronta(voceDestinazione, improntaIniziale, "0-1000", "finto").atteso()
        parlanti.salva(pDest).atteso()
        attribuzioni.salva(attribuisci(voceDestinazione, pDest.id))
        val intervalliCorrenti = listOf(IntervalloMs(0, 3000))
        val pol = politica(mapOf(REGISTRAZIONE to listOf(VoceVista(voceDestinazione, intervalliCorrenti))))

        pol.applicaSegmentoRiassegnato(
            REGISTRAZIONE,
            da = VoceId(1),
            a = VoceId(2),
            daRimossa = true,
            aNuova = false,
        ).atteso()

        val attribuzioneDest = assertNotNull(attribuzioni.trova(voceDestinazione))
        assertEquals(pDest.id, attribuzioneDest.parlanteId)
        val destSalvato = assertNotNull(parlanti.trova(pDest.id))
        val improntaSalvata = destSalvato.impronte.single { it.voceRef == voceDestinazione }.impronta
        assertEquals(improntaAttesa(intervalliCorrenti), improntaSalvata)
        assertNotEquals(improntaIniziale, improntaSalvata)
    }

    @Test
    fun `INV-21 riassegnare con la sorgente non svuotata ri-deriva la sua impronta`() {
        val pSrc = unParlante("id-src")
        val voceDa = unaVoce(1)
        val improntaIniziale = Impronta(floatArrayOf(7f, 7f, 7f))
        pSrc.registraImpronta(voceDa, improntaIniziale, "0-1000", "finto").atteso()
        parlanti.salva(pSrc).atteso()
        attribuzioni.salva(attribuisci(voceDa, pSrc.id))
        val intervalliRidotti = listOf(IntervalloMs(0, 1500))
        val pol = politica(mapOf(REGISTRAZIONE to listOf(VoceVista(voceDa, intervalliRidotti))))

        pol.applicaSegmentoRiassegnato(
            REGISTRAZIONE,
            da = VoceId(1),
            a = VoceId(2),
            daRimossa = false,
            aNuova = true,
        ).atteso()

        val attribuzioneDa = assertNotNull(
            attribuzioni.trova(voceDa),
            "la sorgente non svuotata mantiene l'Attribuzione",
        )
        assertEquals(pSrc.id, attribuzioneDa.parlanteId)
        val srcSalvato = assertNotNull(parlanti.trova(pSrc.id))
        val improntaSalvata = srcSalvato.impronte.single { it.voceRef == voceDa }.impronta
        assertEquals(improntaAttesa(intervalliRidotti), improntaSalvata)
        assertNotEquals(improntaIniziale, improntaSalvata)
    }

    // user decision 2026-09-23: unire(A sopravvissuta, B rimossa) con SOLO B attribuita — A EREDITA il
    // Parlante di B (non resta senza Attribuzione): nuova Attribuzione(A -> P) confermata come
    // conferma-attribuzione, l'Attribuzione+impronta di B sparisce, l'impronta di P e ri-derivata per A
    // dai Segmenti correnti. P mantiene cosi un'Attribuzione (quella ereditata): un occasionale P NON
    // cessa (INV-25 non si applica qui).
    @Test
    fun `INV-21 unire con B attribuita e A no, A eredita il Parlante di B con l impronta ri-derivata`() {
        val pb = unParlante("id-pb", tipo = TipoParlante.OCCASIONALE)
        val voceA = unaVoce(1)
        val voceB = unaVoce(2)
        pb.registraImpronta(voceB, Impronta(floatArrayOf(8f)), "0-1000", "finto").atteso()
        parlanti.salva(pb).atteso()
        attribuzioni.salva(attribuisci(voceB, pb.id))
        val intervalliMerged = listOf(IntervalloMs(0, 1000), IntervalloMs(1000, 2000))
        val pol = politica(mapOf(REGISTRAZIONE to listOf(VoceVista(voceA, intervalliMerged))))

        pol.applicaVociUnite(REGISTRAZIONE, sopravvissuta = VoceId(1), rimossa = VoceId(2)).atteso()

        assertNull(attribuzioni.trova(voceB), "B perde l'Attribuzione")
        val attribuzioneA = assertNotNull(attribuzioni.trova(voceA), "A eredita il Parlante di B")
        assertEquals(pb.id, attribuzioneA.parlanteId)
        val pbSalvato = assertNotNull(
            parlanti.trova(pb.id),
            "occasionale, ma mantiene l'Attribuzione ereditata: non cessa",
        )
        val improntaSalvata = pbSalvato.impronte.single { it.voceRef == voceA }.impronta
        assertEquals(
            improntaAttesa(intervalliMerged),
            improntaSalvata,
            "l'impronta di P e ri-derivata dai Segmenti di A",
        )
        assertEquals(1, pbSalvato.impronte.size, "l'impronta di B (rimossa) non resta")
    }

    @Test
    fun `INV-21 unire con B attribuita a un Parlante eliminato e A no, A eredita l Attribuzione senza impronta`() {
        val pb = unParlante("id-pb")
        pb.elimina().atteso()
        parlanti.salva(pb).atteso()
        val voceA = unaVoce(1)
        val voceB = unaVoce(2)
        attribuzioni.salva(attribuisci(voceB, pb.id))
        val intervalliMerged = listOf(IntervalloMs(0, 1000), IntervalloMs(1000, 2000))
        val decodificatoreMock = mockk<DecodificatoreAudio>()
        val estrattoreMock = mockk<EstrattoreImpronta>()
        val pol = ApplicaRevisionePolitica(
            parlanti,
            attribuzioni,
            LettoreVociFinta(mapOf(REGISTRAZIONE to listOf(VoceVista(voceA, intervalliMerged)))),
            decodificatoreMock,
            estrattoreMock,
        )

        pol.applicaVociUnite(REGISTRAZIONE, sopravvissuta = VoceId(1), rimossa = VoceId(2)).atteso()

        assertNull(attribuzioni.trova(voceB), "B perde l'Attribuzione")
        val attribuzioneA = assertNotNull(attribuzioni.trova(voceA), "A eredita l'Attribuzione, anche se P e eliminato")
        assertEquals(pb.id, attribuzioneA.parlanteId)
        assertEquals(
            emptyList(),
            assertNotNull(parlanti.trova(pb.id)).impronte,
            "nessuna impronta per un eliminato (INV-15)",
        )
        verify(exactly = 0) { decodificatoreMock.campioni(any(), any()) }
        verify(exactly = 0) { estrattoreMock.estrai(any()) }
    }

    @Test
    fun `INV-21 unire con A e B attribuiti allo stesso Parlante, l impronta e ri-derivata e l Attribuzione resta`() {
        val p = unParlante("id-p", tipo = TipoParlante.OCCASIONALE)
        val voceA = unaVoce(1)
        val voceB = unaVoce(2)
        p.registraImpronta(voceA, Impronta(floatArrayOf(9f)), "0-1000", "finto").atteso()
        p.registraImpronta(voceB, Impronta(floatArrayOf(10f)), "0-1000", "finto").atteso()
        parlanti.salva(p).atteso()
        attribuzioni.salva(attribuisci(voceA, p.id))
        attribuzioni.salva(attribuisci(voceB, p.id))
        val intervalliMerged = listOf(IntervalloMs(0, 1000), IntervalloMs(1000, 2000))
        val pol = politica(mapOf(REGISTRAZIONE to listOf(VoceVista(voceA, intervalliMerged))))

        pol.applicaVociUnite(REGISTRAZIONE, sopravvissuta = VoceId(1), rimossa = VoceId(2)).atteso()

        assertNull(attribuzioni.trova(voceB), "B perde l'Attribuzione")
        val attribuzioneA = assertNotNull(attribuzioni.trova(voceA), "A mantiene la sua Attribuzione")
        assertEquals(p.id, attribuzioneA.parlanteId)
        val pSalvato = assertNotNull(
            parlanti.trova(p.id),
            "e ancora attribuito da A: non cessa nonostante sia occasionale",
        )
        val improntaSalvata = pSalvato.impronte.single { it.voceRef == voceA }.impronta
        assertEquals(improntaAttesa(intervalliMerged), improntaSalvata)
    }

    @Test
    fun `INV-25 un occasionale con altre Attribuzioni non cessa`() {
        val pOcc = unParlante("id-occ", tipo = TipoParlante.OCCASIONALE)
        val voceDa = unaVoce(1)
        val voceAltrove = VoceRef(RegistrazioneId("registrazione-2"), VoceId(1))
        pOcc.registraImpronta(voceDa, Impronta(floatArrayOf(11f)), "0-1000", "finto").atteso()
        parlanti.salva(pOcc).atteso()
        attribuzioni.salva(attribuisci(voceDa, pOcc.id))
        attribuzioni.salva(Attribuzione.conferma(voceAltrove, PROGETTO, pOcc.id).aggregato)
        val pol = politica()

        pol.applicaSegmentoRiassegnato(
            REGISTRAZIONE,
            da = VoceId(1),
            a = VoceId(2),
            daRimossa = true,
            aNuova = true,
        ).atteso()

        assertNull(attribuzioni.trova(voceDa), "la sorgente svuotata perde questa Attribuzione")
        val occSalvato = assertNotNull(parlanti.trova(pOcc.id), "resta: ha ancora un'Attribuzione altrove")
        assertEquals(emptyList(), occSalvato.impronte, "l'unica impronta rimasta era quella di voceDa, ora rimossa")
        assertEquals(listOf(voceAltrove), attribuzioni.diParlante(pOcc.id).map { it.voceRef })
    }

    /**
     * [ParlanteRepository] il cui [salva] fallisce sempre, come l'indice unico dell'ADR 0007 (F2/AC-96):
     * prova che l'Errore di AC-96 e un guasto REALE del repository, non il falso-positivo "eliminato"
     * corretto per F1. Delega ogni lettura/rimozione a [delegato].
     */
    private class ParlanteRepositorySalvaFallisce(private val delegato: ParlanteRepository) : ParlanteRepository {
        override fun trova(id: ParlanteId): Parlante? = delegato.trova(id)

        override fun delProgetto(id: ProgettoId): List<Parlante> = delegato.delProgetto(id)

        override fun nomeAttivoInUso(progettoId: ProgettoId, nome: Nome, escluso: ParlanteId?): Boolean =
            delegato.nomeAttivoInUso(progettoId, nome, escluso)

        override fun salva(p: Parlante): Esito<Unit> = Esito.Errore(ErroreParlanti.NomeGiaInUso(p.nome.valore))

        override fun rimuovi(id: ParlanteId) = delegato.rimuovi(id)

        override fun impronteDiRegistrazione(id: RegistrazioneId): List<RigaImpronta> =
            delegato.impronteDiRegistrazione(id)

        override fun impronteDelProgetto(id: ProgettoId): List<RigaImpronta> = delegato.impronteDelProgetto(id)

        override fun aggiornaImpronta(attesa: RigaImpronta, impronta: Impronta, sorgente: String, modello: String) =
            delegato.aggiornaImpronta(attesa, impronta, sorgente, modello)
    }

    /** [EstrattoreImpronta] che lancia sempre, come un guasto nativo dell'estrattore (AC-96). */
    private object EstrattoreImprontaCheLancia : EstrattoreImpronta {
        override val modello: String = "finto"

        override fun estrai(c: CampioniAudio): Impronta = error("guasto nativo dell'estrattore")
    }

    private companion object {
        val REGISTRAZIONE = RegistrazioneId("registrazione-1")
        val PROGETTO = ProgettoId("progetto-1")
    }
}
