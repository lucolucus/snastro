package snastro.trascrizione.dominio

import snastro.kernel.IntervalloMs
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.VoceId
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrascrittoTest {
    private val registrazioneId = RegistrazioneId("id-1")

    // --- creation ---------------------------------------------------------------------------------

    @Test
    fun `AC-20 crea numera le Voci per prima apparizione e i Segmenti per inizio poi voce`() {
        val turni = listOf(
            unSegmentoIniziale(voceIndice = 0, inizioMs = 2_000, fineMs = 3_000, testo = "f"),
            unSegmentoIniziale(voceIndice = 5, inizioMs = 0, fineMs = 1_000, testo = "b"),
            unSegmentoIniziale(voceIndice = 2, inizioMs = 500, fineMs = 1_500, testo = "d"),
            unSegmentoIniziale(voceIndice = 0, inizioMs = 500, fineMs = 900, testo = "c"),
            unSegmentoIniziale(voceIndice = 5, inizioMs = 2_000, fineMs = 2_500, testo = "e"),
            unSegmentoIniziale(voceIndice = 5, inizioMs = 0, fineMs = 400, testo = "a"),
        )

        val creato = Trascritto.crea(registrazioneId, DURATA_TRASCRITTO_MS, turni).atteso()

        val t = creato.aggregato
        assertEquals(TrascrittoCreato(registrazioneId), creato.evento)
        assertEquals(registrazioneId, t.registrazioneId)
        // diarizer 5 speaks first → Voce 1; 0 and 2 tie at 500 ms → the lower voceIndice (0) is Voce 2.
        // At 2000 ms Voce 1 (voceIndice 5) and Voce 2 (voceIndice 0) tie: ordered by VoceId, not voceIndice.
        // At 0 ms Voce 1 has two turns: ordered by fine, not by input order.
        assertEquals(
            listOf(
                Segmento(SegmentoId(1), VoceId(1), IntervalloMs(0, 400), "a"),
                Segmento(SegmentoId(2), VoceId(1), IntervalloMs(0, 1_000), "b"),
                Segmento(SegmentoId(3), VoceId(2), IntervalloMs(500, 900), "c"),
                Segmento(SegmentoId(4), VoceId(3), IntervalloMs(500, 1_500), "d"),
                Segmento(SegmentoId(5), VoceId(1), IntervalloMs(2_000, 2_500), "e"),
                Segmento(SegmentoId(6), VoceId(2), IntervalloMs(2_000, 3_000), "f"),
            ),
            t.segmenti,
        )
        assertEquals(listOf(1, 2, 3), t.voci.map { it.id.numero })
        assertEquals(4, t.prossimaVoce)
        assertEquals(7, t.prossimoSegmento)
    }

    @Test
    fun `AC-21 crea con zero segmenti restituisce NessunParlatoRilevato`() {
        Trascritto.crea(registrazioneId, DURATA_TRASCRITTO_MS, emptyList())
            .erroreAtteso<ErroreTrascrizione.NessunParlatoRilevato>()
    }

    // --- INV-6 ------------------------------------------------------------------------------------

    @Test
    fun `INV-6 riassegnare l ultimo Segmento di una Voce la rimuove e unire rimuove B e nessuna Voce resta vuota`() {
        val t = unTrascritto(voci = 3, segmentiPerVoce = 1) // V1:S1 V2:S2 V3:S3

        val riassegnato = t.riassegna(SegmentoId(3), VoceId(1)).atteso()
        assertTrue(riassegnato.daRimossa)
        assertEquals(listOf(1, 2), t.voci.map { it.id.numero })

        t.unisci(sopravvive = VoceId(1), rimossa = VoceId(2)).atteso()
        assertEquals(listOf(VoceId(1)), t.voci.map { it.id })

        assertTrue(t.voci.all { it.segmenti.isNotEmpty() })
        val voci = t.voci.map { it.id }.toSet()
        assertTrue(t.segmenti.all { it.voceId in voci })
        assertEquals(t.segmenti.toSet(), t.voci.flatMap { it.segmenti }.toSet())
        assertEquals(t.segmenti.size, t.voci.sumOf { it.segmenti.size })
    }

    // --- INV-7 ------------------------------------------------------------------------------------

    @Test
    fun `INV-7 Segmenti di una Voce ordinati per inizio poi segmentoId e sovrapposizioni mai bloccanti`() {
        val turni = listOf(
            unSegmentoIniziale(voceIndice = 0, inizioMs = 0, fineMs = 3_000), // S1 V1
            unSegmentoIniziale(voceIndice = 1, inizioMs = 0, fineMs = 2_000), // S2 V2, overlaps S1
            unSegmentoIniziale(voceIndice = 1, inizioMs = 1_000, fineMs = 4_000), // S4 V2, overlaps S2
            unSegmentoIniziale(voceIndice = 0, inizioMs = 500, fineMs = 2_500), // S3 V1, overlaps S1
        )
        val t = Trascritto.crea(registrazioneId, DURATA_TRASCRITTO_MS, turni).atteso().aggregato
        assertEquals(listOf(1, 3), t.voce(1))
        assertEquals(listOf(2, 4), t.voce(2))

        // S2 (inizio 0) joins V1 where S1 also starts at 0: the tie is broken by segmentoId
        t.riassegna(SegmentoId(2), VoceId(1)).atteso()
        assertEquals(listOf(1, 2, 3), t.voce(1))

        // everything overlapping ends up on one Voce: never blocked
        t.unisci(sopravvive = VoceId(2), rimossa = VoceId(1)).atteso()
        assertEquals(listOf(1, 2, 3, 4), t.voce(2))
        t.dividi(VoceId(2), setOf(SegmentoId(1), SegmentoId(4))).atteso()
        assertEquals(listOf(2, 3), t.voce(2))
        assertEquals(listOf(1, 4), t.voce(3))
    }

    @Test
    fun `INV-7 crea rifiuta un Segmento che finisce oltre la durata e accetta fine uguale alla durata`() {
        val oltre = listOf(unSegmentoIniziale(voceIndice = 0, inizioMs = 9_000, fineMs = 10_001))
        val errore = Trascritto.crea(registrazioneId, 10_000, oltre)
            .erroreAtteso<ErroreTrascrizione.SegmentoOltreLaDurata>()
        assertEquals(ErroreTrascrizione.SegmentoOltreLaDurata(IntervalloMs(9_000, 10_001), 10_000), errore)

        val alLimite = listOf(unSegmentoIniziale(voceIndice = 0, inizioMs = 9_000, fineMs = 10_000))
        Trascritto.crea(registrazioneId, 10_000, alLimite).atteso()
    }

    // --- INV-8 ------------------------------------------------------------------------------------

    @Test
    fun `INV-8 dopo ogni Revisione e confermaSegmento id intervallo e testo dei Segmenti sono identici`() {
        val t = unTrascritto(voci = 3, segmentiPerVoce = 3)
        val prima = t.contenuto()

        t.unisci(sopravvive = VoceId(1), rimossa = VoceId(2)).atteso()
        assertEquals(prima, t.contenuto())
        t.dividi(VoceId(1), setOf(SegmentoId(2), SegmentoId(4))).atteso()
        assertEquals(prima, t.contenuto())
        t.riassegna(SegmentoId(3), null).atteso()
        assertEquals(prima, t.contenuto())
        t.riassegna(SegmentoId(5), VoceId(3)).atteso()
        assertEquals(prima, t.contenuto())
        val s6 = t.segmenti.single { it.id == SegmentoId(6) }
        t.riassegnaInBlocco(listOf(SpostamentoSegmento(s6.id, s6.voceId, VoceId(1), s6.intervallo))).atteso()
        assertEquals(prima, t.contenuto())
        t.confermaSegmento(SegmentoId(6), true).atteso()
        t.confermaSegmento(SegmentoId(3), false).atteso()
        assertEquals(prima, t.contenuto())
        assertEquals(9, t.segmenti.size)

        // a rejected Revisione changes nothing either, not even the Voce of a Segmento
        val dopo = t.segmenti
        t.unisci(VoceId(1), VoceId(1)).erroreAtteso<ErroreTrascrizione.UnioneNonAmmessa>()
        assertEquals(dopo, t.segmenti)
    }

    // --- INV-9 ------------------------------------------------------------------------------------

    @Test
    fun `INV-9 unire A con A e rifiutato e unire A e B sposta tutti i Segmenti di B su A e rimuove B`() {
        val t = unTrascritto(voci = 3, segmentiPerVoce = 2) // V1:S1,S4 V2:S2,S5 V3:S3,S6

        assertEquals(
            ErroreTrascrizione.UnioneNonAmmessa(VoceId(2), VoceId(2)),
            t.unisci(VoceId(2), VoceId(2)).erroreAtteso<ErroreTrascrizione.UnioneNonAmmessa>(),
        )
        assertEquals(
            ErroreTrascrizione.VoceNonTrovata(VoceId(9)),
            t.unisci(VoceId(2), VoceId(9)).erroreAtteso<ErroreTrascrizione.VoceNonTrovata>(),
        )
        assertEquals(
            ErroreTrascrizione.VoceNonTrovata(VoceId(9)),
            t.unisci(VoceId(9), VoceId(2)).erroreAtteso<ErroreTrascrizione.VoceNonTrovata>(),
        )
        assertEquals(listOf(1, 2, 3), t.voci.map { it.id.numero })

        val evento = t.unisci(sopravvive = VoceId(3), rimossa = VoceId(1)).atteso()

        assertEquals(VociUnite(registrazioneId, sopravvissuta = VoceId(3), rimossa = VoceId(1)), evento)
        assertEquals(listOf(2, 3), t.voci.map { it.id.numero })
        assertEquals(listOf(1, 3, 4, 6), t.voce(3))
        assertEquals(listOf(2, 5), t.voce(2))
        assertEquals(
            ErroreTrascrizione.VoceNonTrovata(VoceId(1)),
            t.unisci(VoceId(3), VoceId(1)).erroreAtteso<ErroreTrascrizione.VoceNonTrovata>(),
        )
    }

    // --- INV-10 -----------------------------------------------------------------------------------

    @Test
    fun `INV-10 dividere con S vuoto o con tutti i Segmenti di A e rifiutato e con S valido nasce la Voce n+1`() {
        val t = unTrascritto(voci = 2, segmentiPerVoce = 3) // V1:S1,S3,S5 V2:S2,S4,S6

        t.dividi(VoceId(1), emptySet()).erroreAtteso<ErroreTrascrizione.DivisioneNonAmmessa>()
        t.dividi(VoceId(1), setOf(SegmentoId(1), SegmentoId(3), SegmentoId(5)))
            .erroreAtteso<ErroreTrascrizione.DivisioneNonAmmessa>()
        // a Segmento of another Voce, or an unknown one, is not a subset of A's Segmenti
        t.dividi(VoceId(1), setOf(SegmentoId(1), SegmentoId(2)))
            .erroreAtteso<ErroreTrascrizione.DivisioneNonAmmessa>()
        t.dividi(VoceId(1), setOf(SegmentoId(99))).erroreAtteso<ErroreTrascrizione.DivisioneNonAmmessa>()
        t.dividi(VoceId(7), setOf(SegmentoId(1))).erroreAtteso<ErroreTrascrizione.VoceNonTrovata>()
        assertEquals(listOf(1, 3, 5), t.voce(1))

        val evento = t.dividi(VoceId(1), setOf(SegmentoId(5), SegmentoId(3))).atteso()

        assertEquals(
            VoceDivisa(registrazioneId, origine = VoceId(1), nuova = VoceId(3), listOf(SegmentoId(3), SegmentoId(5))),
            evento,
        )
        assertEquals(listOf(1), t.voce(1))
        assertEquals(listOf(3, 5), t.voce(3))
        assertEquals(listOf(2, 4, 6), t.voce(2))
        assertEquals(4, t.prossimaVoce)
    }

    // --- INV-11 -----------------------------------------------------------------------------------

    @Test
    fun `INV-11 riassegnare verso la stessa Voce o una inesistente e rifiutato e verso null crea una Voce nuova`() {
        val t = unTrascritto(voci = 2, segmentiPerVoce = 2) // V1:S1,S3 V2:S2,S4

        assertEquals(
            ErroreTrascrizione.RiassegnazioneNonAmmessa(SegmentoId(1), VoceId(1)),
            t.riassegna(SegmentoId(1), VoceId(1)).erroreAtteso<ErroreTrascrizione.RiassegnazioneNonAmmessa>(),
        )
        t.riassegna(SegmentoId(1), VoceId(8)).erroreAtteso<ErroreTrascrizione.VoceNonTrovata>()
        assertEquals(
            ErroreTrascrizione.SegmentoNonTrovato(SegmentoId(99)),
            t.riassegna(SegmentoId(99), VoceId(2)).erroreAtteso<ErroreTrascrizione.SegmentoNonTrovato>(),
        )

        val versoNuova = t.riassegna(SegmentoId(3), null).atteso()
        assertEquals(
            SegmentoRiassegnato(
                registrazioneId,
                SegmentoId(3),
                da = VoceId(1),
                a = VoceId(3),
                daRimossa = false,
                aNuova = true,
            ),
            versoNuova,
        )
        assertEquals(listOf(1), t.voce(1))
        assertEquals(listOf(3), t.voce(3))

        val versoEsistente = t.riassegna(SegmentoId(1), VoceId(2)).atteso()
        assertEquals(
            SegmentoRiassegnato(
                registrazioneId,
                SegmentoId(1),
                da = VoceId(1),
                a = VoceId(2),
                daRimossa = true,
                aNuova = false,
            ),
            versoEsistente,
        )
        assertEquals(listOf(2, 3), t.voci.map { it.id.numero })
        assertEquals(listOf(1, 2, 4), t.voce(2))
        t.riassegna(SegmentoId(2), VoceId(1)).erroreAtteso<ErroreTrascrizione.VoceNonTrovata>()
    }

    @Test
    fun `INV-11 riassegnare verso una Voce nuova l unico Segmento della sua Voce e rifiutato e non cambia nulla`() {
        val t = unTrascritto(voci = 2, segmentiPerVoce = 1) // V1:S1 V2:S2
        val segmenti = t.segmenti
        val voci = t.voci
        val prossimaVoce = t.prossimaVoce

        assertEquals(
            ErroreTrascrizione.RiassegnazioneNonAmmessa(SegmentoId(2), null),
            t.riassegna(SegmentoId(2), null).erroreAtteso<ErroreTrascrizione.RiassegnazioneNonAmmessa>(),
        )

        assertEquals(segmenti, t.segmenti)
        assertEquals(voci, t.voci)
        assertEquals(prossimaVoce, t.prossimaVoce)
        // towards an EXISTING other Voce the only Segmento may still leave, removing its Voce
        assertTrue(t.riassegna(SegmentoId(2), VoceId(1)).atteso().daRimossa)
    }

    // --- INV-12 -----------------------------------------------------------------------------------

    @Test
    fun `INV-12 dopo unire e dividere il nuovo voceId e sempre maggiore di tutti quelli mai usati anche rimossi`() {
        val t = unTrascritto(voci = 3, segmentiPerVoce = 2) // V1:S1,S4 V2:S2,S5 V3:S3,S6
        val maiUsati = t.voci.map { it.id.numero }.toMutableSet()

        t.unisci(sopravvive = VoceId(1), rimossa = VoceId(3)).atteso() // V3 removed: its number is not freed
        assertFalse(t.voci.any { it.id == VoceId(3) })

        val divisa = t.dividi(VoceId(1), setOf(SegmentoId(3))).atteso().nuova
        assertTrue(divisa.numero > maiUsati.max())
        assertEquals(VoceId(4), divisa)
        maiUsati += divisa.numero

        t.unisci(sopravvive = VoceId(1), rimossa = divisa).atteso()
        val nuova = t.riassegna(SegmentoId(2), null).atteso()
        assertTrue(nuova.a.numero > maiUsati.max())
        assertEquals(VoceId(5), nuova.a)

        // surviving Voci keep their number: never renumbered
        assertEquals(listOf(1, 2, 5), t.voci.map { it.id.numero })
        assertEquals(6, t.prossimaVoce)
    }

    // --- read accessors ---------------------------------------------------------------------------

    @Test
    fun `le letture sono copie e non espongono lo stato interno`() {
        val t = unTrascritto(voci = 2, segmentiPerVoce = 1)
        val voci = t.voci
        val segmenti = t.segmenti

        t.unisci(VoceId(1), VoceId(2)).atteso()

        assertEquals(2, voci.size)
        assertEquals(VoceId(2), segmenti.single { it.id == SegmentoId(2) }.voceId)
    }

    private fun Trascritto.voce(numero: Int): List<Int> =
        voci.single { it.id == VoceId(numero) }.segmenti.map { it.id.numero }

    private fun Trascritto.contenuto(): Set<Triple<SegmentoId, IntervalloMs, String>> =
        segmenti.map { Triple(it.id, it.intervallo, it.testo) }.toSet()
}
