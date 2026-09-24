package snastro.parlanti.applicazione.letture

import snastro.kernel.CampioniAudio
import snastro.kernel.IntervalloMs
import snastro.kernel.ParlanteId
import snastro.kernel.ProgettoId
import snastro.kernel.RegistrazioneId
import snastro.kernel.SegmentoId
import snastro.kernel.UnitaDiLavoroFinta
import snastro.kernel.VoceId
import snastro.kernel.VoceRef
import snastro.kernel.atteso
import snastro.kernel.erroreAtteso
import snastro.parlanti.applicazione.porte.AttribuzioneRepositoryFinta
import snastro.parlanti.applicazione.porte.ClassificatoreSomiglianzaFinta
import snastro.parlanti.applicazione.porte.Classificazione
import snastro.parlanti.applicazione.porte.DecodificatoreAudio
import snastro.parlanti.applicazione.porte.DecodificatoreAudioFinta
import snastro.parlanti.applicazione.porte.EstrattoreImpronta
import snastro.parlanti.applicazione.porte.EstrattoreImprontaFinta
import snastro.parlanti.applicazione.porte.LettoreVoci
import snastro.parlanti.applicazione.porte.LettoreVociFinta
import snastro.parlanti.applicazione.porte.ParlanteRepositoryFinta
import snastro.parlanti.applicazione.porte.SegmentoDiVoce
import snastro.parlanti.dominio.Attribuzione
import snastro.parlanti.dominio.ErroreParlanti
import snastro.parlanti.dominio.Impronta
import snastro.parlanti.dominio.Nome
import snastro.parlanti.dominio.Parlante
import snastro.parlanti.dominio.SorgenteImpronta
import snastro.parlanti.dominio.TipoParlante
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * [PianoRiassegnazioneQuery] against the ports' fakes (D1): [INV-27], AC-501..510, AC-543, AC-544
 * (ADR 0019 §4 + Amendment 2026-09-24 (b).1). [ClassificatoreSomiglianzaFinta] scripts outcomes BY
 * POSITION in the `frasi` list `calcola` builds for it — each fixture below derives that position
 * from the segmenti's (inizio, segmentoId) order, documented per test.
 */
class PianoRiassegnazioneTest {

    @Test
    fun `senza Trascritto restituisce TrascrittoNonTrovato`() {
        val ambiente = Ambiente(lettoreVoci = LettoreVociFinta())

        val errore = ambiente.api.calcola(REGISTRAZIONE) { _, _ -> }.erroreAtteso<ErroreParlanti.TrascrittoNonTrovato>()

        assertEquals(REGISTRAZIONE, errore.registrazioneId)
    }

    @Test
    fun `INV-27 un Segmento si sposta solo se non confermato, almeno 1s, non congelato, Sicura, non gia sul target`() {
        val p = parlante("p", "Anna")
        val q = parlante("q", "Marco")
        val r = parlante("r", "Luca") // eliminato: la sua Voce e congelata
        val ambiente = Ambiente(
            segmenti = listOf(
                seg(1, 1, 0, 2_000, confermato = true), // riferimento di P
                seg(2, 2, 2_000, 4_000, confermato = true), // riferimento di Q
                seg(4, 1, 4_000, 6_000, confermato = true), // (a) confermato: mai spostato
                seg(5, 1, 6_000, 6_500), // (b) < 1s: incerta, non estratto
                seg(6, 3, 6_500, 8_500), // (c) su Voce congelata (R eliminato): mai estratto ne spostato
                seg(7, 1, 8_500, 10_500), // (d) Sicura(P) ma gia sulla Voce target di P (1): nessuno spostamento
                seg(8, 4, 10_500, 12_500), // Sicura(P), su Voce non attribuita: SI sposta
            ),
            classificatore = ClassificatoreSomiglianzaFinta(
                // movibiliValidi ordinati per inizio: seg7 (indice 0), seg8 (indice 1) — seg5 e troppo corto.
                programmate = mapOf(0 to Classificazione.Sicura(p.id), 1 to Classificazione.Sicura(p.id)),
            ),
        )
        attribuisci(ambiente, 1, p)
        attribuisci(ambiente, 2, q)
        attribuisci(ambiente, 3, r)
        r.elimina().atteso()
        ambiente.parlanti.salva(r).atteso()

        val piano = ambiente.api.calcola(REGISTRAZIONE) { _, _ -> }.atteso()

        assertEquals(
            listOf(SpostamentoProposto(SegmentoId(8), VoceId(4), VoceId(1), IntervalloMs(10_500, 12_500))),
            piano.spostamenti,
        )
        assertEquals(1, piano.incerte, "solo il Segmento 5 (< 1s)")
    }

    @Test
    fun `AC-501 con una frase confermata di almeno 1s le riferimenti sono solo quella, le altre sono movibili`() {
        val p = parlante("p", "Anna")
        val seconda = parlante("seconda", "Marco") // secondo riferimento richiesto da INV-27 (>= 2)
        val classificatore = ClassificatoreSomiglianzaFinta()
        val ambiente = Ambiente(
            segmenti = listOf(seg(9, 9, 0, 2_000, confermato = true)) + // riferimento della "seconda"
                listOf(seg(1, 1, 10_000, 12_000, confermato = true)) + // il Segmento confermato di P (2s)
                (2..6).map { seg(it, 1, 10_000L * it, 10_000L * it + 1_200) }, // 5 Segmenti NON confermati >= 1s
            classificatore = classificatore,
        )
        attribuisci(ambiente, 1, p)
        attribuisci(ambiente, 9, seconda)

        ambiente.api.calcola(REGISTRAZIONE) { _, _ -> }.atteso()

        assertEquals(
            listOf(seg(1, 1, 10_000, 12_000, confermato = true).impronta()),
            classificatore.chiamate.single().getValue(p.id),
        )
    }

    @Test
    fun `AC-501 senza frase confermata le riferimenti sono i Segmenti di almeno 1s, i piu corti sono incerte`() {
        val p = parlante("p", "Anna")
        val seconda = parlante("seconda", "Marco")
        val classificatore = ClassificatoreSomiglianzaFinta()
        val ambiente = Ambiente(
            segmenti = listOf(seg(9, 9, 0, 2_000, confermato = true)) +
                listOf(
                    seg(1, 1, 10_000, 13_000), // 3s, non confermato
                    seg(2, 1, 13_000, 15_000), // 2s, non confermato
                    seg(3, 1, 15_000, 15_600), // 0.6s: incerta, non riferimento
                ),
            classificatore = classificatore,
        )
        attribuisci(ambiente, 1, p)
        attribuisci(ambiente, 9, seconda)

        val piano = ambiente.api.calcola(REGISTRAZIONE) { _, _ -> }.atteso()

        assertEquals(
            setOf(seg(1, 1, 10_000, 13_000).impronta(), seg(2, 1, 13_000, 15_000).impronta()),
            classificatore.chiamate.single().getValue(p.id).toSet(),
        )
        assertTrue(piano.incerte >= 1, "il Segmento di 0.6s conta come incerta")
    }

    @Test
    fun `AC-501 l unico Segmento confermato sotto 1s non blocca il fallback a intera Voce`() {
        val p = parlante("p", "Anna")
        val seconda = parlante("seconda", "Marco")
        val classificatore = ClassificatoreSomiglianzaFinta()
        val ambiente = Ambiente(
            segmenti = listOf(seg(9, 9, 0, 2_000, confermato = true)) +
                listOf(
                    seg(1, 1, 10_000, 10_800, confermato = true), // confermato ma < 1s: non conta come riferimento
                    seg(2, 1, 11_000, 13_000), // >= 1s, non confermato: e il riferimento di fallback
                ),
            classificatore = classificatore,
        )
        attribuisci(ambiente, 1, p)
        attribuisci(ambiente, 9, seconda)

        ambiente.api.calcola(REGISTRAZIONE) { _, _ -> }.atteso()

        assertEquals(listOf(seg(2, 1, 11_000, 13_000).impronta()), classificatore.chiamate.single().getValue(p.id))
    }

    @Test
    fun `AC-501 un Parlante su due Voci nessuna confermata usa i Segmenti di almeno 1s di entrambe`() {
        val p = parlante("p", "Anna")
        val seconda = parlante("seconda", "Marco")
        val classificatore = ClassificatoreSomiglianzaFinta()
        val ambiente = Ambiente(
            segmenti = listOf(seg(9, 9, 0, 2_000, confermato = true)) +
                listOf(seg(1, 2, 10_000, 12_000), seg(2, 4, 12_000, 14_000)),
            classificatore = classificatore,
        )
        attribuisci(ambiente, 2, p)
        attribuisci(ambiente, 4, p)
        attribuisci(ambiente, 9, seconda)

        ambiente.api.calcola(REGISTRAZIONE) { _, _ -> }.atteso()

        assertEquals(
            setOf(seg(1, 2, 10_000, 12_000).impronta(), seg(2, 4, 12_000, 14_000).impronta()),
            classificatore.chiamate.single().getValue(p.id).toSet(),
        )
    }

    @Test
    fun `AC-501 un Parlante eliminato non ha mai riferimenti neanche con un Segmento confermato`() {
        val eliminato = parlante("elim", "Anna")
        val p = parlante("p", "Marco")
        val seconda = parlante("seconda", "Luca")
        val ambiente = Ambiente(
            segmenti = listOf(
                seg(1, 1, 0, 2_000, confermato = true), // sulla Voce dell'eliminato
                seg(9, 9, 2_000, 4_000, confermato = true),
                seg(10, 10, 4_000, 6_000, confermato = true),
            ),
        )
        attribuisci(ambiente, 1, eliminato)
        eliminato.elimina().atteso()
        ambiente.parlanti.salva(eliminato).atteso()
        attribuisci(ambiente, 9, p)
        attribuisci(ambiente, 10, seconda)

        // Se l'eliminato contasse come riferimento la Voce 1 non sarebbe congelata: verifichiamo che lo sia
        // controllando che il piano non estragga mai il suo Segmento (nessuna chiamata lo cita).
        ambiente.api.calcola(REGISTRAZIONE) { _, _ -> }.atteso()

        assertEquals(
            2,
            ambiente.parlanti.delProgetto(PROGETTO).count { it.attivo },
            "solo p e seconda sono riferimenti attivi",
        )
        assertEquals(
            setOf(p.id, seconda.id),
            ambiente.classificatore.chiamate.single().keys,
            "l'eliminato non e mai una chiave del riferimenti map, nemmeno con un Segmento confermato sulla sua Voce",
        )
    }

    @Test
    fun `AC-501 un Segmento confermato su una Voce non attribuita non e riferimento di nessuno`() {
        val p = parlante("p", "Anna")
        val seconda = parlante("seconda", "Marco")
        val ambiente = Ambiente(
            segmenti = listOf(
                seg(1, 1, 0, 2_000, confermato = true),
                seg(9, 9, 2_000, 4_000, confermato = true),
                seg(5, 5, 4_000, 6_000, confermato = true), // Voce 5 non attribuita
            ),
        )
        attribuisci(ambiente, 1, p)
        attribuisci(ambiente, 9, seconda)

        // Nessun errore: p e seconda bastano. Il Segmento 5 (Voce non attribuita) non e riferimento di nessuno.
        ambiente.api.calcola(REGISTRAZIONE) { _, _ -> }.atteso()

        assertEquals(setOf(p.id, seconda.id), ambiente.classificatore.chiamate.single().keys)
    }

    @Test
    fun `AC-502 un solo Parlante di riferimento da RiferimentiInsufficienti senza alcuna estrazione`() {
        lateinit var decodificatoreSpia: DecodificatoreAudioCheConta
        lateinit var estrattoreSpia: EstrattoreImprontaCheConta
        val p = parlante("p", "Anna")
        val ambiente = Ambiente(
            segmenti = listOf(seg(1, 1, 0, 2_000, confermato = true)),
            decodificatore = { uow ->
                DecodificatoreAudioCheConta(DecodificatoreAudioFinta(uow)).also { decodificatoreSpia = it }
            },
            estrattore = { uow ->
                EstrattoreImprontaCheConta(EstrattoreImprontaFinta(unitaDiLavoro = uow)).also { estrattoreSpia = it }
            },
        )
        attribuisci(ambiente, 1, p)

        val errore = ambiente.api.calcola(REGISTRAZIONE) { _, _ -> }
            .erroreAtteso<ErroreParlanti.RiferimentiInsufficienti>()

        assertEquals(REGISTRAZIONE, errore.registrazioneId)
        assertEquals(0, decodificatoreSpia.chiamate.size)
        assertEquals(0, estrattoreSpia.chiamate)
    }

    @Test
    fun `AC-502 due Parlanti senza frase confermata ciascuno con un Segmento di almeno 1s non danno errore`() {
        val p = parlante("p", "Anna")
        val q = parlante("q", "Marco")
        val ambiente = Ambiente(
            segmenti = listOf(seg(1, 1, 0, 1_500), seg(2, 2, 1_500, 3_000)),
        )
        attribuisci(ambiente, 1, p)
        attribuisci(ambiente, 2, q)

        val piano = ambiente.api.calcola(REGISTRAZIONE) { _, _ -> }.atteso()

        assertEquals(0, piano.spostamenti.size)
    }

    @Test
    fun `AC-503 le Voci congelate sono attribuite a un eliminato o ad un attivo senza riferimento, mai estratte`() {
        val p = parlante("p", "Anna")
        val q = parlante("q", "Marco")
        val r = parlante("r", "Elena") // eliminato
        val s = parlante("s", "Luca") // attivo senza riferimento (solo < 1s)
        val t = parlante("t", "Giulia") // attivo, senza confermate, intera Voce
        lateinit var decodificatoreSpia: DecodificatoreAudioCheConta
        val ambiente = Ambiente(
            segmenti = listOf(
                seg(1, 1, 0, 2_000, confermato = true), // P: riferimento confermato
                seg(2, 2, 2_000, 4_000, confermato = true), // Q: riferimento confermato
                seg(3, 3, 4_000, 6_000), // sulla Voce di R (eliminato): congelata
                seg(4, 5, 6_000, 6_400), // sulla Voce di S (< 1s, nessun riferimento): congelata
                seg(5, 6, 6_400, 9_400), // T: riferimento intera Voce (3s) e candidato
                seg(6, 6, 9_400, 11_400), // T: riferimento intera Voce (2s) e candidato, classificato Sicura(Q)
            ),
            decodificatore = { uow ->
                DecodificatoreAudioCheConta(DecodificatoreAudioFinta(uow)).also { decodificatoreSpia = it }
            },
            classificatore = ClassificatoreSomiglianzaFinta(
                // movibiliValidi ordinati per inizio: seg5 (indice 0, Voce 6), seg6 (indice 1, Voce 6).
                programmate = mapOf(1 to Classificazione.Sicura(q.id)),
            ),
        )
        attribuisci(ambiente, 1, p)
        attribuisci(ambiente, 2, q)
        attribuisci(ambiente, 3, r)
        r.elimina().atteso()
        ambiente.parlanti.salva(r).atteso()
        attribuisci(ambiente, 5, s)
        attribuisci(ambiente, 6, t)

        val piano = ambiente.api.calcola(REGISTRAZIONE) { _, _ -> }.atteso()

        assertEquals(
            listOf(SpostamentoProposto(SegmentoId(6), VoceId(6), VoceId(2), IntervalloMs(9_400, 11_400))),
            piano.spostamenti,
            "una Voce non congelata (T, intera Voce) produce un candidato; nessuna Voce congelata appare come da/a",
        )
        val decodificati = decodificatoreSpia.chiamate.flatMap { it.second }.toSet()
        assertTrue(
            IntervalloMs(4_000, 6_000) !in decodificati && IntervalloMs(6_000, 6_400) !in decodificati,
            "le Voci congelate non sono mai decodificate/estratte: $decodificati",
        )
    }

    @Test
    fun `AC-504 un Segmento movibile sotto 1s non e estratto e conta come incerta`() {
        val p = parlante("p", "Anna")
        val q = parlante("q", "Marco")
        lateinit var estrattoreSpia: EstrattoreImprontaCheConta
        val ambiente = Ambiente(
            segmenti = listOf(
                seg(1, 1, 0, 2_000, confermato = true),
                seg(2, 2, 2_000, 4_000, confermato = true),
                seg(3, 3, 4_000, 4_900), // 900ms, su Voce non attribuita
            ),
            estrattore = { uow ->
                EstrattoreImprontaCheConta(EstrattoreImprontaFinta(unitaDiLavoro = uow)).also { estrattoreSpia = it }
            },
        )
        attribuisci(ambiente, 1, p)
        attribuisci(ambiente, 2, q)

        val piano = ambiente.api.calcola(REGISTRAZIONE) { _, _ -> }.atteso()

        assertEquals(0, piano.spostamenti.size)
        assertEquals(1, piano.incerte)
        assertEquals(2, estrattoreSpia.chiamate, "solo i due riferimenti confermati sono estratti")
    }

    @Test
    fun `AC-505 il target e la Voce piu bassa tra quelle del Parlante, gia li niente, altrove uno spostamento`() {
        val p = parlante("p", "Anna")
        val q = parlante("q", "Marco")
        val ambiente = Ambiente(
            segmenti = listOf(
                seg(1, 2, 0, 2_000, confermato = true), // P: riferimento su Voce 2 (il target, la piu bassa)
                seg(2, 3, 2_000, 4_000, confermato = true), // Q: riferimento
                seg(3, 2, 4_000, 6_000), // gia sul target di P: nessuno spostamento se Sicura(P)
                seg(4, 4, 6_000, 8_000), // su Voce 4 (anch'essa di P): si sposta verso il target 2
            ),
            classificatore = ClassificatoreSomiglianzaFinta(
                // movibiliValidi ordinati per inizio: seg3 (indice 0), seg4 (indice 1).
                programmate = mapOf(0 to Classificazione.Sicura(p.id), 1 to Classificazione.Sicura(p.id)),
            ),
        )
        attribuisci(ambiente, 2, p)
        attribuisci(ambiente, 4, p)
        attribuisci(ambiente, 3, q)

        val piano = ambiente.api.calcola(REGISTRAZIONE) { _, _ -> }.atteso()

        assertEquals(
            listOf(SpostamentoProposto(SegmentoId(4), VoceId(4), VoceId(2), IntervalloMs(6_000, 8_000))),
            piano.spostamenti,
        )
    }

    @Test
    fun `AC-506 un Segmento Incerta non produce spostamento e conta in incerte`() {
        val p = parlante("p", "Anna")
        val q = parlante("q", "Marco")
        val ambiente = Ambiente(
            segmenti = listOf(
                seg(1, 1, 0, 2_000, confermato = true),
                seg(2, 2, 2_000, 4_000, confermato = true),
                seg(3, 3, 4_000, 6_000),
            ),
            // seg3 (unico movibile, indice 0) non e programmato: default Incerta.
        )
        attribuisci(ambiente, 1, p)
        attribuisci(ambiente, 2, q)

        val piano = ambiente.api.calcola(REGISTRAZIONE) { _, _ -> }.atteso()

        assertEquals(0, piano.spostamenti.size)
        assertEquals(1, piano.incerte)
    }

    @Test
    fun `AC-507 un Segmento riferimento e candidato e estratto una sola volta, progresso chiamato per estrazione`() {
        val p = parlante("p", "Anna") // riferimento confermato
        val q = parlante("q", "Marco") // riferimento intera Voce: il suo unico Segmento e anche candidato
        lateinit var estrattoreSpia: EstrattoreImprontaCheConta
        val ambiente = Ambiente(
            segmenti = listOf(
                seg(1, 1, 0, 2_000, confermato = true),
                seg(2, 2, 2_000, 4_000), // riferimento intera Voce di Q E candidato movibile
            ),
            estrattore = { uow ->
                EstrattoreImprontaCheConta(EstrattoreImprontaFinta(unitaDiLavoro = uow)).also { estrattoreSpia = it }
            },
        )
        attribuisci(ambiente, 1, p)
        attribuisci(ambiente, 2, q)
        val progressi = mutableListOf<Pair<Int, Int>>()

        ambiente.api.calcola(REGISTRAZIONE) { fatti, totale -> progressi += fatti to totale }.atteso()

        assertEquals(2, estrattoreSpia.chiamate, "seg1 e seg2, seg2 una volta sola pur essendo riferimento E candidato")
        assertEquals(listOf(1 to 2, 2 to 2), progressi)
    }

    @Test
    fun `AC-508 un InterruptedException durante l estrazione propaga e il calcolo successivo riparte da zero`() {
        val p = parlante("p", "Anna")
        val q = parlante("q", "Marco")
        var chiamate = 0
        val ambiente = Ambiente(
            segmenti = listOf(seg(1, 1, 0, 2_000, confermato = true), seg(2, 2, 2_000, 4_000, confermato = true)),
            estrattore = {
                object : EstrattoreImpronta {
                    override val modello: String = "finto"

                    override fun estrai(c: CampioniAudio): Impronta {
                        chiamate++
                        if (chiamate == 2) throw InterruptedException("annullato")
                        return Impronta(floatArrayOf(chiamate.toFloat()))
                    }
                }
            },
        )
        attribuisci(ambiente, 1, p)
        attribuisci(ambiente, 2, q)

        assertFailsWith<InterruptedException> { ambiente.api.calcola(REGISTRAZIONE) { _, _ -> } }
        assertEquals(2, chiamate)

        ambiente.api.calcola(REGISTRAZIONE) { _, _ -> }.atteso()
        assertEquals(4, chiamate, "il calcolo successivo riparte dal primo Segmento")
    }

    @Test
    fun `AC-509 calcola non scrive nulla, due calcola consecutivi raddoppiano le estrazioni`() {
        val p = parlante("p", "Anna")
        val q = parlante("q", "Marco")
        lateinit var estrattoreSpia: EstrattoreImprontaCheConta
        val ambiente = Ambiente(
            segmenti = listOf(seg(1, 1, 0, 2_000, confermato = true), seg(2, 2, 2_000, 4_000, confermato = true)),
            estrattore = { uow ->
                EstrattoreImprontaCheConta(EstrattoreImprontaFinta(unitaDiLavoro = uow)).also { estrattoreSpia = it }
            },
        )
        attribuisci(ambiente, 1, p)
        attribuisci(ambiente, 2, q)
        val parlantiPrima = ambiente.parlanti.delProgetto(PROGETTO).size
        val attribuzioniPrima = ambiente.attribuzioni.diRegistrazione(REGISTRAZIONE).size

        ambiente.api.calcola(REGISTRAZIONE) { _, _ -> }.atteso()
        assertEquals(2, estrattoreSpia.chiamate)
        ambiente.api.calcola(REGISTRAZIONE) { _, _ -> }.atteso()
        assertEquals(4, estrattoreSpia.chiamate, "il secondo calcola estrae di nuovo, nessuna impronta e trattenuta")

        assertEquals(parlantiPrima, ambiente.parlanti.delProgetto(PROGETTO).size)
        assertEquals(attribuzioniPrima, ambiente.attribuzioni.diRegistrazione(REGISTRAZIONE).size)
    }

    @Test
    fun `AC-510 con riferimenti tutti a frasi confermate applicare il piano e ricalcolare da spostamenti vuoti`() {
        val p = parlante("p", "Anna")
        val q = parlante("q", "Marco")
        val ambiente = Ambiente(
            segmenti = listOf(
                seg(1, 1, 0, 2_000, confermato = true),
                seg(2, 2, 2_000, 4_000, confermato = true),
                seg(3, 3, 4_000, 6_000), // su Voce non attribuita: si sposta su P
            ),
            classificatore = ClassificatoreSomiglianzaFinta(mapOf(0 to Classificazione.Sicura(p.id))),
        )
        attribuisci(ambiente, 1, p)
        attribuisci(ambiente, 2, q)

        val primo = ambiente.api.calcola(REGISTRAZIONE) { _, _ -> }.atteso()
        assertEquals(1, primo.spostamenti.size)
        ambiente.applica(primo.spostamenti)

        val secondo = ambiente.api.calcola(REGISTRAZIONE) { _, _ -> }.atteso()

        assertEquals(emptyList(), secondo.spostamenti)
        assertEquals(primo.incerte, secondo.incerte)
    }

    @Test
    fun `AC-543 le riferimenti a intera Voce sono ricalcolate dallo stato corrente senza memoria del run precedente`() {
        val p = parlante("p", "Anna") // riferimento confermato
        val q = parlante("q", "Marco") // riferimento a intera Voce, unica Voce 5
        val classificatore = ClassificatoreSomiglianzaFinta(
            // movibiliValidi ordinati per inizio: s (indice 0, Voce 3), t (indice 1, Voce 5 = riferimento di Q).
            programmate = mapOf(0 to Classificazione.Sicura(q.id), 1 to Classificazione.Sicura(p.id)),
        )
        val ambiente = Ambiente(
            segmenti = listOf(
                seg(1, 1, 0, 2_000, confermato = true), // P
                seg(3, 3, 3_000, 5_000), // s: su Voce non attribuita, si sposta su Q
                seg(5, 5, 5_000, 7_000), // t: riferimento a intera Voce di Q, si sposta via verso P
            ),
            classificatore = classificatore,
        )
        attribuisci(ambiente, 1, p)
        attribuisci(ambiente, 5, q)

        val primo = ambiente.api.calcola(REGISTRAZIONE) { _, _ -> }.atteso()
        assertEquals(
            listOf(
                SpostamentoProposto(SegmentoId(3), VoceId(3), VoceId(5), IntervalloMs(3_000, 5_000)),
                SpostamentoProposto(SegmentoId(5), VoceId(5), VoceId(1), IntervalloMs(5_000, 7_000)),
            ),
            primo.spostamenti,
        )
        assertEquals(
            0,
            primo.spostamenti.count { it.segmentoId == SegmentoId(1) },
            "il confermato di P non si sposta mai",
        )
        ambiente.applica(primo.spostamenti)

        ambiente.api.calcola(REGISTRAZIONE) { _, _ -> }.atteso()

        val improntaAttesaS = attesaImpronta(seg(3, 5, 3_000, 5_000))
        assertEquals(
            listOf(improntaAttesaS),
            classificatore.chiamate[1].getValue(q.id),
            "in run 2 solo s (ora sulla Voce 5) e riferimento di Q: t se n'e andato",
        )
    }

    @Test
    fun `AC-544 tutti i Segmenti di un unica Voce Sicura altrove svuoterebbero il riferimento, nessuno spostamento`() {
        val p = parlante("p", "Anna") // confermato
        val q = parlante("q", "Marco") // intera Voce, unica Voce 5, tutti i suoi Segmenti Sicura(P)
        val ambiente = Ambiente(
            segmenti = listOf(
                seg(1, 1, 10_000, 12_000, confermato = true),
                seg(2, 5, 0, 2_000),
                seg(3, 5, 2_000, 4_000),
                seg(4, 5, 4_000, 6_000),
            ),
            classificatore = ClassificatoreSomiglianzaFinta(
                mapOf(
                    0 to Classificazione.Sicura(p.id),
                    1 to Classificazione.Sicura(p.id),
                    2 to Classificazione.Sicura(p.id),
                ),
            ),
        )
        attribuisci(ambiente, 1, p)
        attribuisci(ambiente, 5, q)

        val piano = ambiente.api.calcola(REGISTRAZIONE) { _, _ -> }.atteso()

        assertEquals(
            emptyList(),
            piano.spostamenti,
            "svuoterebbero l'unica Voce di Q: la guardia scarta i tre spostamenti",
        )
        assertEquals(3, piano.incerte)
    }

    @Test
    fun `AC-544 con una seconda Voce che tiene una incerta i tre spostamenti sono pianificati`() {
        val p = parlante("p", "Anna")
        val q = parlante("q", "Marco") // due Voci: 5 (si svuota) e 6 (resta un'incerta)
        val ambiente = Ambiente(
            segmenti = listOf(
                seg(1, 1, 10_000, 12_000, confermato = true),
                seg(2, 5, 0, 2_000),
                seg(3, 5, 2_000, 4_000),
                seg(4, 5, 4_000, 6_000),
                seg(5, 6, 6_000, 8_000), // resta Incerta: tiene viva la Voce di Q
            ),
            classificatore = ClassificatoreSomiglianzaFinta(
                mapOf(
                    0 to Classificazione.Sicura(p.id),
                    1 to Classificazione.Sicura(p.id),
                    2 to Classificazione.Sicura(p.id),
                ),
                // indice 3 (seg5) non programmato: Incerta di default.
            ),
        )
        attribuisci(ambiente, 1, p)
        attribuisci(ambiente, 5, q)
        attribuisci(ambiente, 6, q)

        val piano = ambiente.api.calcola(REGISTRAZIONE) { _, _ -> }.atteso()

        assertEquals(3, piano.spostamenti.size, "Q resta con seg5 su Voce 6: la guardia non scarta nulla")
        assertEquals(1, piano.incerte, "seg5, mai classificato Sicura")
    }

    @Test
    fun `AC-544 una cascata scarta anche gli spostamenti dipendenti da quelli gia scartati, in modo deterministico`() {
        val p = parlante("p", "Anna") // confermato, destinazione finale di r1
        val q = parlante("q", "Marco") // intera Voce, tutti Sicura(R)
        val r = parlante("r", "Elena") // intera Voce, il suo unico Segmento e Sicura(P)
        val ambiente = Ambiente(
            segmenti = listOf(
                seg(1, 1, 20_000, 22_000, confermato = true),
                seg(2, 5, 0, 2_000), // Q
                seg(3, 5, 2_000, 4_000), // Q
                seg(4, 5, 4_000, 6_000), // Q
                seg(5, 8, 6_000, 8_000), // R (unico Segmento)
            ),
            classificatore = ClassificatoreSomiglianzaFinta(
                // movibiliValidi per inizio: seg2,3,4 (Q, indici 0-2) -> Sicura(R); seg5 (R, indice 3) -> Sicura(P).
                mapOf(
                    0 to Classificazione.Sicura(r.id),
                    1 to Classificazione.Sicura(r.id),
                    2 to Classificazione.Sicura(r.id),
                    3 to Classificazione.Sicura(p.id),
                ),
            ),
        )
        attribuisci(ambiente, 1, p)
        attribuisci(ambiente, 5, q)
        attribuisci(ambiente, 8, r)

        val primo = ambiente.api.calcola(REGISTRAZIONE) { _, _ -> }.atteso()
        val secondo = ambiente.api.calcola(REGISTRAZIONE) { _, _ -> }.atteso()

        assertEquals(
            emptyList(),
            primo.spostamenti,
            "scartare le mosse di Q per non svuotarla scarta anche quella di R",
        )
        assertEquals(4, primo.incerte)
        assertEquals(primo, secondo, "il risultato e deterministico: stesso piano due volte")
    }

    private fun attesaImpronta(s: SegmentoDiVoce): Impronta =
        EstrattoreImprontaFinta().estrai(
            DecodificatoreAudioFinta().campioni(REGISTRAZIONE, SorgenteImpronta.di(listOf(s.intervallo)).intervalli),
        )

    private fun SegmentoDiVoce.impronta(): Impronta = attesaImpronta(this)

    private fun attribuisci(ambiente: Ambiente, voce: Int, parlante: Parlante) {
        ambiente.parlanti.salva(parlante).atteso()
        val attribuzione = Attribuzione.conferma(VoceRef(REGISTRAZIONE, VoceId(voce)), PROGETTO, parlante.id).aggregato
        ambiente.attribuzioni.salva(attribuzione)
    }

    /** Records every `(Registrazione, intervalli)` it is asked to decode, delegating for real samples. */
    private class DecodificatoreAudioCheConta(private val delegato: DecodificatoreAudio) : DecodificatoreAudio {
        val chiamate: MutableList<Pair<RegistrazioneId, List<IntervalloMs>>> = mutableListOf()

        override fun campioni(id: RegistrazioneId, intervalli: List<IntervalloMs>): CampioniAudio {
            chiamate += id to intervalli
            return delegato.campioni(id, intervalli)
        }
    }

    private class EstrattoreImprontaCheConta(private val delegato: EstrattoreImpronta) : EstrattoreImpronta {
        var chiamate: Int = 0
            private set

        override val modello: String get() = delegato.modello

        override fun estrai(c: CampioniAudio): Impronta {
            chiamate++
            return delegato.estrai(c)
        }
    }

    @Suppress("LongParameterList") // one parameter per collaborator, mirrors the read-model's own constructor
    private class Ambiente(
        val parlanti: ParlanteRepositoryFinta = ParlanteRepositoryFinta(),
        val attribuzioni: AttribuzioneRepositoryFinta = AttribuzioneRepositoryFinta(),
        segmenti: List<SegmentoDiVoce> = emptyList(),
        val classificatore: ClassificatoreSomiglianzaFinta = ClassificatoreSomiglianzaFinta(),
        decodificatore: (UnitaDiLavoroFinta) -> DecodificatoreAudio = { DecodificatoreAudioFinta(it) },
        estrattore: (UnitaDiLavoroFinta) -> EstrattoreImpronta = { EstrattoreImprontaFinta(unitaDiLavoro = it) },
        lettoreVoci: LettoreVoci? = null,
    ) {
        private val uow = UnitaDiLavoroFinta()
        private val segmentiVivi = segmenti.toMutableList()
        private val dati = mutableMapOf(REGISTRAZIONE to segmentiVivi.toList())
        private val lettore: LettoreVoci = lettoreVoci ?: LettoreVociFinta(segmenti = dati)
        val api: PianoRiassegnazioneQuery = PianoRiassegnazioneQuery(
            lettore,
            attribuzioni,
            parlanti,
            decodificatore(uow),
            estrattore(uow),
            classificatore,
        )

        /** Moves each spostamento's Segmento onto its `a` Voce (the automatic batch touches no `confermato` flag). */
        fun applica(spostamenti: List<SpostamentoProposto>) {
            spostamenti.forEach { m ->
                val i = segmentiVivi.indexOfFirst { it.segmentoId == m.segmentoId }
                segmentiVivi[i] = segmentiVivi[i].copy(voceId = m.a)
            }
            dati[REGISTRAZIONE] = segmentiVivi.toList()
        }
    }

    private companion object {
        val PROGETTO = ProgettoId("progetto-1")
        val REGISTRAZIONE = RegistrazioneId("registrazione-1")

        fun parlante(id: String, nome: String, tipo: TipoParlante = TipoParlante.RICORRENTE): Parlante =
            Parlante.crea(ParlanteId(id), PROGETTO, Nome.di(nome).atteso(), tipo).aggregato

        fun seg(numero: Int, voce: Int, inizioMs: Long, fineMs: Long, confermato: Boolean = false): SegmentoDiVoce =
            SegmentoDiVoce(SegmentoId(numero), VoceId(voce), IntervalloMs(inizioMs, fineMs), confermato)
    }
}
